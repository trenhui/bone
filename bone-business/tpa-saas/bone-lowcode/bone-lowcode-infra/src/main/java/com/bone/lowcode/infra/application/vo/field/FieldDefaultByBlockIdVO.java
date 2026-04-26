package com.bone.lowcode.infra.application.vo.field;

import lombok.Data;

@Data
public class FieldDefaultByBlockIdVO {

    private String id;

    private String modelName;

    private String fieldName;

    private String fieldCode;

    private Byte displayed; //0：隐藏，1：展示

    private Byte required; //0：不必填，1：必填
}
