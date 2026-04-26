package com.bone.lowcode.infra.application.vo.field;

import lombok.Data;

@Data
public class FieldRuleField {

    private String id;

    private String code;

    private String showName;

    private Byte fieldType;

    private String componentType;

    private String dataBinding;
}
