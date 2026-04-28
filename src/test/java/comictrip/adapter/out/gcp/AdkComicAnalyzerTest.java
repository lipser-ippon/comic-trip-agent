package comictrip.adapter.out.gcp;

import comictrip.domain.model.ImageAnalysis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdkComicAnalyzerTest {

    @Test
    void parseDescriptionAndLocation_plainJson() throws Exception {
        String json = "{\"description\": \"La tour Eiffel\", \"location\": \"Paris, France\"}";

        ImageAnalysis result = AdkComicAnalyzer.parseDescriptionAndLocation(json);

        assertNotNull(result);
        assertEquals("La tour Eiffel", result.description());
        assertEquals("Paris, France", result.location());
    }

    @Test
    void parseDescriptionAndLocation_withJsonCodeFence() throws Exception {
        String json = "```json\n{\"description\": \"La tour Eiffel\", \"location\": \"Paris, France\"}\n```";

        ImageAnalysis result = AdkComicAnalyzer.parseDescriptionAndLocation(json);

        assertNotNull(result);
        assertEquals("La tour Eiffel", result.description());
        assertEquals("Paris, France", result.location());
    }

    @Test
    void parseDescriptionAndLocation_withPlainCodeFence() throws Exception {
        String json = "```\n{\"description\": \"Le Louvre\", \"location\": \"Paris, France\"}\n```";

        ImageAnalysis result = AdkComicAnalyzer.parseDescriptionAndLocation(json);

        assertNotNull(result);
        assertEquals("Le Louvre", result.description());
        assertEquals("Paris, France", result.location());
    }
}
