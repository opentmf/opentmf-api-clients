package org.opentmf.api.client.common.model;

import org.springframework.data.domain.Pageable;

/**
 * Offset-based implementation of {@link TmfPage}.
 *
 * @param <T> type of the page content
 */
public class OffsetPage<T> implements TmfPage<T> {

  private final Pageable pageable;
  private final long total;
  private final int size;
  private final T content;

  public OffsetPage(long total, int itemCount, Pageable pageable, T content) {
    this.total = total;
    this.size = itemCount;
    this.content = content;

    int effectiveLimit = pageable.getPageNumber() == 0
        && itemCount != pageable.getPageSize()
        && itemCount > 0
        ? itemCount
        : pageable.getPageSize();

    this.pageable = pageable instanceof TmfOffsetRequest t
        ? t.withLimit(effectiveLimit)
        : TmfOffsetRequest.of((int) pageable.getOffset(), effectiveLimit, pageable.getSort());
  }

  @Override
  public int getTotalPages() {
    int pageLimit = pageable.getPageSize();
    int totalPages = (int) (total / pageLimit);
    if (total % pageLimit != 0) totalPages++;
    return totalPages;
  }

  @Override
  public long getTotalElements() {
    return total;
  }

  @Override
  public T getContent() {
    return content;
  }

  @Override
  public int getSize() {
    return size;
  }

  @Override
  public int getNumber() {
    return pageable.getPageNumber();
  }

  @Override
  public boolean hasNext() {
    return getNumber() + 1 < getTotalPages();
  }

  @Override
  public boolean isLast() {
    return !hasNext();
  }

  @Override
  public Pageable getNextPageable() {
    return pageable.next();
  }
}
