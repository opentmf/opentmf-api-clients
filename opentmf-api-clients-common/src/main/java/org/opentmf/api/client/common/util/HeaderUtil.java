package org.opentmf.api.client.common.util;

import static org.opentmf.api.client.common.util.TmfApiClientConstants.ERR_EMPTY_AUTH_TOKEN;
import static org.opentmf.api.client.common.util.TmfApiClientConstants.ERR_NULL_HEADERS_CONSUMER;
import static org.opentmf.api.client.common.util.TmfApiClientConstants.MEDIA_TYPE_JSON_PATCH;
import static org.opentmf.api.client.common.util.TmfApiClientConstants.MEDIA_TYPE_MERGE_PATCH;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

/**
 * Header preparation utilities shared by both reactive and REST client implementations.
 */
@Slf4j
public final class HeaderUtil {

  private HeaderUtil() {}

  /**
   * Invokes {@code headersConsumer} exactly once, validates the Authorization header, and sets a
   * default Content-Type if the consumer did not provide one.
   *
   * @param headersConsumer consumer that adds headers (must not be null)
   * @param defaultContentType content type to set when the consumer does not provide one
   * @return the prepared {@link HttpHeaders}
   */
  public static HttpHeaders prepareAndValidate(
      Consumer<HttpHeaders> headersConsumer, MediaType defaultContentType) {
    if (headersConsumer == null) {
      throw new IllegalArgumentException(ERR_NULL_HEADERS_CONSUMER);
    }
    var headers = new HttpHeaders();
    headersConsumer.accept(headers);
    validateAuthorization(headers);
    if (!headers.containsHeader(HttpHeaders.CONTENT_TYPE)) {
      headers.setContentType(defaultContentType);
    }
    return headers;
  }

  /**
   * Prepares headers for a PATCH request, defaulting to the expected patch content type and logging
   * a warning when the consumer overrides it with a different value.
   *
   * @param headersConsumer consumer that adds headers
   * @param expectedMediaType expected patch content-type string (e.g. {@code application/merge-patch+json})
   * @return the prepared {@link HttpHeaders}
   */
  public static HttpHeaders prepareAndValidatePatch(
      Consumer<HttpHeaders> headersConsumer, String expectedMediaType) {
    var headers = prepareAndValidate(headersConsumer, MediaType.valueOf(expectedMediaType));
    MediaType actual = headers.getContentType();
    if (actual != null && !actual.toString().startsWith(expectedMediaType)) {
      log.warn(
          "PATCH request Content-Type is '{}' but expected '{}'. "
              + "Consider updating your headers configuration.",
          actual,
          expectedMediaType);
    }
    return headers;
  }

  /**
   * Prepares headers for a merge-PATCH request.
   */
  public static HttpHeaders prepareAndValidateMergePatch(Consumer<HttpHeaders> headersConsumer) {
    return prepareAndValidatePatch(headersConsumer, MEDIA_TYPE_MERGE_PATCH);
  }

  /**
   * Prepares headers for a JSON-PATCH request.
   */
  public static HttpHeaders prepareAndValidateJsonPatch(Consumer<HttpHeaders> headersConsumer) {
    return prepareAndValidatePatch(headersConsumer, MEDIA_TYPE_JSON_PATCH);
  }

  /**
   * Validates that the consumer is non-null and produces an Authorization header, then returns the
   * headers. Does NOT set a default Content-Type (use for GET and DELETE).
   */
  public static HttpHeaders prepareGetDelete(Consumer<HttpHeaders> headersConsumer) {
    if (headersConsumer == null) {
      throw new IllegalArgumentException(ERR_NULL_HEADERS_CONSUMER);
    }
    var headers = new HttpHeaders();
    headersConsumer.accept(headers);
    validateAuthorization(headers);
    return headers;
  }

  private static void validateAuthorization(HttpHeaders headers) {
    if (!StringUtils.hasText(headers.getFirst(HttpHeaders.AUTHORIZATION))) {
      throw new IllegalArgumentException(ERR_EMPTY_AUTH_TOKEN);
    }
  }

  /**
   * Merges server-level and endpoint-level fixed headers. Endpoint entries win on key collision.
   * Returns null when both inputs are null/empty so callers can skip the consumer's fixed-headers
   * branch cheaply.
   */
  public static Map<String, String> mergeFixedHeaders(
      Map<String, String> serverFixed, Map<String, String> endpointFixed) {
    boolean serverEmpty = serverFixed == null || serverFixed.isEmpty();
    boolean endpointEmpty = endpointFixed == null || endpointFixed.isEmpty();
    if (serverEmpty && endpointEmpty) return null;
    if (endpointEmpty) return serverFixed;
    if (serverEmpty) return endpointFixed;
    Map<String, String> merged = new HashMap<>(serverFixed);
    merged.putAll(endpointFixed);
    return merged;
  }

  /**
   * Builds a {@link Consumer Consumer&lt;HttpHeaders&gt;} that sets the Authorization header using
   * the given token type and token value, adds any fixed headers from {@code fixedHeaders}, and
   * merges any request-context header parameters. If neither the fixed headers nor the request
   * context supplied an {@code Accept} header, defaults it to {@code application/json} — TMF APIs
   * always speak JSON.
   */
  public static Consumer<HttpHeaders> headersConsumer(
      String tokenType,
      String token,
      Map<String, String> fixedHeaders,
      TmfRequestContext ctx) {

    return httpHeaders -> {
      httpHeaders.set(HttpHeaders.AUTHORIZATION, tokenType + " " + token);
      if (fixedHeaders != null) fixedHeaders.forEach(httpHeaders::add);
      if (ctx != null && ctx.getHeaderParameters() != null) {
        ctx.getHeaderParameters().forEach((k, values) -> values.forEach(v -> httpHeaders.add(k, v)));
      }
      if (!httpHeaders.containsHeader(HttpHeaders.ACCEPT)) {
        httpHeaders.setAccept(List.of(MediaType.APPLICATION_JSON));
      }
    };
  }
}
