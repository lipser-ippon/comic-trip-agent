package comictrip;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.mockito.Mockito.when;

@QuarkusTest
public class WebResourceTest {

    @InjectMock
    TripService tripService;

    @Test
    public void testHomeEndpoint() {
        // Given: Mock the TripService to return a list of trips
        var trip1 = new TripService.TripData("trip1", "My First Trip", List.of(
            new TripService.PictureData("pic1", "Desc 1", "Location 1", "image/png", "/images/trip1/pic1", "POI 1")
        ));
        var trip2 = new TripService.TripData("trip2", "My Second Trip", List.of(
            new TripService.PictureData("pic2", "Desc 2", "Location 2", "image/png", "/images/trip2/pic2", "POI 2")
        ));
        when(tripService.listTrips()).thenReturn(List.of(trip1, trip2));

        // When & Then: We access the home page
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
        // Given: Mock the TripService to return a specific trip
        String tripId = "testTrip123";
        String tripTitle = "Adventure in Paris";
        var picture = new TripService.PictureData(
            "eiffel",
            "The Eiffel Tower",
            "Paris, France",
            "image/png",
            "/images/" + tripId + "/eiffel",
            "Champs de Mars"
        );
        var tripData = new TripService.TripData(tripId, tripTitle, List.of(picture));

        when(tripService.getTrip(tripId)).thenReturn(tripData);

        // When & Then: We access the trip detail page
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
        // Given: Mock the TripService to return null for a non-existent trip
        String tripId = "nonExistentTrip";
        when(tripService.getTrip(tripId)).thenReturn(null);

        // When & Then: We access a non-existent trip page
        given()
            .pathParam("tripId", tripId)
            .when().get("/trips/{tripId}")
            .then()
            .statusCode(200) // The resource handles null and returns a default page
            .contentType("text/html;charset=UTF-8")
            .body(
                containsString("Trip " + tripId) // It should display a default title
            );
    }
}
