package org.opentmf.api.client.hub.config;

import static org.opentmf.client.common.util.TokenUtil.CLIENT_PROPERTIES;
import static org.opentmf.client.common.util.TokenUtil.TOKEN_SERVICE;
import static org.opentmf.client.common.util.TokenUtil.WEB_CLIENT;

import lombok.extern.slf4j.Slf4j;
import org.opentmf.api.client.common.config.TmfApiClientsConfig;
import org.opentmf.api.client.common.util.TmfApiClientConstants;
import org.opentmf.api.client.hub.api.ReactiveTmfHubClient;
import org.opentmf.api.client.hub.impl.ReactiveTmfHubClientImpl;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.reactive.service.api.TokenService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configuration that registers {@link ReactiveTmfHubClient} beans for every configured
 * endpoint whose path ends with {@code /hub}. Uses a static {@link BeanDefinitionRegistryPostProcessor}.
 *
 * <p>Only active when {@link WebClient} is on the classpath.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(WebClient.class)
public class ReactiveTmfHubAutoConfiguration {

  @Bean
  public static BeanDefinitionRegistryPostProcessor reactiveTmfHubClientBeanRegistrar(
      Environment env) {
    return new BeanDefinitionRegistryPostProcessor() {

      @Override
      public void postProcessBeanDefinitionRegistry(@org.jspecify.annotations.NonNull BeanDefinitionRegistry registry)
          throws BeansException {

        TmfApiClientsConfig config = Binder.get(env)
            .bind("opentmf", TmfApiClientsConfig.class)
            .orElse(new TmfApiClientsConfig());

        if (config.getApiClients() == null || config.getApiClients().isEmpty()) {
          return;
        }

        config.getApiClients().forEach((serverName, serverConfig) ->
            serverConfig.getEndpoints().forEach((endpointName, endpointConfig) -> {
              if (!endpointConfig.getPath().endsWith(TmfApiClientConstants.HUB_ENDPOINT_SUFFIX)) {
                return;
              }

              String beanName = serverName + "." + endpointName + "ReactiveTmfHubClient";
              if (registry.containsBeanDefinition(beanName)) {
                log.debug("Bean '{}' already registered, skipping.", beanName);
                return;
              }

              String clientRef = serverConfig.getClientRef();

              RootBeanDefinition bd = new RootBeanDefinition(
                  ReactiveTmfHubClientImpl.class, () -> {
                ConfigurableListableBeanFactory bf = (ConfigurableListableBeanFactory) registry;
                WebClient webClient = bf.getBean(clientRef + WEB_CLIENT, WebClient.class);
                TokenService tokenService =
                    bf.getBean(clientRef + TOKEN_SERVICE, TokenService.class);
                ClientProperties props =
                    bf.getBean(clientRef + CLIENT_PROPERTIES, ClientProperties.class);
                return new ReactiveTmfHubClientImpl(
                    endpointConfig, serverConfig, webClient, tokenService, props);
              });
              bd.setDependsOn("opentmfHttpClientsStarter");
              registry.registerBeanDefinition(beanName, bd);
              log.debug("Registered ReactiveTmfHubClient bean definition '{}'.", beanName);
            }));
      }

      @Override
      public void postProcessBeanFactory(@org.jspecify.annotations.NonNull ConfigurableListableBeanFactory beanFactory)
          throws BeansException {
        // no-op
      }
    };
  }
}
