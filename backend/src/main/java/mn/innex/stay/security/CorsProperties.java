package mn.innex.stay.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Browser origins allowed to call the API: the Vite portal and the Next.js PWA. */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
