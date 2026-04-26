package com.bone.lowcode.infra.application.dto.field;

import lombok.Data;

@Data
public class BatchUpdateFieldLocationDTO {

    private Long fieldId;

    private Integer rowNumber;

    private Integer columnNumber;

    private Integer width;
}
