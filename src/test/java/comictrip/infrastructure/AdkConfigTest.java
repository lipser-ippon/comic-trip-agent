package comictrip.infrastructure;

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
 * Unit tests for AdkConfig.
 */
class AdkConfigTest {

    @Mock
    Storage storage;

    @Mock
    IdGenerator idGenerator;

    AdkConfig adkConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adkConfig = new AdkConfig();
        adkConfig.storage = storage;
        adkConfig.idGenerator = idGenerator;
        adkConfig.comicTripPictureBucket = "test-bucket";
        adkConfig.appName = "test_app";
        adkConfig.pictureAnalyserAgentModel = "gemini-2.5-flash";
        adkConfig.comicIllustratorAgentModel = "gemini-2.5-flash-image";
        adkConfig.pointOfInterestAgentModel = "gemini-2.5-flash";
    }

    @Test
    void comicTripApp_buildsSuccessfully() {
        App app = adkConfig.comicTripApp();
        assertNotNull(app);
    }

    @Test
    void comicTripApp_doesNotCreateLocalFilesOnDisk() throws IOException {
        Path workingDir = Path.of(".");
        long pngCountBefore = countPngFiles(workingDir);

        adkConfig.comicTripApp();

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
