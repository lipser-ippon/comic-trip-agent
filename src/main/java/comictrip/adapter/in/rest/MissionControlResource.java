package comictrip.adapter.in.rest;

import comictrip.domain.model.AnalysisResult;
import comictrip.domain.port.in.CreateTripUseCase;
import comictrip.domain.port.in.DeleteTripUseCase;
import comictrip.domain.port.out.ImageAnalysisPort;
import comictrip.domain.port.out.TitleGenerationPort;
import comictrip.infrastructure.IdGenerator;
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

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Path("/api")
public class MissionControlResource {

    private static final Logger LOGGER = Logger.getLogger(MissionControlResource.class);

    @Inject
    ImageAnalysisPort imageAnalysisPort;

    @Inject
    TitleGenerationPort titleGenerationPort;

    @Inject
    CreateTripUseCase createTripUseCase;

    @Inject
    DeleteTripUseCase deleteTripUseCase;

    @Inject
    IdGenerator idGenerator;

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadFiles(@RestForm("file") List<FileUpload> files) {
        if (files == null || files.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("No files uploaded").build();
        }

        List<comictrip.domain.model.UploadedImage> filesToProcess;
        try {
            filesToProcess = files.stream()
                    .map(file -> {
                        try {
                            String fileName = file.fileName();
                            byte[] fileBytes = Files.readAllBytes(file.filePath());
                            String mimeType = file.contentType();
                            return new comictrip.domain.model.UploadedImage(fileName, fileBytes, mimeType);
                        } catch (IOException e) {
                            LOGGER.error("Failed to read file", e);
                            throw new RuntimeException("Failed to read file", e);
                        }
                    })
                    .toList();
        } catch (Exception e) {
            LOGGER.error("Error during file ingestion", e);
            return Response.serverError().entity("Error during file ingestion").build();
        }

        String tripId = idGenerator.generateId();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = filesToProcess.stream()
                    .map(fileData -> CompletableFuture.supplyAsync(
                            () -> imageAnalysisPort.analyze(fileData.imageBytes(), fileData.mimeType(), tripId),
                            executor))
                    .toList();

            List<AnalysisResult> analysisResults = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            List<String> descriptions = analysisResults.stream()
                    .map(r -> r.details() != null && r.details().description() != null ? r.details().description() : "")
                    .toList();

            String tripTitle = titleGenerationPort.generateTitle(descriptions);

            createTripUseCase.createTrip(tripId, tripTitle, analysisResults);

            return Response.ok(Map.of("tripId", tripId, "title", tripTitle, "pictures", analysisResults)).build();
        } catch (Exception e) {
            LOGGER.error("Parallel execution failed", e);
            return Response.serverError().entity("Parallel execution failed").build();
        }
    }

    @DELETE
    @Path("/trips/{tripId}")
    public Response deleteTrip(@jakarta.ws.rs.PathParam("tripId") String tripId) {
        deleteTripUseCase.deleteTrip(tripId);
        return Response.noContent().build();
    }
}
