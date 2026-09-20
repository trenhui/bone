package com.bone.system.adapter.web.dto.request;

import lombok.Data;

/**
 * 日志导出请求（MVP-09）。
 *
 * <p>时间字段同时接受 {@code yyyy-MM-dd HH:mm:ss} 与 ISO {@code yyyy-MM-ddTHH:mm:ss}：前者是页面日期控件的常见 输出，后者是
 * OpenAPI 的默认序列化形态，只认一种会让另一侧的请求静默变成「不带时间条件」。
 */
@Data
public class LogExportReq {
  private String service;
  private String level;
  private String startTime;
  private String endTime;
}
