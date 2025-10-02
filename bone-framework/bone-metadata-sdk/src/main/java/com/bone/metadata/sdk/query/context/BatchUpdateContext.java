package com.bone.metadata.sdk.query.context;

import com.bone.metadata.sdk.domain.model.TableMetadata;

import java.util.List;

public class BatchUpdateContext {
    private final TableMetadata table;
    private final List<?> entities;
    public BatchUpdateContext(TableMetadata table, List<?> entities) {
        this.table = table;
        this.entities = entities;
    }
    public TableMetadata getTable() { return table; }
    public List<?> getEntities() { return entities; }
}
