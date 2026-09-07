package org.opentmf.api.client.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.opentmf.client.common.model.AuthType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;

class HeaderUtilTest {

  private static Consumer<HttpHeaders> authConsumer() {
    return h -> h.set(HttpHeaders.AUTHORIZATION, "Bearer test-token");
  }

  /** The consumer a NONE-auth client builds: blank type, blank token — the NoOp*TokenService case. */
  private static Consumer<HttpHeaders> noneConsumer() {
    return HeaderUtil.headersConsumer("", "", null, null, AuthType.NONE);
  }

  @Test
  void prepareAndValidate_setsDefaultContentType() {
    var headers = HeaderUtil.prepareAndValidate(authConsumer(), MediaType.APPLICATION_JSON);
    assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
  }

  @Test
  void prepareAndValidate_respectsCallerProvidedContentType() {
    Consumer<HttpHeaders> consumer = h -> {
      h.set(HttpHeaders.AUTHORIZATION, "Bearer test-token");
      h.setContentType(MediaType.APPLICATION_XML);
    };
    var headers = HeaderUtil.prepareAndValidate(consumer, MediaType.APPLICATION_JSON);
    assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_XML);
  }

  @Test
  void prepareAndValidate_throwsWhenConsumerIsNull() {
    assertThatThrownBy(() -> HeaderUtil.prepareAndValidate(null, MediaType.APPLICATION_JSON))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");
  }

  @Test
  void prepareGetDelete_doesNotSetContentType() {
    var headers = HeaderUtil.prepareGetDelete(authConsumer());
    assertThat(headers.getContentType()).isNull();
    assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-token");
  }

  @Test
  void prepareAndValidateMergePatch_setsCorrectContentType() {
    var headers = HeaderUtil.prepareAndValidateMergePatch(authConsumer());
    assertThat(headers.getContentType().toString())
        .startsWith(TmfApiClientConstants.MEDIA_TYPE_MERGE_PATCH);
  }

  @Test
  void prepareAndValidateJsonPatch_setsCorrectContentType() {
    var headers = HeaderUtil.prepareAndValidateJsonPatch(authConsumer());
    assertThat(headers.getContentType().toString())
        .startsWith(TmfApiClientConstants.MEDIA_TYPE_JSON_PATCH);
  }

  // --- headersConsumer: the four-row auth matrix ---

  @Test
  void headersConsumer_bearerWithToken_setsSchemeAndToken() {
    var consumer = HeaderUtil.headersConsumer("Bearer", "my-token", null, null, AuthType.BEARER);
    var headers = new HttpHeaders();
    consumer.accept(headers);
    assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer my-token");
  }

  @Test
  void headersConsumer_bearerWithBlankToken_throws() {
    // NEW guarantee, not a preserved one: before the AuthType.NONE fix the assembled header was
    // "Bearer " and hasText("Bearer ") is true, so a blank token sailed through to a remote 401.
    var consumer = HeaderUtil.headersConsumer("Bearer", "", null, null, AuthType.BEARER);
    var headers = new HttpHeaders();
    assertThatThrownBy(() -> consumer.accept(headers))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("token");
  }

  @Test
  void headersConsumer_basicWithBlankToken_throws() {
    var consumer = HeaderUtil.headersConsumer("Basic", null, null, null, AuthType.BASIC);
    var headers = new HttpHeaders();
    assertThatThrownBy(() -> consumer.accept(headers))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("token");
  }

  @Test
  void headersConsumer_noneWithBlankToken_setsNoAuthorizationHeader() {
    var headers = new HttpHeaders();
    noneConsumer().accept(headers);
    assertThat(headers.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
  }

  @Test
  void headersConsumer_noneWithExplicitToken_sendsItVerbatim() {
    // A …WithToken overload on a NONE client: no token type of its own, so the caller's string is
    // the whole credential — no scheme prefix, no leading space.
    var consumer = HeaderUtil.headersConsumer("", "opaque-credential", null, null, AuthType.NONE);
    var headers = new HttpHeaders();
    consumer.accept(headers);
    assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("opaque-credential");
  }

  @Test
  void headersConsumer_nullAuthType_throwsEagerly() {
    assertThatThrownBy(() -> HeaderUtil.headersConsumer("Bearer", "t", null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("authType");
  }

  // --- NONE client through every public prepare method: no header, nothing throws ---

  @Test
  void prepareGetDelete_noneClient_noAuthorizationAndNoThrow() {
    assertThatCode(() -> {
      var headers = HeaderUtil.prepareGetDelete(noneConsumer());
      assertThat(headers.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }).doesNotThrowAnyException();
  }

  @Test
  void prepareAndValidate_noneClient_noAuthorizationAndNoThrow() {
    assertThatCode(() -> {
      var headers = HeaderUtil.prepareAndValidate(noneConsumer(), MediaType.APPLICATION_JSON);
      assertThat(headers.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
      assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }).doesNotThrowAnyException();
  }

  @Test
  void prepareAndValidatePatch_noneClient_noAuthorizationAndNoThrow() {
    assertThatCode(() -> {
      var headers = HeaderUtil.prepareAndValidatePatch(
          noneConsumer(), TmfApiClientConstants.MEDIA_TYPE_MERGE_PATCH);
      assertThat(headers.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }).doesNotThrowAnyException();
  }

  @Test
  void prepareAndValidateMergePatch_noneClient_noAuthorizationAndNoThrow() {
    assertThatCode(() -> {
      var headers = HeaderUtil.prepareAndValidateMergePatch(noneConsumer());
      assertThat(headers.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }).doesNotThrowAnyException();
  }

  @Test
  void prepareAndValidateJsonPatch_noneClient_noAuthorizationAndNoThrow() {
    assertThatCode(() -> {
      var headers = HeaderUtil.prepareAndValidateJsonPatch(noneConsumer());
      assertThat(headers.containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
    }).doesNotThrowAnyException();
  }

  // --- pass-through behaviour, unchanged ---

  @Test
  void headersConsumer_withFixedHeaders() {
    var consumer = HeaderUtil.headersConsumer(
        "Bearer", "my-token", Map.of("X-Tenant", "t1"), null, AuthType.BEARER);
    var headers = new HttpHeaders();
    consumer.accept(headers);
    assertThat(headers.getFirst("X-Tenant")).isEqualTo("t1");
  }

  @Test
  void headersConsumer_withRequestContext() {
    var ctx = new TmfRequestContext();
    var headerParams = new LinkedMultiValueMap<String, String>();
    headerParams.add("X-Ctx", "ctx-val");
    ctx.setHeaderParameters(headerParams);

    var consumer = HeaderUtil.headersConsumer("Bearer", "my-token", null, ctx, AuthType.BEARER);
    var headers = new HttpHeaders();
    consumer.accept(headers);
    assertThat(headers.getFirst("X-Ctx")).isEqualTo("ctx-val");
  }

  @Test
  void prepareAndValidatePatch_warnOnMismatchedContentType() {
    Consumer<HttpHeaders> consumer = h -> {
      h.set(HttpHeaders.AUTHORIZATION, "Bearer test-token");
      h.setContentType(MediaType.APPLICATION_JSON);
    };
    var headers = HeaderUtil.prepareAndValidateMergePatch(consumer);
    assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
  }

  @Test
  void headersConsumer_defaultsAcceptToJson_whenAbsent() {
    var consumer = HeaderUtil.headersConsumer("Bearer", "my-token", null, null, AuthType.BEARER);
    var headers = new HttpHeaders();
    consumer.accept(headers);
    assertThat(headers.getAccept()).containsExactly(MediaType.APPLICATION_JSON);
  }

  @Test
  void headersConsumer_preservesExplicitAcceptFromFixedHeaders() {
    var consumer = HeaderUtil.headersConsumer(
        "Bearer", "my-token",
        Map.of(HttpHeaders.ACCEPT, "application/vnd.tmf.v4+json"),
        null, AuthType.BEARER);
    var headers = new HttpHeaders();
    consumer.accept(headers);
    assertThat(headers.getFirst(HttpHeaders.ACCEPT)).isEqualTo("application/vnd.tmf.v4+json");
  }

  @Test
  void headersConsumer_preservesExplicitAcceptFromRequestContext() {
    var ctx = new TmfRequestContext();
    var headerParams = new LinkedMultiValueMap<String, String>();
    headerParams.add(HttpHeaders.ACCEPT, "application/xml");
    ctx.setHeaderParameters(headerParams);

    var consumer = HeaderUtil.headersConsumer("Bearer", "my-token", null, ctx, AuthType.BEARER);
    var headers = new HttpHeaders();
    consumer.accept(headers);
    assertThat(headers.getFirst(HttpHeaders.ACCEPT)).isEqualTo("application/xml");
  }

  @Test
  void mergeFixedHeaders_bothNull_returnsNull() {
    assertThat(HeaderUtil.mergeFixedHeaders(null, null)).isNull();
  }

  @Test
  void mergeFixedHeaders_bothEmpty_returnsNull() {
    assertThat(HeaderUtil.mergeFixedHeaders(Map.of(), Map.of())).isNull();
  }

  @Test
  void mergeFixedHeaders_onlyServer_returnsServer() {
    var server = Map.of("X-A", "1");
    assertThat(HeaderUtil.mergeFixedHeaders(server, null)).isSameAs(server);
    assertThat(HeaderUtil.mergeFixedHeaders(server, Map.of())).isSameAs(server);
  }

  @Test
  void mergeFixedHeaders_onlyEndpoint_returnsEndpoint() {
    var endpoint = Map.of("X-B", "2");
    assertThat(HeaderUtil.mergeFixedHeaders(null, endpoint)).isSameAs(endpoint);
    assertThat(HeaderUtil.mergeFixedHeaders(Map.of(), endpoint)).isSameAs(endpoint);
  }

  @Test
  void mergeFixedHeaders_endpointOverridesServer() {
    var server = Map.of("X-A", "server", "X-Shared", "server");
    var endpoint = Map.of("X-B", "endpoint", "X-Shared", "endpoint");
    var merged = HeaderUtil.mergeFixedHeaders(server, endpoint);
    assertThat(merged)
        .containsEntry("X-A", "server")
        .containsEntry("X-B", "endpoint")
        .containsEntry("X-Shared", "endpoint");
  }

  @Test
  void prepareGetDelete_throwsWhenConsumerIsNull() {
    assertThatThrownBy(() -> HeaderUtil.prepareGetDelete(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");
  }
}
