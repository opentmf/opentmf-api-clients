package org.opentmf.api.client.hub.helper;

import org.opentmf.client.reactive.service.api.TokenService;
import reactor.core.publisher.Mono;

/**
 * Mock reactive token service for hub integration tests.
 */
public class MockTokenService implements TokenService {

  private static final String MOCK_TOKEN = "mock-hub-token";

  @Override
  public String getTokenType() {
    return "Bearer";
  }

  @Override
  public Mono<String> getToken() {
    return Mono.just(MOCK_TOKEN);
  }

  @Override
  public Mono<String> getToken(String additionalScopes) {
    return Mono.just(MOCK_TOKEN);
  }
}
