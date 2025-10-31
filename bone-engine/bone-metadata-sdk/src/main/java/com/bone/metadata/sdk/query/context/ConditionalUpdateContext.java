package com.bone.metadata.sdk.query.context;

import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.query.criteria.Criteria;

public class ConditionalUpdateContext {
    private final TableMetadata table;
    private final Object entity;
    private final Criteria<?> criteria;
    public ConditionalUpdateContext(TableMetadata table, Object entity, Criteria<?> criteria) {
        this.table = table; this.entity = entity; this.criteria = criteria;
    }
    public TableMetadata getTable() { return table; }
    public Object getEntity() { return entity; }
    public Criteria<?> getCriteria() { return criteria; }
}
