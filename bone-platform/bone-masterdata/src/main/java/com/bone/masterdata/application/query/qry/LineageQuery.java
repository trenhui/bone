package com.bone.masterdata.application.query.qry;

import lombok.Data;

@Data
public class LineageQuery {
  /** 按目标实体查询上游。 */
  private String targetEntity;

  /** 按来源实体查询下游。 */
  private String sourceEntity;
}
