package org.opentmf.api.client.rest.api;

import org.opentmf.api.client.common.api.GenericTmfClient;

/**
 * Marker interface so that the REST-module generic client can be identified as a specific type.
 * Extends {@link GenericTmfClient} which extends {@code TmfClient<Object, Object, Object>}.
 */
public interface RestGenericTmfClient extends GenericTmfClient {}
