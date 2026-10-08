package com.bone.metadata.sdk.query.context;

import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.Collections;
import java.util.List;

public class AggregationContext {
  private final TableMetadata tableMetadata;
  private final List<String> aggregations;
  private final Criteria<?> criteria;
  private final List<String> groupByFields;
  private final List<String> havingConditions;
  private final boolean includeDeleted;
  private final Integer page;
  private final Integer size;

  public AggregationContext(
      TableMetadata tableMetadata,
      List<String> aggregations,
      Criteria<?> criteria,
      List<String> groupByFields,
      List<String> havingConditions,
      boolean includeDeleted) {
    this(
        tableMetadata,
        aggregations,
        criteria,
        groupByFields,
        havingConditions,
        includeDeleted,
        null,
        null);
  }

  public AggregationContext(
      TableMetadata tableMetadata,
      List<String> aggregations,
      Criteria<?> criteria,
      List<String> groupByFields,
      List<String> havingConditions,
      boolean includeDeleted,
      Integer page,
      Integer size) {
    this.tableMetadata = tableMetadata;
    this.aggregations = aggregations != null ? aggregations : Collections.emptyList();
    this.criteria = criteria;
    this.groupByFields = groupByFields != null ? groupByFields : Collections.emptyList();
    this.havingConditions = havingConditions != null ? havingConditions : Collections.emptyList();
    this.includeDeleted = includeDeleted;
    this.page = page;
    this.size = size;
  }

  /** 聚合表达式列表，比如 ["COUNT(*)", "SUM(amount)"] */
  public List<String> getAggregations() {
    return aggregations;
  }

  /** 过滤条件 */
  public Criteria<?> getCriteria() {
    return criteria;
  }

  /** 表元数据 */
  public TableMetadata getTableMetadata() {
    return tableMetadata;
  }

  /** GROUP BY 字段列表 */
  public List<String> getGroupByFields() {
    return groupByFields;
  }

  /** HAVING 条件列表 */
  public List<String> getHavingConditions() {
    return havingConditions;
  }

  /** 是否包含已软删行（默认 false，与 SELECT/COUNT 通道一致）。 软删表聚合默认只统计未删除行； 仅在显式 opt-in 时统计已删除行。 */
  public boolean isIncludeDeleted() {
    return includeDeleted;
  }

  /**
   * 显式页码（1 起）；为 null 时回退到 {@link Criteria#getPage()}。
   *
   * <p>把分页随查询上下文传递，而不是改写调用方传入的 {@code Criteria}，避免 {@code aggregateWithPagination} 对调用方对象 产生副作用外溢。
   */
  public Integer getPage() {
    return page;
  }

  /** 显式每页大小；为 null 时回退到 {@link Criteria#getSize()}，<=0 表示不分页。 */
  public Integer getSize() {
    return size;
  }

  /** 验证聚合表达式和GROUP BY字段的合法性 */
  public void validate() {
    // 这里可以添加验证逻辑，比如检查字段是否存在于表中
    // 实际实现可能需要依赖TableMetadata进行验证
  }
}
