package comictrip.domain.exception;

public class TripPersistenceException extends RuntimeException {

    public TripPersistenceException(String message) {
        super(message);
    }

    public TripPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
