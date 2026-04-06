package org.opentmf.api.client.rest.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class TmfApiClientsAutoConfigurationTest {

  private BeanDefinitionRegistry registry;
  private StandardEnvironment env;

  @BeforeEach
  void setUp() {
    registry = mock(BeanDefinitionRegistry.class);
    env = new StandardEnvironment();
  }

  private void addEndpoint(Map<String, Object> props, String endpoint, String path) {
    String prefix = "opentmf.api-clients.catalog";
    props.put(prefix + ".client-ref", "default");
    props.put(prefix + ".base-url", "http://localhost");
    props.put(prefix + ".endpoints." + endpoint + ".path", path);
  }

  @Test
  void postProcessor_registersGenericBeanForEachEndpoint() {
    Map<String, Object> props = new LinkedHashMap<>();
    addEndpoint(props, "product", "/products");
    addEndpoint(props, "order", "/orders");
    env.getPropertySources().addFirst(new MapPropertySource("test", props));

    BeanDefinitionRegistryPostProcessor pp =
        TmfApiClientsAutoConfiguration.tmfApiClientsBeanRegistrar(env);
    pp.postProcessBeanDefinitionRegistry(registry);

    verify(registry).registerBeanDefinition(eq("catalog.productTmfClient"), any());
    verify(registry).registerBeanDefinition(eq("catalog.orderTmfClient"), any());
  }

  @Test
  void postProcessor_skipsHubEndpoints() {
    Map<String, Object> props = new LinkedHashMap<>();
    addEndpoint(props, "product", "/products");
    addEndpoint(props, "hub", "/hub");
    env.getPropertySources().addFirst(new MapPropertySource("test", props));

    BeanDefinitionRegistryPostProcessor pp =
        TmfApiClientsAutoConfiguration.tmfApiClientsBeanRegistrar(env);
    pp.postProcessBeanDefinitionRegistry(registry);

    verify(registry).registerBeanDefinition(eq("catalog.productTmfClient"), any());
    verify(registry, never()).registerBeanDefinition(eq("catalog.hubTmfClient"), any());
  }

  @Test
  void postProcessor_skipsAlreadyRegisteredBean() {
    Map<String, Object> props = new LinkedHashMap<>();
    addEndpoint(props, "product", "/products");
    env.getPropertySources().addFirst(new MapPropertySource("test", props));
    when(registry.containsBeanDefinition("catalog.productTmfClient")).thenReturn(true);

    BeanDefinitionRegistryPostProcessor pp =
        TmfApiClientsAutoConfiguration.tmfApiClientsBeanRegistrar(env);
    pp.postProcessBeanDefinitionRegistry(registry);

    verify(registry, never()).registerBeanDefinition(eq("catalog.productTmfClient"), any());
  }

  @Test
  void postProcessor_emptyConfig_registersNothing() {
    BeanDefinitionRegistryPostProcessor pp =
        TmfApiClientsAutoConfiguration.tmfApiClientsBeanRegistrar(env);
    pp.postProcessBeanDefinitionRegistry(registry);

    verify(registry, never()).registerBeanDefinition(any(), any());
  }
}
