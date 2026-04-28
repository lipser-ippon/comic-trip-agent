package comictrip.domain.port.in;

import comictrip.domain.model.Trip;

import java.util.List;

public interface ListTripsUseCase {
    List<Trip> listTrips();
}
