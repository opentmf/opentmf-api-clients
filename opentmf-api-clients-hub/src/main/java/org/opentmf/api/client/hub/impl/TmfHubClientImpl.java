package org.opentmf.api.client.hub.impl;

import static org.opentmf.api.client.common.util.HeaderUtil.headersConsumer;
import static org.opentmf.api.client.common.util.HeaderUtil.prepareAndValidate;
import static org.opentmf.api.client.common.util.HeaderUtil.prepareGetDelete;

import java.net.URI;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.model.Scope;
import org.opentmf.api.client.common.util.UriBuilderUtil;
import org.opentmf.api.client.hub.api.TmfHubClient;
import org.opentmf.api.client.hub.model.EventSubscription;
import org.opentmf.api.client.hub.model.EventSubscriptionInput;
import org.opentmf.api.client.hub.model.HubRegistration;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.rest.service.api.SyncTokenService;
import org.opentmf.client.rest.util.SyncClientUtil;
import org.opentmf.commons.util.JacksonUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Synchronous (RestClient) implementation of {@link TmfHubClient}.
 *
 * <p>Uses composition: the hub-specific methods (register/unregister) are implemented directly
 * against {@link RestClient}, while standard CRUD operations are intentionally not exposed.
 */
@Slf4j
public class TmfHubClientImpl implements TmfHubClient {

  private final EndpointConfig endpointConfig;
  private final ServerConfig serverConfig;
  private final RestClient restClient;
  private final SyncTokenService tokenService;
  private final ClientProperties clientProperties;

  public TmfHubClientImpl(
      EndpointConfig endpointConfig,
      ServerConfig serverConfig,
      RestClient restClient,
      SyncTokenService tokenService,
      ClientProperties clientProperties) {
    this.endpointConfig   = Objects.requireNonNull(endpointConfig);
    this.serverConfig     = Objects.requireNonNull(serverConfig);
    this.restClient       = Objects.requireNonNull(restClient);
    this.tokenService     = Objects.requireNonNull(tokenService);
    this.clientProperties = Objects.requireNonNull(clientProperties);
  }

  @Override
  public HubRegistration registerListener(EventSubscriptionInput input) {
    return registerListener(getToken(Scope.POST), input);
  }

  @Override
  public HubRegistration registerListener(String token, EventSubscriptionInput input) {
    Objects.requireNonNull(input, "EventSubscriptionInput must not be null.");
    URI uri = UriBuilderUtil.buildUri(serverConfig, endpointConfig, null);
    var h = prepareAndValidate(headers(token), MediaType.APPLICATION_JSON);

    EventSubscription response = withRetry(() ->
        restClient.post().uri(uri).headers(hh -> hh.addAll(h))
            .body(input).retrieve().body(EventSubscription.class));

    return toRegistration(response);
  }

  @Override
  public void unregisterListener(String id) {
    unregisterListener(getToken(Scope.DELETE), id);
  }

  @Override
  public void unregisterListener(String token, String id) {
    Objects.requireNonNull(id, "Subscription ID must not be null.");
    URI uri = UriBuilderUtil.buildUriWithId(serverConfig, endpointConfig, id);
    var h = prepareGetDelete(headers(token));
    withRetry(() -> {
      restClient.delete().uri(uri).headers(hh -> hh.addAll(h))
          .retrieve().toBodilessEntity();
      return null;
    });
  }

  @Override
  public void unregisterListener(HubRegistration registration) {
    Objects.requireNonNull(registration, "HubRegistration must not be null.");
    Objects.requireNonNull(registration.getHubUri(), "HubRegistration.hubUri must not be null.");
    Objects.requireNonNull(registration.getId(), "HubRegistration.id must not be null.");

    URI uri = UriComponentsBuilder.fromUri(registration.getHubUri())
        .pathSegment(registration.getId())
        .build().toUri();

    String token = getToken(Scope.DELETE);
    var h = prepareGetDelete(headers(token));
    withRetry(() -> {
      restClient.delete().uri(uri).headers(hh -> hh.addAll(h))
          .retrieve().toBodilessEntity();
      return null;
    });
  }

  private HubRegistration toRegistration(EventSubscription subscription) {
    URI hubUri = UriBuilderUtil.buildBaseUri(serverConfig, endpointConfig);
    HubRegistration reg = JacksonUtil.jsonToObject(
        JacksonUtil.objectToJson(subscription), HubRegistration.class);
    reg.setHubUri(hubUri);
    return reg;
  }

  private String getToken(Scope scope) {
    String scopeValue = endpointConfig.getScopes().get(scope);
    return (scopeValue != null && !scopeValue.isBlank())
        ? tokenService.getToken(scopeValue)
        : tokenService.getToken();
  }

  private Consumer<HttpHeaders> headers(String token) {
    return headersConsumer(tokenService.getTokenType(), token, null, null);
  }

  private <T> T withRetry(java.util.function.Supplier<T> action) {
    return SyncClientUtil.executeWithRetry(
        action,
        clientProperties.getNumRetries(),
        clientProperties.getRetryWaitDuration());
  }
}
