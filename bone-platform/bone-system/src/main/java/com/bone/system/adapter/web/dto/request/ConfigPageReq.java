package com.bone.system.adapter.web.dto.request;

import lombok.Data;

/** 配置分页查询请求 */
@Data
public class ConfigPageReq {
  private String keyword;
  private String configType;
  private int pageNum = 1;
  private int pageSize = 10;
}
