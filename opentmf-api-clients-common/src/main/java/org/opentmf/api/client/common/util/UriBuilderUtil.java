package org.opentmf.api.client.common.util;

import static org.opentmf.api.client.common.util.TmfApiClientConstants.QUERY_PARAM_FIELDS;
import static org.opentmf.api.client.common.util.TmfApiClientConstants.QUERY_PARAM_FILTER;
import static org.opentmf.api.client.common.util.TmfApiClientConstants.QUERY_PARAM_LIMIT;
import static org.opentmf.api.client.common.util.TmfApiClientConstants.QUERY_PARAM_OFFSET;
import static org.opentmf.api.client.common.util.TmfApiClientConstants.QUERY_PARAM_SORT;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.model.JsonFilter;
import org.opentmf.api.client.common.model.SubResourcePath;
import org.opentmf.api.client.common.model.TmfOffsetRequest;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

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

  /**
   * Builds the base collection URI with a sub-resource suffix appended after the endpoint path.
   * With an empty suffix, delegates to {@link #buildBaseUri(ServerConfig, EndpointConfig)} so the
   * zero-suffix path is byte-identical to today's behaviour.
   */
  public static URI buildBaseUri(ServerConfig server, EndpointConfig endpoint,
      SubResourcePath sub) {
    if (sub.isEmpty()) {
      return buildBaseUri(server, endpoint);
    }
    return UriComponentsBuilder
        .fromUriString(server.getBaseUrl())
        .path(server.getContextPath())
        .path(endpoint.getPath())
        .path(sub.template())
        .build(sub.vars());
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
   * Builds a resource URI with a sub-resource suffix between the endpoint path and {@code /{id}}.
   * With an empty suffix, delegates to {@link #buildUriWithId(ServerConfig, EndpointConfig,
   * String)} so the zero-suffix path is byte-identical to today's behaviour.
   */
  public static URI buildUriWithId(
      ServerConfig server, EndpointConfig endpoint, String id, SubResourcePath sub) {
    if (sub.isEmpty()) {
      return buildUriWithId(server, endpoint, id);
    }
    Objects.requireNonNull(id, TmfApiClientConstants.ERR_NULL_ID);
    return UriComponentsBuilder
        .fromUriString(server.getBaseUrl())
        .path(server.getContextPath())
        .path(endpoint.getPath())
        .path(sub.template())
        .path("/{id}")
        .build(sub.varsWith(id));
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
   * Builds a resource URI with a sub-resource suffix, {@code /{id}} and optional
   * TmfRequestContext query params. With an empty suffix, delegates to
   * {@link #buildUriWithId(ServerConfig, EndpointConfig, String, TmfRequestContext)} so the
   * zero-suffix path is byte-identical to today's behaviour.
   */
  public static URI buildUriWithId(
      ServerConfig server, EndpointConfig endpoint, String id, TmfRequestContext ctx,
      SubResourcePath sub) {
    if (sub.isEmpty()) {
      return buildUriWithId(server, endpoint, id, ctx);
    }
    Objects.requireNonNull(id, TmfApiClientConstants.ERR_NULL_ID);
    var builder = UriComponentsBuilder
        .fromUriString(server.getBaseUrl())
        .path(server.getContextPath())
        .path(endpoint.getPath())
        .path(sub.template())
        .queryParams(ctx != null ? ctx.getQueryParameters() : null)
        .path("/{id}");

    if (ctx != null) {
      applyServerFilter(builder, ctx.getJsonFilterType(), ctx.getJsonFilterQuery());
      applyFields(builder, ctx.getFields());
    }
    return builder.encode().build(sub.varsWith(id));
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
   * Builds a collection URI with a sub-resource suffix and optional TmfRequestContext query
   * params. With an empty suffix, delegates to {@link #buildUri(ServerConfig, EndpointConfig,
   * TmfRequestContext)} so the zero-suffix path is byte-identical to today's behaviour.
   */
  public static URI buildUri(
      ServerConfig server, EndpointConfig endpoint, TmfRequestContext ctx, SubResourcePath sub) {
    if (sub.isEmpty()) {
      return buildUri(server, endpoint, ctx);
    }
    return UriComponentsBuilder
        .fromUriString(server.getBaseUrl())
        .path(server.getContextPath())
        .path(endpoint.getPath())
        .path(sub.template())
        .queryParams(ctx != null ? ctx.getQueryParameters() : null)
        .encode()
        .build(sub.vars());
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
      builder.queryParam(QUERY_PARAM_SORT, encodeValue(sortQuery(pageable.getSort())));
    }

    if (pageable instanceof TmfOffsetRequest req) {
      applyServerFilterEncoded(builder, req.getJsonFilterType(), req.getJsonFilterQuery());
      applyFieldsEncoded(builder, req.getFields());
      if (req.getQueryParameters() != null && !req.getQueryParameters().isEmpty()) {
        encodedQueryParams(req.getQueryParameters()).forEach(builder::queryParam);
      }
    }
    return builder.build(true).toUri();
  }

  /**
   * Appends fields and server-side filter from a {@link TmfRequestContext} to an existing URI.
   */
  public static URI withContext(URI base, TmfRequestContext ctx) {
    if (ctx == null) return base;
    var builder = UriComponentsBuilder.fromUri(base);
    applyServerFilterEncoded(builder, ctx.getJsonFilterType(), ctx.getJsonFilterQuery());
    applyFieldsEncoded(builder, ctx.getFields());
    return builder.build(true).toUri();
  }

  // --- private helpers ---

  // withContext / withPagination append query params to a URI that already carries an
  // encoded path (from buildUriWithId or buildUri). They terminate with .build(true).toUri()
  // so UriComponentsBuilder does not re-encode that path (which would turn %3A into %253A
  // for composite-key ids like Spec:(version=1)). .build(true) requires every value to be
  // pre-encoded, which is why the helpers below run user-supplied query values through
  // UriUtils.encodeQueryParam before handing them to the builder. Do not "simplify" by
  // switching back to .encode().build() or by removing the encodeValue(...) calls — both
  // reintroduce the double-encoding bug.
  private static void applyServerFilterEncoded(
      UriComponentsBuilder builder, JsonFilter.TYPE type, String query) {
    if (type == JsonFilter.TYPE.SERVER && query != null) {
      builder.queryParam(QUERY_PARAM_FILTER, encodeValue(query));
    }
  }

  private static void applyFieldsEncoded(UriComponentsBuilder builder, Set<String> fields) {
    if (fields != null && !fields.isEmpty()) {
      builder.queryParam(QUERY_PARAM_FIELDS, encodeValue(String.join(",", fields)));
    }
  }

  private static MultiValueMap<String, String> encodedQueryParams(
      MultiValueMap<String, String> params) {
    MultiValueMap<String, String> out = new LinkedMultiValueMap<>(params.size());
    params.forEach((key, values) -> {
      List<String> encoded = new ArrayList<>(values.size());
      for (String v : values) {
        encoded.add(v == null ? null : encodeValue(v));
      }
      out.put(key, encoded);
    });
    return out;
  }

  private static String encodeValue(String raw) {
    return UriUtils.encodeQueryParam(raw, StandardCharsets.UTF_8);
  }

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
