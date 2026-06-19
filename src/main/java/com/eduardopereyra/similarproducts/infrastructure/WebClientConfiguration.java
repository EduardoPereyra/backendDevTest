package com.eduardopereyra.similarproducts.infrastructure;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class WebClientConfiguration {

    @Bean
    WebClient productsWebClient(ProductsClientProperties properties) {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("products-api")
                .maxConnections(properties.maxConnections())
                .pendingAcquireTimeout(properties.pendingAcquireTimeout())
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.connectTimeout().toMillis())
                )
                .responseTimeout(properties.responseTimeout());

        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
