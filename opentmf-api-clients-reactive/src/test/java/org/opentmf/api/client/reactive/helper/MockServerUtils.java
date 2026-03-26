package org.opentmf.api.client.reactive.helper;

import static org.mockserver.model.HttpRequest.request;

import java.util.concurrent.ThreadLocalRandom;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpClassCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Parameter;
import org.opentmf.mockserver.callback.DynamicDeleteCallback;
import org.opentmf.mockserver.callback.DynamicPostCallback;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Utility to manage a shared MockServer instance with TMF-630 dynamic callbacks.
 */
public final class MockServerUtils {

  private static ClientAndServer mockServer;
  private static String baseUrl;
  private static final JsonMapper MAPPER = JsonMapper.shared();

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

  public static String randomPath() {
    return "/tmf-api/test/v4/resource" + ThreadLocalRandom.current().nextInt(1000, 9999);
  }

  // --- Dynamic callback registrations ---

  public static void setUpDynamicPostCallback(String path) {
    mockServer.when(
        request().withMethod("POST").withPath(path)
    ).respond(
        HttpClassCallback.callback("org.opentmf.mockserver.callback.DynamicPostCallback")
    );
  }

  public static void setUpDynamicGetCallback(String path) {
    mockServer.when(
        request().withMethod("GET")
            .withPath(path + "/{id}")
            .withPathParameter(Parameter.param("id", ".*"))
    ).respond(
        HttpClassCallback.callback("org.opentmf.mockserver.callback.DynamicGetCallback")
    );
  }

  public static void setUpDynamicGetListCallback(String path) {
    mockServer.when(
        request().withMethod("GET").withPath(path)
    ).respond(
        HttpClassCallback.callback("org.opentmf.mockserver.callback.DynamicGetListCallback")
    );
  }

  public static void setUpDynamicMergePatchCallback(String path) {
    mockServer.when(
        request().withMethod("PATCH")
            .withPath(path + "/{id}")
            .withPathParameter(Parameter.param("id", ".*"))
            .withHeader("Content-Type", "application/merge-patch+json")
    ).respond(
        HttpClassCallback.callback("org.opentmf.mockserver.callback.DynamicMergePatchCallback")
    );
  }

  public static void setUpDynamicJsonPatchCallback(String path) {
    mockServer.when(
        request().withMethod("PATCH")
            .withPath(path + "/{id}")
            .withPathParameter(Parameter.param("id", ".*"))
            .withHeader("Content-Type", "application/json-patch+json")
    ).respond(
        HttpClassCallback.callback("org.opentmf.mockserver.callback.DynamicJsonPatchCallback")
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

  public static void setUpAllDynamicCallbacks(String path) {
    setUpDynamicPostCallback(path);
    setUpDynamicGetCallback(path);
    setUpDynamicGetListCallback(path);
    setUpDynamicMergePatchCallback(path);
    setUpDynamicJsonPatchCallback(path);
    setUpDynamicDeleteCallback(path);
  }

  // --- Data helpers ---

  public static ObjectNode getTestData() {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("name", "TestItem-" + ThreadLocalRandom.current().nextInt(10000));
    node.put("description", "Test description " + System.currentTimeMillis());
    node.put("randomNumber", ThreadLocalRandom.current().nextInt(1, 1000));
    return node;
  }

  /**
   * Directly invokes the DynamicPostCallback to seed data into the MockServer cache.
   * Returns the generated resource ID.
   */
  public static String addDataToCache(String path, ObjectNode data) {
    HttpRequest httpRequest = new HttpRequest()
        .withPath(path)
        .withBody(data.toString());
    DynamicPostCallback callback = new DynamicPostCallback();
    HttpResponse response = callback.handle(httpRequest);
    try {
      return MAPPER.readTree(response.getBodyAsString()).get("id").asText();
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse response from DynamicPostCallback", e);
    }
  }

  /**
   * Seeds multiple items into the MockServer cache.
   */
  public static void seedData(String path, int count) {
    for (int i = 0; i < count; i++) {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("name", "Item-" + i);
      node.put("description", "Description " + i);
      node.put("orderNumber", i);
      node.put("randomNumber", ThreadLocalRandom.current().nextInt(1, 1000));
      node.put("even", i % 2 == 0);
      addDataToCache(path, node);
    }
  }

  /**
   * Seeds resources with a 'characteristic' array into the MockServer cache.
   * Creates a mix of resources where some match a specific IMEI filter and others do not.
   *
   * <p>Out of 5 resources, resources at index 0 and 3 will have
   * characteristic [{name:"IMEI", value:"123456789012345"}, ...].
   */
  public static void seedDataWithCharacteristics(String path) {
    String[][] charSets = {
        {"IMEI", "123456789012345", "Color", "Black"},
        {"IMEI", "999888777666555", "Color", "White"},
        {"SerialNumber", "SN001", "Color", "Black"},
        {"IMEI", "123456789012345", "Color", "Red"},
        {"SerialNumber", "SN002", "Model", "X100"},
    };

    for (int i = 0; i < charSets.length; i++) {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("name", "CharItem-" + i);
      node.put("description", "Description " + i);

      var charArray = node.putArray("characteristic");
      for (int c = 0; c < charSets[i].length; c += 2) {
        var charObj = charArray.addObject();
        charObj.put("name", charSets[i][c]);
        charObj.put("value", charSets[i][c + 1]);
      }

      addDataToCache(path, node);
    }
  }

  public static void deleteDataFromCache(String path, String id) {
    HttpRequest httpRequest = new HttpRequest().withPath(path + "/" + id);
    DynamicDeleteCallback callback = new DynamicDeleteCallback();
    callback.handle(httpRequest);
  }
}
