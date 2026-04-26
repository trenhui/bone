package com.bone.lowcode.infra.application.dto.optionSet;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateSetValueDTO {

    /**
     * 选项值id
     */
    private Long valueId;

    /**
     * 选项值标识，新增时使用
     */
//    @Size(min = 1, max = 20, message = "选项值标识长度需在1-20字符之间")
//    @Pattern(regexp = "^[a-zA-Z0-9_]*$", message = "选项值标识仅允许数字、英文字符或下划线")
    private String valueCode;

    /**
     * 选项值名称
     */
//    @Size(min = 1, max = 50, message = "选项值名称长度需在1-50字符之间")
//    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9_]*$", message = "选项值名称仅允许中文、英文、数字、下划线等字符")
    private String valueName;

    /**
     * 选项值启用状态,0:不启用,1:启用
     */
    private Byte valueEnable;

    /**
     * 选项值额外属性,json格式
     */
    private String extraProperty;
}
