# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [3.0.0] - unreleased

### Fixed
- No-auth (`AuthType.NONE`) clients work: an `opentmf.api-clients` server whose
  `client-ref` points at an http-client with neither `bearer-auth` nor
  `basic-auth` block previously threw
  `IllegalArgumentException: Authorization token must not be empty.` on every
  call, before any request reached the network. Such a client now sends no
  `Authorization` header at all. A token passed explicitly to a `…WithToken`
  overload on a NONE client is sent verbatim as the whole credential (pass
  `"Bearer eyJ…"` if a scheme is needed).

### Changed
- **Breaking:** reactive `listPaged*` returns `Mono<TmfPage<List<R>>>` instead
  of `Mono<TmfPage<Flux<R>>>`. A body `Flux` inside a page was a
  single-subscription live connection stream: reading only the metadata (which
  our own tests did) left the connection undrained, and the content could
  never be read twice. Pages are now fully materialized. Migration:
  `flatMapMany(TmfPage::getContent)` becomes
  `flatMapIterable(TmfPage::getContent)` — see MIGRATION.md. As a side effect,
  reactive `list`/`listAll` keep their `Flux<R>` signatures but buffer each
  page before emitting its items.
- **A BEARER or BASIC client whose token service returns a blank token now
  fails locally** with `IllegalArgumentException: Authorization token must not
  be empty.` instead of sending `Authorization: Bearer ` and collecting a
  remote 401. This is the behaviour the old guard was always documented to
  provide; it never actually fired for authenticated clients.
- `HeaderUtil.headersConsumer(...)` gains a fifth parameter — the client's
  `AuthType` — and owns the whole authorization decision. The `prepare*`
  methods no longer reject header sets without `Authorization` (a NONE
  client's headers legitimately carry none); `validateAuthorization` is
  removed. No delegating four-argument overload is kept, so every call site
  states its auth type explicitly.

## [2.1.0] - 2026-08-20

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
- New immutable value type `SubResourcePath` in the common module, plus
  `SubResourcePath`-accepting overloads of the `UriBuilderUtil` builders. The
  pre-existing builder methods are untouched; with an empty suffix the new
  overloads delegate to them, so existing URI construction is byte-identical.

### Changed
- Direct callers of `UriBuilderUtil.buildUriWithId(server, endpoint, id, null)` that pass a
  **null literal** as the fourth argument must now cast it
  (`(TmfRequestContext) null`) because of the new `SubResourcePath` overload.
  Binary compatibility is unaffected; the typed clients' public API is unaffected.
- Bumped the Spring Boot BOM to 4.1.1 and `opentmf-http-clients` to 2.1.8.
- Building from source now requires JDK 17 and Maven 3.9.x, enforced by
  maven-enforcer-plugin. JDK 23+ silently breaks Lombok annotation processing;
  the enforcer turns that into an explicit failure.

## [2.0.9] - 2026-05-28

### Added
- `PUT` verb on both the synchronous (`TmfClient`, RestClient impl) and reactive
  (`ReactiveTmfClient`, WebClient impl) surfaces. Eight overloads per interface (4
  auto-token + 4 with-token), mirroring the existing merge-patch family. Issues
  `PUT /{endpoint}/{id}` with content-type `application/json` against the same URI
  + header + retry machinery used by `POST` / `PATCH`. Pure addition: no existing
  signatures change.
- New `Scope.PUT` enum value (`"put"`) so per-endpoint OAuth scope maps can carry a
  dedicated `put:` entry. Fully additive; existing configurations are unaffected.

### Changed
- Bumped `opentmf-mockserver` (test scope) to 2.1.4 for the new `DynamicPutCallback`,
  used by the PUT integration tests.

## [2.0.8] - 2026-05-18

### Fixed
- Fix double percent-encoding of resource path segments when
  `UriBuilderUtil.withContext(...)` or `UriBuilderUtil.withPagination(...)` is applied to
  a URI that already contains an encoded `{id}` (typically TMF composite keys like
  `Spec:(version=1)`). Previously the terminal `.encode().build().toUri()` re-encoded the
  already-encoded path, producing e.g. `%253A%2528version%253D1%2529` instead of
  `%3A%28version%3D1%29`. The methods now pre-encode the appended query-parameter values
  via `UriUtils.encodeQueryParam(...)` and call `.build(true).toUri()`, which preserves the
  existing single encoding of the path.

## [2.0.7] - 2026-05-10

### Added
- Collection-level JSON Patch (`patchCollection` / `patchCollectionWithToken`) on both
  the synchronous (`TmfClient`, RestClient impl) and reactive (`ReactiveTmfClient`,
  WebClient impl) surfaces. Issues `PATCH` against the collection root (no `/{id}`)
  with content-type `application/json-patch+json` and decodes a JSON array response
  into `List<R>` (sync) / `Mono<List<R>>` (reactive). Supports the TMF v4
  high-performance bulk-creation pattern. No `RestTemplate` implementation; no
  `Flux<R>` overloads. Pure addition: no existing signatures change.

## [2.0.6] - 2026-04-16

### Added
- `HeaderUtil.headersConsumer(...)` now defaults `Accept: application/json` when the caller
  did not already provide an `Accept` header via fixed headers or the request context. TMF
  APIs always speak JSON, so this removes the reliance on `*/*` from the underlying HTTP
  client defaults.

## [2.0.5] - 2026-04-14

### Added
- Fixed headers support per TMF client, ported from the retired `opentmf-clients-base` library.
  - `ServerConfig.fixedHeaders`: headers applied to every request made by the client.
  - `EndpointConfig.fixedHeaders`: headers applied to every request made against the endpoint.
  - When both are present, maps are merged; endpoint-level entries override server-level entries
    on key collisions.
- `HeaderUtil.mergeFixedHeaders(server, endpoint)` helper.

### Changed
- `TmfClientImpl` and `ReactiveTmfClientImpl` now forward merged fixed headers to the underlying
  `HeaderUtil.headersConsumer(...)` instead of passing `null`.

## [2.0.4] - 2026-04-06

### Changed
- Bumped opentmf-http-clients version to 2.1.2

## [2.0.3] - 2026-04-06

### Fixed
- All four auto-configuration classes refactored from constructor-based bean registration to
  `static BeanDefinitionRegistryPostProcessor` pattern. This fixes the "Ghost Configuration"
  problem in Spring Boot 4 where auto-configuration classes that only register beans in their
  constructor are never instantiated if no `@Bean` method output is requested by the dependency
  graph. Bean definitions are now registered during the post-processing phase (before any regular
  beans are created), closing the phase gap between component-scanned beans and dynamically
  registered beans. Configuration is read from the `Environment` via `Binder.get(env).bind(...)`
  instead of `@ConfigurationProperties` injection. Bean instances use lazy suppliers with
  `@DependsOn("opentmfHttpClientsStarter")` to ensure HTTP client beans are available at creation
  time. Affected classes:
  - `TmfApiClientsAutoConfiguration` (sync REST)
  - `ReactiveTmfApiClientsAutoConfiguration` (reactive)
  - `TmfHubAutoConfiguration` (sync hub)
  - `ReactiveTmfHubAutoConfiguration` (reactive hub)

### Changed
- Constructor parameter changed from `ApplicationContext` + `BeanDefinitionRegistry` to
  `ConfigurableApplicationContext` (registry obtained via `ctx.getBeanFactory()`).

## [2.0.2] - 2026-04-06

### Changed
- All four auto-configuration classes now declare
  `@AutoConfiguration(after = OpentmfHttpClientsAutoConfiguration.class)` to express ordering
  intent with the HTTP clients starter.

## [2.0.1] - 2026-04-03

### Changed
- Bumped `opentmf-http-clients` to 2.1.1.
- Bumped `spring-boot` to 4.0.5.

## [2.0.0] - 2026-03-25

### Added
- Initial implementation of `opentmf-api-clients` multi-module project.
- `opentmf-api-clients-common`: shared interfaces (`TmfClient`, `GenericTmfClient`), config model
  (`TmfApiClientsConfig` with `ServerConfig` and `EndpointConfig`), pagination model
  (`TmfOffsetRequest`, `TmfPage`, `OffsetPage`), request context (`TmfRequestContext`, `JsonFilter`),
  and utilities (`UriBuilderUtil`, `HeaderUtil`, `ResponseHeaderUtil`).
- `opentmf-api-clients-reactive`: `ReactiveTmfClient` and `GenericReactiveTmfClient` interfaces,
  `ReactiveTmfClientImpl` (fully concrete, WebClient-based), `GenericReactiveTmfClientImpl`,
  `ReactiveTmfClientFactory` (Level 2 typed usage), and `ReactiveTmfApiClientsAutoConfiguration`.
- `opentmf-api-clients-rest`: `TmfClientImpl` (fully concrete, RestClient-based),
  `GenericTmfClientImpl`, `TmfClientFactory` (Level 2 typed usage), and
  `TmfApiClientsAutoConfiguration`.
- Three-level usage model: zero custom code (Level 1), single `@Bean` factory method (Level 2),
  custom subclass (Level 3).
- Grouped configuration: endpoints share `base-url` and `client-ref` within a server block.
- Content-Type defaults: POST defaults to `application/json`, PATCH defaults to the expected patch
  media type; a `WARN` log is emitted when the caller overrides the patch Content-Type.
- Reactive and REST modules bring their own auto-configuration, ensuring no cross-module dependency
  leakage.
- Unit tests for `HeaderUtil`, `UriBuilderUtil`, `ResponseHeaderUtil`, and `TmfOffsetRequest`.

### Dependencies
- Built on `opentmf-http-clients` 2.1.0 and Spring Boot 4.0.4.
- Jackson 3.x (`tools.jackson`) throughout; no Jackson 2 (`com.fasterxml.jackson`) dependencies.
- JSON Patch (RFC 6902) via `opentmf-json-patch` 1.0.0 (Jackson 3 native).
- `opentmf-mockserver` 2.1.2 for integration tests (replaces standalone `mockserver-netty`).
