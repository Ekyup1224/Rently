package mn.innex.stay.security;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless bearer-token security.
 *
 * <p>Authorization is expressed two ways: coarse URL rules here (everything under
 * {@code /api/v1/admin/**} needs SUPER_ADMIN), and {@code @PreAuthorize} on
 * service methods for anything finer, such as owning the resource being touched.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

    private final JwtProperties jwtProperties;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtProperties jwtProperties, CorsProperties corsProperties) {
        this.jwtProperties = jwtProperties;
        this.corsProperties = corsProperties;
        byte[] secret = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < JwtProperties.MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.security.jwt.secret must be at least "
                    + JwtProperties.MIN_SECRET_BYTES + " bytes; got " + secret.length);
        }
    }

    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                // No cookies are used for auth, so there is no CSRF surface to protect.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/otp/request",
                                "/api/v1/auth/otp/verify",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("SUPER_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "unauthenticated", "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "forbidden", "You do not have permission to perform this action")));
        return http.build();
    }

    /**
     * Error bodies produced inside the filter chain are hand-written rather than
     * serialized, so they stay identical to {@code ErrorResponse} without dragging
     * an ObjectMapper into the security layer.
     */
    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"timestamp\":\"" + java.time.Instant.now() + "\"}");
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.CLAIM_ROLES);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private SecretKeySpec secretKey() {
        return new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost 12: roughly 250ms per hash on current hardware, which keeps offline
        // cracking expensive without making login feel slow.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT, "X-Requested-With", "Accept-Language"));
        configuration.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
