package org.opentmf.api.client.reactive.config;

import static org.opentmf.client.common.util.TokenUtil.CLIENT_PROPERTIES;
import static org.opentmf.client.common.util.TokenUtil.TOKEN_SERVICE;
import static org.opentmf.client.common.util.TokenUtil.WEB_CLIENT;

import lombok.extern.slf4j.Slf4j;
import org.opentmf.api.client.common.config.TmfApiClientsConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.util.TmfApiClientConstants;
import org.opentmf.api.client.reactive.api.GenericReactiveTmfClient;
import org.opentmf.api.client.reactive.impl.GenericReactiveTmfClientImpl;import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.reactive.service.api.TokenService;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring Boot auto-configuration for the reactive TMF API client module.
 *
 * <p>For each configured server/endpoint pair, registers a {@link GenericReactiveTmfClient} bean
 * named {@code {serverName}.{endpointName}TmfClient}. Also registers a
 * {@link ReactiveTmfClientFactory} bean for Level 2 (typed) usage.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(TmfApiClientsConfig.class)
public class ReactiveTmfApiClientsAutoConfiguration {

  public ReactiveTmfApiClientsAutoConfiguration(
      TmfApiClientsConfig config,
      ApplicationContext ctx,
      BeanDefinitionRegistry registry) {

    config.getApiClients().forEach((serverName, serverConfig) ->
        serverConfig.getEndpoints().forEach((endpointName, endpointConfig) ->
            registerGenericBean(registry, ctx, serverName, endpointName, serverConfig, endpointConfig)));
  }

  @Bean
  public ReactiveTmfClientFactory reactiveTmfClientFactory(ApplicationContext ctx) {
    return new ReactiveTmfClientFactory(ctx);
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
    WebClient webClient = ctx.getBean(clientRef + WEB_CLIENT, WebClient.class);
    TokenService tokenService = ctx.getBean(clientRef + TOKEN_SERVICE, TokenService.class);
    ClientProperties props = ctx.getBean(clientRef + CLIENT_PROPERTIES, ClientProperties.class);

    GenericReactiveTmfClient client =
        new GenericReactiveTmfClientImpl(endpointConfig, serverConfig, webClient, tokenService, props);

    BeanDefinition bd = new RootBeanDefinition(GenericReactiveTmfClient.class,
        () -> client);
    registry.registerBeanDefinition(beanName, bd);
    log.debug("Registered reactive TMF client bean '{}'.", beanName);
  }
}
