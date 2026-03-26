package org.opentmf.api.client.hub.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentmf.api.client.common.config.TmfApiClientsConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.model.Scope;
import org.opentmf.api.client.hub.helper.MockSyncTokenService;
import org.opentmf.api.client.hub.helper.MockTokenService;
import org.opentmf.client.common.model.ClientProperties;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

class HubAutoConfigurationIT {

  private ApplicationContext ctx;
  private BeanDefinitionRegistry registry;
  private TmfApiClientsConfig config;

  @BeforeEach
  void setUp() {
    ctx = mock(ApplicationContext.class);
    registry = mock(BeanDefinitionRegistry.class);

    ClientProperties props = new ClientProperties();
    props.setNumRetries(0);
    props.setRetryWaitDuration(Duration.ofMillis(100));

    RestClient restClient = RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory()).build();
    WebClient webClient = WebClient.builder().build();

    when(ctx.getBean("defaultRestClient", RestClient.class)).thenReturn(restClient);
    when(ctx.getBean("defaultTokenService",
        org.opentmf.client.rest.service.api.SyncTokenService.class))
        .thenReturn(new MockSyncTokenService());
    when(ctx.getBean("defaultClientProperties", ClientProperties.class)).thenReturn(props);

    when(ctx.getBean("defaultWebClient", WebClient.class)).thenReturn(webClient);
    when(ctx.getBean("defaultTokenService",
        org.opentmf.client.reactive.service.api.TokenService.class))
        .thenReturn(new MockTokenService());

    config = new TmfApiClientsConfig();
    Map<String, ServerConfig> apiClients = new LinkedHashMap<>();

    ServerConfig serverConfig = new ServerConfig();
    serverConfig.setClientRef("default");
    serverConfig.setBaseUrl("http://localhost:8080");
    serverConfig.setContextPath("/tmf-api/productCatalogManagement/v4");

    Map<String, EndpointConfig> endpoints = new LinkedHashMap<>();

    EndpointConfig hubEndpoint = new EndpointConfig();
    hubEndpoint.setPath("/hub");
    Map<Scope, String> scopes = new EnumMap<>(Scope.class);
    scopes.put(Scope.POST, "POST_SCOPE");
    scopes.put(Scope.DELETE, "DELETE_SCOPE");
    hubEndpoint.setScopes(scopes);
    endpoints.put("hub", hubEndpoint);

    EndpointConfig regularEndpoint = new EndpointConfig();
    regularEndpoint.setPath("/productOffering");
    regularEndpoint.setScopes(new EnumMap<>(Scope.class));
    endpoints.put("product-offering", regularEndpoint);

    serverConfig.setEndpoints(endpoints);
    apiClients.put("catalog-management", serverConfig);
    config.setApiClients(apiClients);
  }

  @Test
  void syncAutoConfig_registersHubBean_skipsNonHub() {
    new TmfHubAutoConfiguration(config, ctx, registry);

    verify(registry).registerBeanDefinition(
        eq("catalog-management.hubTmfHubClient"), any(BeanDefinition.class));
    verify(registry, never()).registerBeanDefinition(
        eq("catalog-management.product-offeringTmfHubClient"), any(BeanDefinition.class));
  }

  @Test
  void reactiveAutoConfig_registersHubBean_skipsNonHub() {
    new ReactiveTmfHubAutoConfiguration(config, ctx, registry);

    verify(registry).registerBeanDefinition(
        eq("catalog-management.hubReactiveTmfHubClient"), any(BeanDefinition.class));
    verify(registry, never()).registerBeanDefinition(
        eq("catalog-management.product-offeringReactiveTmfHubClient"), any(BeanDefinition.class));
  }

  @Test
  void syncAutoConfig_skipsAlreadyRegisteredBean() {
    when(registry.containsBeanDefinition("catalog-management.hubTmfHubClient")).thenReturn(true);

    new TmfHubAutoConfiguration(config, ctx, registry);

    verify(registry, never()).registerBeanDefinition(
        eq("catalog-management.hubTmfHubClient"), any(BeanDefinition.class));
  }

  @Test
  void reactiveAutoConfig_skipsAlreadyRegisteredBean() {
    when(registry.containsBeanDefinition("catalog-management.hubReactiveTmfHubClient"))
        .thenReturn(true);

    new ReactiveTmfHubAutoConfiguration(config, ctx, registry);

    verify(registry, never()).registerBeanDefinition(
        eq("catalog-management.hubReactiveTmfHubClient"), any(BeanDefinition.class));
  }
}
