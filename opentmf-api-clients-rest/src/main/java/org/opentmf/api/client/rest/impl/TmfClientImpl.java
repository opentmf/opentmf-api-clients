package org.opentmf.api.client.rest.impl;

import static org.opentmf.api.client.common.util.HeaderUtil.headersConsumer;
import static org.opentmf.api.client.common.util.HeaderUtil.mergeFixedHeaders;
import static org.opentmf.api.client.common.util.HeaderUtil.prepareAndValidate;
import static org.opentmf.api.client.common.util.HeaderUtil.prepareAndValidateJsonPatch;
import static org.opentmf.api.client.common.util.HeaderUtil.prepareAndValidateMergePatch;
import static org.opentmf.api.client.common.util.HeaderUtil.prepareGetDelete;
import static org.opentmf.api.client.common.util.UriBuilderUtil.buildBaseUri;
import static org.opentmf.api.client.common.util.UriBuilderUtil.buildUri;
import static org.opentmf.api.client.common.util.UriBuilderUtil.buildUriWithId;
import static org.opentmf.api.client.common.util.UriBuilderUtil.withPagination;

import com.jayway.jsonpath.JsonPath;
import java.lang.reflect.Array;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.opentmf.api.client.common.api.GenericTmfClient;
import org.opentmf.api.client.common.api.TmfClient;
import org.opentmf.api.client.common.api.TmfEntityClient;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.model.JsonFilter;
import org.opentmf.api.client.common.model.OffsetPage;
import org.opentmf.api.client.common.model.Scope;
import org.opentmf.api.client.common.model.SubResourcePath;
import org.opentmf.api.client.common.model.TmfOffsetRequest;
import org.opentmf.api.client.common.model.TmfPage;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.opentmf.api.client.common.util.ResponseHeaderUtil;
import org.opentmf.api.client.common.util.TmfApiClientConstants;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.opentmf.client.rest.util.SyncClientUtil;
import org.opentmf.commons.patch.JsonPatch;
import org.opentmf.commons.util.JacksonUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Synchronous (RestClient) implementation of {@link TmfClient}.
 *
 * <p>Each verb terminates in an entity-returning core ({@code *EntityWithToken}) that reads the
 * full {@link ResponseEntity}; the body-returning interface methods unwrap it. The cores are what
 * the entity view returned by {@code entity()} delegates to, so both views share one request path.
 *
 * @param <C> create DTO type
 * @param <U> update/patch DTO type
 * @param <R> response type
 */
@Slf4j
public class TmfClientImpl<C, U, R> implements TmfClient<C, U, R> {

  private final EndpointConfig endpointConfig;
  private final ServerConfig serverConfig;
  private final RestClient restClient;
  private final SyncTokenService tokenService;
  private final ClientProperties clientProperties;
  private final Class<R> responseType;
  private final SubResourcePath subPath;
  private final TmfEntityClient<C, U, R> entityView = new TmfEntityClientImpl<>(this);

  public TmfClientImpl(
      EndpointConfig endpointConfig,
      ServerConfig serverConfig,
      RestClient restClient,
      SyncTokenService tokenService,
      ClientProperties clientProperties,
      Class<R> responseType) {
    this(endpointConfig, serverConfig, restClient, tokenService, clientProperties, responseType,
        SubResourcePath.none());
  }

  public TmfClientImpl(
      EndpointConfig endpointConfig,
      ServerConfig serverConfig,
      RestClient restClient,
      SyncTokenService tokenService,
      ClientProperties clientProperties,
      Class<R> responseType,
      SubResourcePath subPath) {
    this.endpointConfig   = Objects.requireNonNull(endpointConfig);
    this.serverConfig     = Objects.requireNonNull(serverConfig);
    this.restClient       = Objects.requireNonNull(restClient);
    this.tokenService     = Objects.requireNonNull(tokenService);
    this.clientProperties = Objects.requireNonNull(clientProperties);
    this.responseType     = Objects.requireNonNull(responseType);
    this.subPath          = Objects.requireNonNull(subPath);
  }

  // ==========================================================================
  // SUB-RESOURCE
  // ==========================================================================

  @Override public GenericTmfClient sub(String template, Object... vars) {
    return new GenericTmfClientImpl(endpointConfig, serverConfig, restClient, tokenService,
        clientProperties, subPath.append(template, vars));
  }

  // ==========================================================================
  // ENTITY VIEW
  // ==========================================================================

  @Override public TmfEntityClient<C, U, R> entity() {
    return entityView;
  }

  // ==========================================================================
  // Token helper
  // ==========================================================================

  protected String getToken(Scope scope) {
    String scopeValue = endpointConfig.getScopes().get(scope);
    return (scopeValue != null && !scopeValue.isBlank())
        ? tokenService.getToken(scopeValue)
        : tokenService.getToken();
  }

  protected Consumer<HttpHeaders> headers(String token, TmfRequestContext ctx) {
    return headersConsumer(tokenService.getTokenType(), token,
        mergeFixedHeaders(serverConfig.getFixedHeaders(), endpointConfig.getFixedHeaders()), ctx,
        clientProperties.getAuthType());
  }

  /** The default response type, for the entity view's overload defaulting. */
  Class<R> responseType() {
    return responseType;
  }

  // ==========================================================================
  // GET
  // ==========================================================================

  @Override public R get(String id) {
    return getWithToken(getToken(Scope.GET), id);
  }

  @Override public R get(String id, TmfRequestContext ctx) {
    return get(id, ctx, responseType);
  }

  @Override public <T> T get(String id, Class<T> type) {
    return getWithToken(getToken(Scope.GET), id, type);
  }

  @Override public <T> T get(String id, TmfRequestContext ctx, Class<T> type) {
    return getWithToken(getToken(Scope.GET), id, ctx, type);
  }

  @Override public R getWithToken(String token, String id) {
    return getWithToken(token, id, responseType);
  }

  @Override public R getWithToken(String token, String id, TmfRequestContext ctx) {
    return getWithToken(token, id, ctx, responseType);
  }

  @Override public <T> T getWithToken(String token, String id, Class<T> type) {
    return getWithToken(token, id, null, type);
  }

  @Override public <T> T getWithToken(String token, String id, TmfRequestContext ctx, Class<T> type) {
    return getEntityWithToken(token, id, ctx, type).getBody();
  }

  <T> ResponseEntity<T> getEntityWithToken(
      String token, String id, TmfRequestContext ctx, Class<T> type) {
    URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx, subPath);
    var h = prepareGetDelete(headers(token, ctx));
    return withRetry(() -> restClient.get().uri(uri).headers(hh -> hh.addAll(h))
        .retrieve().toEntity(type));
  }

  // ==========================================================================
  // LIST single page
  // ==========================================================================

  @Override public List<R> list() {
    return listWithToken(getToken(Scope.LIST));
  }

  @Override public <T> List<T> list(Class<T> type) {
    return listWithToken(getToken(Scope.LIST), type);
  }

  @Override public List<R> list(Pageable pageable) {
    return listWithToken(getToken(Scope.LIST), pageable);
  }

  @Override public <T> List<T> list(Pageable pageable, Class<T> type) {
    return listWithToken(getToken(Scope.LIST), pageable, type);
  }

  @Override public List<R> listWithToken(String token) {
    return listWithToken(token, responseType);
  }

  @Override public <T> List<T> listWithToken(String token, Class<T> type) {
    return listWithToken(token, TmfOffsetRequest.of(0), type);
  }

  @Override public List<R> listWithToken(String token, Pageable pageable) {
    return listWithToken(token, pageable, responseType);
  }

  @Override public <T> List<T> listWithToken(String token, Pageable pageable, Class<T> type) {
    return retrieveSinglePageWithResponse(token, pageable, type).getContent();
  }

  @SuppressWarnings("unchecked")
  <T> ResponseEntity<List<T>> listEntityWithToken(String token, Pageable pageable, Class<T> type) {
    URI base = buildBaseUri(serverConfig, endpointConfig, subPath);
    URI uri = withPagination(base, pageable);

    var ctx = toContext(pageable);
    var h = prepareGetDelete(headers(token, ctx));

    Class<T[]> arrayType = (Class<T[]>) Array.newInstance(type, 0).getClass();
    ResponseEntity<T[]> entity = withRetry(
        () -> restClient.get().uri(uri).headers(hh -> hh.addAll(h))
            .retrieve().toEntity(arrayType));

    T[] body = entity.getBody();
    List<T> content = body != null ? List.of(body) : List.of();
    return new ResponseEntity<>(content, entity.getHeaders(), entity.getStatusCode());
  }

  // ==========================================================================
  // LIST ALL pages
  // ==========================================================================

  @Override public List<R> listAll() {
    return listAllWithToken(getToken(Scope.LIST));
  }

  @Override public <T> List<T> listAll(Class<T> type) {
    return listAllWithToken(getToken(Scope.LIST), type);
  }

  @Override public List<R> listAll(Pageable pageable) {
    return listAllWithToken(getToken(Scope.LIST), pageable);
  }

  @Override public <T> List<T> listAll(Pageable pageable, Class<T> type) {
    return listAllWithToken(getToken(Scope.LIST), pageable, type);
  }

  @Override public List<R> listAllWithToken(String token) {
    return listAllWithToken(token, responseType);
  }

  @Override public <T> List<T> listAllWithToken(String token, Class<T> type) {
    return listAllWithToken(token, TmfOffsetRequest.of(), type);
  }

  @Override public List<R> listAllWithToken(String token, Pageable pageable) {
    return listAllWithToken(token, pageable, responseType);
  }

  @Override public <T> List<T> listAllWithToken(String token, Pageable pageable, Class<T> type) {
    TmfOffsetRequest req = TmfOffsetRequest.of(pageable);
    if (req.getJsonFilterType() == JsonFilter.TYPE.CLIENT) {
      return retrieveAllPagesWithClientFilter(token, req, type);
    }
    return recursiveRetrieve(token, req, type);
  }

  // ==========================================================================
  // LIST PAGED with metadata
  // ==========================================================================

  @Override public TmfPage<List<R>> listPaged() {
    return listPagedWithToken(getToken(Scope.LIST));
  }

  @Override public <T> TmfPage<List<T>> listPaged(Class<T> type) {
    return listPagedWithToken(getToken(Scope.LIST), type);
  }

  @Override public TmfPage<List<R>> listPaged(Pageable pageable) {
    return listPagedWithToken(getToken(Scope.LIST), pageable);
  }

  @Override public <T> TmfPage<List<T>> listPaged(Pageable pageable, Class<T> type) {
    return listPagedWithToken(getToken(Scope.LIST), pageable, type);
  }

  @Override public TmfPage<List<R>> listPagedWithToken(String token) {
    return listPagedWithToken(token, responseType);
  }

  @Override public <T> TmfPage<List<T>> listPagedWithToken(String token, Class<T> type) {
    return listPagedWithToken(token, TmfOffsetRequest.of(), type);
  }

  @Override public TmfPage<List<R>> listPagedWithToken(String token, Pageable pageable) {
    return listPagedWithToken(token, pageable, responseType);
  }

  @Override public <T> TmfPage<List<T>> listPagedWithToken(String token, Pageable pageable, Class<T> type) {
    return retrieveSinglePageWithResponse(token, pageable, type);
  }

  // ==========================================================================
  // POST
  // ==========================================================================

  @Override public R post(C obj) {
    return postWithToken(getToken(Scope.POST), obj);
  }

  @Override public R post(C obj, TmfRequestContext ctx) {
    return postWithToken(getToken(Scope.POST), obj, ctx);
  }

  @Override public <T> T post(C obj, Class<T> type) {
    return postWithToken(getToken(Scope.POST), obj, type);
  }

  @Override public <T> T post(C obj, TmfRequestContext ctx, Class<T> type) {
    return postWithToken(getToken(Scope.POST), obj, ctx, type);
  }

  @Override public R postWithToken(String token, C obj) {
    return postWithToken(token, obj, responseType);
  }

  @Override public R postWithToken(String token, C obj, TmfRequestContext ctx) {
    return postWithToken(token, obj, ctx, responseType);
  }

  @Override public <T> T postWithToken(String token, C obj, Class<T> type) {
    return postWithToken(token, obj, null, type);
  }

  @Override public <T> T postWithToken(String token, C obj, TmfRequestContext ctx, Class<T> type) {
    return postEntityWithToken(token, obj, ctx, type).getBody();
  }

  <T> ResponseEntity<T> postEntityWithToken(
      String token, C obj, TmfRequestContext ctx, Class<T> type) {
    Objects.requireNonNull(obj, TmfApiClientConstants.ERR_NULL_BODY.formatted(type.getSimpleName()));
    URI uri = buildUri(serverConfig, endpointConfig, ctx, subPath);
    var h = prepareAndValidate(headers(token, ctx), MediaType.APPLICATION_JSON);
    return withRetry(() -> restClient.post().uri(uri).headers(hh -> hh.addAll(h))
        .body(obj).retrieve().toEntity(type));
  }

  // ==========================================================================
  // MERGE PATCH
  // ==========================================================================

  @Override public R patch(String id, U obj) {
    return patch(id, obj, responseType);
  }

  @Override public R patch(String id, U obj, TmfRequestContext ctx) {
    return patch(id, obj, ctx, responseType);
  }

  @Override public <T> T patch(String id, U obj, Class<T> type) {
    return patch(id, obj, null, type);
  }

  @Override public <T> T patch(String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return patchWithToken(getToken(Scope.PATCH), id, obj, ctx, type);
  }

  @Override public R patchWithToken(String token, String id, U obj) {
    return patchWithToken(token, id, obj, responseType);
  }

  @Override public R patchWithToken(String token, String id, U obj, TmfRequestContext ctx) {
    return patchWithToken(token, id, obj, ctx, responseType);
  }

  @Override public <T> T patchWithToken(String token, String id, U obj, Class<T> type) {
    return patchWithToken(token, id, obj, null, type);
  }

  @Override public <T> T patchWithToken(String token, String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return patchEntityWithToken(token, id, obj, ctx, type).getBody();
  }

  <T> ResponseEntity<T> patchEntityWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type) {
    Objects.requireNonNull(obj, TmfApiClientConstants.ERR_NULL_BODY.formatted(type.getSimpleName()));
    URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx, subPath);
    var h = prepareAndValidateMergePatch(headers(token, ctx));
    return withRetry(() -> restClient.patch().uri(uri).headers(hh -> hh.addAll(h))
        .body(obj).retrieve().toEntity(type));
  }

  // ==========================================================================
  // JSON PATCH
  // ==========================================================================

  @Override public R patch(String id, JsonPatch jsonPatch) {
    return patch(id, jsonPatch, responseType);
  }

  @Override public R patch(String id, JsonPatch jsonPatch, TmfRequestContext ctx) {
    return patch(id, jsonPatch, ctx, responseType);
  }

  @Override public <T> T patch(String id, JsonPatch jsonPatch, Class<T> type) {
    return patch(id, jsonPatch, null, type);
  }

  @Override public <T> T patch(String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return patchWithToken(getToken(Scope.PATCH), id, jsonPatch, ctx, type);
  }

  @Override public R patchWithToken(String token, String id, JsonPatch jsonPatch) {
    return patchWithToken(token, id, jsonPatch, responseType);
  }

  @Override public R patchWithToken(String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx) {
    return patchWithToken(token, id, jsonPatch, ctx, responseType);
  }

  @Override public <T> T patchWithToken(String token, String id, JsonPatch jsonPatch, Class<T> type) {
    return patchWithToken(token, id, jsonPatch, null, type);
  }

  @Override public <T> T patchWithToken(
      String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return patchEntityWithToken(token, id, jsonPatch, ctx, type).getBody();
  }

  <T> ResponseEntity<T> patchEntityWithToken(
      String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    Objects.requireNonNull(jsonPatch,
        TmfApiClientConstants.ERR_NULL_BODY.formatted(type.getSimpleName()));
    URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx, subPath);
    var h = prepareAndValidateJsonPatch(headers(token, ctx));
    return withRetry(() -> restClient.patch().uri(uri).headers(hh -> hh.addAll(h))
        .body(jsonPatch.toJsonNode()).retrieve().toEntity(type));
  }

  // ==========================================================================
  // COLLECTION JSON PATCH
  // ==========================================================================

  @Override public List<R> patchCollection(JsonPatch jsonPatch) {
    return patchCollection(jsonPatch, responseType);
  }

  @Override public List<R> patchCollection(JsonPatch jsonPatch, TmfRequestContext ctx) {
    return patchCollection(jsonPatch, ctx, responseType);
  }

  @Override public <T> List<T> patchCollection(JsonPatch jsonPatch, Class<T> type) {
    return patchCollection(jsonPatch, null, type);
  }

  @Override public <T> List<T> patchCollection(JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return patchCollectionWithToken(getToken(Scope.PATCH), jsonPatch, ctx, type);
  }

  @Override public List<R> patchCollectionWithToken(String token, JsonPatch jsonPatch) {
    return patchCollectionWithToken(token, jsonPatch, responseType);
  }

  @Override public List<R> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx) {
    return patchCollectionWithToken(token, jsonPatch, ctx, responseType);
  }

  @Override public <T> List<T> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, Class<T> type) {
    return patchCollectionWithToken(token, jsonPatch, null, type);
  }

  @Override public <T> List<T> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return patchCollectionEntityWithToken(token, jsonPatch, ctx, type).getBody();
  }

  @SuppressWarnings("unchecked")
  <T> ResponseEntity<List<T>> patchCollectionEntityWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    Objects.requireNonNull(jsonPatch,
        TmfApiClientConstants.ERR_NULL_BODY.formatted(type.getSimpleName()));
    URI uri = buildUri(serverConfig, endpointConfig, ctx, subPath);
    var h = prepareAndValidateJsonPatch(headers(token, ctx));
    Class<T[]> arrayType = (Class<T[]>) Array.newInstance(type, 0).getClass();
    ResponseEntity<T[]> entity = withRetry(
        () -> restClient.patch().uri(uri).headers(hh -> hh.addAll(h))
            .body(jsonPatch.toJsonNode()).retrieve().toEntity(arrayType));
    T[] body = entity.getBody();
    List<T> content = body != null ? List.of(body) : List.of();
    return new ResponseEntity<>(content, entity.getHeaders(), entity.getStatusCode());
  }

  // ==========================================================================
  // PUT
  // ==========================================================================

  @Override public R put(String id, U obj) {
    return put(id, obj, responseType);
  }

  @Override public R put(String id, U obj, TmfRequestContext ctx) {
    return put(id, obj, ctx, responseType);
  }

  @Override public <T> T put(String id, U obj, Class<T> type) {
    return put(id, obj, null, type);
  }

  @Override public <T> T put(String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return putWithToken(getToken(Scope.PUT), id, obj, ctx, type);
  }

  @Override public R putWithToken(String token, String id, U obj) {
    return putWithToken(token, id, obj, responseType);
  }

  @Override public R putWithToken(String token, String id, U obj, TmfRequestContext ctx) {
    return putWithToken(token, id, obj, ctx, responseType);
  }

  @Override public <T> T putWithToken(String token, String id, U obj, Class<T> type) {
    return putWithToken(token, id, obj, null, type);
  }

  @Override public <T> T putWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return putEntityWithToken(token, id, obj, ctx, type).getBody();
  }

  <T> ResponseEntity<T> putEntityWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type) {
    Objects.requireNonNull(obj, TmfApiClientConstants.ERR_NULL_BODY.formatted(type.getSimpleName()));
    URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx, subPath);
    var h = prepareAndValidate(headers(token, ctx), MediaType.APPLICATION_JSON);
    return withRetry(() -> restClient.put().uri(uri).headers(hh -> hh.addAll(h))
        .body(obj).retrieve().toEntity(type));
  }

  // ==========================================================================
  // DELETE
  // ==========================================================================

  @Override public void delete(String id) {
    deleteWithToken(getToken(Scope.DELETE), id);
  }

  @Override public void delete(String id, TmfRequestContext ctx) {
    deleteWithToken(getToken(Scope.DELETE), id, ctx);
  }

  @Override public <T> T delete(String id, Class<T> type) {
    return deleteWithToken(getToken(Scope.DELETE), id, type);
  }

  @Override public <T> T delete(String id, Class<T> type, TmfRequestContext ctx) {
    return deleteWithToken(getToken(Scope.DELETE), id, type, ctx);
  }

  @Override public void deleteWithToken(String token, String id) {
    deleteWithToken(token, id, (TmfRequestContext) null);
  }

  @Override public void deleteWithToken(String token, String id, TmfRequestContext ctx) {
    deleteEntityWithToken(token, id, ctx);
  }

  ResponseEntity<Void> deleteEntityWithToken(String token, String id, TmfRequestContext ctx) {
    URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx, subPath);
    var h = prepareGetDelete(headers(token, ctx));
    return withRetry(() -> restClient.delete().uri(uri).headers(hh -> hh.addAll(h))
        .retrieve().toBodilessEntity());
  }

  @Override public <T> T deleteWithToken(String token, String id, Class<T> type) {
    return deleteWithToken(token, id, type, null);
  }

  @Override public <T> T deleteWithToken(
      String token, String id, Class<T> type, TmfRequestContext ctx) {
    return deleteEntityWithToken(token, id, type, ctx).getBody();
  }

  <T> ResponseEntity<T> deleteEntityWithToken(
      String token, String id, Class<T> type, TmfRequestContext ctx) {
    URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx, subPath);
    var h = prepareGetDelete(headers(token, ctx));
    return withRetry(() -> restClient.delete().uri(uri).headers(hh -> hh.addAll(h))
        .retrieve().toEntity(type));
  }

  // ==========================================================================
  // Internal pagination helpers
  // ==========================================================================

  private <T> TmfPage<List<T>> retrieveSinglePageWithResponse(
      String token, Pageable pageable, Class<T> type) {
    ResponseEntity<List<T>> entity = listEntityWithToken(token, pageable, type);

    long total = ResponseHeaderUtil.getXTotalCount(entity);
    int count  = ResponseHeaderUtil.getContentRangeItemCount(entity);
    return new OffsetPage<>(total, count, pageable, entity.getBody());
  }

  private <T> List<T> recursiveRetrieve(String token, Pageable pageable, Class<T> type) {
    TmfPage<List<T>> page = retrieveSinglePageWithResponse(token, pageable, type);
    List<T> result = new ArrayList<>(page.getContent());
    if (!page.isLast()) {
      result.addAll(recursiveRetrieve(token, page.getNextPageable(), type));
    }
    return result;
  }

  private <T> List<T> retrieveAllPagesWithClientFilter(
      String token, TmfOffsetRequest req, Class<T> type) {
    List<Object> all = recursiveRetrieve(token, req, Object.class);
    String json = JacksonUtil.objectToJson(all);
    List<T> result = JsonPath.read(json, req.getJsonFilter().getQuery());
    return result.stream()
        .map(o -> JacksonUtil.jsonToObject(JacksonUtil.objectToJson(o), type))
        .toList();
  }

  private <T> T withRetry(Supplier<T> action) {
    return SyncClientUtil.executeWithRetry(
        action,
        clientProperties.getNumRetries(),
        clientProperties.getRetryWaitDuration());
  }

  private static TmfRequestContext toContext(Pageable pageable) {
    return pageable instanceof TmfOffsetRequest r ? r.getRequestContext() : null;
  }
}
