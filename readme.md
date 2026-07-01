# Backend developer test — Similar Products

Spring Boot implementation of the `GET /product/{productId}/similar` contract
defined in [`similarProducts.yaml`](./similarProducts.yaml).

## Requirements

- Java 21+
- Docker Compose for the supplied mocks and load test

Maven does not need to be installed; the repository includes Maven Wrapper.

## Run locally

Start the supplied dependencies:

```bash
docker compose up -d simulado influxdb grafana
```

Start the application:

```bash
./mvnw spring-boot:run
```

The API is exposed on port `5000`:

```bash
curl http://localhost:5000/product/1/similar
```

Expected response:

```json
[
  {"id":"2","name":"Dress","price":19.99,"availability":true},
  {"id":"3","name":"Blazer","price":29.99,"availability":false},
  {"id":"4","name":"Boots","price":39.99,"availability":true}
]
```

## Run with Docker

```bash
docker build -t similar-products .
docker run --rm \
  -p 5000:5000 \
  -e PRODUCTS_API_BASE_URL=http://host.docker.internal:3001 \
  similar-products
```

## Tests

Run unit and integration tests:

```bash
./mvnw verify
```

Run the supplied load test while the application is running:

```bash
docker compose run --rm k6 run scripts/test.js
```

The supplied Grafana dashboard is available at
<http://localhost:3000/d/Le2Ku9NMk/k6-performance-test>.

## Design

The implementation follows a ports-and-adapters structure:

- `api`: HTTP contract and error responses.
- `application`: use case orchestration and the outbound port.
- `domain`: product model.
- `infrastructure`: non-blocking HTTP client and connection configuration.

`WebClient` keeps the full request path non-blocking. Product details are fetched
concurrently with a configurable upper bound, while `flatMapSequential` preserves
the similarity order required by the OpenAPI contract.

The HTTP connection pool and all relevant timeouts are explicit and configurable.
This prevents slow or unavailable dependencies from consuming resources
indefinitely under load.

The outbound products gateway is wrapped with an in-memory Caffeine cache. Both
similar ID lists and product details are cached, so repeated requests do not keep
hitting the supplied dependency for the same data. The cache also shares in-flight
requests for the same key, which avoids a thundering herd when many users request
the same product at once. Failed calls are not cached, allowing the application to
recover as soon as the dependency becomes healthy again.

Responses from the dependency are validated before reaching the public API.
Empty bodies, duplicate or blank IDs, and incomplete product details fail as
upstream errors instead of producing a response that violates the public contract.

## Error policy

The public contract only defines `200` and `404`. For upstream infrastructure
failures, this implementation also uses standard gateway responses:

| Upstream outcome | API response |
|---|---:|
| Successful response | `200 OK` |
| Any required product is missing | `404 Not Found` |
| Upstream returns another error | `502 Bad Gateway` |
| Upstream exceeds the response timeout | `504 Gateway Timeout` |
| Upstream cannot be reached | `502 Bad Gateway` |

No automatic retry is applied. Retrying read calls can be useful in production,
but without an agreed retry budget it would amplify load against the supplied
dependency during an outage.

## Configuration

The defaults are in [`src/main/resources/application.yml`](src/main/resources/application.yml).
The upstream URL can be overridden with:

```bash
PRODUCTS_API_BASE_URL=http://localhost:3001
```

Cache behaviour can be tuned in `application.yml`:

```yaml
similar-products:
  cache:
    enabled: true
    ttl: 5m
    max-size: 10000
```

Actuator health and info endpoints are exposed below `/actuator`.
