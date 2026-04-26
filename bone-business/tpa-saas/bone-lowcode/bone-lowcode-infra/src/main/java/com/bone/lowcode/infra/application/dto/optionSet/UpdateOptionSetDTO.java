package com.bone.lowcode.infra.application.dto.optionSet;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class UpdateOptionSetDTO {

    /**
     * 选项集id
     */
    @NotNull(message = "选项集id不能为空")
    private Long optionSetId;

    /**
     * 选项集描述
     */
    @Length(max = 100, message = "选项集描述最多不超过100字")
    private String setDesc;

    /**
     * 选项值额外属性名,数组格式
     */
    private String extraPropertyKey;
}
