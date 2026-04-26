package com.bone.lowcode.infra.application.dto.processPage;

import lombok.Data;

import java.util.List;

@Data
public class BindOptionSetDTO {

    /**
     * 流程页面code
     */
    private String code;

    /**
     * 主体code
     */
    private String bizIdentityCode;

    /**
     * 选项集id列表
     */
    List<Long> optionSetIdList;
}
