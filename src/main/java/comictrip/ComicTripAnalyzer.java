/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package comictrip;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.apps.App;
import com.google.adk.artifacts.GcsArtifactService;
import com.google.adk.events.Event;
import com.google.adk.plugins.LoggingPlugin;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.SessionKey;
import com.google.cloud.storage.StorageOptions;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.sqids.Sqids;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@ApplicationScoped
public class ComicTripAnalyzer {

    private static final Logger LOGGER = Logger.getLogger(ComicTripAnalyzer.class);

    @ConfigProperty(name = "comic-trip.picture.bucket", defaultValue = "comic-trip-picture-bucket")
    String comicTripPictureBucket;

    private static final String COMIC_TRIP_APP_NAME = "comic_trip_app";
    private static final String COMIC_TRIP_USER = "comic_trip_user";

    private static final String OUTPUT_KEY_COMIC_ILLUSTRATION = "comic_illustration";
    private static final String OUTPUT_KEY_DESCRIPTION_AND_LOCATION = "description_and_location";
    private static final String OUTPUT_KEY_POINTS_OF_INTEREST = "points_of_interest";

    public ComicOutput analyzeComic(byte[] imageBytes, String mimeType, String tripId) {

        LlmAgent comicTripAgent = LlmAgent.builder()
            .model("gemini-2.5-flash")
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
            .model("gemini-2.5-flash-image")
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
                byte[] comicImageBytes = part.inlineData().get().data().get();
                String imageId = generateId();
                saveFileLocally(imageId, comicImageBytes);

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
            .model("gemini-2.5-flash")
            .instruction("""
                    Étant donné l'emplacement à {description_and_location}
                    
                    Dresse une liste des points d'intérêt (POI) dans les environs, à une distance maximale d'un kilomètre.

                    Chaque POI doit comporter un nom et une description.

                    Ne mentionne pas les distances dans ta réponse. Ne commence pas la liste par un texte d'introduction.
                """)
            .outputKey(OUTPUT_KEY_POINTS_OF_INTEREST)
            .build();

        ParallelAgent poiAndCommicFlow = ParallelAgent.builder()
            .name("poi_and_comic_flow")
            .subAgents(
                poiGoogleMapsAgent,
                comicCreatorAgent)
            .build();

        SequentialAgent mainFlow = SequentialAgent.builder()
            .name("main_flow")
            .subAgents(
                comicTripAgent,
                poiAndCommicFlow)
            .build();

        App comicTripApp = App.builder()
            .name(COMIC_TRIP_APP_NAME)
            .plugins(List.of(
                new LoggingPlugin(COMIC_TRIP_APP_NAME)))
            .rootAgent(mainFlow)
            .build();

        InMemorySessionService sessionService = new InMemorySessionService();

        Runner runner = InMemoryRunner.builder()
            .app(comicTripApp)
            .sessionService(sessionService)
            .artifactService(new GcsArtifactService(
                comicTripPictureBucket,
                StorageOptions.getDefaultInstance().getService()))
            .build();

        SessionKey sessionKey = new SessionKey(COMIC_TRIP_APP_NAME, COMIC_TRIP_USER, tripId);

        Session session = runner.sessionService()
            .createSession(sessionKey)
            .blockingGet();

        Flowable<Event> eventFlowable = runner.runAsync(
            session.userId(), session.id(),
            Content.fromParts(
                Part.fromBytes(imageBytes, mimeType),
                Part.fromText("Analyze this image.")));

        eventFlowable.ignoreElements().blockingAwait();

        session = runner.sessionService().getSession(sessionKey, null).blockingGet();
        Map<String, Object> state = session.state();

        String pointsOfInterest = (String) state.get(OUTPUT_KEY_POINTS_OF_INTEREST);
        String descriptionAndLocation = (String) state.get(OUTPUT_KEY_DESCRIPTION_AND_LOCATION);
        String imageId = (String) state.get(OUTPUT_KEY_COMIC_ILLUSTRATION);

        String imageUrl = "";
        // The imageUrl variable is no longer needed to construct ComicOutput.Image.
        // It was used to get a public URL for GCS, but the proxy handles that now.

        ComicOutput.Image image = new ComicOutput.Image(imageId, null, "image/png");
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            String jsonToParse = descriptionAndLocation.trim();
            if (jsonToParse.startsWith("```")) {
                jsonToParse = jsonToParse.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
            }
            ComicOutput.Details details = objectMapper.readValue(jsonToParse,
                ComicOutput.Details.class);
            return new ComicOutput(tripId, image, details, pointsOfInterest);
        } catch (Exception e) {
            LOGGER.error("Failed to parse comic trip details", e);
            return new ComicOutput(tripId, image,
                new ComicOutput.Details(descriptionAndLocation, descriptionAndLocation),
                pointsOfInterest);
        }
    }

    private static String generateId() {
        Sqids sqids = Sqids.builder().build();
        return sqids.encode(List.of(new Random().nextLong(0, Integer.MAX_VALUE)));
    }

    private static void saveFileLocally(String imageId, byte[] comicImageBytes) {
        try {
            Files.write(Path.of(imageId + ".png"),
                comicImageBytes,
                StandardOpenOption.CREATE);
        } catch (IOException e) {
            LOGGER.error("Failed to save file locally", e);
        }
    }
}
