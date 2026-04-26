package com.bone.tpa.sdk.tpasaasdb.model;

import lombok.Data;

import java.util.Date;

@Data
public class ClaimPushFailOperateDO {

    private Long id;

    private String errorType;

    private String vipSign;

    private String policyNo;

    private String batchNo;

    private String claimNo;

    private Date failTime;

    private String createUser;

    private String backAuditUser;

    private Date createTime;

    private String backNode;

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
