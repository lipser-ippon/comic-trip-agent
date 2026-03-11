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
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.jspecify.annotations.Nullable;
import org.sqids.Sqids;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Path("/api/mission-control")
public class MissionControlResource {

    private static final Logger LOGGER = Logger.getLogger(MissionControlResource.class);

    @Inject
    ComicTripAnalyzer analyzer;

    @Inject
    TripService tripService;

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadFiles(MultipartFormDataInput input) {
        List<InputPart> fileParts = input.getFormDataMap().get("file");
        if (fileParts == null || fileParts.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("No files uploaded").build();
        }

        List<ComicOutput.Image> filesToProcess;
        try {
            filesToProcess = fileParts.stream()
                .map(part -> {
                    try {
                        String contentDisposition = part.getHeaders().getFirst("Content-Disposition");
                        String fileName = "unknown";
                        if (contentDisposition != null) {
                            for (String cd : contentDisposition.split(";")) {
                                if (cd.trim().startsWith("filename")) {
                                    fileName = cd.split("=")[1].trim().replace("\"", "");
                                }
                            }
                        }
                        byte[] fileBytes = part.getBody(byte[].class, null);
                        String mimeType = fileName.endsWith(".png") ? "image/png"
                            : "image/jpeg";
                        return new ComicOutput.Image(fileName, fileBytes, mimeType);
                    } catch (IOException e) {
                        LOGGER.error("Failed to read file part", e);
                        throw new RuntimeException("Failed to read file part", e);
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

    private static String generateId() {
        Sqids sqids = Sqids.builder().build();
        String tripId = sqids.encode(List.of(new Random().nextLong(0, Integer.MAX_VALUE)));
        return tripId;
    }

    private static @Nullable String createTripName(List<ComicOutput> results) {
        String combinedDescriptions = results.stream()
            .map(r -> r.details() != null && r.details().description() != null ? r.details().description() : "")
            .collect(java.util.stream.Collectors.joining("\n"));

        String tripTitle = "Unknown Adventure";
        try {
            var client = Client.builder().build();
            tripTitle = client.models.generateContent(
                "gemini-3.1-flash-lite-preview", """
                    Give a short, catchy, comic-book style title (max 5 words)
                    for a trip based on these photo descriptions.
                    Output ONLY the title:
                    """ + combinedDescriptions,
                com.google.genai.types.GenerateContentConfig.builder().build()
            ).text();
        } catch (Exception ex) {
            LOGGER.error("Failed to generate trip title", ex);
        }
        return tripTitle;
    }
}
