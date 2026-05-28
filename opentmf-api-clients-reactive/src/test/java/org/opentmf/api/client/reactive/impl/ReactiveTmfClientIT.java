package org.opentmf.api.client.reactive.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
import org.opentmf.api.client.reactive.helper.MockServerUtils;
import org.opentmf.api.client.reactive.helper.MockTokenService;
import org.opentmf.api.client.reactive.helper.TestResponseClass;
import org.opentmf.api.client.reactive.helper.TestResponseModel;
import org.opentmf.client.common.exception.OpenTmfClientResponseException;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.commons.patch.JsonPatch;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

@SuppressWarnings("unchecked")
class ReactiveTmfClientIT {

  private ReactiveTmfClientImpl<Map<String, Object>, Map<String, Object>, TestResponseModel> client;
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

    client = new ReactiveTmfClientImpl<>(
        endpointConfig, serverConfig,
        WebClient.create(), new MockTokenService(),
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

    StepVerifier.create(client.post(data))
        .assertNext(res -> {
          assertThat(res).isNotNull();
          assertThat(res.getId()).isNotBlank();
          assertThat(res.getHref()).isNotBlank();
          assertThat(res.getName()).isEqualTo(data.get("name"));
        })
        .verifyComplete();
  }

  @Test
  void post_andGetVerifiesRoundTrip() {
    MockServerUtils.setUpAllDynamicCallbacks(path);
    Map<String, Object> data = testDataMap();

    String id = client.post(data).map(TestResponseModel::getId).block();
    assertThat(id).isNotBlank();

    StepVerifier.create(client.get(id))
        .assertNext(res -> {
          assertThat(res.getId()).isEqualTo(id);
          assertThat(res.getHref()).isNotBlank();
          assertThat(res.getName()).isEqualTo(data.get("name"));
        })
        .verifyComplete();
  }

  @Test
  void postWithToken_createsResource() {
    MockServerUtils.setUpDynamicPostCallback(path);

    StepVerifier.create(client.postWithToken("custom-token", testDataMap()))
        .assertNext(res -> assertThat(res.getId()).isNotBlank())
        .verifyComplete();
  }

  @Test
  void post_withRequestContext() {
    MockServerUtils.setUpDynamicPostCallback(path);
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withHeaderValues("X-Custom", "value1")
        .build();

    StepVerifier.create(client.post(testDataMap(), ctx))
        .assertNext(res -> assertThat(res.getId()).isNotBlank())
        .verifyComplete();
  }

  @Test
  void post_withAlternateResponseType() {
    MockServerUtils.setUpDynamicPostCallback(path);

    StepVerifier.create(client.post(testDataMap(), String.class))
        .assertNext(res -> {
          assertThat(res).isNotBlank();
          assertThat(res).contains("id");
        })
        .verifyComplete();
  }

  // --- GET ---

  @Test
  void get_returnsResource() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    StepVerifier.create(client.get(id))
        .assertNext(res -> {
          assertThat(res).isNotNull();
          assertThat(res.getId()).isEqualTo(id);
        })
        .verifyComplete();
  }

  @Test
  void getWithToken_returnsResource() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    StepVerifier.create(client.getWithToken("custom-token", id))
        .assertNext(res -> assertThat(res.getId()).isEqualTo(id))
        .verifyComplete();
  }

  @Test
  void get_withAlternateType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    StepVerifier.create(client.get(id, TestResponseClass.class))
        .assertNext(res -> assertThat(res.getId()).isEqualTo(id))
        .verifyComplete();
  }

  @Test
  void get_withRequestContextAndFields() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withFields("id", "name")
        .build();

    StepVerifier.create(client.get(id, ctx))
        .assertNext(res -> assertThat(res.getId()).isEqualTo(id))
        .verifyComplete();
  }

  // --- LIST ---

  @Test
  void list_returnsResources() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 5);

    StepVerifier.create(client.list().collectList())
        .assertNext(list -> assertThat(list).hasSizeGreaterThanOrEqualTo(5))
        .verifyComplete();
  }

  @Test
  void list_withPageable() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 15);

    TmfOffsetRequest pageable = TmfOffsetRequest.of(0, 5);
    StepVerifier.create(client.list(pageable).collectList())
        .assertNext(list -> assertThat(list).hasSize(5))
        .verifyComplete();
  }

  @Test
  void list_withAlternateType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 3);

    StepVerifier.create(client.list(TestResponseClass.class).collectList())
        .assertNext(list -> {
          assertThat(list).hasSizeGreaterThanOrEqualTo(3);
          assertThat(list.get(0).getId()).isNotBlank();
        })
        .verifyComplete();
  }

  // --- LIST ALL ---

  @Test
  void listAll_fetchesAllPages() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 25);

    StepVerifier.create(client.listAll().collectList())
        .assertNext(list -> assertThat(list).hasSize(25))
        .verifyComplete();
  }

  @Test
  void listAll_withSort() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 15);

    TmfOffsetRequest pageable = TmfOffsetRequest.of(0, 5,
        Sort.Direction.DESC, "orderNumber");

    StepVerifier.create(client.listAll(pageable).collectList())
        .assertNext(list -> assertThat(list).hasSize(15))
        .verifyComplete();
  }

  // --- LIST PAGED ---

  @Test
  void listPaged_returnsMetadata() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 20);

    TmfOffsetRequest pageable = TmfOffsetRequest.of(0, 10);

    StepVerifier.create(client.listPaged(pageable))
        .assertNext(page -> {
          assertThat(page.getTotalElements()).isEqualTo(20);
          assertThat(page.getTotalPages()).isEqualTo(2);
          assertThat(page.getSize()).isEqualTo(10);
          assertThat(page.getNumber()).isZero();
          assertThat(page.hasNext()).isTrue();
          assertThat(page.isLast()).isFalse();
        })
        .verifyComplete();
  }

  @Test
  void listPaged_lastPage() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 5);

    TmfOffsetRequest pageable = TmfOffsetRequest.of(0, 10);

    StepVerifier.create(client.listPaged(pageable))
        .assertNext(page -> {
          assertThat(page.getTotalElements()).isEqualTo(5);
          assertThat(page.isLast()).isTrue();
          assertThat(page.hasNext()).isFalse();
        })
        .verifyComplete();
  }

  @Test
  void listPaged_contentIsAccessible() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 3);

    StepVerifier.create(
            client.listPaged(TmfOffsetRequest.of(0, 10))
                .flatMapMany(TmfPage::getContent)
                .collectList())
        .assertNext(list -> assertThat(list).hasSize(3))
        .verifyComplete();
  }

  // --- MERGE PATCH ---

  @Test
  void patch_mergePatch_updatesResource() {
    MockServerUtils.setUpAllDynamicCallbacks(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    Map<String, Object> update = Map.of("description", "Updated description");

    StepVerifier.create(client.patch(id, update))
        .assertNext(res -> {
          assertThat(res).isNotNull();
          assertThat(res.getId()).isEqualTo(id);
          assertThat(res.getDescription()).isEqualTo("Updated description");
        })
        .verifyComplete();
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

    StepVerifier.create(client.patch(id, update, ctx))
        .assertNext(res -> assertThat(res.getId()).isEqualTo(id))
        .verifyComplete();
  }

  // --- JSON PATCH ---

  @Test
  void patch_jsonPatch_returnsResponse() {
    MockServerUtils.setUpAllDynamicCallbacks(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    JsonPatch jsonPatch = JsonPatch.builder()
        .replace("/description", "JSON-patched description")
        .build();

    StepVerifier.create(client.patch(id, jsonPatch))
        .assertNext(res -> {
          assertThat(res).isNotNull();
          assertThat(res.getId()).isEqualTo(id);
          assertThat(res.getDescription()).isEqualTo("JSON-patched description");
        })
        .verifyComplete();
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

    StepVerifier.create(client.patchCollection(jp))
        .assertNext(list -> {
          assertThat(list).hasSize(3);
          assertThat(list).extracting(TestResponseModel::getName)
              .containsExactly("A", "B", "C");
          assertThat(list).extracting(TestResponseModel::getId).doesNotContainNull();
          assertThat(list).extracting(TestResponseModel::getHref).doesNotContainNull();
        })
        .verifyComplete();
  }

  @Test
  void patchCollection_withCustomReturnType() {
    MockServerUtils.setUpDynamicJsonPatchCollectionCallback(path);

    JsonPatch jp = JsonPatch.builder()
        .add("/", Map.of("name", "X", "description", "d"))
        .build();

    StepVerifier.create(client.patchCollection(jp, TestResponseClass.class))
        .assertNext(list -> {
          assertThat(list).hasSize(1);
          assertThat(list.get(0).getName()).isEqualTo("X");
          assertThat(list.get(0).getDescription()).isEqualTo("d");
          assertThat(list.get(0).getId()).isNotBlank();
        })
        .verifyComplete();
  }

  @Test
  void patchCollectionWithToken_useCallerSuppliedToken() {
    MockServerUtils.setUpDynamicJsonPatchCollectionCallback(path);

    JsonPatch jp = JsonPatch.builder().add("/", Map.of("name", "T")).build();

    StepVerifier.create(client.patchCollectionWithToken("custom-token", jp))
        .assertNext(list -> {
          assertThat(list).hasSize(1);
          assertThat(list.get(0).getName()).isEqualTo("T");
        })
        .verifyComplete();
  }

  @Test
  void patchCollection_emptyPatch_returns400() {
    MockServerUtils.setUpDynamicJsonPatchCollectionCallback(path);

    JsonPatch jp = JsonPatch.builder().build();

    StepVerifier.create(client.patchCollection(jp))
        .expectErrorMatches(e -> e instanceof OpenTmfClientResponseException)
        .verify();
  }

  @Test
  void patchCollection_5xx_propagatesException() {
    MockServerUtils.setUpCollectionJsonPatchCallback(path, "", HttpStatus.INTERNAL_SERVER_ERROR);

    JsonPatch jp = JsonPatch.builder().add("/", Map.of("name", "A")).build();

    StepVerifier.create(client.patchCollection(jp))
        .expectErrorMatches(e -> e instanceof OpenTmfClientResponseException)
        .verify();
  }

  @Test
  void patchCollection_nullPatch_throwsNpe() {
    StepVerifier.create(client.patchCollection((JsonPatch) null))
        .expectError(NullPointerException.class)
        .verify();
  }

  // --- PUT ---

  @Test
  void put_replacesResource() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicPutCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    Map<String, Object> replacement = Map.of(
        "name", "Replaced", "description", "Replaced description");

    StepVerifier.create(client.put(id, replacement))
        .assertNext(res -> {
          assertThat(res).isNotNull();
          assertThat(res.getId()).isEqualTo(id);
          assertThat(res.getName()).isEqualTo("Replaced");
          assertThat(res.getDescription()).isEqualTo("Replaced description");
        })
        .verifyComplete();
  }

  @Test
  void put_withRequestContext() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicPutCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withHeaderValues("X-Custom", "header-val")
        .build();

    StepVerifier.create(client.put(id, Map.of("name", "Replaced"), ctx))
        .assertNext(res -> {
          assertThat(res.getId()).isEqualTo(id);
          assertThat(res.getName()).isEqualTo("Replaced");
        })
        .verifyComplete();
  }

  @Test
  void put_withCustomReturnType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicPutCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    StepVerifier.create(client.put(id,
            Map.of("name", "Replaced", "description", "rd"), TestResponseClass.class))
        .assertNext(res -> {
          assertThat(res.getId()).isEqualTo(id);
          assertThat(res.getName()).isEqualTo("Replaced");
          assertThat(res.getDescription()).isEqualTo("rd");
        })
        .verifyComplete();
  }

  @Test
  void putWithToken_useCallerSuppliedToken() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicPutCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    StepVerifier.create(client.putWithToken("custom-token", id, Map.of("name", "Replaced")))
        .assertNext(res -> {
          assertThat(res.getId()).isEqualTo(id);
          assertThat(res.getName()).isEqualTo("Replaced");
        })
        .verifyComplete();
  }

  @Test
  void put_nullBody_throwsNullPointerException() {
    StepVerifier.create(client.put("put-id", null, TestResponseModel.class))
        .expectError(NullPointerException.class)
        .verify();
  }

  // --- DELETE ---

  @Test
  void delete_removesResource() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicDeleteCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    StepVerifier.create(client.delete(id))
        .verifyComplete();
  }

  @Test
  void deleteWithToken_removesResource() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicDeleteCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    StepVerifier.create(client.deleteWithToken("custom-token", id))
        .verifyComplete();
  }

  // --- ADDITIONAL OVERLOADS ---

  @Test
  void get_withCtxAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());
    TmfRequestContext ctx = TmfRequestContext.builder().withFields("id").build();

    StepVerifier.create(client.get(id, ctx, TestResponseClass.class))
        .assertNext(res -> assertThat(res.getId()).isEqualTo(id))
        .verifyComplete();
  }

  @Test
  void getWithToken_withCtxAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());
    TmfRequestContext ctx = TmfRequestContext.builder().withFields("id").build();

    StepVerifier.create(client.getWithToken("custom-token", id, ctx))
        .assertNext(res -> assertThat(res.getId()).isEqualTo(id))
        .verifyComplete();
  }

  @Test
  void post_withCtxAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);

    StepVerifier.create(client.post(testDataMap(), TmfRequestContext.builder().build(),
            TestResponseClass.class))
        .assertNext(res -> assertThat(res.getId()).isNotBlank())
        .verifyComplete();
  }

  @Test
  void postWithToken_withCtx() {
    MockServerUtils.setUpDynamicPostCallback(path);

    StepVerifier.create(client.postWithToken("custom-token", testDataMap(),
            TmfRequestContext.builder().build()))
        .assertNext(res -> assertThat(res.getId()).isNotBlank())
        .verifyComplete();
  }

  @Test
  void list_withPageableAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 3);

    StepVerifier.create(client.list(TmfOffsetRequest.of(0, 10), TestResponseClass.class)
            .collectList())
        .assertNext(list -> assertThat(list).hasSizeGreaterThanOrEqualTo(3))
        .verifyComplete();
  }

  @Test
  void listAll_withType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 5);

    StepVerifier.create(client.listAll(TestResponseClass.class).collectList())
        .assertNext(list -> assertThat(list).hasSize(5))
        .verifyComplete();
  }

  @Test
  void listAll_withPageableAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 10);

    StepVerifier.create(client.listAll(TmfOffsetRequest.of(0, 5),
            TestResponseClass.class).collectList())
        .assertNext(list -> assertThat(list).hasSize(10))
        .verifyComplete();
  }

  @Test
  void listAll_withClientFilter_characteristicArray() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedDataWithCharacteristics(path);

    TmfOffsetRequest req = TmfOffsetRequest.of(0, 100).withClientFilter(
        "$[?(@.characteristic[?(@.name=='IMEI' && @.value=='123456789012345')] empty false)]");
    StepVerifier.create(client.listAll(req).collectList())
        .assertNext(list -> {
          assertThat(list).hasSize(2)
              .extracting(TestResponseModel::getName)
              .containsExactlyInAnyOrder("CharItem-0", "CharItem-3");
        })
        .verifyComplete();
  }

  @Test
  void listAll_withServerFilter_characteristicArray() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedDataWithCharacteristics(path);

    TmfOffsetRequest req = TmfOffsetRequest.of(0, 100).withServerFilter(
        "$[?(@.characteristic[?(@.name=='IMEI' && @.value=='123456789012345')] empty false)]");
    StepVerifier.create(client.listAll(req).collectList())
        .assertNext(list -> {
          assertThat(list).hasSize(2)
              .extracting(TestResponseModel::getName)
              .containsExactlyInAnyOrder("CharItem-0", "CharItem-3");
        })
        .verifyComplete();
  }

  @Test
  void listAll_withServerFilter_simpleFieldEquality() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedDataWithCharacteristics(path);

    TmfOffsetRequest req = TmfOffsetRequest.of(0, 100)
        .withServerFilter("$[?(@.name=='CharItem-2')]");
    StepVerifier.create(client.listAll(req).collectList())
        .assertNext(list -> {
          assertThat(list).hasSize(1)
              .extracting(TestResponseModel::getName)
              .containsExactly("CharItem-2");
        })
        .verifyComplete();
  }

  @Test
  void listPaged_noArgs() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 3);

    StepVerifier.create(client.listPaged()
            .flatMapMany(TmfPage::getContent).collectList())
        .assertNext(list -> assertThat(list).hasSizeGreaterThanOrEqualTo(3))
        .verifyComplete();
  }

  @Test
  void listPaged_withType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 3);

    StepVerifier.create(client.listPaged(TestResponseClass.class)
            .flatMapMany(TmfPage::getContent).collectList())
        .assertNext(list -> assertThat(list).isNotEmpty())
        .verifyComplete();
  }

  @Test
  void patchWithToken_mergePatch_withCtxAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicMergePatchCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());
    TmfRequestContext ctx = TmfRequestContext.builder().build();

    StepVerifier.create(client.patch(id, Map.of("name", "Updated"), ctx,
            TestResponseClass.class))
        .assertNext(res -> assertThat(res.getId()).isEqualTo(id))
        .verifyComplete();
  }

  @Test
  void patchWithToken_jsonPatch_withCtxAndType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicJsonPatchCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());
    TmfRequestContext ctx = TmfRequestContext.builder().build();

    JsonPatch jp = JsonPatch.builder().replace("/description", "jp-updated").build();
    StepVerifier.create(client.patch(id, jp, ctx, TestResponseClass.class))
        .assertNext(res -> assertThat(res.getId()).isEqualTo(id))
        .verifyComplete();
  }

  @Test
  void delete_withRequestContext() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicDeleteCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    StepVerifier.create(client.delete(id, TmfRequestContext.builder().build()))
        .verifyComplete();
  }

  @Test
  void delete_withType() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicDeleteCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    StepVerifier.create(client.delete(id, String.class))
        .verifyComplete();
  }

  @Test
  void delete_withTypeAndCtx() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicDeleteCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    StepVerifier.create(client.delete(id, String.class, TmfRequestContext.builder().build()))
        .verifyComplete();
  }

  // --- BRANCH COVERAGE: no scopes, plain Pageable ---

  @Test
  void get_withNoScopes_usesDefaultToken() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetCallback(path);
    String id = MockServerUtils.addDataToCache(path, MockServerUtils.getTestData());

    ServerConfig sc = new ServerConfig();
    sc.setBaseUrl(MockServerUtils.getBaseUrl());
    sc.setContextPath("");
    sc.setClientRef("default");
    sc.setEndpoints(Map.of());

    EndpointConfig ec = new EndpointConfig();
    ec.setPath(path);

    ClientProperties cp = new ClientProperties();
    cp.setNumRetries(0);
    cp.setRetryWaitDuration(Duration.ofMillis(100));

    var noScopeClient = new ReactiveTmfClientImpl<>(
        ec, sc, WebClient.create(), new MockTokenService(), cp, TestResponseModel.class);

    StepVerifier.create(noScopeClient.get(id))
        .assertNext(res -> assertThat(res.getId()).isEqualTo(id))
        .verifyComplete();
  }

  @Test
  void list_withPlainPageRequest() {
    MockServerUtils.setUpDynamicPostCallback(path);
    MockServerUtils.setUpDynamicGetListCallback(path);
    MockServerUtils.seedData(path, 3);

    StepVerifier.create(client.list(PageRequest.of(0, 10)).collectList())
        .assertNext(list -> assertThat(list).hasSizeGreaterThanOrEqualTo(3))
        .verifyComplete();
  }

  // --- FULL CRUD ---

  @Test
  void fullCrudLifecycle() {
    MockServerUtils.setUpAllDynamicCallbacks(path);
    Map<String, Object> data = testDataMap();

    // POST
    TestResponseModel created = client.post(data).block();
    assertThat(created).isNotNull();
    assertThat(created.getId()).isNotBlank();
    String id = created.getId();

    // GET
    TestResponseModel fetched = client.get(id).block();
    assertThat(fetched).isNotNull();
    assertThat(fetched.getId()).isEqualTo(id);

    // MERGE PATCH
    Map<String, Object> patch = Map.of("description", "patched");
    TestResponseModel patched = client.patch(id, patch).block();
    assertThat(patched).isNotNull();
    assertThat(patched.getDescription()).isEqualTo("patched");

    // DELETE
    client.delete(id).block();
  }
}
