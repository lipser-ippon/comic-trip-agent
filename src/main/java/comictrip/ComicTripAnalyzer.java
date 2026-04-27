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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.SessionKey;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.util.Map;

@ApplicationScoped
public class ComicTripAnalyzer {

    private static final Logger LOGGER = Logger.getLogger(ComicTripAnalyzer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject
    Runner runner;
    @Inject
    InMemorySessionService sessionService;

    @ConfigProperty(name = "comic-trip.app-name", defaultValue = "comic_trip_app")
    String appName;
    @ConfigProperty(name = "comic-trip.user", defaultValue = "comic_trip_user")
    String user;
    
    private static final String OUTPUT_KEY_COMIC_ILLUSTRATION = "comic_illustration";
    private static final String OUTPUT_KEY_DESCRIPTION_AND_LOCATION = "description_and_location";
    private static final String OUTPUT_KEY_POINTS_OF_INTEREST = "points_of_interest";

    public ComicOutput analyzeComic(byte[] imageBytes, String mimeType, String tripId) {

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

        ComicOutput.Image image = new ComicOutput.Image(imageId, null, "image/png");

        try {
            String jsonToParse = descriptionAndLocation.trim();
            if (jsonToParse.startsWith("```")) {
                jsonToParse = jsonToParse.replaceAll("^```[a-zA-Z]*\\\\n?", "").replaceAll("```$", "").trim();
            }
            ComicOutput.Details details = OBJECT_MAPPER.readValue(jsonToParse,
                ComicOutput.Details.class);
            return new ComicOutput(tripId, image, details, pointsOfInterest);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse comic trip details", e);
            return new ComicOutput(tripId, image,
                new ComicOutput.Details(descriptionAndLocation, descriptionAndLocation),
                pointsOfInterest);
        }
    }
}
