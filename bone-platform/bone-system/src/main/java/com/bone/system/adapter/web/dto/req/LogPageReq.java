package com.bone.system.adapter.web.dto.req;

import lombok.Data;

/** 日志分页查询请求 */
@Data
public class LogPageReq {
  private String keyword;
  private String logLevel;
  private String serviceName;
  private int pageNum = 1;
  private int pageSize = 10;
}
