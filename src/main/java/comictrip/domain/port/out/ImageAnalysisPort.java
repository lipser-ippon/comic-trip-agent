package comictrip.domain.port.out;

import comictrip.domain.model.AnalysisResult;

public interface ImageAnalysisPort {
    AnalysisResult analyze(byte[] imageBytes, String mimeType, String tripId);
}
