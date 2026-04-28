package comictrip.domain.model;

import java.util.List;

public record Picture(
        String fileName,
        String description,
        String location,
        String mimeType,
        String imageUrl,
        String pointsOfInterest
) {
    public String locationShort() {
        if (location != null && location.contains(",")) {
            return location.split(",")[0].trim();
        }
        return location != null ? location : "Unknown";
    }
}
