package com.bone.metadata.sdk.query;

import com.bone.metadata.sdk.domain.enums.QueryType;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.BatchCompiledQuery;
import com.bone.metadata.sdk.domain.query.CompiledQuery;
import com.bone.metadata.sdk.domain.spec.TableMetadataResolver;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.query.builder.*;
import com.bone.metadata.sdk.query.context.*;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.criteria.Condition;
import com.bone.metadata.sdk.sql.dialect.DatabaseDialect;
import com.bone.metadata.sdk.domain.exception.SqlInjectionRiskException;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import jakarta.annotation.PostConstruct;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SqlBuilder {

    private final MetadataService metadataService;
    private final DatabaseDialect dialect;
    private final Map<QueryType, Object> builders = new EnumMap<>(QueryType.class);

    public SqlBuilder(MetadataService metadataService,
                      DatabaseDialect dialect) {
        this.metadataService = metadataService;
        this.dialect = dialect;
    }

    @PostConstruct
    public void init() {
        builders.put(QueryType.SELECT, new SelectBuilderSql(metadataService, dialect));
        builders.put(QueryType.COUNT, new CountBuilderSql(metadataService));
        builders.put(QueryType.BATCH_INSERT, new BatchInsertBuilder());
        builders.put(QueryType.BATCH_UPDATE, new BatchUpdateBuilder());
        builders.put(QueryType.DYNAMIC_UPDATE, new DynamicUpdateBuilderSql());
        builders.put(QueryType.CONDITIONAL_UPDATE, new ConditionalUpdateBuilderSql());
        builders.put(QueryType.DELETE, new DeleteBuilderSql());
        builders.put(QueryType.AGGREGATION, new AggregationBuilderSql());
        builders.put(QueryType.UPSERT, new UpsertBuilderSql());
        builders.put(QueryType.COUNT_AGGREGATION, new CountAggregationBuilderSql()); // 新增计数聚合构建器
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildSelect(Class<?> cls, Criteria<?> c, boolean includeDeleted) {
        TableMetadata t = TableMetadataResolver.load(cls);
        return ((SqlQueryBuilder<SelectContext>) builders.get(QueryType.SELECT))
                .build(new SelectContext(t, c, includeDeleted));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildSelect(Class<?> cls, Criteria<?> c) {
        TableMetadata t = TableMetadataResolver.load(cls);
        return ((SqlQueryBuilder<SelectContext>) builders.get(QueryType.SELECT))
                .build(new SelectContext(t, c, false));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildSelect(Class<?> cls, Criteria<?> c, AllocationContext extContext) {
        TableMetadata t = TableMetadataResolver.load(cls);
        // 验证用户输入的条件是否安全
        validateCriteriaSafety(c);
        return ((SqlQueryBuilder<SelectContext>) builders.get(QueryType.SELECT))
                .build(new SelectContext(t, c, extContext, false));
    }
    
    /**
     * 验证查询条件的安全性
     * @param criteria 查询条件对象
     */
    private void validateCriteriaSafety(Criteria<?> criteria) {
        if (criteria == null) {
            return;
        }
        
        // 验证主表条件
        if (criteria.getMainConditions() != null) {
            for (Condition condition : criteria.getMainConditions()) {
                validateCondition(condition);
            }
        }
        
        // 验证扩展表条件
        if (criteria.getExtConditions() != null) {
            for (Condition condition : criteria.getExtConditions()) {
                validateCondition(condition);
            }
        }
        
        // 子条件组检查暂时注释，因为Criteria类可能没有getGroups()方法
        // TODO: 如果Criteria类支持条件组，取消注释下面的代码
        /*
        // 检查子条件组
        if (criteria.getGroups() != null) {
            for (Criteria<?> group : criteria.getGroups()) {
                validateCriteriaSafety(group);
            }
        }
        */
        
        // 连接条件和排序条件的验证暂时注释，因为Criteria类中可能没有这些方法
        // TODO: 如果Criteria类支持连接和排序，取消注释下面的代码
        /*
        // 检查连接条件
        if (criteria.getJoins() != null) {
            for (Criteria.Join join : criteria.getJoins()) {
                validateCriteriaSafety(join.getCriteria());
            }
        }
        
        // 检查排序条件
        if (criteria.getOrders() != null) {
            for (Criteria.Order order : criteria.getOrders()) {
                validateOrderField(order.getField());
            }
        }
        */
    }
    
    /**
     * 验证单个条件的安全性
     * @param condition 查询条件
     */
    private void validateCondition(Condition condition) {
        if (condition == null) {
            return;
        }
        
        // 基本的SQL注入检查 - 确保列名不包含危险字符
        String column = condition.getColumn();
        if (column != null && containsDangerousCharacters(column)) {
            throw new SqlInjectionRiskException("Potential SQL injection detected in column name: " + column);
        }
        
        // 扩展字段名检查暂时注释，因为Condition类可能没有getExtFieldName()方法
        // TODO: 如果Condition类支持扩展字段，取消注释下面的代码
        /*
        // 检查扩展字段名
        String extFieldName = condition.getExtFieldName();
        if (extFieldName != null && containsDangerousCharacters(extFieldName)) {
            throw new SqlInjectionRiskException("Potential SQL injection detected in extension field name: " + extFieldName);
        }
        */
    }
    
    /**
     * 验证排序字段的安全性
     * @param fieldName 排序字段名
     */
    // TODO: 如果Criteria类支持排序，取消注释下面的方法
    /*
    private void validateOrderField(String fieldName) {
        if (fieldName != null && containsDangerousCharacters(fieldName)) {
            throw new SqlInjectionRiskException("Potential SQL injection detected in order field: " + fieldName);
        }
    }
    */
    
    /**
     * 检查字符串是否包含危险字符
     * @param input 输入字符串
     * @return 是否包含危险字符
     */
    private boolean containsDangerousCharacters(String input) {
        // 检查SQL注入的危险字符
        return input.contains(";" ) || // 语句终止符
               input.contains("--") || // 单行注释
               input.contains("/*") || // 多行注释开始
               input.contains("*/") || // 多行注释结束
               input.contains("'") || // 单引号
               input.contains("\"") || // 双引号
               input.contains("/") || // 可能的路径遍历
               input.matches("(?i)OR\\s+.*=.*") || // OR 1=1 类型注入
               input.matches("(?i)UNION\\s+SELECT"); // UNION查询注入
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildCount(Class<?> cls, Criteria<?> c, AllocationContext extContext) {
        TableMetadata t = TableMetadataResolver.load(cls);
        return ((SqlQueryBuilder<CountContext>) builders.get(QueryType.COUNT))
                .build(new CountContext(t, c, extContext, false));
    }

    @SuppressWarnings("unchecked")
    public BatchCompiledQuery buildBatchInsert(Class<?> cls, List<?> list) {
        TableMetadata t = TableMetadataResolver.load(cls);
        return ((BatchQueryBuilder<BatchInsertContext>) builders.get(QueryType.BATCH_INSERT))
                .build(new BatchInsertContext(t, list));
    }

    @SuppressWarnings("unchecked")
    public BatchCompiledQuery buildBatchUpdate(Class<?> cls, List<?> list) {
        TableMetadata t = TableMetadataResolver.load(cls);
        return ((BatchQueryBuilder<BatchUpdateContext>) builders.get(QueryType.BATCH_UPDATE))
                .build(new BatchUpdateContext(t, list));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildDynamicUpdate(Class<?> cls, Object e) {
        TableMetadata t = TableMetadataResolver.load(cls);
        return ((SqlQueryBuilder<DynamicUpdateContext>) builders.get(QueryType.DYNAMIC_UPDATE))
                .build(new DynamicUpdateContext(t, e));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildConditionalUpdate(Class<?> cls, Object e, Criteria<?> c) {
        TableMetadata t = TableMetadataResolver.load(cls);
        return ((SqlQueryBuilder<ConditionalUpdateContext>) builders.get(QueryType.CONDITIONAL_UPDATE))
                .build(new ConditionalUpdateContext(t, e, c));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildDelete(Class<?> cls, Criteria<?> c, AllocationContext extContext) {
        TableMetadata t = TableMetadataResolver.load(cls);
        return ((SqlQueryBuilder<DeleteContext>) builders.get(QueryType.DELETE))
                .build(new DeleteContext(t, c, extContext));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildUpsert(Class<?> cls, Object e) {
        TableMetadata t = TableMetadataResolver.load(cls);
        return ((SqlQueryBuilder<UpsertContext>) builders.get(QueryType.UPSERT))
                .build(new UpsertContext(t, e, MetadataSdkContext.getDatabaseType()));
    }

    @SuppressWarnings("unchecked")
    public CompiledQuery buildAggregation(Class<?> cls, List<String> aggregations, Criteria<?> criteria,
                                          List<String> groupBy, List<String> having) {
        TableMetadata t = TableMetadataResolver.load(cls);
        return ((SqlQueryBuilder<AggregationContext>) builders.get(QueryType.AGGREGATION))
                .build(new AggregationContext(t, aggregations, criteria, groupBy, having));
    }

    // 新增方法：构建带HAVING条件的聚合计数查询
    @SuppressWarnings("unchecked")
    public CompiledQuery buildCountAggregation(Class<?> cls, Criteria<?> criteria,
                                               List<String> groupBy, List<String> having) {
        TableMetadata t = TableMetadataResolver.load(cls);
        return ((SqlQueryBuilder<AggregationContext>) builders.get(QueryType.COUNT_AGGREGATION))
                .build(new AggregationContext(t, Collections.emptyList(), criteria, groupBy, having));
    }
}