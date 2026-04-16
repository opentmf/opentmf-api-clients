package org.opentmf.api.client.hub.config;

import static org.opentmf.client.common.util.TokenUtil.CLIENT_PROPERTIES;
import static org.opentmf.client.common.util.TokenUtil.REST_CLIENT;
import static org.opentmf.client.common.util.TokenUtil.TOKEN_SERVICE;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.opentmf.api.client.common.config.TmfApiClientsConfig;
import org.opentmf.api.client.common.util.TmfApiClientConstants;
import org.opentmf.api.client.hub.api.TmfHubClient;
import org.opentmf.api.client.hub.impl.TmfHubClientImpl;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
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
import org.springframework.web.client.RestClient;

/**
 * Auto-configuration that registers {@link TmfHubClient} beans for every configured endpoint whose
 * path ends with {@code /hub}. Uses a static {@link BeanDefinitionRegistryPostProcessor} to ensure
 * bean definitions are registered before component-scanned beans are created.
 *
 * <p>Only active when {@link RestClient} is on the classpath.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
public class TmfHubAutoConfiguration {

  @Bean
  public static BeanDefinitionRegistryPostProcessor tmfHubClientBeanRegistrar(Environment env) {
    return new BeanDefinitionRegistryPostProcessor() {

      @Override
      public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry)
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

              String beanName = serverName + "." + endpointName + "TmfHubClient";
              if (registry.containsBeanDefinition(beanName)) {
                log.debug("Bean '{}' already registered, skipping.", beanName);
                return;
              }

              String clientRef = serverConfig.getClientRef();

              RootBeanDefinition bd = new RootBeanDefinition(TmfHubClientImpl.class, () -> {
                ConfigurableListableBeanFactory bf = (ConfigurableListableBeanFactory) registry;
                RestClient restClient = bf.getBean(clientRef + REST_CLIENT, RestClient.class);
                SyncTokenService tokenService =
                    bf.getBean(clientRef + TOKEN_SERVICE, SyncTokenService.class);
                ClientProperties props =
                    bf.getBean(clientRef + CLIENT_PROPERTIES, ClientProperties.class);
                return new TmfHubClientImpl(
                    endpointConfig, serverConfig, restClient, tokenService, props);
              });
              bd.setDependsOn("opentmfHttpClientsStarter");
              registry.registerBeanDefinition(beanName, bd);
              log.debug("Registered TmfHubClient bean definition '{}'.", beanName);
            }));
      }

      @Override
      public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory)
          throws BeansException {
        // no-op
      }
    };
  }
}
