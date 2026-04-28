package comictrip.domain.port.in;

import comictrip.domain.model.AnalysisResult;

import java.util.List;

public interface CreateTripUseCase {
    void createTrip(String tripId, String title, List<AnalysisResult> results);
}
