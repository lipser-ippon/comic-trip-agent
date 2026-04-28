package comictrip.infrastructure;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.apps.App;
import com.google.adk.artifacts.GcsArtifactService;
import com.google.adk.plugins.LoggingPlugin;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.InMemorySessionService;
import com.google.cloud.storage.Storage;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

import static comictrip.infrastructure.AdkConstants.OUTPUT_KEY_COMIC_ILLUSTRATION;
import static comictrip.infrastructure.AdkConstants.OUTPUT_KEY_DESCRIPTION_AND_LOCATION;
import static comictrip.infrastructure.AdkConstants.OUTPUT_KEY_POINTS_OF_INTEREST;

@ApplicationScoped
public class AdkConfig {

    private static final Logger LOGGER = Logger.getLogger(AdkConfig.class);

    @Inject
    Storage storage;
    @Inject
    IdGenerator idGenerator;

    @ConfigProperty(name = "comic-trip.picture.bucket")
    String comicTripPictureBucket;
    @ConfigProperty(name = "comic-trip.app-name", defaultValue = "comic_trip_app")
    String appName;
    @ConfigProperty(name = "picture_analyzer_agent_model", defaultValue = "gemini-2.5-flash")
    String pictureAnalyserAgentModel;
    @ConfigProperty(name = "comic_illustrator_agent_model", defaultValue = "gemini-2.5-flash-image")
    String comicIllustratorAgentModel;
    @ConfigProperty(name = "point_of_interest_agent_model", defaultValue = "gemini-2.5-flash")
    String pointOfInterestAgentModel;

    @Produces
    @ApplicationScoped
    public App comicTripApp() {
        LlmAgent comicTripAgent = LlmAgent.builder()
                .model(pictureAnalyserAgentModel)
                .name("picture_analyzer_agent")
                .instruction("""
                            Analyse l'image et retourne :
                            - une description détaillée du contenu de l'image
                            - le lieu où cette photo a probablement été prise

                            Retourne le résultat au format JSON, sans aucun commentaire ni balise de bloc de code Markdown, sous la forme :

                            {"description": "La tour Eiffel depuis le Champ de Mars par une journée ensoleillée",
                            "location": "Tour Eiffel, Paris, France"}
                        """)
                .outputKey(OUTPUT_KEY_DESCRIPTION_AND_LOCATION)
                .build();

        LlmAgent comicCreatorAgent = LlmAgent.builder()
                .model(comicIllustratorAgentModel)
                .name("comic_illustrator_agent")
                .instruction("""
                        Transforme cette photographie en une case de bande dessinée pop-art, avec des contours noirs épais, des gouttes de couleur, des éclaboussures et des traits larges ou des formes géométriques. Utilise des textures en simili-gravure (halftone) pour les zones qui ne sont pas en couleurs primaires, ainsi qu'une palette de couleurs vintage aux tons atténués. Une légende devra décrire le lieu, tel qu'indiqué dans :
                        {description_and_location}
                        """)
                .generateContentConfig(GenerateContentConfig.builder()
                        .responseModalities("IMAGE")
                        .build())
                .outputKey(OUTPUT_KEY_COMIC_ILLUSTRATION)
                .afterModelCallback((callbackContext, llmResponse) -> {
                    Optional<Part> imagePart = llmResponse.content()
                            .flatMap(Content::parts)
                            .stream()
                            .flatMap(List::stream)
                            .filter(part -> part.inlineData().isPresent())
                            .findFirst();

                    if (imagePart.isEmpty()) {
                        LOGGER.warn("comic_illustrator_agent: aucune donnée image inline dans la réponse");
                        return Maybe.empty();
                    }

                    Part part = imagePart.get();
                    String imageId = idGenerator.generateId();

                    try {
                        callbackContext.saveArtifact(imageId + ".png", part).blockingAwait();
                        LOGGER.infof("Image comic sauvegardée sur GCS : %s.png", imageId);
                    } catch (Exception e) {
                        LOGGER.errorf(e, "Échec de la sauvegarde GCS : %s.png", imageId);
                    }

                    return Maybe.just(llmResponse.toBuilder()
                            .content(Content.fromParts(Part.fromText(imageId)))
                            .build());
                }).build();

        LlmAgent poiGoogleMapsAgent = LlmAgent.builder()
                .name("points_of_interest_agent")
                .model(pointOfInterestAgentModel)
                .instruction("""
                            Étant donné l'emplacement à {description_and_location}

                            Dresse une liste des points d'intérêt (POI) dans les environs, à une distance maximale d'un kilomètre.

                            Chaque POI doit comporter un nom et une description.

                            Ne mentionne pas les distances dans ta réponse. Ne commence pas la liste par un texte d'introduction.
                        """)
                .outputKey(OUTPUT_KEY_POINTS_OF_INTEREST)
                .build();

        ParallelAgent poiAndComicFlow = ParallelAgent.builder()
                .name("poi_and_comic_flow")
                .subAgents(poiGoogleMapsAgent, comicCreatorAgent)
                .build();

        SequentialAgent mainFlow = SequentialAgent.builder()
                .name("main_flow")
                .subAgents(comicTripAgent, poiAndComicFlow)
                .build();

        return App.builder()
                .name(appName)
                .plugins(List.of(new LoggingPlugin(appName)))
                .rootAgent(mainFlow)
                .build();
    }

    @Produces
    @ApplicationScoped
    public GcsArtifactService gcsArtifactService(Storage storage) {
        return new GcsArtifactService(comicTripPictureBucket, storage);
    }

    @Produces
    @ApplicationScoped
    public InMemorySessionService inMemorySessionService() {
        return new InMemorySessionService();
    }

    @Produces
    @ApplicationScoped
    public Runner runner(App app, InMemorySessionService sessionService, GcsArtifactService artifactService) {
        return InMemoryRunner.builder()
                .app(app)
                .sessionService(sessionService)
                .artifactService(artifactService)
                .build();
    }
}
