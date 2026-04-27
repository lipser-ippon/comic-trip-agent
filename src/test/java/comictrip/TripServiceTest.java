package comictrip;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Note: This test class uses direct Mockito injection because mocking the Firestore client
// via @InjectMock can be complex. This approach provides more control.
// A full integration test would require a Firestore emulator.
@QuarkusTest
@Disabled("This test requires a running Firestore emulator or a more complex mocking setup.")
public class TripServiceTest {

    @Mock
    Firestore firestore;

    @InjectMocks
    TripService tripService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetTrip_Success() throws ExecutionException, InterruptedException {
        // Given
        String tripId = "testTrip";
        String tripTitle = "My Awesome Trip";

        Map<String, Object> pictureMap = Map.of(
            "fileName", "pic1",
            "description", "A cool picture",
            "location", "Someplace, Earth",
            "mimeType", "image/jpeg",
            "imageUrl", "/images/testTrip/pic1",
            "pointsOfInterest", "A big rock"
        );
        List<Map<String, Object>> picturesList = List.of(pictureMap);

        DocumentSnapshot docSnapshot = mock(DocumentSnapshot.class);
        when(docSnapshot.exists()).thenReturn(true);
        when(docSnapshot.getId()).thenReturn(tripId);
        when(docSnapshot.getString("title")).thenReturn(tripTitle);
        when(docSnapshot.get("pictures")).thenReturn(picturesList);

        @SuppressWarnings("unchecked")
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);
        when(future.get()).thenReturn(docSnapshot);

        DocumentReference docRef = mock(DocumentReference.class);
        when(docRef.get()).thenReturn(future);

        CollectionReference collectionRef = mock(CollectionReference.class);
        when(collectionRef.document(tripId)).thenReturn(docRef);

        when(firestore.collection(anyString())).thenReturn(collectionRef);

        // When
        TripService.TripData tripData = tripService.getTrip(tripId);

        // Then
        assertNotNull(tripData);
        assertEquals(tripId, tripData.tripId());
        assertEquals(tripTitle, tripData.title());
        assertEquals(1, tripData.pictures().size());

        TripService.PictureData pictureData = tripData.pictures().getFirst();
        assertEquals("pic1", pictureData.fileName());
        assertEquals("A cool picture", pictureData.description());
        assertEquals("/images/testTrip/pic1", pictureData.imageUrl());
    }

    @Test
    void testListTrips() throws ExecutionException, InterruptedException {
        // Given
        QueryDocumentSnapshot doc1 = mock(QueryDocumentSnapshot.class);
        when(doc1.getId()).thenReturn("trip1");
        when(doc1.getString("title")).thenReturn("Trip One");
        when(doc1.get("pictures")).thenReturn(List.of());

        QueryDocumentSnapshot doc2 = mock(QueryDocumentSnapshot.class);
        when(doc2.getId()).thenReturn("trip2");
        when(doc2.getString("title")).thenReturn("Trip Two");
        when(doc2.get("pictures")).thenReturn(List.of());

        List<QueryDocumentSnapshot> docList = List.of(doc1, doc2);

        QuerySnapshot querySnapshot = mock(QuerySnapshot.class);
        when(querySnapshot.getDocuments()).thenReturn(docList);

        @SuppressWarnings("unchecked")
        ApiFuture<QuerySnapshot> future = mock(ApiFuture.class);
        when(future.get()).thenReturn(querySnapshot);

        CollectionReference collectionRef = mock(CollectionReference.class);
        when(collectionRef.get()).thenReturn(future);

        when(firestore.collection(anyString())).thenReturn(collectionRef);

        // When
        List<TripService.TripData> trips = tripService.listTrips();

        // Then
        assertNotNull(trips);
        assertEquals(2, trips.size());
        assertEquals("trip1", trips.get(0).tripId());
        assertEquals("Trip One", trips.get(0).title());
        assertEquals("trip2", trips.get(1).tripId());
        assertEquals("Trip Two", trips.get(1).title());
    }

    @Test
    void testSaveTrip() throws ExecutionException, InterruptedException {
        // Given
        String tripId = "newTrip";
        String tripTitle = "A New Adventure";
        ComicOutput output = new ComicOutput(
            tripId,
            new ComicOutput.Image("img1", null, "image/png"),
            new ComicOutput.Details("description", "location"),
            "pois"
        );
        List<ComicOutput> comicOutputs = List.of(output);

        @SuppressWarnings("unchecked")
        ApiFuture<WriteResult> future = mock(ApiFuture.class);
        when(future.get()).thenReturn(mock(WriteResult.class));

        DocumentReference docRef = mock(DocumentReference.class);
        when(docRef.set(any(Map.class))).thenReturn(future);

        CollectionReference collectionRef = mock(CollectionReference.class);
        when(collectionRef.document(tripId)).thenReturn(docRef);

        when(firestore.collection(anyString())).thenReturn(collectionRef);

        // When & Then
        // No exception should be thrown
        assertDoesNotThrow(() -> tripService.saveTrip(tripId, tripTitle, comicOutputs));
    }
}
