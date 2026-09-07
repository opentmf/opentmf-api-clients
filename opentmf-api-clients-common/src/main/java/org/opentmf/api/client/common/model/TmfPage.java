package org.opentmf.api.client.common.model;

import org.springframework.data.domain.Pageable;

/**
 * Pagination result with total-count metadata.
 *
 * @param <T> type of the page content (typically {@code List<R>})
 */
public interface TmfPage<T> {

  int getTotalPages();

  long getTotalElements();

  T getContent();

  int getSize();

  int getNumber();

  boolean hasNext();

  boolean isLast();

  Pageable getNextPageable();
}
