/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package comictrip;

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

        if (tripData == null) {
            throw new NotFoundException("Trip not found: " + tripId);
        }

        String title = tripData.title() != null ? tripData.title() : "Trip " + tripId;

        return trip
            .data("pageTitle", "Comic Trip | " + title)
            .data("tripId", tripId)
            .data("tripTitle", title)
            .data("pictures", tripData.pictures());
    }
}
