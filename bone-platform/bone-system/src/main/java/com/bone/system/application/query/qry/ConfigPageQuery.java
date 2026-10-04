package com.bone.system.application.query.qry;

import lombok.Data;

@Data
public class ConfigPageQuery {
  private String keyword;
  private String configType;
  private int page = 1;
  private int size = 10;
}
