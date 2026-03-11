/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package comictrip;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class TripService {

    private static final Logger LOGGER = Logger.getLogger(TripService.class);

    private static final String DATABASE_ID = "comic-trip";
    private static final String COLLECTION_NAME = "trips";

    private final Firestore firestore;

    public TripService() {
        this.firestore = FirestoreOptions.newBuilder()
            .setDatabaseId(DATABASE_ID)
            .build()
            .getService();
    }

    /**
     * Saves a trip (title + list of pictures) to Firestore.
     */
    public void saveTrip(String tripId, String title, List<ComicOutput> comicOutputs) {
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(tripId);

        List<Map<String, Object>> pictures = new ArrayList<>();
        for (ComicOutput output : comicOutputs) {
            Map<String, Object> pic = new HashMap<>();
            pic.put("fileName", output.image() != null ? output.image().name() : "");
            pic.put("description", output.details() != null ? output.details().description() : "");
            pic.put("location", output.details() != null ? output.details().location() : "");
            pic.put("mimeType", output.image() != null ? output.image().mimeType() : "");
            pic.put("imageUrl", output.image() != null ? output.image().name() : "");
            pic.put("pointsOfInterest", output.pointsOfInterest() != null ? output.pointsOfInterest() : "");
            pictures.add(pic);
        }

        Map<String, Object> tripData = new HashMap<>();
        tripData.put("title", title);
        tripData.put("pictures", pictures);

        try {
            ApiFuture<WriteResult> result = docRef.set(tripData);
            result.get(); // block until write completes
        } catch (InterruptedException e) {
            LOGGER.error("Failed to save trip to Firestore due to interruption", e);
            Thread.currentThread().interrupt();
            throw new FirestorePersistenceException("Interrupted while saving trip to Firestore", e);
        } catch (ExecutionException e) {
            LOGGER.error("Failed to save trip to Firestore", e);
            throw new FirestorePersistenceException("Failed to save trip to Firestore", e);
        }
    }

    /**
     * Returns a single trip's data for the trip detail page.
     * Returns null if the trip doesn't exist.
     */
    public TripData getTrip(String tripId) {
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(tripId);
        try {
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot doc = future.get();
            if (doc.exists()) {
                return toTripData(tripId, doc);
            }
            return null;
        } catch (InterruptedException e) {
            LOGGER.error("Failed to get trip from Firestore due to interruption", e);
            Thread.currentThread().interrupt();
            throw new FirestorePersistenceException("Interrupted while getting trip from Firestore", e);
        } catch (ExecutionException e) {
            LOGGER.error("Failed to get trip from Firestore", e);
            throw new FirestorePersistenceException("Failed to get trip from Firestore", e);
        }
    }

    /**
     * Returns all trips for the home page gallery.
     */
    public List<TripData> listTrips() {
        try {
            ApiFuture<QuerySnapshot> future = firestore.collection(COLLECTION_NAME).get();
            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            List<TripData> trips = new ArrayList<>();
            for (QueryDocumentSnapshot doc : docs) {
                trips.add(toTripData(doc.getId(), doc));
            }
            return trips;
        } catch (InterruptedException e) {
            LOGGER.error("Failed to list trips from Firestore due to interruption", e);
            Thread.currentThread().interrupt();
            throw new FirestorePersistenceException("Interrupted while listing trips from Firestore", e);
        } catch (ExecutionException e) {
            LOGGER.error("Failed to list trips from Firestore", e);
            throw new FirestorePersistenceException("Failed to list trips from Firestore", e);
        }
    }

    @SuppressWarnings("unchecked")
    private TripData toTripData(String tripId, DocumentSnapshot doc) {
        String title = doc.getString("title");
        List<Map<String, Object>> rawPictures = (List<Map<String, Object>>) doc.get("pictures");

        List<PictureData> pictures = new ArrayList<>();
        if (rawPictures != null) {
            for (Map<String, Object> raw : rawPictures) {
                pictures.add(new PictureData(
                    (String) raw.getOrDefault("fileName", ""),
                    (String) raw.getOrDefault("description", ""),
                    (String) raw.getOrDefault("location", ""),
                    (String) raw.getOrDefault("mimeType", ""),
                    (String) raw.getOrDefault("imageUrl", ""),
                    (String) raw.getOrDefault("pointsOfInterest", "")
                ));
            }
        }
        return new TripData(tripId, title, pictures);
    }

    // Data classes for template consumption
    public record TripData(String tripId, String title, List<PictureData> pictures) {
        public String firstImageUrl() {
            if (pictures != null && !pictures.isEmpty()) {
                return pictures.getFirst().imageUrl();
            }
            return "";
        }
    }

    public record PictureData(
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
}
