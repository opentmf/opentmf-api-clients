package org.opentmf.api.client.hub.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.model.Scope;
import org.opentmf.api.client.hub.helper.MockServerUtils;
import org.opentmf.api.client.hub.helper.MockSyncTokenService;
import org.opentmf.api.client.hub.model.EventSubscriptionInput;
import org.opentmf.api.client.hub.model.HubRegistration;
import org.opentmf.client.common.model.ClientProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Tests for the scope-fallback branch in TmfHubClientImpl and empty-scope handling.
 */
class TmfHubClientScopeTest {

  private TmfHubClientImpl clientWithNoScopes;
  private TmfHubClientImpl clientWithBlankScopes;
  private String path;

  @BeforeAll
  static void startServer() {
    MockServerUtils.startMockServer();
  }

  @AfterAll
  static void stopServer() {
    MockServerUtils.stopMockServer();
  }

  @BeforeEach
  void setUp() {
    MockServerUtils.resetMockServer();
    path = MockServerUtils.randomHubPath();

    ServerConfig serverConfig = new ServerConfig();
    serverConfig.setBaseUrl(MockServerUtils.getBaseUrl());
    serverConfig.setContextPath("");
    serverConfig.setClientRef("default");
    serverConfig.setEndpoints(Map.of());

    ClientProperties clientProperties = new ClientProperties();
    clientProperties.setNumRetries(0);
    clientProperties.setRetryWaitDuration(Duration.ofMillis(100));

    RestClient restClient = RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory()).build();

    EndpointConfig noScopeConfig = new EndpointConfig();
    noScopeConfig.setPath(path);
    noScopeConfig.setScopes(new EnumMap<>(Scope.class));

    clientWithNoScopes = new TmfHubClientImpl(
        noScopeConfig, serverConfig, restClient, new MockSyncTokenService(), clientProperties);

    EndpointConfig blankScopeConfig = new EndpointConfig();
    blankScopeConfig.setPath(path);
    Map<Scope, String> blankScopes = new EnumMap<>(Scope.class);
    blankScopes.put(Scope.POST, "  ");
    blankScopes.put(Scope.DELETE, "");
    blankScopeConfig.setScopes(blankScopes);

    clientWithBlankScopes = new TmfHubClientImpl(
        blankScopeConfig, serverConfig, restClient, new MockSyncTokenService(), clientProperties);
  }

  private EventSubscriptionInput subscriptionInput() {
    var input = new EventSubscriptionInput();
    input.setCallback(URI.create("http://myapp:8080/listener"));
    return input;
  }

  @Test
  void registerListener_noScopes_usesDefaultToken() {
    MockServerUtils.setUpHubCallbacks(path);
    HubRegistration reg = clientWithNoScopes.registerListener(subscriptionInput());
    assertThat(reg.getId()).isNotBlank();
  }

  @Test
  void registerListener_blankScopes_usesDefaultToken() {
    MockServerUtils.setUpHubCallbacks(path);
    HubRegistration reg = clientWithBlankScopes.registerListener(subscriptionInput());
    assertThat(reg.getId()).isNotBlank();
  }

  @Test
  void unregisterListener_noScopes_usesDefaultToken() {
    MockServerUtils.setUpHubCallbacks(path);
    HubRegistration reg = clientWithNoScopes.registerListener(subscriptionInput());
    clientWithNoScopes.unregisterListener(reg.getId());
  }

  @Test
  void unregisterListener_byRegistration_blankScopes_usesDefaultToken() {
    MockServerUtils.setUpHubCallbacks(path);
    HubRegistration reg = clientWithBlankScopes.registerListener(subscriptionInput());
    clientWithBlankScopes.unregisterListener(reg);
  }
}
