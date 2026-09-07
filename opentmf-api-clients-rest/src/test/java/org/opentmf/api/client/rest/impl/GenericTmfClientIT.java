package org.opentmf.api.client.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.rest.api.RestGenericTmfClient;
import org.opentmf.api.client.rest.helper.MockServerUtils;
import org.opentmf.api.client.rest.helper.MockSyncTokenService;
import org.opentmf.api.client.rest.helper.TestResponseClass;
import org.opentmf.api.client.rest.helper.TestResponseModel;
import org.opentmf.client.common.model.BearerAuthConfig;
import org.opentmf.client.common.model.ClientProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class GenericTmfClientIT {

  private RestGenericTmfClient client;
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
    clientProperties.setBearerAuth(new BearerAuthConfig());

    RestClient restClient = RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory()).build();

    client = new GenericTmfClientImpl(
        endpointConfig, serverConfig,
        restClient, new MockSyncTokenService(),
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

    TestResponseModel posted = client.post(data, TestResponseModel.class);
    assertThat(posted).isNotNull();
    String id = posted.getId();

    TestResponseModel fetched = client.get(id, TestResponseModel.class);
    assertThat(fetched.getId()).isEqualTo(id);
    assertThat(fetched.getName()).isEqualTo(data.get("name"));
  }

  @Test
  void postAndList_withExplicitType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);

    client.post(testDataMap(), TestResponseModel.class);
    client.post(testDataMap(), TestResponseModel.class);

    List<TestResponseClass> list = client.list(TestResponseClass.class);
    assertThat(list).hasSizeGreaterThanOrEqualTo(2);
    assertThat(list.get(0).getId()).isNotBlank();
  }

  @Test
  void get_nonExistentResource_throwsException() {
    assertThatThrownBy(() -> client.get("non-existent-id", TestResponseModel.class))
        .isInstanceOf(RestClientException.class);
  }

  @Test
  void listAll_withExplicitType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 15);

    List<TestResponseModel> list = client.listAll(TestResponseModel.class);
    assertThat(list).hasSize(15);
  }
}
