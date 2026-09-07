package org.opentmf.api.client.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
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
import org.opentmf.client.common.model.BearerAuthConfig;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.opentmf.commons.patch.JsonPatch;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
    scopes.put(Scope.PUT, "PUT_SCOPE");
    scopes.put(Scope.PATCH, "PATCH_SCOPE");
    scopes.put(Scope.DELETE, "DELETE_SCOPE");
    endpointConfig.setScopes(scopes);

    ClientProperties clientProperties = new ClientProperties();
    clientProperties.setNumRetries(0);
    clientProperties.setRetryWaitDuration(Duration.ofMillis(100));
    // An empty bearer-auth block makes this an authenticated (BEARER) client; without it,
    // getAuthType() is NONE and the fixture would silently stop exercising the auth path.
    clientProperties.setBearerAuth(new BearerAuthConfig());

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
  void get_sendsBearerAuthorizationHeader() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    client.get(id);

    HttpRequest[] recorded = MockServerUtils.getMockServer()
        .retrieveRecordedRequests(request().withMethod("GET").withPath(path + "/" + id));
    assertThat(recorded).hasSize(1);
    assertThat(recorded[0].getFirstHeader("Authorization")).isEqualTo("Bearer mock-test-token");
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

  // --- COLLECTION JSON PATCH ---

  @Test
  void patchCollection_returnsListInOrder() {
    MockServerUtils.setUpDynamicJsonPatchCollectionCallback(path);

    JsonPatch jp = JsonPatch.builder()
        .add("/", Map.of("name", "A"))
        .add("/", Map.of("name", "B"))
        .add("/", Map.of("name", "C"))
        .build();

    List<TestResponseModel> list = client.patchCollection(jp);
    assertThat(list).hasSize(3);
    assertThat(list).extracting(TestResponseModel::getName).containsExactly("A", "B", "C");
    assertThat(list).extracting(TestResponseModel::getId).doesNotContainNull();
    assertThat(list).extracting(TestResponseModel::getHref).doesNotContainNull();
  }

  @Test
  void patchCollection_withCustomReturnType() {
    MockServerUtils.setUpDynamicJsonPatchCollectionCallback(path);

    JsonPatch jp = JsonPatch.builder()
        .add("/", Map.of("name", "X", "description", "d"))
        .build();

    List<TestResponseClass> list = client.patchCollection(jp, TestResponseClass.class);
    assertThat(list).hasSize(1);
    assertThat(list.get(0).getName()).isEqualTo("X");
    assertThat(list.get(0).getDescription()).isEqualTo("d");
    assertThat(list.get(0).getId()).isNotBlank();
  }

  @Test
  void patchCollectionWithToken_useCallerSuppliedToken() {
    MockServerUtils.setUpDynamicJsonPatchCollectionCallback(path);

    JsonPatch jp = JsonPatch.builder().add("/", Map.of("name", "T")).build();

    List<TestResponseModel> list = client.patchCollectionWithToken("custom-token", jp);
    assertThat(list).hasSize(1);
    assertThat(list.get(0).getName()).isEqualTo("T");
  }

  @Test
  void patchCollection_emptyPatch_returns400() {
    MockServerUtils.setUpDynamicJsonPatchCollectionCallback(path);

    JsonPatch jp = JsonPatch.builder().build();

    assertThatThrownBy(() -> client.patchCollection(jp))
        .isInstanceOf(RestClientException.class);
  }

  @Test
  void patchCollection_5xx_propagatesException() {
    MockServerUtils.setUpCollectionJsonPatchCallback(path, "", HttpStatus.INTERNAL_SERVER_ERROR);

    JsonPatch jp = JsonPatch.builder().add("/", Map.of("name", "A")).build();

    assertThatThrownBy(() -> client.patchCollection(jp))
        .isInstanceOf(RestClientException.class);
  }

  @Test
  void patchCollection_nullPatch_throwsNpe() {
    assertThatThrownBy(() -> client.patchCollection((JsonPatch) null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void patchCollection_noContent_returnsEmptyList() {
    MockServerUtils.setUpCollectionJsonPatchCallback(path, "", HttpStatus.NO_CONTENT);

    JsonPatch jp = JsonPatch.builder().add("/", Map.of("name", "A")).build();

    List<TestResponseModel> list = client.patchCollection(jp);
    assertThat(list).isEmpty();
  }

  // --- PUT ---

  @Test
  void put_replacesResource() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicPutCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    Map<String, Object> replacement = Map.of(
        "name", "Replaced", "description", "Replaced description");

    TestResponseModel res = client.put(id, replacement);
    assertThat(res).isNotNull();
    assertThat(res.getId()).isEqualTo(id);
    assertThat(res.getName()).isEqualTo("Replaced");
    assertThat(res.getDescription()).isEqualTo("Replaced description");
  }

  @Test
  void put_withRequestContext() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicPutCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withHeaderValues("X-Custom", "header-val")
        .build();

    TestResponseModel res = client.put(id, Map.of("name", "Replaced"), ctx);
    assertThat(res.getId()).isEqualTo(id);
    assertThat(res.getName()).isEqualTo("Replaced");
  }

  @Test
  void put_withCustomReturnType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicPutCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    TestResponseClass res = client.put(id,
        Map.of("name", "Replaced", "description", "rd"), TestResponseClass.class);
    assertThat(res.getId()).isEqualTo(id);
    assertThat(res.getName()).isEqualTo("Replaced");
    assertThat(res.getDescription()).isEqualTo("rd");
  }

  @Test
  void putWithToken_useCallerSuppliedToken() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicPutCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    TestResponseModel res = client.putWithToken("custom-token", id, Map.of("name", "Replaced"));
    assertThat(res.getId()).isEqualTo(id);
    assertThat(res.getName()).isEqualTo("Replaced");
  }

  @Test
  void put_nullBody_throwsNullPointerException() {
    assertThatThrownBy(() -> client.put("put-id", null, TestResponseModel.class))
        .isInstanceOf(NullPointerException.class);
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

  // --- SUB-RESOURCE ---

  private static final String SUB_TEMPLATE = "/{orderId}/action/{action}/item";

  private String nestedPath() {
    return path + "/o1/action/cancel/item";
  }

  @Test
  void sub_get_hitsNestedPath() {
    String nested = nestedPath();
    MockServerUtils.setUpAllDynamicCallbacks(nested);
    String id = MockServerUtils.addDataToCache(nested, MockServerUtils.getTestData());

    TestResponseModel res =
        client.sub(SUB_TEMPLATE, "o1", "cancel").get(id, TestResponseModel.class);

    assertThat(res.getId()).isEqualTo(id);
    HttpRequest[] recorded = MockServerUtils.getMockServer()
        .retrieveRecordedRequests(request().withMethod("GET"));
    assertThat(recorded).hasSize(1);
    assertThat(recorded[0].getPath().getValue()).isEqualTo(nested + "/" + id);
  }

  @Test
  void sub_list_hitsNestedCollection() {
    String nested = nestedPath();
    MockServerUtils.setUpAllDynamicCallbacks(nested);
    MockServerUtils.seedData(nested, 3);

    List<TestResponseModel> items =
        client.sub(SUB_TEMPLATE, "o1", "cancel").list(TestResponseModel.class);

    assertThat(items).hasSize(3);
    HttpRequest[] recorded = MockServerUtils.getMockServer()
        .retrieveRecordedRequests(request().withMethod("GET"));
    assertThat(recorded).hasSize(1);
    assertThat(recorded[0].getPath().getValue()).isEqualTo(nested);
  }

  @Test
  void sub_post_createsUnderNestedCollection() {
    String nested = nestedPath();
    MockServerUtils.setUpAllDynamicCallbacks(nested);

    TestResponseModel res = client.sub(SUB_TEMPLATE, "o1", "cancel")
        .post(testDataMap(), TestResponseModel.class);

    assertThat(res.getId()).isNotBlank();
    HttpRequest[] recorded = MockServerUtils.getMockServer()
        .retrieveRecordedRequests(request().withMethod("POST"));
    assertThat(recorded).hasSize(1);
    assertThat(recorded[0].getPath().getValue()).isEqualTo(nested);
  }

  @Test
  void sub_chained_producesDeepPath() {
    String nested = nestedPath();
    MockServerUtils.setUpAllDynamicCallbacks(nested);
    String id = MockServerUtils.addDataToCache(nested, MockServerUtils.getTestData());

    TestResponseModel res = client
        .sub("/{orderId}/action", "o1")
        .sub("/{action}/item", "cancel")
        .get(id, TestResponseModel.class);

    assertThat(res.getId()).isEqualTo(id);
    HttpRequest[] recorded = MockServerUtils.getMockServer()
        .retrieveRecordedRequests(request().withMethod("GET"));
    assertThat(recorded).hasSize(1);
    assertThat(recorded[0].getPath().getValue()).isEqualTo(nested + "/" + id);
  }

  @Test
  void sub_inheritsFixedHeadersAndScopes() {
    String nested = nestedPath();
    MockServerUtils.setUpAllDynamicCallbacks(nested);
    String id = MockServerUtils.addDataToCache(nested, MockServerUtils.getTestData());

    ServerConfig serverConfig = new ServerConfig();
    serverConfig.setBaseUrl(MockServerUtils.getBaseUrl());
    serverConfig.setContextPath("");
    serverConfig.setClientRef("default");
    serverConfig.setEndpoints(Map.of());

    EndpointConfig endpointConfig = new EndpointConfig();
    endpointConfig.setPath(path);
    Map<Scope, String> scopes = new EnumMap<>(Scope.class);
    scopes.put(Scope.GET, "GET_SCOPE");
    endpointConfig.setScopes(scopes);
    endpointConfig.setFixedHeaders(Map.of("X-Fixed", "fixed-value"));

    ClientProperties clientProperties = new ClientProperties();
    clientProperties.setNumRetries(0);
    clientProperties.setRetryWaitDuration(Duration.ofMillis(100));
    clientProperties.setBearerAuth(new BearerAuthConfig());

    List<String> requestedScopes = new ArrayList<>();
    SyncTokenService recordingTokenService = new MockSyncTokenService() {
      @Override
      public String getToken(String additionalScopes) {
        requestedScopes.add(additionalScopes);
        return super.getToken(additionalScopes);
      }
    };

    TmfClientImpl<Map<String, Object>, Map<String, Object>, TestResponseModel> parent =
        new TmfClientImpl<>(endpointConfig, serverConfig,
            RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()).build(),
            recordingTokenService, clientProperties, TestResponseModel.class);

    TestResponseModel res =
        parent.sub(SUB_TEMPLATE, "o1", "cancel").get(id, TestResponseModel.class);

    assertThat(res.getId()).isEqualTo(id);
    assertThat(requestedScopes).containsExactly("GET_SCOPE");
    HttpRequest[] recorded = MockServerUtils.getMockServer()
        .retrieveRecordedRequests(request().withMethod("GET"));
    assertThat(recorded).hasSize(1);
    assertThat(recorded[0].getFirstHeader("X-Fixed")).isEqualTo("fixed-value");
  }

  @Test
  void sub_withPageable_appendsPaginationAfterNestedPath() {
    String nested = nestedPath();
    MockServerUtils.setUpAllDynamicCallbacks(nested);
    MockServerUtils.seedData(nested, 3);

    client.sub(SUB_TEMPLATE, "o1", "cancel")
        .list(TmfOffsetRequest.of(0, 10), TestResponseModel.class);

    HttpRequest[] recorded = MockServerUtils.getMockServer()
        .retrieveRecordedRequests(request().withMethod("GET"));
    assertThat(recorded).hasSize(1);
    assertThat(recorded[0].getPath().getValue()).isEqualTo(nested);
    assertThat(recorded[0].getFirstQueryStringParameter("offset")).isEqualTo("0");
    assertThat(recorded[0].getFirstQueryStringParameter("limit")).isEqualTo("10");
  }

  @Test
  void sub_arityMismatch_throwsBeforeAnyRequest() {
    assertThatThrownBy(() -> client.sub(SUB_TEMPLATE, "o1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(SUB_TEMPLATE);

    assertThat(MockServerUtils.getMockServer().retrieveRecordedRequests(request())).isEmpty();
  }
}
