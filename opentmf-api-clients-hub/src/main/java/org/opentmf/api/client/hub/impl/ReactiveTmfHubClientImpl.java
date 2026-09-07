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
import org.opentmf.api.client.hub.api.ReactiveTmfHubClient;
import org.opentmf.api.client.hub.model.EventSubscription;
import org.opentmf.api.client.hub.model.EventSubscriptionInput;
import org.opentmf.api.client.hub.model.HubRegistration;
import org.opentmf.client.common.exception.OpenTmfClientResponseException;
import org.opentmf.client.common.model.ClientProperties;
import org.opentmf.client.reactive.service.api.TokenService;
import org.opentmf.client.reactive.util.WebClientUtil;
import org.opentmf.commons.util.JacksonUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.util.retry.RetryBackoffSpec;

/**
 * Reactive (WebClient) implementation of {@link ReactiveTmfHubClient}.
 *
 * <p>Uses composition: the hub-specific methods (register/unregister) are implemented directly
 * against {@link WebClient}, while standard CRUD operations are intentionally not exposed.
 */
@Slf4j
public class ReactiveTmfHubClientImpl implements ReactiveTmfHubClient {

  private final EndpointConfig endpointConfig;
  private final ServerConfig serverConfig;
  private final WebClient webClient;
  private final TokenService tokenService;
  private final ClientProperties clientProperties;

  public ReactiveTmfHubClientImpl(
      EndpointConfig endpointConfig,
      ServerConfig serverConfig,
      WebClient webClient,
      TokenService tokenService,
      ClientProperties clientProperties) {
    this.endpointConfig  = Objects.requireNonNull(endpointConfig);
    this.serverConfig    = Objects.requireNonNull(serverConfig);
    this.webClient       = Objects.requireNonNull(webClient);
    this.tokenService    = Objects.requireNonNull(tokenService);
    this.clientProperties = Objects.requireNonNull(clientProperties);
  }

  @Override
  public Mono<HubRegistration> registerListener(EventSubscriptionInput input) {
    return getToken(Scope.POST).flatMap(token -> registerListener(token, input));
  }

  @Override
  public Mono<HubRegistration> registerListener(String token, EventSubscriptionInput input) {
    Objects.requireNonNull(input, "EventSubscriptionInput must not be null.");
    URI uri = UriBuilderUtil.buildUri(serverConfig, endpointConfig, null);
    var h = prepareAndValidate(headers(token), MediaType.APPLICATION_JSON);

    return webClient.post().uri(uri).headers(hh -> hh.addAll(h))
        .bodyValue(input)
        .retrieve()
        .onStatus(HttpStatusCode::isError, ReactiveTmfHubClientImpl::handleError)
        .bodyToMono(EventSubscription.class)
        .retryWhen(retry())
        .map(this::toRegistration);
  }

  @Override
  public Mono<Void> unregisterListener(String id) {
    return getToken(Scope.DELETE).flatMap(token -> unregisterListener(token, id));
  }

  @Override
  public Mono<Void> unregisterListener(String token, String id) {
    Objects.requireNonNull(id, "Subscription ID must not be null.");
    URI uri = UriBuilderUtil.buildUriWithId(serverConfig, endpointConfig, id);
    var h = prepareGetDelete(headers(token));
    return webClient.delete().uri(uri).headers(hh -> hh.addAll(h))
        .retrieve()
        .onStatus(HttpStatusCode::isError, ReactiveTmfHubClientImpl::handleError)
        .toBodilessEntity()
        .retryWhen(retry())
        .then();
  }

  @Override
  public Mono<Void> unregisterListener(HubRegistration registration) {
    Objects.requireNonNull(registration, "HubRegistration must not be null.");
    Objects.requireNonNull(registration.getHubUri(), "HubRegistration.hubUri must not be null.");
    Objects.requireNonNull(registration.getId(), "HubRegistration.id must not be null.");

    URI uri = UriComponentsBuilder.fromUri(registration.getHubUri())
        .pathSegment(registration.getId())
        .build().toUri();

    return getToken(Scope.DELETE).flatMap(token -> {
      var h = prepareGetDelete(headers(token));
      return webClient.delete().uri(uri).headers(hh -> hh.addAll(h))
          .retrieve()
          .onStatus(HttpStatusCode::isError, ReactiveTmfHubClientImpl::handleError)
          .toBodilessEntity()
          .retryWhen(retry())
          .then();
    });
  }

  private HubRegistration toRegistration(EventSubscription subscription) {
    URI hubUri = UriBuilderUtil.buildBaseUri(serverConfig, endpointConfig);
    HubRegistration reg = JacksonUtil.jsonToObject(
        JacksonUtil.objectToJson(subscription), HubRegistration.class);
    reg.setHubUri(hubUri);
    return reg;
  }

  private Mono<String> getToken(Scope scope) {
    String scopeValue = endpointConfig.getScopes().get(scope);
    return (scopeValue != null && !scopeValue.isBlank())
        ? tokenService.getToken(scopeValue)
        : tokenService.getToken();
  }

  private Consumer<HttpHeaders> headers(String token) {
    return headersConsumer(tokenService.getTokenType(), token, null, null,
        clientProperties.getAuthType());
  }

  private RetryBackoffSpec retry() {
    return WebClientUtil.retry(
        clientProperties.getNumRetries(),
        clientProperties.getRetryWaitDuration());
  }

  private static Mono<? extends Throwable> handleError(ClientResponse response) {
    return WebClientUtil.handleError(response, OpenTmfClientResponseException.class);
  }
}
