package org.opentmf.api.client.reactive.helper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestResponseClass {

  private String id;
  private String name;
  private String description;
}
