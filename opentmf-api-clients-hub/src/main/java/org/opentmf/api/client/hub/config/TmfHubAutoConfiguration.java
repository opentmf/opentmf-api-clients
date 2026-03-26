package org.opentmf.api.client.hub.config;

import static org.opentmf.client.common.util.TokenUtil.CLIENT_PROPERTIES;
import static org.opentmf.client.common.util.TokenUtil.REST_CLIENT;
import static org.opentmf.client.common.util.TokenUtil.TOKEN_SERVICE;

import lombok.extern.slf4j.Slf4j;
import org.opentmf.api.client.common.config.TmfApiClientsConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.util.TmfApiClientConstants;
import org.opentmf.api.client.hub.api.TmfHubClient;
import org.opentmf.api.client.hub.impl.TmfHubClientImpl;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestClient;

/**
 * Auto-configuration that registers {@link TmfHubClient} beans for every configured endpoint whose
 * path ends with {@code /hub}.
 *
 * <p>Only active when {@link RestClient} is on the classpath.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@EnableConfigurationProperties(TmfApiClientsConfig.class)
public class TmfHubAutoConfiguration {

  public TmfHubAutoConfiguration(
      TmfApiClientsConfig config,
      ApplicationContext ctx,
      BeanDefinitionRegistry registry) {

    config.getApiClients().forEach((serverName, serverConfig) ->
        serverConfig.getEndpoints().forEach((endpointName, endpointConfig) -> {
          if (endpointConfig.getPath().endsWith(TmfApiClientConstants.HUB_ENDPOINT_SUFFIX)) {
            registerHubBean(registry, ctx, serverName, endpointName, serverConfig, endpointConfig);
          }
        }));
  }

  private void registerHubBean(
      BeanDefinitionRegistry registry,
      ApplicationContext ctx,
      String serverName,
      String endpointName,
      ServerConfig serverConfig,
      EndpointConfig endpointConfig) {

    String beanName = serverName + "." + endpointName + "TmfHubClient";
    if (registry.containsBeanDefinition(beanName)) {
      log.debug("Bean '{}' already registered, skipping.", beanName);
      return;
    }

    String clientRef = serverConfig.getClientRef();
    RestClient restClient = ctx.getBean(clientRef + REST_CLIENT, RestClient.class);
    SyncTokenService tokenService = ctx.getBean(clientRef + TOKEN_SERVICE, SyncTokenService.class);
    ClientProperties props = ctx.getBean(clientRef + CLIENT_PROPERTIES, ClientProperties.class);

    TmfHubClient hubClient =
        new TmfHubClientImpl(endpointConfig, serverConfig, restClient, tokenService, props);

    BeanDefinition bd = new RootBeanDefinition(TmfHubClient.class, () -> hubClient);
    registry.registerBeanDefinition(beanName, bd);
    log.debug("Registered TmfHubClient bean '{}'.", beanName);
  }
}
