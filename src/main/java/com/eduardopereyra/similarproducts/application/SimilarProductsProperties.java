package com.eduardopereyra.similarproducts.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("similar-products")
public record SimilarProductsProperties(int detailConcurrency) {

    public SimilarProductsProperties {
        if (detailConcurrency < 1) {
            throw new IllegalArgumentException(
                    "similar-products.detail-concurrency must be greater than zero"
            );
        }
    }
}
