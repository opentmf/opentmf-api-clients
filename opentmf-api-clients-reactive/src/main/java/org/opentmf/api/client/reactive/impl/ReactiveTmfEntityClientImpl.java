package org.opentmf.api.client.reactive.impl;

import java.util.List;
import java.util.Objects;
import org.opentmf.api.client.common.model.Scope;
import org.opentmf.api.client.common.model.TmfOffsetRequest;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.opentmf.api.client.reactive.api.ReactiveTmfEntityClient;
import org.opentmf.commons.patch.JsonPatch;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * Entity view over a {@link ReactiveTmfClientImpl}: a stateless adapter delegating every verb to
 * the owner's entity-returning cores. One cached instance per client, created by the owner —
 * see {@link ReactiveTmfClientImpl#entity()}.
 *
 * @param <C> create DTO type
 * @param <U> update/patch DTO type
 * @param <R> response type
 */
class ReactiveTmfEntityClientImpl<C, U, R> implements ReactiveTmfEntityClient<C, U, R> {

  private final ReactiveTmfClientImpl<C, U, R> owner;

  ReactiveTmfEntityClientImpl(ReactiveTmfClientImpl<C, U, R> owner) {
    this.owner = Objects.requireNonNull(owner);
  }

  // --- GET ---

  @Override public Mono<ResponseEntity<R>> get(String id) {
    return owner.getToken(Scope.GET).flatMap(t -> getWithToken(t, id));
  }

  @Override public Mono<ResponseEntity<R>> get(String id, TmfRequestContext ctx) {
    return owner.getToken(Scope.GET).flatMap(t -> getWithToken(t, id, ctx));
  }

  @Override public <T> Mono<ResponseEntity<T>> get(String id, Class<T> type) {
    return owner.getToken(Scope.GET).flatMap(t -> getWithToken(t, id, type));
  }

  @Override public <T> Mono<ResponseEntity<T>> get(
      String id, TmfRequestContext ctx, Class<T> type) {
    return owner.getToken(Scope.GET).flatMap(t -> getWithToken(t, id, ctx, type));
  }

  @Override public Mono<ResponseEntity<R>> getWithToken(String token, String id) {
    return getWithToken(token, id, null, owner.responseType());
  }

  @Override public Mono<ResponseEntity<R>> getWithToken(
      String token, String id, TmfRequestContext ctx) {
    return getWithToken(token, id, ctx, owner.responseType());
  }

  @Override public <T> Mono<ResponseEntity<T>> getWithToken(
      String token, String id, Class<T> type) {
    return getWithToken(token, id, null, type);
  }

  @Override public <T> Mono<ResponseEntity<T>> getWithToken(
      String token, String id, TmfRequestContext ctx, Class<T> type) {
    return owner.getEntityWithToken(token, id, ctx, type);
  }

  // --- LIST single page ---

  @Override public Mono<ResponseEntity<List<R>>> list() {
    return owner.getToken(Scope.LIST).flatMap(this::listWithToken);
  }

  @Override public <T> Mono<ResponseEntity<List<T>>> list(Class<T> type) {
    return owner.getToken(Scope.LIST).flatMap(t -> listWithToken(t, type));
  }

  @Override public Mono<ResponseEntity<List<R>>> list(Pageable pageable) {
    return owner.getToken(Scope.LIST).flatMap(t -> listWithToken(t, pageable));
  }

  @Override public <T> Mono<ResponseEntity<List<T>>> list(Pageable pageable, Class<T> type) {
    return owner.getToken(Scope.LIST).flatMap(t -> listWithToken(t, pageable, type));
  }

  @Override public Mono<ResponseEntity<List<R>>> listWithToken(String token) {
    return listWithToken(token, owner.responseType());
  }

  @Override public <T> Mono<ResponseEntity<List<T>>> listWithToken(String token, Class<T> type) {
    return listWithToken(token, TmfOffsetRequest.of(0), type);
  }

  @Override public Mono<ResponseEntity<List<R>>> listWithToken(String token, Pageable pageable) {
    return listWithToken(token, pageable, owner.responseType());
  }

  @Override public <T> Mono<ResponseEntity<List<T>>> listWithToken(
      String token, Pageable pageable, Class<T> type) {
    return owner.listEntityWithToken(token, pageable, type);
  }

  // --- POST ---

  @Override public Mono<ResponseEntity<R>> post(C obj) {
    return owner.getToken(Scope.POST).flatMap(t -> postWithToken(t, obj));
  }

  @Override public Mono<ResponseEntity<R>> post(C obj, TmfRequestContext ctx) {
    return owner.getToken(Scope.POST).flatMap(t -> postWithToken(t, obj, ctx));
  }

  @Override public <T> Mono<ResponseEntity<T>> post(C obj, Class<T> type) {
    return owner.getToken(Scope.POST).flatMap(t -> postWithToken(t, obj, type));
  }

  @Override public <T> Mono<ResponseEntity<T>> post(C obj, TmfRequestContext ctx, Class<T> type) {
    return owner.getToken(Scope.POST).flatMap(t -> postWithToken(t, obj, ctx, type));
  }

  @Override public Mono<ResponseEntity<R>> postWithToken(String token, C obj) {
    return postWithToken(token, obj, null, owner.responseType());
  }

  @Override public Mono<ResponseEntity<R>> postWithToken(
      String token, C obj, TmfRequestContext ctx) {
    return postWithToken(token, obj, ctx, owner.responseType());
  }

  @Override public <T> Mono<ResponseEntity<T>> postWithToken(String token, C obj, Class<T> type) {
    return postWithToken(token, obj, null, type);
  }

  @Override public <T> Mono<ResponseEntity<T>> postWithToken(
      String token, C obj, TmfRequestContext ctx, Class<T> type) {
    return owner.postEntityWithToken(token, obj, ctx, type);
  }

  // --- MERGE PATCH ---

  @Override public Mono<ResponseEntity<R>> patch(String id, U obj) {
    return owner.getToken(Scope.PATCH).flatMap(t -> patchWithToken(t, id, obj));
  }

  @Override public Mono<ResponseEntity<R>> patch(String id, U obj, TmfRequestContext ctx) {
    return owner.getToken(Scope.PATCH).flatMap(t -> patchWithToken(t, id, obj, ctx));
  }

  @Override public <T> Mono<ResponseEntity<T>> patch(String id, U obj, Class<T> type) {
    return owner.getToken(Scope.PATCH).flatMap(t -> patchWithToken(t, id, obj, type));
  }

  @Override public <T> Mono<ResponseEntity<T>> patch(
      String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return owner.getToken(Scope.PATCH).flatMap(t -> patchWithToken(t, id, obj, ctx, type));
  }

  @Override public Mono<ResponseEntity<R>> patchWithToken(String token, String id, U obj) {
    return patchWithToken(token, id, obj, null, owner.responseType());
  }

  @Override public Mono<ResponseEntity<R>> patchWithToken(
      String token, String id, U obj, TmfRequestContext ctx) {
    return patchWithToken(token, id, obj, ctx, owner.responseType());
  }

  @Override public <T> Mono<ResponseEntity<T>> patchWithToken(
      String token, String id, U obj, Class<T> type) {
    return patchWithToken(token, id, obj, null, type);
  }

  @Override public <T> Mono<ResponseEntity<T>> patchWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return owner.patchEntityWithToken(token, id, obj, ctx, type);
  }

  // --- JSON PATCH ---

  @Override public Mono<ResponseEntity<R>> patch(String id, JsonPatch jsonPatch) {
    return owner.getToken(Scope.PATCH).flatMap(t -> patchWithToken(t, id, jsonPatch));
  }

  @Override public Mono<ResponseEntity<R>> patch(
      String id, JsonPatch jsonPatch, TmfRequestContext ctx) {
    return owner.getToken(Scope.PATCH).flatMap(t -> patchWithToken(t, id, jsonPatch, ctx));
  }

  @Override public <T> Mono<ResponseEntity<T>> patch(
      String id, JsonPatch jsonPatch, Class<T> type) {
    return owner.getToken(Scope.PATCH).flatMap(t -> patchWithToken(t, id, jsonPatch, type));
  }

  @Override public <T> Mono<ResponseEntity<T>> patch(
      String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return owner.getToken(Scope.PATCH).flatMap(t -> patchWithToken(t, id, jsonPatch, ctx, type));
  }

  @Override public Mono<ResponseEntity<R>> patchWithToken(
      String token, String id, JsonPatch jsonPatch) {
    return patchWithToken(token, id, jsonPatch, null, owner.responseType());
  }

  @Override public Mono<ResponseEntity<R>> patchWithToken(
      String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx) {
    return patchWithToken(token, id, jsonPatch, ctx, owner.responseType());
  }

  @Override public <T> Mono<ResponseEntity<T>> patchWithToken(
      String token, String id, JsonPatch jsonPatch, Class<T> type) {
    return patchWithToken(token, id, jsonPatch, null, type);
  }

  @Override public <T> Mono<ResponseEntity<T>> patchWithToken(
      String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return owner.patchEntityWithToken(token, id, jsonPatch, ctx, type);
  }

  // --- COLLECTION JSON PATCH ---

  @Override public Mono<ResponseEntity<List<R>>> patchCollection(JsonPatch jsonPatch) {
    return owner.getToken(Scope.PATCH).flatMap(t -> patchCollectionWithToken(t, jsonPatch));
  }

  @Override public Mono<ResponseEntity<List<R>>> patchCollection(
      JsonPatch jsonPatch, TmfRequestContext ctx) {
    return owner.getToken(Scope.PATCH).flatMap(t -> patchCollectionWithToken(t, jsonPatch, ctx));
  }

  @Override public <T> Mono<ResponseEntity<List<T>>> patchCollection(
      JsonPatch jsonPatch, Class<T> type) {
    return owner.getToken(Scope.PATCH).flatMap(t -> patchCollectionWithToken(t, jsonPatch, type));
  }

  @Override public <T> Mono<ResponseEntity<List<T>>> patchCollection(
      JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return owner.getToken(Scope.PATCH)
        .flatMap(t -> patchCollectionWithToken(t, jsonPatch, ctx, type));
  }

  @Override public Mono<ResponseEntity<List<R>>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch) {
    return patchCollectionWithToken(token, jsonPatch, null, owner.responseType());
  }

  @Override public Mono<ResponseEntity<List<R>>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx) {
    return patchCollectionWithToken(token, jsonPatch, ctx, owner.responseType());
  }

  @Override public <T> Mono<ResponseEntity<List<T>>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, Class<T> type) {
    return patchCollectionWithToken(token, jsonPatch, null, type);
  }

  @Override public <T> Mono<ResponseEntity<List<T>>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return owner.patchCollectionEntityWithToken(token, jsonPatch, ctx, type);
  }

  // --- PUT ---

  @Override public Mono<ResponseEntity<R>> put(String id, U obj) {
    return owner.getToken(Scope.PUT).flatMap(t -> putWithToken(t, id, obj));
  }

  @Override public Mono<ResponseEntity<R>> put(String id, U obj, TmfRequestContext ctx) {
    return owner.getToken(Scope.PUT).flatMap(t -> putWithToken(t, id, obj, ctx));
  }

  @Override public <T> Mono<ResponseEntity<T>> put(String id, U obj, Class<T> type) {
    return owner.getToken(Scope.PUT).flatMap(t -> putWithToken(t, id, obj, type));
  }

  @Override public <T> Mono<ResponseEntity<T>> put(
      String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return owner.getToken(Scope.PUT).flatMap(t -> putWithToken(t, id, obj, ctx, type));
  }

  @Override public Mono<ResponseEntity<R>> putWithToken(String token, String id, U obj) {
    return putWithToken(token, id, obj, null, owner.responseType());
  }

  @Override public Mono<ResponseEntity<R>> putWithToken(
      String token, String id, U obj, TmfRequestContext ctx) {
    return putWithToken(token, id, obj, ctx, owner.responseType());
  }

  @Override public <T> Mono<ResponseEntity<T>> putWithToken(
      String token, String id, U obj, Class<T> type) {
    return putWithToken(token, id, obj, null, type);
  }

  @Override public <T> Mono<ResponseEntity<T>> putWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return owner.putEntityWithToken(token, id, obj, ctx, type);
  }

  // --- DELETE ---

  @Override public Mono<ResponseEntity<Void>> delete(String id) {
    return owner.getToken(Scope.DELETE).flatMap(t -> deleteWithToken(t, id));
  }

  @Override public Mono<ResponseEntity<Void>> delete(String id, TmfRequestContext ctx) {
    return owner.getToken(Scope.DELETE).flatMap(t -> deleteWithToken(t, id, ctx));
  }

  @Override public <T> Mono<ResponseEntity<T>> delete(String id, Class<T> type) {
    return owner.getToken(Scope.DELETE).flatMap(t -> deleteWithToken(t, id, type));
  }

  @Override public <T> Mono<ResponseEntity<T>> delete(
      String id, Class<T> type, TmfRequestContext ctx) {
    return owner.getToken(Scope.DELETE).flatMap(t -> deleteWithToken(t, id, type, ctx));
  }

  @Override public Mono<ResponseEntity<Void>> deleteWithToken(String token, String id) {
    return deleteWithToken(token, id, (TmfRequestContext) null);
  }

  @Override public Mono<ResponseEntity<Void>> deleteWithToken(
      String token, String id, TmfRequestContext ctx) {
    return owner.deleteEntityWithToken(token, id, ctx);
  }

  @Override public <T> Mono<ResponseEntity<T>> deleteWithToken(
      String token, String id, Class<T> type) {
    return deleteWithToken(token, id, type, null);
  }

  @Override public <T> Mono<ResponseEntity<T>> deleteWithToken(
      String token, String id, Class<T> type, TmfRequestContext ctx) {
    return owner.deleteEntityWithToken(token, id, type, ctx);
  }
}
