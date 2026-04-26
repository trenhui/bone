package com.bone.lowcode.infra.application.dto.table;

import lombok.Data;

@Data
public class ModelOfTableChange {

    private Long modelId;

    private Byte used; // 0:不启用，1:启用
}
