package com.bone.iam.application.query.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class DeptTreeDTO {
  private Long id;
  private String name;
  private Long parentId;
  private Integer orderNo;
  private Integer status;

  /** 部门负责人账户 ID（审批流 DEPT_HEAD 的数据源）；未指定时为 null。 */
  private Long leaderId;

  /** 部门负责人姓名（展示态派生值，不落库）。 */
  private String leaderName;

  private List<DeptTreeDTO> children = new ArrayList<>();
}
