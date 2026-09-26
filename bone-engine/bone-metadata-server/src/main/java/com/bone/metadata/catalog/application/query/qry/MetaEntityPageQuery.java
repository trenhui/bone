package com.bone.metadata.catalog.application.query.qry;

import lombok.Data;

@Data
public class MetaEntityPageQuery {
  private int pageNum = 1;
  private int pageSize = 10;
  private String keyword;
  private Integer status;

  /**
   * 按 IAM 模块过滤（{@code meta_entity.module_id}）。
   *
   * <p>用途：建模工作台的「应用 → 模块 → 模型」树需按模块分页取数。缺省（null）= 不过滤，保持既有全量列表语义。
   */
  private Long moduleId;
}
