package org.opentmf.api.client.hub.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.opentmf.commons.util.JacksonUtil;

class HubModelTest {

  @Test
  void eventSubscriptionInput_gettersSetters() {
    var input = new EventSubscriptionInput();
    input.setCallback(URI.create("http://localhost:8080/callback"));
    input.setQuery("eventType=ProductOrderCreateEvent");

    assertThat(input.getCallback()).hasToString("http://localhost:8080/callback");
    assertThat(input.getQuery()).isEqualTo("eventType=ProductOrderCreateEvent");
  }

  @Test
  void eventSubscription_inheritsInput() {
    var sub = new EventSubscription();
    sub.setCallback(URI.create("http://localhost:8080/callback"));
    sub.setQuery("eventType=ResourceCreateEvent");
    sub.setId("sub-123");

    assertThat(sub.getId()).isEqualTo("sub-123");
    assertThat(sub.getCallback()).hasToString("http://localhost:8080/callback");
    assertThat(sub.getQuery()).isEqualTo("eventType=ResourceCreateEvent");
  }

  @Test
  void hubRegistration_extendsSubscription() {
    var reg = new HubRegistration();
    reg.setId("reg-456");
    reg.setCallback(URI.create("http://app:8080/listener"));
    reg.setQuery("eventType=ProductOfferingCreateEvent");
    reg.setHubUri(URI.create("http://catalog-server:8080/tmf-api/productCatalogManagement/v4/hub"));

    assertThat(reg.getHubUri())
        .hasToString("http://catalog-server:8080/tmf-api/productCatalogManagement/v4/hub");
    assertThat(reg.getId()).isEqualTo("reg-456");
    assertThat(reg.getCallback()).hasToString("http://app:8080/listener");
  }

  @Test
  void hubRegistration_toString_containsAllFields() {
    var reg = new HubRegistration();
    reg.setId("id-1");
    reg.setCallback(URI.create("http://cb"));
    reg.setQuery("q");
    reg.setHubUri(URI.create("http://hub"));

    String str = reg.toString();
    assertThat(str).contains("HubRegistration{");
    assertThat(str).contains("hubUri=http://hub");
    assertThat(str).contains("listenerId=id-1");
    assertThat(str).contains("callbackUri=http://cb");
    assertThat(str).contains("query=q");
  }

  @Test
  void eventSubscriptionInput_serialization_roundTrip() {
    var input = new EventSubscriptionInput();
    input.setCallback(URI.create("http://localhost:8080/callback"));
    input.setQuery("eventType=Test");

    String json = JacksonUtil.objectToJson(input);
    var deserialized = JacksonUtil.jsonToObject(json, EventSubscriptionInput.class);

    assertThat(deserialized.getCallback()).isEqualTo(input.getCallback());
    assertThat(deserialized.getQuery()).isEqualTo(input.getQuery());
  }

  @Test
  void hubRegistration_serialization_roundTrip() {
    var reg = new HubRegistration();
    reg.setId("x");
    reg.setCallback(URI.create("http://cb"));
    reg.setQuery("q");
    reg.setHubUri(URI.create("http://hub/path"));

    String json = JacksonUtil.objectToJson(reg);
    var deserialized = JacksonUtil.jsonToObject(json, HubRegistration.class);

    assertThat(deserialized.getId()).isEqualTo("x");
    assertThat(deserialized.getHubUri()).isEqualTo(reg.getHubUri());
    assertThat(deserialized.getCallback()).isEqualTo(reg.getCallback());
  }
}
