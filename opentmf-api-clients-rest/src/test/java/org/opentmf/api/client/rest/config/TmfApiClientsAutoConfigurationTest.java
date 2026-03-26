package org.opentmf.api.client.rest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opentmf.client.common.util.TokenUtil.CLIENT_PROPERTIES;
import static org.opentmf.client.common.util.TokenUtil.REST_CLIENT;
import static org.opentmf.client.common.util.TokenUtil.TOKEN_SERVICE;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentmf.api.client.common.config.TmfApiClientsConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestClient;

class TmfApiClientsAutoConfigurationTest {

  private TmfApiClientsConfig config;
  private ApplicationContext ctx;
  private BeanDefinitionRegistry registry;

  @BeforeEach
  void setUp() {
    ctx = mock(ApplicationContext.class);
    registry = mock(BeanDefinitionRegistry.class);

    when(ctx.getBean("default" + REST_CLIENT, RestClient.class))
        .thenReturn(mock(RestClient.class));
    when(ctx.getBean("default" + TOKEN_SERVICE, SyncTokenService.class))
        .thenReturn(mock(SyncTokenService.class));
    when(ctx.getBean("default" + CLIENT_PROPERTIES, ClientProperties.class))
        .thenReturn(new ClientProperties());

    EndpointConfig ep1 = new EndpointConfig();
    ep1.setPath("/products");
    EndpointConfig ep2 = new EndpointConfig();
    ep2.setPath("/orders");

    ServerConfig server = new ServerConfig();
    server.setClientRef("default");
    server.setBaseUrl("http://localhost");
    Map<String, EndpointConfig> endpoints = new LinkedHashMap<>();
    endpoints.put("product", ep1);
    endpoints.put("order", ep2);
    server.setEndpoints(endpoints);

    config = new TmfApiClientsConfig();
    config.setApiClients(Map.of("catalog", server));
  }

  @Test
  void constructor_registersGenericBeanForEachEndpoint() {
    new TmfApiClientsAutoConfiguration(config, ctx, registry);

    verify(registry).registerBeanDefinition(eq("catalog.productTmfClient"), any());
    verify(registry).registerBeanDefinition(eq("catalog.orderTmfClient"), any());
  }

  @Test
  void constructor_skipsAlreadyRegisteredBean() {
    when(registry.containsBeanDefinition("catalog.productTmfClient")).thenReturn(true);

    new TmfApiClientsAutoConfiguration(config, ctx, registry);

    verify(registry, never()).registerBeanDefinition(eq("catalog.productTmfClient"), any());
    verify(registry).registerBeanDefinition(eq("catalog.orderTmfClient"), any());
  }

  @Test
  void tmfClientFactory_returnsFactoryInstance() {
    var autoConfig = new TmfApiClientsAutoConfiguration(config, ctx, registry);

    TmfClientFactory factory = autoConfig.tmfClientFactory(ctx);

    assertThat(factory).isNotNull();
  }
}
