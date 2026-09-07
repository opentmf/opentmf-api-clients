# opentmf-api-clients

High-level TMF API client library built on
[opentmf-http-clients](https://github.com/opentmf/opentmf-http-clients).
Supports both **reactive** (WebClient) and **synchronous** (RestClient) clients with a
simplified configuration model that eliminates `baseUrl` repetition across endpoints.

The existing [opentmf-clients-base](https://github.com/opentmf/opentmf-clients-base) remains in
maintenance mode for Spring Boot 3.x users. This library targets **Spring Boot 4.x** and
**Java 17+**.

---

## Table of Contents

1. [Module Structure](#module-structure)
2. [Quick Start](#quick-start)
3. [Configuration](#configuration)
4. [Client Setup — three approaches](#client-setup--three-approaches)
   - [Level 1 — Zero custom code](#level-1--zero-custom-code)
   - [Level 2 — Typed client with one `@Bean` (recommended)](#level-2--typed-client-with-one-bean-recommended)
   - [Level 3 — Custom subclass](#level-3--custom-subclass)
5. [API Reference](#api-reference)
   - [Operation overview](#operation-overview)
   - [Overload pattern](#overload-pattern)
   - [Read operations](#read-operations)
   - [Write operations](#write-operations)
   - [Bulk create — collection-level JSON Patch](#bulk-create--collection-level-json-patch)
   - [Delete](#delete)
   - [Sub-resource paths](#sub-resource-paths)
6. [Per-request options — `TmfRequestContext`](#per-request-options--tmfrequestcontext)
7. [Pagination](#pagination)
   - [Passing `TmfRequestContext` to list operations](#passing-tmfrequestcontext-to-list-operations)
8. [Client-side filtering — JsonPath guide](#client-side-filtering--jsonpath-guide)
9. [Hub Client](#hub-client)
10. [Error Handling](#error-handling)
11. [Migration](#migration)
12. [Version History](#version-history)

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

For a **reactive** microservice:

```xml
<dependency>
  <groupId>org.opentmf.client</groupId>
  <artifactId>opentmf-api-clients-reactive</artifactId>
  <version>2.0.6</version>
</dependency>
```

For a **synchronous** microservice:

```xml
<dependency>
  <groupId>org.opentmf.client</groupId>
  <artifactId>opentmf-api-clients-rest</artifactId>
  <version>2.0.6</version>
</dependency>
```

A minimal end-to-end use looks like:

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
```

```java
@Autowired
@Qualifier("catalogManagement.productOfferingTmfClient")
GenericReactiveTmfClient client;

Mono<ProductOffering> po = client.get("123", ProductOffering.class);
```

The full configuration schema and bean wiring options are covered next.

---

## Configuration

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
    catalog-management:                           # logical server name
      client-ref: default                         # references opentmf.http-clients.default
      base-url: http://catalog-service:8080
      context-path: /tmf-api/productCatalogManagement/v4
      fixed-headers:                              # optional, applied to every request
        X-Tenant-Id: acme
      endpoints:
        product-offering:
          path: /productOffering
          fixed-headers:                          # optional, merged with server fixed-headers
            X-Source: catalog-ui
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
            put: ORDER_REPLACE
            patch: ORDER_PATCH
            delete: ORDER_DELETE
```

`scopes` are optional. When present, the value is forwarded to the token service so that the
appropriate access scope is requested per operation. When absent, the default token is used.

Endpoint-level `fixed-headers` override server-level entries on key collisions. See
[CHANGELOG 2.0.5](CHANGELOG.md) for details.

---

## Client Setup — three approaches

Three increasing levels of customization are supported. Pick the lowest level that fits your
needs.

### Level 1 — Zero custom code

Auto-configuration registers a generic client bean per endpoint. Inject by qualifier and pass
the desired response type at call time. Bean name pattern:
`{serverName}.{endpointName}TmfClient`.

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
GenericTmfClient productOfferingClient;

ProductOffering po = productOfferingClient.get("123", ProductOffering.class);
List<ProductOffering> all = productOfferingClient.list(ProductOffering.class);
ProductOffering created = productOfferingClient.post(createPayload, ProductOffering.class);
```

### Level 2 — Typed client with one `@Bean` (recommended)

Use the client factory for compile-time type safety on the client itself.

> **Note:** The end-of-life `opentmf-v4-clients` library shipped a dedicated module per TMF API
> (e.g. `opentmf-632-v4-client`, `opentmf-641-v4-client`). With `opentmf-api-clients`, a single
> `@Bean` method replaces an entire module — no per-API dependency, interface, or exception
> class is needed.

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

Then inject as a typed `TmfClient<C, U, R>` or `ReactiveTmfClient<C, U, R>`. No extra
interfaces, implementations, or exception classes are needed.

### Level 3 — Custom subclass

For custom behaviour (overriding error handling, audit logging, etc.).

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

## API Reference

Both interfaces are parameterized as `<C, U, R>`:

- `C` — create DTO type (POST body)
- `U` — update DTO type (merge-patch body)
- `R` — default response type

Source files:
[`TmfClient`](opentmf-api-clients-common/src/main/java/org/opentmf/api/client/common/api/TmfClient.java) ·
[`ReactiveTmfClient`](opentmf-api-clients-reactive/src/main/java/org/opentmf/api/client/reactive/api/ReactiveTmfClient.java).

### Operation overview

| Operation | `TmfClient` (sync) | `ReactiveTmfClient` (reactive) |
|---|---|---|
| Read one | `R get(id)` | `Mono<R> get(id)` |
| List one page | `List<R> list(pageable)` | `Flux<R> list(pageable)` |
| List all pages | `List<R> listAll(pageable)` | `Flux<R> listAll(pageable)` |
| List with metadata | `TmfPage<List<R>> listPaged(pageable)` | `Mono<TmfPage<List<R>>> listPaged(pageable)` |
| Create | `R post(C obj)` | `Mono<R> post(C obj)` |
| Merge patch | `R patch(id, U obj)` | `Mono<R> patch(id, U obj)` |
| JSON patch | `R patch(id, JsonPatch jp)` | `Mono<R> patch(id, JsonPatch jp)` |
| Bulk create (collection JSON patch) | `List<R> patchCollection(JsonPatch jp)` | `Mono<List<R>> patchCollection(JsonPatch jp)` |
| Delete | `void delete(id)` | `Mono<Void> delete(id)` |

### Overload pattern

Each row above represents **eight overloads** following the same pattern:

| Variant | Combinations |
|---|---|
| Auto-token (the impl fetches a token via `TokenService`) | (default `R` / explicit `Class<T>`) × (no `ctx` / with `TmfRequestContext ctx`) |
| With-token (caller supplies the bearer token) | same combinations, prefixed by `String token` |

Concrete shape for the `get` operation:

```java
// auto-token
R get(String id);
R get(String id, TmfRequestContext ctx);
<T> T get(String id, Class<T> type);
<T> T get(String id, TmfRequestContext ctx, Class<T> type);

// with-token
R getWithToken(String token, String id);
R getWithToken(String token, String id, TmfRequestContext ctx);
<T> T getWithToken(String token, String id, Class<T> type);
<T> T getWithToken(String token, String id, TmfRequestContext ctx, Class<T> type);
```

`post`, `patch` (merge), `patch` (JSON), `patchCollection`, and `delete` follow the identical
pattern. `list`, `listAll`, and `listPaged` have a similar 8-overload shape, replacing
`String id` with optional `Pageable pageable`.

> **Why don't `list*` methods take `TmfRequestContext`?**
> They do — just not as a separate parameter. The context travels **inside** the `Pageable`.
> `TmfOffsetRequest` (this library's `Pageable` implementation) embeds a `TmfRequestContext`
> field, so headers, query parameters, fields, and filters are attached to the request
> object itself. There is no `list(pageable, ctx, ...)` overload because it would be
> redundant. See
> [Passing `TmfRequestContext` to list operations](#passing-tmfrequestcontext-to-list-operations)
> for concrete examples.

### Read operations

```java
// Single resource — sync
ProductOffering po = client.get("123");
ProductOffering po = client.get("123", ProductOffering.class);
ProductOffering po = client.get("123", ctx);
ProductOffering po = client.get("123", ctx, ProductOffering.class);

// Single resource — reactive
Mono<ProductOffering> po = client.get("123");
```

The three list flavours differ only in what they return:

| Method | Returns (sync) | Returns (reactive) | Behaviour |
|---|---|---|---|
| `list(...)` | `List<R>` | `Flux<R>` | one page worth of items (single HTTP call) |
| `listAll(...)` | `List<R>` | `Flux<R>` | walks every page until the API reports no `next` link |
| `listPaged(...)` | `TmfPage<List<R>>` | `Mono<TmfPage<List<R>>>` | one page **plus** metadata (`X-Total-Count`, `hasNext()`, …) — see [Pagination](#pagination) |

### Write operations

| Body content type | Method family | Notes |
|---|---|---|
| `application/json` | `post(C obj, …)` | Create |
| `application/merge-patch+json` (RFC 7396) | `patch(String id, U obj, …)` | Update by merging a partial DTO |
| `application/json-patch+json` (RFC 6902) | `patch(String id, JsonPatch jp, …)` | Update by applying a list of operations |
| `application/json-patch+json` (RFC 6902) | `patchCollection(JsonPatch jp, …)` | Bulk-create — see next section |

```java
// Create
ProductOffering created = client.post(createPayload);

// Merge patch — sends a partial DTO; server replaces only the supplied fields
ProductOffering patched = client.patch("123", new ProductOfferingUpdate("new name"));

// JSON patch — sends a sequence of /op/path/value operations
JsonPatch jp = JsonPatch.builder()
    .replace("/lifecycleStatus", "Retired")
    .build();
ProductOffering patched = client.patch("123", jp);
```

### Bulk create — collection-level JSON Patch

`patchCollection` implements the TMF v4 §6.2 high-performance bulk-creation pattern:
`PATCH /<endpoint>` (no `/{id}` segment) with a JSON-Patch body whose operations are all
`{"op":"add","path":"/","value":{…}}`. The server responds with a JSON array of the created
resources, in the same order as the patch operations.

```java
JsonPatch bulk = JsonPatch.builder()
    .add("/", offering1)
    .add("/", offering2)
    .add("/", offering3)
    .build();

// sync
List<ProductOffering> created = client.patchCollection(bulk);

// reactive
Mono<List<ProductOffering>> created = client.patchCollection(bulk);
```

A `Flux<R>` overload is intentionally not provided — the HTTP roundtrip is single-shot
(one request, one buffered JSON-array response), so a `Flux` would only emit after the full
body is decoded. Callers wanting a `Flux` can append `.flatMapMany(Flux::fromIterable)`.

### Delete

```java
// sync
client.delete("123");                   // void
String confirmation = client.delete("123", String.class);

// reactive
Mono<Void> done = client.delete("123");
Mono<String> confirmation = client.delete("123", String.class);
```

### Sub-resource paths

`sub(String template, Object... vars)` derives a client scoped to a nested path under the
endpoint, e.g. `GET /order/{orderId}/action/{action}/item/{itemId}`. The derived client is the
generic (`Object`-typed) client — use the `Class<T>` overloads for typing — and every existing
verb works against the nested path unchanged:

```java
// GET /productOrder/o1/action/cancel/item/it7
Item item = orderClient.sub("/{orderId}/action/{action}/item", orderId, action)
                       .get(itemId, Item.class);

// GET /productOrder/o1/action/cancel/item
List<Item> items = orderClient.sub("/{orderId}/action/{action}/item", orderId, action)
                              .list(Item.class);

// POST /productOrder/o1/action/cancel/item
Item created = orderClient.sub("/{orderId}/action/{action}/item", orderId, action)
                          .post(body, Item.class);
```

Rules and guarantees:

- **The template must be a compile-time constant.** Never concatenate runtime data into it —
  every runtime value goes in `vars`, where it is expanded as a URI template variable and
  strictly percent-encoded. A value containing `/`, `:` or `{` cannot inject or break the path
  (`a/b` becomes `a%2Fb`), and encoding is identical to what `get(id)` produces for the same
  value.
- Only simple `{name}` placeholders are supported; `{name:regex}` is rejected.
- Template/argument arity is validated eagerly — too few *and* too many arguments throw
  `IllegalArgumentException` before any request is issued.
- The derived client inherits the parent endpoint's OAuth scopes, fixed headers, and transport
  (`RestClient`/`WebClient`, retries, token service). For a different scope, configure a
  separate endpoint in YAML instead.
- `sub(...)` on an already-derived client appends to its path, so nesting depth is unbounded.
  Each call allocates a thin new wrapper; hoist the derived client out of hot loops.

---

## Per-request options — `TmfRequestContext`

`TmfRequestContext` carries optional per-request parameters and works identically with both
client surfaces:

```java
TmfRequestContext ctx = TmfRequestContext.builder()
    .withFields("id", "name", "status")
    .withServerJsonFilter("name=='Fiber 100Mbps'")   // sent as ?filter= to the TMF API
    .withHeaderValues("X-Correlation-Id", correlationId)
    .withQueryParameters("category", "TMF_CATALOG")
    .build();

// sync
ProductOffering po = client.get("123", ctx, ProductOffering.class);

// reactive
Mono<ProductOffering> po = client.get("123", ctx, ProductOffering.class);
```

Two filter modes are available:

- `withServerJsonFilter(query)` — forwards the expression **as-is** to the server as a `?filter=`
  query parameter. This library does not interpret or validate the expression; the remote TMF
  API is solely responsible for evaluating it. The accepted syntax (TMF-630 query language,
  FIQL, RSQL, …) varies by server implementation — consult the target API's documentation.
- `withClientJsonFilter(query)` — fetches all results first, then applies the expression locally
  using [Jayway JsonPath 3.x](https://github.com/json-path/JsonPath). The full filter syntax is
  described in the
  [Client-side filtering — JsonPath guide](#client-side-filtering--jsonpath-guide) section.

---

## Pagination

Use `TmfOffsetRequest` for paginated list operations. It is a `Pageable` so it accepts a sort
order, optional client-side filter, and an embedded `TmfRequestContext`.

**Sync:**

```java
TmfOffsetRequest page = TmfOffsetRequest.of(0, 20);

List<ProductOffering> items = client.list(page, ProductOffering.class);
List<ProductOffering> all   = client.listAll(ProductOffering.class);

TmfPage<List<ProductOffering>> paged = client.listPaged(page, ProductOffering.class);
paged.getTotalElements();      // X-Total-Count header value
paged.hasNext();
```

**Reactive:**

```java
TmfOffsetRequest page = TmfOffsetRequest.of(0, 20);

Flux<ProductOffering> items = client.list(page, ProductOffering.class);
Flux<ProductOffering> all   = client.listAll(ProductOffering.class);

Mono<TmfPage<List<ProductOffering>>> paged = client.listPaged(page, ProductOffering.class);
paged.map(TmfPage::getTotalElements);
paged.map(TmfPage::hasNext);
```

### Passing `TmfRequestContext` to list operations

Single-resource methods (`get`, `post`, `patch`, `delete`) accept a separate
`TmfRequestContext` argument. **List methods do not** — and this trips people up. The
context is instead carried **inside** the `Pageable`: `TmfOffsetRequest` embeds a
`TmfRequestContext` field. There is no `list(pageable, ctx, …)` overload because passing
both would be redundant (the pageable already owns the context).

So whenever you need to attach fields, headers, query parameters, or a filter to a list
call, set them on the `TmfOffsetRequest`. Two equivalent ways are supported.

**(a) Fluent shortcuts directly on `TmfOffsetRequest`** — best for one-off list calls:

```java
TmfOffsetRequest req = TmfOffsetRequest.of(0, 20)
    .withFields("id", "name", "status")
    .withServerFilter("status=='active'")
    .withQueryParameters(new LinkedMultiValueMap<>(
        Map.of("category", List.of("TMF_CATALOG"))))
    .withHeaderParameters(new LinkedMultiValueMap<>(
        Map.of("X-Correlation-Id", List.of(correlationId))));

List<ProductOffering> items = client.list(req, ProductOffering.class);          // sync
Flux<ProductOffering> items = client.list(req, ProductOffering.class);          // reactive
```

**(b) Build a `TmfRequestContext` and attach it** — best when you already have a context
that's shared across single-resource calls and list calls:

```java
TmfRequestContext ctx = TmfRequestContext.builder()
    .withFields("id", "name", "status")
    .withServerJsonFilter("status=='active'")
    .withHeaderValues("X-Correlation-Id", correlationId)
    .build();

// Same ctx, three call sites
ProductOffering one  = client.get("123", ctx, ProductOffering.class);

TmfOffsetRequest req = TmfOffsetRequest.of(0, 20).withRequestContext(ctx);
List<ProductOffering> page = client.list(req, ProductOffering.class);
List<ProductOffering> all  = client.listAll(req, ProductOffering.class);
```

Both forms produce the same HTTP request. Pick whichever reads better at the call site.

The fluent helpers on `TmfOffsetRequest` map 1:1 to the underlying `TmfRequestContext`:

| `TmfOffsetRequest` shortcut | `TmfRequestContext` field |
|---|---|
| `withFields(String...)` | `fields` |
| `withClientFilter(String)` | `jsonFilter` (CLIENT type — local JsonPath evaluation) |
| `withServerFilter(String)` | `jsonFilter` (SERVER type — `?filter=` query parameter) |
| `withHeaderParameters(MultiValueMap)` | `headerParameters` |
| `withQueryParameters(MultiValueMap)` | `queryParameters` |
| `withRequestContext(TmfRequestContext)` | replaces the embedded context wholesale |

---

## Client-side filtering — JsonPath guide

This section applies to **client-side filtering only** (`withClientFilter` /
`withClientJsonFilter`), which uses [Jayway JsonPath 3.x](https://github.com/json-path/JsonPath)
to evaluate expressions locally after fetching results.

> **Server-side filters are out of scope.** The `withServerJsonFilter` / `withServerFilter`
> methods simply pass the expression string to the remote API as a `?filter=` query parameter.
> The accepted syntax depends entirely on the server implementation (e.g. TMF-630 query
> language, FIQL, RSQL, or a proprietary format). Consult the target API's documentation for
> the supported grammar. Do **not** assume the server uses Jayway JsonPath.
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
`name == 'IMEI'` **and** `value == '123456789012345'` on the same characteristic entry), use
a nested filter with `empty false`:

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
> guarantee that both values come from the same array element. An object with `name=IMEI` on
> one characteristic and `value=123...` on a **different** characteristic would be a false
> positive. Always use the nested `[?(...)] empty false` form for correlated conditions.

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

### Key feature — unregister from a previous server

When you register a listener, the returned `HubRegistration` stores the `hubUri` — the base
URL of the hub endpoint that was used during registration. If the hub endpoint URL changes
between deployments (e.g. due to service migration), you can pass the old `HubRegistration` to
`unregisterListener(HubRegistration)` so that the DELETE request is sent to the **original**
server.

### Dependency

```xml
<dependency>
  <groupId>org.opentmf.client</groupId>
  <artifactId>opentmf-api-clients-hub</artifactId>
  <version>2.0.6</version>
</dependency>
```

### YAML configuration

Hub endpoints are configured in the same `opentmf.api-clients` section. Any endpoint whose path
ends with `/hub` is automatically detected. The generic layer **will not** create a generic
bean for these endpoints; only specialized `TmfHubClient` / `ReactiveTmfHubClient` beans are
registered.

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

EventSubscriptionInput input = new EventSubscriptionInput();
input.setCallback(URI.create("http://my-service:8080/listener"));
input.setQuery("eventType=ProductOfferingCreateEvent");

HubRegistration reg = hubClient.registerListener(input);
// reg.getId()     → server-assigned subscription ID
// reg.getHubUri() → http://catalog-service:8080/tmf-api/productCatalogManagement/v4/hub

hubClient.unregisterListener(reg.getId());   // unregister against the current server
hubClient.unregisterListener(reg);           // unregister against reg.getHubUri()
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

---

## Error Handling

All errors are surfaced as `OpenTmfClientResponseException` (or the
`OpenTmfClientNotFoundException` subclass for 404s) from `opentmf-http-clients`. No per-client
exception classes are needed.

---

## Migration

Migrating from `opentmf-clients-base` or `opentmf-hub-v4-client`? See
[MIGRATION.md](MIGRATION.md) for the configuration-key and type-name mapping tables.

---

## Version History

See [CHANGELOG.md](CHANGELOG.md) for version history and release notes.
