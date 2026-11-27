package com.autoscientist.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .filter(origin -> !"*".equals(origin))
                .filter(this::isAbsoluteHttpOrigin)
                .toList();
        if (origins.isEmpty()) {
            throw new IllegalStateException(
                    "No valid CORS origins configured. Set APP_CORS_ALLOWED_ORIGINS to exact HTTP(S) frontend origin(s).");
        }
        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "PATCH",
                        "DELETE", "OPTIONS")
        );

        configuration.setAllowedHeaders(
                Arrays.asList("Authorization", "Content-Type",
                        "Accept", "X-Requested-With", "Origin")
        );

        configuration.setExposedHeaders(
                Arrays.asList("Authorization", "X-Request-Id",
                        "X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After")
        );

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /** Never allow wildcards or attacker-influenced origin strings. */
    private boolean isAbsoluteHttpOrigin(String origin) {
        try {
            URI uri = URI.create(origin);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()));
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring malformed configured CORS origin: {}", origin);
            return false;
        }
    }
}
