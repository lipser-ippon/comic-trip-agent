package comictrip;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
public class ComicTripAnalyzerTest {

    // The main method, `analyzeComic`, orchestrates a complex flow using the Google Agent Development Kit (ADK).
    // The ADK components (Agents, Runner, Session) are created and chained together within the method,
    // making them very difficult to mock from the outside for a standard unit test.
    //
    // A meaningful test for this class would be an integration test that runs against emulators
    // for GCS and Firestore, and uses a fake or mocked LLM service. Setting up such a test
    // is beyond the scope of a simple test generation request.
    //
    // The test below demonstrates how a small, isolated part of the logic (JSON parsing) could be tested
    // if it were refactored into its own public or package-private method.

    @Test
    void testParseDescriptionAndLocation_plainJson() throws Exception {
        String jsonFromLLM = """
                {"description": "La tour Eiffel vue du Champ de Mars", "location": "Tour Eiffel, Paris, France"}
                """;

        ComicOutput.Details details = ComicTripAnalyzer.parseDescriptionAndLocation(jsonFromLLM);

        assertNotNull(details);
        assertEquals("La tour Eiffel vue du Champ de Mars", details.description());
        assertEquals("Tour Eiffel, Paris, France", details.location());
    }

    @Test
    void testParseDescriptionAndLocation_withJsonCodeFence() throws Exception {
        String jsonFromLLM = "```json\n{\"description\": \"La tour Eiffel\", \"location\": \"Paris, France\"}\n```";

        ComicOutput.Details details = ComicTripAnalyzer.parseDescriptionAndLocation(jsonFromLLM);

        assertNotNull(details);
        assertEquals("La tour Eiffel", details.description());
        assertEquals("Paris, France", details.location());
    }

    @Test
    void testParseDescriptionAndLocation_withPlainCodeFence() throws Exception {
        String jsonFromLLM = "```\n{\"description\": \"Le Louvre\", \"location\": \"Paris, France\"}\n```";

        ComicOutput.Details details = ComicTripAnalyzer.parseDescriptionAndLocation(jsonFromLLM);

        assertNotNull(details);
        assertEquals("Le Louvre", details.description());
        assertEquals("Paris, France", details.location());
    }
}
