package com.bone.system.application.query.qry;

import lombok.Data;

/**
 * 告警记录分页的用例输入对象（application 层，E-13.1）。
 *
 * <p>三个过滤条件全部可缺省：缺省即「不过滤该维度」。不要把缺省值设计成某个具体枚举——那会让「查全部」变成 一个需要显式传参的特殊分支。
 */
@Data
public class AlertRecordPageQuery {
  private Long alertRuleId;
  private String alertLevel;
  private String status;
  private int pageNum = 1;
  private int pageSize = 10;
}
