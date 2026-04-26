package com.bone.lowcode.infra.application.dto.optionSet;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateValue {
    /**
     * 选项值id
     */
    @NotNull(message = "更新时选项值id不能为空")
    private Long valueId;

    /**
     * 选项值名称
     */
    @Size(min = 1, max = 100, message = "选项值名称长度需在1-100字符之间")
//    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9_]*$", message = "选项值名称仅允许中文、英文、数字、下划线等字符")
    private String valueName;

    /**
     * 选项值额外属性,json格式
     */
    private String extraProperty;

    /**
     * 选项值启用状态,0:不启用,1:启用
     */
    private Byte status;
}
