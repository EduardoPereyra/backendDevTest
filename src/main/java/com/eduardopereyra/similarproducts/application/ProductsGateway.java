package com.eduardopereyra.similarproducts.application;

import com.eduardopereyra.similarproducts.domain.ProductDetail;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ProductsGateway {

    Mono<List<String>> findSimilarIds(String productId);

    Mono<ProductDetail> findById(String productId);
}
