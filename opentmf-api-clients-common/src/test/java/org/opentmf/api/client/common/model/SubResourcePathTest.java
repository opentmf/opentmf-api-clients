package org.opentmf.api.client.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SubResourcePathTest {

  private static final String TEMPLATE = "/{orderId}/action/{action}/item";

  @Test
  void none_isEmpty() {
    assertThat(SubResourcePath.none().isEmpty()).isTrue();
    assertThat(SubResourcePath.none().template()).isEmpty();
    assertThat(SubResourcePath.none().vars()).isEmpty();
  }

  @Test
  void append_bindsTemplateAndVars() {
    SubResourcePath sub = SubResourcePath.none().append(TEMPLATE, "o1", "cancel");
    assertThat(sub.isEmpty()).isFalse();
    assertThat(sub.template()).isEqualTo(TEMPLATE);
    assertThat(sub.vars()).containsExactly("o1", "cancel");
  }

  @Test
  void varsWith_appendsTerminalId() {
    SubResourcePath sub = SubResourcePath.none().append(TEMPLATE, "o1", "cancel");
    assertThat(sub.varsWith("it7")).containsExactly("o1", "cancel", "it7");
  }

  @Test
  void append_tooFewVars_throwsNamingTemplateAndCounts() {
    SubResourcePath none = SubResourcePath.none();
    assertThatThrownBy(() -> none.append(TEMPLATE, "o1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(TEMPLATE)
        .hasMessageContaining("2")
        .hasMessageContaining("1");
  }

  @Test
  void append_tooManyVars_throwsInsteadOfSilentTruncation() {
    SubResourcePath none = SubResourcePath.none();
    assertThatThrownBy(() -> none.append(TEMPLATE, "o1", "cancel", "EXTRA"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(TEMPLATE)
        .hasMessageContaining("2")
        .hasMessageContaining("3");
  }

  @Test
  void append_nullTemplate_throws() {
    SubResourcePath none = SubResourcePath.none();
    assertThatThrownBy(() -> none.append(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void append_blankTemplate_throws() {
    SubResourcePath none = SubResourcePath.none();
    assertThatThrownBy(() -> none.append("  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void append_slashOnlyTemplate_throws() {
    SubResourcePath none = SubResourcePath.none();
    assertThatThrownBy(() -> none.append("/"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void append_nullVarElement_throws() {
    SubResourcePath none = SubResourcePath.none();
    assertThatThrownBy(() -> none.append("/{a}/x/{b}", "v", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null");
  }

  @Test
  void append_regexPlaceholder_throws() {
    SubResourcePath none = SubResourcePath.none();
    assertThatThrownBy(() -> none.append("/{id:[abc]+}/x", "v"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("regex");
  }

  @Test
  void append_regexPlaceholderWithSlash_throwsViaBraceCheck() {
    SubResourcePath none = SubResourcePath.none();
    assertThatThrownBy(() -> none.append("/{id:[^/]+}/x", "v"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void append_unbalancedBrace_throws() {
    SubResourcePath none = SubResourcePath.none();
    assertThatThrownBy(() -> none.append("/{orderId"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("brace");
  }

  @Test
  void append_normalizesLeadingAndTrailingSlashes() {
    SubResourcePath a = SubResourcePath.none().append("orderId/action");
    SubResourcePath b = SubResourcePath.none().append("/orderId/action");
    SubResourcePath c = SubResourcePath.none().append("/orderId/action/");
    assertThat(a.template()).isEqualTo("/orderId/action");
    assertThat(b.template()).isEqualTo("/orderId/action");
    assertThat(c.template()).isEqualTo("/orderId/action");
  }

  @Test
  void append_isImmutableAndChainsInOrder() {
    SubResourcePath first = SubResourcePath.none().append("/{orderId}/action", "o1");
    SubResourcePath second = first.append("/{action}/item", "cancel");

    assertThat(first.template()).isEqualTo("/{orderId}/action");
    assertThat(first.vars()).containsExactly("o1");
    assertThat(second.template()).isEqualTo("/{orderId}/action/{action}/item");
    assertThat(second.vars()).containsExactly("o1", "cancel");
  }

  @Test
  void append_constantTemplateWithoutPlaceholders_isAllowed() {
    SubResourcePath sub = SubResourcePath.none().append("/summary");
    assertThat(sub.template()).isEqualTo("/summary");
    assertThat(sub.vars()).isEmpty();
  }
}
