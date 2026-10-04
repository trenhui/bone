package com.bone.system.application.query.qry;

import lombok.Data;

@Data
public class LogPageQuery {
  private String keyword;
  private String logLevel;
  private String serviceName;
  private int page = 1;
  private int size = 10;
}
