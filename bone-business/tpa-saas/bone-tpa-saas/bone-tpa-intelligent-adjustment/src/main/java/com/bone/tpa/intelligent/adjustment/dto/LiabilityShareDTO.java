package com.bone.tpa.intelligent.adjustment.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LiabilityShareDTO extends DtoBase{

    /**
     * 保单号
     */
    private String policyNo;

    /**
     * 计划ID
     */
    private Long planId;

    /**
     * 计划名称
     */
    private String planName;

    /**
     * 责任共保ID
     */
    private String shareId;

    /**
     * 责任共保代码
     */
    private String shareCode;

    /**
     * 责任共保额度
     */
    private BigDecimal shareLimit;

    /**
     * 计划版本
     */
    private String version;
}
