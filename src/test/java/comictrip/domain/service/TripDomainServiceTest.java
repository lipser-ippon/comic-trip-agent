package comictrip.domain.service;

import comictrip.domain.model.AnalysisResult;
import comictrip.domain.model.ImageAnalysis;
import comictrip.domain.model.Picture;
import comictrip.domain.model.Trip;
import comictrip.domain.model.UploadedImage;
import comictrip.domain.port.out.ImageStoragePort;
import comictrip.domain.port.out.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripDomainServiceTest {

    @Mock
    TripRepository tripRepository;

    @Mock
    ImageStoragePort imageStoragePort;

    TripDomainService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TripDomainService(tripRepository, imageStoragePort);
    }

    @Test
    void createTrip_delegatesToRepository() {
        List<AnalysisResult> results = List.of(
                new AnalysisResult("trip1", new UploadedImage("img1", null, "image/png"),
                        new ImageAnalysis("desc", "loc"), "POIs")
        );

        service.createTrip("trip1", "Mon Titre", results);

        verify(tripRepository).save("trip1", "Mon Titre", results);
    }

    @Test
    void getTrip_returnsEmptyWhenNotFound() {
        when(tripRepository.findById("unknown")).thenReturn(Optional.empty());

        Optional<Trip> result = service.getTrip("unknown");

        assertTrue(result.isEmpty());
    }

    @Test
    void getTrip_returnsTripWhenFound() {
        Trip trip = new Trip("trip1", "Titre", List.of());
        when(tripRepository.findById("trip1")).thenReturn(Optional.of(trip));

        Optional<Trip> result = service.getTrip("trip1");

        assertTrue(result.isPresent());
        assertEquals("Titre", result.get().title());
    }

    @Test
    void listTrips_delegatesToRepository() {
        List<Trip> trips = List.of(new Trip("t1", "T1", List.of()), new Trip("t2", "T2", List.of()));
        when(tripRepository.findAll()).thenReturn(trips);

        List<Trip> result = service.listTrips();

        assertEquals(2, result.size());
    }

    @Test
    void deleteTrip_deletesImagesAndFirestoreDoc() {
        Picture picture = new Picture("img1", "desc", "loc", "image/png", "/images/t1/img1", "POIs");
        Trip trip = new Trip("trip1", "Titre", List.of(picture));
        when(tripRepository.findById("trip1")).thenReturn(Optional.of(trip));

        service.deleteTrip("trip1");

        verify(imageStoragePort).deleteImages("trip1", List.of(picture));
        verify(tripRepository).delete("trip1");
    }

    @Test
    void deleteTrip_whenTripNotFound_onlyDeletesFirestoreDoc() {
        when(tripRepository.findById("missing")).thenReturn(Optional.empty());

        service.deleteTrip("missing");

        verify(imageStoragePort, never()).deleteImages(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(tripRepository).delete("missing");
    }
}
