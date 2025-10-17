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
import com.bone.metadata.sdk.sql.dialect.DatabaseDialect;
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
        return ((SqlQueryBuilder<SelectContext>) builders.get(QueryType.SELECT))
                .build(new SelectContext(t, c, extContext, false));
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