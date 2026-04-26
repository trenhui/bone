package com.bone.tpa.sdk.adjustment.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.domain.extension.ExtensibleObject;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;
/**
 * 理算明细信息
 *

 */
@Table("ia_adjustment_record")
@Builder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class AdjustmentRecord extends ExtensibleObject<Long> {

    /**
     * 关联赔案ID
     */
    private Long relatedId;

    /**
     * 赔案出险时间
     */
    private Date outInsureTime;

    /**
     * 发票ID
     */
    private Long invoiceId;

    /**
     * 发票号
     */
    private String invoiceNo;

    /**
     * 发票开票日期
     */
    private Date invoiceDate;

    /**
     * 发票就诊日期
     */
    private Date visitDate;

    /**
     * 发票医院名称
     */
    private String hospitalName;

    /**
     * 发票住院科别
     */
    private String hospitalDepartment;

    /**
     * 发票疾病诊断
     */
    private String diagnosis;

    /**
     * 计划uuid
     */
    private String policyNo;

    /**
     * 计划uuid
     */
    private String planUuid;

    /**
     * 险种名称
     */
    private String coverageName;

    /**
     * 责任UUID
     */
    private String liabilityUuid;

    /**
     * 责任名称
     */
    private String liabilityName;

    /**
     * 被保险人姓名
     */
    private String insuredName;

    /**
     * 被保险人证件类型
     */
    private String insuredCertificateType;

    /**
     * 被保险人证件号
     */
    private String insuredCertificateNumber;

    /**
     * 发票上的是否有医保
     */
    private Integer invoiceHasYb;

    /**
     * 发票上的医院等级
     */
    private String invoiceHospitalLevel;

    /**
     * 发票上的医院类型
     */
    private String invoiceHospitalType;

    /**
     * 计划版本
     */
    private String version;

    /**
     * 理算明细状态
     */
    private String recordStatus;

    /**
     * 就诊类型
     */
    private String visitType;

    /**
     * 责任账户类型
     */
    private String accountType;

    /**
     * 公账保单号
     */
    private String publicAmountPolicyNo;

    /**
     * 发票金额
     */
    private BigDecimal invoiceAmount;

    /**
     * 免赔方式
     */
    private String deductType;

    /**
     * 理算前剩余的免赔额度/免赔天数
     */
    private String remainDeductLimit;

    /**
     * 免赔额
     */
    private BigDecimal deductAmount;

    /**
     * 免赔天数
     */
    private Integer deductDays;

    /**
     * 责任控额方式
     */
    private String quotaType;

    /**
     * 额度信息
     */
    private String quotaDetail;

    /**
     * 赔付金额
     */
    private BigDecimal payoutAmount;

    /**
     * 每个单独金额成分的理算金额，在控额之前
     */
    private String feeCalculateList;

    /**
     * 每个单独金额成分的赔付比例
     */
    private String ratioPayoutList;

    /**
     * 每个单独金额成分的赔付额
     */
    private String feePayoutList;

    /**
     * 每个单独金额成分的免赔额
     */
    private String feeDeductList;

    /**
     * 每个单独金额成分的赔付额所对应占用的金额
     */
    private String feeOccupyList;

    /**
     * 发票上每个单独金额成分剩余的金额
     */
    private String feeRemainList;

    /**
     * 理算公式
     */
    private String formula;

    /**
     * 理算结论
     */
    private String adjustmentResult;

    /**
     * 发票结论
     */
    private String invoiceResult;

    /**
     * 结论明细
     */
    private String resultDetail;

    /**
     * 赔付结论原因类型
     */
    private String compensateType;

    /**
     * 操作人员名称
     */
    private String operatorName;

    // Getters and Setters
    public Long getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(Long relatedId) {
        this.relatedId = relatedId;
    }

    public Date getOutInsureTime() {
        return outInsureTime;
    }

    public void setOutInsureTime(Date outInsureTime) {
        this.outInsureTime = outInsureTime;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    public Date getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(Date invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public Date getVisitDate() {
        return visitDate;
    }

    public void setVisitDate(Date visitDate) {
        this.visitDate = visitDate;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public String getHospitalDepartment() {
        return hospitalDepartment;
    }

    public void setHospitalDepartment(String hospitalDepartment) {
        this.hospitalDepartment = hospitalDepartment;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public String getPolicyNo() {
        return policyNo;
    }

    public void setPolicyNo(String policyNo) {
        this.policyNo = policyNo;
    }

    public String getPlanUuid() {
        return planUuid;
    }

    public void setPlanUuid(String planUuid) {
        this.planUuid = planUuid;
    }

    public String getCoverageName() {
        return coverageName;
    }

    public void setCoverageName(String coverageName) {
        this.coverageName = coverageName;
    }

    public String getLiabilityUuid() {
        return liabilityUuid;
    }

    public void setLiabilityUuid(String liabilityUuid) {
        this.liabilityUuid = liabilityUuid;
    }

    public String getLiabilityName() {
        return liabilityName;
    }

    public void setLiabilityName(String liabilityName) {
        this.liabilityName = liabilityName;
    }

    public String getInsuredName() {
        return insuredName;
    }

    public void setInsuredName(String insuredName) {
        this.insuredName = insuredName;
    }

    public String getInsuredCertificateType() {
        return insuredCertificateType;
    }

    public void setInsuredCertificateType(String insuredCertificateType) {
        this.insuredCertificateType = insuredCertificateType;
    }

    public String getInsuredCertificateNumber() {
        return insuredCertificateNumber;
    }

    public void setInsuredCertificateNumber(String insuredCertificateNumber) {
        this.insuredCertificateNumber = insuredCertificateNumber;
    }

    public Integer getInvoiceHasYb() {
        return invoiceHasYb;
    }

    public void setInvoiceHasYb(Integer invoiceHasYb) {
        this.invoiceHasYb = invoiceHasYb;
    }

    public String getInvoiceHospitalLevel() {
        return invoiceHospitalLevel;
    }

    public void setInvoiceHospitalLevel(String invoiceHospitalLevel) {
        this.invoiceHospitalLevel = invoiceHospitalLevel;
    }

    public String getInvoiceHospitalType() {
        return invoiceHospitalType;
    }

    public void setInvoiceHospitalType(String invoiceHospitalType) {
        this.invoiceHospitalType = invoiceHospitalType;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getRecordStatus() {
        return recordStatus;
    }

    public void setRecordStatus(String recordStatus) {
        this.recordStatus = recordStatus;
    }

    public String getVisitType() {
        return visitType;
    }

    public void setVisitType(String visitType) {
        this.visitType = visitType;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getPublicAmountPolicyNo() {
        return publicAmountPolicyNo;
    }

    public void setPublicAmountPolicyNo(String publicAmountPolicyNo) {
        this.publicAmountPolicyNo = publicAmountPolicyNo;
    }

    public BigDecimal getInvoiceAmount() {
        return invoiceAmount;
    }

    public void setInvoiceAmount(BigDecimal invoiceAmount) {
        this.invoiceAmount = invoiceAmount;
    }

    public String getDeductType() {
        return deductType;
    }

    public void setDeductType(String deductType) {
        this.deductType = deductType;
    }

    public String getRemainDeductLimit() {
        return remainDeductLimit;
    }

    public void setRemainDeductLimit(String remainDeductLimit) {
        this.remainDeductLimit = remainDeductLimit;
    }

    public BigDecimal getDeductAmount() {
        return deductAmount;
    }

    public void setDeductAmount(BigDecimal deductAmount) {
        this.deductAmount = deductAmount;
    }

    public Integer getDeductDays() {
        return deductDays;
    }

    public void setDeductDays(Integer deductDays) {
        this.deductDays = deductDays;
    }

    public String getQuotaType() {
        return quotaType;
    }

    public void setQuotaType(String quotaType) {
        this.quotaType = quotaType;
    }

    public String getQuotaDetail() {
        return quotaDetail;
    }

    public void setQuotaDetail(String quotaDetail) {
        this.quotaDetail = quotaDetail;
    }

    public BigDecimal getPayoutAmount() {
        return payoutAmount;
    }

    public void setPayoutAmount(BigDecimal payoutAmount) {
        this.payoutAmount = payoutAmount;
    }

    public String getFeeCalculateList() {
        return feeCalculateList;
    }

    public void setFeeCalculateList(String feeCalculateList) {
        this.feeCalculateList = feeCalculateList;
    }

    public String getRatioPayoutList() {
        return ratioPayoutList;
    }

    public void setRatioPayoutList(String ratioPayoutList) {
        this.ratioPayoutList = ratioPayoutList;
    }

    public String getFeePayoutList() {
        return feePayoutList;
    }

    public void setFeePayoutList(String feePayoutList) {
        this.feePayoutList = feePayoutList;
    }

    public String getFeeDeductList() {
        return feeDeductList;
    }

    public void setFeeDeductList(String feeDeductList) {
        this.feeDeductList = feeDeductList;
    }

    public String getFeeOccupyList() {
        return feeOccupyList;
    }

    public void setFeeOccupyList(String feeOccupyList) {
        this.feeOccupyList = feeOccupyList;
    }

    public String getFeeRemainList() {
        return feeRemainList;
    }

    public void setFeeRemainList(String feeRemainList) {
        this.feeRemainList = feeRemainList;
    }

    public String getFormula() {
        return formula;
    }

    public void setFormula(String formula) {
        this.formula = formula;
    }

    public String getAdjustmentResult() {
        return adjustmentResult;
    }

    public void setAdjustmentResult(String adjustmentResult) {
        this.adjustmentResult = adjustmentResult;
    }

    public String getInvoiceResult() {
        return invoiceResult;
    }

    public void setInvoiceResult(String invoiceResult) {
        this.invoiceResult = invoiceResult;
    }

    public String getResultDetail() {
        return resultDetail;
    }

    public void setResultDetail(String resultDetail) {
        this.resultDetail = resultDetail;
    }

    public String getCompensateType() {
        return compensateType;
    }

    public void setCompensateType(String compensateType) {
        this.compensateType = compensateType;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }
}
