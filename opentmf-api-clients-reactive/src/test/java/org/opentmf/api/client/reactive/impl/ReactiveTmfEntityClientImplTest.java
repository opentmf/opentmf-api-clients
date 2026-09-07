package org.opentmf.api.client.reactive.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.opentmf.api.client.reactive.api.ReactiveTmfEntityClient;
import org.opentmf.api.client.reactive.helper.MockTokenService;
import org.opentmf.api.client.reactive.helper.TestResponseModel;
import org.opentmf.client.common.model.BearerAuthConfig;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.commons.patch.JsonPatch;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Pins the reactive entity view's 64-overload delegation ladder against a spied owner with
 * stubbed cores. Wire behaviour is covered by the entity ITs; these catch argument-ordering and
 * default-filling mistakes at unit cost.
 */
class ReactiveTmfEntityClientImplTest {

  private static final TestResponseModel MODEL = new TestResponseModel();
  private static final ResponseEntity<TestResponseModel> ENTITY =
      new ResponseEntity<>(MODEL, HttpStatus.OK);
  private static final ResponseEntity<List<TestResponseModel>> LIST_ENTITY =
      new ResponseEntity<>(List.of(MODEL), HttpStatus.OK);
  private static final ResponseEntity<Void> VOID_ENTITY = new ResponseEntity<>(HttpStatus.NO_CONTENT);
  private static final TmfRequestContext CTX = TmfRequestContext.builder().build();
  private static final JsonPatch JP = JsonPatch.builder().replace("/x", "y").build();

  private ReactiveTmfClientImpl<Map<String, Object>, Map<String, Object>, TestResponseModel> owner;
  private ReactiveTmfEntityClient<Map<String, Object>, Map<String, Object>, TestResponseModel> entity;

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

    owner = spy(new ReactiveTmfClientImpl<>(ec, sc, WebClient.create(), new MockTokenService(),
        props, TestResponseModel.class));
    doReturn(Mono.just("tok")).when(owner).getToken(any());
    // NOT owner.entity(): the cached view was built in the original object's field
    // initializer and points at the unspied instance - stubs would never fire.
    entity = new ReactiveTmfEntityClientImpl<>(owner);
  }

  @Test
  void entity_isCachedPerClient() {
    assertThat(owner.entity()).isSameAs(owner.entity());
  }

  @Test
  void getLadder_delegatesAllOverloads() {
    doReturn(Mono.just(ENTITY)).when(owner).getEntityWithToken(any(), any(), any(), any());

    assertThat(entity.get("id").block()).isSameAs(ENTITY);
    assertThat(entity.get("id", CTX).block()).isSameAs(ENTITY);
    assertThat(entity.get("id", TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.get("id", CTX, TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.getWithToken("t", "id").block()).isSameAs(ENTITY);
    assertThat(entity.getWithToken("t", "id", CTX).block()).isSameAs(ENTITY);
    assertThat(entity.getWithToken("t", "id", TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.getWithToken("t", "id", CTX, TestResponseModel.class).block()).isSameAs(ENTITY);
  }

  @Test
  void listLadder_delegatesAllOverloads() {
    doReturn(Mono.just(LIST_ENTITY)).when(owner).listEntityWithToken(any(), any(), any());

    assertThat(entity.list().block()).isSameAs(LIST_ENTITY);
    assertThat(entity.list(TestResponseModel.class).block()).isSameAs(LIST_ENTITY);
    assertThat(entity.list(PageRequest.of(0, 5)).block()).isSameAs(LIST_ENTITY);
    assertThat(entity.list(PageRequest.of(0, 5), TestResponseModel.class).block()).isSameAs(LIST_ENTITY);
    assertThat(entity.listWithToken("t").block()).isSameAs(LIST_ENTITY);
    assertThat(entity.listWithToken("t", TestResponseModel.class).block()).isSameAs(LIST_ENTITY);
    assertThat(entity.listWithToken("t", PageRequest.of(0, 5)).block()).isSameAs(LIST_ENTITY);
    assertThat(entity.listWithToken("t", PageRequest.of(0, 5), TestResponseModel.class).block())
        .isSameAs(LIST_ENTITY);
  }

  @Test
  void postLadder_delegatesAllOverloads() {
    doReturn(Mono.just(ENTITY)).when(owner).postEntityWithToken(any(), any(), any(), any());
    Map<String, Object> body = Map.of("k", "v");

    assertThat(entity.post(body).block()).isSameAs(ENTITY);
    assertThat(entity.post(body, CTX).block()).isSameAs(ENTITY);
    assertThat(entity.post(body, TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.post(body, CTX, TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.postWithToken("t", body).block()).isSameAs(ENTITY);
    assertThat(entity.postWithToken("t", body, CTX).block()).isSameAs(ENTITY);
    assertThat(entity.postWithToken("t", body, TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.postWithToken("t", body, CTX, TestResponseModel.class).block()).isSameAs(ENTITY);
  }

  @Test
  @SuppressWarnings("unchecked")
  void mergePatchLadder_delegatesAllOverloads() {
    doReturn(Mono.just(ENTITY)).when(owner)
        .patchEntityWithToken(any(), any(), any(Map.class), any(), any());
    Map<String, Object> body = Map.of("k", "v");

    assertThat(entity.patch("id", body).block()).isSameAs(ENTITY);
    assertThat(entity.patch("id", body, CTX).block()).isSameAs(ENTITY);
    assertThat(entity.patch("id", body, TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.patch("id", body, CTX, TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", body).block()).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", body, CTX).block()).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", body, TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", body, CTX, TestResponseModel.class).block())
        .isSameAs(ENTITY);
  }

  @Test
  void jsonPatchLadder_delegatesAllOverloads() {
    doReturn(Mono.just(ENTITY)).when(owner)
        .patchEntityWithToken(any(), any(), any(JsonPatch.class), any(), any());

    assertThat(entity.patch("id", JP).block()).isSameAs(ENTITY);
    assertThat(entity.patch("id", JP, CTX).block()).isSameAs(ENTITY);
    assertThat(entity.patch("id", JP, TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.patch("id", JP, CTX, TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", JP).block()).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", JP, CTX).block()).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", JP, TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.patchWithToken("t", "id", JP, CTX, TestResponseModel.class).block())
        .isSameAs(ENTITY);
  }

  @Test
  void patchCollectionLadder_delegatesAllOverloads() {
    doReturn(Mono.just(LIST_ENTITY)).when(owner)
        .patchCollectionEntityWithToken(any(), any(), any(), any());

    assertThat(entity.patchCollection(JP).block()).isSameAs(LIST_ENTITY);
    assertThat(entity.patchCollection(JP, CTX).block()).isSameAs(LIST_ENTITY);
    assertThat(entity.patchCollection(JP, TestResponseModel.class).block()).isSameAs(LIST_ENTITY);
    assertThat(entity.patchCollection(JP, CTX, TestResponseModel.class).block()).isSameAs(LIST_ENTITY);
    assertThat(entity.patchCollectionWithToken("t", JP).block()).isSameAs(LIST_ENTITY);
    assertThat(entity.patchCollectionWithToken("t", JP, CTX).block()).isSameAs(LIST_ENTITY);
    assertThat(entity.patchCollectionWithToken("t", JP, TestResponseModel.class).block())
        .isSameAs(LIST_ENTITY);
    assertThat(entity.patchCollectionWithToken("t", JP, CTX, TestResponseModel.class).block())
        .isSameAs(LIST_ENTITY);
  }

  @Test
  void putLadder_delegatesAllOverloads() {
    doReturn(Mono.just(ENTITY)).when(owner).putEntityWithToken(any(), any(), any(), any(), any());
    Map<String, Object> body = Map.of("k", "v");

    assertThat(entity.put("id", body).block()).isSameAs(ENTITY);
    assertThat(entity.put("id", body, CTX).block()).isSameAs(ENTITY);
    assertThat(entity.put("id", body, TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.put("id", body, CTX, TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.putWithToken("t", "id", body).block()).isSameAs(ENTITY);
    assertThat(entity.putWithToken("t", "id", body, CTX).block()).isSameAs(ENTITY);
    assertThat(entity.putWithToken("t", "id", body, TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.putWithToken("t", "id", body, CTX, TestResponseModel.class).block())
        .isSameAs(ENTITY);
  }

  @Test
  void deleteLadder_delegatesAllOverloads() {
    // untyped any() matches the null ctx the two-arg overloads pass through
    doReturn(Mono.just(VOID_ENTITY)).when(owner).deleteEntityWithToken(any(), any(), any());
    doReturn(Mono.just(ENTITY)).when(owner).deleteEntityWithToken(any(), any(), any(), any());

    assertThat(entity.delete("id").block()).isSameAs(VOID_ENTITY);
    assertThat(entity.delete("id", CTX).block()).isSameAs(VOID_ENTITY);
    assertThat(entity.delete("id", TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.delete("id", TestResponseModel.class, CTX).block()).isSameAs(ENTITY);
    assertThat(entity.deleteWithToken("t", "id").block()).isSameAs(VOID_ENTITY);
    assertThat(entity.deleteWithToken("t", "id", CTX).block()).isSameAs(VOID_ENTITY);
    assertThat(entity.deleteWithToken("t", "id", TestResponseModel.class).block()).isSameAs(ENTITY);
    assertThat(entity.deleteWithToken("t", "id", TestResponseModel.class, CTX).block())
        .isSameAs(ENTITY);
  }
}
