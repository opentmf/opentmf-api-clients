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
import org.opentmf.api.client.hub.helper.MockTokenService;
import org.opentmf.api.client.hub.model.EventSubscriptionInput;
import org.opentmf.api.client.hub.model.HubRegistration;
import org.opentmf.client.common.model.BearerAuthConfig;
import org.opentmf.client.common.model.ClientProperties;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/**
 * Tests for the scope-fallback branch in ReactiveTmfHubClientImpl.
 */
class ReactiveTmfHubClientScopeTest {

  private ReactiveTmfHubClientImpl clientWithNoScopes;
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
    clientProperties.setBearerAuth(new BearerAuthConfig());

    WebClient webClient = WebClient.builder()
        .baseUrl(MockServerUtils.getBaseUrl()).build();

    EndpointConfig noScopeConfig = new EndpointConfig();
    noScopeConfig.setPath(path);
    noScopeConfig.setScopes(new EnumMap<>(Scope.class));

    clientWithNoScopes = new ReactiveTmfHubClientImpl(
        noScopeConfig, serverConfig, webClient, new MockTokenService(), clientProperties);
  }

  private EventSubscriptionInput subscriptionInput() {
    var input = new EventSubscriptionInput();
    input.setCallback(URI.create("http://myapp:8080/listener"));
    return input;
  }

  @Test
  void registerListener_noScopes_usesDefaultToken() {
    MockServerUtils.setUpHubCallbacks(path);

    StepVerifier.create(clientWithNoScopes.registerListener(subscriptionInput()))
        .assertNext(reg -> assertThat(reg.getId()).isNotBlank())
        .verifyComplete();
  }

  @Test
  void unregisterListener_noScopes_usesDefaultToken() {
    MockServerUtils.setUpHubCallbacks(path);

    HubRegistration reg = clientWithNoScopes.registerListener(subscriptionInput()).block();
    assertThat(reg).isNotNull();

    StepVerifier.create(clientWithNoScopes.unregisterListener(reg.getId()))
        .verifyComplete();
  }

  @Test
  void unregisterListener_byRegistration_noScopes() {
    MockServerUtils.setUpHubCallbacks(path);

    HubRegistration reg = clientWithNoScopes.registerListener(subscriptionInput()).block();
    assertThat(reg).isNotNull();

    StepVerifier.create(clientWithNoScopes.unregisterListener(reg))
        .verifyComplete();
  }
}
