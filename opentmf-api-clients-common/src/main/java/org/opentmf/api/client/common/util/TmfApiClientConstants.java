package org.opentmf.api.client.common.util;

/**
 * Shared constants for TMF API clients.
 */
public final class TmfApiClientConstants {

  public static final String MEDIA_TYPE_JSON_PATCH  = "application/json-patch+json";
  public static final String MEDIA_TYPE_MERGE_PATCH = "application/merge-patch+json";

  public static final String QUERY_PARAM_FILTER = "filter";
  public static final String QUERY_PARAM_FIELDS = "fields";
  public static final String QUERY_PARAM_OFFSET = "offset";
  public static final String QUERY_PARAM_LIMIT  = "limit";
  public static final String QUERY_PARAM_SORT   = "sort";

  public static final String HUB_ENDPOINT_SUFFIX = "/hub";

  public static final String HEADER_X_TOTAL_COUNT = "X-Total-Count";
  public static final String HEADER_CONTENT_RANGE = "Content-Range";

  public static final String ERR_NULL_HEADERS_CONSUMER = "Headers consumer must not be null.";
  public static final String ERR_EMPTY_AUTH_TOKEN      = "Authorization token must not be empty.";
  public static final String ERR_NULL_BODY             = "Request body must not be null for %s.";
  public static final String ERR_NULL_ID               = "Resource ID must not be null or empty.";
  public static final String ERR_NULL_TYPE             = "Response class type must not be null.";

  @lombok.Generated
  private TmfApiClientConstants() {}
}
