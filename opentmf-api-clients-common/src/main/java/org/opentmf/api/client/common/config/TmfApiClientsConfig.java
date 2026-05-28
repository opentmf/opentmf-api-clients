package org.opentmf.api.client.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.opentmf.api.client.common.model.Scope;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for opentmf-api-clients.
 *
 * <p>Example YAML:
 * <pre>{@code
 * opentmf:
 *   api-clients:
 *     order-management:
 *       client-ref: default
 *       base-url: http://order-service:8080
 *       context-path: /tmf-api/productOrderingManagement/v4
 *       endpoints:
 *         product-order:
 *           path: /productOrder
 *           scopes:
 *             get: GET_ORDER
 *             list: LIST_ORDER
 *             post: POST_ORDER
 *             put: PUT_ORDER
 *             patch: PATCH_ORDER
 *             delete: DELETE_ORDER
 * }</pre>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "opentmf")
public class TmfApiClientsConfig {

  @Valid
  @NotEmpty
  private Map<String, ServerConfig> apiClients;

  @Valid
  @Getter
  @Setter
  public static class ServerConfig {

    /** References an entry in {@code opentmf.http-clients.<id>}. */
    @NotBlank
    private String clientRef;

    @NotBlank
    private String baseUrl;

    private String contextPath = "";

    /**
     * Optional override of the client-type defined in the referenced http-client entry.
     * When set, takes precedence over the global and per-http-client type settings.
     */
    private String clientType;

    /**
     * Optional map of headers that will be added to every request made by this client.
     * Applied in addition to the Authorization header and any request-context headers.
     */
    private Map<String, String> fixedHeaders;

    @Valid
    @NotEmpty
    private Map<String, EndpointConfig> endpoints;
  }

  @Getter
  @Setter
  public static class EndpointConfig {

    @NotBlank
    private String path;

    private Map<Scope, String> scopes = new EnumMap<>(Scope.class);

    /**
     * Optional map of headers that will be added to every request made against this endpoint.
     * Merged with {@link ServerConfig#getFixedHeaders()}; on key collisions, endpoint-level
     * entries win over server-level ones.
     */
    private Map<String, String> fixedHeaders;
  }
}
