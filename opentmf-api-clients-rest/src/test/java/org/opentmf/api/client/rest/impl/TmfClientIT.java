package org.opentmf.api.client.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.EnumMap;
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
import org.opentmf.api.client.common.model.Scope;
import org.opentmf.api.client.common.model.TmfOffsetRequest;
import org.opentmf.api.client.common.model.TmfPage;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.opentmf.api.client.rest.helper.MockServerUtils;
import org.opentmf.api.client.rest.helper.MockSyncTokenService;
import org.opentmf.api.client.rest.helper.TestResponseClass;
import org.opentmf.api.client.rest.helper.TestResponseModel;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.commons.patch.JsonPatch;
import org.springframework.data.domain.Sort;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@SuppressWarnings("unchecked")
class TmfClientIT {

  private TmfClientImpl<Map<String, Object>, Map<String, Object>, TestResponseModel> client;
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
    Map<Scope, String> scopes = new EnumMap<>(Scope.class);
    scopes.put(Scope.GET, "GET_SCOPE");
    scopes.put(Scope.LIST, "LIST_SCOPE");
    scopes.put(Scope.POST, "POST_SCOPE");
    scopes.put(Scope.PATCH, "PATCH_SCOPE");
    scopes.put(Scope.DELETE, "DELETE_SCOPE");
    endpointConfig.setScopes(scopes);

    ClientProperties clientProperties = new ClientProperties();
    clientProperties.setNumRetries(0);
    clientProperties.setRetryWaitDuration(Duration.ofMillis(100));

    RestClient restClient = RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory()).build();

    client = new TmfClientImpl<>(
        endpointConfig, serverConfig,
        restClient, new MockSyncTokenService(),
        clientProperties, TestResponseModel.class);
  }

  private static Map<String, Object> testDataMap() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("name", "TestItem-" + ThreadLocalRandom.current().nextInt(10000));
    data.put("description", "Test description " + System.currentTimeMillis());
    data.put("randomNumber", ThreadLocalRandom.current().nextInt(1, 1000));
    return data;
  }

  // --- POST ---

  @Test
  void post_createsResource() {
    MockServerUtils.setUpDynamicPostCallback(path);
    Map<String, Object> data = testDataMap();

    TestResponseModel res = client.post(data);
    assertThat(res).isNotNull();
    assertThat(res.getId()).isNotBlank();
    assertThat(res.getHref()).isNotBlank();
    assertThat(res.getName()).isEqualTo(data.get("name"));
  }

  @Test
  void postWithToken_createsResource() {
    MockServerUtils.setUpDynamicPostCallback(path);
    Map<String, Object> data = testDataMap();

    TestResponseModel res = client.postWithToken("custom-token", data);
    assertThat(res.getId()).isNotBlank();
  }

  @Test
  void post_withRequestContext() {
    MockServerUtils.setUpDynamicPostCallback(path);
    Map<String, Object> data = testDataMap();
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withHeaderValues("X-Custom", "value1")
        .build();

    TestResponseModel res = client.post(data, ctx);
    assertThat(res.getId()).isNotBlank();
  }

  @Test
  void post_withAlternateResponseType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    Map<String, Object> data = testDataMap();

    String res = client.post(data, String.class);
    assertThat(res).isNotBlank();
    assertThat(res).contains("id");
  }

  // --- GET ---

  @Test
  void get_returnsResource() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    TestResponseModel res = client.get(id);
    assertThat(res).isNotNull();
    assertThat(res.getId()).isEqualTo(id);
  }

  @Test
  void getWithToken_returnsResource() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    TestResponseModel res = client.getWithToken("custom-token", id);
    assertThat(res.getId()).isEqualTo(id);
  }

  @Test
  void get_withAlternateType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    TestResponseClass res = client.get(id, TestResponseClass.class);
    assertThat(res.getId()).isEqualTo(id);
  }

  @Test
  void get_withRequestContextAndFields() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withFields("id", "name")
        .build();

    TestResponseModel res = client.get(id, ctx);
    assertThat(res.getId()).isEqualTo(id);
  }

  // --- LIST ---

  @Test
  void list_returnsResources() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 5);

    List<TestResponseModel> list = client.list();
    assertThat(list).hasSizeGreaterThanOrEqualTo(5);
  }

  @Test
  void list_withPageable() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 15);

    TmfOffsetRequest pageable = TmfOffsetRequest.of(0, 5);
    List<TestResponseModel> list = client.list(pageable);
    assertThat(list).hasSize(5);
  }

  @Test
  void list_withAlternateType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 3);

    List<TestResponseClass> list = client.list(TestResponseClass.class);
    assertThat(list).hasSizeGreaterThanOrEqualTo(3);
    assertThat(list.get(0).getId()).isNotBlank();
  }

  // --- LIST ALL ---

  @Test
  void listAll_fetchesAllPages() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 25);

    List<TestResponseModel> list = client.listAll();
    assertThat(list).hasSize(25);
  }

  @Test
  void listAll_withSort() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 15);

    TmfOffsetRequest pageable = TmfOffsetRequest.of(0, 5,
        Sort.Direction.DESC, "orderNumber");

    List<TestResponseModel> list = client.listAll(pageable);
    assertThat(list).hasSize(15);
  }

  // --- LIST PAGED ---

  @Test
  void listPaged_returnsMetadata() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 20);

    TmfOffsetRequest pageable = TmfOffsetRequest.of(0, 10);
    TmfPage<List<TestResponseModel>> page = client.listPaged(pageable);

    assertThat(page.getTotalElements()).isEqualTo(20);
    assertThat(page.getTotalPages()).isEqualTo(2);
    assertThat(page.getSize()).isEqualTo(10);
    assertThat(page.getNumber()).isZero();
    assertThat(page.hasNext()).isTrue();
    assertThat(page.isLast()).isFalse();
    assertThat(page.getContent()).hasSize(10);
  }

  @Test
  void listPaged_lastPage() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 5);

    TmfOffsetRequest pageable = TmfOffsetRequest.of(0, 10);
    TmfPage<List<TestResponseModel>> page = client.listPaged(pageable);

    assertThat(page.getTotalElements()).isEqualTo(5);
    assertThat(page.isLast()).isTrue();
    assertThat(page.hasNext()).isFalse();
  }

  // --- MERGE PATCH ---

  @Test
  void patch_mergePatch_updatesResource() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    MockServerUtils.setUpDynamicMergePatchCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    Map<String, Object> update = Map.of("description", "Updated description");

    TestResponseModel res = client.patch(id, update);
    assertThat(res.getId()).isEqualTo(id);
    assertThat(res.getDescription()).isEqualTo("Updated description");
  }

  @Test
  void patch_mergePatch_withRequestContext() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicMergePatchCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    Map<String, Object> update = Map.of("name", "Updated");
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withHeaderValues("X-Custom", "header-val")
        .build();

    TestResponseModel res = client.patch(id, update, ctx);
    assertThat(res.getId()).isEqualTo(id);
  }

  // --- JSON PATCH ---

  @Test
  void patch_jsonPatch_updatesResource() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    MockServerUtils.setUpDynamicJsonPatchCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    JsonPatch jsonPatch = JsonPatch.builder()
        .replace("/description", "JSON-patched description")
        .build();

    TestResponseModel res = client.patch(id, jsonPatch);
    assertThat(res.getId()).isEqualTo(id);
    assertThat(res.getDescription()).isEqualTo("JSON-patched description");
  }

  // --- DELETE ---

  @Test
  void delete_removesResource() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicDeleteCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    client.delete(id);
  }

  @Test
  void deleteWithToken_removesResource() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicDeleteCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    client.deleteWithToken("custom-token", id);
  }

  // --- ADDITIONAL OVERLOADS ---

  @Test
  void get_withCtxAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());
    TmfRequestContext ctx = TmfRequestContext.builder().withFields("id").build();

    TestResponseClass res = client.get(id, ctx, TestResponseClass.class);
    assertThat(res.getId()).isEqualTo(id);
  }

  @Test
  void getWithToken_withCtxAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());
    TmfRequestContext ctx = TmfRequestContext.builder().withFields("id").build();

    TestResponseModel res = client.getWithToken("custom-token", id, ctx);
    assertThat(res.getId()).isEqualTo(id);
  }

  @Test
  void post_withCtxAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);

    TestResponseClass res = client.post(testDataMap(), TmfRequestContext.builder().build(),
        TestResponseClass.class);
    assertThat(res.getId()).isNotBlank();
  }

  @Test
  void postWithToken_withCtxAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);

    TestResponseModel res = client.postWithToken("custom-token", testDataMap(),
        TmfRequestContext.builder().build());
    assertThat(res.getId()).isNotBlank();
  }

  @Test
  void list_withPageableAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 3);

    List<TestResponseClass> list = client.list(TmfOffsetRequest.of(0, 10), TestResponseClass.class);
    assertThat(list).hasSizeGreaterThanOrEqualTo(3);
  }

  @Test
  void listAll_withType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 5);

    List<TestResponseClass> list = client.listAll(TestResponseClass.class);
    assertThat(list).hasSize(5);
  }

  @Test
  void listAll_withPageableAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 10);

    List<TestResponseClass> list = client.listAll(TmfOffsetRequest.of(0, 5),
        TestResponseClass.class);
    assertThat(list).hasSize(10);
  }

  @Test
  void listAll_withClientFilter_characteristicArray() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedDataWithCharacteristics(path);

    TmfOffsetRequest req = TmfOffsetRequest.of(0, 100).withClientFilter(
        "$[?(@.characteristic[?(@.name=='IMEI' && @.value=='123456789012345')] empty false)]");
    List<TestResponseModel> list = client.listAll(req);
    assertThat(list).hasSize(2)
        .extracting(TestResponseModel::getName)
        .containsExactlyInAnyOrder("CharItem-0", "CharItem-3");
  }

  @Test
  void listAll_withServerFilter_characteristicArray() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedDataWithCharacteristics(path);

    TmfOffsetRequest req = TmfOffsetRequest.of(0, 100).withServerFilter(
        "$[?(@.characteristic[?(@.name=='IMEI' && @.value=='123456789012345')] empty false)]");
    List<TestResponseModel> list = client.listAll(req);
    assertThat(list).hasSize(2)
        .extracting(TestResponseModel::getName)
        .containsExactlyInAnyOrder("CharItem-0", "CharItem-3");
  }

  @Test
  void listAll_withServerFilter_simpleFieldEquality() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedDataWithCharacteristics(path);

    TmfOffsetRequest req = TmfOffsetRequest.of(0, 100)
        .withServerFilter("$[?(@.name=='CharItem-2')]");
    List<TestResponseModel> list = client.listAll(req);
    assertThat(list).hasSize(1)
        .extracting(TestResponseModel::getName)
        .containsExactly("CharItem-2");
  }

  @Test
  void listPaged_noArgs() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 3);

    TmfPage<List<TestResponseModel>> page = client.listPaged();
    assertThat(page.getContent()).hasSizeGreaterThanOrEqualTo(3);
  }

  @Test
  void listPaged_withType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 3);

    TmfPage<List<TestResponseClass>> page = client.listPaged(TestResponseClass.class);
    assertThat(page.getContent()).isNotEmpty();
  }

  @Test
  void patchWithToken_mergePatch_withCtxAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicMergePatchCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());
    TmfRequestContext ctx = TmfRequestContext.builder().build();

    TestResponseClass res = client.patch(id, Map.of("name", "Updated"), ctx,
        TestResponseClass.class);
    assertThat(res.getId()).isEqualTo(id);
  }

  @Test
  void patchWithToken_jsonPatch_withCtxAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicJsonPatchCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());
    TmfRequestContext ctx = TmfRequestContext.builder().build();

    JsonPatch jp = JsonPatch.builder().replace("/description", "jp-updated").build();
    TestResponseClass res = client.patch(id, jp, ctx, TestResponseClass.class);
    assertThat(res.getId()).isEqualTo(id);
  }

  @Test
  void delete_withRequestContext() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicDeleteCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());
    TmfRequestContext ctx = TmfRequestContext.builder().build();

    client.delete(id, ctx);
  }

  @Test
  void delete_withType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicDeleteCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    client.delete(id, String.class);
  }

  @Test
  void delete_withTypeAndCtx() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicDeleteCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    client.delete(id, String.class, TmfRequestContext.builder().build());
  }

  // --- FULL CRUD ---

  @Test
  void fullCrudLifecycle() {
    MockServerUtils.setUpAllDynamicCallbacks(path);
    Map<String, Object> data = testDataMap();

    // POST
    TestResponseModel created = client.post(data);
    assertThat(created).isNotNull();
    assertThat(created.getId()).isNotBlank();
    String id = created.getId();

    // GET
    TestResponseModel fetched = client.get(id);
    assertThat(fetched.getId()).isEqualTo(id);

    // MERGE PATCH
    Map<String, Object> patch = Map.of("description", "patched");
    TestResponseModel patched = client.patch(id, patch);
    assertThat(patched.getDescription()).isEqualTo("patched");

    // DELETE
    client.delete(id);
  }
}
