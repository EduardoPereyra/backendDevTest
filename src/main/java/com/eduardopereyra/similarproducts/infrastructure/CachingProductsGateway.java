package com.eduardopereyra.similarproducts.infrastructure;

import com.eduardopereyra.similarproducts.application.ProductsGateway;
import com.eduardopereyra.similarproducts.domain.ProductDetail;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;

@Primary
@Component
public class CachingProductsGateway implements ProductsGateway {

    private final ProductsGateway delegate;
    private final boolean enabled;
    private final Cache<String, Mono<List<String>>> similarIdsCache;
    private final Cache<String, Mono<ProductDetail>> productDetailsCache;

    public CachingProductsGateway(
            @Qualifier("webClientProductsGateway") ProductsGateway delegate,
            SimilarProductsCacheProperties properties
    ) {
        this.delegate = delegate;
        this.enabled = properties.enabled();
        this.similarIdsCache = newCache(properties);
        this.productDetailsCache = newCache(properties);
    }

    @Override
    public Mono<List<String>> findSimilarIds(String productId) {
        return cached(similarIdsCache, productId, delegate::findSimilarIds);
    }

    @Override
    public Mono<ProductDetail> findById(String productId) {
        return cached(productDetailsCache, productId, delegate::findById);
    }

    private <T> Mono<T> cached(
            Cache<String, Mono<T>> cache,
            String key,
            Function<String, Mono<T>> loader
    ) {
        if (!enabled) {
            return loader.apply(key);
        }

        return cache.get(key, ignored ->
                        loader.apply(key)
                                .doOnError(error -> cache.invalidate(key))
                                .cache()
                );
    }

    private static <T> Cache<String, Mono<T>> newCache(SimilarProductsCacheProperties properties) {
        return Caffeine.newBuilder()
                .expireAfterWrite(properties.ttl())
                .maximumSize(properties.maxSize())
                .build();
    }
}
