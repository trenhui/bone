package com.bone.tpa.sdk.masterdb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 赔付结论数据
 */
public class TbOverClaimConclusion implements Serializable {


    @TableId(value="conclusion_id",type = IdType.AUTO)
    private Long conclusionId;

    /**
     * 理算ID
     *
     * @mbg.generated
     */
    @Schema(description = "理算ID")
    private String adjustmentguid;

    /**
     * 太保批次号
     *
     * @mbg.generated
     */
    @Schema(description = "太保批次号")
    private String conclusionBatchCode;

    /**
     * 团单号
     *
     * @mbg.generated
     */
    @Schema(description = "团单号")
    private String conclusionGroupPolicy;

    /**
     * 被保人身份证
     *
     * @mbg.generated
     */
    @Schema(description = "被保人身份证")
    private String conclusionRecognizeeCertid;

    /**
     * 性别
     *
     * @mbg.generated
     */
    @Schema(description = "性别")
    private String conclusionPersonGender;

    /**
     * 姓名
     *
     * @mbg.generated
     */
    @Schema(description = "姓名")
    private String conclusionPersonName;

    /**
     * 出险日期
     *
     * @mbg.generated
     */
    @Schema(description = "出险日期")
    private String conclusionPolicyDate;

    /**
     * 出险类型
     *
     * @mbg.generated
     */
    @Schema(description = "出险类型")
    private Integer conclusionPolicyType;

    /**
     * 入院日期
     *
     * @mbg.generated
     */
    @Schema(description = "入院日期")
    private String conclusionInHospitalDate;

    /**
     * 出院日期
     *
     * @mbg.generated
     */
    @Schema(description = "出院日期")
    private String conclusionOutHospitalDate;

    /**
     * 索赔事故性质
     *
     * @mbg.generated
     */
    @Schema(description = "索赔事故性质")
    private String conclusionAccidentNature;

    /**
     * 出险经过与结果
     *
     * @mbg.generated
     */
    @Schema(description = "出险经过与结果")
    private String conclusionOutPass;

    /**
     * 疾病代码
     *
     * @mbg.generated
     */
    @Schema(description = "疾病代码")
    private String conclusionDiseaseCode;

    /**
     * 手术代码
     *
     * @mbg.generated
     */
    @Schema(description = "手术代码")
    private String conclusionOperationCode;

    /**
     * 医院代码
     *
     * @mbg.generated
     */
    @Schema(description = "医院代码")
    private String conclusionHospitalCode;

    /**
     * 领款人身份证
     *
     * @mbg.generated
     */
    @Schema(description = "领款人身份证")
    private String conclusionPayeeCertid;

    /**
     * 领款人姓名
     *
     * @mbg.generated
     */
    @Schema(description = "领款人姓名")
    private String conclusionPayeeName;

    /**
     * 领款人支付方式
     *
     * @mbg.generated
     */
    @Schema(description = "领款人支付方式")
    private String conclusionPayeePayWay;

    /**
     * 领款人开户行
     *
     * @mbg.generated
     */
    @Schema(description = "领款人开户行")
    private String conclusionPayeeBank;

    /**
     * 领款人账号
     *
     * @mbg.generated
     */
    @Schema(description = "领款人账号")
    private String conclusionPayeeAccount;

    /**
     * 手机号
     *
     * @mbg.generated
     */
    @Schema(description = "手机号")
    private String conclusionMobile;

    /**
     * 第三方赔案号
     *
     * @mbg.generated
     */
    @Schema(description = "第三方赔案号")
    private String conclusionClaimCode;

    /**
     * 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     *
     * @mbg.generated
     */
    @Schema(description = "专门用于给太保发送用的，其实就是“PK0000000'+caseCode")
    private String conclusionClaimCodeTb;

    private String conclusionUnCompensateCause;

    /**
     * 保单号
     *
     * @mbg.generated
     */
    @Schema(description = "保单号")
    private String conclusionPolicyCode;

    /**
     * 险种
     *
     * @mbg.generated
     */
    @Schema(description = "险种")
    private String conclusionInsuType;

    /**
     * 责任
     *
     * @mbg.generated
     */
    @Schema(description = "责任")
    private String conclusionDuty;

    /**
     * 责任子码
     *
     * @mbg.generated
     */
    @Schema(description = "责任子码")
    private String conclusionDutySubcode;

    /**
     * 理赔结论,1-正常，2-协议，3-拒赔，4-通融
     *
     * @mbg.generated
     */
    @Schema(description = "理赔结论,1-正常，2-协议，3-拒赔，4-通融")
    private String conclusionClaimConclusion;

    /**
     * 结论子码
     *
     * @mbg.generated
     */
    @Schema(description = "结论子码")
    private String conclusionSubcode;

    /**
     * 赔付金额
     *
     * @mbg.generated
     */
    @Schema(description = "赔付金额 ")
    private BigDecimal conclusionCompensateAmt;

    private Date conclusionCreatetime;

    private Date conclusionUpdatetime;

    /**
     * 状态，默认1，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，-1-作废
     *
     * @mbg.generated
     */
    @Schema(description = "状态，默认1，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，-1-作废")
    private Integer conclusionStatus;

    private String conclusionMemo;

    /**
     * TPA批次号
     *
     * @mbg.generated
     */
    @Schema(description = "TPA批次号")
    private String claimheadnumber;

    /**
     * 责任名称
     *
     * @mbg.generated
     */
    @Schema(description = "责任名称")
    private String conclusionDutyName;

    /**
     * 打包结案文件时生成的批次号，会在成品邮件已结结案数据里面用到，格式为时间戳，如：20180729215941425
     *
     * @mbg.generated
     */
    @Schema(description = "打包结案文件时生成的批次号，会在成品邮件已结结案数据里面用到，格式为时间戳，如：20180729215941425")
    private String conclusionBatchId;

    /**
     * 填写【线上】或者【线下】，案件是否是太享福线上的
     *
     * @mbg.generated
     */
    @Schema(description = "填写【线上】或者【线下】，案件是否是太享福线上的")
    private String conclusionIsonline;

    /**
     * 领款人证件类型，参考枚举表tb_dict
     *
     * @mbg.generated
     */
    @Schema(description = "领款人证件类型，参考枚举表tb_dict")
    private String conclusionPayeeCerttype;

    /**
     * 理赔结论代码，参考枚举tb_dict中的conclusionCode
     *
     * @mbg.generated
     */
    @Schema(description = "理赔结论代码，参考枚举tb_dict中的conclusionCode")
    private String conclusionClaimConclusionCode;

    /**
     * 出险人证件类型，枚举tb_dict
     *
     * @mbg.generated
     */
    @Schema(description = "出险人证件类型，枚举tb_dict")
    private String conclusionPersonCerttype;

    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database table tb_over_claim_conclusion
     *
     * @mbg.generated
     */
    private static final long serialVersionUID = 1L;

    /**
     *
     * @return conclusion_id
     */
    public Long getConclusionId() {
        return conclusionId;
    }

    /**
     *
     * @param conclusionId
     */
    public void setConclusionId(Long conclusionId) {
        this.conclusionId = conclusionId;
    }

    /**
     * 理算ID
     * @return AdjustmentGuid 理算ID
     */
    public String getAdjustmentguid() {
        return adjustmentguid;
    }

    /**
     * 理算ID
     * @param adjustmentguid 理算ID
     */
    public void setAdjustmentguid(String adjustmentguid) {
        this.adjustmentguid = adjustmentguid;
    }

    /**
     * 太保批次号
     * @return conclusion_batch_code 太保批次号
     */
    public String getConclusionBatchCode() {
        return conclusionBatchCode;
    }

    /**
     * 太保批次号
     * @param conclusionBatchCode 太保批次号
     */
    public void setConclusionBatchCode(String conclusionBatchCode) {
        this.conclusionBatchCode = conclusionBatchCode;
    }

    /**
     * 团单号
     * @return conclusion_group_policy 团单号
     */
    public String getConclusionGroupPolicy() {
        return conclusionGroupPolicy;
    }

    /**
     * 团单号
     * @param conclusionGroupPolicy 团单号
     */
    public void setConclusionGroupPolicy(String conclusionGroupPolicy) {
        this.conclusionGroupPolicy = conclusionGroupPolicy;
    }

    /**
     * 被保人身份证
     * @return conclusion_recognizee_certId 被保人身份证
     */
    public String getConclusionRecognizeeCertid() {
        return conclusionRecognizeeCertid;
    }

    /**
     * 被保人身份证
     * @param conclusionRecognizeeCertid 被保人身份证
     */
    public void setConclusionRecognizeeCertid(String conclusionRecognizeeCertid) {
        this.conclusionRecognizeeCertid = conclusionRecognizeeCertid;
    }

    /**
     * 性别
     * @return conclusion_person_gender 性别
     */
    public String getConclusionPersonGender() {
        return conclusionPersonGender;
    }

    /**
     * 性别
     * @param conclusionPersonGender 性别
     */
    public void setConclusionPersonGender(String conclusionPersonGender) {
        this.conclusionPersonGender = conclusionPersonGender;
    }

    /**
     * 姓名
     * @return conclusion_person_name 姓名
     */
    public String getConclusionPersonName() {
        return conclusionPersonName;
    }

    /**
     * 姓名
     * @param conclusionPersonName 姓名
     */
    public void setConclusionPersonName(String conclusionPersonName) {
        this.conclusionPersonName = conclusionPersonName;
    }

    /**
     * 出险日期
     * @return conclusion_policy_date 出险日期
     */
    public String getConclusionPolicyDate() {
        return conclusionPolicyDate;
    }

    /**
     * 出险日期
     * @param conclusionPolicyDate 出险日期
     */
    public void setConclusionPolicyDate(String conclusionPolicyDate) {
        this.conclusionPolicyDate = conclusionPolicyDate;
    }

    /**
     * 出险类型
     * @return conclusion_policy_type 出险类型
     */
    public Integer getConclusionPolicyType() {
        return conclusionPolicyType;
    }

    /**
     * 出险类型
     * @param conclusionPolicyType 出险类型
     */
    public void setConclusionPolicyType(Integer conclusionPolicyType) {
        this.conclusionPolicyType = conclusionPolicyType;
    }

    /**
     * 入院日期
     * @return conclusion_in_hospital_date 入院日期
     */
    public String getConclusionInHospitalDate() {
        return conclusionInHospitalDate;
    }

    /**
     * 入院日期
     * @param conclusionInHospitalDate 入院日期
     */
    public void setConclusionInHospitalDate(String conclusionInHospitalDate) {
        this.conclusionInHospitalDate = conclusionInHospitalDate;
    }

    /**
     * 出院日期
     * @return conclusion_out_hospital_date 出院日期
     */
    public String getConclusionOutHospitalDate() {
        return conclusionOutHospitalDate;
    }

    /**
     * 出院日期
     * @param conclusionOutHospitalDate 出院日期
     */
    public void setConclusionOutHospitalDate(String conclusionOutHospitalDate) {
        this.conclusionOutHospitalDate = conclusionOutHospitalDate;
    }

    /**
     * 索赔事故性质
     * @return conclusion_accident_nature 索赔事故性质
     */
    public String getConclusionAccidentNature() {
        return conclusionAccidentNature;
    }

    /**
     * 索赔事故性质
     * @param conclusionAccidentNature 索赔事故性质
     */
    public void setConclusionAccidentNature(String conclusionAccidentNature) {
        this.conclusionAccidentNature = conclusionAccidentNature;
    }

    /**
     * 出险经过与结果
     * @return conclusion_out_pass 出险经过与结果
     */
    public String getConclusionOutPass() {
        return conclusionOutPass;
    }

    /**
     * 出险经过与结果
     * @param conclusionOutPass 出险经过与结果
     */
    public void setConclusionOutPass(String conclusionOutPass) {
        this.conclusionOutPass = conclusionOutPass;
    }

    /**
     * 疾病代码
     * @return conclusion_disease_code 疾病代码
     */
    public String getConclusionDiseaseCode() {
        return conclusionDiseaseCode;
    }

    /**
     * 疾病代码
     * @param conclusionDiseaseCode 疾病代码
     */
    public void setConclusionDiseaseCode(String conclusionDiseaseCode) {
        this.conclusionDiseaseCode = conclusionDiseaseCode;
    }

    /**
     * 手术代码
     * @return conclusion_operation_code 手术代码
     */
    public String getConclusionOperationCode() {
        return conclusionOperationCode;
    }

    /**
     * 手术代码
     * @param conclusionOperationCode 手术代码
     */
    public void setConclusionOperationCode(String conclusionOperationCode) {
        this.conclusionOperationCode = conclusionOperationCode;
    }

    /**
     * 医院代码
     * @return conclusion_hospital_code 医院代码
     */
    public String getConclusionHospitalCode() {
        return conclusionHospitalCode;
    }

    /**
     * 医院代码
     * @param conclusionHospitalCode 医院代码
     */
    public void setConclusionHospitalCode(String conclusionHospitalCode) {
        this.conclusionHospitalCode = conclusionHospitalCode;
    }

    /**
     * 领款人身份证
     * @return conclusion_payee_certId 领款人身份证
     */
    public String getConclusionPayeeCertid() {
        return conclusionPayeeCertid;
    }

    /**
     * 领款人身份证
     * @param conclusionPayeeCertid 领款人身份证
     */
    public void setConclusionPayeeCertid(String conclusionPayeeCertid) {
        this.conclusionPayeeCertid = conclusionPayeeCertid;
    }

    /**
     * 领款人姓名
     * @return conclusion_payee_name 领款人姓名
     */
    public String getConclusionPayeeName() {
        return conclusionPayeeName;
    }

    /**
     * 领款人姓名
     * @param conclusionPayeeName 领款人姓名
     */
    public void setConclusionPayeeName(String conclusionPayeeName) {
        this.conclusionPayeeName = conclusionPayeeName;
    }

    /**
     * 领款人支付方式
     * @return conclusion_payee_pay_way 领款人支付方式
     */
    public String getConclusionPayeePayWay() {
        return conclusionPayeePayWay;
    }

    /**
     * 领款人支付方式
     * @param conclusionPayeePayWay 领款人支付方式
     */
    public void setConclusionPayeePayWay(String conclusionPayeePayWay) {
        this.conclusionPayeePayWay = conclusionPayeePayWay;
    }

    /**
     * 领款人开户行
     * @return conclusion_payee_bank 领款人开户行
     */
    public String getConclusionPayeeBank() {
        return conclusionPayeeBank;
    }

    /**
     * 领款人开户行
     * @param conclusionPayeeBank 领款人开户行
     */
    public void setConclusionPayeeBank(String conclusionPayeeBank) {
        this.conclusionPayeeBank = conclusionPayeeBank;
    }

    /**
     * 领款人账号
     * @return conclusion_payee_account 领款人账号
     */
    public String getConclusionPayeeAccount() {
        return conclusionPayeeAccount;
    }

    /**
     * 领款人账号
     * @param conclusionPayeeAccount 领款人账号
     */
    public void setConclusionPayeeAccount(String conclusionPayeeAccount) {
        this.conclusionPayeeAccount = conclusionPayeeAccount;
    }

    /**
     * 手机号
     * @return conclusion_mobile 手机号
     */
    public String getConclusionMobile() {
        return conclusionMobile;
    }

    /**
     * 手机号
     * @param conclusionMobile 手机号
     */
    public void setConclusionMobile(String conclusionMobile) {
        this.conclusionMobile = conclusionMobile;
    }

    /**
     * 第三方赔案号
     * @return conclusion_claim_code 第三方赔案号
     */
    public String getConclusionClaimCode() {
        return conclusionClaimCode;
    }

    /**
     * 第三方赔案号
     * @param conclusionClaimCode 第三方赔案号
     */
    public void setConclusionClaimCode(String conclusionClaimCode) {
        this.conclusionClaimCode = conclusionClaimCode;
    }

    /**
     * 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     * @return conclusion_claim_code_tb 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     */
    public String getConclusionClaimCodeTb() {
        return conclusionClaimCodeTb;
    }

    /**
     * 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     * @param conclusionClaimCodeTb 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     */
    public void setConclusionClaimCodeTb(String conclusionClaimCodeTb) {
        this.conclusionClaimCodeTb = conclusionClaimCodeTb;
    }

    /**
     *
     * @return conclusion_un_compensate_cause
     */
    public String getConclusionUnCompensateCause() {
        return conclusionUnCompensateCause;
    }

    /**
     *
     * @param conclusionUnCompensateCause
     */
    public void setConclusionUnCompensateCause(String conclusionUnCompensateCause) {
        this.conclusionUnCompensateCause = conclusionUnCompensateCause;
    }

    /**
     * 保单号
     * @return conclusion_policy_code 保单号
     */
    public String getConclusionPolicyCode() {
        return conclusionPolicyCode;
    }

    /**
     * 保单号
     * @param conclusionPolicyCode 保单号
     */
    public void setConclusionPolicyCode(String conclusionPolicyCode) {
        this.conclusionPolicyCode = conclusionPolicyCode;
    }

    /**
     * 险种
     * @return conclusion_insu_type 险种
     */
    public String getConclusionInsuType() {
        return conclusionInsuType;
    }

    /**
     * 险种
     * @param conclusionInsuType 险种
     */
    public void setConclusionInsuType(String conclusionInsuType) {
        this.conclusionInsuType = conclusionInsuType;
    }

    /**
     * 责任
     * @return conclusion_duty 责任
     */
    public String getConclusionDuty() {
        return conclusionDuty;
    }

    /**
     * 责任
     * @param conclusionDuty 责任
     */
    public void setConclusionDuty(String conclusionDuty) {
        this.conclusionDuty = conclusionDuty;
    }

    /**
     * 责任子码
     * @return conclusion_duty_subcode 责任子码
     */
    public String getConclusionDutySubcode() {
        return conclusionDutySubcode;
    }

    /**
     * 责任子码
     * @param conclusionDutySubcode 责任子码
     */
    public void setConclusionDutySubcode(String conclusionDutySubcode) {
        this.conclusionDutySubcode = conclusionDutySubcode;
    }

    /**
     * 理赔结论,1-正常，2-协议，3-拒赔，4-通融
     * @return conclusion_claim_conclusion 理赔结论,1-正常，2-协议，3-拒赔，4-通融
     */
    public String getConclusionClaimConclusion() {
        return conclusionClaimConclusion;
    }

    /**
     * 理赔结论,1-正常，2-协议，3-拒赔，4-通融
     * @param conclusionClaimConclusion 理赔结论,1-正常，2-协议，3-拒赔，4-通融
     */
    public void setConclusionClaimConclusion(String conclusionClaimConclusion) {
        this.conclusionClaimConclusion = conclusionClaimConclusion;
    }

    /**
     * 结论子码
     * @return conclusion_subcode 结论子码
     */
    public String getConclusionSubcode() {
        return conclusionSubcode;
    }

    /**
     * 结论子码
     * @param conclusionSubcode 结论子码
     */
    public void setConclusionSubcode(String conclusionSubcode) {
        this.conclusionSubcode = conclusionSubcode;
    }

    /**
     * 赔付金额
     * @return conclusion_compensate_amt 赔付金额
     */
    public BigDecimal getConclusionCompensateAmt() {
        return conclusionCompensateAmt;
    }

    /**
     * 赔付金额
     * @param conclusionCompensateAmt 赔付金额
     */
    public void setConclusionCompensateAmt(BigDecimal conclusionCompensateAmt) {
        this.conclusionCompensateAmt = conclusionCompensateAmt;
    }

    /**
     *
     * @return conclusion_createTime
     */
    public Date getConclusionCreatetime() {
        return conclusionCreatetime;
    }

    /**
     *
     * @param conclusionCreatetime
     */
    public void setConclusionCreatetime(Date conclusionCreatetime) {
        this.conclusionCreatetime = conclusionCreatetime;
    }

    /**
     *
     * @return conclusion_updateTime
     */
    public Date getConclusionUpdatetime() {
        return conclusionUpdatetime;
    }

    /**
     *
     * @param conclusionUpdatetime
     */
    public void setConclusionUpdatetime(Date conclusionUpdatetime) {
        this.conclusionUpdatetime = conclusionUpdatetime;
    }

    /**
     * 状态，默认1，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，-1-作废
     * @return conclusion_status 状态，默认1，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，-1-作废
     */
    public Integer getConclusionStatus() {
        return conclusionStatus;
    }

    /**
     * 状态，默认1，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，-1-作废
     * @param conclusionStatus 状态，默认1，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，-1-作废
     */
    public void setConclusionStatus(Integer conclusionStatus) {
        this.conclusionStatus = conclusionStatus;
    }

    /**
     *
     * @return conclusion_memo
     */
    public String getConclusionMemo() {
        return conclusionMemo;
    }

    /**
     *
     * @param conclusionMemo
     */
    public void setConclusionMemo(String conclusionMemo) {
        this.conclusionMemo = conclusionMemo;
    }

    /**
     * TPA批次号
     * @return claimHeadNumber TPA批次号
     */
    public String getClaimheadnumber() {
        return claimheadnumber;
    }

    /**
     * TPA批次号
     * @param claimheadnumber TPA批次号
     */
    public void setClaimheadnumber(String claimheadnumber) {
        this.claimheadnumber = claimheadnumber;
    }

    /**
     * 责任名称
     * @return conclusion_duty_name 责任名称
     */
    public String getConclusionDutyName() {
        return conclusionDutyName;
    }

    /**
     * 责任名称
     * @param conclusionDutyName 责任名称
     */
    public void setConclusionDutyName(String conclusionDutyName) {
        this.conclusionDutyName = conclusionDutyName;
    }

    /**
     * 打包结案文件时生成的批次号，会在成品邮件已结结案数据里面用到，格式为时间戳，如：20180729215941425
     * @return conclusion_batch_id 打包结案文件时生成的批次号，会在成品邮件已结结案数据里面用到，格式为时间戳，如：20180729215941425
     */
    public String getConclusionBatchId() {
        return conclusionBatchId;
    }

    /**
     * 打包结案文件时生成的批次号，会在成品邮件已结结案数据里面用到，格式为时间戳，如：20180729215941425
     * @param conclusionBatchId 打包结案文件时生成的批次号，会在成品邮件已结结案数据里面用到，格式为时间戳，如：20180729215941425
     */
    public void setConclusionBatchId(String conclusionBatchId) {
        this.conclusionBatchId = conclusionBatchId;
    }

    /**
     * 填写【线上】或者【线下】，案件是否是太享福线上的
     * @return conclusion_isOnline 填写【线上】或者【线下】，案件是否是太享福线上的
     */
    public String getConclusionIsonline() {
        return conclusionIsonline;
    }

    /**
     * 填写【线上】或者【线下】，案件是否是太享福线上的
     * @param conclusionIsonline 填写【线上】或者【线下】，案件是否是太享福线上的
     */
    public void setConclusionIsonline(String conclusionIsonline) {
        this.conclusionIsonline = conclusionIsonline;
    }

    /**
     * 领款人证件类型，参考枚举表tb_dict
     * @return conclusion_payee_certType 领款人证件类型，参考枚举表tb_dict
     */
    public String getConclusionPayeeCerttype() {
        return conclusionPayeeCerttype;
    }

    /**
     * 领款人证件类型，参考枚举表tb_dict
     * @param conclusionPayeeCerttype 领款人证件类型，参考枚举表tb_dict
     */
    public void setConclusionPayeeCerttype(String conclusionPayeeCerttype) {
        this.conclusionPayeeCerttype = conclusionPayeeCerttype;
    }

    /**
     * 理赔结论代码，参考枚举tb_dict中的conclusionCode
     * @return conclusion_claim_conclusion_code 理赔结论代码，参考枚举tb_dict中的conclusionCode
     */
    public String getConclusionClaimConclusionCode() {
        return conclusionClaimConclusionCode;
    }

    /**
     * 理赔结论代码，参考枚举tb_dict中的conclusionCode
     * @param conclusionClaimConclusionCode 理赔结论代码，参考枚举tb_dict中的conclusionCode
     */
    public void setConclusionClaimConclusionCode(String conclusionClaimConclusionCode) {
        this.conclusionClaimConclusionCode = conclusionClaimConclusionCode;
    }

    /**
     * 出险人证件类型，枚举tb_dict
     * @return conclusion_person_certType 出险人证件类型，枚举tb_dict
     */
    public String getConclusionPersonCerttype() {
        return conclusionPersonCerttype;
    }

    /**
     * 出险人证件类型，枚举tb_dict
     * @param conclusionPersonCerttype 出险人证件类型，枚举tb_dict
     */
    public void setConclusionPersonCerttype(String conclusionPersonCerttype) {
        this.conclusionPersonCerttype = conclusionPersonCerttype;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table tb_over_claim_conclusion
     *
     * @mbg.generated
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", conclusionId=").append(conclusionId);
        sb.append(", adjustmentguid=").append(adjustmentguid);
        sb.append(", conclusionBatchCode=").append(conclusionBatchCode);
        sb.append(", conclusionGroupPolicy=").append(conclusionGroupPolicy);
        sb.append(", conclusionRecognizeeCertid=").append(conclusionRecognizeeCertid);
        sb.append(", conclusionPersonGender=").append(conclusionPersonGender);
        sb.append(", conclusionPersonName=").append(conclusionPersonName);
        sb.append(", conclusionPolicyDate=").append(conclusionPolicyDate);
        sb.append(", conclusionPolicyType=").append(conclusionPolicyType);
        sb.append(", conclusionInHospitalDate=").append(conclusionInHospitalDate);
        sb.append(", conclusionOutHospitalDate=").append(conclusionOutHospitalDate);
        sb.append(", conclusionAccidentNature=").append(conclusionAccidentNature);
        sb.append(", conclusionOutPass=").append(conclusionOutPass);
        sb.append(", conclusionDiseaseCode=").append(conclusionDiseaseCode);
        sb.append(", conclusionOperationCode=").append(conclusionOperationCode);
        sb.append(", conclusionHospitalCode=").append(conclusionHospitalCode);
        sb.append(", conclusionPayeeCertid=").append(conclusionPayeeCertid);
        sb.append(", conclusionPayeeName=").append(conclusionPayeeName);
        sb.append(", conclusionPayeePayWay=").append(conclusionPayeePayWay);
        sb.append(", conclusionPayeeBank=").append(conclusionPayeeBank);
        sb.append(", conclusionPayeeAccount=").append(conclusionPayeeAccount);
        sb.append(", conclusionMobile=").append(conclusionMobile);
        sb.append(", conclusionClaimCode=").append(conclusionClaimCode);
        sb.append(", conclusionClaimCodeTb=").append(conclusionClaimCodeTb);
        sb.append(", conclusionUnCompensateCause=").append(conclusionUnCompensateCause);
        sb.append(", conclusionPolicyCode=").append(conclusionPolicyCode);
        sb.append(", conclusionInsuType=").append(conclusionInsuType);
        sb.append(", conclusionDuty=").append(conclusionDuty);
        sb.append(", conclusionDutySubcode=").append(conclusionDutySubcode);
        sb.append(", conclusionClaimConclusion=").append(conclusionClaimConclusion);
        sb.append(", conclusionSubcode=").append(conclusionSubcode);
        sb.append(", conclusionCompensateAmt=").append(conclusionCompensateAmt);
        sb.append(", conclusionCreatetime=").append(conclusionCreatetime);
        sb.append(", conclusionUpdatetime=").append(conclusionUpdatetime);
        sb.append(", conclusionStatus=").append(conclusionStatus);
        sb.append(", conclusionMemo=").append(conclusionMemo);
        sb.append(", claimheadnumber=").append(claimheadnumber);
        sb.append(", conclusionDutyName=").append(conclusionDutyName);
        sb.append(", conclusionBatchId=").append(conclusionBatchId);
        sb.append(", conclusionIsonline=").append(conclusionIsonline);
        sb.append(", conclusionPayeeCerttype=").append(conclusionPayeeCerttype);
        sb.append(", conclusionClaimConclusionCode=").append(conclusionClaimConclusionCode);
        sb.append(", conclusionPersonCerttype=").append(conclusionPersonCerttype);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}
