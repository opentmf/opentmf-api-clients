package org.opentmf.api.client.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class HeaderUtilTest {

  private static Consumer<HttpHeaders> authConsumer() {
    return h -> h.set(HttpHeaders.AUTHORIZATION, "Bearer test-token");
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
  void prepareAndValidate_throwsWhenAuthorizationAbsent() {
    assertThatThrownBy(() -> HeaderUtil.prepareAndValidate(h -> {}, MediaType.APPLICATION_JSON))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("token");
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

  @Test
  void headersConsumer_buildsCorrectly() {
    var consumer = HeaderUtil.headersConsumer("Bearer", "my-token", null, null);
    var headers = new HttpHeaders();
    consumer.accept(headers);
    assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer my-token");
  }

  @Test
  void headersConsumer_withFixedHeaders() {
    var consumer = HeaderUtil.headersConsumer(
        "Bearer", "my-token", java.util.Map.of("X-Tenant", "t1"), null);
    var headers = new HttpHeaders();
    consumer.accept(headers);
    assertThat(headers.getFirst("X-Tenant")).isEqualTo("t1");
  }

  @Test
  void headersConsumer_withRequestContext() {
    var ctx = new org.opentmf.api.client.common.model.TmfRequestContext();
    var headerParams = new org.springframework.util.LinkedMultiValueMap<String, String>();
    headerParams.add("X-Ctx", "ctx-val");
    ctx.setHeaderParameters(headerParams);

    var consumer = HeaderUtil.headersConsumer("Bearer", "my-token", null, ctx);
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
  void prepareGetDelete_throwsWhenConsumerIsNull() {
    assertThatThrownBy(() -> HeaderUtil.prepareGetDelete(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");
  }
}
