package org.opentmf.api.client.rest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opentmf.client.common.util.TokenUtil.CLIENT_PROPERTIES;
import static org.opentmf.client.common.util.TokenUtil.REST_CLIENT;
import static org.opentmf.client.common.util.TokenUtil.TOKEN_SERVICE;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.rest.impl.TmfClientImpl;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.RestClient;

class TmfClientFactoryTest {

  private ApplicationContext ctx;
  private TmfClientFactory factory;
  private ServerConfig serverConfig;
  private EndpointConfig endpointConfig;

  @BeforeEach
  void setUp() {
    ctx = mock(ApplicationContext.class);
    factory = new TmfClientFactory(ctx);

    endpointConfig = new EndpointConfig();
    endpointConfig.setPath("/products");

    serverConfig = new ServerConfig();
    serverConfig.setClientRef("default");
    serverConfig.setBaseUrl("http://localhost:8080");
    serverConfig.setEndpoints(Map.of("product", endpointConfig));

    when(ctx.getBean("default" + REST_CLIENT, RestClient.class))
        .thenReturn(mock(RestClient.class));
    when(ctx.getBean("default" + TOKEN_SERVICE, SyncTokenService.class))
        .thenReturn(mock(SyncTokenService.class));
    when(ctx.getBean("default" + CLIENT_PROPERTIES, ClientProperties.class))
        .thenReturn(new ClientProperties());
  }

  @Test
  void create_withValidEndpointName_returnsClient() {
    var client = factory.create(serverConfig, "product", Object.class);

    assertThat(client).isInstanceOf(TmfClientImpl.class);
  }

  @Test
  void create_withMissingEndpointName_throwsIllegalArgument() {
    assertThatThrownBy(() -> factory.create(serverConfig, "nonexistent", Object.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nonexistent");
  }

  @Test
  void create_withEndpointConfig_returnsClient() {
    var client = factory.create(serverConfig, endpointConfig, Object.class);

    assertThat(client).isInstanceOf(TmfClientImpl.class);
  }
}
