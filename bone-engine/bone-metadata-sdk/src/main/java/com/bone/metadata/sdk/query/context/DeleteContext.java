package com.bone.metadata.sdk.query.context;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import lombok.Getter;

public class DeleteContext {
    private final TableMetadata table;
    private final Criteria<?> criteria;
    private final AllocationContext extContext;
    
    // 添加公共的getter方法
    public TableMetadata getTable() { return table; }
    public Criteria<?> getCriteria() { return criteria; }
    public AllocationContext getExtContext() { return extContext; }

    public DeleteContext(TableMetadata table, Criteria<?> criteria, AllocationContext extContext) {
        this.table = table;
        this.criteria = criteria;
        this.extContext = extContext;
    }
}
