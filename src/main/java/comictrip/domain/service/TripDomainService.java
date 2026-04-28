package comictrip.domain.service;

import comictrip.domain.model.AnalysisResult;
import comictrip.domain.model.Trip;
import comictrip.domain.port.in.CreateTripUseCase;
import comictrip.domain.port.in.DeleteTripUseCase;
import comictrip.domain.port.in.GetTripUseCase;
import comictrip.domain.port.in.ListTripsUseCase;
import comictrip.domain.port.out.ImageStoragePort;
import comictrip.domain.port.out.TripRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TripDomainService implements CreateTripUseCase, GetTripUseCase, ListTripsUseCase, DeleteTripUseCase {

    private final TripRepository tripRepository;
    private final ImageStoragePort imageStoragePort;

    public TripDomainService(TripRepository tripRepository, ImageStoragePort imageStoragePort) {
        this.tripRepository = tripRepository;
        this.imageStoragePort = imageStoragePort;
    }

    @Override
    public void createTrip(String tripId, String title, List<AnalysisResult> results) {
        tripRepository.save(tripId, title, results);
    }

    @Override
    public Optional<Trip> getTrip(String tripId) {
        return tripRepository.findById(tripId);
    }

    @Override
    public List<Trip> listTrips() {
        return tripRepository.findAll();
    }

    @Override
    public void deleteTrip(String tripId) {
        tripRepository.findById(tripId).ifPresent(trip ->
                imageStoragePort.deleteImages(tripId, trip.pictures())
        );
        tripRepository.delete(tripId);
    }
}
