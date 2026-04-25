package com.bone.studio.generator.domain.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DatabaseTable {
    private String id;
    private String tableName;
    private String tableComment;
    private List<TableColumn> columns;
}
