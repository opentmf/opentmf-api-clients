package org.opentmf.api.client.common.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable accumulator for a sub-resource path suffix, appended after an endpoint's configured
 * path. Holds a URI template (e.g. {@code "/{orderId}/action/{action}/item"}) together with the
 * values bound to its placeholders. Values are expanded as URI template variables — never
 * concatenated into the path string — so they are strictly percent-encoded and cannot inject
 * path segments.
 *
 * <p>All validation happens eagerly in {@link #append(String, Object...)}, before any request is
 * issued.
 */
public final class SubResourcePath {

  private static final SubResourcePath NONE = new SubResourcePath("", List.of());
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^/{}]+)}");

  private final String template;
  private final List<Object> vars;

  private SubResourcePath(String template, List<Object> vars) {
    this.template = template;
    this.vars = vars;
  }

  /**
   * The empty suffix: URI construction behaves exactly as without a sub-resource path.
   *
   * @return the shared empty instance
   */
  public static SubResourcePath none() {
    return NONE;
  }

  /**
   * Returns a new instance with {@code template} appended and {@code vars} bound to its
   * placeholders, in order. The receiver is unchanged.
   *
   * <p><b>The template must be a compile-time constant.</b> Never build it by concatenating
   * runtime data; every runtime value belongs in {@code vars}. Only simple {@code {name}}
   * placeholders are supported — {@code {name:regex}} is rejected.
   *
   * @param template the constant path template to append, e.g. {@code "/{orderId}/action"}
   * @param vars one value per {@code {name}} placeholder, in order of occurrence
   * @return a new instance with the accumulated template and variables
   * @throws IllegalArgumentException if the template is null, blank, or has no path segment;
   *     contains a {@code {name:regex}} placeholder; contains unbalanced or nested braces; if the
   *     placeholder count differs from {@code vars.length}; or if any element of {@code vars} is
   *     null
   */
  public SubResourcePath append(String template, Object... vars) {
    if (template == null || template.isBlank()) {
      throw new IllegalArgumentException("Sub-resource template must not be null or blank.");
    }
    String normalized = template.startsWith("/") ? template : "/" + template;
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(
          "Sub-resource template must contain at least one path segment: '" + template + "'");
    }
    int placeholders = 0;
    Matcher matcher = PLACEHOLDER.matcher(normalized);
    while (matcher.find()) {
      if (matcher.group(1).contains(":")) {
        throw new IllegalArgumentException(
            "Only simple {name} placeholders are supported; '{" + matcher.group(1)
                + "}' looks like a {name:regex} placeholder: '" + template + "'");
      }
      placeholders++;
    }
    String remainder = matcher.reset().replaceAll("");
    if (remainder.indexOf('{') >= 0 || remainder.indexOf('}') >= 0) {
      throw new IllegalArgumentException(
          "Unbalanced or nested braces in sub-resource template: '" + template + "'");
    }
    Object[] values = vars != null ? vars : new Object[0];
    if (placeholders != values.length) {
      throw new IllegalArgumentException(
          "Sub-resource template '" + template + "' declares " + placeholders
              + " placeholder(s) but " + values.length + " variable(s) were supplied.");
    }
    List<Object> merged = new ArrayList<>(this.vars.size() + values.length);
    merged.addAll(this.vars);
    for (Object value : values) {
      if (value == null) {
        throw new IllegalArgumentException(
            "Sub-resource template variables must not be null: '" + template + "'");
      }
      merged.add(value);
    }
    return new SubResourcePath(this.template + normalized, List.copyOf(merged));
  }

  public boolean isEmpty() {
    return template.isEmpty();
  }

  public String template() {
    return template;
  }

  public Object[] vars() {
    return vars.toArray();
  }

  /**
   * The accumulated variables plus the terminal resource id, for item-level URIs.
   *
   * @param id the terminal resource id, expanded into the trailing {@code /{id}} segment
   * @return the accumulated variables with {@code id} appended
   */
  public Object[] varsWith(String id) {
    Object[] result = new Object[vars.size() + 1];
    for (int i = 0; i < vars.size(); i++) {
      result[i] = vars.get(i);
    }
    result[vars.size()] = id;
    return result;
  }
}
