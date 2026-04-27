package comictrip;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/images")
public class GcsProxyResource {

    @Inject
    Storage storage;

    @ConfigProperty(name = "comic-trip.picture.bucket")
    String bucketName;

    @ConfigProperty(name = "comic-trip.app-name", defaultValue = "comic_trip_app")
    String appName;

    @ConfigProperty(name = "comic-trip.user", defaultValue = "comic_trip_user")
    String user;

    @GET
    @Path("/{tripId}/{imageName}")
    public Uni<Response> getImage(
        @PathParam("tripId") String tripId,
        @PathParam("imageName") String imageName
    ) {
        return Uni.createFrom().item(() -> {
            String gcsPath = String.format("%s/%s/%s/%s.png/0", appName, user, tripId, imageName);
            Blob blob = storage.get(bucketName, gcsPath);

            if (blob == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            // Streaming direct du contenu vers la réponse HTTP
            Response.ResponseBuilder response = Response.ok((jakarta.ws.rs.core.StreamingOutput) output -> {
                blob.downloadTo(output);
            });

            return response
                .type(blob.getContentType())
                .header("Cache-Control", "public, max-age=31536000") // Cache 1 an
                .build();
        });
    }
}
