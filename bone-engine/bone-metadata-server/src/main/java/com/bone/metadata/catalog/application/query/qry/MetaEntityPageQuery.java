package com.bone.metadata.catalog.application.query.qry;

import lombok.Data;

@Data
public class MetaEntityPageQuery {
  private int page = 1;
  private int size = 10;
  private String keyword;
  private Integer status;

  /**
   * 按 IAM 模块过滤（{@code meta_entity.module_id}）。
   *
   * <p>用途：建模工作台的「应用 → 模块 → 模型」树需按模块分页取数。缺省（null）= 不过滤，保持既有全量列表语义。
   */
  private Long moduleId;

  /**
   * 按实体类型过滤（{@code meta_entity.type}）：0-GENERATIVE / 1-RUNTIME 等。
   *
   * <p>用途：「更多筛选」的类型维度（前端 EntityManagement 已传，此前静默 no-op）。null = 不过滤。
   */
  private Integer type;

  /**
   * 按交付模式过滤（{@code meta_entity.deliveryMode}）：0-GENERATIVE / 1-RUNTIME。
   *
   * <p>用途：「更多筛选」的交付模式维度（前端 EntityManagement 已传，此前静默 no-op）。null = 不过滤。
   */
  private Integer deliveryMode;
}
