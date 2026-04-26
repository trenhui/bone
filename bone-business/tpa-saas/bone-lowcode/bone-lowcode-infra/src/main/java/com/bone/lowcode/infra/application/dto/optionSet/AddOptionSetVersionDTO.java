package com.bone.lowcode.infra.application.dto.optionSet;

import lombok.Data;

@Data
public class AddOptionSetVersionDTO {

    /**
     * 选项集id
     */
    private Long optionSetId;

    /**
     * 版本描述
     */
    private String description;
}
