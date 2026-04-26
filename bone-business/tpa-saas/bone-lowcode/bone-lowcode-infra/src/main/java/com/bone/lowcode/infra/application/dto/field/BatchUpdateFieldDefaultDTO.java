package com.bone.lowcode.infra.application.dto.field;

import lombok.Data;

@Data
public class BatchUpdateFieldDefaultDTO {

    private Long fieldId;

    /*默认显示*/
    private Byte displayed; //0：隐藏，1：展示
    /*默认必填*/
    private Byte required; //0：不必填，1：必填

    private String dataBinding;

    private String componentType;
}
