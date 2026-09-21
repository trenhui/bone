package com.bone.system.adapter.web.dto.request;

import lombok.Data;

/** 定时任务分页查询请求（adapter 协议对象，E-13.1）。 */
@Data
public class ScheduleTaskPageReq {
  private String keyword;
  private String status;
  private int pageNum = 1;
  private int pageSize = 10;
}
