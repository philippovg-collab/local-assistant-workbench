package com.example.demo.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String DEFAULT_ALLOWED_ORIGINS =
        "http://127.0.0.1:5173,http://localhost:5173,http://127.0.0.1:4173,http://localhost:4173";

    private final String[] allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins:" + DEFAULT_ALLOWED_ORIGINS + "}") String allowedOrigins) {
        this.allowedOrigins = parseAllowedOrigins(allowedOrigins);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
    }

    static String[] parseAllowedOrigins(String allowedOrigins) {
        String[] origins = allowedOrigins == null
            ? new String[0]
            : Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
        if (Arrays.asList(origins).contains("*")) {
            throw new IllegalStateException(
                "app.cors.allowed-origins cannot contain '*' while allowCredentials=true"
            );
        }
        return origins;
    }
}
