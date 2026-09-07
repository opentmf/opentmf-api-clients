package org.opentmf.api.client.hub.api;

import org.opentmf.api.client.hub.model.EventSubscriptionInput;
import org.opentmf.api.client.hub.model.HubRegistration;

/**
 * Synchronous TMF Hub client for registering and unregistering event subscriptions.
 *
 * <p>Unlike the generic {@code TmfClient}, this interface exposes only the operations that are
 * meaningful for hub endpoints: {@code registerListener} (POST) and {@code unregisterListener}
 * (DELETE). List / GET / PATCH operations are intentionally omitted.
 *
 * <p>The {@link #unregisterListener(HubRegistration)} overload sends the DELETE request to the
 * {@code hubUri} stored in the registration, enabling unregistration
 * from a <em>previous</em> server when the hub endpoint URL has changed between deployments.
 */
public interface TmfHubClient {

  /**
   * Registers a listener using an auto-obtained token.
   *
   * @param input the subscription input (callback + optional query)
   * @return the registration result including the server-assigned id and the hub URI
   */
  HubRegistration registerListener(EventSubscriptionInput input);

  /**
   * Registers a listener using the provided token.
   */
  HubRegistration registerListener(String token, EventSubscriptionInput input);

  /**
   * Unregisters a listener by id using an auto-obtained token.
   */
  void unregisterListener(String id);

  /**
   * Unregisters a listener by id using the provided token.
   */
  void unregisterListener(String token, String id);

  /**
   * Unregisters a listener from the server that was used during the original registration.
   *
   * <p>The DELETE request targets {@code HubRegistration.getHubUri()} + {@code /} +
   * {@code HubRegistration.getId()}, allowing unregistration from a previous server if the
   * hub configuration has since changed.
   */
  void unregisterListener(HubRegistration registration);
}
