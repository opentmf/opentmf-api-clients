package org.opentmf.api.client.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.util.HashMap;
import java.util.Map;

public enum Scope {

  GET("get"),
  LIST("list"),
  POST("post"),
  PATCH("patch"),
  DELETE("delete");

  private final String value;

  Scope(String value) {
    this.value = value;
  }

  @JsonDeserialize
  public String getValue() {
    return value;
  }

  private static final Map<String, Scope> REVERSE_MAP;

  static {
    REVERSE_MAP = new HashMap<>();
    for (Scope scope : Scope.values()) {
      REVERSE_MAP.put(scope.getValue(), scope);
    }
  }

  @JsonCreator
  public static Scope fromValue(String value) {
    Scope scope = REVERSE_MAP.get(value);
    if (scope == null) {
      throw new IllegalArgumentException("Unsupported scope key '" + value + "'");
    }
    return scope;
  }
}
