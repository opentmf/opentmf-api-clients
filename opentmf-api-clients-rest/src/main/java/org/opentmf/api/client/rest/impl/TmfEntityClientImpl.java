package org.opentmf.api.client.rest.impl;

import java.util.List;
import java.util.Objects;
import org.opentmf.api.client.common.api.TmfEntityClient;
import org.opentmf.api.client.common.model.Scope;
import org.opentmf.api.client.common.model.TmfOffsetRequest;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.opentmf.commons.patch.JsonPatch;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

/**
 * Entity view over a {@link TmfClientImpl}: a stateless adapter delegating every verb to the
 * owner's entity-returning cores. One cached instance per client, created by the owner —
 * see {@link TmfClientImpl#entity()}.
 *
 * @param <C> create DTO type
 * @param <U> update/patch DTO type
 * @param <R> response type
 */
class TmfEntityClientImpl<C, U, R> implements TmfEntityClient<C, U, R> {

  private final TmfClientImpl<C, U, R> owner;

  TmfEntityClientImpl(TmfClientImpl<C, U, R> owner) {
    this.owner = Objects.requireNonNull(owner);
  }

  // --- GET ---

  @Override public ResponseEntity<R> get(String id) {
    return getWithToken(owner.getToken(Scope.GET), id);
  }

  @Override public ResponseEntity<R> get(String id, TmfRequestContext ctx) {
    return getWithToken(owner.getToken(Scope.GET), id, ctx);
  }

  @Override public <T> ResponseEntity<T> get(String id, Class<T> type) {
    return getWithToken(owner.getToken(Scope.GET), id, type);
  }

  @Override public <T> ResponseEntity<T> get(String id, TmfRequestContext ctx, Class<T> type) {
    return getWithToken(owner.getToken(Scope.GET), id, ctx, type);
  }

  @Override public ResponseEntity<R> getWithToken(String token, String id) {
    return getWithToken(token, id, null, owner.responseType());
  }

  @Override public ResponseEntity<R> getWithToken(String token, String id, TmfRequestContext ctx) {
    return getWithToken(token, id, ctx, owner.responseType());
  }

  @Override public <T> ResponseEntity<T> getWithToken(String token, String id, Class<T> type) {
    return getWithToken(token, id, null, type);
  }

  @Override public <T> ResponseEntity<T> getWithToken(
      String token, String id, TmfRequestContext ctx, Class<T> type) {
    return owner.getEntityWithToken(token, id, ctx, type);
  }

  // --- LIST single page ---

  @Override public ResponseEntity<List<R>> list() {
    return listWithToken(owner.getToken(Scope.LIST));
  }

  @Override public <T> ResponseEntity<List<T>> list(Class<T> type) {
    return listWithToken(owner.getToken(Scope.LIST), type);
  }

  @Override public ResponseEntity<List<R>> list(Pageable pageable) {
    return listWithToken(owner.getToken(Scope.LIST), pageable);
  }

  @Override public <T> ResponseEntity<List<T>> list(Pageable pageable, Class<T> type) {
    return listWithToken(owner.getToken(Scope.LIST), pageable, type);
  }

  @Override public ResponseEntity<List<R>> listWithToken(String token) {
    return listWithToken(token, owner.responseType());
  }

  @Override public <T> ResponseEntity<List<T>> listWithToken(String token, Class<T> type) {
    return listWithToken(token, TmfOffsetRequest.of(0), type);
  }

  @Override public ResponseEntity<List<R>> listWithToken(String token, Pageable pageable) {
    return listWithToken(token, pageable, owner.responseType());
  }

  @Override public <T> ResponseEntity<List<T>> listWithToken(
      String token, Pageable pageable, Class<T> type) {
    return owner.listEntityWithToken(token, pageable, type);
  }

  // --- POST ---

  @Override public ResponseEntity<R> post(C obj) {
    return postWithToken(owner.getToken(Scope.POST), obj);
  }

  @Override public ResponseEntity<R> post(C obj, TmfRequestContext ctx) {
    return postWithToken(owner.getToken(Scope.POST), obj, ctx);
  }

  @Override public <T> ResponseEntity<T> post(C obj, Class<T> type) {
    return postWithToken(owner.getToken(Scope.POST), obj, type);
  }

  @Override public <T> ResponseEntity<T> post(C obj, TmfRequestContext ctx, Class<T> type) {
    return postWithToken(owner.getToken(Scope.POST), obj, ctx, type);
  }

  @Override public ResponseEntity<R> postWithToken(String token, C obj) {
    return postWithToken(token, obj, null, owner.responseType());
  }

  @Override public ResponseEntity<R> postWithToken(String token, C obj, TmfRequestContext ctx) {
    return postWithToken(token, obj, ctx, owner.responseType());
  }

  @Override public <T> ResponseEntity<T> postWithToken(String token, C obj, Class<T> type) {
    return postWithToken(token, obj, null, type);
  }

  @Override public <T> ResponseEntity<T> postWithToken(
      String token, C obj, TmfRequestContext ctx, Class<T> type) {
    return owner.postEntityWithToken(token, obj, ctx, type);
  }

  // --- MERGE PATCH ---

  @Override public ResponseEntity<R> patch(String id, U obj) {
    return patchWithToken(owner.getToken(Scope.PATCH), id, obj);
  }

  @Override public ResponseEntity<R> patch(String id, U obj, TmfRequestContext ctx) {
    return patchWithToken(owner.getToken(Scope.PATCH), id, obj, ctx);
  }

  @Override public <T> ResponseEntity<T> patch(String id, U obj, Class<T> type) {
    return patchWithToken(owner.getToken(Scope.PATCH), id, obj, type);
  }

  @Override public <T> ResponseEntity<T> patch(
      String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return patchWithToken(owner.getToken(Scope.PATCH), id, obj, ctx, type);
  }

  @Override public ResponseEntity<R> patchWithToken(String token, String id, U obj) {
    return patchWithToken(token, id, obj, null, owner.responseType());
  }

  @Override public ResponseEntity<R> patchWithToken(
      String token, String id, U obj, TmfRequestContext ctx) {
    return patchWithToken(token, id, obj, ctx, owner.responseType());
  }

  @Override public <T> ResponseEntity<T> patchWithToken(
      String token, String id, U obj, Class<T> type) {
    return patchWithToken(token, id, obj, null, type);
  }

  @Override public <T> ResponseEntity<T> patchWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return owner.patchEntityWithToken(token, id, obj, ctx, type);
  }

  // --- JSON PATCH ---

  @Override public ResponseEntity<R> patch(String id, JsonPatch jsonPatch) {
    return patchWithToken(owner.getToken(Scope.PATCH), id, jsonPatch);
  }

  @Override public ResponseEntity<R> patch(String id, JsonPatch jsonPatch, TmfRequestContext ctx) {
    return patchWithToken(owner.getToken(Scope.PATCH), id, jsonPatch, ctx);
  }

  @Override public <T> ResponseEntity<T> patch(String id, JsonPatch jsonPatch, Class<T> type) {
    return patchWithToken(owner.getToken(Scope.PATCH), id, jsonPatch, type);
  }

  @Override public <T> ResponseEntity<T> patch(
      String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return patchWithToken(owner.getToken(Scope.PATCH), id, jsonPatch, ctx, type);
  }

  @Override public ResponseEntity<R> patchWithToken(String token, String id, JsonPatch jsonPatch) {
    return patchWithToken(token, id, jsonPatch, null, owner.responseType());
  }

  @Override public ResponseEntity<R> patchWithToken(
      String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx) {
    return patchWithToken(token, id, jsonPatch, ctx, owner.responseType());
  }

  @Override public <T> ResponseEntity<T> patchWithToken(
      String token, String id, JsonPatch jsonPatch, Class<T> type) {
    return patchWithToken(token, id, jsonPatch, null, type);
  }

  @Override public <T> ResponseEntity<T> patchWithToken(
      String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return owner.patchEntityWithToken(token, id, jsonPatch, ctx, type);
  }

  // --- COLLECTION JSON PATCH ---

  @Override public ResponseEntity<List<R>> patchCollection(JsonPatch jsonPatch) {
    return patchCollectionWithToken(owner.getToken(Scope.PATCH), jsonPatch);
  }

  @Override public ResponseEntity<List<R>> patchCollection(
      JsonPatch jsonPatch, TmfRequestContext ctx) {
    return patchCollectionWithToken(owner.getToken(Scope.PATCH), jsonPatch, ctx);
  }

  @Override public <T> ResponseEntity<List<T>> patchCollection(JsonPatch jsonPatch, Class<T> type) {
    return patchCollectionWithToken(owner.getToken(Scope.PATCH), jsonPatch, type);
  }

  @Override public <T> ResponseEntity<List<T>> patchCollection(
      JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return patchCollectionWithToken(owner.getToken(Scope.PATCH), jsonPatch, ctx, type);
  }

  @Override public ResponseEntity<List<R>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch) {
    return patchCollectionWithToken(token, jsonPatch, null, owner.responseType());
  }

  @Override public ResponseEntity<List<R>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx) {
    return patchCollectionWithToken(token, jsonPatch, ctx, owner.responseType());
  }

  @Override public <T> ResponseEntity<List<T>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, Class<T> type) {
    return patchCollectionWithToken(token, jsonPatch, null, type);
  }

  @Override public <T> ResponseEntity<List<T>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return owner.patchCollectionEntityWithToken(token, jsonPatch, ctx, type);
  }

  // --- PUT ---

  @Override public ResponseEntity<R> put(String id, U obj) {
    return putWithToken(owner.getToken(Scope.PUT), id, obj);
  }

  @Override public ResponseEntity<R> put(String id, U obj, TmfRequestContext ctx) {
    return putWithToken(owner.getToken(Scope.PUT), id, obj, ctx);
  }

  @Override public <T> ResponseEntity<T> put(String id, U obj, Class<T> type) {
    return putWithToken(owner.getToken(Scope.PUT), id, obj, type);
  }

  @Override public <T> ResponseEntity<T> put(
      String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return putWithToken(owner.getToken(Scope.PUT), id, obj, ctx, type);
  }

  @Override public ResponseEntity<R> putWithToken(String token, String id, U obj) {
    return putWithToken(token, id, obj, null, owner.responseType());
  }

  @Override public ResponseEntity<R> putWithToken(
      String token, String id, U obj, TmfRequestContext ctx) {
    return putWithToken(token, id, obj, ctx, owner.responseType());
  }

  @Override public <T> ResponseEntity<T> putWithToken(
      String token, String id, U obj, Class<T> type) {
    return putWithToken(token, id, obj, null, type);
  }

  @Override public <T> ResponseEntity<T> putWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return owner.putEntityWithToken(token, id, obj, ctx, type);
  }

  // --- DELETE ---

  @Override public ResponseEntity<Void> delete(String id) {
    return deleteWithToken(owner.getToken(Scope.DELETE), id);
  }

  @Override public ResponseEntity<Void> delete(String id, TmfRequestContext ctx) {
    return deleteWithToken(owner.getToken(Scope.DELETE), id, ctx);
  }

  @Override public <T> ResponseEntity<T> delete(String id, Class<T> type) {
    return deleteWithToken(owner.getToken(Scope.DELETE), id, type);
  }

  @Override public <T> ResponseEntity<T> delete(String id, Class<T> type, TmfRequestContext ctx) {
    return deleteWithToken(owner.getToken(Scope.DELETE), id, type, ctx);
  }

  @Override public ResponseEntity<Void> deleteWithToken(String token, String id) {
    return deleteWithToken(token, id, (TmfRequestContext) null);
  }

  @Override public ResponseEntity<Void> deleteWithToken(
      String token, String id, TmfRequestContext ctx) {
    return owner.deleteEntityWithToken(token, id, ctx);
  }

  @Override public <T> ResponseEntity<T> deleteWithToken(String token, String id, Class<T> type) {
    return deleteWithToken(token, id, type, null);
  }

  @Override public <T> ResponseEntity<T> deleteWithToken(
      String token, String id, Class<T> type, TmfRequestContext ctx) {
    return owner.deleteEntityWithToken(token, id, type, ctx);
  }
}
