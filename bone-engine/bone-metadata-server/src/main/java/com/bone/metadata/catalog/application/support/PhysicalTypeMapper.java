package com.bone.metadata.catalog.application.support;

import com.bone.metadata.catalog.domain.model.physical.PhysicalTableColumn;

/**
 * 物理列类型 → 元数据模型字段类型映射（逆向建模专用）。
 *
 * <p>映射口径：与 {@code JdbcPhysicalStructureGatewayAdapter#typeCategory} 的类型大类保持一致（数值 / 字符串 / 时间 /
 * 布尔），确保「导入存量表 → 发布」链路不会因类型漂移被拦截——导入的类型与物理列同大类，是逆向建模能被发布接受的前提。
 *
 * <p>长度语义：字符串类保留物理长度（VARCHAR(200) → length=200），避免模型声明与物理表不一致； 数值类的 {@code precision}
 * 记录物理总位数，仅作语义留存——DDL 生成器当前固定 DECIMAL(20,6)， 精度可配（O2）待 L3 评审后落地。
 */
public final class PhysicalTypeMapper {

  private PhysicalTypeMapper() {}

  /** MySQL / H2-MySQL 的 information_schema.DATA_TYPE → MetaField.type。 */
  public static String toMetaType(String dataType) {
    return switch (dataType == null ? "" : dataType.toUpperCase()) {
      case "BIGINT" -> "LONG";
      case "INT", "INTEGER", "MEDIUMINT", "SMALLINT", "TINYINT" -> "INT";
      case "DECIMAL", "NUMERIC" -> "DECIMAL";
      case "DOUBLE" -> "DOUBLE";
      case "FLOAT" -> "FLOAT";
      case "VARCHAR", "CHAR", "NVARCHAR" -> "STRING";
      case "TEXT", "MEDIUMTEXT", "LONGTEXT", "TINYTEXT" -> "TEXT";
      case "JSON" -> "JSON";
      case "DATE" -> "DATE";
      case "DATETIME", "TIMESTAMP" -> "DATETIME";
      case "TIME" -> "TIME";
      case "BOOLEAN", "BOOL" -> "BOOLEAN";
      default -> "STRING";
    };
  }

  /** MySQL 的 TINYINT(1) 是事实上的布尔列（H2 兼容模式下同理），导入时按布尔建模。 */
  public static String toMetaType(PhysicalTableColumn column) {
    String dataType = column.dataType() == null ? "" : column.dataType().toUpperCase();
    if ("TINYINT".equals(dataType)
        && column.numericPrecision() != null
        && column.numericPrecision() == 1) {
      return "BOOLEAN";
    }
    return toMetaType(dataType);
  }

  /** 字符串长度：VARCHAR 取物理长度；TEXT / JSON 无长度语义（length=null）。 */
  public static Integer toLength(PhysicalTableColumn column) {
    String metaType = toMetaType(column);
    if (!"STRING".equals(metaType)) {
      return null;
    }
    Integer len = column.charLength();
    return len == null || len <= 0 ? null : len;
  }
}
