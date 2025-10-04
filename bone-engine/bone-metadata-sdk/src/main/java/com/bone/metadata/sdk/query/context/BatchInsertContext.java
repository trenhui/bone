package com.bone.metadata.sdk.query.context;

import com.bone.metadata.sdk.domain.model.TableMetadata;

import java.util.List;

public class BatchInsertContext {
    private final TableMetadata table;
    private final List<?> entities;
    public BatchInsertContext(TableMetadata table, List<?> entities) {
        this.table = table;
        this.entities = entities;
    }
    public TableMetadata getTable() { return table; }
    public List<?> getEntities() { return entities; }
}
