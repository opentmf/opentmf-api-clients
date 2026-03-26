package org.opentmf.api.client.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OffsetPageTest {

  @Test
  void firstPage_withKnownTotal() {
    var pageable = TmfOffsetRequest.of(0, 10);
    var page = new OffsetPage<>(40L, 10, pageable, List.of("a", "b"));

    assertThat(page.getTotalElements()).isEqualTo(40L);
    assertThat(page.getTotalPages()).isEqualTo(4);
    assertThat(page.getSize()).isEqualTo(10);
    assertThat(page.getNumber()).isZero();
    assertThat(page.hasNext()).isTrue();
    assertThat(page.isLast()).isFalse();
    assertThat(page.getContent()).containsExactly("a", "b");
  }

  @Test
  void lastPage_isCorrect() {
    var pageable = TmfOffsetRequest.of(30, 10);
    var page = new OffsetPage<>(40L, 10, pageable, List.of("x"));

    assertThat(page.getTotalPages()).isEqualTo(4);
    assertThat(page.getNumber()).isEqualTo(3);
    assertThat(page.hasNext()).isFalse();
    assertThat(page.isLast()).isTrue();
  }

  @Test
  void singleItemPage_correctPageCount() {
    var pageable = TmfOffsetRequest.of(0, 10);
    var page = new OffsetPage<>(1L, 1, pageable, List.of("only"));

    assertThat(page.getTotalPages()).isEqualTo(1);
    assertThat(page.getNumber()).isZero();
    assertThat(page.hasNext()).isFalse();
    assertThat(page.isLast()).isTrue();
  }

  @Test
  void emptyPage() {
    var pageable = TmfOffsetRequest.of(0, 10);
    var page = new OffsetPage<>(0L, 0, pageable, List.of());

    assertThat(page.getTotalElements()).isZero();
    assertThat(page.getTotalPages()).isZero();
    assertThat(page.getSize()).isZero();
    assertThat(page.hasNext()).isFalse();
    assertThat(page.isLast()).isTrue();
  }

  @Test
  void nextPageable_advancesCorrectly() {
    var pageable = TmfOffsetRequest.of(0, 10);
    var page = new OffsetPage<>(20L, 10, pageable, List.of("a"));

    var next = page.getNextPageable();
    assertThat(next.getOffset()).isEqualTo(10);
    assertThat(next.getPageSize()).isEqualTo(10);
  }

  @Test
  void unevenTotal_roundsUpPages() {
    var pageable = TmfOffsetRequest.of(0, 10);
    var page = new OffsetPage<>(25L, 10, pageable, List.of("a"));

    assertThat(page.getTotalPages()).isEqualTo(3);
  }

  @Test
  void effectiveLimit_adjustsOnFirstPage_whenItemCountDiffersFromPageSize() {
    var pageable = TmfOffsetRequest.of(0, 100);
    var page = new OffsetPage<>(7L, 7, pageable, List.of());

    assertThat(page.getTotalPages()).isEqualTo(1);
    assertThat(page.getNextPageable().getPageSize()).isEqualTo(7);
  }
}
