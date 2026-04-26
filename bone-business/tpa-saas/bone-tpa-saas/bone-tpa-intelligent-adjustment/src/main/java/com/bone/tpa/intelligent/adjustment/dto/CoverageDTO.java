package com.bone.tpa.intelligent.adjustment.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CoverageDTO extends DtoBase{

    /**
     * 内部保单号
     */
    private String policyNo;

    /**
     * 计划ID
     */
    private Long planId;

    /**
     * 险种名称
     */
    private String coverageName;

    /**
     * 险种代码
     */
    private String coverageCode;

    /**
     * 险种额度
     */
    private BigDecimal coverageLimit;

    /**
     * 计划版本
     */
    private String version;

}
