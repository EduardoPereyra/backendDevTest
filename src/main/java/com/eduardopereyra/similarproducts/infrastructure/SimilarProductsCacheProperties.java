package com.eduardopereyra.similarproducts.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("similar-products.cache")
public record SimilarProductsCacheProperties(
        boolean enabled,
        Duration ttl,
        long maxSize
) {

    public SimilarProductsCacheProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("similar-products.cache.ttl must be positive");
        }
        if (maxSize < 1) {
            throw new IllegalArgumentException("similar-products.cache.max-size must be greater than zero");
        }
    }
}
