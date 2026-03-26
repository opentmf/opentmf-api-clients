package org.opentmf.api.client.hub.helper;

import org.opentmf.client.rest.service.api.SyncTokenService;

/**
 * Mock synchronous token service for hub integration tests.
 */
public class MockSyncTokenService implements SyncTokenService {

  private static final String MOCK_TOKEN = "mock-hub-token";

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
