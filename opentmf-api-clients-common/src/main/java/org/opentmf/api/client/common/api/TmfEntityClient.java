package org.opentmf.api.client.common.api;

import java.util.List;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.opentmf.commons.patch.JsonPatch;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

/**
 * Entity view of {@link TmfClient}: the same verbs, returning {@link ResponseEntity} so callers
 * can read response headers and the status code on the success path — {@code Location} after a
 * create, an {@code ETag} for a conditional follow-up, a {@code Retry-After} on a 202, any custom
 * {@code X-*} header. Reached via {@link TmfClient#entity()}; never instantiated directly.
 *
 * <p><b>Errors still throw.</b> A non-2xx response raises
 * {@code OpenTmfClientResponseException} exactly as on the body view — it does not arrive as a
 * {@code ResponseEntity} with an error status. Headers of failed responses are available from
 * {@code OpenTmfClientResponseException#getHeaders()}.
 *
 * <p><b>Deliberately absent:</b> {@code listAll*} — it walks N pages, so there are N header sets
 * and no single {@code ResponseEntity} could carry them honestly; {@code listPaged*} — a
 * {@code TmfPage} already is the header-derived view of a list response; and {@code sub} —
 * {@code client.sub(...).entity()} composes.
 *
 * @param <C> create DTO type
 * @param <U> update/patch DTO type
 * @param <R> response type
 */
public interface TmfEntityClient<C, U, R> {

  // --- GET (auto-token) ---

  ResponseEntity<R> get(String id);

  ResponseEntity<R> get(String id, TmfRequestContext ctx);

  <T> ResponseEntity<T> get(String id, Class<T> type);

  <T> ResponseEntity<T> get(String id, TmfRequestContext ctx, Class<T> type);

  // --- GET (with token) ---

  ResponseEntity<R> getWithToken(String token, String id);

  ResponseEntity<R> getWithToken(String token, String id, TmfRequestContext ctx);

  <T> ResponseEntity<T> getWithToken(String token, String id, Class<T> type);

  <T> ResponseEntity<T> getWithToken(String token, String id, TmfRequestContext ctx, Class<T> type);

  // --- LIST single page (auto-token) ---

  ResponseEntity<List<R>> list();

  <T> ResponseEntity<List<T>> list(Class<T> type);

  ResponseEntity<List<R>> list(Pageable pageable);

  <T> ResponseEntity<List<T>> list(Pageable pageable, Class<T> type);

  // --- LIST single page (with token) ---

  ResponseEntity<List<R>> listWithToken(String token);

  <T> ResponseEntity<List<T>> listWithToken(String token, Class<T> type);

  ResponseEntity<List<R>> listWithToken(String token, Pageable pageable);

  <T> ResponseEntity<List<T>> listWithToken(String token, Pageable pageable, Class<T> type);

  // --- POST (auto-token) ---

  ResponseEntity<R> post(C obj);

  ResponseEntity<R> post(C obj, TmfRequestContext ctx);

  <T> ResponseEntity<T> post(C obj, Class<T> type);

  <T> ResponseEntity<T> post(C obj, TmfRequestContext ctx, Class<T> type);

  // --- POST (with token) ---

  ResponseEntity<R> postWithToken(String token, C obj);

  ResponseEntity<R> postWithToken(String token, C obj, TmfRequestContext ctx);

  <T> ResponseEntity<T> postWithToken(String token, C obj, Class<T> type);

  <T> ResponseEntity<T> postWithToken(String token, C obj, TmfRequestContext ctx, Class<T> type);

  // --- MERGE PATCH (auto-token) ---

  ResponseEntity<R> patch(String id, U obj);

  ResponseEntity<R> patch(String id, U obj, TmfRequestContext ctx);

  <T> ResponseEntity<T> patch(String id, U obj, Class<T> type);

  <T> ResponseEntity<T> patch(String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- MERGE PATCH (with token) ---

  ResponseEntity<R> patchWithToken(String token, String id, U obj);

  ResponseEntity<R> patchWithToken(String token, String id, U obj, TmfRequestContext ctx);

  <T> ResponseEntity<T> patchWithToken(String token, String id, U obj, Class<T> type);

  <T> ResponseEntity<T> patchWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- JSON PATCH (auto-token) ---

  ResponseEntity<R> patch(String id, JsonPatch jsonPatch);

  ResponseEntity<R> patch(String id, JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> ResponseEntity<T> patch(String id, JsonPatch jsonPatch, Class<T> type);

  <T> ResponseEntity<T> patch(String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- JSON PATCH (with token) ---

  ResponseEntity<R> patchWithToken(String token, String id, JsonPatch jsonPatch);

  ResponseEntity<R> patchWithToken(
      String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> ResponseEntity<T> patchWithToken(
      String token, String id, JsonPatch jsonPatch, Class<T> type);

  <T> ResponseEntity<T> patchWithToken(
      String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- COLLECTION JSON PATCH (auto-token) ---

  ResponseEntity<List<R>> patchCollection(JsonPatch jsonPatch);

  ResponseEntity<List<R>> patchCollection(JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> ResponseEntity<List<T>> patchCollection(JsonPatch jsonPatch, Class<T> type);

  <T> ResponseEntity<List<T>> patchCollection(
      JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- COLLECTION JSON PATCH (with token) ---

  ResponseEntity<List<R>> patchCollectionWithToken(String token, JsonPatch jsonPatch);

  ResponseEntity<List<R>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> ResponseEntity<List<T>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, Class<T> type);

  <T> ResponseEntity<List<T>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- PUT (auto-token) ---

  ResponseEntity<R> put(String id, U obj);

  ResponseEntity<R> put(String id, U obj, TmfRequestContext ctx);

  <T> ResponseEntity<T> put(String id, U obj, Class<T> type);

  <T> ResponseEntity<T> put(String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- PUT (with token) ---

  ResponseEntity<R> putWithToken(String token, String id, U obj);

  ResponseEntity<R> putWithToken(String token, String id, U obj, TmfRequestContext ctx);

  <T> ResponseEntity<T> putWithToken(String token, String id, U obj, Class<T> type);

  <T> ResponseEntity<T> putWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- DELETE (auto-token) ---

  ResponseEntity<Void> delete(String id);

  ResponseEntity<Void> delete(String id, TmfRequestContext ctx);

  <T> ResponseEntity<T> delete(String id, Class<T> type);

  <T> ResponseEntity<T> delete(String id, Class<T> type, TmfRequestContext ctx);

  // --- DELETE (with token) ---

  ResponseEntity<Void> deleteWithToken(String token, String id);

  ResponseEntity<Void> deleteWithToken(String token, String id, TmfRequestContext ctx);

  <T> ResponseEntity<T> deleteWithToken(String token, String id, Class<T> type);

  <T> ResponseEntity<T> deleteWithToken(
      String token, String id, Class<T> type, TmfRequestContext ctx);
}
