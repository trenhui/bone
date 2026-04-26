package com.bone.tpa.push.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author feihaiming
 * @create 2025/10/16 15:31
 */
@Data
public class ClaimConclusionDTO {
    // claim_conclusion
    private String adjustmentGuid;
    private String batchCode;
    private String groupPolicy;
    private String recognizeeCertId;
    private String personGender;
    private String personName;
    private String policyDate;
    private Integer policyType;
    private String inHospitalDate;
    private String outHospitalDate;
    private String accidentNature;
    private String outPass;
    private String diseaseCode;
    private String hospitalCode;
    private String payeeCertId;
    private String payeeName;
    private String payeePayWay;
    private String payeeBank;
    private String payeeAccount;
    private String mobile;
    private String claimCode;
    private String claimCodeTb;
    private String policyCode;
    private String insuType;
    private String duty;
    private String dutySubcode;
    private String claimConclusion;
    private String subcode;
    private BigDecimal compensateAmt = BigDecimal.ZERO;
    private Integer status;
    private String memo;
    private String dutyName;
    private String isOnline;
    private String payeeCertType;
    private String personCertType;

    // claim_conclusion_extend
    private String dutyCode;
    private Integer claimStatus;
    private String publicPersonalFlag;
    private Integer conclusionType;
    private Integer deductionType;
    private String deductGroupPolicy;
    private BigDecimal reasonableRatio = BigDecimal.ZERO;
    private BigDecimal partialSelfPaymentRatio = BigDecimal.ZERO;
    private BigDecimal selfPaymentRatio = BigDecimal.ZERO;
    private Integer isCoverReasonable;
    private Integer isCoverPartialSelfPayment;
    private Integer isCoverSelfPayment;
    private Integer responsibilityPattern;

    // duty
    private String relatedObjectGuid;
    private String corpCode;
    private String personCertId;
    private String visitDuty;
    private BigDecimal applyAmt = BigDecimal.ZERO;
    private BigDecimal abtmAmt = BigDecimal.ZERO;
    private BigDecimal changeAmt = BigDecimal.ZERO;
    private String unCompensateCause;
    private String policyBeginDate;
    private String policyEndDate;
    private String compensateDuty;
    private BigDecimal compensateRatio = BigDecimal.ZERO;
    private BigDecimal billAmt = BigDecimal.ZERO;

    // other
    private Map<String, String> extraFields = new HashMap<>();
}
