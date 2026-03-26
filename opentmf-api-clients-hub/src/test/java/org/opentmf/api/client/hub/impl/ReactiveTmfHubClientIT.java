package org.opentmf.api.client.hub.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.opentmf.client.common.model.ClientProperties;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class ReactiveTmfHubClientIT {

  private ReactiveTmfHubClientImpl client;
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

    EndpointConfig endpointConfig = new EndpointConfig();
    endpointConfig.setPath(path);
    Map<Scope, String> scopes = new EnumMap<>(Scope.class);
    scopes.put(Scope.POST, "POST_SCOPE");
    scopes.put(Scope.DELETE, "DELETE_SCOPE");
    endpointConfig.setScopes(scopes);

    ClientProperties clientProperties = new ClientProperties();
    clientProperties.setNumRetries(0);
    clientProperties.setRetryWaitDuration(Duration.ofMillis(100));

    WebClient webClient = WebClient.builder()
        .baseUrl(MockServerUtils.getBaseUrl()).build();

    client = new ReactiveTmfHubClientImpl(
        endpointConfig, serverConfig, webClient, new MockTokenService(), clientProperties);
  }

  private EventSubscriptionInput subscriptionInput() {
    var input = new EventSubscriptionInput();
    input.setCallback(URI.create("http://myapp:8080/listener"));
    input.setQuery("eventType=ProductOrderCreateEvent");
    return input;
  }

  @Test
  void registerListener_createsSubscription() {
    MockServerUtils.setUpHubCallbacks(path);

    StepVerifier.create(client.registerListener(subscriptionInput()))
        .assertNext(reg -> {
          assertThat(reg).isNotNull();
          assertThat(reg.getId()).isNotBlank();
          assertThat(reg.getHubUri()).isNotNull();
          assertThat(reg.getHubUri().toString()).contains(path);
        })
        .verifyComplete();
  }

  @Test
  void registerListener_withToken_createsSubscription() {
    MockServerUtils.setUpHubCallbacks(path);

    StepVerifier.create(client.registerListener("custom-token", subscriptionInput()))
        .assertNext(reg -> {
          assertThat(reg).isNotNull();
          assertThat(reg.getId()).isNotBlank();
        })
        .verifyComplete();
  }

  @Test
  void unregisterListener_byId() {
    MockServerUtils.setUpHubCallbacks(path);

    HubRegistration reg = client.registerListener(subscriptionInput()).block();
    assertThat(reg).isNotNull();

    StepVerifier.create(client.unregisterListener(reg.getId()))
        .verifyComplete();
  }

  @Test
  void unregisterListener_byIdAndToken() {
    MockServerUtils.setUpHubCallbacks(path);

    HubRegistration reg = client.registerListener(subscriptionInput()).block();
    assertThat(reg).isNotNull();

    StepVerifier.create(client.unregisterListener("custom-token", reg.getId()))
        .verifyComplete();
  }

  @Test
  void unregisterListener_byRegistration() {
    MockServerUtils.setUpHubCallbacks(path);

    HubRegistration reg = client.registerListener(subscriptionInput()).block();
    assertThat(reg).isNotNull();

    StepVerifier.create(client.unregisterListener(reg))
        .verifyComplete();
  }

  @Test
  void unregisterListener_byRegistration_differentServer() {
    MockServerUtils.setUpHubCallbacks(path);

    HubRegistration reg = client.registerListener(subscriptionInput()).block();
    assertThat(reg).isNotNull();

    reg.setHubUri(URI.create(MockServerUtils.getBaseUrl() + path));
    MockServerUtils.setUpDynamicDeleteCallback(path);

    StepVerifier.create(client.unregisterListener(reg))
        .verifyComplete();
  }

  @Test
  void registerListener_nullInput_throwsNpe() {
    assertThatThrownBy(() -> client.registerListener("token", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("must not be null");
  }

  @Test
  void unregisterListener_nullId_throwsNpe() {
    assertThatThrownBy(() -> client.unregisterListener("token", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void unregisterListener_nullRegistration_throwsNpe() {
    assertThatThrownBy(() -> client.unregisterListener((HubRegistration) null))
        .isInstanceOf(NullPointerException.class);
  }
}
