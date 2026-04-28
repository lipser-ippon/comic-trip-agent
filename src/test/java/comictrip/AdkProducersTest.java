package comictrip;

import com.google.adk.apps.App;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for AdkProducers.
 *
 * Note: the afterModelCallback lambda cannot be tested here without running the full
 * ADK runtime. A future integration test with a mocked GCS + ADK runner would be
 * needed to verify the "no local file leak" behavior end-to-end.
 */
class AdkProducersTest {

    @Mock
    Storage storage;

    @Mock
    IdGenerator idGenerator;

    AdkProducers adkProducers;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adkProducers = new AdkProducers();
        adkProducers.storage = storage;
        adkProducers.idGenerator = idGenerator;
        adkProducers.comicTripPictureBucket = "test-bucket";
        adkProducers.appName = "test_app";
        adkProducers.pictureAnalyserAgentModel = "gemini-2.5-flash";
        adkProducers.comicIllustratorAgentModel = "gemini-2.5-flash-image";
        adkProducers.pointOfInterestAgentModel = "gemini-2.5-flash";
    }

    @Test
    void comicTripApp_buildsSuccessfully() {
        App app = adkProducers.comicTripApp();
        assertNotNull(app);
    }

    @Test
    void comicTripApp_doesNotCreateLocalFilesOnDisk() throws IOException {
        Path workingDir = Path.of(".");
        long pngCountBefore = countPngFiles(workingDir);

        adkProducers.comicTripApp();

        long pngCountAfter = countPngFiles(workingDir);
        assertEquals(pngCountBefore, pngCountAfter,
                "Building the ADK pipeline must not write any PNG file to disk");
    }

    private long countPngFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".png")).count();
        }
    }
}
