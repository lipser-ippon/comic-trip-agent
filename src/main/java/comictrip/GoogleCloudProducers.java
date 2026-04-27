package comictrip;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.genai.Client;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class GoogleCloudProducers {

    private static final String DATABASE_ID = "comic-trip";

    @Produces
    @ApplicationScoped
    public Firestore firestore() {
        // This is the same logic that was in the TripService constructor
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
}
