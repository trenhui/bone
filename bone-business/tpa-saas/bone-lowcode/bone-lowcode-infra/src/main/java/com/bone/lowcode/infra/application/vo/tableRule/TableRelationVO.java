package com.bone.lowcode.infra.application.vo.tableRule;

import com.bone.lowcode.infra.application.vo.table.FieldSimpleInfo;
import lombok.Data;

@Data
public class TableRelationVO {

    private String currentTableId;

    private String currentTableName;

    private FieldSimpleInfo currentRelationField;

    private String targetTableId;

    private String targetTableName;

    private FieldSimpleInfo targetRelationField;
}
