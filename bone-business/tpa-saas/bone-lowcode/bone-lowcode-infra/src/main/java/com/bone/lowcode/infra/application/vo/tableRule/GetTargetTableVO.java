package com.bone.lowcode.infra.application.vo.tableRule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetTargetTableVO {

    private String tableId;

    private String tableName;
}
