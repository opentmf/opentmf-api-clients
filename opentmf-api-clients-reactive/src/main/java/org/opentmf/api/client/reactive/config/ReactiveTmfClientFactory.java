package org.opentmf.api.client.reactive.config;

import static org.opentmf.client.common.util.TokenUtil.CLIENT_PROPERTIES;
import static org.opentmf.client.common.util.TokenUtil.TOKEN_SERVICE;
import static org.opentmf.client.common.util.TokenUtil.WEB_CLIENT;

import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.reactive.api.ReactiveTmfClient;
import org.opentmf.api.client.reactive.impl.ReactiveTmfClientImpl;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.reactive.service.api.TokenService;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Factory for creating typed {@link ReactiveTmfClient} instances (Level 2 usage).
 *
 * <p>Example:
 * <pre>{@code
 * @Bean
 * public ReactiveTmfClient<ProductOfferingCreate, ProductOfferingUpdate, ProductOffering>
 *     productOfferingClient(ReactiveTmfClientFactory factory, TmfApiClientsConfig config) {
 *   return factory.create(
 *       config.getApiClients().get("catalog-management"),
 *       "product-offering",
 *       ProductOffering.class);
 * }
 * }</pre>
 */
public class ReactiveTmfClientFactory {

  private final ApplicationContext ctx;

  public ReactiveTmfClientFactory(ApplicationContext ctx) {
    this.ctx = ctx;
  }

  /**
   * Creates a typed reactive client for the given server and endpoint.
   *
   * @param server       server config containing {@code clientRef} (used to look up beans)
   * @param endpointName key within {@code server.getEndpoints()}
   * @param responseType response model class
   */
  public <C, U, R> ReactiveTmfClient<C, U, R> create(
      ServerConfig server, String endpointName, Class<R> responseType) {
    EndpointConfig endpoint = server.getEndpoints().get(endpointName);
    if (endpoint == null) {
      throw new IllegalArgumentException(
          "No endpoint '" + endpointName + "' found in server config.");
    }
    return create(server, endpoint, responseType);
  }

  /**
   * Creates a typed reactive client directly from {@link EndpointConfig}.
   */
  public <C, U, R> ReactiveTmfClient<C, U, R> create(
      ServerConfig server, EndpointConfig endpoint, Class<R> responseType) {
    String clientRef = server.getClientRef();
    WebClient webClient = ctx.getBean(clientRef + WEB_CLIENT, WebClient.class);
    TokenService tokenService = ctx.getBean(clientRef + TOKEN_SERVICE, TokenService.class);
    ClientProperties props = ctx.getBean(clientRef + CLIENT_PROPERTIES, ClientProperties.class);
    return new ReactiveTmfClientImpl<>(endpoint, server, webClient, tokenService, props, responseType);
  }
}
