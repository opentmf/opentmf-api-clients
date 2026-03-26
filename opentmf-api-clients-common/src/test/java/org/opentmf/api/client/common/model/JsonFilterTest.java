package org.opentmf.api.client.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JsonFilterTest {

  @Test
  void of_serverFilter() {
    JsonFilter filter = JsonFilter.of("$.name", JsonFilter.TYPE.SERVER);
    assertThat(filter.getQuery()).isEqualTo("$.name");
    assertThat(filter.getType()).isEqualTo(JsonFilter.TYPE.SERVER);
  }

  @Test
  void of_clientFilter() {
    JsonFilter filter = JsonFilter.of("$[?(@.state == 'active')]", JsonFilter.TYPE.CLIENT);
    assertThat(filter.getQuery()).isEqualTo("$[?(@.state == 'active')]");
    assertThat(filter.getType()).isEqualTo(JsonFilter.TYPE.CLIENT);
  }

  @Test
  void of_defaultType_isServer() {
    JsonFilter filter = JsonFilter.of("$.name");
    assertThat(filter.getType()).isEqualTo(JsonFilter.TYPE.SERVER);
  }

  @Test
  void of_nullQueryThrows() {
    assertThatThrownBy(() -> JsonFilter.of(null, JsonFilter.TYPE.SERVER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null or empty");
  }

  @Test
  void of_emptyQueryThrows() {
    assertThatThrownBy(() -> JsonFilter.of("", JsonFilter.TYPE.SERVER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null or empty");
  }

  @Test
  void of_blankQueryThrows() {
    assertThatThrownBy(() -> JsonFilter.of("   ", JsonFilter.TYPE.CLIENT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null or empty");
  }
}
