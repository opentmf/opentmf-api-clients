package org.opentmf.api.client.reactive.api;

import java.util.List;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.opentmf.commons.patch.JsonPatch;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

/**
 * Entity view of {@link ReactiveTmfClient}: the same verbs, returning
 * {@code Mono<ResponseEntity<...>>} so callers can read response headers and the status code on
 * the success path. Reached via {@link ReactiveTmfClient#entity()}; never instantiated directly.
 *
 * <p>List bodies are fully materialized {@code List<T>}, never {@code Flux<T>}: a body
 * {@code Flux} inside a {@code ResponseEntity} is a single-subscription live connection stream
 * wearing the clothes of a plain container — abandoning it (the natural use of this view) leaves
 * the connection undrained, and reading it twice fails. Every entity handed to a caller is
 * re-readable and its connection released.
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
public interface ReactiveTmfEntityClient<C, U, R> {

  // --- GET (auto-token) ---

  Mono<ResponseEntity<R>> get(String id);

  Mono<ResponseEntity<R>> get(String id, TmfRequestContext ctx);

  <T> Mono<ResponseEntity<T>> get(String id, Class<T> type);

  <T> Mono<ResponseEntity<T>> get(String id, TmfRequestContext ctx, Class<T> type);

  // --- GET (with token) ---

  Mono<ResponseEntity<R>> getWithToken(String token, String id);

  Mono<ResponseEntity<R>> getWithToken(String token, String id, TmfRequestContext ctx);

  <T> Mono<ResponseEntity<T>> getWithToken(String token, String id, Class<T> type);

  <T> Mono<ResponseEntity<T>> getWithToken(
      String token, String id, TmfRequestContext ctx, Class<T> type);

  // --- LIST single page (auto-token) ---

  Mono<ResponseEntity<List<R>>> list();

  <T> Mono<ResponseEntity<List<T>>> list(Class<T> type);

  Mono<ResponseEntity<List<R>>> list(Pageable pageable);

  <T> Mono<ResponseEntity<List<T>>> list(Pageable pageable, Class<T> type);

  // --- LIST single page (with token) ---

  Mono<ResponseEntity<List<R>>> listWithToken(String token);

  <T> Mono<ResponseEntity<List<T>>> listWithToken(String token, Class<T> type);

  Mono<ResponseEntity<List<R>>> listWithToken(String token, Pageable pageable);

  <T> Mono<ResponseEntity<List<T>>> listWithToken(String token, Pageable pageable, Class<T> type);

  // --- POST (auto-token) ---

  Mono<ResponseEntity<R>> post(C obj);

  Mono<ResponseEntity<R>> post(C obj, TmfRequestContext ctx);

  <T> Mono<ResponseEntity<T>> post(C obj, Class<T> type);

  <T> Mono<ResponseEntity<T>> post(C obj, TmfRequestContext ctx, Class<T> type);

  // --- POST (with token) ---

  Mono<ResponseEntity<R>> postWithToken(String token, C obj);

  Mono<ResponseEntity<R>> postWithToken(String token, C obj, TmfRequestContext ctx);

  <T> Mono<ResponseEntity<T>> postWithToken(String token, C obj, Class<T> type);

  <T> Mono<ResponseEntity<T>> postWithToken(
      String token, C obj, TmfRequestContext ctx, Class<T> type);

  // --- MERGE PATCH (auto-token) ---

  Mono<ResponseEntity<R>> patch(String id, U obj);

  Mono<ResponseEntity<R>> patch(String id, U obj, TmfRequestContext ctx);

  <T> Mono<ResponseEntity<T>> patch(String id, U obj, Class<T> type);

  <T> Mono<ResponseEntity<T>> patch(String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- MERGE PATCH (with token) ---

  Mono<ResponseEntity<R>> patchWithToken(String token, String id, U obj);

  Mono<ResponseEntity<R>> patchWithToken(String token, String id, U obj, TmfRequestContext ctx);

  <T> Mono<ResponseEntity<T>> patchWithToken(String token, String id, U obj, Class<T> type);

  <T> Mono<ResponseEntity<T>> patchWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- JSON PATCH (auto-token) ---

  Mono<ResponseEntity<R>> patch(String id, JsonPatch jsonPatch);

  Mono<ResponseEntity<R>> patch(String id, JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> Mono<ResponseEntity<T>> patch(String id, JsonPatch jsonPatch, Class<T> type);

  <T> Mono<ResponseEntity<T>> patch(
      String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- JSON PATCH (with token) ---

  Mono<ResponseEntity<R>> patchWithToken(String token, String id, JsonPatch jsonPatch);

  Mono<ResponseEntity<R>> patchWithToken(
      String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> Mono<ResponseEntity<T>> patchWithToken(
      String token, String id, JsonPatch jsonPatch, Class<T> type);

  <T> Mono<ResponseEntity<T>> patchWithToken(
      String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- COLLECTION JSON PATCH (auto-token) ---

  Mono<ResponseEntity<List<R>>> patchCollection(JsonPatch jsonPatch);

  Mono<ResponseEntity<List<R>>> patchCollection(JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> Mono<ResponseEntity<List<T>>> patchCollection(JsonPatch jsonPatch, Class<T> type);

  <T> Mono<ResponseEntity<List<T>>> patchCollection(
      JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- COLLECTION JSON PATCH (with token) ---

  Mono<ResponseEntity<List<R>>> patchCollectionWithToken(String token, JsonPatch jsonPatch);

  Mono<ResponseEntity<List<R>>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> Mono<ResponseEntity<List<T>>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, Class<T> type);

  <T> Mono<ResponseEntity<List<T>>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- PUT (auto-token) ---

  Mono<ResponseEntity<R>> put(String id, U obj);

  Mono<ResponseEntity<R>> put(String id, U obj, TmfRequestContext ctx);

  <T> Mono<ResponseEntity<T>> put(String id, U obj, Class<T> type);

  <T> Mono<ResponseEntity<T>> put(String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- PUT (with token) ---

  Mono<ResponseEntity<R>> putWithToken(String token, String id, U obj);

  Mono<ResponseEntity<R>> putWithToken(String token, String id, U obj, TmfRequestContext ctx);

  <T> Mono<ResponseEntity<T>> putWithToken(String token, String id, U obj, Class<T> type);

  <T> Mono<ResponseEntity<T>> putWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- DELETE (auto-token) ---

  Mono<ResponseEntity<Void>> delete(String id);

  Mono<ResponseEntity<Void>> delete(String id, TmfRequestContext ctx);

  <T> Mono<ResponseEntity<T>> delete(String id, Class<T> type);

  <T> Mono<ResponseEntity<T>> delete(String id, Class<T> type, TmfRequestContext ctx);

  // --- DELETE (with token) ---

  Mono<ResponseEntity<Void>> deleteWithToken(String token, String id);

  Mono<ResponseEntity<Void>> deleteWithToken(String token, String id, TmfRequestContext ctx);

  <T> Mono<ResponseEntity<T>> deleteWithToken(String token, String id, Class<T> type);

  <T> Mono<ResponseEntity<T>> deleteWithToken(
      String token, String id, Class<T> type, TmfRequestContext ctx);
}
