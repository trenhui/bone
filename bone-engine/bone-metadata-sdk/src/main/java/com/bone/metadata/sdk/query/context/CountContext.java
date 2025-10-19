package com.bone.metadata.sdk.query.context;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.TableMetadata;

public class CountContext {
    private final TableMetadata table;
    private final Criteria<?> criteria;
    private final AllocationContext extContext;
    private final boolean includeDeleted;

    public CountContext(TableMetadata table, Criteria<?> criteria, boolean includeDeleted) {
        this.table = table;
        this.criteria = criteria;
        this.extContext = null;
        this.includeDeleted = includeDeleted;
    }
    public CountContext(TableMetadata table, Criteria<?> criteria, AllocationContext extContext, boolean includeDeleted) {
        this.table = table;
        this.criteria = criteria;
        this.extContext = extContext;
        this.includeDeleted = includeDeleted;
    }
    
    public TableMetadata getTable() {
        return table;
    }
    
    public Criteria<?> getCriteria() {
        return criteria;
    }
    
    public AllocationContext getExtContext() {
        return extContext;
    }
    
    public boolean getIncludeDeleted() {
        return includeDeleted;
    }
}
