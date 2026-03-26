package org.opentmf.api.client.common.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Carries optional per-request context: field projections, JSON filter expressions, extra headers,
 * and extra query parameters. Use {@link #builder()} to construct instances fluently.
 */
@Getter
@Setter
public class TmfRequestContext {

  private Set<String> fields;
  private JsonFilter jsonFilter;
  private MultiValueMap<String, String> headerParameters;
  private MultiValueMap<String, String> queryParameters;

  private TmfRequestContext(Builder builder) {
    this.fields = builder.fields;
    this.jsonFilter = builder.jsonFilter;
    this.headerParameters = builder.headerParameters;
    this.queryParameters = builder.queryParameters;
  }

  /** Default constructor for frameworks that require a no-arg constructor. */
  public TmfRequestContext() {}

  public String getJsonFilterQuery() {
    return jsonFilter != null ? jsonFilter.getQuery() : null;
  }

  public JsonFilter.TYPE getJsonFilterType() {
    return jsonFilter != null ? jsonFilter.getType() : null;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(@NonNull TmfRequestContext ctx) {
    var b = builder();
    if (ctx.fields != null) b.withFields(ctx.fields);
    if (ctx.jsonFilter != null) b.withJsonFilter(ctx.jsonFilter);
    if (ctx.headerParameters != null) b.withHeaderValues(ctx.headerParameters);
    if (ctx.queryParameters != null) b.withQueryParameters(ctx.queryParameters);
    return b;
  }

  public static class Builder {

    private static final String FILTER_ONCE = "JsonFilter can be set only once.";

    private Set<String> fields;
    private JsonFilter jsonFilter;
    private MultiValueMap<String, String> headerParameters;
    private MultiValueMap<String, String> queryParameters;

    public Builder withFields(@NonNull Set<String> fields) {
      if (this.fields == null) this.fields = new LinkedHashSet<>(fields.size());
      this.fields.addAll(fields);
      return this;
    }

    public Builder withFields(@NonNull String... fields) {
      if (this.fields == null) this.fields = new LinkedHashSet<>(fields.length);
      this.fields.addAll(Set.of(fields));
      return this;
    }

    public Builder withServerJsonFilter(String query) {
      Assert.isNull(this.jsonFilter, FILTER_ONCE);
      this.jsonFilter = JsonFilter.of(query, JsonFilter.TYPE.SERVER);
      return this;
    }

    public Builder withClientJsonFilter(String query) {
      Assert.isNull(this.jsonFilter, FILTER_ONCE);
      this.jsonFilter = JsonFilter.of(query, JsonFilter.TYPE.CLIENT);
      return this;
    }

    Builder withJsonFilter(JsonFilter filter) {
      Assert.isNull(this.jsonFilter, FILTER_ONCE);
      this.jsonFilter = filter;
      return this;
    }

    public Builder withHeaderValues(@NonNull MultiValueMap<String, String> headers) {
      if (this.headerParameters == null) this.headerParameters = headers;
      else this.headerParameters.addAll(headers);
      return this;
    }

    public Builder withHeaderValues(String key, String... values) {
      if (this.headerParameters == null) this.headerParameters = new LinkedMultiValueMap<>();
      this.headerParameters.addAll(key, List.of(values));
      return this;
    }

    public Builder withQueryParameters(@NonNull MultiValueMap<String, String> params) {
      if (this.queryParameters == null) this.queryParameters = params;
      else this.queryParameters.addAll(params);
      return this;
    }

    public Builder withQueryParameters(String key, String... values) {
      if (this.queryParameters == null) this.queryParameters = new LinkedMultiValueMap<>();
      this.queryParameters.addAll(key, List.of(values));
      return this;
    }

    public TmfRequestContext build() {
      return new TmfRequestContext(this);
    }
  }
}
