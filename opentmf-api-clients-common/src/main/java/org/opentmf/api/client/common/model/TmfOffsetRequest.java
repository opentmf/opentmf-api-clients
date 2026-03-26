package org.opentmf.api.client.common.model;

import java.util.Set;
import lombok.Getter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.util.MultiValueMap;

/**
 * Offset-based pagination request that carries an optional {@link TmfRequestContext} for field
 * projections, JSON filters, extra headers, and extra query parameters.
 */
@Getter
public class TmfOffsetRequest extends AbstractOffsetRequest {

  private TmfRequestContext requestContext;

  private TmfOffsetRequest(long offset, int limit, Sort sort, TmfRequestContext requestContext) {
    super(sort, offset, limit);
    this.requestContext = requestContext != null ? requestContext : new TmfRequestContext();
  }

  @Override
  public @NonNull Pageable next() {
    return new TmfOffsetRequest(getOffset() + getPageSize(), getPageSize(), getSort(), requestContext);
  }

  @Override
  public Pageable previous() {
    return !hasPrevious() ? this
        : new TmfOffsetRequest(getOffset() - getPageSize(), getPageSize(), getSort(), requestContext);
  }

  @Override
  public @NonNull Pageable first() {
    return new TmfOffsetRequest(0L, getPageSize(), getSort(), requestContext);
  }

  @Override
  public @NonNull Pageable withPage(int pageNumber) {
    return new TmfOffsetRequest((long) getPageSize() * pageNumber, getPageSize(), getSort(), requestContext);
  }

  // --- Convenience accessors delegating to requestContext ---

  public Set<String> getFields() {
    return requestContext.getFields();
  }

  public JsonFilter getJsonFilter() {
    return requestContext.getJsonFilter();
  }

  public String getJsonFilterQuery() {
    return requestContext.getJsonFilterQuery();
  }

  public JsonFilter.TYPE getJsonFilterType() {
    return requestContext.getJsonFilterType();
  }

  public MultiValueMap<String, String> getHeaders() {
    return requestContext.getHeaderParameters();
  }

  public MultiValueMap<String, String> getQueryParameters() {
    return requestContext.getQueryParameters();
  }

  // --- Static factory methods ---

  public static TmfOffsetRequest of() {
    return of(0, Integer.MAX_VALUE, Sort.unsorted());
  }

  public static TmfOffsetRequest of(Pageable pageable) {
    if (pageable instanceof TmfOffsetRequest t) return t;
    return of((int) pageable.getOffset(), pageable.getPageSize(), pageable.getSort());
  }

  public static TmfOffsetRequest of(int offset) {
    return of(offset, Integer.MAX_VALUE, Sort.unsorted());
  }

  public static TmfOffsetRequest of(int offset, int limit) {
    return of(offset, limit, Sort.unsorted());
  }

  public static TmfOffsetRequest of(Sort.Direction direction, String... properties) {
    return of(0, Integer.MAX_VALUE, Sort.by(direction, properties));
  }

  public static TmfOffsetRequest of(int offset, int limit, Sort.Direction direction, String... properties) {
    return of(offset, limit, Sort.by(direction, properties));
  }

  public static TmfOffsetRequest of(int offset, int limit, Sort sort) {
    return new TmfOffsetRequest(offset, limit, sort, null);
  }

  // --- Fluent mutators ---

  public TmfOffsetRequest withFields(String... fields) {
    requestContext.setFields(Set.of(fields));
    return this;
  }

  public TmfOffsetRequest withRequestContext(TmfRequestContext ctx) {
    this.requestContext = ctx;
    return this;
  }

  public TmfOffsetRequest withClientFilter(String query) {
    requestContext.setJsonFilter(JsonFilter.of(query, JsonFilter.TYPE.CLIENT));
    return this;
  }

  public TmfOffsetRequest withServerFilter(String query) {
    requestContext.setJsonFilter(JsonFilter.of(query, JsonFilter.TYPE.SERVER));
    return this;
  }

  public TmfOffsetRequest withLimit(int limit) {
    return new TmfOffsetRequest(getOffset(), limit, getSort(), requestContext);
  }

  public TmfOffsetRequest withHeaderParameters(MultiValueMap<String, String> headers) {
    requestContext.setHeaderParameters(headers);
    return this;
  }

  public TmfOffsetRequest withQueryParameters(MultiValueMap<String, String> params) {
    requestContext.setQueryParameters(params);
    return this;
  }
}
