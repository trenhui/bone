package com.bone.metadata.catalog.domain.model.physical;

import java.util.List;

/**
 * 物理结构对齐计划/结果（MVP-11）。
 *
 * <p>同时承担「dry-run 计划」与「执行结果」两个语义：inspect 产出的 plan 中 {@code executed=0}；align 返回的 plan 记录 执行的
 * DDL（幂等：结构已一致时 statements 为空、executed=0）。
 */
public record PhysicalStructurePlan(
    String entityCode,
    String tableName,
    /** true=整表不存在，将执行 CREATE TABLE；false=表存在，仅可能 ADD COLUMN。 */
    boolean createTable,
    /** 待执行 DDL（MySQL 方言；H2 兼容 MySQL 模式可执行）。 */
    List<String> statements,
    /** align 时实际执行的条数；inspect 恒为 0。 */
    int executed,
    /** 见 {@code PhysicalStructureAlignerConfig} 常量：READY / DRIFT_DETECTED / ... */
    String status,
    String message) {

  public static final String STATUS_READY = "READY";
  public static final String STATUS_CREATED = "CREATED";
  public static final String STATUS_ALIGNED = "ALIGNED";
  public static final String STATUS_DRIFT_DETECTED = "DRIFT_DETECTED";
  public static final String STATUS_NOT_RUNTIME = "NOT_RUNTIME";
  public static final String STATUS_SKIPPED = "SKIPPED";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_DROPPED = "DROPPED";
  public static final String STATUS_RECONCILED = "RECONCILED";
  public static final String STATUS_REFUSED = "REFUSED";

  /** 便捷工厂：非整表场景（建表 createTable=false）的 DROP / RECONCILE 结果构造。 */
  public static PhysicalStructurePlan of(
      String entityCode,
      String tableName,
      List<String> statements,
      int executed,
      String status,
      String message) {
    return new PhysicalStructurePlan(
        entityCode, tableName, false, statements, executed, status, message);
  }

  public PhysicalStructurePlan withExecuted(int newExecuted) {
    return new PhysicalStructurePlan(
        entityCode, tableName, createTable, statements, newExecuted, status, message);
  }

  public PhysicalStructurePlan withResult(String newStatus, String newMessage) {
    return new PhysicalStructurePlan(
        entityCode, tableName, createTable, statements, executed, newStatus, newMessage);
  }
}
