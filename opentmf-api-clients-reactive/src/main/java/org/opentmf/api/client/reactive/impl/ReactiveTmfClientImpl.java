package org.opentmf.api.client.reactive.impl;

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

import java.lang.reflect.Array;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
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
import org.opentmf.api.client.reactive.api.GenericReactiveTmfClient;
import org.opentmf.api.client.reactive.api.ReactiveTmfClient;
import org.opentmf.api.client.reactive.api.ReactiveTmfEntityClient;
import org.opentmf.client.common.exception.OpenTmfClientResponseException;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.reactive.service.api.TokenService;
import org.opentmf.client.reactive.util.WebClientUtil;
import org.opentmf.commons.patch.JsonPatch;
import org.opentmf.commons.util.JacksonUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.RetryBackoffSpec;

/**
 * Reactive (WebClient) implementation of {@link ReactiveTmfClient}.
 *
 * <p>Fully concrete and extensible by subclassing. Constructor args replace the abstract-method
 * pattern from the old library, making subclasses very lightweight.
 *
 * <p>Each verb terminates in an entity-returning core ({@code *EntityWithToken}) that reads the
 * full {@link ResponseEntity}; the body-returning interface methods unwrap it via
 * {@code Mono.justOrEmpty} (an empty body completes empty, exactly as {@code bodyToMono} did).
 * The cores are what the entity view returned by {@code entity()} delegates to, so both views
 * share one request path.
 *
 * @param <C> create DTO type
 * @param <U> update/patch DTO type
 * @param <R> response type
 */
@Slf4j
public class ReactiveTmfClientImpl<C, U, R> implements ReactiveTmfClient<C, U, R> {

  private final EndpointConfig endpointConfig;
  private final ServerConfig serverConfig;
  private final WebClient webClient;
  private final TokenService tokenService;
  private final ClientProperties clientProperties;
  private final Class<R> responseType;
  private final SubResourcePath subPath;
  private final ReactiveTmfEntityClient<C, U, R> entityView =
      new ReactiveTmfEntityClientImpl<>(this);

  public ReactiveTmfClientImpl(
      EndpointConfig endpointConfig,
      ServerConfig serverConfig,
      WebClient webClient,
      TokenService tokenService,
      ClientProperties clientProperties,
      Class<R> responseType) {
    this(endpointConfig, serverConfig, webClient, tokenService, clientProperties, responseType,
        SubResourcePath.none());
  }

  public ReactiveTmfClientImpl(
      EndpointConfig endpointConfig,
      ServerConfig serverConfig,
      WebClient webClient,
      TokenService tokenService,
      ClientProperties clientProperties,
      Class<R> responseType,
      SubResourcePath subPath) {
    this.endpointConfig  = Objects.requireNonNull(endpointConfig);
    this.serverConfig    = Objects.requireNonNull(serverConfig);
    this.webClient       = Objects.requireNonNull(webClient);
    this.tokenService    = Objects.requireNonNull(tokenService);
    this.clientProperties = Objects.requireNonNull(clientProperties);
    this.responseType    = Objects.requireNonNull(responseType);
    this.subPath         = Objects.requireNonNull(subPath);
  }

  // ==========================================================================
  // SUB-RESOURCE
  // ==========================================================================

  @Override public GenericReactiveTmfClient sub(String template, Object... vars) {
    return new GenericReactiveTmfClientImpl(endpointConfig, serverConfig, webClient, tokenService,
        clientProperties, subPath.append(template, vars));
  }

  // ==========================================================================
  // ENTITY VIEW
  // ==========================================================================

  @Override public ReactiveTmfEntityClient<C, U, R> entity() {
    return entityView;
  }

  // ==========================================================================
  // Token helper
  // ==========================================================================

  protected Mono<String> getToken(Scope scope) {
    String scopeValue = endpointConfig.getScopes().get(scope);
    return (scopeValue != null && !scopeValue.isBlank())
        ? tokenService.getToken(scopeValue)
        : tokenService.getToken();
  }

  // ==========================================================================
  // Headers consumer helper
  // ==========================================================================

  protected Consumer<HttpHeaders> headers(String token, TmfRequestContext ctx) {
    return headersConsumer(
        tokenService.getTokenType(),
        token,
        mergeFixedHeaders(serverConfig.getFixedHeaders(), endpointConfig.getFixedHeaders()),
        ctx,
        clientProperties.getAuthType());
  }

  /** The default response type, for the entity view's overload defaulting. */
  Class<R> responseType() {
    return responseType;
  }

  // ==========================================================================
  // GET
  // ==========================================================================

  @Override public Mono<R> get(String id) {
    return getToken(Scope.GET).flatMap(t -> getWithToken(t, id));
  }

  @Override public Mono<R> get(String id, TmfRequestContext ctx) {
    return get(id, ctx, responseType);
  }

  @Override public <T> Mono<T> get(String id, Class<T> type) {
    return getToken(Scope.GET).flatMap(t -> getWithToken(t, id, type));
  }

  @Override public <T> Mono<T> get(String id, TmfRequestContext ctx, Class<T> type) {
    return getToken(Scope.GET).flatMap(t -> getWithToken(t, id, ctx, type));
  }

  @Override public Mono<R> getWithToken(String token, String id) {
    return getWithToken(token, id, responseType);
  }

  @Override public Mono<R> getWithToken(String token, String id, TmfRequestContext ctx) {
    return getWithToken(token, id, ctx, responseType);
  }

  @Override public <T> Mono<T> getWithToken(String token, String id, Class<T> type) {
    return getWithToken(token, id, null, type);
  }

  @Override public <T> Mono<T> getWithToken(
      String token, String id, TmfRequestContext ctx, Class<T> type) {
    // NOT .map(ResponseEntity::getBody): an empty body yields a null body, and map would throw.
    return getEntityWithToken(token, id, ctx, type).flatMap(e -> Mono.justOrEmpty(e.getBody()));
  }

  <T> Mono<ResponseEntity<T>> getEntityWithToken(
      String token, String id, TmfRequestContext ctx, Class<T> type) {
    URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx, subPath);
    var h = prepareGetDelete(headers(token, ctx));
    return webClient.get().uri(uri).headers(hh -> hh.addAll(h))
        .retrieve()
        .onStatus(HttpStatusCode::isError, ReactiveTmfClientImpl::handleError)
        .toEntity(type)
        .retryWhen(retry());
  }

  // ==========================================================================
  // LIST single page
  // ==========================================================================

  @Override public Flux<R> list() {
    return getToken(Scope.LIST).flatMapMany(this::listWithToken);
  }

  @Override public <T> Flux<T> list(Class<T> type) {
    return getToken(Scope.LIST).flatMapMany(t -> listWithToken(t, type));
  }

  @Override public Flux<R> list(Pageable pageable) {
    return getToken(Scope.LIST).flatMapMany(t -> listWithToken(t, pageable));
  }

  @Override public <T> Flux<T> list(Pageable pageable, Class<T> type) {
    return getToken(Scope.LIST).flatMapMany(t -> listWithToken(t, pageable, type));
  }

  @Override public Flux<R> listWithToken(String token) {
    return listWithToken(token, responseType);
  }

  @Override public <T> Flux<T> listWithToken(String token, Class<T> type) {
    return listWithToken(token, TmfOffsetRequest.of(0), type);
  }

  @Override public Flux<R> listWithToken(String token, Pageable pageable) {
    return listWithToken(token, pageable, responseType);
  }

  @Override public <T> Flux<T> listWithToken(String token, Pageable pageable, Class<T> type) {
    return retrieveSinglePageWithResponse(token, pageable, type)
        .flatMapIterable(TmfPage::getContent);
  }

  // ==========================================================================
  // LIST ALL pages
  // ==========================================================================

  @Override public Flux<R> listAll() {
    return getToken(Scope.LIST).flatMapMany(this::listAllWithToken);
  }

  @Override public <T> Flux<T> listAll(Class<T> type) {
    return getToken(Scope.LIST).flatMapMany(t -> listAllWithToken(t, type));
  }

  @Override public Flux<R> listAll(Pageable pageable) {
    return getToken(Scope.LIST).flatMapMany(t -> listAllWithToken(t, pageable));
  }

  @Override public <T> Flux<T> listAll(Pageable pageable, Class<T> type) {
    return getToken(Scope.LIST).flatMapMany(t -> listAllWithToken(t, pageable, type));
  }

  @Override public Flux<R> listAllWithToken(String token) {
    return listAllWithToken(token, responseType);
  }

  @Override public <T> Flux<T> listAllWithToken(String token, Class<T> type) {
    return listAllWithToken(token, TmfOffsetRequest.of(), type);
  }

  @Override public Flux<R> listAllWithToken(String token, Pageable pageable) {
    return listAllWithToken(token, pageable, responseType);
  }

  @Override public <T> Flux<T> listAllWithToken(String token, Pageable pageable, Class<T> type) {
    TmfOffsetRequest req = TmfOffsetRequest.of(pageable);
    if (req.getJsonFilterType() == JsonFilter.TYPE.CLIENT) {
      return retrieveAllPagesWithClientFilter(token, req, type);
    }
    return recursiveRetrieve(token, req, type);
  }

  // ==========================================================================
  // LIST PAGED with metadata
  // ==========================================================================

  @Override public Mono<TmfPage<List<R>>> listPaged() {
    return getToken(Scope.LIST).flatMap(this::listPagedWithToken);
  }

  @Override public <T> Mono<TmfPage<List<T>>> listPaged(Class<T> type) {
    return getToken(Scope.LIST).flatMap(t -> listPagedWithToken(t, type));
  }

  @Override public Mono<TmfPage<List<R>>> listPaged(Pageable pageable) {
    return getToken(Scope.LIST).flatMap(t -> listPagedWithToken(t, pageable));
  }

  @Override public <T> Mono<TmfPage<List<T>>> listPaged(Pageable pageable, Class<T> type) {
    return getToken(Scope.LIST).flatMap(t -> listPagedWithToken(t, pageable, type));
  }

  @Override public Mono<TmfPage<List<R>>> listPagedWithToken(String token) {
    return listPagedWithToken(token, responseType);
  }

  @Override public <T> Mono<TmfPage<List<T>>> listPagedWithToken(String token, Class<T> type) {
    return listPagedWithToken(token, TmfOffsetRequest.of(), type);
  }

  @Override public Mono<TmfPage<List<R>>> listPagedWithToken(String token, Pageable pageable) {
    return listPagedWithToken(token, pageable, responseType);
  }

  @Override public <T> Mono<TmfPage<List<T>>> listPagedWithToken(
      String token, Pageable pageable, Class<T> type) {
    return retrieveSinglePageWithResponse(token, pageable, type);
  }

  // ==========================================================================
  // POST
  // ==========================================================================

  @Override public Mono<R> post(C obj) {
    return getToken(Scope.POST).flatMap(t -> postWithToken(t, obj));
  }

  @Override public Mono<R> post(C obj, TmfRequestContext ctx) {
    return getToken(Scope.POST).flatMap(t -> postWithToken(t, obj, ctx));
  }

  @Override public <T> Mono<T> post(C obj, Class<T> type) {
    return getToken(Scope.POST).flatMap(t -> postWithToken(t, obj, type));
  }

  @Override public <T> Mono<T> post(C obj, TmfRequestContext ctx, Class<T> type) {
    return getToken(Scope.POST).flatMap(t -> postWithToken(t, obj, ctx, type));
  }

  @Override public Mono<R> postWithToken(String token, C obj) {
    return postWithToken(token, obj, responseType);
  }

  @Override public Mono<R> postWithToken(String token, C obj, TmfRequestContext ctx) {
    return postWithToken(token, obj, ctx, responseType);
  }

  @Override public <T> Mono<T> postWithToken(String token, C obj, Class<T> type) {
    return postWithToken(token, obj, null, type);
  }

  @Override public <T> Mono<T> postWithToken(
      String token, C obj, TmfRequestContext ctx, Class<T> type) {
    return postEntityWithToken(token, obj, ctx, type).flatMap(e -> Mono.justOrEmpty(e.getBody()));
  }

  <T> Mono<ResponseEntity<T>> postEntityWithToken(
      String token, C obj, TmfRequestContext ctx, Class<T> type) {
    Objects.requireNonNull(obj, TmfApiClientConstants.ERR_NULL_BODY.formatted(type.getSimpleName()));
    URI uri = buildUri(serverConfig, endpointConfig, ctx, subPath);
    var h = prepareAndValidate(headers(token, ctx), MediaType.APPLICATION_JSON);
    return webClient.post().uri(uri).headers(hh -> hh.addAll(h))
        .bodyValue(obj)
        .retrieve()
        .onStatus(HttpStatusCode::isError, ReactiveTmfClientImpl::handleError)
        .toEntity(type)
        .retryWhen(retry());
  }

  // ==========================================================================
  // MERGE PATCH
  // ==========================================================================

  @Override public Mono<R> patch(String id, U obj) {
    return patch(id, obj, responseType);
  }

  @Override public Mono<R> patch(String id, U obj, TmfRequestContext ctx) {
    return patch(id, obj, ctx, responseType);
  }

  @Override public <T> Mono<T> patch(String id, U obj, Class<T> type) {
    return patch(id, obj, null, type);
  }

  @Override public <T> Mono<T> patch(String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return getToken(Scope.PATCH).flatMap(t -> patchWithToken(t, id, obj, ctx, type));
  }

  @Override public Mono<R> patchWithToken(String token, String id, U obj) {
    return patchWithToken(token, id, obj, responseType);
  }

  @Override public Mono<R> patchWithToken(String token, String id, U obj, TmfRequestContext ctx) {
    return patchWithToken(token, id, obj, ctx, responseType);
  }

  @Override public <T> Mono<T> patchWithToken(String token, String id, U obj, Class<T> type) {
    return patchWithToken(token, id, obj, null, type);
  }

  @Override public <T> Mono<T> patchWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return patchEntityWithToken(token, id, obj, ctx, type)
        .flatMap(e -> Mono.justOrEmpty(e.getBody()));
  }

  <T> Mono<ResponseEntity<T>> patchEntityWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type) {
    Objects.requireNonNull(obj, TmfApiClientConstants.ERR_NULL_BODY.formatted(type.getSimpleName()));
    URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx, subPath);
    var h = prepareAndValidateMergePatch(headers(token, ctx));
    return webClient.patch().uri(uri).headers(hh -> hh.addAll(h))
        .bodyValue(obj)
        .retrieve()
        .onStatus(HttpStatusCode::isError, ReactiveTmfClientImpl::handleError)
        .toEntity(type)
        .retryWhen(retry());
  }

  // ==========================================================================
  // JSON PATCH
  // ==========================================================================

  @Override public Mono<R> patch(String id, JsonPatch jsonPatch) {
    return patch(id, jsonPatch, responseType);
  }

  @Override public Mono<R> patch(String id, JsonPatch jsonPatch, TmfRequestContext ctx) {
    return patch(id, jsonPatch, ctx, responseType);
  }

  @Override public <T> Mono<T> patch(String id, JsonPatch jsonPatch, Class<T> type) {
    return patch(id, jsonPatch, null, type);
  }

  @Override public <T> Mono<T> patch(
      String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return getToken(Scope.PATCH).flatMap(t -> patchWithToken(t, id, jsonPatch, ctx, type));
  }

  @Override public Mono<R> patchWithToken(String token, String id, JsonPatch jsonPatch) {
    return patchWithToken(token, id, jsonPatch, responseType);
  }

  @Override public Mono<R> patchWithToken(
      String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx) {
    return patchWithToken(token, id, jsonPatch, ctx, responseType);
  }

  @Override public <T> Mono<T> patchWithToken(
      String token, String id, JsonPatch jsonPatch, Class<T> type) {
    return patchWithToken(token, id, jsonPatch, null, type);
  }

  @Override public <T> Mono<T> patchWithToken(
      String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return patchEntityWithToken(token, id, jsonPatch, ctx, type)
        .flatMap(e -> Mono.justOrEmpty(e.getBody()));
  }

  <T> Mono<ResponseEntity<T>> patchEntityWithToken(
      String token, String id, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    Objects.requireNonNull(jsonPatch,
        TmfApiClientConstants.ERR_NULL_BODY.formatted(type.getSimpleName()));
    URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx, subPath);
    var h = prepareAndValidateJsonPatch(headers(token, ctx));
    return webClient.patch().uri(uri).headers(hh -> hh.addAll(h))
        .bodyValue(jsonPatch.toJsonNode())
        .retrieve()
        .onStatus(HttpStatusCode::isError, ReactiveTmfClientImpl::handleError)
        .toEntity(type)
        .retryWhen(retry());
  }

  // ==========================================================================
  // COLLECTION JSON PATCH
  // ==========================================================================

  @Override public Mono<List<R>> patchCollection(JsonPatch jsonPatch) {
    return patchCollection(jsonPatch, responseType);
  }

  @Override public Mono<List<R>> patchCollection(JsonPatch jsonPatch, TmfRequestContext ctx) {
    return patchCollection(jsonPatch, ctx, responseType);
  }

  @Override public <T> Mono<List<T>> patchCollection(JsonPatch jsonPatch, Class<T> type) {
    return patchCollection(jsonPatch, null, type);
  }

  @Override public <T> Mono<List<T>> patchCollection(
      JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    return getToken(Scope.PATCH).flatMap(t -> patchCollectionWithToken(t, jsonPatch, ctx, type));
  }

  @Override public Mono<List<R>> patchCollectionWithToken(String token, JsonPatch jsonPatch) {
    return patchCollectionWithToken(token, jsonPatch, responseType);
  }

  @Override public Mono<List<R>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx) {
    return patchCollectionWithToken(token, jsonPatch, ctx, responseType);
  }

  @Override public <T> Mono<List<T>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, Class<T> type) {
    return patchCollectionWithToken(token, jsonPatch, null, type);
  }

  @Override public <T> Mono<List<T>> patchCollectionWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    // The entity core always materializes a non-null List body, so map is safe here.
    return patchCollectionEntityWithToken(token, jsonPatch, ctx, type)
        .map(ResponseEntity::getBody);
  }

  @SuppressWarnings("unchecked")
  <T> Mono<ResponseEntity<List<T>>> patchCollectionEntityWithToken(
      String token, JsonPatch jsonPatch, TmfRequestContext ctx, Class<T> type) {
    Objects.requireNonNull(jsonPatch,
        TmfApiClientConstants.ERR_NULL_BODY.formatted(type.getSimpleName()));
    URI uri = buildUri(serverConfig, endpointConfig, ctx, subPath);
    var h = prepareAndValidateJsonPatch(headers(token, ctx));
    Class<T[]> arrayType = (Class<T[]>) Array.newInstance(type, 0).getClass();
    return webClient.patch().uri(uri).headers(hh -> hh.addAll(h))
        .bodyValue(jsonPatch.toJsonNode())
        .retrieve()
        .onStatus(HttpStatusCode::isError, ReactiveTmfClientImpl::handleError)
        .toEntity(arrayType)
        .retryWhen(retry())
        .map(entity -> {
          T[] body = entity.getBody();
          List<T> content = body != null ? List.of(body) : List.of();
          return new ResponseEntity<>(content, entity.getHeaders(), entity.getStatusCode());
        });
  }

  // ==========================================================================
  // PUT
  // ==========================================================================

  @Override public Mono<R> put(String id, U obj) {
    return put(id, obj, responseType);
  }

  @Override public Mono<R> put(String id, U obj, TmfRequestContext ctx) {
    return put(id, obj, ctx, responseType);
  }

  @Override public <T> Mono<T> put(String id, U obj, Class<T> type) {
    return put(id, obj, null, type);
  }

  @Override public <T> Mono<T> put(String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return getToken(Scope.PUT).flatMap(t -> putWithToken(t, id, obj, ctx, type));
  }

  @Override public Mono<R> putWithToken(String token, String id, U obj) {
    return putWithToken(token, id, obj, responseType);
  }

  @Override public Mono<R> putWithToken(String token, String id, U obj, TmfRequestContext ctx) {
    return putWithToken(token, id, obj, ctx, responseType);
  }

  @Override public <T> Mono<T> putWithToken(String token, String id, U obj, Class<T> type) {
    return putWithToken(token, id, obj, null, type);
  }

  @Override public <T> Mono<T> putWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type) {
    return putEntityWithToken(token, id, obj, ctx, type)
        .flatMap(e -> Mono.justOrEmpty(e.getBody()));
  }

  <T> Mono<ResponseEntity<T>> putEntityWithToken(
      String token, String id, U obj, TmfRequestContext ctx, Class<T> type) {
    Objects.requireNonNull(obj, TmfApiClientConstants.ERR_NULL_BODY.formatted(type.getSimpleName()));
    URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx, subPath);
    var h = prepareAndValidate(headers(token, ctx), MediaType.APPLICATION_JSON);
    return webClient.put().uri(uri).headers(hh -> hh.addAll(h))
        .bodyValue(obj)
        .retrieve()
        .onStatus(HttpStatusCode::isError, ReactiveTmfClientImpl::handleError)
        .toEntity(type)
        .retryWhen(retry());
  }

  // ==========================================================================
  // DELETE
  // ==========================================================================

  @Override public Mono<Void> delete(String id) {
    return getToken(Scope.DELETE).flatMap(t -> deleteWithToken(t, id));
  }

  @Override public Mono<Void> delete(String id, TmfRequestContext ctx) {
    return getToken(Scope.DELETE).flatMap(t -> deleteWithToken(t, id, ctx));
  }

  @Override public <T> Mono<T> delete(String id, Class<T> type) {
    return getToken(Scope.DELETE).flatMap(t -> deleteWithToken(t, id, type));
  }

  @Override public <T> Mono<T> delete(String id, Class<T> type, TmfRequestContext ctx) {
    return getToken(Scope.DELETE).flatMap(t -> deleteWithToken(t, id, type, ctx));
  }

  @Override public Mono<Void> deleteWithToken(String token, String id) {
    return deleteWithToken(token, id, (TmfRequestContext) null);
  }

  @Override public Mono<Void> deleteWithToken(String token, String id, TmfRequestContext ctx) {
    return deleteEntityWithToken(token, id, ctx).then();
  }

  Mono<ResponseEntity<Void>> deleteEntityWithToken(
      String token, String id, TmfRequestContext ctx) {
    URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx, subPath);
    var h = prepareGetDelete(headers(token, ctx));
    return webClient.delete().uri(uri).headers(hh -> hh.addAll(h))
        .retrieve()
        .onStatus(HttpStatusCode::isError, ReactiveTmfClientImpl::handleError)
        .toBodilessEntity()
        .retryWhen(retry());
  }

  @Override public <T> Mono<T> deleteWithToken(String token, String id, Class<T> type) {
    return deleteWithToken(token, id, type, null);
  }

  @Override public <T> Mono<T> deleteWithToken(
      String token, String id, Class<T> type, TmfRequestContext ctx) {
    return deleteEntityWithToken(token, id, type, ctx)
        .flatMap(e -> Mono.justOrEmpty(e.getBody()));
  }

  <T> Mono<ResponseEntity<T>> deleteEntityWithToken(
      String token, String id, Class<T> type, TmfRequestContext ctx) {
    URI uri = buildUriWithId(serverConfig, endpointConfig, id, ctx, subPath);
    var h = prepareGetDelete(headers(token, ctx));
    return webClient.delete().uri(uri).headers(hh -> hh.addAll(h))
        .retrieve()
        .onStatus(HttpStatusCode::isError, ReactiveTmfClientImpl::handleError)
        .toEntity(type)
        .retryWhen(retry());
  }

  // ==========================================================================
  // Internal pagination helpers
  // ==========================================================================

  @SuppressWarnings("unchecked")
  <T> Mono<ResponseEntity<List<T>>> listEntityWithToken(
      String token, Pageable pageable, Class<T> type) {
    URI base = buildBaseUri(serverConfig, endpointConfig, subPath);
    URI uri = withPagination(base, pageable);

    var h = prepareGetDelete(headers(token, toContext(pageable)));
    Class<T[]> arrayType = (Class<T[]>) Array.newInstance(type, 0).getClass();
    return webClient.get().uri(uri).headers(hh -> hh.addAll(h))
        .retrieve()
        .onStatus(HttpStatusCode::isError, ReactiveTmfClientImpl::handleError)
        .toEntity(arrayType)
        .retryWhen(retry())
        .map(entity -> {
          T[] body = entity.getBody();
          List<T> content = body != null ? List.of(body) : List.of();
          return new ResponseEntity<>(content, entity.getHeaders(), entity.getStatusCode());
        });
  }

  private <T> Mono<TmfPage<List<T>>> retrieveSinglePageWithResponse(
      String token, Pageable pageable, Class<T> type) {
    return listEntityWithToken(token, pageable, type)
        .map(entity -> buildPage(entity, pageable));
  }

  private <T> TmfPage<List<T>> buildPage(
      ResponseEntity<List<T>> entity, Pageable pageable) {
    long total = ResponseHeaderUtil.getXTotalCount(entity);
    int count  = ResponseHeaderUtil.getContentRangeItemCount(entity);
    return new OffsetPage<>(total, count, pageable, entity.getBody());
  }

  private <T> Flux<T> recursiveRetrieve(String token, Pageable pageable, Class<T> type) {
    return retrieveSinglePageWithResponse(token, pageable, type)
        .flatMapMany(page -> {
          if (page.isLast()) {
            return Flux.fromIterable(page.getContent());
          }
          return Flux.concat(
              Flux.fromIterable(page.getContent()),
              recursiveRetrieve(token, page.getNextPageable(), type));
        });
  }

  private <T> Flux<T> retrieveAllPagesWithClientFilter(
      String token, TmfOffsetRequest req, Class<T> type) {
    return recursiveRetrieve(token, req, Object.class)
        .collectList()
        .flatMapMany(objects -> {
          String json = JacksonUtil.objectToJson(objects);
          List<T> result = JsonPath.read(json, req.getJsonFilter().getQuery());
          return Flux.fromIterable(
              result.stream()
                  .map(o -> JacksonUtil.jsonToObject(JacksonUtil.objectToJson(o), type))
                  .toList());
        });
  }

  private RetryBackoffSpec retry() {
    return WebClientUtil.retry(
        clientProperties.getNumRetries(),
        clientProperties.getRetryWaitDuration());
  }

  private static Mono<? extends Throwable> handleError(ClientResponse response) {
    return WebClientUtil.handleError(response, OpenTmfClientResponseException.class);
  }

  /** Extracts a TmfRequestContext from a Pageable, if it is a TmfOffsetRequest. */
  private static TmfRequestContext toContext(Pageable pageable) {
    return pageable instanceof TmfOffsetRequest r ? r.getRequestContext() : null;
  }
}
