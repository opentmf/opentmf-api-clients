package org.opentmf.api.client.hub.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.model.Scope;
import org.opentmf.api.client.hub.helper.MockServerUtils;
import org.opentmf.api.client.hub.helper.MockSyncTokenService;
import org.opentmf.api.client.hub.model.EventSubscriptionInput;
import org.opentmf.api.client.hub.model.HubRegistration;
import org.opentmf.client.common.model.BearerAuthConfig;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class TmfHubClientIT {

  private TmfHubClientImpl client;
  private String path;
  private ServerConfig serverConfig;
  private EndpointConfig endpointConfig;

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

    serverConfig = new ServerConfig();
    serverConfig.setBaseUrl(MockServerUtils.getBaseUrl());
    serverConfig.setContextPath("");
    serverConfig.setClientRef("default");
    serverConfig.setEndpoints(Map.of());

    endpointConfig = new EndpointConfig();
    endpointConfig.setPath(path);
    Map<Scope, String> scopes = new EnumMap<>(Scope.class);
    scopes.put(Scope.POST, "POST_SCOPE");
    scopes.put(Scope.DELETE, "DELETE_SCOPE");
    endpointConfig.setScopes(scopes);

    ClientProperties clientProperties = new ClientProperties();
    clientProperties.setNumRetries(0);
    clientProperties.setRetryWaitDuration(Duration.ofMillis(100));
    clientProperties.setBearerAuth(new BearerAuthConfig());

    RestClient restClient = RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory()).build();

    client = new TmfHubClientImpl(
        endpointConfig, serverConfig, restClient, new MockSyncTokenService(), clientProperties);
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

    HubRegistration reg = client.registerListener(subscriptionInput());

    assertThat(reg).isNotNull();
    assertThat(reg.getId()).isNotBlank();
    assertThat(reg.getHubUri()).isNotNull();
    assertThat(reg.getHubUri().toString()).contains(path);
  }

  @Test
  void registerListener_sendsBearerAuthorizationHeader() {
    MockServerUtils.setUpHubCallbacks(path);

    client.registerListener(subscriptionInput());

    HttpRequest[] recorded = MockServerUtils.getMockServer()
        .retrieveRecordedRequests(request().withMethod("POST").withPath(path));
    assertThat(recorded).hasSize(1);
    assertThat(recorded[0].getFirstHeader("Authorization")).isEqualTo("Bearer mock-hub-token");
  }

  @Test
  void registerListener_withToken_createsSubscription() {
    MockServerUtils.setUpHubCallbacks(path);

    HubRegistration reg = client.registerListener("custom-token", subscriptionInput());

    assertThat(reg).isNotNull();
    assertThat(reg.getId()).isNotBlank();
  }

  @Test
  void unregisterListener_byId() {
    MockServerUtils.setUpHubCallbacks(path);
    HubRegistration reg = client.registerListener(subscriptionInput());

    client.unregisterListener(reg.getId());
  }

  @Test
  void unregisterListener_byIdAndToken() {
    MockServerUtils.setUpHubCallbacks(path);
    HubRegistration reg = client.registerListener(subscriptionInput());

    client.unregisterListener("custom-token", reg.getId());
  }

  @Test
  void unregisterListener_byRegistration() {
    MockServerUtils.setUpHubCallbacks(path);
    HubRegistration reg = client.registerListener(subscriptionInput());

    client.unregisterListener(reg);
  }

  @Test
  void unregisterListener_byRegistration_differentServer() {
    MockServerUtils.setUpHubCallbacks(path);
    HubRegistration reg = client.registerListener(subscriptionInput());

    reg.setHubUri(URI.create(MockServerUtils.getBaseUrl() + path));
    MockServerUtils.setUpDynamicDeleteCallback(path);

    client.unregisterListener(reg);
  }

  @Test
  void registerListener_nullInput_throwsNpe() {
    assertThatThrownBy(() -> client.registerListener(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("must not be null");
  }

  @Test
  void unregisterListener_nullId_throwsNpe() {
    assertThatThrownBy(() -> client.unregisterListener((String) null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void unregisterListener_nullRegistration_throwsNpe() {
    assertThatThrownBy(() -> client.unregisterListener((HubRegistration) null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void unregisterListener_registrationWithNullHubUri_throwsNpe() {
    var reg = new HubRegistration();
    reg.setId("x");
    assertThatThrownBy(() -> client.unregisterListener(reg))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("hubUri");
  }

  @Test
  void unregisterListener_registrationWithNullId_throwsNpe() {
    var reg = new HubRegistration();
    reg.setHubUri(URI.create("http://somewhere"));
    assertThatThrownBy(() -> client.unregisterListener(reg))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("id");
  }

  // --- NONE-auth client (no bearer/basic block): no Authorization header, ever ---

  private TmfHubClientImpl noneAuthClient() {
    ClientProperties noneProps = new ClientProperties();
    noneProps.setNumRetries(0);
    noneProps.setRetryWaitDuration(Duration.ofMillis(100));
    // no bearer-auth / basic-auth block: getAuthType() == NONE

    SyncTokenService blankTokenService = new SyncTokenService() {
      @Override public String getTokenType() { return ""; }
      @Override public String getToken() { return ""; }
      @Override public String getToken(String additionalScopes) { return ""; }
    };

    return new TmfHubClientImpl(endpointConfig, serverConfig,
        RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()).build(),
        blankTokenService, noneProps);
  }

  @Test
  void noneAuthClient_registerAndUnregister_sendNoAuthorizationHeader() {
    MockServerUtils.setUpHubCallbacks(path);
    var noneClient = noneAuthClient();

    HubRegistration reg = noneClient.registerListener(subscriptionInput());
    assertThat(reg.getId()).isNotBlank();
    noneClient.unregisterListener(reg.getId());

    HttpRequest[] all = MockServerUtils.getMockServer().retrieveRecordedRequests(request());
    assertThat(all).isNotEmpty();
    for (HttpRequest r : all) {
      assertThat(r.containsHeader("Authorization"))
          .as("request %s %s must carry no Authorization header", r.getMethod(), r.getPath())
          .isFalse();
    }
  }

  @Test
  void noneAuthClient_registerWithExplicitToken_sendsItVerbatim() {
    MockServerUtils.setUpHubCallbacks(path);

    HubRegistration reg = noneAuthClient().registerListener("opaque-credential", subscriptionInput());
    assertThat(reg.getId()).isNotBlank();

    HttpRequest[] recorded = MockServerUtils.getMockServer()
        .retrieveRecordedRequests(request().withMethod("POST").withPath(path));
    assertThat(recorded).hasSize(1);
    assertThat(recorded[0].getFirstHeader("Authorization")).isEqualTo("opaque-credential");
  }
}
