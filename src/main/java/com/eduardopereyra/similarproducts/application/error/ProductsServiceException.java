package com.eduardopereyra.similarproducts.application.error;

public class ProductsServiceException extends RuntimeException {

    public ProductsServiceException(String message) {
        super(message);
    }

    public ProductsServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
