package comictrip.domain.port.in;

import comictrip.domain.model.Trip;

import java.util.Optional;

public interface GetTripUseCase {
    Optional<Trip> getTrip(String tripId);
}
