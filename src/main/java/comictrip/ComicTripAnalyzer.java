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
import com.google.adk.tools.GoogleMapsTool;
import com.google.cloud.storage.StorageOptions;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.sqids.Sqids;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;

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
            .model("gemini-3-flash-preview")
            .name("picture_analyzer_agent")
            .instruction("""
                Analyze the picture and return:
                - a detailed description of the content of the picture
                - the location where this picture was probably taken
                
                Return the result as JSON, without any commentary
                or Markdown code block notation, in the form:
                
                {"description": "The Eiffel tower from the Champs de Mars on a sunny day",
                 "location": "Eiffel tower, Paris, France"}
                """)
            .outputKey(OUTPUT_KEY_DESCRIPTION_AND_LOCATION)
            .build();

        LlmAgent comicCreatorAgent = LlmAgent.builder()
            .model("gemini-3.1-flash-image-preview")
            .name("comic_illustrator_agent")
            .instruction("""
                Turn this photography into a pop-art comic panel,
                with thick black outlines, colors drops,
                splashes and wide strokes or geometrical shapes.
                Use halftone textures for non-primary colored areas,
                and a vintage muted color palette.
                A caption should describe the location, as given in:
                {description_and_location}
                """)
            .generateContentConfig(GenerateContentConfig.builder()
                .responseModalities("IMAGE")
                .build())
            .outputKey(OUTPUT_KEY_COMIC_ILLUSTRATION)
            .afterModelCallback((callbackContext, llmResponse) ->
                Maybe.fromOptional(llmResponse.content()
                    .flatMap(Content::parts)
                    .stream()
                    .flatMap(List::stream)
                    .filter(part -> part.inlineData().isPresent())
                    .findFirst()
                    .flatMap(part -> {
                        byte[] comicImageBytes = part.inlineData().get().data().get();
                        String imageId = generateId();
                        saveFileLocally(imageId, comicImageBytes);

                        callbackContext.saveArtifact(imageId + ".png", part)
                            .blockingAwait();

                        return Optional.of(llmResponse.toBuilder()
                            .content(Content.fromParts(Part.fromText(imageId)))
                            .build());
                    }))
            ).build();

        LlmAgent poiGoogleMapsAgent = LlmAgent.builder()
            .name("points_of_interest_agent")
            .model("gemini-2.5-flash")
            .instruction("""
                Given the location in:
                {description_and_location}
                
                Please list points of interest (POI)
                in the area no further than a kilometer away
                using the `google_maps` tool.
                
                Each POI should have a name and a description.
                
                Don't mention distances in your response.
                And don't start with introductory text for the list.
                """)
            .tools(new GoogleMapsTool())
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
        ConcurrentMap<String, Object> state = session.state();

        String pointsOfInterest = (String) state.get(OUTPUT_KEY_POINTS_OF_INTEREST);
        String descriptionAndLocation = (String) state.get(OUTPUT_KEY_DESCRIPTION_AND_LOCATION);
        String imageId = (String) state.get(OUTPUT_KEY_COMIC_ILLUSTRATION);

        String imageUrl = "";
        if (imageId != null && !imageId.isEmpty()) {
            imageUrl = "https://storage.googleapis.com/" + comicTripPictureBucket +
                "/" + COMIC_TRIP_APP_NAME +
                "/" + COMIC_TRIP_USER +
                "/" + tripId +
                "/" + imageId + ".png/0";
        }

        ComicOutput.Image image = new ComicOutput.Image(imageUrl, null, "image/png");
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            ComicOutput.Details details = objectMapper.readValue(descriptionAndLocation,
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

    public static void main(String[] args) throws IOException {
        // Path input = Path.of("src/main/resources/istanbul/galata-tower.jpg");
        Path input = Path.of("src/main/resources/istanbul/PXL_20250314_075430647.jpg");
        byte[] originalImageBytes = Files.readAllBytes(input);
        LOGGER.infof("Image bytes read: %d", originalImageBytes.length);

        Sqids sqids = Sqids.builder().build();
        String tripId = sqids.encode(List.of(System.currentTimeMillis()));
        ComicOutput comicOutput = new ComicTripAnalyzer().analyzeComic(originalImageBytes, "image/png", tripId);

        LOGGER.infof("comicOutput = %s", comicOutput);
        System.exit(0);
    }
}
