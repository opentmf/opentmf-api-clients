package org.opentmf.api.client.reactive.impl;

import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.model.SubResourcePath;
import org.opentmf.api.client.reactive.api.GenericReactiveTmfClient;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.reactive.service.api.TokenService;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Generic (untyped) reactive client. Both create/update and response types are {@link Object}.
 */
public class GenericReactiveTmfClientImpl
    extends ReactiveTmfClientImpl<Object, Object, Object>
    implements GenericReactiveTmfClient {

  public GenericReactiveTmfClientImpl(
      EndpointConfig endpointConfig,
      ServerConfig serverConfig,
      WebClient webClient,
      TokenService tokenService,
      ClientProperties clientProperties) {
    super(endpointConfig, serverConfig, webClient, tokenService, clientProperties, Object.class);
  }

  public GenericReactiveTmfClientImpl(
      EndpointConfig endpointConfig,
      ServerConfig serverConfig,
      WebClient webClient,
      TokenService tokenService,
      ClientProperties clientProperties,
      SubResourcePath subPath) {
    super(endpointConfig, serverConfig, webClient, tokenService, clientProperties, Object.class,
        subPath);
  }
}
