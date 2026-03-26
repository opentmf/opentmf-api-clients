package org.opentmf.api.client.hub.model;

import java.net.URI;
import lombok.Getter;
import lombok.Setter;

/**
 * Input model for registering an event subscription (hub listener).
 *
 * <p>Mirrors the TMF-630 EventSubscriptionInput schema. Self-contained — does not depend on
 * external model libraries.
 */
@Getter
@Setter
public class EventSubscriptionInput {

  private URI callback;

  private String query;
}
