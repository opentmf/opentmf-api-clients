# opentmf-api-clients

High-level TMF API client library built on [opentmf-http-clients](https://github.com/opentmf/opentmf-http-clients).  
Supports both **reactive** (WebClient) and **synchronous** (RestClient) clients with a simplified
configuration model that eliminates `baseUrl` repetition across endpoints.

The existing [opentmf-clients-base](https://github.com/opentmf/opentmf-clients-base) remains in
maintenance mode for Spring Boot 3.x users. This library targets **Spring Boot 4.x** and
**Java 17+**.

---

## Module Structure

```
opentmf-api-clients/
├── opentmf-api-clients-common     # Interfaces, models, config, utilities (no reactive deps)
├── opentmf-api-clients-reactive   # WebClient implementation + auto-configuration
├── opentmf-api-clients-rest       # RestClient implementation + auto-configuration
└── opentmf-api-clients-hub        # Hub (EventSubscription) client for both sync and reactive
```

Each implementation module carries its own Spring Boot auto-configuration. REST-only apps do not
pull in WebFlux or Reactor.

---

## Quick Start

### Dependency

For a **reactive** microservice:

```xml
<dependency>
  <groupId>org.opentmf.client</groupId>
  <artifactId>opentmf-api-clients-reactive</artifactId>
  <version>1.0.0</version>
</dependency>
```

For a **synchronous / servlet** microservice:

```xml
<dependency>
  <groupId>org.opentmf.client</groupId>
  <artifactId>opentmf-api-clients-rest</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Configuration

```yaml
opentmf:
  http-clients:                                   # from opentmf-http-clients
    default:
      client-type: netty                          # netty | jdk | apache
      bearer-auth:
        token-url: https://auth.example.com/token
        client-id: my-client
        client-secret: secret
        form-data:
          grant_type: client_credentials

  api-clients:
    catalog-management:
      client-ref: default                         # references opentmf.http-clients.default
      base-url: http://catalog-service:8080
      context-path: /tmf-api/productCatalogManagement/v4
      endpoints:
        product-offering:
          path: /productOffering
          scopes:
            get: CATALOG_GET
            list: CATALOG_LIST

    order-management:
      client-ref: default
      base-url: http://order-service:8080
      context-path: /tmf-api/productOrderingManagement/v4
      endpoints:
        product-order:
          path: /productOrder
          scopes:
            get: ORDER_GET
            list: ORDER_LIST
            post: ORDER_CREATE
            patch: ORDER_PATCH
            delete: ORDER_DELETE
```

---

## Usage Levels

### Level 1 — Zero custom code

Auto-configuration registers a generic client bean per endpoint. Inject by qualifier and pass the
desired response type at call time.

Bean name pattern: `{serverName}.{endpointName}TmfClient`

**Reactive (WebClient):**

```java
@Autowired
@Qualifier("catalogManagement.productOfferingTmfClient")
GenericReactiveTmfClient productOfferingClient;

Mono<ProductOffering> po = productOfferingClient.get("123", ProductOffering.class);
Flux<ProductOffering> all = productOfferingClient.list(ProductOffering.class);
Mono<ProductOffering> created = productOfferingClient.post(createPayload, ProductOffering.class);
```

**REST (RestClient):**

```java
@Autowired
@Qualifier("catalogManagement.productOfferingTmfClient")
RestGenericTmfClient productOfferingClient;

ProductOffering po = productOfferingClient.get("123", ProductOffering.class);
List<ProductOffering> all = productOfferingClient.list(ProductOffering.class);
ProductOffering created = productOfferingClient.post(createPayload, ProductOffering.class);
```

### Level 2 — Typed client with one `@Bean` method (Recommended)

Use the client factory for compile-time type safety on the client itself.

> **Note:** The now end-of-life `opentmf-v4-clients` library shipped a dedicated pre-built
> client module per TMF API (e.g. `opentmf-632-v4-client`, `opentmf-641-v4-client`).  With
> `opentmf-api-clients`, a single `@Bean` method replaces an entire module — no per-API
> dependency, interface, or exception class is needed.

**Reactive (WebClient):**

```java
@Bean
public ReactiveTmfClient<ProductOfferingCreate, ProductOfferingUpdate, ProductOffering>
    productOfferingClient(ReactiveTmfClientFactory factory, TmfApiClientsConfig config) {
  return factory.create(
      config.getApiClients().get("catalog-management"),
      "product-offering",
      ProductOffering.class);
}
```

Then inject as a typed `ReactiveTmfClient<C, U, R>`.

**REST (RestClient):**

```java
@Bean
public TmfClient<ProductOfferingCreate, ProductOfferingUpdate, ProductOffering>
    productOfferingClient(TmfClientFactory factory, TmfApiClientsConfig config) {
  return factory.create(
      config.getApiClients().get("catalog-management"),
      "product-offering",
      ProductOffering.class);
}
```

Then inject as a typed `TmfClient<C, U, R>`.

No extra interfaces, implementations, or exception classes needed in either case.

### Level 3 — Custom subclass

For custom behavior (e.g. overriding error handling, adding audit logging).

**Reactive (WebClient):**

```java
public class ProductOfferingReactiveClient
    extends ReactiveTmfClientImpl<ProductOfferingCreate, ProductOfferingUpdate, ProductOffering> {

  public ProductOfferingReactiveClient(
      EndpointConfig endpointConfig,
      ServerConfig serverConfig,
      WebClient webClient,
      TokenService tokenService,
      ClientProperties clientProperties) {
    super(endpointConfig, serverConfig, webClient, tokenService, clientProperties,
        ProductOffering.class);
  }

  // Override only what you need
}
```

**REST (RestClient):**

```java
public class ProductOfferingRestClient
    extends TmfClientImpl<ProductOfferingCreate, ProductOfferingUpdate, ProductOffering> {

  public ProductOfferingRestClient(
      EndpointConfig endpointConfig,
      ServerConfig serverConfig,
      RestClient restClient,
      SyncTokenService tokenService,
      ClientProperties clientProperties) {
    super(endpointConfig, serverConfig, restClient, tokenService, clientProperties,
        ProductOffering.class);
  }

  // Override only what you need
}
```

---

## TmfRequestContext

`TmfRequestContext` carries optional per-request parameters. It works identically with both
reactive and REST clients:

```java
TmfRequestContext ctx = TmfRequestContext.builder()
    .withFields("id", "name", "status")
    .withServerJsonFilter("name=='Fiber 100Mbps'")   // sent as ?filter= to the TMF API
    .withHeaderValues("X-Correlation-Id", correlationId)
    .withQueryParameters("category", "TMF_CATALOG")
    .build();
```

Two filter modes are available:

- `withServerJsonFilter(query)` -- forwards the expression **as-is** to the server as a `?filter=`
  query parameter. This library does not interpret or validate the expression; the remote TMF API
  is solely responsible for evaluating it. The accepted syntax (TMF-630 query language, FIQL,
  RSQL, etc.) varies by server implementation — consult the target API's documentation.
- `withClientJsonFilter(query)` -- fetches all results first, then applies the expression locally
  using [Jayway JsonPath 3.x](https://github.com/json-path/JsonPath). The full filter syntax is
  described in the [JsonPath Filter Guide](#jsonpath-filter-guide) section below.

**Reactive:**

```java
Mono<ProductOffering> po = client.get("123", ctx, ProductOffering.class);
```

**REST:**

```java
ProductOffering po = client.get("123", ctx, ProductOffering.class);
```

---

## Pagination

Use `TmfOffsetRequest` for paginated list operations.

**REST:**

```java
TmfOffsetRequest page = TmfOffsetRequest.of(0, 20);

List<ProductOffering> items = client.list(page, ProductOffering.class);
List<ProductOffering> all = client.listAll(ProductOffering.class);

TmfPage<List<ProductOffering>> paged = client.listPaged(page, ProductOffering.class);
paged.getTotalElements(); // X-Total-Count header value
paged.hasNext();
```

**Reactive:**

```java
TmfOffsetRequest page = TmfOffsetRequest.of(0, 20);

Flux<ProductOffering> items = client.list(page, ProductOffering.class);
Flux<ProductOffering> all = client.listAll(ProductOffering.class);

Mono<TmfPage<Flux<ProductOffering>>> paged = client.listPaged(page, ProductOffering.class);
paged.map(p -> p.getTotalElements()); // X-Total-Count header value
paged.map(TmfPage::hasNext);
```

---

## JsonPath Filter Guide

This section applies to **client-side filtering only** (`withClientFilter` /
`withClientJsonFilter`), which uses
[Jayway JsonPath 3.x](https://github.com/json-path/JsonPath) to evaluate expressions locally
after fetching results.

> **Server-side filters are out of scope.** The `withServerJsonFilter` / `withServerFilter`
> methods simply pass the expression string to the remote API as a `?filter=` query parameter.
> The accepted syntax depends entirely on the server implementation (e.g. TMF-630 query language,
> FIQL, RSQL, or a proprietary format). Consult the target API's documentation for the supported
> grammar.  Do **not** assume the server uses Jayway JsonPath.
>
> Note: `opentmf-mockserver` happens to evaluate `?filter=` parameters as JsonPath expressions
> for convenience in integration tests, but this is a test-time behaviour, not a production
> contract.

> **Reference:** Full Jayway operator documentation is available in the
> [Jayway JsonPath README](https://github.com/json-path/JsonPath#filter-operators).

All examples below assume a JSON array of objects such as:

```json
[
  {
    "name": "Fiber 100Mbps",
    "status": "active",
    "characteristic": [
      {"name": "IMEI", "value": "123456789012345"},
      {"name": "Color", "value": "Black"}
    ]
  }
]
```

### Simple field equality

```
$[?(@.status == 'active')]
$[?(@.status != 'active')]
```

### Comparison operators

```
$[?(@.price > 10)]
$[?(@.price >= 10)]
$[?(@.price < 10)]
$[?(@.price <= 10)]
```

### Logical AND / OR

```
$[?(@.status == 'active' && @.name == 'Fiber 100Mbps')]
$[?(@.status == 'active' || @.status == 'suspended')]
$[?((@.status == 'active' || @.status == 'suspended') && @.name == 'Fiber 100Mbps')]
```

### Regex match (`=~`)

```
$[?(@.name =~ /Fiber.*/)]
$[?(@.status =~ /ACTIVE/i)]
```

### IN / NIN (value in a set)

```
$[?(@.status IN ['active', 'suspended'])]
$[?(@.status NIN ['inactive', 'suspended'])]
```

### Exists check

```
$[?(@.characteristic)]         // has a 'characteristic' field
$[?(!@.someOptionalField)]     // does NOT have the field
```

### SIZE and EMPTY

```
$[?(@.characteristic size 2)]     // array has exactly 2 elements
$[?(@.tags empty true)]           // array is empty
$[?(@.tags empty false)]          // array is NOT empty
```

### Single-field matching in nested arrays

To check whether a nested array contains **any** element where a single field matches:

```
$[?('IMEI' in @.characteristic[*].name)]
$[?(@.characteristic[*].name CONTAINS 'IMEI')]
$[?(@.characteristic[*].name =~ /Serial.*/)]
```

All three return parent objects that have at least one characteristic with a matching `name`.

### Correlated multi-field matching in nested arrays

When you need to ensure that **the same** nested object satisfies multiple conditions (e.g.
`name == 'IMEI'` **and** `value == '123456789012345'` on the same characteristic entry), use a
nested filter with `empty false`:

```
$[?(@.characteristic[?(@.name=='IMEI' && @.value=='123456789012345')] empty false)]
```

`empty false` is a **Jayway-specific operator** introduced in JsonPath 3.0.0. It checks that
the inner filter produced at least one result. Without it, the expression silently matches all
items — see the warning below.

**Java example:**

```java
TmfOffsetRequest req = TmfOffsetRequest.of(0, 100).withClientFilter(
    "$[?(@.characteristic[?(@.name=='IMEI' && @.value=='123456789012345')] empty false)]");
List<ProductOffering> matches = client.listAll(req);
```

> **Warning — uncorrelated matching:**  
> Using `'IMEI' in @.characteristic[*].name && '123...' in @.characteristic[*].value` does NOT
> guarantee that both values come from the same array element. An object with `name=IMEI` on one
> characteristic and `value=123...` on a **different** characteristic would be a false positive.
> Always use the nested `[?(...)] empty false` form for correlated conditions.

> **Warning — bare nested filters:**  
> `$[?(@.characteristic[?(@.name=='IMEI' && @.value=='123456789012345')])]` (without
> `empty false`) returns **all** items in Jayway 3.x. The inner filter always resolves to an
> array (even an empty one), which the outer exists-check treats as truthy.

---

## Hub Client

The `opentmf-api-clients-hub` module provides specialized clients for TMF `/hub` endpoints
(event subscription management). It replaces the EOL `opentmf-hub-v4-client` module.

Hub endpoints only support two operations: **register** (POST) and **unregister** (DELETE).
Generic CRUD operations (GET, LIST, PATCH) are intentionally not exposed.

### Key feature: unregister from a previous server

When you register a listener, the returned `HubRegistration` stores the `hubUri` — the base URL of
the hub endpoint that was used during registration. If the hub endpoint URL changes between
deployments (e.g. due to service migration), you can pass the old `HubRegistration` to
`unregisterListener(HubRegistration)` so that the DELETE request is sent to the **original** server.

### Dependency

```xml
<dependency>
  <groupId>org.opentmf.client</groupId>
  <artifactId>opentmf-api-clients-hub</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
```

### YAML Configuration

Hub endpoints are configured in the same `opentmf.api-clients` section. Any endpoint whose path
ends with `/hub` is automatically detected. The generic layer **will not** create a generic bean for
these endpoints; only specialized `TmfHubClient` / `ReactiveTmfHubClient` beans are registered.

```yaml
opentmf:
  api-clients:
    catalog-management:
      client-ref: default
      base-url: http://catalog-service:8080
      context-path: /tmf-api/productCatalogManagement/v4
      endpoints:
        product-offering:
          path: /productOffering
          scopes:
            get: CATALOG_READ
            list: CATALOG_READ
            post: CATALOG_WRITE
        hub:                          # <-- detected as a hub endpoint
          path: /hub
          scopes:
            post: HUB_REGISTER
            delete: HUB_UNREGISTER
```

### Usage — Synchronous (RestClient)

```java
@Autowired
@Qualifier("catalog-management.hubTmfHubClient")
TmfHubClient hubClient;

// Register a listener
EventSubscriptionInput input = new EventSubscriptionInput();
input.setCallback(URI.create("http://my-service:8080/listener"));
input.setQuery("eventType=ProductOfferingCreateEvent");

HubRegistration reg = hubClient.registerListener(input);
// reg.getId()    → server-assigned subscription ID
// reg.getHubUri() → http://catalog-service:8080/tmf-api/productCatalogManagement/v4/hub

// Unregister by ID (current server)
hubClient.unregisterListener(reg.getId());

// Unregister from a previous server (e.g. after hub URL change)
hubClient.unregisterListener(reg);  // DELETE → reg.getHubUri() + "/" + reg.getId()
```

### Usage — Reactive (WebClient)

```java
@Autowired
@Qualifier("catalog-management.hubReactiveTmfHubClient")
ReactiveTmfHubClient hubClient;

EventSubscriptionInput input = new EventSubscriptionInput();
input.setCallback(URI.create("http://my-service:8080/listener"));
input.setQuery("eventType=ProductOfferingCreateEvent");

hubClient.registerListener(input)
    .doOnNext(reg -> log.info("Registered: {}", reg))
    .flatMap(reg -> hubClient.unregisterListener(reg.getId()))
    .subscribe();
```

### Migration from opentmf-hub-v4-client

| opentmf-hub-v4-client | opentmf-api-clients-hub |
|---|---|
| `HubClient` (reactive only) | `TmfHubClient` (sync) + `ReactiveTmfHubClient` (reactive) |
| `HubClientImpl extends TmfClientBaseImpl` | `TmfHubClientImpl` / `ReactiveTmfHubClientImpl` (composition) |
| `ExtendedEventSubscription` | `HubRegistration` |
| `EventSubscription` (from opentmf-v4-models) | `EventSubscription` (self-contained) |
| `HubClientProvider` + manual bean creation | Auto-configuration detects `/hub` endpoints |
| Throws `UnsupportedOperationException` for CRUD | CRUD methods not exposed at all |

---

## Error Handling

All errors are surfaced as `OpenTmfClientResponseException` (or the `OpenTmfClientNotFoundException`
subclass for 404s) from `opentmf-http-clients`. No per-client exception classes are needed.

---

## Migration from opentmf-clients-base

| opentmf-clients-base | opentmf-api-clients |
|---|---|
| `opentmf.tmf-clients.<id>.base-url` | `opentmf.api-clients.<server>.base-url` |
| `opentmf.tmf-clients.<id>.endpoint` | `opentmf.api-clients.<server>.endpoints.<ep>.path` |
| `opentmf.tmf-clients.<id>.scopes` | `opentmf.api-clients.<server>.endpoints.<ep>.scopes` |
| `GenericClient` | `GenericReactiveTmfClient` or `GenericTmfClient` |
| `TmfClientBaseImpl` | `ReactiveTmfClientImpl` or `TmfClientImpl` |
| `TmfClientProvider` | `ReactiveTmfClientFactory` or `TmfClientFactory` |
| `TmfClientException` (per endpoint) | `OpenTmfClientResponseException` (shared) |

---

## Version History

See [CHANGELOG.md](CHANGELOG.md) for version history and release notes.
