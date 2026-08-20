# Implementation plan: sub-resource paths for `opentmf-api-clients`

## Background

Every URI this library issues comes from exactly two shapes, both built in
`UriBuilderUtil` (`opentmf-api-clients-common/.../util/UriBuilderUtil.java`):

- **collection** — `baseUrl + contextPath + endpoint.path` (`buildUri`, `buildBaseUri`)
- **item** — the same, plus `/{id}` (`buildUriWithId`)

There is no third path segment anywhere. Neither `TmfRequestContext` (fields, filter, headers,
query params) nor `EndpointConfig` (a single `path`, scopes, fixed headers) has a slot for one.
So a nested endpoint such as:

```
GET /order/{orderId}/action/{action}/item/{itemId}
```

cannot be expressed. The predecessor library (`opentmf-clients-base`, retired) could not express it
either — `TmfClientCommonUtil.buildUri` / `buildUriWithId` there are the same two shapes, so this is
genuinely new surface, not a capability lost in the rewrite.

Two workarounds exist today and both are inadequate:

1. **Smuggle the sub-path through the id** — `get("123/then/foo")`. `UriComponentsBuilder.build(Object...)`
   strictly encodes template values, so this yields `/xyz/123%2Fthen%2Ffoo`. That encoding is
   deliberate and load-bearing (see the "do not simplify" comment block in `UriBuilderUtil`, which
   exists because composite keys like `PhysicalSimResourceSpecification:(version=1)` broke once
   already) — it must not be relaxed.
2. **Synthesize an `EndpointConfig` per parent id** via the
   `TmfClientFactory.create(ServerConfig, EndpointConfig, Class)` overload, with
   `path = "/order/" + orderId + "/action"`. This works for trusted ids but is unsafe as a library
   feature — see "The encoding trap" below.

## The encoding trap

The tempting implementation is to build the sub-path by string concatenation. Measured against the
project's own classpath, that is wrong in three distinct ways. `{var}`-expansion is correct in all
three:

| parent id | interpolated into the path string | passed as a `{var}` |
|---|---|---|
| `Spec:(version=1)` | `/xyz/Spec:(version=1)/then` | `/xyz/Spec%3A%28version%3D1%29/then` |
| `n{a}me` | **throws** `IllegalArgumentException: Not enough variable values available to expand` | `/xyz/n%7Ba%7Dme/then` |
| `a/b` | `/xyz/a/b/then` — silent path injection | `/xyz/a%2Fb/then` |

Note the first row: interpolation produces a **different URI than `get(id)` sends today** for the same
id. The second row is an outright crash (or, worse, an unintended expansion) on any id containing a
brace. The third is a path-injection hole.

Therefore: **every runtime value reaches the URI as a URI-template variable. Nothing is concatenated
into the path string.** The whole design follows from this.

Traversal containment falls out for free and needs no special handling:

```
orderId="a/b", action="../../admin"  ->  /ctx/order/a%2Fb/action/..%2F..%2Fadmin/item/i1
```

## Design

Adding sub-resource variants of every verb is a non-starter: `TmfClient` already has ~80 methods and
`ReactiveTmfClient` the same. Instead, note that all 18 URI call sites across both impls
(`TmfClientImpl.java:126-505`, `ReactiveTmfClientImpl.java:140-554`) build from exactly
`(serverConfig, endpointConfig)`. Give an impl a **scoped path** and every verb it already has starts
working against the sub-resource for free.

The API addition is therefore **one method per interface**:

```java
// org.opentmf.api.client.common.api.TmfClient
GenericTmfClient sub(String template, Object... vars);

// org.opentmf.api.client.reactive.api.ReactiveTmfClient
GenericReactiveTmfClient sub(String template, Object... vars);
```

Usage — the driving example, in one call:

```java
Item item = orderClient.sub("/{orderId}/action/{action}/item", orderId, action)
                       .get(itemId, Item.class);      // GET /order/o1/action/cancel/item/it7

List<Item> items = orderClient.sub("/{orderId}/action/{action}/item", orderId, action)
                              .list(Item.class);      // GET /order/o1/action/cancel/item

Item created = orderClient.sub("/{orderId}/action/{action}/item", orderId, action)
                          .post(body, Item.class);    // POST /order/o1/action/cancel/item
```

Why the template form rather than a `sub(id, subPath)` pair that chains:

- The URL shape reads as the API documents it, in one place, instead of forcing the reader to
  re-interleave `sub(orderId, "action").sub(action, "item")` mentally.
- It expresses shapes a pair model cannot: consecutive variables (`/order/{a}/{b}`), a literal before
  the first variable (`/order/summary/{id}`), a collection under a deep parent.
- Deep nesting stays one call rather than N.

Why the return type is the **generic** client rather than a typed one: a sub-resource is almost always
a different resource than its parent, so `C`/`U`/`R` would all have to change. The library already has
the idiom for this — `GenericTmfClient` plus the per-call `Class<T>` overloads that exist on every
verb. No typed variant is needed, and no second overload of `sub` (which would also create a varargs
overload-resolution hazard against `sub(String, String)`).

`sub()` on an already-derived client concatenates templates, so depth is unbounded.

## Arity validation is not optional

Deep templates mean more variables and more ways to miscount them. Both failure modes were measured:

- **Too few vars** → `IllegalArgumentException: Not enough variable values available to expand 'action'`.
  Right outcome, but the message names a placeholder in a form the caller never wrote.
- **Too many vars** → **silently ignored.** `sub("/{orderId}/action/{action}/item", o1, cancel, i1, EXTRA)`
  builds the URI and discards `EXTRA`. A caller who slid an argument out of position gets a
  wrong-but-plausible URL and no signal at all.

So `sub()` validates eagerly, at call time, and throws with the template plus both counts. This is the
difference between a usable feature and a debugging session.

## Implementation

### 1. New value type — `SubResourcePath`

`opentmf-api-clients-common/src/main/java/org/opentmf/api/client/common/model/SubResourcePath.java`

Immutable. Holds the accumulated template and its variable values; performs all validation.

```java
public final class SubResourcePath {

  private static final SubResourcePath NONE = new SubResourcePath("", List.of());
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^/{}]+)}");

  private final String template;      // e.g. "/{orderId}/action/{action}/item"
  private final List<Object> vars;    // e.g. [orderId, action]

  public static SubResourcePath none();

  /** Returns a new instance with {@code template} appended and {@code vars} bound to it. */
  public SubResourcePath append(String template, Object... vars);

  public boolean isEmpty();
  public String template();
  public Object[] vars();
  public Object[] varsWith(String id);   // vars + the terminal resource id
}
```

`append` validates, in order:

1. `template` non-null and non-blank.
2. Normalize: prepend `/` if absent, strip any trailing `/`.
3. Reject `{name:regex}` placeholders — a `:` inside braces (documented restriction, see below).
4. Reject unbalanced or nested braces: after removing every `PLACEHOLDER` match, no `{` or `}` may remain.
5. Placeholder count must equal `vars.length`; otherwise `IllegalArgumentException` naming the
   template, the expected count and the supplied count.
6. No element of `vars` may be null.

### 2. `UriBuilderUtil` — four new overloads

Each existing builder gains a `SubResourcePath` parameter. The four existing methods are **left
completely untouched** — bodies and signatures — which is what leaves the hub module untouched.

```java
public static URI buildBaseUri(ServerConfig s, EndpointConfig e, SubResourcePath sub);
public static URI buildUri(ServerConfig s, EndpointConfig e, TmfRequestContext ctx, SubResourcePath sub);
public static URI buildUriWithId(ServerConfig s, EndpointConfig e, String id, SubResourcePath sub);
public static URI buildUriWithId(ServerConfig s, EndpointConfig e, String id,
                                 TmfRequestContext ctx, SubResourcePath sub);
```

**Every new overload begins with `if (sub.isEmpty()) return <call the existing method>;`** — a plain
call to the untouched sibling, e.g. `return buildUriWithId(server, endpoint, id, ctx);`. The
delegation runs in this direction only (new → old, never old → new). This is deliberate and
non-negotiable: it guarantees that the zero-suffix path — i.e. 100% of existing behaviour — runs the
exact bytes it runs today and cannot regress the encoding rules the "do not simplify" comment
protects. The new code path is reachable only when a sub-resource is actually in play.

The non-empty path appends the template after `endpoint.getPath()` and switches the terminal call from
`build()` / `build(id)` to `build(vars)` / `build(varsWith(id))`:

```java
var builder = UriComponentsBuilder
    .fromUriString(server.getBaseUrl())
    .path(server.getContextPath())
    .path(endpoint.getPath())
    .path(sub.template())        // "/{orderId}/action/{action}/item"
    .path("/{id}");              // buildUriWithId only
// ...ctx query params / filter / fields exactly as today...
return builder.encode().build(sub.varsWith(id));
```

Variables expand **positionally, per occurrence**, so the template's own placeholder names cannot
collide with the appended `/{id}` — verified: a template that itself uses `{id}` still expands
correctly, consuming values in order.

Two details that must be pinned by tests:

- `buildUri` and `buildBaseUri` currently end in `.encode().build().toUri()` and `.build().toUri()`.
  Both **throw** the moment the path contains a `{p}` template
  (`IllegalStateException: Could not create URI object: Illegal character in path`). They must end in
  `build(vars)` on the non-empty path. This is exactly why the `isEmpty()` guard exists.
- `.encode()` followed by `.build(vars)` does **not** double-encode the expanded values — verified to
  produce output identical to `.build(vars)` alone. Pin this with a characterization test so nobody
  "simplifies" it later.

`withPagination` and `withContext` operate on an already-built `URI` and need **no change**.

### 3. Synchronous impl — `TmfClientImpl`

`opentmf-api-clients-rest/src/main/java/org/opentmf/api/client/rest/impl/TmfClientImpl.java`

Add a field, a constructor, and one method. Thread `subPath` through all 9 URI call sites.

```java
private final SubResourcePath subPath;

/** Existing 6-arg constructor — delegates, so no caller changes. */
public TmfClientImpl(EndpointConfig endpointConfig, ServerConfig serverConfig,
    RestClient restClient, SyncTokenService tokenService,
    ClientProperties clientProperties, Class<R> responseType) {
  this(endpointConfig, serverConfig, restClient, tokenService, clientProperties,
       responseType, SubResourcePath.none());
}

public TmfClientImpl(EndpointConfig endpointConfig, ServerConfig serverConfig,
    RestClient restClient, SyncTokenService tokenService,
    ClientProperties clientProperties, Class<R> responseType, SubResourcePath subPath) {
  // ...existing assignments...
  this.subPath = Objects.requireNonNull(subPath);
}

@Override
public GenericTmfClient sub(String template, Object... vars) {
  return new GenericTmfClientImpl(endpointConfig, serverConfig, restClient, tokenService,
      clientProperties, subPath.append(template, vars));
}
```

Call-site edit, applied at lines 126, 278, 318, 360, 405, 448, 479, 491, 504 (and their siblings):

```java
URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx);            // before
URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx, subPath);   // after
```

`GenericTmfClientImpl` gains a matching 6-arg constructor taking `SubResourcePath` and passing
`Object.class` as the response type; its existing 5-arg constructor delegates with
`SubResourcePath.none()`.

### 4. Reactive impl — `ReactiveTmfClientImpl`

`opentmf-api-clients-reactive/src/main/java/org/opentmf/api/client/reactive/impl/ReactiveTmfClientImpl.java`

Mirror image of the above: same field, same constructor pair, same 9 call-site edits (lines 140, 298,
343, 392, 441, 487, 522, 538, 553 and siblings), and

```java
@Override
public GenericReactiveTmfClient sub(String template, Object... vars) {
  return new GenericReactiveTmfClientImpl(endpointConfig, serverConfig, webClient, tokenService,
      clientProperties, subPath.append(template, vars));
}
```

`GenericReactiveTmfClientImpl` gains the matching constructor.

`sub()` itself is synchronous on both sides — it only assembles a client, it issues no request — so the
reactive signature returns `GenericReactiveTmfClient` directly rather than `Mono<...>`.

### 5. Untouched

- **Hub module** (`TmfHubClientImpl`, `ReactiveTmfHubClientImpl`) — calls the preserved 2- and 3-arg
  `UriBuilderUtil` overloads. Hub endpoints (`/hub`, `/hub/{id}`) have no sub-resources.
- **`TmfClientFactory` / `ReactiveTmfClientFactory`** — no signature change. Clients built by the
  factory carry `SubResourcePath.none()` and `sub()` derives from there.
- **`TmfApiClientsConfig`** — no new YAML surface (see Restrictions).
- **`Scope`** — sub-resource operations reuse the parent endpoint's scope map.
- **Architecture tests** — the existing ArchUnit rules assert module dependency direction
  (no WebFlux/Reactor in the REST module and vice versa), not verb enumeration. `SubResourcePath` lives
  in the common module and pulls in nothing reactive, so no rule changes.

## Tests

### `UriBuilderUtilTest` (common module)

Characterization tests, which are the real deliverable of this change:

- Deep nesting: `/order/{orderId}/action/{action}/item` + terminal id →
  `/ctx/order/o%201/action/cancel/item/it%3A7`.
- Encoding parity: a parent id passed via `sub()` encodes **identically** to the same value passed to
  `get(id)` today (`Spec:(version=1)` → `Spec%3A%28version%3D1%29`).
- Brace-bearing value: `n{a}me` → `n%7Ba%7Dme`, no exception, no template injection.
- Traversal containment: `../../admin` → `..%2F..%2Fadmin`.
- `.encode()` + `build(vars)` does not double-encode (guards the "do not simplify" invariant).
- Empty-suffix equivalence: for every one of the four builders, `sub == none()` produces a URI byte-identical
  to the pre-change method.
- `withPagination` / `withContext` layered on a sub-resource base URI keep query params encoded once.

### `SubResourcePathTest` (common module, new)

- Too few vars → `IllegalArgumentException`, message contains the template and both counts.
- Too many vars → `IllegalArgumentException` (**not** silent truncation).
- Null/blank template → `IllegalArgumentException`.
- Null element in `vars` → `IllegalArgumentException`.
- `{name:regex}` placeholder → `IllegalArgumentException`.
- Unbalanced brace (`"/{orderId"`) → `IllegalArgumentException`.
- Normalization: `"orderId/action"`, `"/orderId/action"`, `"/orderId/action/"` all normalize to the same
  template.
- `append` is immutable — the receiver is unchanged; chaining twice concatenates in order.

### `TmfClientIT` / `ReactiveTmfClientIT`

Add a `// --- SUB-RESOURCE ---` section to each, using `MockServerUtils.setUpAllDynamicCallbacks(path)`
against a nested path. Mirror the same cases on both sides:

- `sub_get_hitsNestedPath` — the driving example end to end; assert MockServer received the fully
  expanded path.
- `sub_list_hitsNestedCollection` — `list()` on a derived client.
- `sub_post_createsUnderNestedCollection`.
- `sub_chained_producesDeepPath` — `sub(...).sub(...)`.
- `sub_inheritsFixedHeadersAndScopes` — assert the derived client sends the parent endpoint's fixed
  headers and requests the parent's configured scope token.
- `sub_withPageable_appendsPaginationAfterNestedPath`.
- `sub_arityMismatch_throwsBeforeAnyRequest` — assert MockServer saw **zero** requests.

`MockServerUtils` needs no new callback helpers — `setUpAllDynamicCallbacks(path)` already accepts an
arbitrary path string, and nested paths are just longer strings.

## File checklist

**Common module** — `opentmf-api-clients-common/src/main/java/org/opentmf/api/client/common/`:
- `model/SubResourcePath.java` — **new**.
- `util/UriBuilderUtil.java` — 4 new overloads + `isEmpty()`-guarded delegation.
- `api/TmfClient.java` — 1 new method declaration in a new `// SUB-RESOURCE` block.

**Common tests**:
- `util/UriBuilderUtilTest.java` — extend.
- `model/SubResourcePathTest.java` — **new**.

**REST module**:
- `impl/TmfClientImpl.java` — field, constructor pair, `sub()`, 9 call-site edits.
- `impl/GenericTmfClientImpl.java` — constructor pair.

**REST tests**:
- `impl/TmfClientIT.java` — 7 new cases.

**Reactive module**:
- `api/ReactiveTmfClient.java` — 1 new method declaration.
- `impl/ReactiveTmfClientImpl.java` — field, constructor pair, `sub()`, 9 call-site edits.
- `impl/GenericReactiveTmfClientImpl.java` — constructor pair.

**Reactive tests**:
- `impl/ReactiveTmfClientIT.java` — 7 new cases.

**Project root**:
- `CHANGELOG.md` — new `## [2.1.0]` section (see below).
- `README.md` — new `### Sub-resource paths` subsection under `## API Reference`, plus a Table of
  Contents entry.
- `MIGRATION.md` — one line noting this is new surface with no v1 equivalent.
- `pom.xml` — version bump, pending the decision below.

**`opentmf-versions` BOM** (separate project) — no change; rides the existing release flow.

## Restrictions

These are deliberate limits of the design, not defects. They belong in the `sub()` javadoc.

1. **The template must be a compile-time constant.** Never build it by concatenating user data —
   `sub("/" + orderId + "/action")` reintroduces exactly the injection and crash modes the design
   exists to prevent. Everything variable goes in `vars`. This is a security boundary and should be
   stated in bold in the javadoc.
2. **Only simple `{name}` placeholders are supported.** `{name:regex}` is rejected at `sub()` time
   rather than silently mishandled.
3. **Variables are strictly encoded and cannot span segments.** A `/` in a variable becomes `%2F`.
   You cannot add path segments through a variable — by design. Extra segments belong in the template.
4. **Derived clients inherit the parent endpoint's OAuth scopes and fixed headers.** There is no
   per-sub-resource scope or header configuration. If a sub-resource needs a different scope, build a
   separate endpoint entry in YAML and use the factory.
5. **The derived client is always the generic (`Object`-typed) client.** Use the existing `Class<T>`
   overloads for typing. There is no typed `sub`.
6. **`sub()` does not preserve a user's subclass.** If you subclass `TmfClientImpl`, `sub()` returns
   the library's `GenericTmfClientImpl`, not your subclass. Override `sub()` if you need otherwise.
7. **No YAML/config surface.** Sub-resource paths are runtime-only, because the parent id is runtime
   data. Config alone could never express this case.
8. **No caching of derived clients.** Each `sub()` allocates a new impl. It is a thin wrapper over the
   shared `RestClient`/`WebClient`, so this is cheap — but callers in hot loops should hoist the derived
   client out of the loop.
9. **The hub module gains nothing.** `TmfHubClient` / `ReactiveTmfHubClient` are unchanged.
10. **Adding an abstract method to `TmfClient` / `ReactiveTmfClient` is source- and binary-breaking for
    any external implementor** of those interfaces. See Assumptions and Open questions.
11. **Sub-resources share the parent's transport.** Same `RestClient`/`WebClient` instance, same retry
    count and backoff from `ClientProperties`, same `TokenService`. They cannot be tuned independently.

## Assumptions

1. **`TmfClient` and `ReactiveTmfClient` have no implementors outside this repository.** Only
   `TmfClientImpl` / `ReactiveTmfClientImpl` (and their generic subclasses) implement them. This is what
   makes plain abstract methods acceptable instead of `default` methods that throw. **Verify against the
   known downstreams before merging** — `dsync-engine-client-provider` in particular.
2. **Sub-resource endpoints share their parent's auth model** — same token, same scope map. Restriction 4
   is only reasonable if this holds; no counterexample is known today.
3. **Sub-resource list responses carry the same pagination envelope** (`X-Total-Count` /
   `Content-Range`) as top-level collections, so `listPaged` / `listAll` work unmodified against them.
4. **Servers treat percent-encoded segment values as equivalent to their raw form** — i.e. sending
   `Spec%3A%28version%3D1%29` is safe. This is not a new assumption: `get(id)` has encoded ids this way
   since the double-encoding fix, and it is in production.
5. **MockServer can host arbitrary nested paths**, so ITs need no new infrastructure beyond longer path
   strings.
6. **`endpoint.path` never itself contains `{`/`}`.** No current configuration does. If one ever did, it
   would already be broken today at `buildUriWithId`; the `isEmpty()` guard means this change does not
   make it worse.
7. **The `.encode()` + `build(vars)` combination is stable across Spring versions.** It is verified
   against the version the BOM currently pins; the characterization test is what protects it across
   upgrades.

## CHANGELOG entry

`pom.xml` is currently at `2.0.10-SNAPSHOT` and the latest released heading is `## [2.0.9]`. This adds
public API, so the recommendation is to bump to `2.1.0-SNAPSHOT` and open a `2.1.0` section (per the
SNAPSHOT-heading rule, the heading carries the bare numeric version):

```markdown
## [2.1.0] - YYYY-MM-DD

### Added
- Sub-resource path support on both the synchronous (`TmfClient`) and reactive
  (`ReactiveTmfClient`) surfaces via a new `sub(String template, Object... vars)`
  method. Returns a derived generic client scoped to a nested path, so every
  existing verb — get, list, listAll, listPaged, post, put, patch, delete — works
  against it unchanged. Supports arbitrary depth, e.g.
  `orderClient.sub("/{orderId}/action/{action}/item", orderId, action).get(itemId, Item.class)`
  issues `GET /order/{orderId}/action/{action}/item/{itemId}`. Path variables are
  bound as URI template variables and strictly encoded, so a value containing `/`,
  `:` or `{` cannot inject or break the path. Template/argument arity is validated
  eagerly, before any request is issued.
```

No `### Fixed` entry: the double-encoding behaviour this builds on shipped in an earlier release and
is unchanged here.

## Verification checklist

- `mvn -B clean verify` passes from the project root.
- Per-module JaCoCo `check` still passes — `SubResourcePath` is new code in the common module and needs
  its own coverage there, not coverage borrowed from the REST/reactive ITs.
- IT count rises by 14 (7 sync + 7 reactive); `SubResourcePathTest` and the extended
  `UriBuilderUtilTest` show in the surefire report.
- `mvn -B javadoc:javadoc` is clean — `sub()` and `SubResourcePath` are the newly public surface.
- The empty-suffix equivalence tests confirm no existing URI changed by a single byte.
- A consumer can compile and run
  `client.sub("/{orderId}/action/{action}/item", orderId, action).get(itemId, Item.class)`
  end to end against MockServer.
- Before merge: confirm Assumption 1 by grepping known downstreams for implementations of `TmfClient` /
  `ReactiveTmfClient`.
- Per the release rule, release readiness is a separate gate — both `versions-maven-plugin` goals with
  every profile active, plus the image scan per flavour.

## Open questions

- **Version: `2.0.10` or `2.1.0`?** Recommendation: `2.1.0`. New public interface methods in a patch
  release is the kind of thing that surprises consumers, and the pom is still on an unreleased
  `2.0.10-SNAPSHOT`, so the bump is free. Needs your call before the CHANGELOG section is written.
- **Abstract methods or `default` methods that throw `UnsupportedOperationException`?**
  Recommendation: abstract, contingent on Assumption 1 holding. `default`-that-throws buys binary
  compatibility for external implementors at the cost of an interface that lies about what it supports.
- **Should a typed `subTyped(String template, Class<SR> type, Object... vars)` ship in the same
  release?** Recommendation: defer. The generic client plus `Class<T>` overloads covers the use case, and
  the awkward parameter ordering forced by varargs is a poor trade until someone actually asks.
- **Should `sub()` reject a template with zero placeholders?** A constant nested path
  (`sub("/summary")`) is legitimate and the arity check passes trivially, so: allow it. Flagged only so a
  future contributor does not "tighten" the validation and break that case.
