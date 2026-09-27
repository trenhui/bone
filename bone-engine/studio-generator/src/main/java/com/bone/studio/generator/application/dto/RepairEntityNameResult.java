package com.bone.studio.generator.application.dto;

import java.util.List;

/** 运维修复结果：是否执行、受影响行数、示例（原值 → 新值）。 */
public class RepairEntityNameResult {

  private final boolean executed;
  private final int affectedCount;
  private final List<RepairEntityNameExample> examples;

  public RepairEntityNameResult(
      boolean executed, int affectedCount, List<RepairEntityNameExample> examples) {
    this.executed = executed;
    this.affectedCount = affectedCount;
    this.examples = examples;
  }

  public boolean isExecuted() {
    return executed;
  }

  public int getAffectedCount() {
    return affectedCount;
  }

  public List<RepairEntityNameExample> getExamples() {
    return examples;
  }

  /** 单条修复示例。 */
  public static class RepairEntityNameExample {
    private final Long id;
    private final String dataSourceId;
    private final String originalTableName;
    private final String oldEntityName;
    private final String newEntityName;

    public RepairEntityNameExample(
        Long id,
        String dataSourceId,
        String originalTableName,
        String oldEntityName,
        String newEntityName) {
      this.id = id;
      this.dataSourceId = dataSourceId;
      this.originalTableName = originalTableName;
      this.oldEntityName = oldEntityName;
      this.newEntityName = newEntityName;
    }

    public Long getId() {
      return id;
    }

    public String getDataSourceId() {
      return dataSourceId;
    }

    public String getOriginalTableName() {
      return originalTableName;
    }

    public String getOldEntityName() {
      return oldEntityName;
    }

    public String getNewEntityName() {
      return newEntityName;
    }
  }
}
