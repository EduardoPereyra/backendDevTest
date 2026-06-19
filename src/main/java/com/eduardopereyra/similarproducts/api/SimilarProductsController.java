package com.eduardopereyra.similarproducts.api;

import com.eduardopereyra.similarproducts.application.SimilarProductsService;
import com.eduardopereyra.similarproducts.domain.ProductDetail;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping(path = "/product", produces = MediaType.APPLICATION_JSON_VALUE)
public class SimilarProductsController {

    private final SimilarProductsService service;

    public SimilarProductsController(SimilarProductsService service) {
        this.service = service;
    }

    @GetMapping("/{productId}/similar")
    public Mono<List<ProductDetail>> getSimilarProducts(@PathVariable String productId) {
        return service.findSimilarProducts(productId);
    }
}
