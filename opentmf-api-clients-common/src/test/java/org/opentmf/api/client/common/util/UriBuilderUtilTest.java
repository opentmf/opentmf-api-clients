package org.opentmf.api.client.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.EndpointConfig;
import org.opentmf.api.client.common.config.TmfApiClientsConfig.ServerConfig;
import org.opentmf.api.client.common.model.SubResourcePath;
import org.opentmf.api.client.common.model.TmfOffsetRequest;
import org.opentmf.api.client.common.model.TmfRequestContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.LinkedMultiValueMap;

class UriBuilderUtilTest {

  private static final String COMPOSITE_ID = "PhysicalSimResourceSpecification:(version=1)";
  private static final String EXPECTED_ENCODED_ID =
      "PhysicalSimResourceSpecification%3A%28version%3D1%29";

  private ServerConfig server;
  private EndpointConfig endpoint;

  @BeforeEach
  void setUp() {
    server = new ServerConfig();
    server.setBaseUrl("http://example.com");
    server.setContextPath("/tmf-api/v4");
    server.setClientRef("default");

    endpoint = new EndpointConfig();
    endpoint.setPath("/productOrder");
  }

  @Test
  void buildBaseUri_combinesParts() {
    URI uri = UriBuilderUtil.buildBaseUri(server, endpoint);
    assertThat(uri.toString()).isEqualTo("http://example.com/tmf-api/v4/productOrder");
  }

  @Test
  void buildUriWithId_appendsId() {
    URI uri = UriBuilderUtil.buildUriWithId(server, endpoint, "123");
    assertThat(uri.toString()).isEqualTo("http://example.com/tmf-api/v4/productOrder/123");
  }

  @Test
  void buildUriWithId_throwsOnNullId() {
    assertThatThrownBy(() -> UriBuilderUtil.buildUriWithId(server, endpoint, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void buildUriWithId_withNullCtx() {
    URI uri = UriBuilderUtil.buildUriWithId(server, endpoint, "42", (TmfRequestContext) null);
    assertThat(uri.toString()).isEqualTo("http://example.com/tmf-api/v4/productOrder/42");
  }

  @Test
  void buildUriWithId_withCtxFieldsAndFilter() {
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withServerJsonFilter("name=='test'")
        .withFields("id", "name")
        .withQueryParameters("extra", "val")
        .build();
    URI uri = UriBuilderUtil.buildUriWithId(server, endpoint, "42", ctx);
    String s = uri.toString();
    assertThat(s).contains("filter=");
    assertThat(s).contains("fields=");
    assertThat(s).contains("extra=val");
  }

  @Test
  void buildUri_withNullCtx() {
    URI uri = UriBuilderUtil.buildUri(server, endpoint, null);
    assertThat(uri.toString()).isEqualTo("http://example.com/tmf-api/v4/productOrder");
  }

  @Test
  void withPagination_addsOffsetAndLimit() {
    URI base = UriBuilderUtil.buildBaseUri(server, endpoint);
    URI paginated = UriBuilderUtil.withPagination(base, TmfOffsetRequest.of(10, 20));
    assertThat(paginated.toString()).contains("offset=10", "limit=20");
  }

  @Test
  void withPagination_addsSortParam() {
    URI base = UriBuilderUtil.buildBaseUri(server, endpoint);
    URI paginated = UriBuilderUtil.withPagination(
        base, TmfOffsetRequest.of(Sort.Direction.DESC, "name"));
    assertThat(paginated.toString()).contains("sort=-name");
  }

  @Test
  void withPagination_plainPageable_noSort() {
    URI base = UriBuilderUtil.buildBaseUri(server, endpoint);
    URI paginated = UriBuilderUtil.withPagination(base, PageRequest.of(0, 10));
    assertThat(paginated.toString()).contains("offset=0", "limit=10");
    assertThat(paginated.toString()).doesNotContain("sort=");
  }

  @Test
  void withPagination_withServerFilter() {
    URI base = UriBuilderUtil.buildBaseUri(server, endpoint);
    TmfOffsetRequest req = TmfOffsetRequest.of(0, 10).withServerFilter("name=='x'");
    URI paginated = UriBuilderUtil.withPagination(base, req);
    assertThat(paginated.toString()).contains("filter=");
  }

  @Test
  void withPagination_withFieldsAndQueryParameters() {
    URI base = UriBuilderUtil.buildBaseUri(server, endpoint);
    var params = new LinkedMultiValueMap<String, String>();
    params.add("extra", "param1");
    TmfOffsetRequest req = TmfOffsetRequest.of(0, 10)
        .withFields("id", "name")
        .withQueryParameters(params);
    URI paginated = UriBuilderUtil.withPagination(base, req);
    String s = paginated.toString();
    assertThat(s).contains("fields=");
    assertThat(s).contains("extra=param1");
  }

  @Test
  void withPagination_withClientFilter_doesNotAddFilterParam() {
    URI base = UriBuilderUtil.buildBaseUri(server, endpoint);
    TmfOffsetRequest req = TmfOffsetRequest.of(0, 10).withClientFilter("$.name");
    URI paginated = UriBuilderUtil.withPagination(base, req);
    assertThat(paginated.toString()).doesNotContain("filter=");
  }

  @Test
  void withContext_nullCtx_returnsBaseUnchanged() {
    URI base = UriBuilderUtil.buildBaseUri(server, endpoint);
    URI result = UriBuilderUtil.withContext(base, null);
    assertThat(result).isEqualTo(base);
  }

  @Test
  void withContext_addsFieldsAndFilter() {
    URI base = UriBuilderUtil.buildBaseUri(server, endpoint);
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withServerJsonFilter("state=='active'")
        .withFields("id", "state")
        .build();
    URI result = UriBuilderUtil.withContext(base, ctx);
    String s = result.toString();
    assertThat(s).contains("filter=");
    assertThat(s).contains("fields=");
  }

  @Test
  void withContext_clientFilter_doesNotAddFilterParam() {
    URI base = UriBuilderUtil.buildBaseUri(server, endpoint);
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withClientJsonFilter("$[?(@.x)]")
        .build();
    URI result = UriBuilderUtil.withContext(base, ctx);
    assertThat(result.toString()).doesNotContain("filter=");
  }

  @Test
  void buildUri_withRequestContextQueryParams() {
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withQueryParameters("status", "active")
        .build();
    URI uri = UriBuilderUtil.buildUri(server, endpoint, ctx);
    assertThat(uri.toString()).contains("status=active");
  }

  @Test
  void buildUriWithId_withServerJsonFilter() {
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withServerJsonFilter("$.name")
        .build();
    URI uri = UriBuilderUtil.buildUriWithId(server, endpoint, "42", ctx);
    assertThat(uri.toString()).contains("filter=");
  }

  @Test
  void buildUriWithId_compositeKey_encodesOnce() {
    URI uri = UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID);
    assertThat(uri.toString()).endsWith("/" + EXPECTED_ENCODED_ID);
  }

  @Test
  void buildUriWithId_compositeKey_withCtx_encodesOnce() {
    TmfRequestContext ctx = TmfRequestContext.builder().build();
    URI uri = UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID, ctx);
    assertThat(uri.toString()).endsWith("/" + EXPECTED_ENCODED_ID);
  }

  @Test
  void withContext_preservesPathEncoding() {
    URI base = UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID);
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withServerJsonFilter("state=='active'")
        .build();
    URI result = UriBuilderUtil.withContext(base, ctx);
    assertThat(result.toString()).contains("/" + EXPECTED_ENCODED_ID);
    assertThat(result.toString()).contains("filter=");
    assertThat(result.toString()).doesNotContain("%25");
  }

  @Test
  void withContext_emptyCtx_preservesPathEncoding() {
    URI base = UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID);
    URI result = UriBuilderUtil.withContext(base, TmfRequestContext.builder().build());
    assertThat(result.toString()).contains("/" + EXPECTED_ENCODED_ID);
    assertThat(result.toString()).doesNotContain("%25");
  }

  @Test
  void withPagination_preservesPathEncoding() {
    URI base = UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID);
    URI result = UriBuilderUtil.withPagination(base, TmfOffsetRequest.of(0, 10));
    assertThat(result.toString()).contains("/" + EXPECTED_ENCODED_ID);
    assertThat(result.toString()).contains("offset=0").contains("limit=10");
    assertThat(result.toString()).doesNotContain("%25");
  }

  // --- SUB-RESOURCE ---

  private static final SubResourcePath NESTED =
      SubResourcePath.none().append("/{orderId}/action/{action}/item", "o 1", "cancel");

  @Test
  void sub_buildUriWithId_expandsDeepNestingAndEncodesVars() {
    URI uri = UriBuilderUtil.buildUriWithId(server, endpoint, "it:7", NESTED);
    assertThat(uri.toString()).isEqualTo(
        "http://example.com/tmf-api/v4/productOrder/o%201/action/cancel/item/it%3A7");
  }

  @Test
  void sub_buildBaseUri_expandsNestedCollection() {
    URI uri = UriBuilderUtil.buildBaseUri(server, endpoint, NESTED);
    assertThat(uri.toString()).isEqualTo(
        "http://example.com/tmf-api/v4/productOrder/o%201/action/cancel/item");
  }

  @Test
  void sub_compositeKeyVar_encodesIdenticallyToPlainId() {
    URI plain = UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID);
    SubResourcePath sub = SubResourcePath.none().append("/{parentId}/child", COMPOSITE_ID);
    URI nested = UriBuilderUtil.buildUriWithId(server, endpoint, "c1", sub);
    assertThat(plain.toString()).endsWith("/" + EXPECTED_ENCODED_ID);
    assertThat(nested.toString()).isEqualTo(
        "http://example.com/tmf-api/v4/productOrder/" + EXPECTED_ENCODED_ID + "/child/c1");
  }

  @Test
  void sub_braceBearingVar_encodesWithoutTemplateInjection() {
    SubResourcePath sub = SubResourcePath.none().append("/{parentId}/child", "n{a}me");
    URI uri = UriBuilderUtil.buildUriWithId(server, endpoint, "c1", sub);
    assertThat(uri.toString()).isEqualTo(
        "http://example.com/tmf-api/v4/productOrder/n%7Ba%7Dme/child/c1");
  }

  @Test
  void sub_traversalVar_isContained() {
    SubResourcePath sub = SubResourcePath.none().append("/{parentId}/child", "../../admin");
    URI uri = UriBuilderUtil.buildUriWithId(server, endpoint, "c1", sub);
    assertThat(uri.toString()).isEqualTo(
        "http://example.com/tmf-api/v4/productOrder/..%2F..%2Fadmin/child/c1");
  }

  @Test
  void sub_withCtx_doesNotDoubleEncodeExpandedVars() {
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withServerJsonFilter("state=='active'")
        .withFields("id", "state")
        .build();
    SubResourcePath sub = SubResourcePath.none().append("/{parentId}/child", COMPOSITE_ID);
    URI uri = UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID, ctx, sub);
    String s = uri.toString();
    assertThat(s).contains("/" + EXPECTED_ENCODED_ID + "/child/" + EXPECTED_ENCODED_ID);
    assertThat(s).contains("filter=");
    assertThat(s).contains("fields=");
    assertThat(s).doesNotContain("%25");
  }

  @Test
  void sub_buildUri_withCtxQueryParams() {
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withQueryParameters("status", "active")
        .build();
    URI uri = UriBuilderUtil.buildUri(server, endpoint, ctx, NESTED);
    assertThat(uri.toString()).startsWith(
        "http://example.com/tmf-api/v4/productOrder/o%201/action/cancel/item");
    assertThat(uri.toString()).contains("status=active");
  }

  @Test
  void sub_emptySuffix_isByteIdenticalToLegacyBuilders() {
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withServerJsonFilter("name=='test'")
        .withFields("id", "name")
        .withQueryParameters("extra", "val")
        .build();
    SubResourcePath none = SubResourcePath.none();

    assertThat(UriBuilderUtil.buildBaseUri(server, endpoint, none))
        .isEqualTo(UriBuilderUtil.buildBaseUri(server, endpoint));
    assertThat(UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID, none))
        .isEqualTo(UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID));
    assertThat(UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID, ctx, none))
        .isEqualTo(UriBuilderUtil.buildUriWithId(server, endpoint, COMPOSITE_ID, ctx));
    assertThat(UriBuilderUtil.buildUri(server, endpoint, ctx, none))
        .isEqualTo(UriBuilderUtil.buildUri(server, endpoint, ctx));
  }

  @Test
  void sub_withPagination_keepsQueryParamsEncodedOnce() {
    URI base = UriBuilderUtil.buildBaseUri(server, endpoint, NESTED);
    TmfOffsetRequest req = TmfOffsetRequest.of(0, 10).withServerFilter("name=='x y'");
    URI result = UriBuilderUtil.withPagination(base, req);
    String s = result.toString();
    assertThat(s).contains("/o%201/action/cancel/item");
    assertThat(s).contains("offset=0").contains("limit=10").contains("filter=");
    assertThat(s).doesNotContain("%25");
  }

  @Test
  void sub_withContext_keepsQueryParamsEncodedOnce() {
    URI base = UriBuilderUtil.buildBaseUri(server, endpoint, NESTED);
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withServerJsonFilter("state=='active'")
        .withFields("id", "state")
        .build();
    URI result = UriBuilderUtil.withContext(base, ctx);
    String s = result.toString();
    assertThat(s).contains("/o%201/action/cancel/item");
    assertThat(s).contains("filter=").contains("fields=");
    assertThat(s).doesNotContain("%25");
  }
}
