package org.opentmf.api.client.rest.helper;

import org.opentmf.client.rest.service.api.SyncTokenService;

/**
 * Mock synchronous token service that returns a fixed token for integration tests.
 * No real authentication is needed when testing against MockServer.
 */
public class MockSyncTokenService implements SyncTokenService {

  private static final String MOCK_TOKEN = "mock-test-token";

  @Override
  public String getTokenType() {
    return "Bearer";
  }

  @Override
  public String getToken() {
    return MOCK_TOKEN;
  }

  @Override
  public String getToken(String additionalScopes) {
    return MOCK_TOKEN;
  }
}
