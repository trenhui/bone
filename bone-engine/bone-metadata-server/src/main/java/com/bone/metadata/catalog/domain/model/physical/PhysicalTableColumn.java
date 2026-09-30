package com.bone.metadata.catalog.domain.model.physical;

/**
 * 存量物理表的一列（逆向建模采集结果，纯只读快照）。
 *
 * <p>与 {@link PhysicalStructurePlan} 关注「模型 → 物理」的正向对齐不同，本类型描述「物理 → 模型」的反向采集： 用于把已存在的业务表（如
 * bone-blueprint 的 {@code t_order}）一键导入为元数据目录实体。
 *
 * @param columnName 物理列名（原样，保留大小写）
 * @param dataType 物理类型（information_schema.DATA_TYPE 大写，如 DECIMAL / VARCHAR / BIGINT）
 * @param charLength 字符长度（VARCHAR 等；无长度时为 null）
 * @param numericPrecision 数值精度（DECIMAL 总位数；非数值为 null）
 * @param numericScale 数值标度（DECIMAL 小数位；非数值为 null）
 * @param nullable 是否可空
 * @param comment 列注释（可能为空串）
 * @param reserved 是否平台保留列（id/tenant_id/version/deleted/审计四列）——保留列由平台托管，导入时不得建模为业务字段
 */
public record PhysicalTableColumn(
    String columnName,
    String dataType,
    Integer charLength,
    Integer numericPrecision,
    Integer numericScale,
    boolean nullable,
    String comment,
    boolean reserved) {

  /** 是否数值型（DECIMAL 家族需要精度语义，导入时保留到 MetaField.precision）。 */
  public boolean isDecimal() {
    String t = dataType == null ? "" : dataType.toUpperCase();
    return "DECIMAL".equals(t) || "NUMERIC".equals(t);
  }
}
