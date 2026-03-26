package org.opentmf.api.client.hub.helper;

import static org.mockserver.model.HttpRequest.request;

import java.util.concurrent.ThreadLocalRandom;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.Parameter;

/**
 * Utility to manage a shared MockServer instance for hub integration tests.
 */
public final class MockServerUtils {

  private static ClientAndServer mockServer;
  private static String baseUrl;

  private MockServerUtils() {}

  public static synchronized void startMockServer() {
    if (mockServer == null || !mockServer.isRunning()) {
      mockServer = ClientAndServer.startClientAndServer(0);
      baseUrl = "http://localhost:" + mockServer.getLocalPort();
    }
  }

  public static synchronized void stopMockServer() {
    if (mockServer != null && mockServer.isRunning()) {
      mockServer.stop();
      mockServer = null;
    }
  }

  public static void resetMockServer() {
    if (mockServer != null) {
      mockServer.reset();
    }
  }

  public static String getBaseUrl() {
    return baseUrl;
  }

  public static ClientAndServer getMockServer() {
    return mockServer;
  }

  public static String randomHubPath() {
    return "/tmf-api/test/v4/hub" + ThreadLocalRandom.current().nextInt(1000, 9999);
  }

  public static void setUpDynamicPostCallback(String path) {
    mockServer.when(
        request().withMethod("POST").withPath(path)
    ).respond(
        HttpClassCallback.callback("org.opentmf.mockserver.callback.DynamicPostCallback")
    );
  }

  public static void setUpDynamicDeleteCallback(String path) {
    mockServer.when(
        request().withMethod("DELETE")
            .withPath(path + "/{id}")
            .withPathParameter(Parameter.param("id", ".*"))
    ).respond(
        HttpClassCallback.callback("org.opentmf.mockserver.callback.DynamicDeleteCallback")
    );
  }

  public static void setUpHubCallbacks(String path) {
    setUpDynamicPostCallback(path);
    setUpDynamicDeleteCallback(path);
  }
}
