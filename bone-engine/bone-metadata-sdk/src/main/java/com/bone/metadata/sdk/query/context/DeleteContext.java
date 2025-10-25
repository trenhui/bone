package com.bone.metadata.sdk.query.context;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import lombok.Getter;

public class DeleteContext extends AbstractQueryContext {

    public DeleteContext(TableMetadata table, Criteria<?> criteria, AllocationContext extContext) {
        super(table, criteria, extContext);
    }
}
