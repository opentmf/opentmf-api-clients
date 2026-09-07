# Implementation plan: expose response headers (and align `listPaged`)

> **Status:** approved in design, NOT yet implemented. Written against
> `2.1.1-SNAPSHOT` (develop, 2026-09-07). This plan carries a deliberate
> **backward-incompatible** change and therefore targets a **3.0.0** release.
>
> **Decided:** the entity view is reached by `entity()`; the no-auth fix in
> [`plan-fix-no-auth-clients.md`](plan-fix-no-auth-clients.md) ships in the same
> 3.0.0 release (see *Bundled: no-auth support*).

## Background

Every method on `TmfClient` and `ReactiveTmfClient` returns a deserialized
**body**. Nothing on either interface returns a `ResponseEntity`, `HttpHeaders`,
or a status code. A caller who needs `Location` after a create, an `ETag` for a
conditional follow-up, a `Retry-After` on a 202, or any custom `X-*` header has
no way to get it through the library.

The implementations terminate in body-only calls:

| | sync | reactive |
|---|---|---|
| single object | `.retrieve().body(type)` — `TmfClientImpl.java:154` | `.bodyToMono(type)` — `ReactiveTmfClientImpl.java:170` |
| delete | `.toBodilessEntity()` then discard — `TmfClientImpl.java:507` | `.toBodilessEntity().then()` — `ReactiveTmfClientImpl.java:552` |

The `delete` lines are the sharpest illustration: both transports already hold a
`ResponseEntity` and throw it away.

### The one place headers are read today

`retrieveSinglePageWithResponse` uses `.toEntity(arrayType)` (sync,
`TmfClientImpl.java:538`) / `.toEntityFlux(type)` (reactive,
`ReactiveTmfClientImpl.java:585`), feeds the entity to `ResponseHeaderUtil` for
`X-Total-Count` and `Content-Range`, and then discards the entity. So
pagination metadata escapes; the headers themselves do not.

### What already works — the error path

Response headers **are** reachable when a call fails, on both transports:

```java
try {
  var po = client.get(id);
} catch (OpenTmfClientResponseException ex) {
  HttpHeaders headers = ex.getHeaders();     // nullable
  Duration retryAfter = ex.getRetryAfter();  // nullable
}
```

Populated by `OpenTmfRestClientStatusHandler.handleError` (sync) and
`WebClientConfigUtil.errorWrappingFilter` (reactive), both calling
`ex.setResponseDetails(headers, ...)` in `opentmf-http-clients`. The javadoc on
`OpenTmfClientResponseException.ResponseDetails` states the reason: those are
*"the only point at which they are still reachable, since the response is closed
before callers see it."*

**This plan therefore closes a success-path gap only.** No change is needed in
`opentmf-http-clients`, and the error-path behaviour must be documented rather
than reimplemented.

---

## Scope

**In scope**

- A parallel **entity view** on both client surfaces, reached via a new
  `entity()` method, whose verbs return `ResponseEntity<...>` instead of bodies.
- Refactor of `TmfClientImpl` / `ReactiveTmfClientImpl` so each verb has a single
  entity-returning core, with the existing body-returning methods becoming thin
  unwrappers over it.
- **Breaking:** align reactive `listPaged*` from `Mono<TmfPage<Flux<R>>>` to
  `Mono<TmfPage<List<R>>>`.
- Version bump to `3.0.0-SNAPSHOT`, new `## [3.0.0]` CHANGELOG section, a
  `MIGRATION.md` "From 2.x to 3.0" section, README updates.
- IT coverage in both `-rest` and `-reactive` modules.
- **Bundled:** the `AuthType.NONE` fix from
  [`plan-fix-no-auth-clients.md`](plan-fix-no-auth-clients.md), which owns its
  own design detail. Only its release coupling is decided here.

**Out of scope**

- **No entity variants for `listAll*`.** Decided below (D2).
- **No entity variants for `listPaged*`.** `TmfPage` already *is* the
  header-derived view of a list response; a `ResponseEntity<TmfPage<...>>` would
  expose the same headers twice in one value.
- **No hub-client *entity view*.** `TmfHubClient` / `ReactiveTmfHubClient` are a
  deliberately narrow 5-method surface (`registerListener`,
  `unregisterListener` ×3). (The bundled no-auth fix *does* touch both hub
  impls — they share the broken header path — but adds no new surface there.) `registerListener` already returns a synthesized
  `HubRegistration`, and no consumer has asked for hub response headers. Adding
  an entity view there would be surface for its own sake.
- **No `RestTemplate` implementation.** Unchanged project position: this library
  ships `RestClient` + `WebClient` only.
- **No change to `opentmf-http-clients`.**
- **No change to the error path.** It already works; it only needs documenting.
- **No entity view on the no-auth path specifically.** The two changes meet only
  in `HeaderUtil` and are otherwise independent (see *Bundled* below).

---

## Design decisions

### D1 — The entity view is a separate interface reached by `entity()`, not `…Entity`-suffixed methods on the existing one

```java
ResponseEntity<ProductOffering> re = client.entity().get(id);
String location = client.entity().post(input).getHeaders().getFirst("Location");
```

**Why.** Mirroring the existing ladder in place would add ~64 methods to
`TmfClient`, roughly doubling an interface that is already ~70 methods, and
would force names like `patchCollectionEntityWithToken(token, jsonPatch, ctx,
type)`. The method *count* is identical either way — the ladder has to exist
somewhere — so the only real difference is legibility, and the suffix form loses
on every axis:

- the primary interface, which is what most users read, stays at its current size;
- no name mangling: inside the entity view the verbs keep their existing names,
  so `client.entity().patchCollectionWithToken(...)` reads like its body twin;
- **the entity view can omit what does not belong** (`listAll*`, `listPaged*`,
  `sub`) instead of leaving a partial, unexplained mirror in the main interface.

That last point is the decisive one. A suffix mirror would silently lack
`listAllEntity` and readers would have to guess whether it is an oversight.

`sub()` is not repeated on the entity view — `client.sub("/{id}/item", id).entity()`
already composes.

### D2 — No entity variant for `listAll*`

`listAll` walks N pages via `recursiveRetrieve`. There are N responses and
therefore N header sets. Any single `ResponseEntity` would have to pick one —
"the last page's headers" — which is a value that looks authoritative and is
not. Omitted deliberately; the omission is documented in the interface javadoc
so it reads as a decision, not a gap.

### D3 — Reactive list bodies are `List<R>`, never `Flux<R>`, inside a `ResponseEntity`

The reactive single-page entity method returns `Mono<ResponseEntity<List<R>>>`,
**not** `Mono<ResponseEntity<Flux<R>>>`, even though the latter is what
`toEntityFlux` hands you.

**Why.** A body `Flux` inside a `ResponseEntity` is a live, single-subscription
connection stream wearing the clothes of a plain container:

1. **The intended use of this feature is the use that abandons the body.** The
   motivating request is "I just want the `ETag`" — and a caller who reads
   headers and never subscribes to the body leaves the response undrained.
   Reactor Netty does not return the connection to the pool until the body
   completes or is cancelled.

2. **`getBody()` would return something readable only once.**
   `entity.getBody().count()` followed by `entity.getBody().collectList()` fails
   — a body `Flux` rejects a second subscriber. Every other `ResponseEntity` a
   Java developer has held is re-readable, so the type actively misleads.

3. **There is nothing to stream.** These are single-page operations, bounded by
   `pageable.getPageSize()`. Streaming matters for `listAll`, which by D2 has no
   entity variant at all.

The cost is losing backpressure *within one page*. The sync side already buffers
a page (`TmfClientImpl.java:536-541` does `toEntity(arrayType)` then `List.of(body)`),
so this also makes the two modules structurally parallel — differing only by the
`Mono` wrapper.

### D4 — Existing `listPaged*` is aligned to the same rule (breaking)

`ReactiveTmfClient.listPaged*` currently returns `Mono<TmfPage<Flux<R>>>` and
carries exactly the hazard D3 rejects. Two of our own ITs demonstrate it —
`ReactiveTmfClientIT.java:291` and `:311` read `page.getTotalElements()` /
`page.isLast()` and never subscribe to `page.getContent()`:

```java
StepVerifier.create(client.listPaged(pageable))
    .assertNext(page -> {
      assertThat(page.getTotalElements()).isEqualTo(20);
      assertThat(page.hasNext()).isTrue();
    })
    .verifyComplete();          // body Flux abandoned
```

Against a mock server in a short-lived JVM this is invisible. In a service doing
it per request it is not. Since 3.0.0 is being cut anyway, `listPaged*` moves to
`Mono<TmfPage<List<R>>>` so that one rule — *a response value handed to a caller
is fully materialized* — holds across the whole library, and the new entity
methods are not a special case that quietly contradicts the old ones.

**Behaviour change to state plainly:** after this, `listAll` buffers each page
before emitting its items, instead of streaming items within a page. Per-page
memory is bounded by the page size, and `retrieveAllPagesWithClientFilter`
already collects everything into a list regardless. Cross-page laziness is
unchanged — pages are still fetched on demand.

### D5 — Entity methods do not change error handling or retry

Entity calls keep the same `defaultStatusHandler` / `onStatus` wrapping and the
same `withRetry` / `retryWhen`. A non-2xx still throws
`OpenTmfClientResponseException`; it does **not** arrive as a `ResponseEntity`
with an error status. Headers on failures continue to come from
`ex.getHeaders()`. Making the entity view return error statuses instead would
fork the error model in two, which is worse than the mild asymmetry of "success
→ entity, failure → exception".

---

## API surface

### New interfaces

```
opentmf-api-clients-common/.../common/api/TmfEntityClient.java
opentmf-api-clients-reactive/.../reactive/api/ReactiveTmfEntityClient.java
```

Each carries the same eight verb families as its body twin, minus `listAll*`,
`listPaged*` and `sub`:

`get` · `list` (single page) · `post` · `put` · `patch` (merge) ·
`patch` (JsonPatch) · `patchCollection` · `delete`

Each family keeps the established **8-overload ladder** (4 auto-token + 4
with-token). Spelled out for `get`, sync:

```java
// auto-token
ResponseEntity<R> get(String id);
ResponseEntity<R> get(String id, TmfRequestContext ctx);
<T> ResponseEntity<T> get(String id, Class<T> type);
<T> ResponseEntity<T> get(String id, TmfRequestContext ctx, Class<T> type);

// with token
ResponseEntity<R> getWithToken(String token, String id);
ResponseEntity<R> getWithToken(String token, String id, TmfRequestContext ctx);
<T> ResponseEntity<T> getWithToken(String token, String id, Class<T> type);
<T> ResponseEntity<T> getWithToken(String token, String id, TmfRequestContext ctx, Class<T> type);
```

Reactive is the same ladder wrapped in `Mono`:

```java
Mono<ResponseEntity<R>> get(String id);
// …
```

Return-type mapping per family:

| family | sync | reactive |
|---|---|---|
| `get`, `post`, `put`, `patch` (both) | `ResponseEntity<R>` / `<T> ResponseEntity<T>` | `Mono<ResponseEntity<R>>` / `Mono<ResponseEntity<T>>` |
| `list` (single page), `patchCollection` | `ResponseEntity<List<R>>` / `<T> ResponseEntity<List<T>>` | `Mono<ResponseEntity<List<R>>>` / `Mono<ResponseEntity<List<T>>>` |
| `delete` (no type) | `ResponseEntity<Void>` | `Mono<ResponseEntity<Void>>` |
| `delete` (typed) | `<T> ResponseEntity<T>` | `Mono<ResponseEntity<T>>` |

Note `delete`'s untyped form gains a real return value — `ResponseEntity<Void>`
carries the status code, which today's `void delete(id)` discards.

### New method on the existing interfaces

```java
// TmfClient<C, U, R>
TmfEntityClient<C, U, R> entity();

// ReactiveTmfClient<C, U, R>
ReactiveTmfEntityClient<C, U, R> entity();
```

Generic clients need no new type: `GenericTmfClient extends TmfClient<Object,
Object, Object>` already yields `TmfEntityClient<Object, Object, Object>`.

### Changed (breaking)

```java
// ReactiveTmfClient — all 8 listPaged overloads
- Mono<TmfPage<Flux<R>>> listPaged(...);
+ Mono<TmfPage<List<R>>> listPaged(...);
```

Sync `listPaged*` is already `TmfPage<List<R>>` and does not change.

---

## Implementation approach

### Single entity-returning core per verb

Today each verb's "full" overload does the work and returns a body. Invert that:
the full overload returns a `ResponseEntity`, and the body method unwraps it.
`TmfClientImpl.getWithToken` becomes:

```java
// core — used by both views
<T> ResponseEntity<T> getEntityCore(String token, String id, TmfRequestContext ctx, Class<T> type) {
  URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx, subPath);
  var h = prepareGetDelete(headers(token, ctx));
  return withRetry(() -> restClient.get().uri(uri).headers(hh -> hh.addAll(h))
      .retrieve().toEntity(type));
}

@Override public <T> T getWithToken(String token, String id, TmfRequestContext ctx, Class<T> type) {
  return getEntityCore(token, id, ctx, type).getBody();
}
```

`TmfEntityClientImpl` is then a thin adapter holding a reference to its owner
`TmfClientImpl` and delegating to the cores. It cannot be the *same* class:
`R get(String id)` and `ResponseEntity<R> get(String id)` differ only in return
type, which Java forbids on one class.

`entity()` returns a cached instance (created once in the constructor) — the
adapter is stateless beyond its owner reference, so allocating per call would be
waste.

### Two traps to get right

**1. Reactive null bodies.** `bodyToMono(type)` yields an *empty* `Mono` for an
empty body. `Mono.map(ResponseEntity::getBody)` on a null body throws
`NullPointerException` ("The mapper returned a null value"). The unwrapper must
therefore be:

```java
.flatMap(e -> Mono.justOrEmpty(e.getBody()))   // NOT .map(ResponseEntity::getBody)
```

A 204/empty-body regression test belongs in the suite for exactly this.

**2. `body(type)` → `toEntity(type)` is a refactor of shipped code paths.** Both
read the full body and both honour the status handler, so the change should be
behaviour-neutral — but it touches every verb on both transports. The existing
IT suites are the regression guard and must pass **unchanged** (apart from the
`listPaged` call sites in D4). Do the refactor as its own commit, green, before
adding any new interface.

### `listPaged` alignment mechanics (reactive)

```java
private <T> Mono<TmfPage<List<T>>> retrieveSinglePageWithResponse(
    String token, Pageable pageable, Class<T> type) {
  URI uri = withPagination(buildBaseUri(serverConfig, endpointConfig, subPath), pageable);
  var h = prepareGetDelete(headers(token, toContext(pageable)));

  Class<T[]> arrayType = (Class<T[]>) Array.newInstance(type, 0).getClass();
  return webClient.get().uri(uri).headers(hh -> hh.addAll(h))
      .retrieve()
      .onStatus(HttpStatusCode::isError, ReactiveTmfClientImpl::handleError)
      .toEntity(arrayType)                       // was: toEntityFlux(type)
      .retryWhen(retry())
      .map(entity -> buildPage(entity, pageable));
}
```

`buildPage` takes `ResponseEntity<T[]>` and passes `List.of(body)` (or
`List.of()` when null) into `OffsetPage` — the same two `ResponseHeaderUtil`
calls as today, and now identical to the sync implementation.

`recursiveRetrieve` changes its concat source:

```java
- return Flux.concat(page.getContent(), recursiveRetrieve(token, page.getNextPageable(), type));
+ return Flux.concat(Flux.fromIterable(page.getContent()),
+     recursiveRetrieve(token, page.getNextPageable(), type));
```

`OffsetPage` and `TmfPage` are generic in the content type and need **no**
change.

---

## Versioning and release

- Bump every module `2.1.1-SNAPSHOT` → `3.0.0-SNAPSHOT` via
  `mvn versions:set -DnewVersion=3.0.0-SNAPSHOT -DprocessAllModules
  -DgenerateBackupPoms=false`. **This is the first commit on the branch** — see
  *Work order* for why.
- New `## [3.0.0] - <release date>` section in `CHANGELOG.md` above `## [2.1.0]`
  (bare numeric heading, no `-SNAPSHOT`), with `### Added` for the entity view
  and `### Changed` for the `listPaged` signature.
- Major is required by the `listPaged` signature change. It is both
  source- and binary-incompatible for callers that name the type.

### Bundled: no-auth (`AuthType.NONE`) support

[`plan-fix-no-auth-clients.md`](plan-fix-no-auth-clients.md) ships in the same
3.0.0 release. It is a **non-breaking fix** and does not need a major — the
reason to bundle it is sequencing, not semantics:

- Both changes edit `HeaderUtil` and the `headers(...)` factories that feed it.
  There are **four** of those, not two — `TmfClientImpl.java:113`,
  `ReactiveTmfClientImpl.java:123`, `TmfHubClientImpl.java:126` and
  `ReactiveTmfHubClientImpl.java:135`. Landing the two changes in separate
  releases means touching the same code twice and reviewing the second change
  against a moving base.
- The defect is confirmed still present at `2.1.1-SNAPSHOT` (re-verified
  2026-09-07 — see that plan's *Empirical verification*). A NONE-auth client
  throws on **every** call, so there is nothing to lose by shipping the fix
  sooner.
- 3.0.0 is the release consumers will read the migration notes for. A behaviour
  change that *unblocks* a previously impossible configuration is best announced
  where people are already looking.

**Ordering constraint.** Do the no-auth fix **before** the entity refactor
(work-order step 3). It rewrites `validateAuthorization` and `headersConsumer`; the entity
refactor rewrites the code that *calls* them. Fix-then-refactor keeps each
commit's diff about one thing. That plan's own tests must be green before the
entity work starts.

**No upstream release is on the critical path.** The no-auth fix reads
`ClientProperties.getAuthType() != AuthType.NONE` — a value all four factories
already hold — instead of adding a method to `SyncTokenService` / `TokenService`
in `opentmf-http-clients`. Both changes are therefore contained in this
repository, and 3.0.0 does not wait on an `opentmf-http-clients` release. See
that plan's *Why not `isAuthenticationRequired()` on the token service*.

The two changes are otherwise independent: no-auth touches request headers, the
entity view touches response handling.

---

## Migration note (for `MIGRATION.md`)

### From 2.x to 3.0

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

---

## Tests

**`opentmf-api-clients-rest`** — `TmfClientIT` / `GenericTmfClientIT`:

- per verb family, one entity IT asserting status, a seeded custom response
  header, and body equality with the body-returning twin;
- `entity().delete(id)` returns `ResponseEntity<Void>` with the expected status;
- `entity().list(...)` returns `ResponseEntity<List<R>>` and reads
  `X-Total-Count` off the entity directly;
- existing body-returning ITs pass unchanged (the refactor guard).

**`opentmf-api-clients-reactive`** — `ReactiveTmfClientIT`:

- the same matrix via `StepVerifier`;
- **empty-body test**: a 204 (or empty 200) response completes empty rather than
  erroring — the `justOrEmpty` trap;
- `listPaged` ITs updated to `flatMapIterable`; add one asserting content is
  readable **twice** off the same page, which is impossible under the old
  `Flux` shape and is the point of the change;
- an `entity()` call whose body is ignored must still complete and release —
  assert via `StepVerifier` on a follow-up call succeeding against a
  small connection pool.

**Coverage.** Both modules enforce per-`BUNDLE` JaCoCo at 80% LINE /
INSTRUCTION / BRANCH with `CLASS MISSEDCOUNT = 0` (root `pom.xml:258-310`). The
new adapters are almost entirely one-line delegations, so the ITs must exercise
every overload or the ladder will drag the ratio down. Budget for that: it is
~64 methods per module.

---

## Documentation

- **README** — new "Response headers" section after "Error Handling": the
  `entity()` view, the `listAll` omission and why, and the already-working
  error path via `OpenTmfClientResponseException.getHeaders()`. Update the
  reactive pagination snippet to `Mono<TmfPage<List<ProductOffering>>>`.
- **Interface javadoc** — on both entity interfaces, state that errors still
  throw (D5) and that `listAll` has no entity form and why (D2).
- **MIGRATION.md** — the section above.

---

## Work order

1. **Version bump to `3.0.0-SNAPSHOT`** across every module, plus the empty
   `## [3.0.0]` CHANGELOG heading.
2. **No-auth fix** per its own plan (`HeaderUtil` + all four `headers(...)`
   factories, including both hub clients), with its regression tests. No
   `opentmf-http-clients` change is required.
3. **Sync entity-core refactor** — every verb becomes an entity-returning core
   with the body method unwrapping it. No public API change; the untouched IT
   suite is the guard.
4. **Reactive entity-core refactor** — same shape, plus the `justOrEmpty`
   handling and the 204/empty-body test.
5. **`listPaged` alignment** — reactive interface + impl + ITs + README snippet
   + `MIGRATION.md` + CHANGELOG `### Changed`.
6. **Sync entity view** — `TmfEntityClient` + impl + `entity()` on `TmfClient`,
   ITs, CHANGELOG `### Added`.
7. **Reactive entity view** — the same for `ReactiveTmfClient`.
8. **Docs** — README "Response headers" section, interface javadoc for D2 and D5.
9. **Release gate** — both allowed `versions-maven-plugin` goals with every
   profile activated and the mandatory ignore regex, full build on JDK 17, then
   cut 3.0.0.

### Why this order

- **Version first.** The CHANGELOG heading is keyed off the pom version. With
  the pom still at `2.1.1-SNAPSHOT`, any CHANGELOG line added mid-stream would
  sit under a `## [3.0.0]` heading that contradicts it. Bumping first lets every
  functional commit carry its own CHANGELOG lines as it lands, instead of one
  retrospective doc commit at the end that cannot be reviewed against the diffs.
  The cost — it forecloses shipping the no-auth fix as a `2.1.1` hotfix — is
  already an accepted decision (see *Bundled: no-auth support*).
- **Fix before refactor.** Step 2 rewrites `validateAuthorization` and
  `headersConsumer`; steps 3-4 rewrite the code that *calls* them. This way each
  commit's diff is about one thing.
- **Sync before reactive.** Sync has no null-body trap. Establishing the core
  shape there makes the reactive commit a mirror plus one known hazard, rather
  than two novel things at once.
- **Alignment before the entity views.** Step 5 introduces the
  `toEntity(arrayType)` path in reactive, which step 7's single-page `list`
  reuses. Building the entity view first means writing that path twice and then
  deduplicating it.
- **Refactors before new surface.** Steps 3-4 are behaviour-neutral and guarded
  by the untouched IT suites, so if something breaks after step 6 you know it is
  new surface, not the rewiring of every existing verb.

Steps 1-2 are shippable independently of everything after them; steps 3-4 are
behaviour-neutral and revertable; steps 5, 6 and 7 each land as one reviewable
commit.

---

## Resolved decisions

Both open questions are settled (2026-09-07):

1. **`entity()`** is the method name. It reads best at the call site
   (`client.entity().post(input)`) and matches Spring's own `toEntity` /
   `ResponseEntity` vocabulary. `withResponse()`, `asEntity()` and `response()`
   were considered and dropped.
2. **The no-auth fix ships in 3.0.0**, sequenced first — see *Bundled: no-auth
   support*.

No open questions remain. This plan is ready to implement.
