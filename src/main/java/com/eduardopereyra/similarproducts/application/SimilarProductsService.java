package com.eduardopereyra.similarproducts.application;

import com.eduardopereyra.similarproducts.domain.ProductDetail;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class SimilarProductsService {

    private final ProductsGateway productsGateway;
    private final int detailConcurrency;

    public SimilarProductsService(
            ProductsGateway productsGateway,
            SimilarProductsProperties properties
    ) {
        this.productsGateway = productsGateway;
        this.detailConcurrency = properties.detailConcurrency();
    }

    public Mono<List<ProductDetail>> findSimilarProducts(String productId) {
        return productsGateway.findSimilarIds(productId)
                .flatMapMany(Flux::fromIterable)
                .flatMapSequential(productsGateway::findById, detailConcurrency)
                .collectList();
    }
}
