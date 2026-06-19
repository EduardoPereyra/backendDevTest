package com.eduardopereyra.similarproducts.api;

import com.eduardopereyra.similarproducts.application.error.ProductNotFoundException;
import com.eduardopereyra.similarproducts.application.error.ProductsServiceException;
import com.eduardopereyra.similarproducts.application.error.ProductsServiceTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;

@RestControllerAdvice
public class GlobalErrorHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(
            ProductNotFoundException exception,
            ServerWebExchange exchange
    ) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), exchange);
    }

    @ExceptionHandler(ProductsServiceTimeoutException.class)
    ResponseEntity<ApiError> handleTimeout(
            ProductsServiceTimeoutException exception,
            ServerWebExchange exchange
    ) {
        return response(HttpStatus.GATEWAY_TIMEOUT, "Products API timed out", exchange);
    }

    @ExceptionHandler(ProductsServiceException.class)
    ResponseEntity<ApiError> handleProductsServiceError(
            ProductsServiceException exception,
            ServerWebExchange exchange
    ) {
        return response(HttpStatus.BAD_GATEWAY, exception.getMessage(), exchange);
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String message,
            ServerWebExchange exchange
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                exchange.getRequest().getPath().value()
        );
        return ResponseEntity.status(status).body(error);
    }
}
