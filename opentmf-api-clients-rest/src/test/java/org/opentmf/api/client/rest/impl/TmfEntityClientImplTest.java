package org.opentmf.api.client.rest.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentmf.api.client.common.api.TmfEntityClient;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.opentmf.api.client.rest.helper.MockSyncTokenService;
import org.opentmf.api.client.rest.helper.TestResponseModel;
import org.opentmf.client.common.model.BearerAuthConfig;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.commons.patch.JsonPatch;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Pins the entity view's 64-overload delegation ladder against a spied owner with stubbed cores.
 * Wire behaviour (status, headers, bodies) is covered by the entity ITs; what can go wrong in the
 * ladder is argument ordering and default filling, which these tests catch at unit cost.
 */
class TmfEntityClientImplTest {

  private static final TestResponseModel MODEL = new TestResponseModel();
  private static final ResponseEntity<TestResponseModel> ENTITY =
      new ResponseEntity<>(MODEL, HttpStatus.OK);
  private static final ResponseEntity<List<TestResponseModel>> LIST_ENTITY =
      new ResponseEntity<>(List.of(MODEL), HttpStatus.OK);
  private static final ResponseEntity<Void> VOID_ENTITY = new ResponseEntity<>(HttpStatus.NO_CONTENT);
  private static final TmfRequestContext CTX = TmfRequestContext.builder().build();
  private static final JsonPatch JP = JsonPatch.builder().replace("/x", "y").build();

  private TmfClientImpl<Map<String, Object>, Map<String, Object>, TestResponseModel> owner;
  private TmfEntityClient<Map<String, Object>, Map<String, Object>, TestResponseModel> entity;

  @BeforeEach
  void setUp() {
    ServerConfig sc = new ServerConfig();
    sc.setBaseUrl("http://unit-test");
    sc.setContextPath("");
    sc.setClientRef("default");
    sc.setEndpoints(Map.of());
    EndpointConfig ec = new EndpointConfig();
    ec.setPath("/unit");
    ClientProperties props = new ClientProperties();
    props.setNumRetries(0);
    props.setRetryWaitDuration(Duration.ofMillis(1));
    props.setBearerAuth(new BearerAuthConfig());

    owner = spy(new TmfClientImpl<>(ec, sc, RestClient.create(), new MockSyncTokenService(),
        props, TestResponseModel.class));
    doReturn("tok").when(owner).getToken(any());
    // NOT owner.entity(): the cached view was built in the original object's field
    // initializer and points at the unspied instance - stubs would never fire.
    entity = new TmfEntityClientImpl<>(owner);
  }

  @Test
  void entity_isCachedPerClient() {
    assertThat(owner.entity()).isSameAs(owner.entity());
  }

  @Test
  void getLadder_delegatesAllOverloads() {
    doReturn(ENTITY).when(owner).getEntityWithToken(any(), any(), any(), any());

    assertThat(entity.get("id")).isSameAs(ENTITY);
    assertThat(entity.get("id", CTX)).isSameAs(ENTITY);
    assertThat(entity.get("id", TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.get("id", CTX, TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.getWithToken("t", "id")).isSameAs(ENTITY);
    assertThat(entity.getWithToken("t", "id", CTX)).isSameAs(ENTITY);
    assertThat(entity.getWithToken("t", "id", TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.getWithToken("t", "id", CTX, TestResponseModel.class)).isSameAs(ENTITY);
  }

  @Test
  void listLadder_delegatesAllOverloads() {
    doReturn(LIST_ENTITY).when(owner).listEntityWithToken(any(), any(), any());

    assertThat(entity.list()).isSameAs(LIST_ENTITY);
    assertThat(entity.list(TestResponseModel.class)).isSameAs(LIST_ENTITY);
    assertThat(entity.list(org.springframework.data.domain.PageRequest.of(0, 5))).isSameAs(LIST_ENTITY);
    assertThat(entity.list(org.springframework.data.domain.PageRequest.of(0, 5),
        TestResponseModel.class)).isSameAs(LIST_ENTITY);
    assertThat(entity.listWithToken("t")).isSameAs(LIST_ENTITY);
    assertThat(entity.listWithToken("t", TestResponseModel.class)).isSameAs(LIST_ENTITY);
    assertThat(entity.listWithToken("t", org.springframework.data.domain.PageRequest.of(0, 5)))
        .isSameAs(LIST_ENTITY);
    assertThat(entity.listWithToken("t", org.springframework.data.domain.PageRequest.of(0, 5),
        TestResponseModel.class)).isSameAs(LIST_ENTITY);
  }

  @Test
  void postLadder_delegatesAllOverloads() {
    doReturn(ENTITY).when(owner).postEntityWithToken(any(), any(), any(), any());
    Map<String, Object> body = Map.of("k", "v");

    assertThat(entity.post(body)).isSameAs(ENTITY);
    assertThat(entity.post(body, CTX)).isSameAs(ENTITY);
    assertThat(entity.post(body, TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.post(body, CTX, TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.postWithToken("t", body)).isSameAs(ENTITY);
    assertThat(entity.postWithToken("t", body, CTX)).isSameAs(ENTITY);
    assertThat(entity.postWithToken("t", body, TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.postWithToken("t", body, CTX, TestResponseModel.class)).isSameAs(ENTITY);
  }

  @Test
  void mergePatchLadder_delegatesAllOverloads() {
    doReturn(ENTITY).when(owner)
        .patchEntityWithToken(any(), any(), any(Map.class), any(), any());
    Map<String, Object> body = Map.of("k", "v");

    assertThat(entity.patch("id", body)).isSameAs(ENTITY);
    assertThat(entity.patch("id", body, CTX)).isSameAs(ENTITY);
    assertThat(entity.patch("id", body, TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.patch("id", body, CTX, TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", body)).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", body, CTX)).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", body, TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", body, CTX, TestResponseModel.class)).isSameAs(ENTITY);
  }

  @Test
  void jsonPatchLadder_delegatesAllOverloads() {
    doReturn(ENTITY).when(owner)
        .patchEntityWithToken(any(), any(), any(JsonPatch.class), any(), any());

    assertThat(entity.patch("id", JP)).isSameAs(ENTITY);
    assertThat(entity.patch("id", JP, CTX)).isSameAs(ENTITY);
    assertThat(entity.patch("id", JP, TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.patch("id", JP, CTX, TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", JP)).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", JP, CTX)).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", JP, TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", JP, CTX, TestResponseModel.class)).isSameAs(ENTITY);
  }

  @Test
  void patchCollectionLadder_delegatesAllOverloads() {
    doReturn(LIST_ENTITY).when(owner)
        .patchCollectionEntityWithToken(any(), any(), any(), any());

    assertThat(entity.patchCollection(JP)).isSameAs(LIST_ENTITY);
    assertThat(entity.patchCollection(JP, CTX)).isSameAs(LIST_ENTITY);
    assertThat(entity.patchCollection(JP, TestResponseModel.class)).isSameAs(LIST_ENTITY);
    assertThat(entity.patchCollection(JP, CTX, TestResponseModel.class)).isSameAs(LIST_ENTITY);
    assertThat(entity.patchCollectionWithToken("t", JP)).isSameAs(LIST_ENTITY);
    assertThat(entity.patchCollectionWithToken("t", JP, CTX)).isSameAs(LIST_ENTITY);
    assertThat(entity.patchCollectionWithToken("t", JP, TestResponseModel.class)).isSameAs(LIST_ENTITY);
    assertThat(entity.patchCollectionWithToken("t", JP, CTX, TestResponseModel.class))
        .isSameAs(LIST_ENTITY);
  }

  @Test
  void putLadder_delegatesAllOverloads() {
    doReturn(ENTITY).when(owner).putEntityWithToken(any(), any(), any(), any(), any());
    Map<String, Object> body = Map.of("k", "v");

    assertThat(entity.put("id", body)).isSameAs(ENTITY);
    assertThat(entity.put("id", body, CTX)).isSameAs(ENTITY);
    assertThat(entity.put("id", body, TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.put("id", body, CTX, TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.putWithToken("t", "id", body)).isSameAs(ENTITY);
    assertThat(entity.putWithToken("t", "id", body, CTX)).isSameAs(ENTITY);
    assertThat(entity.putWithToken("t", "id", body, TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.putWithToken("t", "id", body, CTX, TestResponseModel.class)).isSameAs(ENTITY);
  }

  @Test
  @SuppressWarnings("unchecked")
  void deleteLadder_delegatesAllOverloads() {
    // untyped any() matches the null ctx the two-arg overloads pass through
    doReturn(VOID_ENTITY).when(owner).deleteEntityWithToken(any(), any(), any());
    doReturn(ENTITY).when(owner).deleteEntityWithToken(any(), any(), any(), any());

    assertThat(entity.delete("id")).isSameAs(VOID_ENTITY);
    assertThat(entity.delete("id", CTX)).isSameAs(VOID_ENTITY);
    assertThat(entity.delete("id", TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.delete("id", TestResponseModel.class, CTX)).isSameAs(ENTITY);
    assertThat(entity.deleteWithToken("t", "id")).isSameAs(VOID_ENTITY);
    assertThat(entity.deleteWithToken("t", "id", CTX)).isSameAs(VOID_ENTITY);
    assertThat(entity.deleteWithToken("t", "id", TestResponseModel.class)).isSameAs(ENTITY);
    assertThat(entity.deleteWithToken("t", "id", TestResponseModel.class, CTX)).isSameAs(ENTITY);
  }
}
