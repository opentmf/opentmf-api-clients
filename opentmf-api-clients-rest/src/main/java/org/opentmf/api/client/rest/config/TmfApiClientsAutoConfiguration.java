package org.opentmf.api.client.rest.config;

import static org.opentmf.client.common.util.TokenUtil.CLIENT_PROPERTIES;
import static org.opentmf.client.common.util.TokenUtil.REST_CLIENT;
import static org.opentmf.client.common.util.TokenUtil.TOKEN_SERVICE;

import lombok.extern.slf4j.Slf4j;
import org.opentmf.api.client.common.config.TmfApiClientsConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.util.TmfApiClientConstants;
import org.opentmf.api.client.rest.impl.GenericTmfClientImpl;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot auto-configuration for the synchronous (REST) TMF API client module.
 *
 * <p>For each configured server/endpoint pair, registers a {@link org.opentmf.api.client.common.api.GenericTmfClient}
 * bean named {@code {serverName}.{endpointName}TmfClient}. Also registers a
 * {@link TmfClientFactory} bean for Level 2 (typed) usage.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(TmfApiClientsConfig.class)
public class TmfApiClientsAutoConfiguration {

  public TmfApiClientsAutoConfiguration(
      TmfApiClientsConfig config,
      ApplicationContext ctx,
      BeanDefinitionRegistry registry) {

    config.getApiClients().forEach((serverName, serverConfig) ->
        serverConfig.getEndpoints().forEach((endpointName, endpointConfig) ->
            registerGenericBean(registry, ctx, serverName, endpointName, serverConfig, endpointConfig)));
  }

  @Bean
  public TmfClientFactory tmfClientFactory(ApplicationContext ctx) {
    return new TmfClientFactory(ctx);
  }

  private void registerGenericBean(
      BeanDefinitionRegistry registry,
      ApplicationContext ctx,
      String serverName,
      String endpointName,
      ServerConfig serverConfig,
      EndpointConfig endpointConfig) {

    if (endpointConfig.getPath().endsWith(TmfApiClientConstants.HUB_ENDPOINT_SUFFIX)) {
      log.debug("Skipping generic bean for hub endpoint '{}.{}'.", serverName, endpointName);
      return;
    }

    String beanName = serverName + "." + endpointName + "TmfClient";
    if (registry.containsBeanDefinition(beanName)) {
      log.debug("Bean '{}' already registered, skipping.", beanName);
      return;
    }

    String clientRef = serverConfig.getClientRef();
    RestClient restClient = ctx.getBean(clientRef + REST_CLIENT, RestClient.class);
    SyncTokenService tokenService = ctx.getBean(clientRef + TOKEN_SERVICE, SyncTokenService.class);
    ClientProperties props = ctx.getBean(clientRef + CLIENT_PROPERTIES, ClientProperties.class);

    GenericTmfClientImpl client =
        new GenericTmfClientImpl(endpointConfig, serverConfig, restClient, tokenService, props);

    BeanDefinition bd = new RootBeanDefinition(GenericTmfClientImpl.class, () -> client);
    registry.registerBeanDefinition(beanName, bd);
    log.debug("Registered TMF client bean '{}'.", beanName);
  }
}
