package com.bone.metadata.sdk.query.context;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.TableMetadata;

public class SelectContext extends AbstractQueryContext {
    private final boolean includeDeleted;

    public SelectContext(TableMetadata table, Criteria<?> criteria, AllocationContext extContext, boolean includeDeleted) {
        super(table, criteria, extContext);
        this.includeDeleted = includeDeleted;
    }

    public SelectContext(TableMetadata table, Criteria<?> criteria, boolean includeDeleted) {
        super(table, criteria, null);
        this.includeDeleted = includeDeleted;
    }
    
    public boolean isIncludeDeleted() { return includeDeleted; }
}
