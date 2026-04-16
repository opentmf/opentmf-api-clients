package org.opentmf.api.client.rest.config;

import static org.opentmf.client.common.util.TokenUtil.CLIENT_PROPERTIES;
import static org.opentmf.client.common.util.TokenUtil.REST_CLIENT;
import static org.opentmf.client.common.util.TokenUtil.TOKEN_SERVICE;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.opentmf.api.client.common.config.TmfApiClientsConfig;
import org.opentmf.api.client.common.util.TmfApiClientConstants;
import org.opentmf.api.client.rest.impl.GenericTmfClientImpl;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot auto-configuration for the synchronous (REST) TMF API client module.
 *
 * <p>Uses a {@code static} {@link BeanDefinitionRegistryPostProcessor} to register
 * {@link org.opentmf.api.client.common.api.GenericTmfClient} bean definitions during the
 * post-processing phase — before any regular beans are created. This closes the "phase gap"
 * between component-scanned beans and dynamically-registered beans.
 *
 * <p>Each bean definition uses a lazy supplier that resolves HTTP client dependencies
 * ({@code RestClient}, {@code SyncTokenService}, {@code ClientProperties}) at creation time,
 * not at registration time.
 */
@Slf4j
@AutoConfiguration
public class TmfApiClientsAutoConfiguration {

  @Bean
  public static BeanDefinitionRegistryPostProcessor tmfApiClientsBeanRegistrar(Environment env) {
    return new BeanDefinitionRegistryPostProcessor() {

      @Override
      public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry)
          throws BeansException {

        TmfApiClientsConfig config = Binder.get(env)
            .bind("opentmf", TmfApiClientsConfig.class)
            .orElse(new TmfApiClientsConfig());

        if (config.getApiClients() == null || config.getApiClients().isEmpty()) {
          log.debug("No opentmf.api-clients configured.");
          return;
        }

        config.getApiClients().forEach((serverName, serverConfig) ->
            serverConfig.getEndpoints().forEach((endpointName, endpointConfig) -> {
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

              // Lazy supplier: resolves HTTP client beans at creation time, not now
              RootBeanDefinition bd = new RootBeanDefinition(GenericTmfClientImpl.class, () -> {
                ConfigurableListableBeanFactory bf = (ConfigurableListableBeanFactory) registry;
                RestClient restClient = bf.getBean(clientRef + REST_CLIENT, RestClient.class);
                SyncTokenService tokenService =
                    bf.getBean(clientRef + TOKEN_SERVICE, SyncTokenService.class);
                ClientProperties props =
                    bf.getBean(clientRef + CLIENT_PROPERTIES, ClientProperties.class);
                return new GenericTmfClientImpl(
                    endpointConfig, serverConfig, restClient, tokenService, props);
              });
              bd.setDependsOn("opentmfHttpClientsStarter");
              registry.registerBeanDefinition(beanName, bd);
              log.debug("Registered TMF client bean definition '{}'.", beanName);
            }));
      }

      @Override
      public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory)
          throws BeansException {
        // no-op
      }
    };
  }

  @Bean
  public TmfClientFactory tmfClientFactory(ConfigurableApplicationContext ctx) {
    return new TmfClientFactory(ctx);
  }
}
