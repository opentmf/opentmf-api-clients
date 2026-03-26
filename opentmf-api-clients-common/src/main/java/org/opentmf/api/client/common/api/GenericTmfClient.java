package org.opentmf.api.client.common.api;

/**
 * Untyped (generic) synchronous TMF client. All create, update, and response types are
 * {@link Object}; the caller specifies the desired type at call time via {@code Class<T>}
 * overloads.
 *
 * <p>Example:
 * <pre>{@code
 * @Autowired
 * @Qualifier("orderManagement.productOrderTmfClient")
 * GenericTmfClient productOrderClient;
 *
 * ProductOrder order = productOrderClient.get("123", ProductOrder.class);
 * }</pre>
 */
public interface GenericTmfClient extends TmfClient<Object, Object, Object> {}
