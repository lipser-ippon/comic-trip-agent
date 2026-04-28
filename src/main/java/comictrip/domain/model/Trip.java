package comictrip.domain.model;

import java.util.List;

public record Trip(String tripId, String title, List<Picture> pictures) {
    public String firstImageUrl() {
        if (pictures != null && !pictures.isEmpty()) {
            return pictures.getFirst().imageUrl();
        }
        return "";
    }
}
