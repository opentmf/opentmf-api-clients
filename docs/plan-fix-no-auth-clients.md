# Plan: support no-auth (`AuthType.NONE`) clients

> **Status:** SCHEDULED for **3.0.0**, not yet implemented. Re-confirmed still
> present at `2.1.1-SNAPSHOT` (develop) on 2026-09-07 — see *Empirical
> verification*. Sequenced as step 0 of
> [`plan-response-headers.md`](plan-response-headers.md), because both changes
> edit `HeaderUtil` and the two transports' `headers(...)` call sites; this fix
> lands first so each commit's diff is about one thing.
>
> This is a non-breaking fix and does not itself require a major. Consumers can
> still work around it by giving every client a `bearer-auth` or `basic-auth`
> block.

## Background

`opentmf-http-clients` treats "this endpoint needs no authorization" as a
first-class, deliberately supported configuration. `ClientProperties`
(`opentmf-http-clients-common/.../model/ClientProperties.java:173-177`):

```java
public AuthType getAuthType() {
  if (basicAuth != null) return AuthType.BASIC;
  if (bearerAuth != null) return AuthType.BEARER;
  return AuthType.NONE;
}
```

A client definition carrying neither block resolves to `AuthType.NONE`, and the
registrars wire a purpose-built no-op token service for it —
`RestClientRegistrar.java:96` and `ReactiveClientRegistrar.java:112`:

```java
case NONE -> new NoOpSyncTokenService();   // reactive: new NoOpTokenService()
```

`NoOpSyncTokenService` returns the empty string from both `getTokenType()` and
`getToken()`. Used directly, an http-client so configured sends no
`Authorization` header and works out of the box.

**`opentmf-api-clients` does not honour that contract.** An
`opentmf.api-clients` server whose `client-ref` points at a NONE-auth
http-client throws on EVERY call, before any request reaches the network. There
is currently no way to call an unauthenticated TMF endpoint through
`GenericTmfClient` / `TmfClient`.

## Empirical verification (re-run 2026-09-07, still failing)

A probe test was added temporarily at
`opentmf-api-clients-common/src/test/java/org/opentmf/api/client/common/util/NoAuthProbeTest.java`
and run with `JAVA_HOME=<jdk17> mvn -pl opentmf-api-clients-common -Dtest=NoAuthProbeTest test`.
It feeds `HeaderUtil` exactly what `NoOpSyncTokenService` returns (`""`, `""`):

| Observation | Result |
| --- | --- |
| Authorization header value produced | `" "` — a single space |
| Header actually present on the request | `true` |
| `prepareGetDelete(...)` (GET, DELETE) | ❌ throws `IllegalArgumentException: Authorization token must not be empty.` |
| `prepareAndValidate(...)` (POST, PUT) | ❌ throws, same message |
| `prepareAndValidateJsonPatch(...)` (PATCH) | ❌ throws, same message |

The probe was re-run against `2.1.1-SNAPSHOT` on 2026-09-07 with identical
results — `headersConsumer("", "", null, null)` still produces
`Authorization: " "` and all four prepare methods still throw. Neither
`headersConsumer` nor `validateAuthorization` has changed, and no
`isAuthenticationRequired` (or equivalent) exists in either repository, so the
fix below is entirely unimplemented.

The probe has been removed again; it returns as a permanent regression test with
the fix (see **Tests** below).

## Root cause

Two independent defects in
`opentmf-api-clients-common/.../util/HeaderUtil.java`, which is shared by
**four** header factories across three modules — all of them call
`headersConsumer` identically:

| module | call site |
| --- | --- |
| `-rest` | `TmfClientImpl.java:113` |
| `-reactive` | `ReactiveTmfClientImpl.java:123` |
| `-hub` (sync) | `TmfHubClientImpl.java:126` |
| `-hub` (reactive) | `ReactiveTmfHubClientImpl.java:135` |

The hub clients are affected exactly as the generic ones are: a NONE-auth
`registerListener` / `unregisterListener` throws before reaching the network.

**1. The Authorization header is set unconditionally.** In `headersConsumer`:

```java
httpHeaders.set(HttpHeaders.AUTHORIZATION, tokenType + " " + token);
```

With the no-op service that is `"" + " " + ""` — a lone space. Note this is
wrong on its own terms, independently of the exception below: were validation
absent, the client would put a malformed `Authorization: ` header on the wire
for every no-auth request.

**2. An absent token is treated as an error in all cases.** Every request path
(`prepareAndValidate`, `prepareGetDelete`, and the three patch variants) calls:

```java
private static void validateAuthorization(HttpHeaders headers) {
  if (!StringUtils.hasText(headers.getFirst(HttpHeaders.AUTHORIZATION))) {
    throw new IllegalArgumentException(ERR_EMPTY_AUTH_TOKEN);
  }
}
```

`StringUtils.hasText(" ")` is `false` — it ignores whitespace — so the lone
space produced above fails validation and the call dies at
`TmfApiClientConstants.ERR_EMPTY_AUTH_TOKEN`.

Incidentally, the api-clients layer is not bearer-specific at any point: it
emits `tokenType + " " + token`, so a BASIC client legitimately sends
`Basic …`. Only the NONE case is broken.

## Why the obvious fix is the wrong one

`validateAuthorization` exists for a good reason: it catches a misconfigured
BEARER client whose token service silently produced nothing, which would
otherwise surface as an opaque 401 from the far end. Deleting the check — or
loosening it to "allow blank" — would trade a clear local failure for a remote
one, on the path where it currently earns its keep.

The real problem is that `NoOpSyncTokenService`'s `""` sentinel is
**indistinguishable** from "auth applies and I got nothing". The fix must
separate those two states rather than conflate them further.

## Proposed fix

1. **Read the no-auth signal from the configuration, not the token service.**
   All four header factories above already hold a `ClientProperties`
   (`TmfClientImpl.java:70`, `ReactiveTmfClientImpl.java:67`,
   `TmfHubClientImpl.java:42`, `ReactiveTmfHubClientImpl.java:46`), and
   `ClientProperties.getAuthType()` returns `AuthType.NONE` exactly when neither
   `basic-auth` nor `bearer-auth` is configured. So each factory passes an
   explicit `authRequired` flag:

   ```java
   protected Consumer<HttpHeaders> headers(String token, TmfRequestContext ctx) {
     return headersConsumer(tokenService.getTokenType(), token,
         mergeFixedHeaders(...), ctx,
         clientProperties.getAuthType() != AuthType.NONE);
   }
   ```

2. **`headersConsumer`: skip the header when no auth applies** — do not set
   `Authorization` at all, rather than setting it blank.
3. **`validateAuthorization`: validate only when auth applies.** Keep today's
   throw for the BEARER/BASIC paths untouched — that behaviour is correct and
   deliberately load-bearing.

Scope is contained to **this repository**: `HeaderUtil` plus the four call
sites. No public API is broken.

### Why not `isAuthenticationRequired()` on the token service

An earlier draft of this plan proposed a default method
`boolean isAuthenticationRequired()` on `SyncTokenService` / `TokenService`,
overridden to `false` in `NoOpSyncTokenService` and `NoOpTokenService`. It is a
clean shape, but those four types all live in **`opentmf-http-clients`**. Taking
that route means: change the upstream repo → cut an upstream release → bump the
dependency here → only then fix. That puts a whole release cycle of another
project on the critical path of this one, for a signal we can already read
locally.

What the `ClientProperties` route gives up: a custom `TokenService`
implementation cannot declare *itself* no-auth. That is the correct trade —
whether an endpoint requires authorization is a property of the client's
configuration, not of the token service instance, and no such implementation
exists. If one ever does, the upstream default method can be added later as a
refinement without changing `HeaderUtil`'s signature again.

## Tests

- Reinstate the probe above as a permanent test: a NONE-auth consumer must
  produce headers with **no** `Authorization` key at all, and must not throw, on
  each of GET/DELETE, POST/PUT and the three patch content types.
- Keep an explicit negative test that a BEARER-shaped client with an empty token
  still throws `ERR_EMPTY_AUTH_TOKEN` — the regression this fix must not cause.
- Cover all **four** call sites — `-rest`, `-reactive` and both hub clients.
  `HeaderUtil` is shared, but the four factories are not, and the hub module has
  its own module-level coverage gate.

## Impact on consumers

Any project pointing an `opentmf.api-clients` server at an unauthenticated TMF
endpoint. Present workaround: configure a `bearer-auth` or `basic-auth` block on
the referenced http-client even where the target requires none.

Known affected: `external-task-generator-suite` generates opentmf client
scaffolding from an OAS, and currently forces bearer-auth scaffolding for every
TMF-matched client because of this defect — a `platform-internal: false` client
cannot be emitted honestly until this is fixed.
