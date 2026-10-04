package com.bone.system.application.query.qry;

import lombok.Data;

@Data
public class ScheduleTaskPageQuery {
  private String keyword;
  private String status;
  private int page = 1;
  private int size = 10;
}
