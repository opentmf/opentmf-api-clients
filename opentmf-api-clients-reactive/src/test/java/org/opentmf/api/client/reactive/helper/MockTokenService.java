package org.opentmf.api.client.reactive.helper;

import org.opentmf.client.reactive.service.api.TokenService;
import reactor.core.publisher.Mono;

/**
 * Mock token service that returns a fixed token for integration tests.
 * No real authentication is needed when testing against MockServer.
 */
public class MockTokenService implements TokenService {

  private static final String MOCK_TOKEN = "mock-test-token";

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
