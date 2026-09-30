package com.bone.metadata.catalog.domain.model.physical;

import java.util.List;

/**
 * 存量物理表结构快照（逆向建模输入）。
 *
 * <p>真实场景：企业已有业务表（ERP / 自研交易库）要先纳入元数据平台治理，再由平台增量扩展字段。 若平台只支持「模型优先、发布建表」，存量表就必须人工逐字段重录—— 这是业界元数据平台（如
 * Alation / Purview 的 crawl、Salesforce 的 schema sync）必备的逆向采集能力。
 *
 * @param tableName 物理表名
 * @param exists 表是否存在（不存在时导入应直接拒绝，避免建出与物理库脱节的空模型）
 * @param columns 列清单（按物理顺序；保留列同样列出，由导入用例决定是否跳过）
 */
public record PhysicalTableSnapshot(
    String tableName, boolean exists, List<PhysicalTableColumn> columns) {

  public static PhysicalTableSnapshot missing(String tableName) {
    return new PhysicalTableSnapshot(tableName, false, List.of());
  }

  /** 可建模列：排除保留列（平台托管，不作为业务字段）。 */
  public List<PhysicalTableColumn> modelableColumns() {
    return columns.stream().filter(c -> !c.reserved()).toList();
  }

  public int reservedCount() {
    return (int) columns.stream().filter(PhysicalTableColumn::reserved).count();
  }
}
