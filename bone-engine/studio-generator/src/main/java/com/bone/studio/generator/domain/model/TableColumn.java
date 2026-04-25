package com.bone.studio.generator.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TableColumn {
    private String columnName;
    private String dataType;
    private String columnComment;
    private boolean primaryKey;
    private boolean nullable;
    private int length;
    private int precision;
    private int scale;
}
