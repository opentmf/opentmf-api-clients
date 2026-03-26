package org.opentmf.api.client.rest.helper;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResponseModel {

  private String id;
  private String name;
  private String description;
  private String href;
  private String state;
  private int randomNumber;
  private int orderNumber;
  private boolean even;
  private List<Characteristic> characteristics;
  private String createdDate;
  private String createdBy;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Characteristic {
    private String key;
    private String value;
  }
}
