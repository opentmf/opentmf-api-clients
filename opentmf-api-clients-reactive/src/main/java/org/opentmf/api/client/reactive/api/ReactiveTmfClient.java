package org.opentmf.api.client.reactive.api;

import java.util.List;
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
 *
 * <p><b>{@code …WithToken} on a no-auth client:</b> when the referenced http-client configures
 * neither {@code bearer-auth} nor {@code basic-auth} ({@code AuthType.NONE}), the client has no
 * token scheme of its own, so a token passed to a {@code …WithToken} overload is sent
 * <b>verbatim</b> as the whole {@code Authorization} value — pass {@code "Bearer eyJ…"} if a
 * scheme is needed. With a blank token, a NONE client sends no {@code Authorization} header at
 * all, while a BEARER/BASIC client fails fast with an {@link IllegalArgumentException}.
 */
public interface ReactiveTmfClient<C, U, R> {

  // --- SUB-RESOURCE ---

  /**
   * Returns a derived generic client scoped to a nested path under this client's endpoint.
   * Every verb of the derived client operates against {@code endpointPath + template}, with each
   * {@code {name}} placeholder replaced by the corresponding element of {@code vars}, in order.
   *
   * <p>Example: {@code orderClient.sub("/{orderId}/action/{action}/item", orderId, action)
   * .get(itemId, Item.class)} issues {@code GET /order/o1/action/cancel/item/it7}.
   *
   * <p><b>The template must be a compile-time constant.</b> Never build it by concatenating
   * runtime data — every runtime value belongs in {@code vars}, where it is expanded as a URI
   * template variable and strictly percent-encoded. A value containing {@code /}, {@code :} or a
   * brace cannot inject or break the path. Only simple {@code {name}} placeholders are supported;
   * {@code {name:regex}} is rejected.
   *
   * <p>The derived client inherits this endpoint's OAuth scopes, fixed headers and transport.
   * Calling {@code sub} on an already-derived client appends to its path, so depth is unbounded.
   * This method only assembles a client and issues no request, hence the non-reactive signature.
   *
   * @param template the constant path template, e.g. {@code "/{orderId}/action/{action}/item"}
   * @param vars one value per {@code {name}} placeholder, in order of occurrence
   * @return a derived generic client scoped to the nested path
   * @throws IllegalArgumentException if the template is invalid or the number of placeholders
   *     does not match {@code vars.length} — validated eagerly, before any request is issued
   */
  GenericReactiveTmfClient sub(String template, Object... vars);

  // --- ENTITY VIEW ---

  /**
   * The entity view of this client: the same verbs returning
   * {@code Mono<ResponseEntity<...>>} so response headers and the status code are readable on the
   * success path. List bodies are fully materialized {@code List<T>} - never a body {@code Flux}
   * inside an entity. {@code listAll*} has no entity form (N pages means N header sets - no
   * single entity could carry them honestly), {@code listPaged*} has none ({@code TmfPage}
   * already is the header-derived view), and {@code sub} composes:
   * {@code client.sub(...).entity()}. Errors still throw
   * {@code OpenTmfClientResponseException}; failed-response headers come from the exception.
   * The returned instance is cached - calling this repeatedly is free.
   */
  ReactiveTmfEntityClient<C, U, R> entity();

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

  Mono<TmfPage<List<R>>> listPaged();

  <T> Mono<TmfPage<List<T>>> listPaged(Class<T> type);

  Mono<TmfPage<List<R>>> listPaged(Pageable pageable);

  <T> Mono<TmfPage<List<T>>> listPaged(Pageable pageable, Class<T> type);

  // --- LIST PAGED with metadata (with token) ---

  Mono<TmfPage<List<R>>> listPagedWithToken(String token);

  <T> Mono<TmfPage<List<T>>> listPagedWithToken(String token, Class<T> type);

  Mono<TmfPage<List<R>>> listPagedWithToken(String token, Pageable pageable);

  <T> Mono<TmfPage<List<T>>> listPagedWithToken(String token, Pageable pageable, Class<T> type);

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

  // --- COLLECTION JSON PATCH (auto-token) ---

  Mono<List<R>> patchCollection(JsonPatch jsonPatch);

  Mono<List<R>> patchCollection(JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> Mono<List<T>> patchCollection(JsonPatch jsonPatch, Class<T> type);

  <T> Mono<List<T>> patchCollection(JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- COLLECTION JSON PATCH (with token) ---

  Mono<List<R>> patchCollectionWithToken(String token, JsonPatch jsonPatch);

  Mono<List<R>> patchCollectionWithToken(String token, JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> Mono<List<T>> patchCollectionWithToken(String token, JsonPatch jsonPatch, Class<T> type);

  <T> Mono<List<T>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- PUT (auto-token) ---

  Mono<R> put(String id, U obj);

  Mono<R> put(String id, U obj, TmfRequestContext ctx);

  <T> Mono<T> put(String id, U obj, Class<T> type);

  <T> Mono<T> put(String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- PUT (with token) ---

  Mono<R> putWithToken(String token, String id, U obj);

  Mono<R> putWithToken(String token, String id, U obj, TmfRequestContext ctx);

  <T> Mono<T> putWithToken(String token, String id, U obj, Class<T> type);

  <T> Mono<T> putWithToken(String token, String id, U obj, TmfRequestContext ctx, Class<T> type);

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
