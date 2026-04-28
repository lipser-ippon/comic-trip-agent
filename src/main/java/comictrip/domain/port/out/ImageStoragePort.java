package comictrip.domain.port.out;

import comictrip.domain.model.Picture;

import java.util.List;

public interface ImageStoragePort {
    void deleteImages(String tripId, List<Picture> pictures);
}
