package com.bone.lowcode.infra.infrastructure.persistence.dto;

import lombok.Data;

/**
 * cfgField 的  extraCofnig  的配置字段
 */
@Data
public class FieldExtraConfig {
    /**
     * 选项集"其他"是否开启
     */
    private Boolean optionSetOtherTag = false;
    /**
     * 选项集"其他"占位文案
     */
    private String optionSetOtherTitle="";

    /**
     * 选项集"其他"限制字数
     */
    private Integer optionSetOtherNumberLimited = 10;
    /**
     * 选项集"其他"是否必填
     */
    private Boolean optionSetOtherRequired = false;

    /**
     * 匹配其他的code
     */
    private String optionSetOtherMatchValue ="" ;



}
