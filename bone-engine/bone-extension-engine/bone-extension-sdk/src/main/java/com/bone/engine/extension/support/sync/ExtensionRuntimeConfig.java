package com.bone.engine.extension.support.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** Studio {@code Extension.config} JSON 中与运行时路由相关的字段。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtensionRuntimeConfig {

  /** 覆盖扩展 code（须与 @Extension.name 或 EXT_类名 一致） */
  private String code;

  private String condition;
  private String env;
  private Integer traffic;
  private Integer weight;
  private Boolean defaultImpl;
}
