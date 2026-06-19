package com.eduardopereyra.similarproducts.application.error;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productId) {
        super("Product %s was not found".formatted(productId));
    }
}
