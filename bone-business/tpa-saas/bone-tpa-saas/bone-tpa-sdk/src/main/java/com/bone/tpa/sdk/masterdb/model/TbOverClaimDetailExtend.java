package com.bone.tpa.sdk.masterdb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tb_over_claim_detail_extend")
@Schema(description="理赔结案发票扩展表")
public class TbOverClaimDetailExtend implements Serializable {

    @Schema(description = "主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "赔案号")
    @TableField("claim_code")
    private String claimCode;

    @Schema(description = "发票号")
    @TableField("bill_code")
    private String billCode;

    @Schema(description = "发票id")
    @TableField("bill_id")
    private Long billId;

    @Schema(description = "责任id")
    @TableField("conclusion_id")
    private Long conclusionId;

    @Schema(description = "是否分段 1是 0否")
    @TableField("segment_is")
    private Long segmentIs;

    @Schema(description = "是否拆分 1是 0否")
    @TableField("split_is")
    private Long splitIs;

    @Schema(description = "审核结论 1-拒付  2-给付  3-协议给付  4-通融给付")
    @TableField("audit_conclusion")
    private Long auditConclusion;

    @Schema(description = "发票备注")
    @TableField("bill_remark")
    private String billRemark;

    @Schema(description = "票据重复说明")
    @TableField("bill_repetition_explain")
    private String billRepetitionExplain;

    @Schema(description = "险种代码")
    @TableField("insu_type")
    private String insuType;

    @Schema(description = "责任代码")
    @TableField("duty_code")
    private String dutyCode;

    @Schema(description = "责任名称")
    @TableField("duty_name")
    private String dutyName;

    @Schema(description = "责任子码")
    @TableField("duty_subcode")
    private String dutySubcode;

    @Schema(description = "发票责任层赔付金额")
    @TableField("bill_duty_compensate_amt")
    private BigDecimal billDutyCompensateAmt;

    @Schema(description = "结论明细")
    @TableField("bill_memo")
    private String billMemo;

    @Schema(description = "创建时间")
    @TableField("create_time")
    private Date createtime;

    @Schema(description = "修改时间")
    @TableField("update_time")
    private Date updateTime;

    @Schema(description = "1正常  -1作废")
    @TableField("bill_status")
    private Long billStatus;


    @Schema(description = "发票类型：1-发票就诊日的期不在保单的有效期内; " +
            "2-发票的姓名与出险人不一致; 3-就诊的医院不符合保险医院规则; " +
            "4-诊断不符合保险的责免规则; 5-发票计算合理金额为0; 6-未达免赔的导致合理金额为0; " +
            "7-未达免赔的导致合理金额为0; 8-强制使用医保的责任，未使用医保卡进行就诊; " +
            "9-无剩余保额; 10-发票类就诊类型不符合责任定义; 11-责任不包含特需区域就诊; " +
            "12-不满足等待期';")
    @TableField(" detail_type")
    private Long  detailType;

    @TableField(exist = false)
    private String tpaDutyId;

    @TableField(exist = false)
    private String treatmenttype;

    @Schema(description = "伤病标志")
    @TableField("injury_disease_flag")
    private String injuryDiseaseFlag;

    @Schema(description = "发票类型 1门诊 2住院 3购药")
    @TableField("invoice_type")
    private Integer invoiceType;


    /**
     * 医院性质
     *
     * @mbg.generated
     */
    @Schema(description = "医院类型(医院性质)")
    private String hospitalType;

    @Schema(description = "关联的影像件地址 以,分割")
    private String detailImgPath;

    @Schema(description = "发票姓名")
    @TableField("bill_name")
    private String billName;

    @Schema(description = "发票责任内金额")
    private BigDecimal billReasonableAmount;

    @Schema(description = "发票责任下申请金额")
    private BigDecimal billApplyAmount;

    @Schema(description = "社保范围内自付赔付金额")
    @TableField("medical_self_pay_amount")
    private BigDecimal medicalSelfPayAmount;
    @Schema(description = "部分自费赔付金额")
    @TableField("part_self_pay_amount")
    private BigDecimal partSelfPayAmount;
    @Schema(description = "全部自费赔付金额")
    @TableField("all_self_pay_amount")
    private BigDecimal allSelfPayAmount;
    @Schema(description = "个人医保账户支付金额")
    @TableField("medical_pay_amount")
    private BigDecimal medicalPayAmount;

    @Schema(description = "个人现金支付金额")
    @TableField("cash_pay_amount")
    private BigDecimal cashPayAmount;

    @Schema(description = "费用代码")
    @TableField("item_kind_code")
    private String itemKindCode;

    @Schema(description = "费用名称")
    @TableField("item_kind_name")
    private String itemKindName;

    @Schema(description = "责任赔付比例")
    @TableField("duty_compensate_ratio")
    private BigDecimal dutyCompensateRatio;

    @Schema(description = "第三方费用类型编号")
    @TableField("third_fee_code")
    private String thirdFeeCode;


    @Schema(description = "责任赔付比例")
    @TableField("bill_duty_compensate_all_amt")
    private BigDecimal billDutyCompensateAllAmt;


    @Schema(description = "意外细节代码")
    @TableField("accident_detail_code")
    private String accidentDetailCode;
    @Schema(description = "理赔类型代码")
    @TableField("claim_type_code")
    private String claimTypeCode;
    @Schema(description = "手术项目代码")
    @TableField("opertion_code")
    private String opertionCode;

    @Schema(description = "手术项目名称")
    @TableField("opertion_name")
    private String opertionName;
    @Schema(description = "津贴等级")
    @TableField("deformity_kind")
    private String deformityKind;
    @Schema(description = "理赔给付类型")
    @TableField("claim_duty_kind")
    private String claimDutyKind;
    @Schema(description = "有效保额")
    @TableField("effective_insure_amount")
    private BigDecimal effectiveInsureAmount;
    @Schema(description = "不合理金额")
    @TableField("unreasonable_amount")
    private BigDecimal unreasonableAmount;

    @Schema(description = "免赔额")
    @TableField("abtm_amt")
    private BigDecimal abtmAmt;
//    @Schema(description = "核算赔付金额")
//    @TableField("stand_pay_amount")
//    private BigDecimal standPayAmount;
//    @Schema(description = "累计免赔额")
//    @TableField("sum_out_duty_amount")
//    private BigDecimal sumOutDutyAmount;
    @Schema(description = "收据类型")
    @TableField("fee_type")
    private String feeType;
    @Schema(description = "发票津贴天数")
    @TableField("invoice_allowance_days")
    private BigDecimal invoiceAllowanceDays;

    @Schema(description = "责任使用免赔天数")
    @TableField("responsibility_deductible_days")
    private BigDecimal responsibilityDeductibleDays;

    @Schema(description = "日固定赔付金额")
    @TableField("daily_compensation_amount")
    private BigDecimal dailyCompensationAmount;

    @Schema(description = "tpa内部责任Id")
    @TableField("duty_guid")
    private String dutyGuid;

    @Schema(description = "是否电子发票 0否；1是")
    @TableField("is_electronic_invoice")
    private Integer isElectronicInvoice;

    @Schema(description = "是否北京发票 0-否；1-是")
    @TableField("is_bj_invoice")
    private Integer isBjInvoice;

    @Schema(description = "票据代码/发票代码")
    @TableField("invoice_code")
    private String invoiceCode;

    @Schema(description = "开票日期")
    @TableField("invoice_date")
    private Date invoiceDate;

    @Schema(description = "发票校验码")
    @TableField("invoice_verification_code")
    private String invoiceVerificationCode;
    @Schema(description = "1 普通门诊、2 普通住院、3 药房购药、4 门诊慢性病、 5 门诊重特大疾病、6 重大疾病住院")
    @TableField("invoice_visit_type ")
    private Integer invoiceVisitType;

    @Schema(description = "是否验真发票 1-是 0-否")
    @TableField("is_verify_valid")
    private Byte isVerifyValid;

    @Schema(description = "其它医院名称")
    @TableField("other_hospital_name")
    private String otherHospitalName;

    @Schema(description = "是否既往疾病")
    @TableField("is_history_injury")
    private String isHistoryInjury;


    @Schema(description = "起付线以下金额")
    public BigDecimal underMinimumAmt;

    @Schema(description = "起付线以上至封顶线金额")
    public BigDecimal upperMinimumAmt;

    @Schema(description = "大额补充以上至封顶线金额")
    public BigDecimal compLimitAmt;

    @Schema(description = "大额补充封顶线以上金额")
    public BigDecimal upperCompLimitAmt;

    @Schema(description = "起付线以下赔付金额")
    public BigDecimal underMinimumPayAmt;

    @Schema(description = "起付线以上至封顶线赔付金额")
    public BigDecimal upperMinimumPayAmt;

    @Schema(description = "大额补充以上至封顶线赔付金额")
    public BigDecimal compLimitPayAmt;

    @Schema(description = "大额补充封顶线以上赔付金额")
    public BigDecimal upperCompLimitPayAmt;

    @Schema(description = "理算公式")
    public String arithmeticFormula;
    /**
     * 财务凭证类型代码
     */
    private String financeVoucherTypeCode;

    /**
     * 财务凭证名称
     */
    private String financeVoucherName;
    @Schema(description = "账单属性")
    @TableField("billing_attributes")
    public String billingAttributes;

    @Schema(description = "是否门特")
    @TableField("is_special_clinic")
    public Integer isSpecialClinic;

    @Schema(description = "是否生育")
    @TableField("is_maternity_related")
    public Integer isMaternityRelated;

    @Schema(description = "科室")
    @TableField("medical_department")
    public String medicalDepartment;

    /**
     * 是否重大疾病
     */
    private Integer isCriticalIllness;

    /**
     * 是否慢性病
     */
    private Integer isChronicDisease;

    /**
     * 门诊号
     */
    private String outpatientNo;

    /**
     * 病历号
     */
    private String medicalRecordNo;

    /**
     * 住院号
     */
    private String inpatientNo;

    /**
     * 住院科室
     */
    private String inpatientDepartment;

    /**
     * 预交金额
     */
    private BigDecimal prepaidAmt;

    /**
     * 退款金额
     */
    private BigDecimal refundAmt;

    /**
     * 重大疾病保险金额
     */
    private BigDecimal criticalIllnessInsAmt;

    /**
     * 医疗救助金额
     */
    private BigDecimal medicalAssistAmt;

    /**
     * 公务员医疗补助金额
     */
    private BigDecimal civilServantMedicalSubsidyAmt;

    /**
     * 大病补充金额
     */
    private BigDecimal majorSupplementAmt;

    /**
     * 其他金额
     */
    private BigDecimal otherAmt;

    /**
     * 成员疾病身故金
     */
    private BigDecimal illnessDeathBenefitAmt;

    /**
     * 个人自付
     */
    private BigDecimal selfPaidAmt;

    /**
     * 个人自费
     */
    private BigDecimal selfFinanceAmt;

    /**
     * 是否已验证 1-是；0-否
     */
    private Integer isPharmacyLicenseVerified;
    @Schema(description = "医院服务部")
    @TableField("hospital_service_division")
    public String hospitalServiceDivision;

    @Schema(description = "出险原因")
    @TableField("out_insure_reason")
    public String outInsureReason;
}
