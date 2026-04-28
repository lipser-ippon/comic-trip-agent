package comictrip.domain.model;

public record UploadedImage(String name, byte[] imageBytes, String mimeType) {
}
