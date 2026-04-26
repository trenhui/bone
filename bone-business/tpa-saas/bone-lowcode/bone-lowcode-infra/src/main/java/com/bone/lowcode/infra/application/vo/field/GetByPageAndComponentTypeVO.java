package com.bone.lowcode.infra.application.vo.field;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class GetByPageAndComponentTypeVO {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "数据模型ID")
    private String modelId;

    @Schema(description = "业务字段编码")
    private String bizCode;

    @Schema(description = "业务字段名称")
    private String bizName;

    @Schema(description = "业务字段类型，0：系统，1：专属")
    private Byte fieldType;

    @Schema(description = "页面组件类型")
    private String componentType;

    @Schema(description = "展示标题名称")
    private String title;

    @Schema(description = "字段取值表达式")
    private String dataBinding;
}
