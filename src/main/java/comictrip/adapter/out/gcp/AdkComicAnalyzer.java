package comictrip.adapter.out.gcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.SessionKey;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import comictrip.domain.model.AnalysisResult;
import comictrip.domain.model.ImageAnalysis;
import comictrip.domain.model.UploadedImage;
import comictrip.domain.port.out.ImageAnalysisPort;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Map;

import static comictrip.infrastructure.AdkConstants.OUTPUT_KEY_COMIC_ILLUSTRATION;
import static comictrip.infrastructure.AdkConstants.OUTPUT_KEY_DESCRIPTION_AND_LOCATION;
import static comictrip.infrastructure.AdkConstants.OUTPUT_KEY_POINTS_OF_INTEREST;

@ApplicationScoped
public class AdkComicAnalyzer implements ImageAnalysisPort {

    private static final Logger LOGGER = Logger.getLogger(AdkComicAnalyzer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Runner runner;
    private final InMemorySessionService sessionService;
    private final String appName;
    private final String user;

    public AdkComicAnalyzer(
            Runner runner,
            InMemorySessionService sessionService,
            @ConfigProperty(name = "comic-trip.app-name", defaultValue = "comic_trip_app") String appName,
            @ConfigProperty(name = "comic-trip.user", defaultValue = "comic_trip_user") String user) {
        this.runner = runner;
        this.sessionService = sessionService;
        this.appName = appName;
        this.user = user;
    }

    @Override
    public AnalysisResult analyze(byte[] imageBytes, String mimeType, String tripId) {
        SessionKey sessionKey = new SessionKey(appName, user, tripId);

        Session session = sessionService
                .createSession(sessionKey)
                .blockingGet();

        Flowable<Event> eventFlowable = runner.runAsync(
                session.userId(), session.id(),
                Content.fromParts(
                        Part.fromBytes(imageBytes, mimeType),
                        Part.fromText("Analyze this image.")));

        eventFlowable.ignoreElements().blockingAwait();

        session = sessionService.getSession(sessionKey, null).blockingGet();
        Map<String, Object> state = session.state();

        String pointsOfInterest = (String) state.get(OUTPUT_KEY_POINTS_OF_INTEREST);
        String descriptionAndLocation = (String) state.get(OUTPUT_KEY_DESCRIPTION_AND_LOCATION);
        String imageId = (String) state.get(OUTPUT_KEY_COMIC_ILLUSTRATION);

        UploadedImage image = new UploadedImage(imageId, null, "image/png");

        try {
            ImageAnalysis details = parseDescriptionAndLocation(descriptionAndLocation);
            return new AnalysisResult(tripId, image, details, pointsOfInterest);
        } catch (JsonProcessingException e) {
            LOGGER.error("Échec du parsing des détails du trip", e);
            return new AnalysisResult(tripId, image,
                    new ImageAnalysis(descriptionAndLocation, descriptionAndLocation),
                    pointsOfInterest);
        }
    }

    static ImageAnalysis parseDescriptionAndLocation(String raw) throws JsonProcessingException {
        String jsonToParse = raw.trim();
        if (jsonToParse.startsWith("```")) {
            jsonToParse = jsonToParse.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
        }
        return OBJECT_MAPPER.readValue(jsonToParse, ImageAnalysis.class);
    }
}
