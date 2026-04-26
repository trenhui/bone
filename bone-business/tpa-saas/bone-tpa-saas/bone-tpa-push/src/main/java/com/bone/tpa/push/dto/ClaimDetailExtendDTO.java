package com.bone.tpa.push.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author feihaiming
 * @create 2025/10/16 15:19
 */
@Data
public class ClaimDetailExtendDTO {
    private String tpaDutyId;
    private String treatmentType;
    private Long segmentIs;
    private Long splitIs;
    private Long auditConclusion;
    private String billRemark;
    private String insuType;
    private String dutyCode;
    private String extendDutyName;
    private String dutySubcode;
    private BigDecimal billDutyCompensateAmt = BigDecimal.ZERO;
    private String billMemo;
    private Integer detailType;
    private String billName;
    private String detailImgPath;
    private BigDecimal billReasonableAmount = BigDecimal.ZERO;
    private BigDecimal billApplyAmount = BigDecimal.ZERO;
    private BigDecimal medicalSelfPayAmount = BigDecimal.ZERO;
    private BigDecimal partSelfPayAmount = BigDecimal.ZERO;
    private BigDecimal allSelfPayAmount = BigDecimal.ZERO;
    private BigDecimal medicalPayAmount = BigDecimal.ZERO;
    private BigDecimal cashPayAmount = BigDecimal.ZERO;
    private String itemKindCode;
    private String itemKindName;
    private BigDecimal dutyCompensateRatio = BigDecimal.ZERO;
    private String thirdFeeCode;
    private BigDecimal billDutyCompensateAllAmt = BigDecimal.ZERO;
    private BigDecimal abtmAmt = BigDecimal.ZERO;
    private BigDecimal invoiceAllowanceDays = BigDecimal.ZERO;
    private BigDecimal responsibilityDeductibleDays = BigDecimal.ZERO;
    private BigDecimal dailyCompensationAmount = BigDecimal.ZERO;
    private String dutyGuid;
    private Integer isElectronicInvoice;
    private String invoiceCode;
    private String invoiceVerificationCode;
    private Date invoiceDate;
    private Integer isBjInvoice;
    private Integer isVerifyValid;
    private String isHistoryInjury;
    private BigDecimal underMinimumAmt = BigDecimal.ZERO;
    private BigDecimal upperMinimumAmt = BigDecimal.ZERO;
    private BigDecimal compLimitAmt = BigDecimal.ZERO;
    private BigDecimal upperCompLimitAmt = BigDecimal.ZERO;
    private BigDecimal underMinimumPayAmt = BigDecimal.ZERO;
    private BigDecimal upperMinimumPayAmt = BigDecimal.ZERO;
    private BigDecimal compLimitPayAmt = BigDecimal.ZERO;
    private BigDecimal upperCompLimitPayAmt = BigDecimal.ZERO;
    private String arithmeticFormula;
    private String financeVoucherTypeCode;
    private Integer isCriticalIllness;
    private Integer isChronicDisease;
    private String outpatientNo;
    private String medicalRecordNo;
    private String inpatientNo;
    private String inpatientDepartment;
    private BigDecimal prepaidAmt = BigDecimal.ZERO;
    private BigDecimal refundAmt = BigDecimal.ZERO;
    private BigDecimal criticalIllnessInsAmt = BigDecimal.ZERO;
    private BigDecimal medicalAssistAmt = BigDecimal.ZERO;
    private BigDecimal civilServantMedicalSubsidyAmt = BigDecimal.ZERO;
    private BigDecimal majorSupplementAmt = BigDecimal.ZERO;
    private BigDecimal otherAmt = BigDecimal.ZERO;
    private BigDecimal illnessDeathBenefitAmt = BigDecimal.ZERO;
    private BigDecimal selfPaidAmt = BigDecimal.ZERO;
    private BigDecimal selfFinanceAmt = BigDecimal.ZERO;
    private String billRepetitionExplain;
    private Integer isPharmacyLicenseVerified;
    private String paperType;

    // other
    private Map<String, String> extraFields = new HashMap<>();
}
