package com.eduardopereyra.similarproducts.infrastructure;

import com.eduardopereyra.similarproducts.application.ProductsGateway;
import com.eduardopereyra.similarproducts.application.error.ProductsServiceException;
import com.eduardopereyra.similarproducts.domain.ProductDetail;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CachingProductsGatewayTest {

    @Test
    void cachesSimilarIdsAndProductDetails() {
        CountingProductsGateway delegate = new CountingProductsGateway();
        CachingProductsGateway gateway = new CachingProductsGateway(delegate, enabledCacheProperties());

        StepVerifier.create(gateway.findSimilarIds("1"))
                .expectNext(List.of("2", "3"))
                .verifyComplete();
        StepVerifier.create(gateway.findSimilarIds("1"))
                .expectNext(List.of("2", "3"))
                .verifyComplete();

        StepVerifier.create(gateway.findById("2"))
                .expectNext(product("2"))
                .verifyComplete();
        StepVerifier.create(gateway.findById("2"))
                .expectNext(product("2"))
                .verifyComplete();

        assertThat(delegate.similarIdsCalls).hasValue(1);
        assertThat(delegate.productDetailCalls).hasValue(1);
    }

    @Test
    void sharesInFlightRequestsForTheSameKey() {
        Sinks.One<ProductDetail> productResponse = Sinks.one();
        AtomicInteger productDetailCalls = new AtomicInteger();
        ProductsGateway delegate = new ProductsGateway() {
            @Override
            public Mono<List<String>> findSimilarIds(String productId) {
                return Mono.just(List.of("2"));
            }

            @Override
            public Mono<ProductDetail> findById(String productId) {
                return Mono.defer(() -> {
                    productDetailCalls.incrementAndGet();
                    return productResponse.asMono();
                });
            }
        };
        CachingProductsGateway gateway = new CachingProductsGateway(delegate, enabledCacheProperties());

        Mono<List<ProductDetail>> concurrentRequests = Mono.zip(
                        gateway.findById("2"),
                        gateway.findById("2")
                )
                .doOnSubscribe(subscription -> productResponse.tryEmitValue(product("2")))
                .map(tuple -> List.of(tuple.getT1(), tuple.getT2()));

        StepVerifier.create(concurrentRequests)
                .expectNext(List.of(product("2"), product("2")))
                .verifyComplete();

        assertThat(productDetailCalls).hasValue(1);
    }

    @Test
    void doesNotCacheFailures() {
        AtomicInteger productDetailCalls = new AtomicInteger();
        ProductsGateway delegate = new ProductsGateway() {
            @Override
            public Mono<List<String>> findSimilarIds(String productId) {
                return Mono.just(List.of("2"));
            }

            @Override
            public Mono<ProductDetail> findById(String productId) {
                return Mono.defer(() -> {
                    int call = productDetailCalls.incrementAndGet();
                    if (call == 1) {
                        return Mono.error(new ProductsServiceException("temporary failure"));
                    }
                    return Mono.just(product(productId));
                });
            }
        };
        CachingProductsGateway gateway = new CachingProductsGateway(delegate, enabledCacheProperties());

        StepVerifier.create(gateway.findById("2"))
                .expectError(ProductsServiceException.class)
                .verify();
        StepVerifier.create(gateway.findById("2"))
                .expectNext(product("2"))
                .verifyComplete();

        assertThat(productDetailCalls).hasValue(2);
    }

    @Test
    void bypassesCacheWhenDisabled() {
        CountingProductsGateway delegate = new CountingProductsGateway();
        CachingProductsGateway gateway = new CachingProductsGateway(
                delegate,
                new SimilarProductsCacheProperties(false, Duration.ofMinutes(5), 100)
        );

        StepVerifier.create(gateway.findById("2"))
                .expectNext(product("2"))
                .verifyComplete();
        StepVerifier.create(gateway.findById("2"))
                .expectNext(product("2"))
                .verifyComplete();

        assertThat(delegate.productDetailCalls).hasValue(2);
    }

    private static SimilarProductsCacheProperties enabledCacheProperties() {
        return new SimilarProductsCacheProperties(true, Duration.ofMinutes(5), 100);
    }

    private static ProductDetail product(String id) {
        return new ProductDetail(id, "Product " + id, BigDecimal.TEN, true);
    }

    private static class CountingProductsGateway implements ProductsGateway {

        private final AtomicInteger similarIdsCalls = new AtomicInteger();
        private final AtomicInteger productDetailCalls = new AtomicInteger();

        @Override
        public Mono<List<String>> findSimilarIds(String productId) {
            similarIdsCalls.incrementAndGet();
            return Mono.just(List.of("2", "3"));
        }

        @Override
        public Mono<ProductDetail> findById(String productId) {
            productDetailCalls.incrementAndGet();
            return Mono.just(product(productId));
        }
    }
}
