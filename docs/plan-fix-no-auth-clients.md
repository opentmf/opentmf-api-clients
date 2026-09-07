# Plan: support no-auth (`AuthType.NONE`) clients

> **Status:** IMPLEMENTED and merged into `develop` in PR #2 (2026-09-08),
> shipping in **3.0.0**. The defect described below was last confirmed present
> at `2.1.1-SNAPSHOT` (develop) on 2026-09-07 — see *Empirical verification*;
> everything stated in the present tense about the broken behaviour describes
> 2.1.0 and earlier. Sequenced as work-order step 2 of
> [`plan-response-headers.md`](plan-response-headers.md), because both changes
> edit `HeaderUtil` and the four `headers(...)` call sites; this fix lands first
> so each commit's diff is about one thing.
>
> Plan reviewed against the code on 2026-09-07; three corrections were folded in
> (see *Review corrections* at the end). The fix does not itself require a
> major, but it does change `HeaderUtil`'s public signatures and tightens
> validation for authenticated clients, so 3.0.0 is the right vehicle. Consumers
> can still work around the defect today by giving every client a `bearer-auth`
> or `basic-auth` block.

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

## What the existing guard actually catches

`validateAuthorization` was written to catch a misconfigured BEARER client whose
token service silently produced nothing, so that the failure is a clear local
exception instead of an opaque 401 from the far end. **It does not do that
today.** With `tokenType = "Bearer"` and `token = ""` the assembled header is
`"Bearer "`, and `StringUtils.hasText("Bearer ")` is `true` (verified with
jshell against the spring-core on the classpath: `hasText("Bearer ") = true`,
`hasText(" ") = false`). The guard only fires when the token type *and* the
token are both blank — which is exactly, and only, the NONE case it is supposed
to allow.

So the guard is not load-bearing for authenticated clients; it is a misfire that
happens to block no-auth clients. Two consequences for the fix:

- Loosening the header check to "allow blank" would simply delete the only case
  it fires on. Not wrong, but it leaves the BEARER empty-token hole open.
- The check has to look at the **token**, not at the assembled header, and it
  has to know whether authorization applies. Neither of those is visible from
  `validateAuthorization(HttpHeaders)`, which only sees the finished headers.
  Threading a flag through the five `prepare*` methods just to reach it would
  widen every call site in four classes for no gain.

The real problem is that `NoOpSyncTokenService`'s `""` sentinel is
**indistinguishable** from "auth applies and I got nothing". The fix must
separate those two states, and do the check where both the token and the
no-auth signal are in hand: inside `headersConsumer`.

## Proposed fix

1. **Read the no-auth signal from the configuration, not the token service.**
   All four header factories above already hold a `ClientProperties`
   (`TmfClientImpl.java:70`, `ReactiveTmfClientImpl.java:67`,
   `TmfHubClientImpl.java:42`, `ReactiveTmfHubClientImpl.java:46`), and
   `ClientProperties.getAuthType()` returns `AuthType.NONE` exactly when neither
   `basic-auth` nor `bearer-auth` is configured. So each factory passes the
   **`AuthType` itself** — not a bare boolean, whose meaning is invisible at the
   call site (`…, ctx, true)`). `opentmf-api-clients-common` already depends on
   `opentmf-http-clients-common` (where `AuthType` lives), so this costs no new
   dependency, and a future auth type extends the enum instead of changing
   `HeaderUtil`'s signature again:

   ```java
   protected Consumer<HttpHeaders> headers(String token, TmfRequestContext ctx) {
     return headersConsumer(tokenService.getTokenType(), token,
         mergeFixedHeaders(...), ctx,
         clientProperties.getAuthType());
   }
   ```

2. **`headersConsumer` owns the whole decision.** The consumer body becomes:

   ```java
   return httpHeaders -> {
     if (authType != AuthType.NONE && !StringUtils.hasText(token)) {
       throw new IllegalArgumentException(ERR_EMPTY_AUTH_TOKEN);
     }
     if (StringUtils.hasText(token)) {
       httpHeaders.set(HttpHeaders.AUTHORIZATION,
           StringUtils.hasText(tokenType) ? tokenType + " " + token : token);
     }
     // fixed headers, request-context headers, Accept default — unchanged
   };
   ```

   The resulting matrix, which is the contract the tests pin down:

   | client auth | token | result |
   | --- | --- | --- |
   | BEARER / BASIC | non-blank | `Authorization: <type> <token>` — unchanged |
   | BEARER / BASIC | blank | throws `ERR_EMPTY_AUTH_TOKEN` (**new** — today this sends `Bearer ` and gets a remote 401) |
   | NONE | blank (the `NoOp*TokenService` case) | no `Authorization` header at all — the fix |
   | NONE | non-blank (caller used a `…WithToken` overload) | `Authorization: <token>` verbatim — see below |

   The throw stays inside the consumer rather than at construction time so that
   it fires at the same moment it does today: inside `prepare*`, before any
   request is built, and exactly once per request.

3. **`validateAuthorization` is deleted** from all five `prepare*` methods.
   After step 2 it is redundant for every consumer this library builds, and it
   is wrong for NONE clients, whose headers legitimately carry no
   `Authorization`. The only thing it could still catch is a *custom*
   `Consumer<HttpHeaders>` (a Level-3 subclass overriding `headers(...)`) that
   forgets the header — and today it does not even catch that reliably (a custom
   consumer setting `"Bearer "` passes). A subclass that overrides header
   construction owns header construction. `ERR_EMPTY_AUTH_TOKEN` and the
   `ERR_NULL_HEADERS_CONSUMER` null check are kept; only the header inspection
   goes.

### Explicit tokens on a NONE client

The `…WithToken(token, …)` overloads exist so a caller can hand over a token the
library did not obtain itself (a propagated inbound token, a one-off service
credential). On a NONE client the caller's token must still reach the wire —
silently discarding a value the caller passed explicitly is the worst of the
options, and today's behaviour (`Authorization:  <token>` with a leading space)
is malformed. Since a NONE client has no token type of its own, the caller's
string is sent **verbatim** as the whole credential: pass `"Bearer eyJ…"` if a
scheme is needed. This is documented on the `…WithToken` javadoc of both client
interfaces.

### Scope

Contained to **this repository**: `HeaderUtil`, its unit tests, the four
`headers(...)` factories, and the IT fixtures (see *Tests*). `HeaderUtil` is a
public class, so its surface does change — `headersConsumer` gains a fifth
parameter (`AuthType`), and the `prepare*` methods stop rejecting header sets
without `Authorization`. No delegating four-argument overload is kept: a hidden
`authType = BEARER` default is precisely the kind of thing that would
resurrect this defect in a forgotten call site. Both changes go into the 3.0.0
CHANGELOG under `### Changed`, alongside the `### Fixed` entry for NONE clients.

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

**`HeaderUtilTest` (common)** — pin the four-row matrix above:

- NONE + blank token: headers carry **no** `Authorization` key and nothing
  throws, on each of the **five** public prepare methods: `prepareGetDelete`,
  `prepareAndValidate`, `prepareAndValidatePatch` (public and callable
  directly, not only via its two delegates), `prepareAndValidateMergePatch`
  and `prepareAndValidateJsonPatch`.
- NONE + explicit token: `Authorization` equals the token verbatim, no leading
  space, no scheme prefix.
- BEARER + blank token: throws `ERR_EMPTY_AUTH_TOKEN`. This is a **new**
  assertion, not a preserved one — see *What the existing guard actually
  catches*. It must fail against the current code before the fix is applied;
  if it passes, the test is not testing the token.
- BEARER + non-blank token: `Authorization: Bearer <token>` — unchanged.
- Delete `prepareAndValidate_throwsWhenAuthorizationAbsent`, which encodes the
  removed header inspection. Keep the null-consumer tests.

**Fix the IT fixtures first — every existing IT is a NONE client.** All rest,
reactive and hub ITs build the client from a bare `new ClientProperties()`
(`TmfClientIT.java:76`, `GenericTmfClientIT.java:56`,
`ReactiveTmfClientIT.java:78/798/945`, `GenericReactiveTmfClientIT.java:54`,
`TmfHubClientIT.java:60`, `ReactiveTmfHubClientIT.java:58`, the two hub
`*ScopeTest`s) paired with a mock token service that returns
`Bearer mock-test-token`. With neither auth block set, `getAuthType()` is NONE,
so after the fix **every existing IT would stop sending `Authorization`** and
still pass, because no IT asserts on that header. The authenticated path would
silently lose all integration coverage.

So, as part of this change and before the fix lands:

- In each fixture, set `clientProperties.setBearerAuth(new BearerAuthConfig())`.
  `getAuthType()` only tests the block for non-null, so an otherwise empty
  config is enough; the mock token service is unaffected.
- In one IT per module (`-rest`, `-reactive`, `-hub` sync and reactive), add a
  MockServer expectation that matches on
  `Authorization: Bearer mock-test-token`, so the suites finally assert what
  they have been assuming.

**NONE-client ITs, one per call site.** `HeaderUtil` is shared, but the four
`headers(...)` factories are not, and the hub module has its own module-level
coverage gate. For each of `TmfClientImpl`, `ReactiveTmfClientImpl`,
`TmfHubClientImpl` and `ReactiveTmfHubClientImpl`:

- a bare `new ClientProperties()` plus a token service returning `""` / `""`
  (mirror of `NoOp*TokenService`), against a MockServer expectation that
  rejects any request carrying an `Authorization` header;
- one round trip per verb family for the generic clients (GET, list, POST,
  PUT, merge-patch, JSON-patch, collection patch, DELETE), and
  `registerListener` + `unregisterListener` for the hub clients;
- one `…WithToken("opaque-credential", …)` call asserting the verbatim header.

## Impact on consumers

Any project pointing an `opentmf.api-clients` server at an unauthenticated TMF
endpoint. Present workaround: configure a `bearer-auth` or `basic-auth` block on
the referenced http-client even where the target requires none.

Known affected: `external-task-generator-suite` generates opentmf client
scaffolding from an OAS, and currently forces bearer-auth scaffolding for every
TMF-matched client because of this defect — a `platform-internal: false` client
cannot be emitted honestly until this is fixed.

**Behaviour change for authenticated clients.** A BEARER or BASIC client whose
token service returns a blank token now fails locally with
`IllegalArgumentException: Authorization token must not be empty.` instead of
sending `Authorization: Bearer ` and receiving a 401. This is the behaviour the
guard was always documented to provide; it goes into the CHANGELOG as
`### Changed` so nobody is surprised by the new exception.

## Review corrections (2026-09-07)

Three points found when the plan was checked against `2.1.1-SNAPSHOT`, all
folded into the sections above:

1. The claim that `validateAuthorization` protects BEARER clients was wrong —
   `hasText("Bearer ")` is `true`. The empty-token check is now a **new**
   guarantee, tested as such.
2. The original fix put the flag in `headersConsumer` but the throw in
   `validateAuthorization`, which cannot see it. The check moved into
   `headersConsumer`; `validateAuthorization` is removed.
3. Every IT fixture is a NONE client by construction. The fixtures gain a
   `BearerAuthConfig` and an `Authorization` assertion before the fix lands.

The open question about `…WithToken` on a NONE client is decided: send the
caller's token verbatim.

## Review corrections (2026-09-07, second pass)

Independent senior review against the code; three fixations folded into the
sections above:

1. **The fifth parameter is `AuthType`, not `boolean`.** A bare
   `authRequired = true` is unreadable at every call site. The common module
   already depends on `opentmf-http-clients-common`, so passing
   `clientProperties.getAuthType()` costs nothing and keeps the signature
   stable if auth types are ever added. The consumer's check becomes
   `authType != AuthType.NONE`.
2. **`HeaderUtil` has five public prepare methods, not four.** The generic
   `prepareAndValidatePatch(consumer, mediaType)` is public and callable
   directly, not only via the merge/JSON delegates. The NONE-matrix unit tests
   cover all five.
3. **Noted edge, accepted as-is:** a BEARER/BASIC client whose token service
   returns blank but whose `fixed-headers` (or request context) supply their
   own `Authorization` value passes validation today and will **throw** after
   the fix — the check reads the token, not the assembled headers. Supplying
   `Authorization` via fixed headers on an authenticated client is a
   misconfiguration, not a supported pattern; the new exception is the correct
   outcome and needs no carve-out. Recorded so the behaviour change is a
   decision, not a surprise.
