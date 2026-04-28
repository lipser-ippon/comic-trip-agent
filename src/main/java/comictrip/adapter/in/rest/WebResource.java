package comictrip.adapter.in.rest;

import comictrip.domain.model.Trip;
import comictrip.domain.port.in.GetTripUseCase;
import comictrip.domain.port.in.ListTripsUseCase;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
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
    GetTripUseCase getTripUseCase;

    @Inject
    ListTripsUseCase listTripsUseCase;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance home() {
        List<Trip> pastTrips = listTripsUseCase.listTrips();

        return index
                .data("pageTitle", "Comic Trip | Mission Control")
                .data("pastTrips", pastTrips);
    }

    @GET
    @Path("/trips/{tripId:[a-zA-Z0-9]+}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance trip(@PathParam("tripId") String tripId) {
        Trip tripData = getTripUseCase.getTrip(tripId)
                .orElseThrow(() -> new NotFoundException("Trip not found: " + tripId));

        String title = tripData.title() != null ? tripData.title() : "Trip " + tripId;

        return trip
                .data("pageTitle", "Comic Trip | " + title)
                .data("tripId", tripId)
                .data("tripTitle", title)
                .data("pictures", tripData.pictures());
    }
}
