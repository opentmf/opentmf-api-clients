# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
