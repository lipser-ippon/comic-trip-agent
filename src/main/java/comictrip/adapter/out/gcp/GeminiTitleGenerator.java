package comictrip.adapter.out.gcp;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import comictrip.domain.port.out.TitleGenerationPort;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class GeminiTitleGenerator implements TitleGenerationPort {

    private static final Logger LOGGER = Logger.getLogger(GeminiTitleGenerator.class);

    private final Client client;

    public GeminiTitleGenerator(Client client) {
        this.client = client;
    }

    @Override
    public String generateTitle(List<String> descriptions) {
        String combinedDescriptions = String.join("\n", descriptions);
        try {
            return client.models.generateContent(
                    "gemini-2.5-flash-lite", """
                             Donne un titre court et accrocheur, dans le style bande dessinée (max 5 mots), pour un voyage basé sur ces descriptions de photos.
                            Affiche UNIQUEMENT le titre :
                            """ + combinedDescriptions,
                    GenerateContentConfig.builder().build()
            ).text();
        } catch (Exception e) {
            LOGGER.error("Échec de la génération du titre du voyage", e);
            return "Voyage sans titre";
        }
    }
}
