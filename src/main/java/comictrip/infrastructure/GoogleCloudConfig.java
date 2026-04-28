package comictrip.infrastructure;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.genai.Client;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.sqids.Sqids;

@ApplicationScoped
public class GoogleCloudConfig {

    private static final String DATABASE_ID = "comic-trip";

    @Produces
    @ApplicationScoped
    public Firestore firestore() {
        return FirestoreOptions.newBuilder()
                .setDatabaseId(DATABASE_ID)
                .build()
                .getService();
    }

    @Produces
    @ApplicationScoped
    public Client genaiClient() {
        return Client.builder().build();
    }

    @Produces
    @ApplicationScoped
    public Sqids sqids() {
        return Sqids.builder().build();
    }
}
