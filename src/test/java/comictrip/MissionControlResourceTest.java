package comictrip;

import comictrip.domain.model.AnalysisResult;
import comictrip.domain.model.ImageAnalysis;
import comictrip.domain.model.UploadedImage;
import comictrip.domain.port.in.CreateTripUseCase;
import comictrip.domain.port.in.DeleteTripUseCase;
import comictrip.domain.port.out.ImageAnalysisPort;
import comictrip.domain.port.out.TitleGenerationPort;
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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class MissionControlResourceTest {

    @InjectMock
    ImageAnalysisPort imageAnalysisPort;

    @InjectMock
    TitleGenerationPort titleGenerationPort;

    @InjectMock
    CreateTripUseCase createTripUseCase;

    @InjectMock
    DeleteTripUseCase deleteTripUseCase;

    private File tempFile;

    @BeforeEach
    public void setup() throws IOException {
        tempFile = File.createTempFile("test-upload", ".jpg");
        Files.writeString(tempFile.toPath(), "fake image data");
        tempFile.deleteOnExit();
    }

    @Test
    public void testDeleteTrip_Success() {
        String tripId = "test-trip-to-delete";
        ArgumentCaptor<String> tripIdCaptor = ArgumentCaptor.forClass(String.class);
        doNothing().when(deleteTripUseCase).deleteTrip(tripIdCaptor.capture());

        given()
                .when()
                .delete("/api/trips/{tripId}", tripId)
                .then()
                .statusCode(204);

        verify(deleteTripUseCase).deleteTrip(tripId);
        assertEquals(tripId, tripIdCaptor.getValue());
    }

    @Test
    public void testUploadFile_Success() {
        String fakeTripId = "xyz123";
        String fakeTripTitle = "Aventure : Mystères Insoupçonnés";
        ImageAnalysis details = new ImageAnalysis("A mock description", "Mockland");
        UploadedImage image = new UploadedImage("mock-image-id", null, "image/jpeg");
        AnalysisResult analysisResult = new AnalysisResult(fakeTripId, image, details, "Some POIs");

        when(imageAnalysisPort.analyze(any(byte[].class), anyString(), anyString())).thenReturn(analysisResult);
        when(titleGenerationPort.generateTitle(anyList())).thenReturn(fakeTripTitle);

        ArgumentCaptor<String> tripIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<List> resultsCaptor = ArgumentCaptor.forClass(List.class);
        doNothing().when(createTripUseCase).createTrip(tripIdCaptor.capture(), titleCaptor.capture(), resultsCaptor.capture());

        given()
                .multiPart("file", tempFile, "image/jpeg")
                .when()
                .post("/api/upload")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("tripId", notNullValue())
                .body("title", notNullValue())
                .body("title", is(not("")))
                .body("pictures.size()", is(1))
                .body("pictures[0].image.name", is("mock-image-id"));

        verify(createTripUseCase).createTrip(anyString(), anyString(), any(List.class));
        List<AnalysisResult> capturedResults = resultsCaptor.getValue();
        assertEquals(1, capturedResults.size());
        assertEquals("mock-image-id", capturedResults.getFirst().image().name());
    }

    @Test
    public void testUploadFile_NoFiles() {
        given()
                .contentType(ContentType.MULTIPART)
                .when()
                .post("/api/upload")
                .then()
                .statusCode(400);
    }
}
