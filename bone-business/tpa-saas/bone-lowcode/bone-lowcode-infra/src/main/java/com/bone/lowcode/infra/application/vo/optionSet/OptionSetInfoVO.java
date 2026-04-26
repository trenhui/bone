package com.bone.lowcode.infra.application.vo.optionSet;

import lombok.Data;

import java.util.List;

@Data
public class OptionSetInfoVO {
    /**
     * 选项集id
     */
    public String id;

    /**
     * 选项集名称
     */
    private String name;

    /**
     * 选项集标识
     */
    private String code;

    /**
     * 选项集描述
     */
    private String desc;

    /**
     * 选项集适用范围
     */
    private Byte useScope;

    /**
     * 选项值启用状态,0:不启用,1:启用
     */
    private Byte status;

    /**
     * 额外属性
     */
    private List<String> extraPropertyKey;
}
