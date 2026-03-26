package org.opentmf.api.client.hub.config;

import static org.opentmf.client.common.util.TokenUtil.CLIENT_PROPERTIES;
import static org.opentmf.client.common.util.TokenUtil.TOKEN_SERVICE;
import static org.opentmf.client.common.util.TokenUtil.WEB_CLIENT;

import lombok.extern.slf4j.Slf4j;
import org.opentmf.api.client.common.config.TmfApiClientsConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.util.TmfApiClientConstants;
import org.opentmf.api.client.hub.api.ReactiveTmfHubClient;
import org.opentmf.api.client.hub.impl.ReactiveTmfHubClientImpl;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.reactive.service.api.TokenService;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration that registers {@link ReactiveTmfHubClient} beans for every configured
 * endpoint whose path ends with {@code /hub}.
 *
 * <p>Only active when {@link WebClient} is on the classpath.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(WebClient.class)
@EnableConfigurationProperties(TmfApiClientsConfig.class)
public class ReactiveTmfHubAutoConfiguration {

  public ReactiveTmfHubAutoConfiguration(
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

    String beanName = serverName + "." + endpointName + "ReactiveTmfHubClient";
    if (registry.containsBeanDefinition(beanName)) {
      log.debug("Bean '{}' already registered, skipping.", beanName);
      return;
    }

    String clientRef = serverConfig.getClientRef();
    WebClient webClient = ctx.getBean(clientRef + WEB_CLIENT, WebClient.class);
    TokenService tokenService = ctx.getBean(clientRef + TOKEN_SERVICE, TokenService.class);
    ClientProperties props = ctx.getBean(clientRef + CLIENT_PROPERTIES, ClientProperties.class);

    ReactiveTmfHubClient hubClient =
        new ReactiveTmfHubClientImpl(endpointConfig, serverConfig, webClient, tokenService, props);

    BeanDefinition bd = new RootBeanDefinition(ReactiveTmfHubClient.class, () -> hubClient);
    registry.registerBeanDefinition(beanName, bd);
    log.debug("Registered ReactiveTmfHubClient bean '{}'.", beanName);
  }
}
