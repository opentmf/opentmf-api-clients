package org.opentmf.api.client.common.model;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;

/**
 * Abstract base for offset-based pagination. Implements {@link Pageable} using an absolute offset
 * and a page size (limit), rather than a zero-based page number.
 */
public abstract class AbstractOffsetRequest implements Pageable {

  private final Sort sort;
  private final long offset;
  private final int limit;

  protected AbstractOffsetRequest(Sort sort, long offset, int limit) {
    if (offset < 0) throw new IllegalArgumentException("Offset must not be less than zero.");
    if (limit < 1) throw new IllegalArgumentException("Limit must not be less than one.");
    this.sort = sort != null ? sort : Sort.unsorted();
    this.offset = offset;
    this.limit = limit;
  }

  @Override
  public @NonNull Sort getSort() {
    return sort;
  }

  @Override
  public int getPageSize() {
    return limit;
  }

  @Override
  public int getPageNumber() {
    return Math.toIntExact(offset / limit);
  }

  @Override
  public boolean hasPrevious() {
    return getPageNumber() > 0;
  }

  @Override
  public long getOffset() {
    return offset;
  }

  @Override
  public @NonNull Pageable previousOrFirst() {
    return hasPrevious() ? previous() : first();
  }

  public abstract Pageable previous();
}
