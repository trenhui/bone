package com.bone.lowcode.infra.application.vo.table;

import lombok.Data;

@Data
public class FieldOfTable {

    private String id;

    private String title;

    private String modelId;

    private Byte display; //0：隐藏，1：展示

    private Integer sequence;

    private String bizCode;

    private String bizName;//业务字段名称

    private Byte singleLineEditable = 0;//可单行编辑,0：否,1：是

    private Byte BatchEditable = 0;//可批量编辑,0：否,1：是
}
