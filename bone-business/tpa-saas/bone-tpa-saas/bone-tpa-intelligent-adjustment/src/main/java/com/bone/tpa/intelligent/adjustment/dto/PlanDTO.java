package com.bone.tpa.intelligent.adjustment.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlanDTO   extends  DtoBase{

    /**
     * 内部保单号
     */
    private String policyNo;

    private String uuid;

    /**
     * 计划名称
     */
    private String planName;

    /**
     * 计划代码
     */
    private String planCode;

    /**
     * 计划额度
     */
    private BigDecimal planLimit;

    /**
     * 计划版本
     */
    private String version;

    /**
     * 计划版本状态
     */
    private String status;

    /**
     * 计划版本状态中文
     */
    private String statusCn ;


}
