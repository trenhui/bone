package com.bone.system.adapter.web.dto.request;

import lombok.Data;

/** 配置分页查询请求 */
@Data
public class ConfigPageReq {
  private String keyword;
  private String configType;
  private int page = 1;
  private int size = 10;
}
