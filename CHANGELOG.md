# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

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
