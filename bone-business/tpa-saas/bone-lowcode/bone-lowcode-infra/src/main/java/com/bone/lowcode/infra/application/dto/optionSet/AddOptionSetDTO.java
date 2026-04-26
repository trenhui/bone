package com.bone.lowcode.infra.application.dto.optionSet;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.List;

@Data
public class AddOptionSetDTO {

    /**
     * 选项集名称
     */
    @NotBlank(message = "选项集名称不能为空")
//    @Length(min = 1, max = 20, message = "选项集名称的长度范围是1-20字符")
    private String setName;

    /**
     * 选项集标识
     */
    @NotBlank(message = "选项集标识不能为空")
//    @Length(min = 1, max = 50, message = "选项集标识的长度范围是1-50字符")
    private String setCode;

    /**
     * 选项集描述
     */
    @Length(max = 100, message = "选项集描述最多不超过100字")
    private String setDesc;

    /**
     * 选项值额外属性名,数组格式
     */
    private String extraPropertyKey; //["key1","key2","key3"]

    @Valid
    List<AddSetValueDTO> valueList;
}
