package org.opentmf.api.client.hub.api;

import org.opentmf.api.client.hub.model.EventSubscriptionInput;
import org.opentmf.api.client.hub.model.HubRegistration;
import reactor.core.publisher.Mono;

/**
 * Reactive TMF Hub client for registering and unregistering event subscriptions.
 *
 * <p>Unlike the generic {@code ReactiveTmfClient}, this interface exposes only the operations that
 * are meaningful for hub endpoints: {@code registerListener} (POST) and {@code unregisterListener}
 * (DELETE). List / GET / PATCH operations are intentionally omitted.
 *
 * <p>The {@link #unregisterListener(HubRegistration)} overload sends the DELETE request to the
 * {@link HubRegistration#getHubUri() hubUri} stored in the registration, enabling unregistration
 * from a <em>previous</em> server when the hub endpoint URL has changed between deployments.
 */
public interface ReactiveTmfHubClient {

  /**
   * Registers a listener using an auto-obtained token.
   */
  Mono<HubRegistration> registerListener(EventSubscriptionInput input);

  /**
   * Registers a listener using the provided token.
   */
  Mono<HubRegistration> registerListener(String token, EventSubscriptionInput input);

  /**
   * Unregisters a listener by id using an auto-obtained token.
   */
  Mono<Void> unregisterListener(String id);

  /**
   * Unregisters a listener by id using the provided token.
   */
  Mono<Void> unregisterListener(String token, String id);

  /**
   * Unregisters a listener from the server that was used during the original registration.
   *
   * <p>The DELETE request targets {@link HubRegistration#getHubUri()} + {@code /} +
   * {@link HubRegistration#getId()}, allowing unregistration from a previous server if the
   * hub configuration has since changed.
   */
  Mono<Void> unregisterListener(HubRegistration registration);
}
