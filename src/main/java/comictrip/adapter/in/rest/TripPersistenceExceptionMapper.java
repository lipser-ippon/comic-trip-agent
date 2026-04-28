package comictrip.adapter.in.rest;

import comictrip.domain.exception.TripPersistenceException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class TripPersistenceExceptionMapper implements ExceptionMapper<TripPersistenceException> {

    private static final Logger LOGGER = Logger.getLogger(TripPersistenceExceptionMapper.class);

    @Override
    public Response toResponse(TripPersistenceException exception) {
        LOGGER.error("Erreur de persistance", exception);
        return Response.serverError().entity("Une erreur est survenue lors de l'accès aux données").build();
    }
}
