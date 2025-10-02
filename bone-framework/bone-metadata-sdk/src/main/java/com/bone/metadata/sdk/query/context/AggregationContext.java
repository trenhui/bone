package com.bone.metadata.sdk.query.context;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.domain.model.TableMetadata;

import java.util.Collections;
import java.util.List;

public class AggregationContext {
    private final TableMetadata tableMetadata;
    private final List<String> aggregations;
    private final Criteria<?> criteria;
    private final List<String> groupByFields;
    private final List<String> havingConditions;

    public AggregationContext(TableMetadata tableMetadata, List<String> aggregations,
                              Criteria<?> criteria, List<String> groupByFields, List<String> havingConditions) {
        this.tableMetadata = tableMetadata;
        this.aggregations = aggregations != null ? aggregations : Collections.emptyList();
        this.criteria = criteria;
        this.groupByFields = groupByFields != null ? groupByFields : Collections.emptyList();
        this.havingConditions = havingConditions != null ? havingConditions : Collections.emptyList();
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

    /** 验证聚合表达式和GROUP BY字段的合法性 */
    public void validate() {
        // 这里可以添加验证逻辑，比如检查字段是否存在于表中
        // 实际实现可能需要依赖TableMetadata进行验证
    }
}