package com.eduardopereyra.similarproducts.infrastructure;

import com.eduardopereyra.similarproducts.application.ProductsGateway;
import com.eduardopereyra.similarproducts.application.error.ProductNotFoundException;
import com.eduardopereyra.similarproducts.application.error.ProductsServiceException;
import com.eduardopereyra.similarproducts.application.error.ProductsServiceTimeoutException;
import com.eduardopereyra.similarproducts.domain.ProductDetail;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.HashSet;
import java.util.concurrent.TimeoutException;

@Component
public class WebClientProductsGateway implements ProductsGateway {

    private static final ParameterizedTypeReference<List<String>> STRING_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;

    public WebClientProductsGateway(WebClient productsWebClient) {
        this.webClient = productsWebClient;
    }

    @Override
    public Mono<List<String>> findSimilarIds(String productId) {
        return get("/product/{productId}/similarids", productId, STRING_LIST)
                .switchIfEmpty(invalidResponse("empty similar product IDs body"))
                .flatMap(this::validateSimilarIds);
    }

    @Override
    public Mono<ProductDetail> findById(String productId) {
        return get("/product/{productId}", productId, ProductDetail.class)
                .switchIfEmpty(invalidResponse("empty product detail body"))
                .flatMap(product -> validateProductDetail(productId, product));
    }

    private <T> Mono<T> get(String path, String productId, Class<T> bodyType) {
        return response(path, productId)
                .bodyToMono(bodyType)
                .onErrorMap(this::mapRequestFailure);
    }

    private <T> Mono<T> get(
            String path,
            String productId,
            ParameterizedTypeReference<T> bodyType
    ) {
        return response(path, productId)
                .bodyToMono(bodyType)
                .onErrorMap(this::mapRequestFailure);
    }

    private WebClient.ResponseSpec response(String path, String productId) {
        return webClient.get()
                .uri(path, productId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.createException().flatMap(exception -> {
                            if (response.statusCode().value() == HttpStatus.NOT_FOUND.value()) {
                                return Mono.error(new ProductNotFoundException(productId));
                            }
                            return Mono.error(new ProductsServiceException(
                                    "Products API returned HTTP %d".formatted(
                                            response.statusCode().value()
                                    ),
                                    exception
                            ));
                        })
                );
    }

    private Throwable mapRequestFailure(Throwable failure) {
        if (failure instanceof ProductNotFoundException
                || failure instanceof ProductsServiceException) {
            return failure;
        }
        if (hasCause(failure, ReadTimeoutException.class)
                || hasCause(failure, TimeoutException.class)) {
            return new ProductsServiceTimeoutException(failure);
        }
        if (failure instanceof WebClientRequestException) {
            return new ProductsServiceException("Products API is unavailable", failure);
        }
        return new ProductsServiceException("Products API returned an invalid response", failure);
    }

    private Mono<List<String>> validateSimilarIds(List<String> productIds) {
        boolean containsInvalidId = productIds.stream()
                .anyMatch(productId -> productId == null || productId.isBlank());
        boolean containsDuplicates = new HashSet<>(productIds).size() != productIds.size();

        if (containsInvalidId || containsDuplicates) {
            return invalidResponse("invalid similar product IDs");
        }
        return Mono.just(List.copyOf(productIds));
    }

    private Mono<ProductDetail> validateProductDetail(
            String requestedProductId,
            ProductDetail product
    ) {
        if (product.id() == null
                || product.id().isBlank()
                || !product.id().equals(requestedProductId)
                || product.name() == null
                || product.name().isBlank()
                || product.price() == null) {
            return invalidResponse("invalid product detail");
        }
        return Mono.just(product);
    }

    private <T> Mono<T> invalidResponse(String reason) {
        return Mono.error(new ProductsServiceException(
                "Products API returned an invalid response: " + reason
        ));
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
