package com.eduardopereyra.similarproducts.application.error;

public class ProductsServiceTimeoutException extends ProductsServiceException {

    public ProductsServiceTimeoutException(Throwable cause) {
        super("Products API timed out", cause);
    }
}
