package comictrip;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/")
public class WebResource {

    @Inject
    Template index;

    @Inject
    Template trip;

    @Inject
    TripService tripService;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance home() {
        List<TripService.TripData> pastTrips = tripService.listTrips();

        return index
            .data("pageTitle", "Comic Trip | Mission Control")
            .data("pastTrips", pastTrips);
    }

    @GET
    @Path("/trips/{tripId:[a-zA-Z0-9]+}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance trip(@PathParam("tripId") String tripId) {
        TripService.TripData tripData = tripService.getTrip(tripId);

        String title = tripData != null && tripData.title() != null
            ? tripData.title() : "Trip " + tripId;
        List<TripService.PictureData> pictures = tripData != null
            ? tripData.pictures() : List.of();

        return trip
            .data("pageTitle", "Comic Trip | " + title)
            .data("tripId", tripId)
            .data("tripTitle", title)
            .data("pictures", pictures);
    }
}
