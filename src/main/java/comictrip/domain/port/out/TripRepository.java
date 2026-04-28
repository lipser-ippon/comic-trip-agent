package comictrip.domain.port.out;

import comictrip.domain.model.AnalysisResult;
import comictrip.domain.model.Trip;

import java.util.List;
import java.util.Optional;

public interface TripRepository {
    void save(String tripId, String title, List<AnalysisResult> results);
    Optional<Trip> findById(String tripId);
    List<Trip> findAll();
    void delete(String tripId);
}
