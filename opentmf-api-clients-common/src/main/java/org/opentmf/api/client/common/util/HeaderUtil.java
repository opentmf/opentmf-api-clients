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
import org.opentmf.client.common.model.AuthType;
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
   * Invokes {@code headersConsumer} exactly once and sets a default Content-Type if the consumer
   * did not provide one. Token validation happens inside the consumer built by
   * {@link #headersConsumer}, where both the token and the client's {@link AuthType} are in hand
   * — a {@code NONE} client's headers legitimately carry no {@code Authorization}.
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
   * Validates that the consumer is non-null, then returns the headers it produces. Does NOT set a
   * default Content-Type (use for GET and DELETE). Token validation happens inside the consumer
   * built by {@link #headersConsumer} — see {@link #prepareAndValidate}.
   */
  public static HttpHeaders prepareGetDelete(Consumer<HttpHeaders> headersConsumer) {
    if (headersConsumer == null) {
      throw new IllegalArgumentException(ERR_NULL_HEADERS_CONSUMER);
    }
    var headers = new HttpHeaders();
    headersConsumer.accept(headers);
    return headers;
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
   * Builds a {@link Consumer Consumer&lt;HttpHeaders&gt;} that sets the Authorization header from
   * the given token type and token value, adds any fixed headers from {@code fixedHeaders}, and
   * merges any request-context header parameters. If neither the fixed headers nor the request
   * context supplied an {@code Accept} header, defaults it to {@code application/json} — TMF APIs
   * always speak JSON.
   *
   * <p>The {@code authType} decides how an absent token is treated, at the one point where both
   * the token and the client's configuration are in hand:
   *
   * <ul>
   *   <li>{@code BEARER} / {@code BASIC} + blank token → {@link IllegalArgumentException}, thrown
   *       when the consumer runs (inside {@code prepare*}, before any request is built) — a
   *       misconfigured token service fails locally instead of collecting a remote 401;</li>
   *   <li>{@code NONE} + blank token (the {@code NoOp*TokenService} case) → no
   *       {@code Authorization} header at all;</li>
   *   <li>{@code NONE} + non-blank token (a {@code …WithToken} overload) → the caller's value is
   *       sent <b>verbatim</b> as the whole credential — pass {@code "Bearer eyJ…"} if a scheme
   *       is needed, since a NONE client has no token type of its own.</li>
   * </ul>
   *
   * @param tokenType the token scheme (e.g. {@code Bearer}); may be blank for NONE clients
   * @param token the token value; may be blank only when {@code authType} is {@code NONE}
   * @param fixedHeaders merged fixed headers, or null
   * @param ctx request context carrying additional header parameters, or null
   * @param authType the client's configured {@link AuthType}, from
   *     {@code ClientProperties.getAuthType()} (must not be null)
   */
  public static Consumer<HttpHeaders> headersConsumer(
      String tokenType,
      String token,
      Map<String, String> fixedHeaders,
      TmfRequestContext ctx,
      AuthType authType) {

    if (authType == null) {
      throw new IllegalArgumentException("authType must not be null.");
    }
    return httpHeaders -> {
      if (authType != AuthType.NONE && !StringUtils.hasText(token)) {
        throw new IllegalArgumentException(ERR_EMPTY_AUTH_TOKEN);
      }
      if (StringUtils.hasText(token)) {
        httpHeaders.set(HttpHeaders.AUTHORIZATION,
            StringUtils.hasText(tokenType) ? tokenType + " " + token : token);
      }
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
