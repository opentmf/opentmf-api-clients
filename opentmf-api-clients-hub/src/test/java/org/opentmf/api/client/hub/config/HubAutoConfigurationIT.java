package org.opentmf.api.client.hub.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class HubAutoConfigurationIT {

  private BeanDefinitionRegistry registry;
  private StandardEnvironment env;

  @BeforeEach
  void setUp() {
    registry = mock(BeanDefinitionRegistry.class);
    env = new StandardEnvironment();

    Map<String, Object> props = new LinkedHashMap<>();
    String prefix = "opentmf.api-clients.catalog-management";
    props.put(prefix + ".client-ref", "default");
    props.put(prefix + ".base-url", "http://localhost:8080");
    props.put(prefix + ".context-path", "/tmf-api/productCatalogManagement/v4");
    props.put(prefix + ".endpoints.hub.path", "/hub");
    props.put(prefix + ".endpoints.product-offering.path", "/productOffering");
    env.getPropertySources().addFirst(new MapPropertySource("test", props));
  }

  @Test
  void syncHubRegistrar_registersHubBean_skipsNonHub() {
    BeanDefinitionRegistryPostProcessor pp =
        TmfHubAutoConfiguration.tmfHubClientBeanRegistrar(env);
    pp.postProcessBeanDefinitionRegistry(registry);

    verify(registry).registerBeanDefinition(
        eq("catalog-management.hubTmfHubClient"), any(BeanDefinition.class));
    verify(registry, never()).registerBeanDefinition(
        eq("catalog-management.product-offeringTmfHubClient"), any(BeanDefinition.class));
  }

  @Test
  void reactiveHubRegistrar_registersHubBean_skipsNonHub() {
    BeanDefinitionRegistryPostProcessor pp =
        ReactiveTmfHubAutoConfiguration.reactiveTmfHubClientBeanRegistrar(env);
    pp.postProcessBeanDefinitionRegistry(registry);

    verify(registry).registerBeanDefinition(
        eq("catalog-management.hubReactiveTmfHubClient"), any(BeanDefinition.class));
    verify(registry, never()).registerBeanDefinition(
        eq("catalog-management.product-offeringReactiveTmfHubClient"), any(BeanDefinition.class));
  }

  @Test
  void syncHubRegistrar_noApiClients_registersNothing() {
    var emptyEnv = new StandardEnvironment();
    BeanDefinitionRegistryPostProcessor pp =
        TmfHubAutoConfiguration.tmfHubClientBeanRegistrar(emptyEnv);
    pp.postProcessBeanDefinitionRegistry(registry);
    verify(registry, never()).registerBeanDefinition(
        anyString(), any(BeanDefinition.class));
  }

  @Test
  void reactiveHubRegistrar_noApiClients_registersNothing() {
    var emptyEnv = new StandardEnvironment();
    BeanDefinitionRegistryPostProcessor pp =
        ReactiveTmfHubAutoConfiguration.reactiveTmfHubClientBeanRegistrar(emptyEnv);
    pp.postProcessBeanDefinitionRegistry(registry);
    verify(registry, never()).registerBeanDefinition(
        anyString(), any(BeanDefinition.class));
  }

  @Test
  void syncHubRegistrar_skipsWhenAlreadyRegistered() {
    when(
            registry.containsBeanDefinition("catalog-management.hubTmfHubClient"))
        .thenReturn(true);
    BeanDefinitionRegistryPostProcessor pp =
        TmfHubAutoConfiguration.tmfHubClientBeanRegistrar(env);
    pp.postProcessBeanDefinitionRegistry(registry);
    verify(registry, never()).registerBeanDefinition(
        eq("catalog-management.hubTmfHubClient"), any(BeanDefinition.class));
  }

  @Test
  void reactiveHubRegistrar_skipsWhenAlreadyRegistered() {
    when(
            registry.containsBeanDefinition("catalog-management.hubReactiveTmfHubClient"))
        .thenReturn(true);
    BeanDefinitionRegistryPostProcessor pp =
        ReactiveTmfHubAutoConfiguration.reactiveTmfHubClientBeanRegistrar(env);
    pp.postProcessBeanDefinitionRegistry(registry);
    verify(registry, never()).registerBeanDefinition(
        eq("catalog-management.hubReactiveTmfHubClient"), any(BeanDefinition.class));
  }

  @Test
  void postProcessBeanFactory_isNoOp() {
    var beanFactory = mock(ConfigurableListableBeanFactory.class);
    TmfHubAutoConfiguration.tmfHubClientBeanRegistrar(env).postProcessBeanFactory(beanFactory);
    ReactiveTmfHubAutoConfiguration.reactiveTmfHubClientBeanRegistrar(env).postProcessBeanFactory(beanFactory);
    verifyNoInteractions(beanFactory);
  }
}
