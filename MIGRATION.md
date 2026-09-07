# Migration guide

This page is for users moving to **`opentmf-api-clients`** from one of the predecessor
libraries. New users do not need to read it — start from the [README](README.md).

---

## From 2.x to 3.0

### Reactive `listPaged*` returns fully materialized pages (breaking)

`ReactiveTmfClient.listPaged*` now returns `Mono<TmfPage<List<R>>>` instead of
`Mono<TmfPage<Flux<R>>>`. The page body is fully materialized before you receive
it, so headers and content can both be read, in any order, more than once, and
the connection is released regardless of what you do with the page.

```java
// before
client.listPaged(page, ProductOffering.class)
    .flatMapMany(TmfPage::getContent)

// after
client.listPaged(page, ProductOffering.class)
    .flatMapIterable(TmfPage::getContent)
```

Metadata accessors (`getTotalElements`, `hasNext`, `isLast`, …) are unchanged.
Sync `listPaged*` is unchanged. No configuration changes.

### Emission timing of reactive `list` / `listAll` (behavioural)

Reactive single-page `list(...)` and `listAll(...)` keep their `Flux<R>`
signatures but now buffer each page before emitting its items, instead of
streaming items as they decode. Per-page memory is bounded by the page size;
cross-page laziness is unchanged — pages are still fetched on demand.

### `entity()` is a new abstract method on the client interfaces

Any class that implements `TmfClient` or `ReactiveTmfClient` directly (rather
than extending the shipped `TmfClientImpl` / `ReactiveTmfClientImpl`) stops
compiling until it implements `entity()`. This is intended breakage in a major
release; extenders of the shipped implementations are unaffected.

### No-auth clients and blank tokens

A `client-ref` pointing at an http-client with no `bearer-auth` / `basic-auth`
block now works and sends no `Authorization` header (see the CHANGELOG). The
flip side: a BEARER or BASIC client whose token service returns a blank token
now fails locally with `IllegalArgumentException` instead of sending
`Authorization: Bearer ` and collecting a remote 401.

## From `opentmf-clients-base`

`opentmf-clients-base` remains in maintenance mode for Spring Boot 3.x users.
`opentmf-api-clients` targets Spring Boot 4.x and Java 17+, and re-shapes the configuration
namespace and the public types.

### Configuration keys

| `opentmf-clients-base` | `opentmf-api-clients` |
|---|---|
| `opentmf.tmf-clients.<id>.base-url` | `opentmf.api-clients.<server>.base-url` |
| `opentmf.tmf-clients.<id>.endpoint` | `opentmf.api-clients.<server>.endpoints.<ep>.path` |
| `opentmf.tmf-clients.<id>.scopes` | `opentmf.api-clients.<server>.endpoints.<ep>.scopes` |

The new shape groups endpoints under a logical *server* so that `base-url` and
`context-path` are written once per server instead of once per endpoint.

### Public types

| `opentmf-clients-base` | `opentmf-api-clients` |
|---|---|
| `GenericClient` | `GenericReactiveTmfClient` or `GenericTmfClient` |
| `TmfClientBaseImpl` | `ReactiveTmfClientImpl` or `TmfClientImpl` |
| `TmfClientProvider` | `ReactiveTmfClientFactory` or `TmfClientFactory` |
| `TmfClientException` (per endpoint) | `OpenTmfClientResponseException` (shared) |

The reactive and synchronous surfaces are now separate modules
(`opentmf-api-clients-reactive` / `opentmf-api-clients-rest`). REST-only apps no longer pull
in WebFlux or Reactor.

---

## From `opentmf-hub-v4-client`

The hub functionality lives in the `opentmf-api-clients-hub` module. It is no longer a
separate library and no longer reactive-only.

| `opentmf-hub-v4-client` | `opentmf-api-clients-hub` |
|---|---|
| `HubClient` (reactive only) | `TmfHubClient` (sync) + `ReactiveTmfHubClient` (reactive) |
| `HubClientImpl extends TmfClientBaseImpl` | `TmfHubClientImpl` / `ReactiveTmfHubClientImpl` (composition) |
| `ExtendedEventSubscription` | `HubRegistration` |
| `EventSubscription` (from `opentmf-v4-models`) | `EventSubscription` (self-contained) |
| `HubClientProvider` + manual bean creation | Auto-configuration detects `/hub` endpoints |
| Throws `UnsupportedOperationException` for CRUD | CRUD methods not exposed at all |

See the [Hub Client](README.md#hub-client) section in the README for the full configuration
and usage examples.

---

## New since 2.1.0: sub-resource paths

`TmfClient` and `ReactiveTmfClient` now expose
`sub(String template, Object... vars)`, returning a derived generic client scoped to a nested
path (e.g. `/order/{orderId}/action/{action}/item`). This is new surface with no v1
equivalent — the predecessor library could not express nested endpoint paths at all. See the
[Sub-resource paths](README.md#sub-resource-paths) section in the README.

## New since 2.0.9: `PUT` verb

Both `TmfClient` and `ReactiveTmfClient` now expose a `put(id, body, ...)` /
`putWithToken(token, id, body, ...)` family alongside the existing CRUD verbs. Use it for
TMF-adjacent endpoints that mandate full-body replace (e.g. re-registering an adapter).
Add a `put: <scope>` row under `endpoints.<ep>.scopes` to bind a dedicated OAuth scope.
