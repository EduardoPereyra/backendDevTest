package com.eduardopereyra.similarproducts.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("clients.products")
public record ProductsClientProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration responseTimeout,
        int maxConnections,
        Duration pendingAcquireTimeout
) {
    public ProductsClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("clients.products.base-url must not be blank");
        }
        requirePositive(connectTimeout, "connect-timeout");
        requirePositive(responseTimeout, "response-timeout");
        requirePositive(pendingAcquireTimeout, "pending-acquire-timeout");
        if (maxConnections < 1) {
            throw new IllegalArgumentException("clients.products.max-connections must be greater than zero");
        }
    }

    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("clients.products." + property + " must be positive");
        }
    }
}
