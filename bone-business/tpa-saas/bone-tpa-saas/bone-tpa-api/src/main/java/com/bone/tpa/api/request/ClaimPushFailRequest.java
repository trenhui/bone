package com.bone.tpa.api.request;

import lombok.Data;

@Data
public class ClaimPushFailRequest {

    /**
     * 普康批次号
     */
    private String batchNo;

    /**
     * 赔案号
     */
    private String claimNo;

    /**
     * 保单号
     */
    private String policyNo;

    /**
     * 出险人姓名，对应ss_claim_stakeholder#name
     */
    private String outInsureName;

    /**
     * 出险人证件号
     */
    private String outInsureIdentityNo;

    /**
     * 投保公司
     */
    private String insureName;

    /**
     * 保险公司
     */
    private String insuranceName;

    /**
     * 保险分公司
     */
    private String branchName;

    /**
     * 错误归档
     */
    private String errorType;

    /**
     * 审核人员
     */
    private String auditingOperatorName;

    /**
     * 赔案等级
     */
    private String vipSign;

    private Integer pageNumber = 1;

    private Integer pageSize = 20;

}
