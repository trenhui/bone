package com.bone.lowcode.infra.application.vo.model;

import lombok.Data;

@Data
public class GetAllModelByPageCodeVo {

    private String blockName;

    private String modelName;

    // 0:区块字段  1：区块表格
    private byte presentationFormat;

    // 0:不启用  1：启用
    private Byte status;

    private String modelId;
}
