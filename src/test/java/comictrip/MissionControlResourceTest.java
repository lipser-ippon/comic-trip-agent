package comictrip;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not; // Add this import
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class MissionControlResourceTest {

    @InjectMock
    ComicTripAnalyzer analyzer;

    @InjectMock
    TripService tripService;

    private File tempFile;

    @BeforeEach
    public void setup() throws IOException {
        tempFile = File.createTempFile("test-upload", ".jpg");
        Files.writeString(tempFile.toPath(), "fake image data");
        tempFile.deleteOnExit();
    }

    @Test
    public void testDeleteTrip_Success() {
        // Given
        String tripId = "test-trip-to-delete";
        ArgumentCaptor<String> tripIdCaptor = ArgumentCaptor.forClass(String.class);
        doNothing().when(tripService).deleteTrip(tripIdCaptor.capture());

        // When & Then
        given()
            .when()
            .delete("/api/mission-control/trips/{tripId}", tripId)
            .then()
            .statusCode(204);

        // Verify
        verify(tripService).deleteTrip(tripId);
        assertEquals(tripId, tripIdCaptor.getValue());
    }

    @Test
    public void testUploadFile_Success() {
        // Given: Mock the analyzer and service
        String fakeTripId = "xyz123";
        String fakeTripTitle = "Aventure : Mystères Insoupçonnés";
        ComicOutput.Details details = new ComicOutput.Details("A mock description", "Mockland");
        ComicOutput.Image image = new ComicOutput.Image("mock-image-id", null, "image/jpeg");
        ComicOutput comicOutput = new ComicOutput(fakeTripId, image, details, "Some POIs");

        // We can't easily mock the static method generateId, but we can capture the generated tripId.
        // The analyzer is mocked, so we'll have it return a ComicOutput with a consistent tripId.
        when(analyzer.analyzeComic(any(byte[].class), anyString(), anyString())).thenReturn(comicOutput);

        // Mock the trip saving process
        ArgumentCaptor<String> tripIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> comicOutputsCaptor = ArgumentCaptor.forClass(List.class);
        doNothing().when(tripService).saveTrip(tripIdCaptor.capture(), titleCaptor.capture(), comicOutputsCaptor.capture());


        // When & Then: We upload a file
        given()
            .multiPart("file", tempFile, "image/jpeg")
            .when()
            .post("/api/mission-control/upload")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("tripId", notNullValue()) // The tripId is generated, so we just check for presence
            .body("title", notNullValue())
            .body("title", is(not(""))) // The title comes from the mocked Gemini call inside the resource
            .body("pictures.size()", is(1))
            .body("pictures[0].image.name", is("mock-image-id"));


        // Verify that the service was called correctly
        verify(tripService).saveTrip(anyString(), anyString(), any(List.class));
        List<ComicOutput> capturedOutputs = comicOutputsCaptor.getValue();
        assertEquals(1, capturedOutputs.size());
        assertEquals("mock-image-id", capturedOutputs.getFirst().image().name());
    }

    @Test
    public void testUploadFile_NoFiles() {
        // When & Then: We send a request with no files
        given()
            .contentType(ContentType.MULTIPART) // Explicitly set content type
            .when()
            .post("/api/mission-control/upload")
            .then()
            .statusCode(400); // Expecting Bad Request
    }
}
