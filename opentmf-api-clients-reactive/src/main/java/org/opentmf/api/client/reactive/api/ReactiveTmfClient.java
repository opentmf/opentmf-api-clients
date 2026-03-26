package org.opentmf.api.client.reactive.api;

import org.opentmf.commons.patch.JsonPatch;
import org.opentmf.api.client.common.model.TmfPage;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive TMF CRUD client.
 *
 * <ul>
 *   <li><b>C</b> – create DTO type</li>
 *   <li><b>U</b> – update/patch DTO type</li>
 *   <li><b>R</b> – response type</li>
 * </ul>
 *
 * <p>Each operation group has two flavors:
 * <ul>
 *   <li><em>auto-token</em> – the implementation retrieves a token from {@code TokenService}</li>
 *   <li><em>withToken</em> – the caller supplies an already-obtained token</li>
 * </ul>
 */
public interface ReactiveTmfClient<C, U, R> {

  // --- GET (auto-token) ---

  Mono<R> get(String id);

  Mono<R> get(String id, TmfRequestContext ctx);

  <T> Mono<T> get(String id, Class<T> type);

  <T> Mono<T> get(String id, TmfRequestContext ctx, Class<T> type);

  // --- GET (with token) ---

  Mono<R> getWithToken(String token, String id);

  Mono<R> getWithToken(String token, String id, TmfRequestContext ctx);

  <T> Mono<T> getWithToken(String token, String id, Class<T> type);

  <T> Mono<T> getWithToken(String token, String id, TmfRequestContext ctx, Class<T> type);

  // --- LIST single page (auto-token) ---

  Flux<R> list();

  <T> Flux<T> list(Class<T> type);

  Flux<R> list(Pageable pageable);

  <T> Flux<T> list(Pageable pageable, Class<T> type);

  // --- LIST single page (with token) ---

  Flux<R> listWithToken(String token);

  <T> Flux<T> listWithToken(String token, Class<T> type);

  Flux<R> listWithToken(String token, Pageable pageable);

  <T> Flux<T> listWithToken(String token, Pageable pageable, Class<T> type);

  // --- LIST ALL pages (auto-token) ---

  Flux<R> listAll();

  <T> Flux<T> listAll(Class<T> type);

  Flux<R> listAll(Pageable pageable);

  <T> Flux<T> listAll(Pageable pageable, Class<T> type);

  // --- LIST ALL pages (with token) ---

  Flux<R> listAllWithToken(String token);

  <T> Flux<T> listAllWithToken(String token, Class<T> type);

  Flux<R> listAllWithToken(String token, Pageable pageable);

  <T> Flux<T> listAllWithToken(String token, Pageable pageable, Class<T> type);

  // --- LIST PAGED with metadata (auto-token) ---

  Mono<TmfPage<Flux<R>>> listPaged();

  <T> Mono<TmfPage<Flux<T>>> listPaged(Class<T> type);

  Mono<TmfPage<Flux<R>>> listPaged(Pageable pageable);

  <T> Mono<TmfPage<Flux<T>>> listPaged(Pageable pageable, Class<T> type);

  // --- LIST PAGED with metadata (with token) ---

  Mono<TmfPage<Flux<R>>> listPagedWithToken(String token);

  <T> Mono<TmfPage<Flux<T>>> listPagedWithToken(String token, Class<T> type);

  Mono<TmfPage<Flux<R>>> listPagedWithToken(String token, Pageable pageable);

  <T> Mono<TmfPage<Flux<T>>> listPagedWithToken(String token, Pageable pageable, Class<T> type);

  // --- POST (auto-token) ---

  Mono<R> post(C obj);

  Mono<R> post(C obj, TmfRequestContext ctx);

  <T> Mono<T> post(C obj, Class<T> type);

  <T> Mono<T> post(C obj, TmfRequestContext ctx, Class<T> type);

  // --- POST (with token) ---

  Mono<R> postWithToken(String token, C obj);

  Mono<R> postWithToken(String token, C obj, TmfRequestContext ctx);

  <T> Mono<T> postWithToken(String token, C obj, Class<T> type);

  <T> Mono<T> postWithToken(String token, C obj, TmfRequestContext ctx, Class<T> type);

  // --- MERGE PATCH (auto-token) ---

  Mono<R> patch(String id, U obj);

  Mono<R> patch(String id, U obj, TmfRequestContext ctx);

  <T> Mono<T> patch(String id, U obj, Class<T> type);

  <T> Mono<T> patch(String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- MERGE PATCH (with token) ---

  Mono<R> patchWithToken(String token, String id, U obj);

  Mono<R> patchWithToken(String token, String id, U obj, TmfRequestContext ctx);

  <T> Mono<T> patchWithToken(String token, String id, U obj, Class<T> type);

  <T> Mono<T> patchWithToken(String token, String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- JSON PATCH (auto-token) ---

  Mono<R> patch(String id, JsonPatch jsonPatch);

  Mono<R> patch(String id, JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> Mono<T> patch(String id, JsonPatch jsonPatch, Class<T> type);

  <T> Mono<T> patch(String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- JSON PATCH (with token) ---

  Mono<R> patchWithToken(String token, String id, JsonPatch jsonPatch);

  Mono<R> patchWithToken(String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> Mono<T> patchWithToken(String token, String id, JsonPatch jsonPatch, Class<T> type);

  <T> Mono<T> patchWithToken(String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- DELETE (auto-token) ---

  Mono<Void> delete(String id);

  Mono<Void> delete(String id, TmfRequestContext ctx);

  <T> Mono<T> delete(String id, Class<T> type);

  <T> Mono<T> delete(String id, Class<T> type, TmfRequestContext ctx);

  // --- DELETE (with token) ---

  Mono<Void> deleteWithToken(String token, String id);

  Mono<Void> deleteWithToken(String token, String id, TmfRequestContext ctx);

  <T> Mono<T> deleteWithToken(String token, String id, Class<T> type);

  <T> Mono<T> deleteWithToken(String token, String id, Class<T> type, TmfRequestContext ctx);
}
