package com.eduardopereyra.similarproducts;

import com.eduardopereyra.similarproducts.application.SimilarProductsProperties;
import com.eduardopereyra.similarproducts.infrastructure.ProductsClientProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        SimilarProductsProperties.class,
        ProductsClientProperties.class
})
public class SimilarProductsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimilarProductsApplication.class, args);
    }
}
