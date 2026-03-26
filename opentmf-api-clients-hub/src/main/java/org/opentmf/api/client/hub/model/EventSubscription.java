package org.opentmf.api.client.hub.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Server-side response model for an event subscription. Extends {@link EventSubscriptionInput} with
 * the server-assigned {@code id}.
 */
@Getter
@Setter
public class EventSubscription extends EventSubscriptionInput {

  private String id;
}
