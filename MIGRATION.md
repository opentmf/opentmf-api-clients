# Migration guide

This page is for users moving to **`opentmf-api-clients`** from one of the predecessor
libraries. New users do not need to read it — start from the [README](README.md).

---

## From `opentmf-clients-base`

`opentmf-clients-base` remains in maintenance mode for Spring Boot 3.x users.
`opentmf-api-clients` targets Spring Boot 4.x and Java 17+, and re-shapes the configuration
namespace and the public types.

### Configuration keys

| `opentmf-clients-base` | `opentmf-api-clients` |
|---|---|
| `opentmf.tmf-clients.<id>.base-url` | `opentmf.api-clients.<server>.base-url` |
| `opentmf.tmf-clients.<id>.endpoint` | `opentmf.api-clients.<server>.endpoints.<ep>.path` |
| `opentmf.tmf-clients.<id>.scopes` | `opentmf.api-clients.<server>.endpoints.<ep>.scopes` |

The new shape groups endpoints under a logical *server* so that `base-url` and
`context-path` are written once per server instead of once per endpoint.

### Public types

| `opentmf-clients-base` | `opentmf-api-clients` |
|---|---|
| `GenericClient` | `GenericReactiveTmfClient` or `GenericTmfClient` |
| `TmfClientBaseImpl` | `ReactiveTmfClientImpl` or `TmfClientImpl` |
| `TmfClientProvider` | `ReactiveTmfClientFactory` or `TmfClientFactory` |
| `TmfClientException` (per endpoint) | `OpenTmfClientResponseException` (shared) |

The reactive and synchronous surfaces are now separate modules
(`opentmf-api-clients-reactive` / `opentmf-api-clients-rest`). REST-only apps no longer pull
in WebFlux or Reactor.

---

## From `opentmf-hub-v4-client`

The hub functionality lives in the `opentmf-api-clients-hub` module. It is no longer a
separate library and no longer reactive-only.

| `opentmf-hub-v4-client` | `opentmf-api-clients-hub` |
|---|---|
| `HubClient` (reactive only) | `TmfHubClient` (sync) + `ReactiveTmfHubClient` (reactive) |
| `HubClientImpl extends TmfClientBaseImpl` | `TmfHubClientImpl` / `ReactiveTmfHubClientImpl` (composition) |
| `ExtendedEventSubscription` | `HubRegistration` |
| `EventSubscription` (from `opentmf-v4-models`) | `EventSubscription` (self-contained) |
| `HubClientProvider` + manual bean creation | Auto-configuration detects `/hub` endpoints |
| Throws `UnsupportedOperationException` for CRUD | CRUD methods not exposed at all |

See the [Hub Client](README.md#hub-client) section in the README for the full configuration
and usage examples.
