package com.bone.lowcode.infra.application.vo.field;

import lombok.Data;

@Data
public class FieldLocationVO {
    private String id;

    private String modelId;

    private String modelCode;

    private String modelName;

    private String fieldName;

    private String fieldCode;

    private Integer rowNumber;

    private Integer columnNumber;

    private Integer width;

    private Byte displayed;
}
