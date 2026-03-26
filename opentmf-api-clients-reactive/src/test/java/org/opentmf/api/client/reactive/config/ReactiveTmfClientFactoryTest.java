package org.opentmf.api.client.reactive.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opentmf.client.common.util.TokenUtil.CLIENT_PROPERTIES;
import static org.opentmf.client.common.util.TokenUtil.TOKEN_SERVICE;
import static org.opentmf.client.common.util.TokenUtil.WEB_CLIENT;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.reactive.impl.ReactiveTmfClientImpl;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.reactive.service.api.TokenService;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

class ReactiveTmfClientFactoryTest {

  private ApplicationContext ctx;
  private ReactiveTmfClientFactory factory;
  private ServerConfig serverConfig;
  private EndpointConfig endpointConfig;

  @BeforeEach
  void setUp() {
    ctx = mock(ApplicationContext.class);
    factory = new ReactiveTmfClientFactory(ctx);

    endpointConfig = new EndpointConfig();
    endpointConfig.setPath("/products");

    serverConfig = new ServerConfig();
    serverConfig.setClientRef("default");
    serverConfig.setBaseUrl("http://localhost:8080");
    serverConfig.setEndpoints(Map.of("product", endpointConfig));

    when(ctx.getBean("default" + WEB_CLIENT, WebClient.class))
        .thenReturn(mock(WebClient.class));
    when(ctx.getBean("default" + TOKEN_SERVICE, TokenService.class))
        .thenReturn(mock(TokenService.class));
    when(ctx.getBean("default" + CLIENT_PROPERTIES, ClientProperties.class))
        .thenReturn(new ClientProperties());
  }

  @Test
  void create_withValidEndpointName_returnsClient() {
    var client = factory.create(serverConfig, "product", Object.class);

    assertThat(client).isInstanceOf(ReactiveTmfClientImpl.class);
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

    assertThat(client).isInstanceOf(ReactiveTmfClientImpl.class);
  }
}
