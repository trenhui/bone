package com.bone.tpa.sdk.tpasaasdb.model;

import lombok.Data;

import java.util.Date;

@Data
public class ClaimPushFailDO {

    private Long id;

    /**
     * 业务身份code
     */
    private String bizIdentityCode;

    /**
     * 租户id
     */
    private String tenantId;

    private String errorType;

    private String vipSign;

    private String policyNo;

    private String batchNo;

    private String claimNo;

    private Date failTime;

    private String pushBackReason;

    private String outInsureName;

    private String outInsureIdentityTypeCn;

    private String outInsureIdentityNo;

    private String mainInsureName;

    private String mainInsureIdentityTypeCn;

    private String mainInsureIdentityNo;

    private String insureName;

    private String insuranceName;

    private String branchName;

    private String auditingOperatorName;

}
