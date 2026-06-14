package com.bone.masterdata.infrastructure.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class ExcelConfig {
  // Excel 相关配置
  public static final int MAX_ROWS = 10000;
  public static final int MAX_COLUMNS = 50;
  public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
}
