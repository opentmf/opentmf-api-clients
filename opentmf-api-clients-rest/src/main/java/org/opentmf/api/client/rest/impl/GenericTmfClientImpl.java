package org.opentmf.api.client.rest.impl;

import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.rest.api.RestGenericTmfClient;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.springframework.web.client.RestClient;

/**
 * Generic (untyped) synchronous client. Both create/update and response types are {@link Object}.
 */
public class GenericTmfClientImpl
    extends TmfClientImpl<Object, Object, Object>
    implements RestGenericTmfClient {

  public GenericTmfClientImpl(
      EndpointConfig endpointConfig,
      ServerConfig serverConfig,
      RestClient restClient,
      SyncTokenService tokenService,
      ClientProperties clientProperties) {
    super(endpointConfig, serverConfig, restClient, tokenService, clientProperties, Object.class);
  }
}
