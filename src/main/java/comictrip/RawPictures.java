package comictrip;

import java.util.List;
import java.util.Map;

public record RawPictures(String title, List<Map<String, Object>> pictures) {
}
