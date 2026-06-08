package com.erumpay.payment.core.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origin-patterns:http://localhost:3000,http://localhost:5173,http://localhost:8080}")
    private String[] allowedOriginPatterns;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
    private String[] allowedMethods;

    @Value("${app.cors.allowed-headers:*}")
    private String[] allowedHeaders;

    @Value("${app.cors.exposed-headers:}")
    private String[] exposedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${app.cors.max-age:3600}")
    private long maxAge;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var corsRegistration = registry.addMapping("/**")
                .allowedOriginPatterns(trimmed(allowedOriginPatterns))
                .allowedMethods(trimmed(allowedMethods))
                .allowedHeaders(trimmed(allowedHeaders))
                .allowCredentials(allowCredentials)
                .maxAge(maxAge);

        String[] exposed = trimmed(exposedHeaders);
        if (exposed.length > 0) {
            corsRegistration.exposedHeaders(exposed);
        }
    }

    private String[] trimmed(String[] values) {
        return Arrays.stream(values)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }
}
