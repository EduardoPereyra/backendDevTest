package com.eduardopereyra.similarproducts.api;

import com.eduardopereyra.similarproducts.SimilarProductsApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        classes = SimilarProductsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ProductsUnavailableApiIT {

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void productsApiProperties(DynamicPropertyRegistry registry) {
        registry.add("clients.products.base-url", () -> "http://127.0.0.1:1");
        registry.add("clients.products.connect-timeout", () -> "100ms");
    }

    @Test
    void mapsConnectionFailureToBadGateway() {
        webTestClient.get()
                .uri("/product/1/similar")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.status").isEqualTo(502)
                .jsonPath("$.message").isEqualTo("Products API is unavailable");
    }
}
