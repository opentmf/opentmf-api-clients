package org.opentmf.api.client.common.model;

import io.micrometer.common.util.StringUtils;
import lombok.Getter;

/**
 * Represents a JSON-based filter expression.
 * The filter can be evaluated server-side (sent as a query param) or client-side (applied locally
 * via JsonPath after receiving the response).
 */
@Getter
public class JsonFilter {

  private final String query;
  private final TYPE type;

  private JsonFilter(String query, TYPE type) {
    if (StringUtils.isBlank(query)) {
      throw new IllegalArgumentException("JsonFilter query must not be null or empty.");
    }
    this.query = query;
    this.type = type;
  }

  public static JsonFilter of(String query, TYPE type) {
    return new JsonFilter(query, type);
  }

  /** Creates a server-side filter (the default). */
  public static JsonFilter of(String query) {
    return new JsonFilter(query, TYPE.SERVER);
  }

  public enum TYPE {
    /** Filter is forwarded to the server as a query parameter. */
    SERVER,
    /** Filter is applied locally on the client after receiving the response. */
    CLIENT
  }
}
