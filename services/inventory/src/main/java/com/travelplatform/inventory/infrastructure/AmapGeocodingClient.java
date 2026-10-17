package com.travelplatform.inventory.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.travelplatform.inventory.application.seed.SeedDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class AmapGeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(AmapGeocodingClient.class);

    private final RestClient restClient;
    private final String webServiceKey;
    private final long minIntervalMs;
    private long lastCallNanos = 0L;

    public AmapGeocodingClient(RestClient.Builder builder,
                               @Value("${amap.web-service-key:}") String webServiceKey,
                               @Value("${seed.amap-min-interval-ms:250}") long minIntervalMs) {
        this.restClient = builder.build();
        this.webServiceKey = webServiceKey;
        this.minIntervalMs = Math.max(0, minIntervalMs);
    }

    public boolean isConfigured() {
        return webServiceKey != null && !webServiceKey.isBlank();
    }

    public Optional<SeedDtos.SeedCoordinate> geocode(String name, String city) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        throttle();
        try {
            AmapResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("restapi.amap.com")
                            .path("/v3/geocode/geo")
                            .queryParam("key", webServiceKey)
                            .queryParam("address", name)
                            .queryParam("city", city)
                            .build())
                    .retrieve()
                    .body(AmapResponse.class);
            if (response == null || !"1".equals(response.status())
                    || response.geocodes() == null || response.geocodes().isEmpty()) {
                return Optional.empty();
            }
            String location = response.geocodes().get(0).location();
            if (location == null || !location.contains(",")) {
                return Optional.empty();
            }
            String[] parts = location.split(",", 2);
            return Optional.of(new SeedDtos.SeedCoordinate(
                    new BigDecimal(parts[1]), new BigDecimal(parts[0])));
        } catch (Exception e) {
            log.warn("amap geocoding failed for {} / {}: {}", name, city, e.toString());
            return Optional.empty();
        }
    }

    private void throttle() {
        if (minIntervalMs <= 0) {
            return;
        }
        long now = System.nanoTime();
        long waitNanos = minIntervalMs * 1_000_000L - (now - lastCallNanos);
        if (waitNanos > 0) {
            try {
                Thread.sleep(waitNanos / 1_000_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallNanos = System.nanoTime();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AmapResponse(String status, String count, List<Geocode> geocodes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Geocode(String location) {
    }
}
