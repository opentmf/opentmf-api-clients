package org.opentmf.api.client.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

class TmfRequestContextTest {

  @Test
  void builder_withFields_varargs() {
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withFields("id", "name", "state")
        .build();
    assertThat(ctx.getFields()).containsExactlyInAnyOrder("id", "name", "state");
  }

  @Test
  void builder_withFields_set() {
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withFields(Set.of("id", "description"))
        .build();
    assertThat(ctx.getFields()).containsExactlyInAnyOrder("id", "description");
  }

  @Test
  void builder_withFields_additive() {
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withFields("id")
        .withFields("name")
        .build();
    assertThat(ctx.getFields()).containsExactlyInAnyOrder("id", "name");
  }

  @Test
  void builder_withServerJsonFilter() {
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withServerJsonFilter("$.name")
        .build();
    assertThat(ctx.getJsonFilterType()).isEqualTo(JsonFilter.TYPE.SERVER);
    assertThat(ctx.getJsonFilterQuery()).isEqualTo("$.name");
  }

  @Test
  void builder_withClientJsonFilter() {
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withClientJsonFilter("$[?(@.state == 'active')]")
        .build();
    assertThat(ctx.getJsonFilterType()).isEqualTo(JsonFilter.TYPE.CLIENT);
    assertThat(ctx.getJsonFilterQuery()).isEqualTo("$[?(@.state == 'active')]");
  }

  @Test
  void builder_jsonFilterCanBeSetOnlyOnce() {
    var builder = TmfRequestContext.builder().withServerJsonFilter("$.name");
    assertThatThrownBy(() -> builder.withClientJsonFilter("$.other"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("only once");
  }

  @Test
  void builder_withHeaderValues_keyValues() {
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withHeaderValues("X-Custom", "val1", "val2")
        .build();
    assertThat(ctx.getHeaderParameters().get("X-Custom")).containsExactly("val1", "val2");
  }

  @Test
  void builder_withHeaderValues_multiValueMap() {
    var map = new LinkedMultiValueMap<String, String>();
    map.add("X-A", "a1");
    map.add("X-B", "b1");
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withHeaderValues(map)
        .build();
    assertThat(ctx.getHeaderParameters()).containsKeys("X-A", "X-B");
  }

  @Test
  void builder_withQueryParameters_keyValues() {
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withQueryParameters("status", "active", "pending")
        .build();
    assertThat(ctx.getQueryParameters().get("status")).containsExactly("active", "pending");
  }

  @Test
  void builder_withQueryParameters_multiValueMap() {
    var map = new LinkedMultiValueMap<String, String>();
    map.add("offset", "10");
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withQueryParameters(map)
        .build();
    assertThat(ctx.getQueryParameters().getFirst("offset")).isEqualTo("10");
  }

  @Test
  void builder_copy_preservesAllFields() {
    TmfRequestContext original = TmfRequestContext.builder()
        .withFields("id", "name")
        .withServerJsonFilter("$.state")
        .withHeaderValues("X-Custom", "v1")
        .withQueryParameters("limit", "10")
        .build();

    TmfRequestContext copy = TmfRequestContext.builder(original).build();
    assertThat(copy.getFields()).isEqualTo(original.getFields());
    assertThat(copy.getJsonFilterQuery()).isEqualTo(original.getJsonFilterQuery());
    assertThat(copy.getJsonFilterType()).isEqualTo(original.getJsonFilterType());
    assertThat(copy.getHeaderParameters()).isEqualTo(original.getHeaderParameters());
    assertThat(copy.getQueryParameters()).isEqualTo(original.getQueryParameters());
  }

  @Test
  void emptyContext_hasNullFields() {
    TmfRequestContext ctx = TmfRequestContext.builder().build();
    assertThat(ctx.getFields()).isNull();
    assertThat(ctx.getJsonFilter()).isNull();
    assertThat(ctx.getJsonFilterQuery()).isNull();
    assertThat(ctx.getJsonFilterType()).isNull();
    assertThat(ctx.getHeaderParameters()).isNull();
    assertThat(ctx.getQueryParameters()).isNull();
  }

  @Test
  void noArgConstructor_worksForFrameworks() {
    TmfRequestContext ctx = new TmfRequestContext();
    assertThat(ctx.getFields()).isNull();
    assertThat(ctx.getJsonFilter()).isNull();
  }

  @Test
  void builder_copyEmptyContext_producesEmptyContext() {
    TmfRequestContext empty = new TmfRequestContext();
    TmfRequestContext copy = TmfRequestContext.builder(empty).build();
    assertThat(copy.getFields()).isNull();
    assertThat(copy.getJsonFilter()).isNull();
    assertThat(copy.getHeaderParameters()).isNull();
    assertThat(copy.getQueryParameters()).isNull();
  }

  @Test
  void builder_withHeaderValues_additive() {
    var map1 = new LinkedMultiValueMap<String, String>();
    map1.add("X-A", "a1");
    var map2 = new LinkedMultiValueMap<String, String>();
    map2.add("X-B", "b1");
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withHeaderValues(map1)
        .withHeaderValues(map2)
        .build();
    assertThat(ctx.getHeaderParameters()).containsKeys("X-A", "X-B");
  }

  @Test
  void builder_withQueryParameters_additive() {
    var map1 = new LinkedMultiValueMap<String, String>();
    map1.add("status", "active");
    var map2 = new LinkedMultiValueMap<String, String>();
    map2.add("type", "order");
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withQueryParameters(map1)
        .withQueryParameters(map2)
        .build();
    assertThat(ctx.getQueryParameters()).containsKeys("status", "type");
  }
}
