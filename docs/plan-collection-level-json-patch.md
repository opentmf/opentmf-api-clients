# Implementation plan: collection-level JSON Patch (`patchCollection`)

## Background

TMF v4 introduces a "high-performance bulk-creation" pattern: `PATCH` against the
collection root (`/<endpoint>`, no `/{id}` segment) with a body that is an RFC 6902
JSON Patch. Each `add` operation on path `"/"` carries one full resource; the server
responds with a JSON array of created resources in the same order as the patch
operations.

This pattern has already shipped in the retired predecessor library
`opentmf-clients-base` (release `1.1.9`) under the method family
`patchCollection(...)` / `patchCollectionWithToken(...)`. The job here is to bring
the same surface to `opentmf-api-clients`, in both the synchronous and reactive
flavors.

## Scope

In scope:

- New `patchCollection` family on the **synchronous** interface in the common module.
- New `patchCollection` family on the **reactive** interface in the reactive module.
- Implementation in **`TmfClientImpl`** (RestClient, sync) and **`ReactiveTmfClientImpl`**
  (WebClient, reactive).
- IT coverage in both modules.
- CHANGELOG entry under the current `2.0.7-SNAPSHOT` development line.

Explicitly out of scope:

- **No `RestTemplate` implementation.** No such impl exists in this project, and we are
  not adding one. `RestTemplate` is legacy and the project standardized on `RestClient`
  for blocking calls.
- **No `Flux<R>` overloads** on the reactive side. The HTTP roundtrip is single-shot
  (one request, one buffered JSON-array response); a `Flux` would only emit after the
  full body is decoded, so the streaming type would be illusory. Use `Mono<List<R>>`
  exclusively. Callers wanting a `Flux` can append `.flatMapMany(Flux::fromIterable)`.
  This decision is consistent with what landed in the predecessor (`1.1.9`) after
  reviewing the same question.

## Method signatures

The naming and arity mirror the existing `patch(String id, JsonPatch ...)` family.
Eight overloads per interface (4 auto-token + 4 with-token).

### Synchronous — `org.opentmf.api.client.common.api.TmfClient`

```java
// auto-token
List<R> patchCollection(JsonPatch jsonPatch);
List<R> patchCollection(JsonPatch jsonPatch, TmfRequestContext ctx);
<T> List<T> patchCollection(JsonPatch jsonPatch, Class<T> type);
<T> List<T> patchCollection(JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

// with-token
List<R> patchCollectionWithToken(String token, JsonPatch jsonPatch);
List<R> patchCollectionWithToken(String token, JsonPatch jsonPatch, TmfRequestContext ctx);
<T> List<T> patchCollectionWithToken(String token, JsonPatch jsonPatch, Class<T> type);
<T> List<T> patchCollectionWithToken(
    String token, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);
```

`GenericTmfClient` (which extends `TmfClient<Object, Object, Object>`) inherits these
without further changes.

### Reactive — `org.opentmf.api.client.reactive.api.ReactiveTmfClient`

```java
// auto-token
Mono<List<R>> patchCollection(JsonPatch jsonPatch);
Mono<List<R>> patchCollection(JsonPatch jsonPatch, TmfRequestContext ctx);
<T> Mono<List<T>> patchCollection(JsonPatch jsonPatch, Class<T> type);
<T> Mono<List<T>> patchCollection(JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

// with-token
Mono<List<R>> patchCollectionWithToken(String token, JsonPatch jsonPatch);
Mono<List<R>> patchCollectionWithToken(String token, JsonPatch jsonPatch, TmfRequestContext ctx);
<T> Mono<List<T>> patchCollectionWithToken(String token, JsonPatch jsonPatch, Class<T> type);
<T> Mono<List<T>> patchCollectionWithToken(
    String token, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);
```

`GenericReactiveTmfClient` (which extends `ReactiveTmfClient<Object, Object, Object>`)
inherits these without further changes.

## Implementation

### 1. `ReactiveTmfClientImpl` (WebClient, reactive)

Insert a new section between the existing JSON-PATCH and DELETE sections (after
`patchWithToken(... JsonPatch ..., TmfRequestContext ctx, Class<T> type)`, around
line 400). Mirror the existing JSON-PATCH section's structure: four auto-token
delegators that route through `getToken(Scope.PATCH)`, and four with-token methods
where the last one carries the actual logic.

The terminal method body should follow the same style as the existing single-resource
JSON Patch (around lines 388–400 of `ReactiveTmfClientImpl.java`), with two
differences:

1. URI is built without an id, using `UriBuilderUtil.buildUri(serverConfig, endpointConfig, ctx)`.
2. Response is decoded as a list: `.bodyToFlux(type).collectList()`.

Sketch:

```java
@Override public <T> Mono<List<T>> patchCollectionWithToken(
    String token, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
  Objects.requireNonNull(jsonPatch,
      TmfApiClientConstants.ERR_NULL_BODY.formatted(type.getSimpleName()));
  URI uri = buildUri(serverConfig, endpointConfig, ctx);
  var h = prepareAndValidateJsonPatch(headers(token, ctx));
  return webClient.patch().uri(uri).headers(hh -> hh.addAll(h))
      .bodyValue(jsonPatch.toJsonNode())
      .retrieve()
      .onStatus(HttpStatusCode::isError, ReactiveTmfClientImpl::handleError)
      .bodyToFlux(type)
      .collectList()
      .retryWhen(retry());
}
```

The other seven methods are thin delegators identical in shape to those at lines
357–386.

### 2. `TmfClientImpl` (RestClient, sync)

Insert the same section between the JSON-PATCH and DELETE blocks (after
`patchWithToken(... JsonPatch ..., TmfRequestContext ctx, Class<T> type)`, around
line 364). Eight methods, same delegation pattern.

The terminal with-token method needs to deserialize a JSON array into `List<T>`.
**Use the array-class trick rather than `ParameterizedTypeReference`** — it works
naturally with the `Class<T> type` parameter we already have, costs nothing, and
avoids generic-type-token construction:

```java
@Override public <T> List<T> patchCollectionWithToken(
    String token, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
  Objects.requireNonNull(jsonPatch,
      TmfApiClientConstants.ERR_NULL_BODY.formatted(type.getSimpleName()));
  URI uri = buildUri(serverConfig, endpointConfig, ctx);
  var h = prepareAndValidateJsonPatch(headers(token, ctx));
  @SuppressWarnings("unchecked")
  Class<T[]> arrayType = (Class<T[]>) type.arrayType();
  T[] result = withRetry(() -> restClient.patch().uri(uri).headers(hh -> hh.addAll(h))
      .body(jsonPatch.toJsonNode())
      .retrieve()
      .body(arrayType));
  return result == null ? List.of() : Arrays.asList(result);
}
```

Notes:

- `Class.arrayType()` is Java 12+. The project is on Java 17, so this is fine.
- Returning `List.of()` on a null body keeps the contract non-null-friendly. If the
  established convention elsewhere differs (e.g., letting `null` propagate), match
  the convention before merging — re-check after reading neighboring `delete(...,
  Class<T> type)` and `list(...)` impls and follow whichever pattern they use for
  empty/null bodies.

### 3. Helpers — no changes needed

- `UriBuilderUtil.buildUri(server, endpoint, ctx)` already builds the collection-root
  URI (no `/{id}` segment).
- `HeaderUtil.prepareAndValidateJsonPatch(...)` already provides JSON-Patch-aware
  Content-Type defaulting and mismatch logging.
- `getToken(Scope.PATCH)` is the existing token-acquisition path on both impls.

No new util methods are required.

## Tests

### Reactive module IT

File: `opentmf-api-clients-reactive/src/test/java/.../impl/ReactiveTmfClientIT.java`.

Add 6 cases after the existing JSON-Patch tests:

1. `patchCollection` with a 3-op `add` patch — assert returned list has 3 elements
   in order, with expected ids/names from the mock response.
2. `patchCollection(JsonPatch, Class<T>)` with custom return type — assert
   deserialization into the custom POJO.
3. `patchCollectionWithToken` — assert it does not call the token service.
4. 4xx error path — assert the failure surface (existing pattern in the file uses
   `expectErrorMatches` on `TmfApiClientException` or whatever the project's typed
   exception is; mirror what the existing single-resource JSON Patch failure tests
   do).
5. 5xx error path — same shape as (4) with status 500.
6. Empty patch (no ops) — assert the call still hits the server and returns an
   empty list.

### Reactive module mock helpers

File: `opentmf-api-clients-reactive/src/test/java/.../helper/MockServerUtils.java`.

Add two helpers, mirroring the `setUpDynamicJsonPatchCallback` style at lines 98–107
but targeting the collection root (no `/{id}` segment) and returning a static body /
a static error status:

```java
public static void setUpCollectionJsonPatchCallback(
    String path, String responseBody, HttpStatus status) { ... }

public static void setUpCollectionJsonPatchErrorCallback(
    String path, HttpStatus status) { ... }
```

The MockServer expectation should match `withMethod("PATCH")`, `withPath(path)` (no
`/{id}`), and `withHeader("Content-Type", "application/json-patch+json")`.

### REST module IT and helpers

Files:
- `opentmf-api-clients-rest/src/test/java/.../impl/TmfClientIT.java`
- `opentmf-api-clients-rest/src/test/java/.../helper/MockServerUtils.java`

Mirror the reactive cases 1:1, but synchronous: no `StepVerifier`. Direct
`assertEquals` / `assertThrows` style. The REST module already has its own
`MockServerUtils` and `TmfClientIT` — extend them in place.

The `JsonPatch` builder import (`org.opentmf.commons.patch.JsonPatch.builder()`) is
already used in both ITs (e.g., `ReactiveTmfClientIT.java:365`) — reuse it.

## CHANGELOG

`pom.xml` is currently `2.0.7-SNAPSHOT` and the latest released heading is
`## [2.0.6]`. Per the SNAPSHOT-CHANGELOG rule, insert a new
`## [2.0.7] - YYYY-MM-DD` section above `## [2.0.6]`, with today's date when the
release actually ships:

```markdown
## [2.0.7] - YYYY-MM-DD

### Added
- Collection-level JSON Patch (`patchCollection` / `patchCollectionWithToken`) on both
  the synchronous (`TmfClient`, RestClient impl) and reactive (`ReactiveTmfClient`,
  WebClient impl) surfaces. Issues `PATCH` against the collection root (no `/{id}`)
  with content-type `application/json-patch+json` and decodes a JSON array response
  into `List<R>` (sync) / `Mono<List<R>>` (reactive). Supports the TMF v4
  high-performance bulk-creation pattern. No `RestTemplate` implementation; no
  `Flux<R>` overloads. Pure addition: no existing signatures change.
```

## Architecture-test caveat

Both modules ship an `ArchitectureTest` (`ReactiveArchitectureTest.java` /
`RestArchitectureTest.java`). Run them — they may pin allowed return types or
package-membership rules that need a small relaxation for `List<R>` return types on
the new methods. If a rule fails, update the rule rather than working around it.

## Suggested commit shape

One feature commit, mirroring the predecessor's commit message style:

```
feat: collection-level JSON Patch (patchCollection) on TmfClient and ReactiveTmfClient

Ports the patchCollection / patchCollectionWithToken family from the retired
opentmf-clients-base 1.1.9 to both surfaces of opentmf-api-clients.

- Sync: TmfClient + TmfClientImpl (RestClient), List<R> return type.
- Reactive: ReactiveTmfClient + ReactiveTmfClientImpl (WebClient), Mono<List<R>>.
- 4 + 4 overloads each (auto-token / with-token, default / custom return type).
- IT coverage in both modules including 4xx, 5xx and empty-patch cases.
- No RestTemplate implementation; no Flux<R> overloads.
```

## Verification checklist

- [ ] `mvn -pl opentmf-api-clients-common -am verify` passes.
- [ ] `mvn -pl opentmf-api-clients-reactive -am verify` passes (architecture + IT).
- [ ] `mvn -pl opentmf-api-clients-rest -am verify` passes (architecture + IT).
- [ ] `mvn verify` at the reactor root passes.
- [ ] CHANGELOG `## [2.0.7]` heading exists with today's date filled in at release time.
- [ ] No new `RestTemplate` references introduced anywhere.
