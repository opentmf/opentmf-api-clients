package org.opentmf.api.client.rest.helper;

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
