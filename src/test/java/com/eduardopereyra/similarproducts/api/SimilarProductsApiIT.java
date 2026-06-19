package com.eduardopereyra.similarproducts.api;

import com.eduardopereyra.similarproducts.SimilarProductsApplication;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = SimilarProductsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class SimilarProductsApiIT {

    private static final MockWebServer PRODUCTS_API = new MockWebServer();
    private static final Map<String, MockResponse> RESPONSES = new ConcurrentHashMap<>();

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startServer() throws IOException {
        PRODUCTS_API.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return RESPONSES.getOrDefault(
                        request.getPath(),
                        new MockResponse().setResponseCode(404)
                );
            }
        });
        PRODUCTS_API.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        PRODUCTS_API.shutdown();
    }

    @DynamicPropertySource
    static void productsApiProperties(DynamicPropertyRegistry registry) {
        registry.add("clients.products.base-url", () -> PRODUCTS_API.url("/").toString());
        registry.add("clients.products.response-timeout", () -> "200ms");
    }

    @BeforeEach
    void resetServer() throws InterruptedException {
        RESPONSES.clear();
        while (PRODUCTS_API.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // Drain requests left by the previous test.
        }
    }

    @Test
    void returnsOrderedProductDetailsRegardlessOfResponseArrivalOrder() throws InterruptedException {
        respondWithJson("/product/1/similarids", "[\"2\",\"3\"]");
        respondWithJson("/product/2", """
                {"id":"2","name":"Dress","price":19.99,"availability":true}
                """).setBodyDelay(100, TimeUnit.MILLISECONDS);
        respondWithJson("/product/3", """
                {"id":"3","name":"Blazer","price":29.99,"availability":false}
                """);

        webTestClient.get()
                .uri("/product/1/similar")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .json("""
                        [
                          {"id":"2","name":"Dress","price":19.99,"availability":true},
                          {"id":"3","name":"Blazer","price":29.99,"availability":false}
                        ]
                        """);

        assertThat(receivedPaths(3))
                .containsExactlyInAnyOrder(
                        "/product/1/similarids",
                        "/product/2",
                        "/product/3"
                );
    }

    @Test
    void mapsAMissingProductToNotFound() {
        respondWithJson("/product/4/similarids", "[\"5\"]");
        RESPONSES.put("/product/5", new MockResponse()
                .setResponseCode(404)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"message\":\"Product not found\"}"));

        webTestClient.get()
                .uri("/product/4/similar")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.path").isEqualTo("/product/4/similar");
    }

    @Test
    void mapsAnUpstreamFailureToBadGateway() {
        respondWithJson("/product/5/similarids", "[\"6\"]");
        RESPONSES.put("/product/6", new MockResponse().setResponseCode(500));

        webTestClient.get()
                .uri("/product/5/similar")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.status").isEqualTo(502);
    }

    @Test
    void returnsAnEmptyListWithoutRequestingDetails() throws InterruptedException {
        respondWithJson("/product/7/similarids", "[]");

        webTestClient.get()
                .uri("/product/7/similar")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("[]");

        assertThat(receivedPaths(1)).containsExactly("/product/7/similarids");
    }

    @Test
    void mapsSimilarIdsFailureToBadGateway() {
        RESPONSES.put("/product/8/similarids", new MockResponse().setResponseCode(503));

        webTestClient.get()
                .uri("/product/8/similar")
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.status").isEqualTo(502);
    }

    @Test
    void mapsAnUpstreamTimeoutToGatewayTimeout() {
        respondWithJson("/product/9/similarids", "[\"10\"]");
        respondWithJson("/product/10", """
                {"id":"10","name":"Slow product","price":10.00,"availability":true}
                """).setHeadersDelay(1, TimeUnit.SECONDS);

        webTestClient.get()
                .uri("/product/9/similar")
                .exchange()
                .expectStatus().isEqualTo(504)
                .expectBody()
                .jsonPath("$.status").isEqualTo(504)
                .jsonPath("$.message").isEqualTo("Products API timed out");
    }

    @Test
    void rejectsAnEmptySimilarIdsBody() {
        RESPONSES.put("/product/11/similarids", new MockResponse().setResponseCode(204));

        expectInvalidUpstreamResponse("/product/11/similar");
    }

    @Test
    void rejectsDuplicateSimilarIds() {
        respondWithJson("/product/12/similarids", "[\"2\",\"2\"]");

        expectInvalidUpstreamResponse("/product/12/similar");
    }

    @Test
    void rejectsAnIncompleteProductDetail() {
        respondWithJson("/product/13/similarids", "[\"14\"]");
        respondWithJson("/product/14", """
                {"id":"14","price":10.00,"availability":true}
                """);

        expectInvalidUpstreamResponse("/product/13/similar");
    }

    @Test
    void rejectsAProductDetailWithAnUnexpectedId() {
        respondWithJson("/product/15/similarids", "[\"16\"]");
        respondWithJson("/product/16", """
                {"id":"999","name":"Wrong product","price":10.00,"availability":true}
                """);

        expectInvalidUpstreamResponse("/product/15/similar");
    }

    @Test
    void rejectsMalformedJsonFromTheProductsApi() {
        respondWithJson("/product/17/similarids", "not-json");

        expectInvalidUpstreamResponse("/product/17/similar");
    }

    private static MockResponse respondWithJson(String path, String body) {
        MockResponse response = new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
        RESPONSES.put(path, response);
        return response;
    }

    private void expectInvalidUpstreamResponse(String path) {
        webTestClient.get()
                .uri(path)
                .exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.status").isEqualTo(502)
                .jsonPath("$.message").value(message ->
                        assertThat(message.toString())
                                .startsWith("Products API returned an invalid response")
                );
    }

    private static java.util.List<String> receivedPaths(int count) throws InterruptedException {
        java.util.List<String> paths = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            RecordedRequest request = PRODUCTS_API.takeRequest(1, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            paths.add(request.getPath());
        }
        return paths;
    }
}
