package org.opentmf.api.client.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.model.Scope;

class TmfApiClientsConfigTest {

  @Test
  void apiClients_setAndGet() {
    TmfApiClientsConfig config = new TmfApiClientsConfig();
    ServerConfig server = new ServerConfig();
    server.setClientRef("ref");
    server.setBaseUrl("http://localhost");

    config.setApiClients(Map.of("srv", server));

    assertThat(config.getApiClients()).containsKey("srv");
    assertThat(config.getApiClients().get("srv")).isSameAs(server);
  }

  @Test
  void serverConfig_defaults() {
    ServerConfig server = new ServerConfig();

    assertThat(server.getContextPath()).isEmpty();
    assertThat(server.getClientType()).isNull();
    assertThat(server.getEndpoints()).isNull();
  }

  @Test
  void serverConfig_settersAndGetters() {
    ServerConfig server = new ServerConfig();
    server.setClientRef("myRef");
    server.setBaseUrl("http://host:8080");
    server.setContextPath("/api/v1");
    server.setClientType("rest");

    EndpointConfig ep = new EndpointConfig();
    ep.setPath("/orders");
    server.setEndpoints(Map.of("order", ep));

    assertThat(server.getClientRef()).isEqualTo("myRef");
    assertThat(server.getBaseUrl()).isEqualTo("http://host:8080");
    assertThat(server.getContextPath()).isEqualTo("/api/v1");
    assertThat(server.getClientType()).isEqualTo("rest");
    assertThat(server.getEndpoints()).containsKey("order");
  }

  @Test
  void endpointConfig_defaults() {
    EndpointConfig ep = new EndpointConfig();

    assertThat(ep.getScopes()).isInstanceOf(EnumMap.class);
    assertThat(ep.getScopes()).isEmpty();
  }

  @Test
  void endpointConfig_settersAndGetters() {
    EndpointConfig ep = new EndpointConfig();
    ep.setPath("/products");

    Map<Scope, String> scopes = new EnumMap<>(Scope.class);
    scopes.put(Scope.GET, "GET_PRODUCT");
    scopes.put(Scope.POST, "POST_PRODUCT");
    ep.setScopes(scopes);

    assertThat(ep.getPath()).isEqualTo("/products");
    assertThat(ep.getScopes()).hasSize(2);
    assertThat(ep.getScopes()).containsEntry(Scope.GET, "GET_PRODUCT");
  }
}
