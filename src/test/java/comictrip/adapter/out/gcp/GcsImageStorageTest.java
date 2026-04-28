package comictrip.adapter.out.gcp;

import com.google.cloud.storage.Storage;
import comictrip.domain.model.Picture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.Mockito.verify;

class GcsImageStorageTest {

    @Mock
    Storage storage;

    GcsImageStorage gcsImageStorage;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        gcsImageStorage = new GcsImageStorage(storage, "test-bucket", "comic_trip_app", "comic_trip_user");
    }

    @Test
    void deleteImages_callsStorageDeleteForEachPicture() {
        Picture pic1 = new Picture("img1", "desc1", "loc1", "image/png", "/images/trip1/img1", "");
        Picture pic2 = new Picture("img2", "desc2", "loc2", "image/png", "/images/trip1/img2", "");

        gcsImageStorage.deleteImages("trip1", List.of(pic1, pic2));

        verify(storage).delete("test-bucket", "comic_trip_app/comic_trip_user/trip1/img1.png/0");
        verify(storage).delete("test-bucket", "comic_trip_app/comic_trip_user/trip1/img2.png/0");
    }

    @Test
    void deleteImages_withEmptyList_doesNotCallStorage() {
        gcsImageStorage.deleteImages("trip1", List.of());

        org.mockito.Mockito.verifyNoInteractions(storage);
    }
}
