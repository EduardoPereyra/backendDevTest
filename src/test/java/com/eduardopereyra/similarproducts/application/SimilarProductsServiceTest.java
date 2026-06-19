package com.eduardopereyra.similarproducts.application;

import com.eduardopereyra.similarproducts.domain.ProductDetail;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarProductsServiceTest {

    @Test
    void fetchesDetailsConcurrentlyWhilePreservingSimilarityOrder() {
        Map<String, ProductDetail> products = Map.of(
                "2", product("2"),
                "3", product("3"),
                "4", product("4")
        );
        ProductsGateway gateway = new ProductsGateway() {
            @Override
            public Mono<List<String>> findSimilarIds(String productId) {
                return Mono.just(List.of("2", "3", "4"));
            }

            @Override
            public Mono<ProductDetail> findById(String productId) {
                Duration delay = productId.equals("2")
                        ? Duration.ofMillis(40)
                        : Duration.ofMillis(5);
                return Mono.just(products.get(productId)).delayElement(delay);
            }
        };

        SimilarProductsService service = new SimilarProductsService(gateway, properties());

        StepVerifier.create(service.findSimilarProducts("1"))
                .assertNext(result -> assertThat(result)
                        .extracting(ProductDetail::id)
                        .containsExactly("2", "3", "4"))
                .verifyComplete();
    }

    @Test
    void subscribesToProductDetailsConcurrently() {
        AtomicInteger subscriptions = new AtomicInteger();
        Sinks.One<ProductDetail> secondProduct = Sinks.one();
        Sinks.One<ProductDetail> thirdProduct = Sinks.one();

        ProductsGateway gateway = new ProductsGateway() {
            @Override
            public Mono<List<String>> findSimilarIds(String productId) {
                return Mono.just(List.of("2", "3"));
            }

            @Override
            public Mono<ProductDetail> findById(String productId) {
                Sinks.One<ProductDetail> sink = productId.equals("2")
                        ? secondProduct
                        : thirdProduct;
                return Mono.defer(() -> {
                    if (subscriptions.incrementAndGet() == 2) {
                        thirdProduct.tryEmitValue(product("3"));
                        secondProduct.tryEmitValue(product("2"));
                    }
                    return sink.asMono();
                });
            }
        };

        SimilarProductsService service = new SimilarProductsService(gateway, properties());

        StepVerifier.create(service.findSimilarProducts("1"))
                .assertNext(result -> assertThat(result)
                        .extracting(ProductDetail::id)
                        .containsExactly("2", "3"))
                .expectComplete()
                .verify(Duration.ofSeconds(1));

        assertThat(subscriptions).hasValue(2);
    }

    @Test
    void returnsAnEmptyListWhenThereAreNoSimilarProducts() {
        ProductsGateway gateway = new ProductsGateway() {
            @Override
            public Mono<List<String>> findSimilarIds(String productId) {
                return Mono.just(List.of());
            }

            @Override
            public Mono<ProductDetail> findById(String productId) {
                return Mono.error(new AssertionError("Details must not be requested"));
            }
        };

        SimilarProductsService service = new SimilarProductsService(gateway, properties());

        StepVerifier.create(service.findSimilarProducts("1"))
                .expectNext(List.of())
                .verifyComplete();
    }

    private static ProductDetail product(String id) {
        return new ProductDetail(id, "Product " + id, BigDecimal.TEN, true);
    }

    private static SimilarProductsProperties properties() {
        return new SimilarProductsProperties(3);
    }
}
