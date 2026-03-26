package org.opentmf.api.client.common.util;

import static org.opentmf.api.client.common.util.TmfApiClientConstants.QUERY_PARAM_FIELDS;
import static org.opentmf.api.client.common.util.TmfApiClientConstants.QUERY_PARAM_FILTER;
import static org.opentmf.api.client.common.util.TmfApiClientConstants.QUERY_PARAM_LIMIT;
import static org.opentmf.api.client.common.util.TmfApiClientConstants.QUERY_PARAM_OFFSET;
import static org.opentmf.api.client.common.util.TmfApiClientConstants.QUERY_PARAM_SORT;

import java.net.URI;
import java.util.Objects;
import java.util.Set;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.model.JsonFilter;
import org.opentmf.api.client.common.model.TmfOffsetRequest;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * URI construction helpers for TMF API clients.
 */
public final class UriBuilderUtil {

  private UriBuilderUtil() {}

  /** Builds the base collection URI: baseUrl + contextPath + endpointPath */
  public static URI buildBaseUri(ServerConfig server, EndpointConfig endpoint) {
    return UriComponentsBuilder
        .fromUriString(server.getBaseUrl())
        .path(server.getContextPath())
        .path(endpoint.getPath())
        .build()
        .toUri();
  }

  /** Builds a resource URI with {@code /{id}} appended. */
  public static URI buildUriWithId(ServerConfig server, EndpointConfig endpoint, String id) {
    Objects.requireNonNull(id, TmfApiClientConstants.ERR_NULL_ID);
    return UriComponentsBuilder
        .fromUriString(server.getBaseUrl())
        .path(server.getContextPath())
        .path(endpoint.getPath())
        .path("/{id}")
        .build(id);
  }

  /**
   * Builds a resource URI with {@code /{id}} and optional TmfRequestContext query params.
   */
  public static URI buildUriWithId(
      ServerConfig server, EndpointConfig endpoint, String id, TmfRequestContext ctx) {
    Objects.requireNonNull(id, TmfApiClientConstants.ERR_NULL_ID);
    var builder = UriComponentsBuilder
        .fromUriString(server.getBaseUrl())
        .path(server.getContextPath())
        .path(endpoint.getPath())
        .queryParams(ctx != null ? ctx.getQueryParameters() : null)
        .path("/{id}");

    if (ctx != null) {
      applyServerFilter(builder, ctx.getJsonFilterType(), ctx.getJsonFilterQuery());
      applyFields(builder, ctx.getFields());
    }
    return builder.encode().build(id);
  }

  /**
   * Builds a collection URI with optional TmfRequestContext query params.
   */
  public static URI buildUri(
      ServerConfig server, EndpointConfig endpoint, TmfRequestContext ctx) {
    return UriComponentsBuilder
        .fromUriString(server.getBaseUrl())
        .path(server.getContextPath())
        .path(endpoint.getPath())
        .queryParams(ctx != null ? ctx.getQueryParameters() : null)
        .encode()
        .build()
        .toUri();
  }

  /**
   * Appends pagination ({@code offset}, {@code limit}, {@code sort}) and any filter/fields from a
   * {@link Pageable} to an existing URI.
   */
  public static URI withPagination(URI base, Pageable pageable) {
    var builder = UriComponentsBuilder.fromUri(base)
        .queryParam(QUERY_PARAM_OFFSET, pageable.getOffset())
        .queryParam(QUERY_PARAM_LIMIT, pageable.getPageSize());

    if (!pageable.getSort().isEmpty()) {
      builder.queryParam(QUERY_PARAM_SORT, sortQuery(pageable.getSort()));
    }

    if (pageable instanceof TmfOffsetRequest req) {
      applyServerFilter(builder, req.getJsonFilterType(), req.getJsonFilterQuery());
      applyFields(builder, req.getFields());
      if (req.getQueryParameters() != null && !req.getQueryParameters().isEmpty()) {
        builder.queryParams(req.getQueryParameters());
      }
    }
    return builder.encode().build().toUri();
  }

  /**
   * Appends fields and server-side filter from a {@link TmfRequestContext} to an existing URI.
   */
  public static URI withContext(URI base, TmfRequestContext ctx) {
    if (ctx == null) return base;
    var builder = UriComponentsBuilder.fromUri(base);
    applyServerFilter(builder, ctx.getJsonFilterType(), ctx.getJsonFilterQuery());
    applyFields(builder, ctx.getFields());
    return builder.encode().build().toUri();
  }

  // --- private helpers ---

  private static void applyServerFilter(
      UriComponentsBuilder builder, JsonFilter.TYPE type, String query) {
    if (type == JsonFilter.TYPE.SERVER && query != null) {
      builder.queryParam(QUERY_PARAM_FILTER, query);
    }
  }

  private static void applyFields(UriComponentsBuilder builder, Set<String> fields) {
    if (fields != null && !fields.isEmpty()) {
      builder.queryParam(QUERY_PARAM_FIELDS, String.join(",", fields));
    }
  }

  private static String sortQuery(Sort sort) {
    return sort.stream()
        .map(o -> (o.getDirection() == Sort.Direction.DESC ? "-" : "") + o.getProperty())
        .reduce((a, b) -> a + "," + b)
        .orElse("");
  }
}
