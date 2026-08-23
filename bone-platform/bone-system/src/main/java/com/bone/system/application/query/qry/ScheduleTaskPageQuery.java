package com.bone.system.application.query.qry;

import lombok.Data;

@Data
public class ScheduleTaskPageQuery {
  private String keyword;
  private String status;
  private int pageNum = 1;
  private int pageSize = 10;
}
