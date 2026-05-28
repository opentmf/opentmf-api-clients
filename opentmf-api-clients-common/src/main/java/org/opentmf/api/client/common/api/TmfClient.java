package org.opentmf.api.client.common.api;

import org.opentmf.commons.patch.JsonPatch;
import java.util.List;
import org.opentmf.api.client.common.model.TmfPage;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.springframework.data.domain.Pageable;

/**
 * Synchronous TMF CRUD client.
 *
 * <ul>
 *   <li><b>C</b> – create DTO type</li>
 *   <li><b>U</b> – update/patch DTO type</li>
 *   <li><b>R</b> – response type</li>
 * </ul>
 *
 * <p>Each operation group has two flavors:
 * <ul>
 *   <li><em>auto-token</em> – the implementation retrieves a token from {@code SyncTokenService}</li>
 *   <li><em>withToken</em> – the caller supplies an already-obtained token</li>
 * </ul>
 */
public interface TmfClient<C, U, R> {

  // --- GET (auto-token) ---

  R get(String id);

  R get(String id, TmfRequestContext ctx);

  <T> T get(String id, Class<T> type);

  <T> T get(String id, TmfRequestContext ctx, Class<T> type);

  // --- GET (with token) ---

  R getWithToken(String token, String id);

  R getWithToken(String token, String id, TmfRequestContext ctx);

  <T> T getWithToken(String token, String id, Class<T> type);

  <T> T getWithToken(String token, String id, TmfRequestContext ctx, Class<T> type);

  // --- LIST single page (auto-token) ---

  List<R> list();

  <T> List<T> list(Class<T> type);

  List<R> list(Pageable pageable);

  <T> List<T> list(Pageable pageable, Class<T> type);

  // --- LIST single page (with token) ---

  List<R> listWithToken(String token);

  <T> List<T> listWithToken(String token, Class<T> type);

  List<R> listWithToken(String token, Pageable pageable);

  <T> List<T> listWithToken(String token, Pageable pageable, Class<T> type);

  // --- LIST ALL pages (auto-token) ---

  List<R> listAll();

  <T> List<T> listAll(Class<T> type);

  List<R> listAll(Pageable pageable);

  <T> List<T> listAll(Pageable pageable, Class<T> type);

  // --- LIST ALL pages (with token) ---

  List<R> listAllWithToken(String token);

  <T> List<T> listAllWithToken(String token, Class<T> type);

  List<R> listAllWithToken(String token, Pageable pageable);

  <T> List<T> listAllWithToken(String token, Pageable pageable, Class<T> type);

  // --- LIST PAGED with metadata (auto-token) ---

  TmfPage<List<R>> listPaged();

  <T> TmfPage<List<T>> listPaged(Class<T> type);

  TmfPage<List<R>> listPaged(Pageable pageable);

  <T> TmfPage<List<T>> listPaged(Pageable pageable, Class<T> type);

  // --- LIST PAGED with metadata (with token) ---

  TmfPage<List<R>> listPagedWithToken(String token);

  <T> TmfPage<List<T>> listPagedWithToken(String token, Class<T> type);

  TmfPage<List<R>> listPagedWithToken(String token, Pageable pageable);

  <T> TmfPage<List<T>> listPagedWithToken(String token, Pageable pageable, Class<T> type);

  // --- POST (auto-token) ---

  R post(C obj);

  R post(C obj, TmfRequestContext ctx);

  <T> T post(C obj, Class<T> type);

  <T> T post(C obj, TmfRequestContext ctx, Class<T> type);

  // --- POST (with token) ---

  R postWithToken(String token, C obj);

  R postWithToken(String token, C obj, TmfRequestContext ctx);

  <T> T postWithToken(String token, C obj, Class<T> type);

  <T> T postWithToken(String token, C obj, TmfRequestContext ctx, Class<T> type);

  // --- MERGE PATCH (auto-token) ---

  R patch(String id, U obj);

  R patch(String id, U obj, TmfRequestContext ctx);

  <T> T patch(String id, U obj, Class<T> type);

  <T> T patch(String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- MERGE PATCH (with token) ---

  R patchWithToken(String token, String id, U obj);

  R patchWithToken(String token, String id, U obj, TmfRequestContext ctx);

  <T> T patchWithToken(String token, String id, U obj, Class<T> type);

  <T> T patchWithToken(String token, String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- JSON PATCH (auto-token) ---

  R patch(String id, JsonPatch jsonPatch);

  R patch(String id, JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> T patch(String id, JsonPatch jsonPatch, Class<T> type);

  <T> T patch(String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- JSON PATCH (with token) ---

  R patchWithToken(String token, String id, JsonPatch jsonPatch);

  R patchWithToken(String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> T patchWithToken(String token, String id, JsonPatch jsonPatch, Class<T> type);

  <T> T patchWithToken(String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- COLLECTION JSON PATCH (auto-token) ---

  List<R> patchCollection(JsonPatch jsonPatch);

  List<R> patchCollection(JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> List<T> patchCollection(JsonPatch jsonPatch, Class<T> type);

  <T> List<T> patchCollection(JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- COLLECTION JSON PATCH (with token) ---

  List<R> patchCollectionWithToken(String token, JsonPatch jsonPatch);

  List<R> patchCollectionWithToken(String token, JsonPatch jsonPatch, TmfRequestContext ctx);

  <T> List<T> patchCollectionWithToken(String token, JsonPatch jsonPatch, Class<T> type);

  <T> List<T> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type);

  // --- PUT (auto-token) ---

  R put(String id, U obj);

  R put(String id, U obj, TmfRequestContext ctx);

  <T> T put(String id, U obj, Class<T> type);

  <T> T put(String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- PUT (with token) ---

  R putWithToken(String token, String id, U obj);

  R putWithToken(String token, String id, U obj, TmfRequestContext ctx);

  <T> T putWithToken(String token, String id, U obj, Class<T> type);

  <T> T putWithToken(String token, String id, U obj, TmfRequestContext ctx, Class<T> type);

  // --- DELETE (auto-token) ---

  void delete(String id);

  void delete(String id, TmfRequestContext ctx);

  <T> T delete(String id, Class<T> type);

  <T> T delete(String id, Class<T> type, TmfRequestContext ctx);

  // --- DELETE (with token) ---

  void deleteWithToken(String token, String id);

  void deleteWithToken(String token, String id, TmfRequestContext ctx);

  <T> T deleteWithToken(String token, String id, Class<T> type);

  <T> T deleteWithToken(String token, String id, Class<T> type, TmfRequestContext ctx);
}
