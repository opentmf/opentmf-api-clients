package org.opentmf.api.client.rest.config;

import static org.opentmf.client.common.util.TokenUtil.CLIENT_PROPERTIES;
import static org.opentmf.client.common.util.TokenUtil.REST_CLIENT;
import static org.opentmf.client.common.util.TokenUtil.TOKEN_SERVICE;

import org.opentmf.api.client.common.api.TmfClient;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.rest.impl.TmfClientImpl;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestClient;

/**
 * Factory for creating typed {@link TmfClient} instances (Level 2 usage).
 *
 * <p>Example:
 * <pre>{@code
 * @Bean
 * public TmfClient<ProductOfferingCreate, ProductOfferingUpdate, ProductOffering>
 *     productOfferingClient(TmfClientFactory factory, TmfApiClientsConfig config) {
 *   return factory.create(
 *       config.getApiClients().get("catalog-management"),
 *       "product-offering",
 *       ProductOffering.class);
 * }
 * }</pre>
 */
public class TmfClientFactory {

  private final ApplicationContext ctx;

  public TmfClientFactory(ApplicationContext ctx) {
    this.ctx = ctx;
  }

  /**
   * Creates a typed synchronous client for the given server and endpoint.
   */
  public <C, U, R> TmfClient<C, U, R> create(
      ServerConfig server, String endpointName, Class<R> responseType) {
    EndpointConfig endpoint = server.getEndpoints().get(endpointName);
    if (endpoint == null) {
      throw new IllegalArgumentException(
          "No endpoint '" + endpointName + "' found in server config.");
    }
    return create(server, endpoint, responseType);
  }

  /**
   * Creates a typed synchronous client directly from {@link EndpointConfig}.
   */
  public <C, U, R> TmfClient<C, U, R> create(
      ServerConfig server, EndpointConfig endpoint, Class<R> responseType) {
    String clientRef = server.getClientRef();
    RestClient restClient = ctx.getBean(clientRef + REST_CLIENT, RestClient.class);
    SyncTokenService tokenService = ctx.getBean(clientRef + TOKEN_SERVICE, SyncTokenService.class);
    ClientProperties props = ctx.getBean(clientRef + CLIENT_PROPERTIES, ClientProperties.class);
    return new TmfClientImpl<>(endpoint, server, restClient, tokenService, props, responseType);
  }
}
