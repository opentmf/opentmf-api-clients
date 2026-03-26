package org.opentmf.api.client.reactive.api;

/**
 * Untyped (generic) reactive TMF client. All create, update, and response types are
 * {@link Object}; the caller specifies the desired type at call time via {@code Class<T>} overloads.
 *
 * <p>Example:
 * <pre>{@code
 * @Autowired
 * @Qualifier("catalogManagement.productOfferingTmfClient")
 * GenericReactiveTmfClient productOfferingClient;
 *
 * Mono<ProductOffering> po = productOfferingClient.get("123", ProductOffering.class);
 * Flux<ProductOffering> all = productOfferingClient.list(ProductOffering.class);
 * }</pre>
 */
public interface GenericReactiveTmfClient extends ReactiveTmfClient<Object, Object, Object> {}
