package comictrip.infrastructure;

import jakarta.enterprise.context.ApplicationScoped;
import org.sqids.Sqids;

import java.util.List;
import java.util.Random;

@ApplicationScoped
public class IdGenerator {

    private final Sqids sqids;
    private final Random random = new Random();

    public IdGenerator(Sqids sqids) {
        this.sqids = sqids;
    }

    public String generateId() {
        return sqids.encode(List.of(random.nextLong(0, Long.MAX_VALUE)));
    }
}
