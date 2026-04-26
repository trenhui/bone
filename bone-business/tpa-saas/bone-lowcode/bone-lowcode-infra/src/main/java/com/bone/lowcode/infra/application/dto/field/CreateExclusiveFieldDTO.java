package com.bone.lowcode.infra.application.dto.field;

import com.bone.lowcode.infra.infrastructure.common.annotation.KeyPart;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateExclusiveFieldDTO {

    @NotNull(message = "modelId不能为空")
    private Long modelId;

    //业务字段名称
    @NotBlank(message = "业务字段名称不能为空")
    private String bizName;

    //页面组件类型，Input：文本单行输入框，SelectDrop：下拉框，DateTime：日期时间，DateRange：日期区间，SelectCtrl：级联下拉框，InputNum：数字单行输入框
    @NotBlank(message = "组件类型不能为空")
    private String componentType;

    @KeyPart
    @NotBlank(message = "主体code不能为空")
    private String bizIdentityCode;

    //展示标题
    private String title;

    //对齐方式，0：左对齐，1：居中，2：右对齐
    private Byte alignment;

    //业务字段code
    @NotBlank(message = "业务字段code不能为空")
    private String bizCode;
}
