package org.opentmf.api.client.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ScopeTest {

  @ParameterizedTest
  @EnumSource(Scope.class)
  void fromValue_roundTrips(Scope scope) {
    assertThat(Scope.fromValue(scope.getValue())).isEqualTo(scope);
  }

  @Test
  void fromValue_get() {
    assertThat(Scope.fromValue("get")).isEqualTo(Scope.GET);
  }

  @Test
  void fromValue_list() {
    assertThat(Scope.fromValue("list")).isEqualTo(Scope.LIST);
  }

  @Test
  void fromValue_post() {
    assertThat(Scope.fromValue("post")).isEqualTo(Scope.POST);
  }

  @Test
  void fromValue_patch() {
    assertThat(Scope.fromValue("patch")).isEqualTo(Scope.PATCH);
  }

  @Test
  void fromValue_delete() {
    assertThat(Scope.fromValue("delete")).isEqualTo(Scope.DELETE);
  }

  @Test
  void fromValue_unknownThrows() {
    assertThatThrownBy(() -> Scope.fromValue("unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void getValue_returnsLowerCase() {
    for (Scope scope : Scope.values()) {
      assertThat(scope.getValue()).isEqualTo(scope.name().toLowerCase());
    }
  }
}
