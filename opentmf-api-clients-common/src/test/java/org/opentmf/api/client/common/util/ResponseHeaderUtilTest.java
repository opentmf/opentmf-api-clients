package org.opentmf.api.client.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class ResponseHeaderUtilTest {

  @Test
  void parseItemCount_standardRange() {
    assertThat(ResponseHeaderUtil.parseItemCount("items 0-9/100")).isEqualTo(10);
  }

  @Test
  void parseItemCount_singleItem() {
    assertThat(ResponseHeaderUtil.parseItemCount("items 0-0/1")).isEqualTo(0);
  }

  @Test
  void parseItemCount_unknownTotal() {
    assertThat(ResponseHeaderUtil.parseItemCount("items 0-4/*")).isEqualTo(5);
  }

  @Test
  void parseItemCount_noResults_starRange() {
    assertThat(ResponseHeaderUtil.parseItemCount("items */*")).isEqualTo(0);
  }

  @Test
  void parseItemCount_zeroTotal() {
    assertThat(ResponseHeaderUtil.parseItemCount("items 0-0/0")).isEqualTo(0);
  }

  @Test
  void parseItemCount_malformed() {
    assertThat(ResponseHeaderUtil.parseItemCount("not-a-range")).isEqualTo(0);
  }

  @Test
  void parseItemCount_secondPage() {
    assertThat(ResponseHeaderUtil.parseItemCount("items 10-19/100")).isEqualTo(10);
  }

  @Test
  void parseItemCount_malformedRange_missingDash() {
    assertThat(ResponseHeaderUtil.parseItemCount("items 5/20")).isEqualTo(0);
  }

  @Test
  void getXTotalCount_withHeader() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(TmfApiClientConstants.HEADER_X_TOTAL_COUNT, "42");
    ResponseEntity<String> entity = ResponseEntity.ok().headers(headers).body("test");
    assertThat(ResponseHeaderUtil.getXTotalCount(entity)).isEqualTo(42L);
  }

  @Test
  void getXTotalCount_withoutHeader() {
    ResponseEntity<String> entity = ResponseEntity.ok().body("test");
    assertThat(ResponseHeaderUtil.getXTotalCount(entity)).isZero();
  }

  @Test
  void getContentRangeItemCount_withHeader() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(TmfApiClientConstants.HEADER_CONTENT_RANGE, "items 0-9/50");
    ResponseEntity<String> entity = ResponseEntity.ok().headers(headers).body("test");
    assertThat(ResponseHeaderUtil.getContentRangeItemCount(entity)).isEqualTo(10);
  }

  @Test
  void getContentRangeItemCount_withoutHeader() {
    ResponseEntity<String> entity = ResponseEntity.ok().body("test");
    assertThat(ResponseHeaderUtil.getContentRangeItemCount(entity)).isZero();
  }

  @Test
  void firstHeader_missingHeader_returnsNull() {
    ResponseEntity<String> entity = ResponseEntity.ok().body("test");
    assertThat(ResponseHeaderUtil.firstHeader(entity, "X-Missing")).isNull();
  }

  @Test
  void firstHeader_presentHeader_returnsTrimmedValue() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Custom", "  value  ");
    ResponseEntity<String> entity = ResponseEntity.ok().headers(headers).body("test");
    assertThat(ResponseHeaderUtil.firstHeader(entity, "X-Custom")).isEqualTo("value");
  }
}
