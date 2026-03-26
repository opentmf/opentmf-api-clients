package org.opentmf.api.client.hub.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.opentmf.api.client.common.util.TmfApiClientConstants;

class TmfHubAutoConfigurationTest {

  @Test
  void hubEndpointSuffix_isHub() {
    assertThat(TmfApiClientConstants.HUB_ENDPOINT_SUFFIX).isEqualTo("/hub");
  }

  @Test
  void hubPath_endsWithSuffix() {
    String path = "/tmf-api/productCatalogManagement/v4/hub";
    assertThat(path.endsWith(TmfApiClientConstants.HUB_ENDPOINT_SUFFIX)).isTrue();
  }

  @Test
  void nonHubPath_doesNotEndWithSuffix() {
    String path = "/tmf-api/productCatalogManagement/v4/productOffering";
    assertThat(path.endsWith(TmfApiClientConstants.HUB_ENDPOINT_SUFFIX)).isFalse();
  }

  @Test
  void hubSubPath_doesNotEndWithSuffix() {
    String path = "/tmf-api/test/v4/hub/subscriptions";
    assertThat(path.endsWith(TmfApiClientConstants.HUB_ENDPOINT_SUFFIX)).isFalse();
  }
}
