package com.bone.lowcode.infra.application.vo.field;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class FieldVO {

    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "业务字段名称")
    private String bizName;

    @Schema(description = "业务字段编码")
    private String bizCode;

    @Schema(description = "字段取值表达式")
    private String dataBinding;

    @Schema(description = "页面组件类型，Input：文本单行输入框，SelectDrop：下拉框，DateTime：日期时间，DateRange：日期区间，SelectCtrl：级联下拉框，InputNum：数字单行输入框")
    private String componentType;

    @Schema(description = "展示标题名称")
    private String title;
}
