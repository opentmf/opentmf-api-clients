package org.opentmf.api.client.reactive.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.reactive.api.GenericReactiveTmfClient;
import org.opentmf.api.client.reactive.helper.MockServerUtils;
import org.opentmf.api.client.reactive.helper.MockTokenService;
import org.opentmf.api.client.reactive.helper.TestResponseClass;
import org.opentmf.api.client.reactive.helper.TestResponseModel;
import org.opentmf.client.common.exception.OpenTmfClientResponseException;
import org.opentmf.client.common.model.ClientProperties;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class GenericReactiveTmfClientIT {

  private GenericReactiveTmfClient client;
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
    path = MockServerUtils.randomPath();

    ServerConfig serverConfig = new ServerConfig();
    serverConfig.setBaseUrl(MockServerUtils.getBaseUrl());
    serverConfig.setContextPath("");
    serverConfig.setClientRef("default");
    serverConfig.setEndpoints(Map.of());

    EndpointConfig endpointConfig = new EndpointConfig();
    endpointConfig.setPath(path);

    ClientProperties clientProperties = new ClientProperties();
    clientProperties.setNumRetries(0);
    clientProperties.setRetryWaitDuration(Duration.ofMillis(100));

    client = new GenericReactiveTmfClientImpl(
        endpointConfig, serverConfig,
        WebClient.create(), new MockTokenService(),
        clientProperties);
  }

  private static Map<String, Object> testDataMap() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "TestItem-" + ThreadLocalRandom.current().nextInt(10000));
    data.put("description", "Test description " + System.currentTimeMillis());
    data.put("randomNumber", ThreadLocalRandom.current().nextInt(1, 1000));
    return data;
  }

  @Test
  void postAndGet_withExplicitType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    Map<String, Object> data = testDataMap();

    TestResponseModel posted = client.post(data, TestResponseModel.class).block();
    assertThat(posted).isNotNull();
    String id = posted.getId();

    StepVerifier.create(client.get(id, TestResponseModel.class))
        .assertNext(res -> {
          assertThat(res.getId()).isEqualTo(id);
          assertThat(res.getName()).isEqualTo(data.get("name"));
        })
        .verifyComplete();
  }

  @Test
  void postAndList_withExplicitType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);

    client.post(testDataMap(), TestResponseModel.class).block();
    client.post(testDataMap(), TestResponseModel.class).block();

    StepVerifier.create(client.list(TestResponseClass.class).collectList())
        .assertNext(list -> {
          assertThat(list).hasSizeGreaterThanOrEqualTo(2);
          assertThat(list.get(0).getId()).isNotBlank();
        })
        .verifyComplete();
  }

  @Test
  void get_nonExistentResource_throwsException() {
    StepVerifier.create(client.get("non-existent-id", TestResponseModel.class))
        .expectErrorMatches(error ->
            error instanceof OpenTmfClientResponseException)
        .verify();
  }

  @Test
  void listAll_withExplicitType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 15);

    StepVerifier.create(client.listAll(TestResponseModel.class).collectList())
        .assertNext(list -> assertThat(list).hasSize(15))
        .verifyComplete();
  }

  @Test
  void delete_withExplicitType_nonExistent_throwsException() {
    StepVerifier.create(client.delete("non-existent"))
        .expectErrorMatches(error ->
            error instanceof OpenTmfClientResponseException)
        .verify();
  }
}
