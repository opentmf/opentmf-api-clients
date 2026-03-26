package org.opentmf.api.client.common.util;

import static org.springframework.util.StringUtils.hasText;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * Helper methods for reading response headers.
 */
public final class ResponseHeaderUtil {

  private ResponseHeaderUtil() {}

  public static long getXTotalCount(ResponseEntity<?> entity) {
    String value = firstHeader(entity, TmfApiClientConstants.HEADER_X_TOTAL_COUNT);
    return hasText(value) ? Long.parseLong(value) : 0L;
  }

  public static int getContentRangeItemCount(ResponseEntity<?> entity) {
    String value = firstHeader(entity, TmfApiClientConstants.HEADER_CONTENT_RANGE);
    return hasText(value) ? parseItemCount(value) : 0;
  }

  public static String firstHeader(ResponseEntity<?> entity, String headerName) {
    HttpHeaders headers = entity.getHeaders();
    if (!headers.containsHeader(headerName)) return null;
    String value = headers.getFirst(headerName);
    return value == null ? null : value.trim();
  }

  /**
   * Parses RFC 7233-style {@code Content-Range: items 0-9/100} headers.
   * Returns 0 for any value that cannot be parsed or indicates zero results.
   */
  static int parseItemCount(String contentRange) {
    String normalized = contentRange.replace("items ", "");
    String[] parts = normalized.split("/");
    if (parts.length != 2) return 0;
    if (!parts[1].contains("*") && Integer.parseInt(parts[1].trim()) == 0) return 0;
    if (parts[0].contains("*")) return 0;
    String[] range = parts[0].split("-");
    if (range.length != 2) return 0;
    int start = Integer.parseInt(range[0].trim());
    int end   = Integer.parseInt(range[1].trim());
    if (start == 0 && end == 0) return 0;
    return end - start + 1;
  }
}
