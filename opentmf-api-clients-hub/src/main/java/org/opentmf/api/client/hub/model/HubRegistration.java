package org.opentmf.api.client.hub.model;

import java.net.URI;
import lombok.Getter;
import lombok.Setter;

/**
 * Extends {@link EventSubscription} with {@code hubUri} — the base URL of the hub endpoint that
 * was used during registration.
 *
 * <p>This information is essential for the "unregister from a previous server" use case: if the
 * hub endpoint changes between deployments, the caller can pass the old {@code HubRegistration} to
 * {@code unregisterListener(HubRegistration)} so that the DELETE request is sent to the
 * <em>original</em> server.
 */
@Getter
@Setter
public class HubRegistration extends EventSubscription {

  private URI hubUri;

  @Override
  public String toString() {
    return "HubRegistration{" +
        "hubUri=" + getHubUri() +
        ", listenerId=" + getId() +
        ", callbackUri=" + getCallback() +
        ", query=" + getQuery() +
        "}";
  }
}
