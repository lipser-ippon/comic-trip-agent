package comictrip.adapter.out.gcp;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import comictrip.domain.exception.TripPersistenceException;
import comictrip.domain.model.AnalysisResult;
import comictrip.domain.model.Picture;
import comictrip.domain.model.Trip;
import comictrip.domain.port.out.TripRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class FirestoreTripRepository implements TripRepository {

    private static final Logger LOGGER = Logger.getLogger(FirestoreTripRepository.class);
    private static final String COLLECTION_NAME = "trips";

    private final Firestore firestore;

    public FirestoreTripRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void save(String tripId, String title, List<AnalysisResult> results) {
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(tripId);

        List<Map<String, Object>> pictures = results.stream()
                .map(result -> {
                    String imageName = result.image() != null ? result.image().name() : "";
                    Map<String, Object> pic = new HashMap<>();
                    pic.put("fileName", imageName);
                    pic.put("description", result.details() != null ? result.details().description() : "");
                    pic.put("location", result.details() != null ? result.details().location() : "");
                    pic.put("mimeType", result.image() != null ? result.image().mimeType() : "");
                    pic.put("imageUrl", String.format("/images/%s/%s", tripId, imageName));
                    pic.put("pointsOfInterest", result.pointsOfInterest() != null ? result.pointsOfInterest() : "");
                    return pic;
                }).toList();

        Map<String, Object> tripData = new HashMap<>();
        tripData.put("title", title);
        tripData.put("pictures", pictures);

        try {
            ApiFuture<WriteResult> future = docRef.set(tripData);
            future.get();
        } catch (InterruptedException e) {
            LOGGER.error("Sauvegarde Firestore interrompue", e);
            Thread.currentThread().interrupt();
            throw new TripPersistenceException("Interrupted while saving trip to Firestore", e);
        } catch (ExecutionException e) {
            LOGGER.error("Échec de la sauvegarde Firestore", e);
            throw new TripPersistenceException("Failed to save trip to Firestore", e);
        }
    }

    @Override
    public Optional<Trip> findById(String tripId) {
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(tripId);
        try {
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot doc = future.get();
            if (doc.exists()) {
                return Optional.of(toTrip(tripId, doc));
            }
            return Optional.empty();
        } catch (InterruptedException e) {
            LOGGER.error("Lecture Firestore interrompue", e);
            Thread.currentThread().interrupt();
            throw new TripPersistenceException("Interrupted while getting trip from Firestore", e);
        } catch (ExecutionException e) {
            LOGGER.error("Échec de la lecture Firestore", e);
            throw new TripPersistenceException("Failed to get trip from Firestore", e);
        }
    }

    @Override
    public List<Trip> findAll() {
        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME).get();
            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            List<Trip> trips = new ArrayList<>();
            for (QueryDocumentSnapshot doc : docs) {
                trips.add(toTrip(doc.getId(), doc));
            }
            return trips;
        } catch (InterruptedException e) {
            LOGGER.error("Listing Firestore interrompu", e);
            Thread.currentThread().interrupt();
            throw new TripPersistenceException("Interrupted while listing trips from Firestore", e);
        } catch (ExecutionException e) {
            LOGGER.error("Échec du listing Firestore", e);
            throw new TripPersistenceException("Failed to list trips from Firestore", e);
        }
    }

    @Override
    public void delete(String tripId) {
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(tripId);
        try {
            ApiFuture<WriteResult> future = docRef.delete();
            future.get();
            LOGGER.infof("Document Firestore supprimé pour le trip : %s", tripId);
        } catch (InterruptedException e) {
            LOGGER.error("Suppression Firestore interrompue", e);
            Thread.currentThread().interrupt();
            throw new TripPersistenceException("Interrupted while deleting trip from Firestore", e);
        } catch (ExecutionException e) {
            LOGGER.error("Échec de la suppression Firestore", e);
            throw new TripPersistenceException("Failed to delete trip from Firestore", e);
        }
    }

    private Trip toTrip(String tripId, DocumentSnapshot doc) {
        FirestoreTripDocument raw = doc.toObject(FirestoreTripDocument.class);
        String title = raw != null && raw.title() != null ? raw.title() : "Titre par défaut";
        List<Picture> pictures = new ArrayList<>();
        if (raw != null && raw.pictures() != null) {
            for (Map<String, Object> entry : raw.pictures()) {
                pictures.add(new Picture(
                        (String) entry.getOrDefault("fileName", ""),
                        (String) entry.getOrDefault("description", ""),
                        (String) entry.getOrDefault("location", ""),
                        (String) entry.getOrDefault("mimeType", ""),
                        (String) entry.getOrDefault("imageUrl", ""),
                        (String) entry.getOrDefault("pointsOfInterest", "")
                ));
            }
        }
        return new Trip(tripId, title, pictures);
    }

    private record FirestoreTripDocument(String title, List<Map<String, Object>> pictures) {
        // Required by Firestore deserialization
        @SuppressWarnings("unused")
        private FirestoreTripDocument() {
            this(null, null);
        }
    }
}
