package comictrip.domain.model;

public record AnalysisResult(
        String tripId,
        UploadedImage image,
        ImageAnalysis details,
        String pointsOfInterest
) {
}
