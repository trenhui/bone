package com.bone.system.adapter.web.dto.request;

import lombok.Data;

/** 日志分页查询请求 */
@Data
public class LogPageReq {
  private String keyword;
  private String logLevel;
  private String serviceName;

  /** 起始时间（可选），格式 yyyy-MM-dd HH:mm:ss */
  private String startTime;

  /** 结束时间（可选），格式 yyyy-MM-dd HH:mm:ss */
  private String endTime;

  private int page = 1;
  private int size = 10;
}
