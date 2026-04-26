package com.bone.lowcode.infra.infrastructure.feign.bean;


import lombok.Data;

@Data
public class ZfSlipInfo {
    /**
     * 投保公司编号
     */
    private String corpCode ;
    /**
     * 投保公司名称
     */
    private String corpName;

    /**
     * 保险分公司code
     */
    private String insuCode;

    /**
     * 保险分公司名称
     */
    private String insuName;

    /**
     * 保单号
     */
    private String slipCode;

    /**
     * 保险公司code
     */
    private String topInsuranceCode;

    /**
     * 保险公司名称
     */
    private String topInsuranceName;
}
