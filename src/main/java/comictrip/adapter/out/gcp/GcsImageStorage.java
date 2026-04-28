package comictrip.adapter.out.gcp;

import com.google.cloud.storage.Storage;
import comictrip.domain.model.Picture;
import comictrip.domain.port.out.ImageStoragePort;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class GcsImageStorage implements ImageStoragePort {

    private static final Logger LOGGER = Logger.getLogger(GcsImageStorage.class);

    private final Storage storage;
    private final String bucketName;
    private final String appName;
    private final String user;

    public GcsImageStorage(
            Storage storage,
            @ConfigProperty(name = "comic-trip.picture.bucket") String bucketName,
            @ConfigProperty(name = "comic-trip.app-name", defaultValue = "comic_trip_app") String appName,
            @ConfigProperty(name = "comic-trip.user", defaultValue = "comic_trip_user") String user) {
        this.storage = storage;
        this.bucketName = bucketName;
        this.appName = appName;
        this.user = user;
    }

    @Override
    public void deleteImages(String tripId, List<Picture> pictures) {
        for (Picture picture : pictures) {
            try {
                String gcsPath = String.format("%s/%s/%s/%s.png/0", appName, user, tripId, picture.fileName());
                boolean deleted = storage.delete(bucketName, gcsPath);
                if (deleted) {
                    LOGGER.infof("Suppression GCS réussie : %s/%s", bucketName, gcsPath);
                } else {
                    LOGGER.warnf("Objet GCS introuvable : %s/%s", bucketName, gcsPath);
                }
            } catch (Exception e) {
                LOGGER.errorf(e, "Erreur lors de la suppression GCS de %s pour le trip %s", picture.fileName(), tripId);
            }
        }
    }
}
