package com.bone.tpa.push.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author feihaiming
 * @create 2025/10/16 13:44
 */
@Data
public class ClaimDTO {
    // tb_claim
    private String batchCode;
    private String caseStatus;
    private String caseStatusSecond;
    private String caseCode;
    private BigDecimal payMoney = BigDecimal.ZERO;
    private String receivedName;
    private String receivedBank;
    private String receivedBankNo;
    private String payType;
    private String closeDate;
    private String visitDate;
    private String name;
    private String bbrzjh;
    private String bbrzjlx;
    private String zbbrzjh;
    private String zbbrzjlx;
    private String applyTypeCode;
    private Integer tbClaimStatus;
    private String groupPolicy;

    // tb_over_claim
    private String claimCode;
    private Integer claimStatus;
    private String corpCode;
    private String personCertId;
    private String policy;
    private String policyDate;
    private Integer policyType;
    private String accidentNature;
    private String outPass;
    private String payeeName;
    private String payeeCertId;
    private String payeeGender;
    private String personMobile;
    private String payeePayType;
    private String payeeAccount;
    private String bankCode;
    private BigDecimal applyAmt = BigDecimal.ZERO;
    private BigDecimal abtmAmt = BigDecimal.ZERO;
    private BigDecimal compensateAmt = BigDecimal.ZERO;
    private BigDecimal changeAmt = BigDecimal.ZERO;
    private String endDate;
    private String typeCode;
    private String corpName;
    private String mainPersonName;
    private String mainPersonCertId;
    private String bankName;
    private String adjustmentGuid;
    private BigDecimal billAmt = BigDecimal.ZERO;
    private Integer status;
    private String imagingPath;
    private Integer tpaStatus;
    private Integer payStatus;
    private String reportDate;
    private String branchCode;
    private String branchName;
    private String signDate;
    private String payeeCertBeginDate;
    private String payeeCertEndDate;
    private String oftenLiveAddress;
    private Integer payObj;
    private Integer conclusion;
    private String unCompensateCause;
    private String guowangSerialNum;

    // tb_over_claim_extend
    private Date mainPersonBegindate;
    private Date mainPersonEnddate;
    private String payeeType;
    private Integer copyCaseFlag;
    private String reviewedBy;
    private String payeeBankProvince;
    private String payeeBankCity;
    private String payeeCertType;
    private String payeeRelation;
    private Date personCertBeginDate;
    private Date personCertEndDate;
    private String payeePhone;
    private String dangerPlace;
    private String dangerAreaCode;
    private String zbRelation;
    private String planName;
    private String channel;
    private String beneRelation;
    private String payeeHolderRelation;
    private String beneHolderRelation;
    private String batchCodeTpa;
    private Integer relationType;
    private String businessMode;
    private String agentType;
    private String isFormalItiesComplete;
    private String isIdentityCheck;
    private String payThirdReason;
    private String returnReason;
    private String caseSource;
    private String cancelType;
    private String cancelReasonCode;
    private Date signTime;
    private Integer vipLevel;
    private String damageCode;
    private String rpDataState;
    private String reviewFlag;
    private Integer reviewMergeFlag;
    private String reviewMergeParentCode;
    private String handleType;
    private String freezeAmountParams;
    private Integer personSex;
    private Byte imageIssTif;

    // other
    private Map<String, String> extraFields = new HashMap<>();
}
