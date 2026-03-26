package org.opentmf.api.client.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.LinkedMultiValueMap;

class TmfOffsetRequestTest {

  @Test
  void of_defaultValues() {
    TmfOffsetRequest req = TmfOffsetRequest.of();
    assertThat(req.getOffset()).isZero();
    assertThat(req.getPageSize()).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void of_withOffset() {
    TmfOffsetRequest req = TmfOffsetRequest.of(100);
    assertThat(req.getOffset()).isEqualTo(100);
    assertThat(req.getPageNumber()).isEqualTo(0);
  }

  @Test
  void of_withOffsetAndLimit() {
    TmfOffsetRequest req = TmfOffsetRequest.of(50, 25);
    assertThat(req.getOffset()).isEqualTo(50);
    assertThat(req.getPageSize()).isEqualTo(25);
    assertThat(req.getPageNumber()).isEqualTo(2);
  }

  @Test
  void of_withDirection() {
    TmfOffsetRequest req = TmfOffsetRequest.of(Sort.Direction.DESC, "name");
    assertThat(req.getOffset()).isZero();
    assertThat(req.getSort()).isNotEmpty();
  }

  @Test
  void of_withOffsetLimitDirection() {
    TmfOffsetRequest req = TmfOffsetRequest.of(5, 10, Sort.Direction.ASC, "id");
    assertThat(req.getOffset()).isEqualTo(5);
    assertThat(req.getPageSize()).isEqualTo(10);
    assertThat(req.getSort()).isNotEmpty();
  }

  @Test
  void of_fromTmfOffsetRequest_returnsSameInstance() {
    TmfOffsetRequest original = TmfOffsetRequest.of(10, 5);
    TmfOffsetRequest result = TmfOffsetRequest.of(original);
    assertThat(result).isSameAs(original);
  }

  @Test
  void of_fromPlainPageable_createsNew() {
    var pageable = PageRequest.of(2, 10, Sort.by("name"));
    TmfOffsetRequest result = TmfOffsetRequest.of(pageable);
    assertThat(result.getOffset()).isEqualTo(pageable.getOffset());
    assertThat(result.getPageSize()).isEqualTo(10);
  }

  @Test
  void next_incrementsOffset() {
    TmfOffsetRequest req = TmfOffsetRequest.of(0, 10);
    TmfOffsetRequest next = (TmfOffsetRequest) req.next();
    assertThat(next.getOffset()).isEqualTo(10);
  }

  @Test
  void previous_decrementsOffset() {
    TmfOffsetRequest req = TmfOffsetRequest.of(20, 10);
    assertThat(req.hasPrevious()).isTrue();
    var prev = req.previous();
    assertThat(prev.getOffset()).isEqualTo(10);
  }

  @Test
  void previous_atStart_returnsSelf() {
    TmfOffsetRequest req = TmfOffsetRequest.of(0, 10);
    assertThat(req.hasPrevious()).isFalse();
    var prev = req.previousOrFirst();
    assertThat(prev.getOffset()).isZero();
  }

  @Test
  void first_returnsZeroOffset() {
    TmfOffsetRequest req = TmfOffsetRequest.of(50, 10);
    var first = req.first();
    assertThat(first.getOffset()).isZero();
    assertThat(first.getPageSize()).isEqualTo(10);
  }

  @Test
  void withPage_setsCorrectOffset() {
    TmfOffsetRequest req = TmfOffsetRequest.of(0, 10);
    var page3 = req.withPage(3);
    assertThat(page3.getOffset()).isEqualTo(30);
  }

  @Test
  void withLimit_createsNewInstance() {
    TmfOffsetRequest req = TmfOffsetRequest.of(0, 10);
    TmfOffsetRequest limited = req.withLimit(5);
    assertThat(limited.getPageSize()).isEqualTo(5);
    assertThat(req.getPageSize()).isEqualTo(10);
  }

  @Test
  void withClientFilter_setsFilter() {
    TmfOffsetRequest req = TmfOffsetRequest.of().withClientFilter("$.name");
    assertThat(req.getJsonFilterType()).isEqualTo(JsonFilter.TYPE.CLIENT);
    assertThat(req.getJsonFilterQuery()).isEqualTo("$.name");
    assertThat(req.getJsonFilter()).isNotNull();
  }

  @Test
  void withServerFilter_setsFilter() {
    TmfOffsetRequest req = TmfOffsetRequest.of().withServerFilter("name eq 'test'");
    assertThat(req.getJsonFilterType()).isEqualTo(JsonFilter.TYPE.SERVER);
  }

  @Test
  void withFields_setsFields() {
    TmfOffsetRequest req = TmfOffsetRequest.of().withFields("id", "name");
    assertThat(req.getFields()).containsExactlyInAnyOrder("id", "name");
  }

  @Test
  void withRequestContext_replacesContext() {
    TmfRequestContext ctx = TmfRequestContext.builder()
        .withFields("id")
        .build();
    TmfOffsetRequest req = TmfOffsetRequest.of().withRequestContext(ctx);
    assertThat(req.getFields()).containsExactly("id");
    assertThat(req.getRequestContext()).isSameAs(ctx);
  }

  @Test
  void withHeaderParameters_setsHeaders() {
    var headers = new LinkedMultiValueMap<String, String>();
    headers.add("X-Custom", "val");
    TmfOffsetRequest req = TmfOffsetRequest.of().withHeaderParameters(headers);
    assertThat(req.getHeaders()).containsKey("X-Custom");
  }

  @Test
  void withQueryParameters_setsParams() {
    var params = new LinkedMultiValueMap<String, String>();
    params.add("status", "active");
    TmfOffsetRequest req = TmfOffsetRequest.of().withQueryParameters(params);
    assertThat(req.getQueryParameters()).containsKey("status");
  }

  @Test
  void constructor_throwsOnNegativeOffset() {
    assertThatThrownBy(() -> TmfOffsetRequest.of(-1, 10))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_throwsOnZeroLimit() {
    assertThatThrownBy(() -> TmfOffsetRequest.of(0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constructor_nullSort_defaultsToUnsorted() {
    TmfOffsetRequest req = TmfOffsetRequest.of(0, 10, (Sort) null);
    assertThat(req.getSort()).isEqualTo(Sort.unsorted());
  }
}
