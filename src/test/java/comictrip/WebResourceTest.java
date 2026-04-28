package comictrip;

import comictrip.domain.model.Picture;
import comictrip.domain.model.Trip;
import comictrip.domain.port.in.GetTripUseCase;
import comictrip.domain.port.in.ListTripsUseCase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.Mockito.when;

@QuarkusTest
public class WebResourceTest {

    @InjectMock
    GetTripUseCase getTripUseCase;

    @InjectMock
    ListTripsUseCase listTripsUseCase;

    @Test
    public void testHomeEndpoint() {
        var trip1 = new Trip("trip1", "My First Trip", List.of(
                new Picture("pic1", "Desc 1", "Location 1", "image/png", "/images/trip1/pic1", "POI 1")
        ));
        var trip2 = new Trip("trip2", "My Second Trip", List.of(
                new Picture("pic2", "Desc 2", "Location 2", "image/png", "/images/trip2/pic2", "POI 2")
        ));
        when(listTripsUseCase.listTrips()).thenReturn(List.of(trip1, trip2));

        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .contentType("text/html;charset=UTF-8")
                .body(
                        containsString("My First Trip"),
                        containsString("My Second Trip")
                );
    }

    @Test
    public void testTripEndpoint() {
        String tripId = "testTrip123";
        String tripTitle = "Adventure in Paris";
        var picture = new Picture(
                "eiffel", "The Eiffel Tower", "Paris, France",
                "image/png", "/images/" + tripId + "/eiffel", "Champs de Mars"
        );
        var tripData = new Trip(tripId, tripTitle, List.of(picture));

        when(getTripUseCase.getTrip(tripId)).thenReturn(Optional.of(tripData));

        given()
                .pathParam("tripId", tripId)
                .when().get("/trips/{tripId}")
                .then()
                .statusCode(200)
                .contentType("text/html;charset=UTF-8")
                .body(
                        containsString(tripTitle),
                        containsString("The Eiffel Tower"),
                        containsString("Paris, France")
                );
    }

    @Test
    public void testTripEndpoint_NotFound() {
        String tripId = "nonExistentTrip";
        when(getTripUseCase.getTrip(tripId)).thenReturn(Optional.empty());

        given()
                .pathParam("tripId", tripId)
                .when().get("/trips/{tripId}")
                .then()
                .statusCode(404);
    }
}
