package com.bone.lowcode.infra.application.vo.page.structure;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;


@Data
public class Model {
    private String level = "model";

    private String id;

    private Byte fromMetadata;

    //使用场景,1:区块字段,2:表格,3:页头,4:上传数据组件字段
    private Byte ownerType;

    private String name;

    private String code;

    @Schema(description = "取值表达式前缀")
    private String dataBindingPrefix;

    @Schema(description = "专属字段前缀")
    private String extraFieldPrefix;

    @Schema(description = "元数据模型对应的表名")
    private String tableName;

    @Schema(description = "是否启用,0:不启用，1:启用")
    private Byte status;
}
