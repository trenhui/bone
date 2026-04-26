package com.bone.tpa.push.feign.response;

import lombok.Data;

@Data
public class DutyConfigResponse {
    /**
     * 保单号
     */
    private String policyNo;
    /**
     * 投保公司
     */
    private String insureName;
    /**
     * 计划id
     */
    private String planId;
    /**
     * 险种代码
     */
    private String insuCode;
    /**
     * 责任id
     */
    private String dutyId;
    /**
     * 责任名称
     */
    private String dutyName;
    /**
     * 发票类型
     */
    private Integer invoiceType;
    /**
     * 发票类型名称
     */
    private String invoiceTypeName;
    /**
     * 责任代码
     */
    private String dutyCode;
    /**
     * 责任子码
     */
    private String dutySubCode;
    /**
     * 索赔事故性质
     */
    private String accidentCode;

    private Byte status;
}
