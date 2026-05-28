# Implementation plan: add `PUT` verb to `opentmf-api-clients`

## Background

The library currently supports `GET`, `LIST`, `POST`, `PATCH` (merge-patch + json-patch + collection-json-patch), and `DELETE`. **`PUT` is missing.** While TMF v4 itself favors `POST` + `PATCH` for resource mutation, real-world TMF-adjacent APIs do exist that use `PUT` for full-body replace — `dsync-engine`'s `PUT /adapter/{code}` re-registration endpoint is one concrete example surfaced by a downstream consumer.

Adding `PUT` brings the framework to feature parity with HTTP's full mutating verb set and lets downstream client libraries (`dsync-engine-client-provider` etc.) drop their local hand-rolled `PUT` implementations.

This plan mirrors the shape of the existing `patch(String id, U obj, …)` family (the merge-patch variant): same arity, same return types, same parameter ordering, swapping only the HTTP verb and the content-type.

## Scope

In scope:

- New `put` family on the **synchronous** interface in the common module.
- New `put` family on the **reactive** interface in the reactive module.
- Implementation in **`TmfClientImpl`** (RestClient, sync) and **`ReactiveTmfClientImpl`** (WebClient, reactive).
- New `Scope.PUT` enum value so per-operation OAuth scope mapping is symmetric with the other verbs.
- IT coverage in both modules (mirror the merge-patch IT cases).
- CHANGELOG entry under the current `2.0.9-SNAPSHOT` development line.
- `MIGRATION.md` short note pointing v1 users to the new method family (v1 `opentmf-clients-base` did not expose `PUT` either — this is genuinely new surface, no breaking change to flag, but worth a sentence so users know it's there).

Explicitly out of scope:

- **No `RestTemplate` implementation.** Same rationale as the patch-collection plan: no `RestTemplate` impl exists in this project, and `RestClient` is the blocking client we standardized on.
- **No collection-level PUT** (e.g. `PUT /<endpoint>` with an array body for bulk replace). Not a TMF idiom; no driving requirement.
- **No `PUT` with `JsonPatch` body.** PUT is full-body replace by definition; the patch families already cover the partial-update use cases.

## Method signatures

Eight overloads per interface (4 auto-token + 4 with-token), mirroring the merge-patch family. Body type is the update DTO type `U` — same as merge-patch — and the return type follows the standard `R` (typed) / `<T> T` (caller-supplied response type) split.

### Synchronous — `org.opentmf.api.client.common.api.TmfClient`

```java
// auto-token
R put(String id, U obj);
R put(String id, U obj, TmfRequestContext ctx);
<T> T put(String id, U obj, Class<T> type);
<T> T put(String id, U obj, TmfRequestContext ctx, Class<T> type);

// with-token
R putWithToken(String token, String id, U obj);
R putWithToken(String token, String id, U obj, TmfRequestContext ctx);
<T> T putWithToken(String token, String id, U obj, Class<T> type);
<T> T putWithToken(String token, String id, U obj, TmfRequestContext ctx, Class<T> type);
```

Place these declarations after the existing `// JSON PATCH` block and before the `// COLLECTION JSON PATCH` block, in their own `// PUT` section. (Keep the file's existing top-to-bottom verb ordering: GET → LIST → POST → PATCH → JSON PATCH → COLLECTION JSON PATCH → DELETE; insert PUT between COLLECTION JSON PATCH and DELETE — see "Ordering" note at the end of this plan.)

### Reactive — `org.opentmf.api.client.reactive.api.ReactiveTmfClient`

```java
// auto-token
Mono<R> put(String id, U obj);
Mono<R> put(String id, U obj, TmfRequestContext ctx);
<T> Mono<T> put(String id, U obj, Class<T> type);
<T> Mono<T> put(String id, U obj, TmfRequestContext ctx, Class<T> type);

// with-token
Mono<R> putWithToken(String token, String id, U obj);
Mono<R> putWithToken(String token, String id, U obj, TmfRequestContext ctx);
<T> Mono<T> putWithToken(String token, String id, U obj, Class<T> type);
<T> Mono<T> putWithToken(String token, String id, U obj, TmfRequestContext ctx, Class<T> type);
```

## Implementation

### `Scope` (`opentmf-api-clients-common/src/main/java/org/opentmf/api/client/common/model/Scope.java`)

Add a `PUT` entry alongside the existing values:

```java
public enum Scope {
  GET("get"),
  LIST("list"),
  POST("post"),
  PUT("put"),       // ← NEW
  PATCH("patch"),
  DELETE("delete");
  // ... unchanged ...
}
```

The `REVERSE_MAP` rebuilds automatically from `values()`; no other change needed in this class.

**Configuration impact**: `TmfApiClientsConfig.EndpointConfig.scopes` is `Map<Scope, String>`. Existing application configs that read e.g. `scopes.put: PUT_SCOPE` from YAML will now resolve. This is purely additive — no existing configuration breaks. Update the Javadoc on `TmfApiClientsConfig` (the YAML example block) to include a `put:` scope row alongside the others.

### Synchronous implementation — `TmfClientImpl`

`opentmf-api-clients-rest/src/main/java/org/opentmf/api/client/rest/impl/TmfClientImpl.java`

Add a new `// PUT` section, mirroring the merge-patch implementation but swapping the verb and the content-type helper. Insert it between the `// COLLECTION JSON PATCH` block (ending around line ~430) and the `// DELETE` block.

```java
// ==========================================================================
// PUT
// ==========================================================================

@Override public R put(String id, U obj) {
  return put(id, obj, responseType);
}

@Override public R put(String id, U obj, TmfRequestContext ctx) {
  return put(id, obj, ctx, responseType);
}

@Override public <T> T put(String id, U obj, Class<T> type) {
  return put(id, obj, null, type);
}

@Override public <T> T put(String id, U obj, TmfRequestContext ctx, Class<T> type) {
  return putWithToken(getToken(Scope.PUT), id, obj, ctx, type);
}

@Override public R putWithToken(String token, String id, U obj) {
  return putWithToken(token, id, obj, responseType);
}

@Override public R putWithToken(String token, String id, U obj, TmfRequestContext ctx) {
  return putWithToken(token, id, obj, ctx, responseType);
}

@Override public <T> T putWithToken(String token, String id, U obj, Class<T> type) {
  return putWithToken(token, id, obj, null, type);
}

@Override public <T> T putWithToken(
    String token, String id, U obj, TmfRequestContext ctx, Class<T> type) {
  Objects.requireNonNull(obj,
      TmfApiClientConstants.ERR_NULL_BODY.formatted(type.getSimpleName()));
  URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx);
  var h = prepareAndValidate(headers(token, ctx), MediaType.APPLICATION_JSON);
  return withRetry(() -> restClient.put().uri(uri).headers(hh -> hh.addAll(h))
      .body(obj).retrieve().body(type));
}
```

Notes:

- `prepareAndValidate(consumer, MediaType.APPLICATION_JSON)` — the generic helper used for `POST` is reused as-is; PUT carries an `application/json` body (no dedicated media type). **No new `HeaderUtil` method is needed.**
- `buildUriWithId(serverConfig, endpointConfig, id, ctx)` — same URI builder that merge-patch uses. Already supports the `TmfRequestContext` query-param overlay.
- `withRetry(...)` — same retry envelope used by every other verb.
- `Objects.requireNonNull(obj, ...)` null-check on the body mirrors the merge-patch impl. The error message uses `TmfApiClientConstants.ERR_NULL_BODY` — no new constant needed.

### Reactive implementation — `ReactiveTmfClientImpl`

`opentmf-api-clients-reactive/src/main/java/org/opentmf/api/client/reactive/impl/ReactiveTmfClientImpl.java`

Mirror the merge-patch reactive impl (around lines 310-360 in the current file) but swap `.patch()` → `.put()` and the helper to `prepareAndValidate(..., MediaType.APPLICATION_JSON)`. Insert in the same relative position (between the existing `// COLLECTION JSON PATCH` and `// DELETE` blocks).

```java
// ==========================================================================
// PUT
// ==========================================================================

@Override public Mono<R> put(String id, U obj) {
  return put(id, obj, responseType);
}

@Override public Mono<R> put(String id, U obj, TmfRequestContext ctx) {
  return put(id, obj, ctx, responseType);
}

@Override public <T> Mono<T> put(String id, U obj, Class<T> type) {
  return put(id, obj, null, type);
}

@Override public <T> Mono<T> put(String id, U obj, TmfRequestContext ctx, Class<T> type) {
  return getToken(Scope.PUT).flatMap(t -> putWithToken(t, id, obj, ctx, type));
}

@Override public Mono<R> putWithToken(String token, String id, U obj) {
  return putWithToken(token, id, obj, responseType);
}

@Override public Mono<R> putWithToken(String token, String id, U obj, TmfRequestContext ctx) {
  return putWithToken(token, id, obj, ctx, responseType);
}

@Override public <T> Mono<T> putWithToken(String token, String id, U obj, Class<T> type) {
  return putWithToken(token, id, obj, null, type);
}

@Override public <T> Mono<T> putWithToken(
    String token, String id, U obj, TmfRequestContext ctx, Class<T> type) {
  Objects.requireNonNull(obj,
      TmfApiClientConstants.ERR_NULL_BODY.formatted(type.getSimpleName()));
  URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx);
  var h = prepareAndValidate(headers(token, ctx), MediaType.APPLICATION_JSON);
  return withRetry(() -> webClient.put().uri(uri).headers(hh -> hh.addAll(h))
      .bodyValue(obj).retrieve().bodyToMono(type));
}
```

The reactive equivalent of `restClient.body(obj)` is `webClient.bodyValue(obj)` — pre-existing pattern in the merge-patch reactive impl.

## Integration tests

### Synchronous — `opentmf-api-clients-rest/src/test/java/.../TmfClientIT.java`

Add a `// --- PUT ---` section after the existing `// --- MERGE PATCH ---` section (around line 276). Cover the same scenario shapes used for merge-patch + at least one with-token and one custom-return-type case. Suggested test methods (names follow the existing convention):

```java
// --- PUT ---

@Test
void put_replacesResource() {
  // Arrange: seed via POST, capture id.
  // Act:     client.put(id, updateBody);
  // Assert:  returned model reflects the put body fields.
}

@Test
void put_withRequestContext() {
  // Same as above but passes a TmfRequestContext with a header and/or query param.
  // Assert: MockServer received the expected header / query.
}

@Test
void put_withCustomReturnType() {
  // client.put(id, updateBody, TestResponseClass.class)
  // Assert: returned instance is of the requested type, not the default R.
}

@Test
void putWithToken_useCallerSuppliedToken() {
  // client.putWithToken("custom-token", id, updateBody)
  // Assert: MockServer saw "Authorization: Bearer custom-token" (or whatever the
  // configured token-type prefix is).
}

@Test
void put_nullBody_throwsNullPointerException() {
  // client.put(id, null, TestResponseModel.class)
  // assertThatThrownBy(...).isInstanceOf(NullPointerException.class);
}
```

### MockServer expectation wiring

Extend `MockServerUtils.java`:

- Add a `mockSyncPutResource(path)` helper that expects `withMethod("PUT").withPath(path).withHeader("Content-Type", "application/json")` and replies with 200 + a modified copy of the body the server received (mirror `mockSyncPatchResource(...)` patterns at lines ~91-105 of the current file).
- Update the `scopes` test fixture in the IT setup to include `Scope.PUT, "PUT_SCOPE"` so the token mock returns a recognizable scope-bearing token.

### Reactive — `opentmf-api-clients-reactive/src/test/java/.../ReactiveTmfClientIT.java`

Mirror the sync tests with `StepVerifier`:

```java
@Test
void put_replacesResource() {
  StepVerifier.create(client.put(id, updateBody))
      .assertNext(res -> assertThat(res.getName()).isEqualTo(...))
      .verifyComplete();
}
```

Cover the same five cases as the sync side. Add a `mockReactivePutResource(...)` helper to the reactive `MockServerUtils.java` paralleling the sync version.

### Architecture tests

`opentmf-api-clients-rest/src/test/java/.../RestArchitectureTest.java` (and the reactive counterpart, if present): if any rule asserts that every public method on `TmfClient` has a matching implementation in `TmfClientImpl`, the new `put` overloads need to be picked up automatically. If a rule explicitly enumerates verb names, add `"put"` and `"putWithToken"` to the list. Otherwise no change.

## File checklist

Changes touch the following files (and only these):

**Common module** — `opentmf-api-clients-common/src/main/java/org/opentmf/api/client/common/`:
- `model/Scope.java` — add `PUT("put")`.
- `api/TmfClient.java` — add 8 method declarations (4 + 4) in a new `// PUT` block.
- `config/TmfApiClientsConfig.java` — update the YAML example in the class Javadoc to include `put: PUT_SCOPE` under `scopes`.

**REST module** — `opentmf-api-clients-rest/src/main/java/org/opentmf/api/client/rest/`:
- `impl/TmfClientImpl.java` — add the 8 method implementations.

**REST tests** — `opentmf-api-clients-rest/src/test/java/org/opentmf/api/client/rest/`:
- `impl/TmfClientIT.java` — add the 5 PUT test cases.
- `helper/MockServerUtils.java` — add `mockSyncPutResource(...)` + extend the scope fixture map.
- `arch/RestArchitectureTest.java` — extend rule if it enumerates verb names (verify; otherwise no change).

**Reactive module** — `opentmf-api-clients-reactive/src/main/java/org/opentmf/api/client/reactive/`:
- `api/ReactiveTmfClient.java` — add 8 Mono-returning declarations.
- `impl/ReactiveTmfClientImpl.java` — add the 8 method implementations.

**Reactive tests** — `opentmf-api-clients-reactive/src/test/java/org/opentmf/api/client/reactive/`:
- `impl/ReactiveTmfClientIT.java` — add the 5 PUT test cases.
- `helper/MockServerUtils.java` — add `mockReactivePutResource(...)` + extend the scope fixture map.

**Project root**:
- `CHANGELOG.md` — add entry (see template below).
- `MIGRATION.md` — one-line addition pointing at the new method family.
- `README.md` — if there's a "Supported HTTP verbs" table, add a row for PUT.

**`opentmf-versions` BOM** (separate project):
- No changes needed. `opentmf-api-clients` versions are already wired through `${opentmf-api-clients.version}` and ride the existing release flow.

## CHANGELOG entry (under `2.0.9-SNAPSHOT` / next release section)

```markdown
## [2.0.9] - YYYY-MM-DD

### Added
- `PUT` verb on both the synchronous (`TmfClient`, RestClient impl) and reactive
  (`ReactiveTmfClient`, WebClient impl) surfaces. Eight overloads per interface (4
  auto-token + 4 with-token), mirroring the existing merge-patch family. Issues
  `PUT /{endpoint}/{id}` with content-type `application/json` against the same URI
  + header + retry machinery used by `POST` / `PATCH`. Pure addition: no existing
  signatures change.
- New `Scope.PUT` enum value (`"put"`) so per-endpoint OAuth scope maps can carry a
  dedicated `put:` entry. Fully additive; existing configurations are unaffected.
```

(Strip `-SNAPSHOT` from the heading per the global "CHANGELOG rule for SNAPSHOT versions" rule.)

## Verification checklist

- `mvn -B clean verify` from the project root passes.
- All new PUT tests show up in the surefire / failsafe report; total IT count went up by **10** (5 sync + 5 reactive).
- `TmfClient` / `ReactiveTmfClient` Javadoc still renders (`mvn -B javadoc:javadoc` is clean).
- `MIGRATION.md` mentions the new family.
- A consumer can write `tmfClient.put(id, body)` and have it compile + execute end-to-end against a mock server.

## Ordering note

The current file ordering inside `TmfClient.java` and the impl files is verb-by-verb (GET, LIST, POST, PATCH, JSON PATCH, COLLECTION JSON PATCH, DELETE). Logically PUT sits between POST and PATCH in HTTP-verb conventional order, but inserting it there would force renumbering / re-shuffling of the existing PATCH section in the file (and produce a noisier diff). **Insert the PUT block between COLLECTION JSON PATCH and DELETE** — at the bottom of the mutating-verb cluster, immediately before DELETE. The interface and both impls then read top-to-bottom as: GET, LIST, POST, PATCH (merge), PATCH (json), PATCH (collection json), PUT, DELETE. Tidy diff, no churn on existing PATCH code.

## Open questions

- **Default scope on `put` when no `Scope.PUT` is configured.** The current `getToken(Scope)` helper falls back to a no-scope token when the requested scope is absent in `endpointConfig.getScopes()`. PUT inherits that behavior automatically — no new code needed. Confirm during impl that this is intentional and documented.
- **Should `Scope.PUT` get a JSON-deserialization shortcut alias `"put"` only**, or also accept legacy mixed-case forms? The existing enum's `fromValue` is strict (`"get"` / `"list"` / etc., all lowercase). PUT should follow suit — no aliasing. Documented for clarity.
- **Whether to expose a `prepareAndValidatePut(...)` helper on `HeaderUtil`** for symmetry with `prepareAndValidateMergePatch` / `prepareAndValidateJsonPatch`. **Decision: no.** PUT carries an `application/json` body identical to POST's; the generic `prepareAndValidate(consumer, MediaType.APPLICATION_JSON)` already serves both. Adding a third helper just to read like the patch ones would be sugar without substance. Document this explicitly in the impl section so a future contributor doesn't add it speculatively.
