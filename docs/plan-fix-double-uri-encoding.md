# Plan: fix double percent-encoding in UriBuilderUtil

## Background

A defect was reported against the retired predecessor
`org.opentmf.client:opentmf-clients-base:1.1.5` and confirmed at HEAD of that
library (`1.1.10-SNAPSHOT`). Symptom: calling
`get(id, TmfRequestContext)` with a composite key id like
`PhysicalSimResourceSpecification:(version=1)` produced

```
.../PhysicalSimResourceSpecification%253A%2528version%253D1%2529
```

instead of the correct single-encoded

```
.../PhysicalSimResourceSpecification%3A%28version%3D1%29
```

The same `UriComponentsBuilder` pattern was carried over into this project
(`opentmf-api-clients`). This plan documents what we found, where the bug
lives here, and how to fix it before someone trips on it in production.

## Empirical verification (already done in this repo)

Probe tests were added temporarily to
`opentmf-api-clients-common/src/test/java/org/opentmf/api/client/common/util/UriBuilderUtilTest.java`
and run via `mvn -Dtest='UriBuilderUtilTest#probe_*' test`. Results:

| Scenario | Method | Result |
| --- | --- | --- |
| Build id-only URI with composite key | `buildUriWithId(server, endpoint, id)` | ✅ single encoding (`%3A%28version%3D1%29`) |
| Build id+ctx URI with composite key | `buildUriWithId(server, endpoint, id, ctx)` | ✅ single encoding |
| Re-apply ctx onto already-encoded URI | `withContext(buildUriWithId(...), ctx)` | ❌ double encoding (`%253A%2528...`) |
| Re-apply pagination onto already-encoded URI | `withPagination(buildUriWithId(...), pageable)` | ❌ double encoding |

So `buildUriWithId(...)` is fine. The bug surfaces only when a fully-built
URI is fed back through `fromUri(...).encode().build().toUri()`, which is
exactly what `withContext` and `withPagination` do.

The transient probes have been removed; they will be re-added as permanent
regression tests as part of the fix.

## Root cause

In `opentmf-api-clients-common/.../UriBuilderUtil.java`:

- `withContext` (lines 110-116) — `UriComponentsBuilder.fromUri(base).encode().build().toUri()`
- `withPagination` (lines 88-105) — same `.encode().build().toUri()` shape at line 104

`fromUri(URI)` reads the URI's raw (already-encoded) path. The subsequent
`.encode().build().toUri()` re-runs encoding over those segments, so `%`
becomes `%25` and every previously-encoded reserved char gets a second pass.

`encode()` was presumably added to encode the *added* query values (filter,
fields, sort) correctly, but it also re-encodes the existing path.

## Current call-site risk

| Caller | Trigger today? | Trigger if id has reserved chars? |
| --- | --- | --- |
| `ReactiveTmfClientImpl:509` — `withPagination(base, pageable)` where `base = buildBaseUri(...)` | static path only, no risk | n/a, no id segment |
| `TmfClientImpl:464` — same as above | static path only, no risk | n/a, no id segment |
| `withContext` | not called from any client impl today | latent — any future caller hits the bug |

So the **production** blast radius today is zero — but the API is
incorrect and silently waiting to break the moment someone composes a
`buildUriWithId(...)` with a `withContext(...)` or `withPagination(...)`,
which is exactly the pattern the predecessor `get(id, ctx)` uses.

## Fix

In both methods, switch the terminal call to `build(true).toUri()`:

```java
// withContext
return builder.build(true).toUri();   // was: .encode().build().toUri();
```

```java
// withPagination
return builder.build(true).toUri();   // was: .encode().build().toUri();
```

`build(true)` tells Spring "the URI components are already in their encoded
form — do not encode them again." Any query parameters added via
`queryParam(...)` between `fromUri(...)` and `build(true)` carry encoded
values because they were appended as already-encoded literals (TMF filter
strings, comma-joined fields, integer offset/limit). Where the value is a
free-form user string with reserved chars (e.g. a server-side `filter=`
RSQL with `=` and `,`), this is already the responsibility of the value
source — same as the rest of the codebase. The fix does not regress that.

The `encode()` call should also be removed from these two methods. It is
unnecessary once we move to `build(true)`, and leaving it in is misleading.

Methods to leave alone:

- `buildBaseUri` — pure static path, `.build().toUri()` is fine.
- `buildUriWithId(server, endpoint, id)` — uses `.build(id)`, single encoding.
- `buildUriWithId(server, endpoint, id, ctx)` — uses `.encode().build(id)`,
  which correctly encodes the id once via template-variable expansion. **Do
  not change this method**; it is the example of how `encode()` is meant to
  work (with template variables, not with `fromUri()`).
- `buildUri(server, endpoint, ctx)` — `fromUriString(literalBaseUrl).encode().build().toUri()`,
  no `fromUri(URI)` involvement and no `{}` template variable, so the
  re-encoding pass does no harm. Could be simplified to plain `.build().toUri()`
  for symmetry but it is not necessary for the fix.

## Regression tests

Add to `UriBuilderUtilTest.java`:

```java
private static final String COMPOSITE_ID = "PhysicalSimResourceSpecification:(version=1)";
private static final String EXPECTED_ENCODED_ID =
    "PhysicalSimResourceSpecification%3A%28version%3D1%29";

@Test
void buildUriWithId_compositeKey_encodesOnce() {
  URI uri = UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID);
  assertThat(uri.toString()).endsWith("/" + EXPECTED_ENCODED_ID);
}

@Test
void buildUriWithId_compositeKey_withCtx_encodesOnce() {
  TmfRequestContext ctx = TmfRequestContext.builder().build();
  URI uri = UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID, ctx);
  assertThat(uri.toString()).endsWith("/" + EXPECTED_ENCODED_ID);
}

@Test
void withContext_preservesPathEncoding() {
  URI base = UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID);
  TmfRequestContext ctx = TmfRequestContext.builder()
      .withServerJsonFilter("state=='active'").build();
  URI result = UriBuilderUtil.withContext(base, ctx);
  assertThat(result.toString()).contains("/" + EXPECTED_ENCODED_ID);
  assertThat(result.toString()).contains("filter=");
  assertThat(result.toString()).doesNotContain("%25");
}

@Test
void withContext_emptyCtx_preservesPathEncoding() {
  URI base = UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID);
  URI result = UriBuilderUtil.withContext(base, TmfRequestContext.builder().build());
  assertThat(result.toString()).contains("/" + EXPECTED_ENCODED_ID);
  assertThat(result.toString()).doesNotContain("%25");
}

@Test
void withPagination_preservesPathEncoding() {
  URI base = UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID);
  URI result = UriBuilderUtil.withPagination(base, TmfOffsetRequest.of(0, 10));
  assertThat(result.toString()).contains("/" + EXPECTED_ENCODED_ID);
  assertThat(result.toString()).contains("offset=0").contains("limit=10");
  assertThat(result.toString()).doesNotContain("%25");
}
```

The `doesNotContain("%25")` assertion is the precise regression guard: a
percent-of-percent only appears when an already-encoded `%3A`-style sequence
gets a second encoding pass.

## CHANGELOG entry

Current `pom.xml` is `2.0.8-SNAPSHOT`. Per the SNAPSHOT-CHANGELOG rule
(strip `-SNAPSHOT` from the heading, new section if the version is higher
than the latest released heading), the entry belongs under

```markdown
## [2.0.8] - YYYY-MM-DD

### Fixed
- Fix double percent-encoding of resource path segments when
  `UriBuilderUtil.withContext(...)` or `UriBuilderUtil.withPagination(...)`
  is applied to a URI that already contains an encoded `{id}` (typically
  TMF composite keys like `Spec:(version=1)`). The terminal
  `.encode().build().toUri()` was re-encoding the path; switched to
  `.build(true).toUri()` which respects the already-encoded components.
```

Check the actual top heading at edit time; if `## [2.0.8]` already exists
(unreleased), append to its `### Fixed`. If it doesn't, insert a new
section above the latest released one.

## Verification checklist

- [ ] Edit `UriBuilderUtil.withContext` — remove `.encode()`, change to `.build(true).toUri()`.
- [ ] Edit `UriBuilderUtil.withPagination` — remove `.encode()`, change to `.build(true).toUri()`.
- [ ] Add 5 regression tests to `UriBuilderUtilTest`.
- [ ] Run `mvn -pl opentmf-api-clients-common test` — all green.
- [ ] Run full `mvn verify` from the aggregator — all integration tests still pass.
- [ ] Update CHANGELOG per rule above.

## Out of scope

- No change to public API surface.
- No change to `RestTemplate` (none exists; see `MEMORY.md` /
  `no_resttemplate_impl.md`).
- No change to `buildBaseUri`, `buildUriWithId(...)`, `buildUri(...,ctx)` —
  these encode correctly.
