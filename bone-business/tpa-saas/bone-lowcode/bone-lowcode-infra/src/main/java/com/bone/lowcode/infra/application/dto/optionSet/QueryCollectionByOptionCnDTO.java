package com.bone.lowcode.infra.application.dto.optionSet;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class QueryCollectionByOptionCnDTO {

    /**
     * 主体code
     */
    @NotEmpty(message = "主体code不能为空")
    private String bizIdentityCode;

    /**
     * 字段Code
     */
    @NotEmpty(message = "fieldCode不能为空")
    private String fieldCode;

    /**
     * 字段所属的model的code
     */
    @NotEmpty(message = "modeCodeList不能为空")
    private List<String> modeCodeList;

    /**
     * 选项值中文
     */
    @NotEmpty(message = "optionCn参数不能为空")
    private String optionCn;
}
