package com.bone.lowcode.infra.application.vo.table;

import lombok.Data;

@Data
public class TableFieldVO {

    private String fieldId;

    private String fieldBizCode;

    private String fieldBizName;

    private String componentType;//页面组件类型
}
