package com.reviewsales.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cookie cookie,
        String authorHashSalt,
        String uploadDir,
        Collector collector,
        Collect collect,
        OAuth oauth) {

    public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {
    }

    public record Cookie(boolean secure) {
    }

    public record Collector(String baseUrl, Duration connectTimeout, Duration readTimeout, int maxRequests) {
    }

    public record Collect(Duration minInterval, int defaultLookbackMonths, boolean weeklyEnabled, String weeklyCron) {
    }

    public record OAuth(String redirectBase, String frontendBaseUrl, Provider kakao, Provider google) {
    }

    public record Provider(String clientId, String clientSecret, String authorizeUri, String tokenUri,
                           String userInfoUri) {

        public boolean enabled() {
            return clientId != null && !clientId.isBlank();
        }
    }
}
