package com.bone.tpa.sdk.masterdb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 理赔明细数据
 */
@Data
public class TbOverClaimDetail implements Serializable {

    @TableId(value="claimd_id",type = IdType.AUTO)
    private Long claimdId;

    /**
     * 理算ID
     *
     * @mbg.generated
     */
    @Schema(description = "理算ID")
    private String adjustmentguid;

    /**
     * 批次号
     *
     * @mbg.generated
     */
    @Schema(description = "批次号")
    private String claimdBatchCode;

    /**
     * 主单号
     *
     * @mbg.generated
     */
    @Schema(description = "主单号")
    private String claimdGroupPolicy;

    /**
     * 赔案号
     *
     * @mbg.generated
     */
    @Schema(description = "赔案号")
    private String claimdClaimCode;

    /**
     * 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     *
     * @mbg.generated
     */
    @Schema(description = "专门用于给太保发送用的，其实就是“PK0000000'+caseCode")
    private String claimdClaimCodeTb;

    /**
     * 投保序号
     *
     * @mbg.generated
     */
    @Schema(description = "投保序号")
    private String claimdCorpCode;

    /**
     * 被保险人身份证号
     *
     * @mbg.generated
     */
    @Schema(description = "被保险人身份证号")
    private String claimdPersonCertid;

    /**
     * 发票号
     *
     * @mbg.generated
     */
    @Schema(description = "发票号")
    private String claimdBillCode;

    /**
     * 就诊日期
     *
     * @mbg.generated
     */
    @Schema(description = "就诊日期")
    private String claimdVisitDate;

    /**
     * 医院代码
     *
     * @mbg.generated
     */
    @Schema(description = "医院代码")
    private String claimdHospitalCode;

    /**
     * 医院名称
     *
     * @mbg.generated
     */
    @Schema(description = "医院名称")
    private String claimdHospitalName;

    /**
     * 就诊责任
     *
     * @mbg.generated
     */
    @Schema(description = "就诊责任")
    private String claimdVisitDuty;

    /**
     * 疾病ID
     *
     * @mbg.generated
     */
    @Schema(description = "疾病ID")
    private String claimdDiseaseId;

    /**
     * 疾病名称
     *
     * @mbg.generated
     */
    @Schema(description = "疾病名称")
    private String claimdDiseaseName;

    /**
     * 自费金额(丙类)
     *
     * @mbg.generated
     */
    @Schema(description = "自费金额(丙类)")
    private BigDecimal claimdSelfPayAmt;

    /**
     * 分类支付(乙类)
     *
     * @mbg.generated
     */
    @Schema(description = "分类支付(乙类)")
    private BigDecimal claimdClassifyPay;

    /**
     * 护理费
     *
     * @mbg.generated
     */
    @Schema(description = "护理费")
    private String claimdNurseAmt;

    /**
     * 自负支付
     *
     * @mbg.generated
     */
    @Schema(description = "自负支付")
    private BigDecimal claimdSelfCashAmt;

    /**
     * 账户支付
     *
     * @mbg.generated
     */
    @Schema(description = "账户支付")
    private BigDecimal claimdAccountPayAmt;

    /**
     * 统筹支付
     *
     * @mbg.generated
     */
    @Schema(description = "统筹支付")
    private BigDecimal claimdPlanPayAmt;

    /**
     * 附加支付
     *
     * @mbg.generated
     */
    @Schema(description = "附加支付")
    private BigDecimal claimdAppendPayAmt;

    /**
     * 第三方支付
     *
     * @mbg.generated
     */
    @Schema(description = "第三方支付")
    private BigDecimal claimdThirdpartyPayAmt;

    /**
     * 检查费
     *
     * @mbg.generated
     */
    @Schema(description = "检查费")
    private BigDecimal claimdInspectAmt;

    /**
     * 理疗费
     *
     * @mbg.generated
     */
    @Schema(description = "理疗费")
    private BigDecimal claimdPhysiotherapyAmt;

    /**
     * 药费
     *
     * @mbg.generated
     */
    @Schema(description = "药费")
    private BigDecimal claimdMedicineAmt;

    /**
     * 洗牙费
     *
     * @mbg.generated
     */
    @Schema(description = "洗牙费")
    private BigDecimal claimdCleanToothAmt;

    /**
     * 出院日期
     *
     * @mbg.generated
     */
    @Schema(description = "出院日期")
    private String claimdOutDate;

    /**
     * 住院天数
     *
     * @mbg.generated
     */
    @Schema(description = "住院天数")
    private BigDecimal claimdHospitalDays;

    /**
     * 调整金额
     *
     * @mbg.generated
     */
    @Schema(description = "调整金额")
    private BigDecimal claimdChangeAmt;

    /**
     * 调整天数
     *
     * @mbg.generated
     */
    @Schema(description = "调整天数")
    private Integer claimdChangeDays;

    /**
     * 调整原因
     *
     * @mbg.generated
     */
    @Schema(description = "调整原因")
    private String claimdChangeCause;

    /**
     * 免赔天数
     *
     * @mbg.generated
     */
    @Schema(description = "免赔天数")
    private Integer claimdAbtmDays;

    /**
     * 赔付天数
     *
     * @mbg.generated
     */
    @Schema(description = "赔付天数")
    private Integer claimdCompensateDays;

    /**
     * 申请金额
     *
     * @mbg.generated
     */
    @Schema(description = "申请金额")
    private BigDecimal claimdApplyAmt;

    /**
     * 免赔额
     *
     * @mbg.generated
     */
    @Schema(description = "免赔额")
    private BigDecimal claimdAbtmAmt;

    /**
     * 赔付金额
     *
     * @mbg.generated
     */
    @Schema(description = "赔付金额")
    private BigDecimal claimdCompensateAmt;

    /**
     * 发票属性
     *
     * @mbg.generated
     */
    @Schema(description = "发票属性")
    private String claimdBillProperty;

    /**
     * 拒赔原因
     *
     * @mbg.generated
     */
    @Schema(description = "拒赔原因")
    private String claimdUnCompensateCause;

    /**
     * 录入日期
     *
     * @mbg.generated
     */
    @Schema(description = "录入日期")
    private String claimdEnterDate;

    /**
     * 复核日期
     *
     * @mbg.generated
     */
    @Schema(description = "复核日期")
    private String claimdRecheckDate;

    /**
     * 分类支付是否参加理赔
     *
     * @mbg.generated
     */
    @Schema(description = "分类支付是否参加理赔")
    private Integer claimdClassifyPayIsCompensate;

    /**
     * 自费是否参加理赔
     *
     * @mbg.generated
     */
    @Schema(description = "自费是否参加理赔")
    private Integer claimdSelfPayIsCompensate;

    /**
     * 个人承担费用”也就是：现金+账户
     *
     * @mbg.generated
     */
    @Schema(description = "个人承担费用”也就是：现金+账户")
    private BigDecimal claimdSelfAssumeAmt;

    /**
     * 责任ID
     *
     * @mbg.generated
     */
    @Schema(description = "责任ID")
    private String claimdDutyId;

    /**
     * 自费金额old
     *
     * @mbg.generated
     */
    @Schema(description = "自费金额old")
    private String claimdSelfPayOid;

    /**
     * 医院ID
     *
     * @mbg.generated
     */
    @Schema(description = "医院ID")
    private String claimdHospitalId;

    /**
     * 疾病代码
     *
     * @mbg.generated
     */
    @Schema(description = "疾病代码")
    private String claimdDiseaseCode;

    /**
     * 自费是否参加理赔
     *
     * @mbg.generated
     */
    @Schema(description = "自费是否参加理赔")
    private Date claimdCreatetime;

    private Date claimdUpdatetime;

    /**
     * 状态，默认1，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，-1-作废
     *
     * @mbg.generated
     */
    @Schema(description = "状态，默认1，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，-1-作废")
    private Integer claimdStatus;

    /**
     * 保存生成文件的有关信息
     *
     * @mbg.generated
     */
    @Schema(description = "保存生成文件的有关信息")
    private String claimdMemo;

    /**
     * 不合理金额
     *
     * @mbg.generated
     */
    @Schema(description = "不合理金额")
    private BigDecimal unreasonableamount;

    /**
     * 合理金额
     *
     * @mbg.generated
     */
    @Schema(description = "合理金额")
    private BigDecimal reasonableamount;

    /**
     * 医院级别【0一级,1二级,2三级,3未评级,4区级,5县级,6市级,7县区级,8镇级】
     *
     * @mbg.generated
     */
    @Schema(description = "医院级别【0一级,1二级,2三级,3未评级,4区级,5县级,6市级,7县区级,8镇级】")
    private String hospitalgrade;

    /**
     * 医院等级【0甲等,1乙等,2丙等,3未评级,4合格,5特等】
     *
     * @mbg.generated
     */
    @Schema(description = "医院等级【0甲等,1乙等,2丙等,3未评级,4合格,5特等】")
    private String hospitallevel;

    /**
     * 定点医院，是否是医保，是或否
     *
     * @mbg.generated
     */
    @Schema(description = "定点医院，是否是医保，是或否")
    private String hospitalifyb;

    /**
     * 医院属性，公立/私立
     *
     * @mbg.generated
     */
    @Schema(description = "医院属性，公立/私立")
    private String hospitalproperty;

    /**
     * 账单金额
     *
     * @mbg.generated
     */
    @Schema(description = "账单金额")
    private String formulaText;

    /**
     * 账单金额对应的具体的值
     *
     * @mbg.generated
     */
    @Schema(description = "账单金额对应的具体的值")
    private String formulaValue;

    /**
     * 先期给付(统筹支付字段)
     *
     * @mbg.generated
     */
    @Schema(description = "先期给付(统筹支付字段)")
    private String earlycompensateamt;

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
    private String claimdDutyName;

    /**
     * 就诊类型，2门诊、1住院
     *
     * @mbg.generated
     */
    @Schema(description = "就诊类型，2门诊、1住院")
    private Integer claimdVisitType;

    /**
     * 有无医保，1无，2有
     *
     * @mbg.generated
     */
    @Schema(description = "有无医保，1无，2有")
    private Integer claimdHaveYb;

    /**
     * 拒赔原因代码，太保枚举
     *
     * @mbg.generated
     */
    @Schema(description = "拒赔原因代码，太保枚举")
    private String claimdUnCompensateCauseCode;

    /**
     * 费用扣除原因代码
     *
     * @mbg.generated
     */
    @Schema(description = "费用扣除原因代码")
    private String claimdDeductCauseCode;

    /**
     * 费用扣除原因
     *
     * @mbg.generated
     */
    @Schema(description = "费用扣除原因")
    private String claimdDeductCause;

    /**
     * 手术代码
     *
     * @mbg.generated
     */
    @Schema(description = "手术代码")
    private String claimdOperationCode;

    /**
     * 入院日期
     *
     * @mbg.generated
     */
    @Schema(description = "入院日期")
    private String claimdInHospitalDate;

    /**
     * 账单类型
     *
     * @mbg.generated
     */
    @Schema(description = "账单类型")
    private Integer claimdBilltype;

    /**
     * 医院所在省
     *
     * @mbg.generated
     */
    @Schema(description = "医院所在省")
    private String claimdHospitalprovince;

    /**
     * 医院所在市
     *
     * @mbg.generated
     */
    @Schema(description = "医院所在市")
    private String claimdHospitalcity;

    /**
     * 医院所在区
     *
     * @mbg.generated
     */
    @Schema(description = "医院所在区")
    private String claimdHospitalarea;

    /**
     * 现金支付
     *
     * @mbg.generated
     */
    @Schema(description = "现金支付")
    private BigDecimal claimdCashPay;

    /**
     * 新农合报销金额
     *
     * @mbg.generated
     */
    @Schema(description = "新农合报销金额")
    private BigDecimal claimdSummitAmt;

    /**
     * 大额(互助资金)（门诊/住院）本次支付
     *
     * @mbg.generated
     */
    @Schema(description = "大额(互助资金)（门诊/住院）本次支付")
    private BigDecimal claimdMutualPay;

    /**
     * 出具单位
     *
     * @mbg.generated
     */
    @Schema(description = "出具单位")
    private String claimdIssuedUnit;

    /**
     * 剩余发生金额
     *
     * @mbg.generated
     */
    @Schema(description = "剩余发生金额")
    private BigDecimal claimdSurplusPay;

    /**
     * 给第三方的赔付比率
     *
     * @mbg.generated
     */
    @Schema(description = "给第三方的赔付比率")
    private BigDecimal claimdThirdpartyRatio;

    @TableField(exist = false)
    private List<TbOverClaimDetailExtend> detailExtends;

    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database table tb_over_claim_detail
     *
     * @mbg.generated
     */
    private static final long serialVersionUID = 1L;

    /**
     *
     * @return claimd_id
     */
    public Long getClaimdId() {
        return claimdId;
    }

    /**
     *
     * @param claimdId
     */
    public void setClaimdId(Long claimdId) {
        this.claimdId = claimdId;
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
     * 批次号
     * @return claimd_batch_code 批次号
     */
    public String getClaimdBatchCode() {
        return claimdBatchCode;
    }

    /**
     * 批次号
     * @param claimdBatchCode 批次号
     */
    public void setClaimdBatchCode(String claimdBatchCode) {
        this.claimdBatchCode = claimdBatchCode;
    }

    /**
     * 主单号
     * @return claimd_group_policy 主单号
     */
    public String getClaimdGroupPolicy() {
        return claimdGroupPolicy;
    }

    /**
     * 主单号
     * @param claimdGroupPolicy 主单号
     */
    public void setClaimdGroupPolicy(String claimdGroupPolicy) {
        this.claimdGroupPolicy = claimdGroupPolicy;
    }

    /**
     * 赔案号
     * @return claimd_claim_code 赔案号
     */
    public String getClaimdClaimCode() {
        return claimdClaimCode;
    }

    /**
     * 赔案号
     * @param claimdClaimCode 赔案号
     */
    public void setClaimdClaimCode(String claimdClaimCode) {
        this.claimdClaimCode = claimdClaimCode;
    }

    /**
     * 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     * @return claimd_claim_code_tb 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     */
    public String getClaimdClaimCodeTb() {
        return claimdClaimCodeTb;
    }

    /**
     * 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     * @param claimdClaimCodeTb 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     */
    public void setClaimdClaimCodeTb(String claimdClaimCodeTb) {
        this.claimdClaimCodeTb = claimdClaimCodeTb;
    }

    /**
     * 投保序号
     * @return claimd_corp_code 投保序号
     */
    public String getClaimdCorpCode() {
        return claimdCorpCode;
    }

    /**
     * 投保序号
     * @param claimdCorpCode 投保序号
     */
    public void setClaimdCorpCode(String claimdCorpCode) {
        this.claimdCorpCode = claimdCorpCode;
    }

    /**
     * 被保险人身份证号
     * @return claimd_person_certId 被保险人身份证号
     */
    public String getClaimdPersonCertid() {
        return claimdPersonCertid;
    }

    /**
     * 被保险人身份证号
     * @param claimdPersonCertid 被保险人身份证号
     */
    public void setClaimdPersonCertid(String claimdPersonCertid) {
        this.claimdPersonCertid = claimdPersonCertid;
    }

    /**
     * 发票号
     * @return claimd_bill_code 发票号
     */
    public String getClaimdBillCode() {
        return claimdBillCode;
    }

    /**
     * 发票号
     * @param claimdBillCode 发票号
     */
    public void setClaimdBillCode(String claimdBillCode) {
        this.claimdBillCode = claimdBillCode;
    }

    /**
     * 就诊日期
     * @return claimd_visit_date 就诊日期
     */
    public String getClaimdVisitDate() {
        return claimdVisitDate;
    }

    /**
     * 就诊日期
     * @param claimdVisitDate 就诊日期
     */
    public void setClaimdVisitDate(String claimdVisitDate) {
        this.claimdVisitDate = claimdVisitDate;
    }

    /**
     * 医院代码
     * @return claimd_hospital_code 医院代码
     */
    public String getClaimdHospitalCode() {
        return claimdHospitalCode;
    }

    /**
     * 医院代码
     * @param claimdHospitalCode 医院代码
     */
    public void setClaimdHospitalCode(String claimdHospitalCode) {
        this.claimdHospitalCode = claimdHospitalCode;
    }

    /**
     * 医院名称
     * @return claimd_hospital_name 医院名称
     */
    public String getClaimdHospitalName() {
        return claimdHospitalName;
    }

    /**
     * 医院名称
     * @param claimdHospitalName 医院名称
     */
    public void setClaimdHospitalName(String claimdHospitalName) {
        this.claimdHospitalName = claimdHospitalName;
    }

    /**
     * 就诊责任
     * @return claimd_visit_duty 就诊责任
     */
    public String getClaimdVisitDuty() {
        return claimdVisitDuty;
    }

    /**
     * 就诊责任
     * @param claimdVisitDuty 就诊责任
     */
    public void setClaimdVisitDuty(String claimdVisitDuty) {
        this.claimdVisitDuty = claimdVisitDuty;
    }

    /**
     * 疾病ID
     * @return claimd_disease_id 疾病ID
     */
    public String getClaimdDiseaseId() {
        return claimdDiseaseId;
    }

    /**
     * 疾病ID
     * @param claimdDiseaseId 疾病ID
     */
    public void setClaimdDiseaseId(String claimdDiseaseId) {
        this.claimdDiseaseId = claimdDiseaseId;
    }

    /**
     * 疾病名称
     * @return claimd_disease_name 疾病名称
     */
    public String getClaimdDiseaseName() {
        return claimdDiseaseName;
    }

    /**
     * 疾病名称
     * @param claimdDiseaseName 疾病名称
     */
    public void setClaimdDiseaseName(String claimdDiseaseName) {
        this.claimdDiseaseName = claimdDiseaseName;
    }

    /**
     * 自费金额(丙类)
     * @return claimd_self_pay_amt 自费金额(丙类)
     */
    public BigDecimal getClaimdSelfPayAmt() {
        return claimdSelfPayAmt;
    }

    /**
     * 自费金额(丙类)
     * @param claimdSelfPayAmt 自费金额(丙类)
     */
    public void setClaimdSelfPayAmt(BigDecimal claimdSelfPayAmt) {
        this.claimdSelfPayAmt = claimdSelfPayAmt;
    }

    /**
     * 分类支付(乙类)
     * @return claimd_classify_pay 分类支付(乙类)
     */
    public BigDecimal getClaimdClassifyPay() {
        return claimdClassifyPay;
    }

    /**
     * 分类支付(乙类)
     * @param claimdClassifyPay 分类支付(乙类)
     */
    public void setClaimdClassifyPay(BigDecimal claimdClassifyPay) {
        this.claimdClassifyPay = claimdClassifyPay;
    }

    /**
     * 护理费
     * @return claimd_nurse_amt 护理费
     */
    public String getClaimdNurseAmt() {
        return claimdNurseAmt;
    }

    /**
     * 护理费
     * @param claimdNurseAmt 护理费
     */
    public void setClaimdNurseAmt(String claimdNurseAmt) {
        this.claimdNurseAmt = claimdNurseAmt;
    }

    /**
     * 自负支付
     * @return claimd_self_cash_amt 自负支付
     */
    public BigDecimal getClaimdSelfCashAmt() {
        return claimdSelfCashAmt;
    }

    /**
     * 自负支付
     * @param claimdSelfCashAmt 自负支付
     */
    public void setClaimdSelfCashAmt(BigDecimal claimdSelfCashAmt) {
        this.claimdSelfCashAmt = claimdSelfCashAmt;
    }

    /**
     * 账户支付
     * @return claimd_account_pay_amt 账户支付
     */
    public BigDecimal getClaimdAccountPayAmt() {
        return claimdAccountPayAmt;
    }

    /**
     * 账户支付
     * @param claimdAccountPayAmt 账户支付
     */
    public void setClaimdAccountPayAmt(BigDecimal claimdAccountPayAmt) {
        this.claimdAccountPayAmt = claimdAccountPayAmt;
    }

    /**
     * 统筹支付
     * @return claimd_plan_pay_amt 统筹支付
     */
    public BigDecimal getClaimdPlanPayAmt() {
        return claimdPlanPayAmt;
    }

    /**
     * 统筹支付
     * @param claimdPlanPayAmt 统筹支付
     */
    public void setClaimdPlanPayAmt(BigDecimal claimdPlanPayAmt) {
        this.claimdPlanPayAmt = claimdPlanPayAmt;
    }

    /**
     * 附加支付
     * @return claimd_append_pay_amt 附加支付
     */
    public BigDecimal getClaimdAppendPayAmt() {
        return claimdAppendPayAmt;
    }

    /**
     * 附加支付
     * @param claimdAppendPayAmt 附加支付
     */
    public void setClaimdAppendPayAmt(BigDecimal claimdAppendPayAmt) {
        this.claimdAppendPayAmt = claimdAppendPayAmt;
    }

    /**
     * 第三方支付
     * @return claimd_thirdParty_pay_amt 第三方支付
     */
    public BigDecimal getClaimdThirdpartyPayAmt() {
        return claimdThirdpartyPayAmt;
    }

    /**
     * 第三方支付
     * @param claimdThirdpartyPayAmt 第三方支付
     */
    public void setClaimdThirdpartyPayAmt(BigDecimal claimdThirdpartyPayAmt) {
        this.claimdThirdpartyPayAmt = claimdThirdpartyPayAmt;
    }

    /**
     * 检查费
     * @return claimd_inspect_amt 检查费
     */
    public BigDecimal getClaimdInspectAmt() {
        return claimdInspectAmt;
    }

    /**
     * 检查费
     * @param claimdInspectAmt 检查费
     */
    public void setClaimdInspectAmt(BigDecimal claimdInspectAmt) {
        this.claimdInspectAmt = claimdInspectAmt;
    }

    /**
     * 理疗费
     * @return claimd_physiotherapy_amt 理疗费
     */
    public BigDecimal getClaimdPhysiotherapyAmt() {
        return claimdPhysiotherapyAmt;
    }

    /**
     * 理疗费
     * @param claimdPhysiotherapyAmt 理疗费
     */
    public void setClaimdPhysiotherapyAmt(BigDecimal claimdPhysiotherapyAmt) {
        this.claimdPhysiotherapyAmt = claimdPhysiotherapyAmt;
    }

    /**
     * 药费
     * @return claimd_medicine_amt 药费
     */
    public BigDecimal getClaimdMedicineAmt() {
        return claimdMedicineAmt;
    }

    /**
     * 药费
     * @param claimdMedicineAmt 药费
     */
    public void setClaimdMedicineAmt(BigDecimal claimdMedicineAmt) {
        this.claimdMedicineAmt = claimdMedicineAmt;
    }

    /**
     * 洗牙费
     * @return claimd_clean_tooth_amt 洗牙费
     */
    public BigDecimal getClaimdCleanToothAmt() {
        return claimdCleanToothAmt;
    }

    /**
     * 洗牙费
     * @param claimdCleanToothAmt 洗牙费
     */
    public void setClaimdCleanToothAmt(BigDecimal claimdCleanToothAmt) {
        this.claimdCleanToothAmt = claimdCleanToothAmt;
    }

    /**
     * 出院日期
     * @return claimd_out_date 出院日期
     */
    public String getClaimdOutDate() {
        return claimdOutDate;
    }

    /**
     * 出院日期
     * @param claimdOutDate 出院日期
     */
    public void setClaimdOutDate(String claimdOutDate) {
        this.claimdOutDate = claimdOutDate;
    }

    /**
     * 住院天数
     * @return claimd_hospital_days 住院天数
     */
    public BigDecimal getClaimdHospitalDays() {
        return claimdHospitalDays;
    }

    /**
     * 住院天数
     * @param claimdHospitalDays 住院天数
     */
    public void setClaimdHospitalDays(BigDecimal claimdHospitalDays) {
        this.claimdHospitalDays = claimdHospitalDays;
    }

    /**
     * 调整金额
     * @return claimd_change_amt 调整金额
     */
    public BigDecimal getClaimdChangeAmt() {
        return claimdChangeAmt;
    }

    /**
     * 调整金额
     * @param claimdChangeAmt 调整金额
     */
    public void setClaimdChangeAmt(BigDecimal claimdChangeAmt) {
        this.claimdChangeAmt = claimdChangeAmt;
    }

    /**
     * 调整天数
     * @return claimd_change_days 调整天数
     */
    public Integer getClaimdChangeDays() {
        return claimdChangeDays;
    }

    /**
     * 调整天数
     * @param claimdChangeDays 调整天数
     */
    public void setClaimdChangeDays(Integer claimdChangeDays) {
        this.claimdChangeDays = claimdChangeDays;
    }

    /**
     * 调整原因
     * @return claimd_change_cause 调整原因
     */
    public String getClaimdChangeCause() {
        return claimdChangeCause;
    }

    /**
     * 调整原因
     * @param claimdChangeCause 调整原因
     */
    public void setClaimdChangeCause(String claimdChangeCause) {
        this.claimdChangeCause = claimdChangeCause;
    }

    /**
     * 免赔天数
     * @return claimd_abtm_days 免赔天数
     */
    public Integer getClaimdAbtmDays() {
        return claimdAbtmDays;
    }

    /**
     * 免赔天数
     * @param claimdAbtmDays 免赔天数
     */
    public void setClaimdAbtmDays(Integer claimdAbtmDays) {
        this.claimdAbtmDays = claimdAbtmDays;
    }

    /**
     * 赔付天数
     * @return claimd_compensate_days 赔付天数
     */
    public Integer getClaimdCompensateDays() {
        return claimdCompensateDays;
    }

    /**
     * 赔付天数
     * @param claimdCompensateDays 赔付天数
     */
    public void setClaimdCompensateDays(Integer claimdCompensateDays) {
        this.claimdCompensateDays = claimdCompensateDays;
    }

    /**
     * 申请金额
     * @return claimd_apply_amt 申请金额
     */
    public BigDecimal getClaimdApplyAmt() {
        return claimdApplyAmt;
    }

    /**
     * 申请金额
     * @param claimdApplyAmt 申请金额
     */
    public void setClaimdApplyAmt(BigDecimal claimdApplyAmt) {
        this.claimdApplyAmt = claimdApplyAmt;
    }

    /**
     * 免赔额
     * @return claimd_abtm_amt 免赔额
     */
    public BigDecimal getClaimdAbtmAmt() {
        return claimdAbtmAmt;
    }

    /**
     * 免赔额
     * @param claimdAbtmAmt 免赔额
     */
    public void setClaimdAbtmAmt(BigDecimal claimdAbtmAmt) {
        this.claimdAbtmAmt = claimdAbtmAmt;
    }

    /**
     * 赔付金额
     * @return claimd_compensate_amt 赔付金额
     */
    public BigDecimal getClaimdCompensateAmt() {
        return claimdCompensateAmt;
    }

    /**
     * 赔付金额
     * @param claimdCompensateAmt 赔付金额
     */
    public void setClaimdCompensateAmt(BigDecimal claimdCompensateAmt) {
        this.claimdCompensateAmt = claimdCompensateAmt;
    }

    /**
     * 发票属性
     * @return claimd_bill_property 发票属性
     */
    public String getClaimdBillProperty() {
        return claimdBillProperty;
    }

    /**
     * 发票属性
     * @param claimdBillProperty 发票属性
     */
    public void setClaimdBillProperty(String claimdBillProperty) {
        this.claimdBillProperty = claimdBillProperty;
    }

    /**
     * 拒赔原因
     * @return claimd_un_compensate_cause 拒赔原因
     */
    public String getClaimdUnCompensateCause() {
        return claimdUnCompensateCause;
    }

    /**
     * 拒赔原因
     * @param claimdUnCompensateCause 拒赔原因
     */
    public void setClaimdUnCompensateCause(String claimdUnCompensateCause) {
        this.claimdUnCompensateCause = claimdUnCompensateCause;
    }

    /**
     * 录入日期
     * @return claimd_enter_date 录入日期
     */
    public String getClaimdEnterDate() {
        return claimdEnterDate;
    }

    /**
     * 录入日期
     * @param claimdEnterDate 录入日期
     */
    public void setClaimdEnterDate(String claimdEnterDate) {
        this.claimdEnterDate = claimdEnterDate;
    }

    /**
     * 复核日期
     * @return claimd_reCheck_date 复核日期
     */
    public String getClaimdRecheckDate() {
        return claimdRecheckDate;
    }

    /**
     * 复核日期
     * @param claimdRecheckDate 复核日期
     */
    public void setClaimdRecheckDate(String claimdRecheckDate) {
        this.claimdRecheckDate = claimdRecheckDate;
    }

    /**
     * 分类支付是否参加理赔
     * @return claimd_classify_pay_is_compensate 分类支付是否参加理赔
     */
    public Integer getClaimdClassifyPayIsCompensate() {
        return claimdClassifyPayIsCompensate;
    }

    /**
     * 分类支付是否参加理赔
     * @param claimdClassifyPayIsCompensate 分类支付是否参加理赔
     */
    public void setClaimdClassifyPayIsCompensate(Integer claimdClassifyPayIsCompensate) {
        this.claimdClassifyPayIsCompensate = claimdClassifyPayIsCompensate;
    }

    /**
     * 自费是否参加理赔
     * @return claimd_self_pay_is_compensate 自费是否参加理赔
     */
    public Integer getClaimdSelfPayIsCompensate() {
        return claimdSelfPayIsCompensate;
    }

    /**
     * 自费是否参加理赔
     * @param claimdSelfPayIsCompensate 自费是否参加理赔
     */
    public void setClaimdSelfPayIsCompensate(Integer claimdSelfPayIsCompensate) {
        this.claimdSelfPayIsCompensate = claimdSelfPayIsCompensate;
    }

    /**
     * 个人承担费用”也就是：现金+账户
     * @return claimd_self_assume_amt 个人承担费用”也就是：现金+账户
     */
    public BigDecimal getClaimdSelfAssumeAmt() {
        return claimdSelfAssumeAmt;
    }

    /**
     * 个人承担费用”也就是：现金+账户
     * @param claimdSelfAssumeAmt 个人承担费用”也就是：现金+账户
     */
    public void setClaimdSelfAssumeAmt(BigDecimal claimdSelfAssumeAmt) {
        this.claimdSelfAssumeAmt = claimdSelfAssumeAmt;
    }

    /**
     * 责任ID
     * @return claimd_duty_id 责任ID
     */
    public String getClaimdDutyId() {
        return claimdDutyId;
    }

    /**
     * 责任ID
     * @param claimdDutyId 责任ID
     */
    public void setClaimdDutyId(String claimdDutyId) {
        this.claimdDutyId = claimdDutyId;
    }

    /**
     * 自费金额old
     * @return claimd_self_pay_oid 自费金额old
     */
    public String getClaimdSelfPayOid() {
        return claimdSelfPayOid;
    }

    /**
     * 自费金额old
     * @param claimdSelfPayOid 自费金额old
     */
    public void setClaimdSelfPayOid(String claimdSelfPayOid) {
        this.claimdSelfPayOid = claimdSelfPayOid;
    }

    /**
     * 医院ID
     * @return claimd_hospital_id 医院ID
     */
    public String getClaimdHospitalId() {
        return claimdHospitalId;
    }

    /**
     * 医院ID
     * @param claimdHospitalId 医院ID
     */
    public void setClaimdHospitalId(String claimdHospitalId) {
        this.claimdHospitalId = claimdHospitalId;
    }

    /**
     * 疾病代码
     * @return claimd_disease_code 疾病代码
     */
    public String getClaimdDiseaseCode() {
        return claimdDiseaseCode;
    }

    /**
     * 疾病代码
     * @param claimdDiseaseCode 疾病代码
     */
    public void setClaimdDiseaseCode(String claimdDiseaseCode) {
        this.claimdDiseaseCode = claimdDiseaseCode;
    }

    /**
     * 自费是否参加理赔
     * @return claimd_createTime 自费是否参加理赔
     */
    public Date getClaimdCreatetime() {
        return claimdCreatetime;
    }

    /**
     * 自费是否参加理赔
     * @param claimdCreatetime 自费是否参加理赔
     */
    public void setClaimdCreatetime(Date claimdCreatetime) {
        this.claimdCreatetime = claimdCreatetime;
    }

    /**
     *
     * @return claimd_updateTime
     */
    public Date getClaimdUpdatetime() {
        return claimdUpdatetime;
    }

    /**
     *
     * @param claimdUpdatetime
     */
    public void setClaimdUpdatetime(Date claimdUpdatetime) {
        this.claimdUpdatetime = claimdUpdatetime;
    }

    /**
     * 状态，默认1，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，-1-作废
     * @return claimd_status 状态，默认1，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，-1-作废
     */
    public Integer getClaimdStatus() {
        return claimdStatus;
    }

    /**
     * 状态，默认1，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，-1-作废
     * @param claimdStatus 状态，默认1，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，-1-作废
     */
    public void setClaimdStatus(Integer claimdStatus) {
        this.claimdStatus = claimdStatus;
    }

    /**
     * 保存生成文件的有关信息
     * @return claimd_memo 保存生成文件的有关信息
     */
    public String getClaimdMemo() {
        return claimdMemo;
    }

    /**
     * 保存生成文件的有关信息
     * @param claimdMemo 保存生成文件的有关信息
     */
    public void setClaimdMemo(String claimdMemo) {
        this.claimdMemo = claimdMemo;
    }

    /**
     * 不合理金额
     * @return UnreasonableAmount 不合理金额
     */
    public BigDecimal getUnreasonableamount() {
        return unreasonableamount;
    }

    /**
     * 不合理金额
     * @param unreasonableamount 不合理金额
     */
    public void setUnreasonableamount(BigDecimal unreasonableamount) {
        this.unreasonableamount = unreasonableamount;
    }

    /**
     * 合理金额
     * @return ReasonableAmount 合理金额
     */
    public BigDecimal getReasonableamount() {
        return reasonableamount;
    }

    /**
     * 合理金额
     * @param reasonableamount 合理金额
     */
    public void setReasonableamount(BigDecimal reasonableamount) {
        this.reasonableamount = reasonableamount;
    }

    /**
     * 医院级别【0一级,1二级,2三级,3未评级,4区级,5县级,6市级,7县区级,8镇级】
     * @return hospitalGrade 医院级别【0一级,1二级,2三级,3未评级,4区级,5县级,6市级,7县区级,8镇级】
     */
    public String getHospitalgrade() {
        return hospitalgrade;
    }

    /**
     * 医院级别【0一级,1二级,2三级,3未评级,4区级,5县级,6市级,7县区级,8镇级】
     * @param hospitalgrade 医院级别【0一级,1二级,2三级,3未评级,4区级,5县级,6市级,7县区级,8镇级】
     */
    public void setHospitalgrade(String hospitalgrade) {
        this.hospitalgrade = hospitalgrade;
    }

    /**
     * 医院等级【0甲等,1乙等,2丙等,3未评级,4合格,5特等】
     * @return hospitalLevel 医院等级【0甲等,1乙等,2丙等,3未评级,4合格,5特等】
     */
    public String getHospitallevel() {
        return hospitallevel;
    }

    /**
     * 医院等级【0甲等,1乙等,2丙等,3未评级,4合格,5特等】
     * @param hospitallevel 医院等级【0甲等,1乙等,2丙等,3未评级,4合格,5特等】
     */
    public void setHospitallevel(String hospitallevel) {
        this.hospitallevel = hospitallevel;
    }

    /**
     * 定点医院，是否是医保，是或否
     * @return hospitalIfYb 定点医院，是否是医保，是或否
     */
    public String getHospitalifyb() {
        return hospitalifyb;
    }

    /**
     * 定点医院，是否是医保，是或否
     * @param hospitalifyb 定点医院，是否是医保，是或否
     */
    public void setHospitalifyb(String hospitalifyb) {
        this.hospitalifyb = hospitalifyb;
    }

    /**
     * 医院属性，公立/私立
     * @return hospitalProperty 医院属性，公立/私立
     */
    public String getHospitalproperty() {
        return hospitalproperty;
    }

    /**
     * 医院属性，公立/私立
     * @param hospitalproperty 医院属性，公立/私立
     */
    public void setHospitalproperty(String hospitalproperty) {
        this.hospitalproperty = hospitalproperty;
    }

    /**
     * 账单金额
     * @return formula_text 账单金额
     */
    public String getFormulaText() {
        return formulaText;
    }

    /**
     * 账单金额
     * @param formulaText 账单金额
     */
    public void setFormulaText(String formulaText) {
        this.formulaText = formulaText;
    }

    /**
     * 账单金额对应的具体的值
     * @return formula_value 账单金额对应的具体的值
     */
    public String getFormulaValue() {
        return formulaValue;
    }

    /**
     * 账单金额对应的具体的值
     * @param formulaValue 账单金额对应的具体的值
     */
    public void setFormulaValue(String formulaValue) {
        this.formulaValue = formulaValue;
    }

    /**
     * 先期给付(统筹支付字段)
     * @return earlyCompensateAmt 先期给付(统筹支付字段)
     */
    public String getEarlycompensateamt() {
        return earlycompensateamt;
    }

    /**
     * 先期给付(统筹支付字段)
     * @param earlycompensateamt 先期给付(统筹支付字段)
     */
    public void setEarlycompensateamt(String earlycompensateamt) {
        this.earlycompensateamt = earlycompensateamt;
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
     * @return claimd_duty_name 责任名称
     */
    public String getClaimdDutyName() {
        return claimdDutyName;
    }

    /**
     * 责任名称
     * @param claimdDutyName 责任名称
     */
    public void setClaimdDutyName(String claimdDutyName) {
        this.claimdDutyName = claimdDutyName;
    }

    /**
     * 就诊类型，2门诊、1住院
     * @return claimd_visit_type 就诊类型，2门诊、1住院
     */
    public Integer getClaimdVisitType() {
        return claimdVisitType;
    }

    /**
     * 就诊类型，2门诊、1住院
     * @param claimdVisitType 就诊类型，2门诊、1住院
     */
    public void setClaimdVisitType(Integer claimdVisitType) {
        this.claimdVisitType = claimdVisitType;
    }

    /**
     * 有无医保，1无，2有
     * @return claimd_have_yb 有无医保，1无，2有
     */
    public Integer getClaimdHaveYb() {
        return claimdHaveYb;
    }

    /**
     * 有无医保，1无，2有
     * @param claimdHaveYb 有无医保，1无，2有
     */
    public void setClaimdHaveYb(Integer claimdHaveYb) {
        this.claimdHaveYb = claimdHaveYb;
    }

    /**
     * 拒赔原因代码，太保枚举
     * @return claimd_un_compensate_cause_code 拒赔原因代码，太保枚举
     */
    public String getClaimdUnCompensateCauseCode() {
        return claimdUnCompensateCauseCode;
    }

    /**
     * 拒赔原因代码，太保枚举
     * @param claimdUnCompensateCauseCode 拒赔原因代码，太保枚举
     */
    public void setClaimdUnCompensateCauseCode(String claimdUnCompensateCauseCode) {
        this.claimdUnCompensateCauseCode = claimdUnCompensateCauseCode;
    }

    /**
     * 费用扣除原因代码
     * @return claimd_deduct_cause_code 费用扣除原因代码
     */
    public String getClaimdDeductCauseCode() {
        return claimdDeductCauseCode;
    }

    /**
     * 费用扣除原因代码
     * @param claimdDeductCauseCode 费用扣除原因代码
     */
    public void setClaimdDeductCauseCode(String claimdDeductCauseCode) {
        this.claimdDeductCauseCode = claimdDeductCauseCode;
    }

    /**
     * 费用扣除原因
     * @return claimd_deduct_cause 费用扣除原因
     */
    public String getClaimdDeductCause() {
        return claimdDeductCause;
    }

    /**
     * 费用扣除原因
     * @param claimdDeductCause 费用扣除原因
     */
    public void setClaimdDeductCause(String claimdDeductCause) {
        this.claimdDeductCause = claimdDeductCause;
    }

    /**
     * 手术代码
     * @return claimd_operation_code 手术代码
     */
    public String getClaimdOperationCode() {
        return claimdOperationCode;
    }

    /**
     * 手术代码
     * @param claimdOperationCode 手术代码
     */
    public void setClaimdOperationCode(String claimdOperationCode) {
        this.claimdOperationCode = claimdOperationCode;
    }

    /**
     * 入院日期
     * @return claimd_in_hospital_date 入院日期
     */
    public String getClaimdInHospitalDate() {
        return claimdInHospitalDate;
    }

    /**
     * 入院日期
     * @param claimdInHospitalDate 入院日期
     */
    public void setClaimdInHospitalDate(String claimdInHospitalDate) {
        this.claimdInHospitalDate = claimdInHospitalDate;
    }

    /**
     * 账单类型
     * @return claimd_billType 账单类型
     */
    public Integer getClaimdBilltype() {
        return claimdBilltype;
    }

    /**
     * 账单类型
     * @param claimdBilltype 账单类型
     */
    public void setClaimdBilltype(Integer claimdBilltype) {
        this.claimdBilltype = claimdBilltype;
    }

    /**
     * 医院所在省
     * @return claimd_hospitalProvince 医院所在省
     */
    public String getClaimdHospitalprovince() {
        return claimdHospitalprovince;
    }

    /**
     * 医院所在省
     * @param claimdHospitalprovince 医院所在省
     */
    public void setClaimdHospitalprovince(String claimdHospitalprovince) {
        this.claimdHospitalprovince = claimdHospitalprovince;
    }

    /**
     * 医院所在市
     * @return claimd_hospitalCity 医院所在市
     */
    public String getClaimdHospitalcity() {
        return claimdHospitalcity;
    }

    /**
     * 医院所在市
     * @param claimdHospitalcity 医院所在市
     */
    public void setClaimdHospitalcity(String claimdHospitalcity) {
        this.claimdHospitalcity = claimdHospitalcity;
    }

    /**
     * 医院所在区
     * @return claimd_hospitalArea 医院所在区
     */
    public String getClaimdHospitalarea() {
        return claimdHospitalarea;
    }

    /**
     * 医院所在区
     * @param claimdHospitalarea 医院所在区
     */
    public void setClaimdHospitalarea(String claimdHospitalarea) {
        this.claimdHospitalarea = claimdHospitalarea;
    }

    /**
     * 现金支付
     * @return claimd_cash_pay 现金支付
     */
    public BigDecimal getClaimdCashPay() {
        return claimdCashPay;
    }

    /**
     * 现金支付
     * @param claimdCashPay 现金支付
     */
    public void setClaimdCashPay(BigDecimal claimdCashPay) {
        this.claimdCashPay = claimdCashPay;
    }

    /**
     * 新农合报销金额
     * @return claimd_summit_amt 新农合报销金额
     */
    public BigDecimal getClaimdSummitAmt() {
        return claimdSummitAmt;
    }

    /**
     * 新农合报销金额
     * @param claimdSummitAmt 新农合报销金额
     */
    public void setClaimdSummitAmt(BigDecimal claimdSummitAmt) {
        this.claimdSummitAmt = claimdSummitAmt;
    }

    /**
     * 大额(互助资金)（门诊/住院）本次支付
     * @return claimd_mutual_pay 大额(互助资金)（门诊/住院）本次支付
     */
    public BigDecimal getClaimdMutualPay() {
        return claimdMutualPay;
    }

    /**
     * 大额(互助资金)（门诊/住院）本次支付
     * @param claimdMutualPay 大额(互助资金)（门诊/住院）本次支付
     */
    public void setClaimdMutualPay(BigDecimal claimdMutualPay) {
        this.claimdMutualPay = claimdMutualPay;
    }

    /**
     * 出具单位
     * @return claimd_issued_unit 出具单位
     */
    public String getClaimdIssuedUnit() {
        return claimdIssuedUnit;
    }

    /**
     * 出具单位
     * @param claimdIssuedUnit 出具单位
     */
    public void setClaimdIssuedUnit(String claimdIssuedUnit) {
        this.claimdIssuedUnit = claimdIssuedUnit;
    }

    /**
     * 剩余发生金额
     * @return claimd_surplus_pay 剩余发生金额
     */
    public BigDecimal getClaimdSurplusPay() {
        return claimdSurplusPay;
    }

    /**
     * 剩余发生金额
     * @param claimdSurplusPay 剩余发生金额
     */
    public void setClaimdSurplusPay(BigDecimal claimdSurplusPay) {
        this.claimdSurplusPay = claimdSurplusPay;
    }

    /**
     * 给第三方的赔付比率
     * @return claimd_thirdparty_ratio 给第三方的赔付比率
     */
    public BigDecimal getClaimdThirdpartyRatio() {
        return claimdThirdpartyRatio;
    }

    /**
     * 给第三方的赔付比率
     * @param claimdThirdpartyRatio 给第三方的赔付比率
     */
    public void setClaimdThirdpartyRatio(BigDecimal claimdThirdpartyRatio) {
        this.claimdThirdpartyRatio = claimdThirdpartyRatio;
    }

    public List<TbOverClaimDetailExtend> getDetailExtends() {
        return detailExtends;
    }

    public void setDetailExtends(List<TbOverClaimDetailExtend> detailExtends) {
        this.detailExtends = detailExtends;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table tb_over_claim_detail
     *
     * @mbg.generated
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", claimdId=").append(claimdId);
        sb.append(", adjustmentguid=").append(adjustmentguid);
        sb.append(", claimdBatchCode=").append(claimdBatchCode);
        sb.append(", claimdGroupPolicy=").append(claimdGroupPolicy);
        sb.append(", claimdClaimCode=").append(claimdClaimCode);
        sb.append(", claimdClaimCodeTb=").append(claimdClaimCodeTb);
        sb.append(", claimdCorpCode=").append(claimdCorpCode);
        sb.append(", claimdPersonCertid=").append(claimdPersonCertid);
        sb.append(", claimdBillCode=").append(claimdBillCode);
        sb.append(", claimdVisitDate=").append(claimdVisitDate);
        sb.append(", claimdHospitalCode=").append(claimdHospitalCode);
        sb.append(", claimdHospitalName=").append(claimdHospitalName);
        sb.append(", claimdVisitDuty=").append(claimdVisitDuty);
        sb.append(", claimdDiseaseId=").append(claimdDiseaseId);
        sb.append(", claimdDiseaseName=").append(claimdDiseaseName);
        sb.append(", claimdSelfPayAmt=").append(claimdSelfPayAmt);
        sb.append(", claimdClassifyPay=").append(claimdClassifyPay);
        sb.append(", claimdNurseAmt=").append(claimdNurseAmt);
        sb.append(", claimdSelfCashAmt=").append(claimdSelfCashAmt);
        sb.append(", claimdAccountPayAmt=").append(claimdAccountPayAmt);
        sb.append(", claimdPlanPayAmt=").append(claimdPlanPayAmt);
        sb.append(", claimdAppendPayAmt=").append(claimdAppendPayAmt);
        sb.append(", claimdThirdpartyPayAmt=").append(claimdThirdpartyPayAmt);
        sb.append(", claimdInspectAmt=").append(claimdInspectAmt);
        sb.append(", claimdPhysiotherapyAmt=").append(claimdPhysiotherapyAmt);
        sb.append(", claimdMedicineAmt=").append(claimdMedicineAmt);
        sb.append(", claimdCleanToothAmt=").append(claimdCleanToothAmt);
        sb.append(", claimdOutDate=").append(claimdOutDate);
        sb.append(", claimdHospitalDays=").append(claimdHospitalDays);
        sb.append(", claimdChangeAmt=").append(claimdChangeAmt);
        sb.append(", claimdChangeDays=").append(claimdChangeDays);
        sb.append(", claimdChangeCause=").append(claimdChangeCause);
        sb.append(", claimdAbtmDays=").append(claimdAbtmDays);
        sb.append(", claimdCompensateDays=").append(claimdCompensateDays);
        sb.append(", claimdApplyAmt=").append(claimdApplyAmt);
        sb.append(", claimdAbtmAmt=").append(claimdAbtmAmt);
        sb.append(", claimdCompensateAmt=").append(claimdCompensateAmt);
        sb.append(", claimdBillProperty=").append(claimdBillProperty);
        sb.append(", claimdUnCompensateCause=").append(claimdUnCompensateCause);
        sb.append(", claimdEnterDate=").append(claimdEnterDate);
        sb.append(", claimdRecheckDate=").append(claimdRecheckDate);
        sb.append(", claimdClassifyPayIsCompensate=").append(claimdClassifyPayIsCompensate);
        sb.append(", claimdSelfPayIsCompensate=").append(claimdSelfPayIsCompensate);
        sb.append(", claimdSelfAssumeAmt=").append(claimdSelfAssumeAmt);
        sb.append(", claimdDutyId=").append(claimdDutyId);
        sb.append(", claimdSelfPayOid=").append(claimdSelfPayOid);
        sb.append(", claimdHospitalId=").append(claimdHospitalId);
        sb.append(", claimdDiseaseCode=").append(claimdDiseaseCode);
        sb.append(", claimdCreatetime=").append(claimdCreatetime);
        sb.append(", claimdUpdatetime=").append(claimdUpdatetime);
        sb.append(", claimdStatus=").append(claimdStatus);
        sb.append(", claimdMemo=").append(claimdMemo);
        sb.append(", unreasonableamount=").append(unreasonableamount);
        sb.append(", reasonableamount=").append(reasonableamount);
        sb.append(", hospitalgrade=").append(hospitalgrade);
        sb.append(", hospitallevel=").append(hospitallevel);
        sb.append(", hospitalifyb=").append(hospitalifyb);
        sb.append(", hospitalproperty=").append(hospitalproperty);
        sb.append(", formulaText=").append(formulaText);
        sb.append(", formulaValue=").append(formulaValue);
        sb.append(", earlycompensateamt=").append(earlycompensateamt);
        sb.append(", claimheadnumber=").append(claimheadnumber);
        sb.append(", claimdDutyName=").append(claimdDutyName);
        sb.append(", claimdVisitType=").append(claimdVisitType);
        sb.append(", claimdHaveYb=").append(claimdHaveYb);
        sb.append(", claimdUnCompensateCauseCode=").append(claimdUnCompensateCauseCode);
        sb.append(", claimdDeductCauseCode=").append(claimdDeductCauseCode);
        sb.append(", claimdDeductCause=").append(claimdDeductCause);
        sb.append(", claimdOperationCode=").append(claimdOperationCode);
        sb.append(", claimdInHospitalDate=").append(claimdInHospitalDate);
        sb.append(", claimdBilltype=").append(claimdBilltype);
        sb.append(", claimdHospitalprovince=").append(claimdHospitalprovince);
        sb.append(", claimdHospitalcity=").append(claimdHospitalcity);
        sb.append(", claimdHospitalarea=").append(claimdHospitalarea);
        sb.append(", claimdCashPay=").append(claimdCashPay);
        sb.append(", claimdSummitAmt=").append(claimdSummitAmt);
        sb.append(", claimdMutualPay=").append(claimdMutualPay);
        sb.append(", claimdIssuedUnit=").append(claimdIssuedUnit);
        sb.append(", claimdSurplusPay=").append(claimdSurplusPay);
        sb.append(", claimdThirdpartyRatio=").append(claimdThirdpartyRatio);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}
