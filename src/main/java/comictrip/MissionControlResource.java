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

import com.google.genai.Client;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jspecify.annotations.Nullable;
import org.sqids.Sqids;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Path("/api")
public class MissionControlResource {

    private static final Logger LOGGER = Logger.getLogger(MissionControlResource.class);

    @Inject
    ComicTripAnalyzer analyzer;

    @Inject
    TripService tripService;

    @Inject
    Client client;

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadFiles(@RestForm("file") List<FileUpload> files) {
        if (files == null || files.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("No files uploaded").build();
        }

        List<ComicOutput.Image> filesToProcess;
        try {
            filesToProcess = files.stream()
                    .map(file -> {
                        try {
                            String fileName = file.fileName();
                            byte[] fileBytes = Files.readAllBytes(file.filePath());
                            String mimeType = file.contentType();
                            return new ComicOutput.Image(fileName, fileBytes, mimeType);
                        } catch (IOException e) {
                            LOGGER.error("Failed to read file", e);
                            throw new RuntimeException("Failed to read file", e);
                        }
                    })
                    .toList();
        } catch (Exception e) {
            LOGGER.error("Error during file ingestion", e);
            return Response.serverError().entity("Error during file ingestion: " + e.getMessage()).build();
        }

        String tripId = generateId();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = filesToProcess.stream()
                    .map(fileData -> CompletableFuture.supplyAsync(
                            () -> analyzer.analyzeComic(fileData.imageBytes(), fileData.mimeType(), tripId),
                            executor))
                    .toList();

            var comicOutputs = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            String tripTitle = createTripName(comicOutputs);

            tripService.saveTrip(tripId, tripTitle, comicOutputs);

            return Response.ok(Map.of("tripId", tripId, "title", tripTitle, "pictures", comicOutputs)).build();
        } catch (Exception e) {
            LOGGER.error("Parallel execution failed", e);
            return Response.serverError().entity("Parallel execution failed: " + e.getMessage()).build();
        }
    }

    @DELETE
    @Path("/trips/{tripId}")
    public Response deleteTrip(@jakarta.ws.rs.PathParam("tripId") String tripId) {
        tripService.deleteTrip(tripId);
        return Response.noContent().build();
    }

    private static String generateId() {
        Sqids sqids = Sqids.builder().build();
        return sqids.encode(List.of(new Random().nextLong(0, Integer.MAX_VALUE)));
    }

    private @Nullable String createTripName(List<ComicOutput> results) {
        String combinedDescriptions = results.stream()
                .map(r -> r.details() != null && r.details().description() != null ? r.details().description() : "")
                .collect(java.util.stream.Collectors.joining("\n"));

        String tripTitle = client.models.generateContent(
                "gemini-2.5-flash-lite", """
                         Donne un titre court et accrocheur, dans le style bande dessinée (max 5 mots), pour un voyage basé sur ces descriptions de photos.
                        Affiche UNIQUEMENT le titre :
                        """ + combinedDescriptions,
                com.google.genai.types.GenerateContentConfig.builder().build()
        ).text();
        return tripTitle;
    }
}
