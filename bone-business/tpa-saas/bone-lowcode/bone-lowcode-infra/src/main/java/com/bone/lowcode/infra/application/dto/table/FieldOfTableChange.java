package com.bone.lowcode.infra.application.dto.table;

import lombok.Data;

@Data
public class FieldOfTableChange {

    private Long fieldId;

    private Byte display; // 0：隐藏，1：展示

    private Integer sequence;

    private Byte singleLineEditable;//可单行编辑,0：否,1：是

    private Byte batchEditable;//可批量编辑,0：否,1：是
}
