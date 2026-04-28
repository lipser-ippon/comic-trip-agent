package comictrip.domain.port.out;

import java.util.List;

public interface TitleGenerationPort {
    String generateTitle(List<String> descriptions);
}
