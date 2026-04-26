package com.bone.tpa.sdk.masterdb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 理赔结案数据
 */
public class TbOverClaim implements Serializable {
    @TableId(value="claim_id",type = IdType.AUTO)
    private Integer claimId;

    /**
     * 批次号
     *
     * @mbg.generated
     */
    @Schema(description = "批次号")
    private String claimBatchCode;

    /**
     * 主单号
     *
     * @mbg.generated
     */
    @Schema(description = "主单号")
    private String claimGroupPolicy;

    /**
     * 赔案号
     *
     * @mbg.generated
     */
    @Schema(description = "赔案号")
    private String claimClaimCode;

    /**
     * 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     *
     * @mbg.generated
     */
    @Schema(description = "专门用于给太保发送用的，其实就是“PK0000000'+caseCode")
    private String claimClaimCodeTb;

    /**
     * 赔案状态
     *
     * @mbg.generated
     */
    @Schema(description = "赔案状态")
    private Integer claimClaimStatus;

    /**
     * 投保序号
     *
     * @mbg.generated
     */
    @Schema(description = "投保序号")
    private String claimCorpCode;

    /**
     * 被保人身份证号
     *
     * @mbg.generated
     */
    @Schema(description = "被保人身份证号")
    private String claimPersonCertid;

    /**
     * 保单号,个单号
     *
     * @mbg.generated
     */
    @Schema(description = "保单号,个单号")
    private String claimPolicy;

    /**
     * 出险日期
     *
     * @mbg.generated
     */
    @Schema(description = "出险日期")
    private String claimPolicyDate;

    /**
     * 出险类型
     *
     * @mbg.generated
     */
    @Schema(description = "出险类型")
    private Integer claimPolicyType;

    private String claimAccidentNature;

    /**
     * 出险经过与结果
     *
     * @mbg.generated
     */
    @Schema(description = "出险经过与结果")
    private String claimOutPass;

    /**
     * 领款人姓名
     *
     * @mbg.generated
     */
    @Schema(description = "领款人姓名")
    private String claimPayeeName;

    /**
     * 领款人证件号
     *
     * @mbg.generated
     */
    @Schema(description = "领款人证件号")
    private String claimPayeeCertid;

    /**
     * 领款人性别，1-男，2-女
     *
     * @mbg.generated
     */
    @Schema(description = "领款人性别，1-男，2-女")
    private String claimPayeeGender;

    /**
     * 手机号
     *
     * @mbg.generated
     */
    @Schema(description = "手机号")
    private String claimPersonMobile;

    /**
     * 领款人支付方式
     *
     * @mbg.generated
     */
    @Schema(description = "领款人支付方式")
    private String claimPayeePayType;

    /**
     * 领款人账号
     *
     * @mbg.generated
     */
    @Schema(description = "领款人账号")
    private String claimPayeeAccount;

    /**
     * 开户行代码
     *
     * @mbg.generated
     */
    @Schema(description = "开户行代码")
    private String claimBankCode;

    /**
     * 申请金额
     *
     * @mbg.generated
     */
    @Schema(description = "申请金额")
    private BigDecimal claimApplyAmt;

    /**
     * 免赔额
     *
     * @mbg.generated
     */
    @Schema(description = "免赔额")
    private BigDecimal claimAbtmAmt;

    /**
     * 赔付金额
     *
     * @mbg.generated
     */
    @Schema(description = "赔付金额")
    private BigDecimal claimCompensateAmt;

    /**
     * 调整金额
     *
     * @mbg.generated
     */
    @Schema(description = "调整金额")
    private BigDecimal claimChangeAmt;

    /**
     * 结案日期
     *
     * @mbg.generated
     */
    @Schema(description = "结案日期")
    private String claimEndDate;

    /**
     * 险种代码
     *
     * @mbg.generated
     */
    @Schema(description = "险种代码")
    private String claimTypeCode;

    /**
     * 投保团体
     *
     * @mbg.generated
     */
    @Schema(description = "投保团体")
    private String claimCorpName;

    /**
     * 主被保人姓名
     *
     * @mbg.generated
     */
    @Schema(description = "主被保人姓名")
    private String claimMainPersonName;

    /**
     * 主被保人身份证号
     *
     * @mbg.generated
     */
    @Schema(description = "主被保人身份证号")
    private String claimMainPersonCertid;

    /**
     * 开户行名称
     *
     * @mbg.generated
     */
    @Schema(description = "开户行名称")
    private String claimBankName;

    /**
     * AdjustmentGuid(理算ID)
     *
     * @mbg.generated
     */
    @Schema(description = "AdjustmentGuid(理算ID)")
    private String claimAdjustmentGuid;

    /**
     * 发票金额
     *
     * @mbg.generated
     */
    @Schema(description = "发票金额")
    private BigDecimal claimBillAmt;

    private Date claimCreatetime;

    private Date claimUpdatetime;

    /**
     * 状态，默认1，0-太享福已撤案，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，4-已完成，-1-作废，    5-影像件踢回
     *
     * @mbg.generated
     */
    @Schema(description = "状态，默认1，0-太享福已撤案，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，4-已完成，-1-作废，    5-影像件踢回")
    private Integer claimStatus;

    private String claimMemo;

    /**
     * TPA状态，默认1，0-已踢回，1-内网已审核，2-外网审核成功,3-TPA已执行踢回操作
     *
     * @mbg.generated
     */
    @Schema(description = "TPA状态，默认1，0-已踢回，1-内网已审核，2-外网审核成功,3-TPA已执行踢回操作")
    private Integer claimTpaStatus;

    /**
     * 是否打款，默认1-未打款，2-已打款
     *
     * @mbg.generated
     */
    @Schema(description = "是否打款，默认1-未打款，2-已打款")
    private Integer claimPayStatus;

    /**
     * TPA批次号
     *
     * @mbg.generated
     */
    @Schema(description = "TPA批次号")
    private String claimheadnumber;

    /**
     * 报案日期，格式yyyy-MM-dd
     *
     * @mbg.generated
     */
    @Schema(description = "报案日期，格式yyyy-MM-dd")
    private String claimReportDate;

    /**
     * 分支机构，跟tb_branch表关联
     *
     * @mbg.generated
     */
    @Schema(description = "分支机构，跟tb_branch表关联")
    private String claimBranchCode;

    /**
     * 分支机构名称，跟tb_branch表关联
     *
     * @mbg.generated
     */
    @Schema(description = "分支机构名称，跟tb_branch表关联")
    private String claimBranchName;

    /**
     * 太保PKUP质检状态，1-未结案，2-已结案
     *
     * @mbg.generated
     */
    @Schema(description = "太保PKUP质检状态，1-未结案，2-已结案")
    private Integer tbclaimstatus;

    /**
     * 签收日期
     *
     * @mbg.generated
     */
    @Schema(description = "签收日期")
    private String claimSignDate;

    /**
     * 领款人属性 必录、文本格式、0-自然人 1-法人
     *
     * @mbg.generated
     */
    @Schema(description = "领款人属性 必录、文本格式、0-自然人 1-法人")
    private Integer claimPayeeProperty;

    /**
     * 领款人与被保险的关系，默认401-本人
     *
     * @mbg.generated
     */
    @Schema(description = "领款人与被保险的关系，默认401-本人")
    private String claimRelation;

    /**
     * 国籍，太保枚举，默认为中国，仅领款人为自然人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "国籍，太保枚举，默认为中国，仅领款人为自然人时填写")
    private String claimNationality;

    /**
     * 职业，太保枚举，默认：0000000-职业不详，仅领款人为自然人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "职业，太保枚举，默认：0000000-职业不详，仅领款人为自然人时填写")
    private String claimProfessional;

    /**
     * 经营范围，默认：未知，仅领款人为法人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "经营范围，默认：未知，仅领款人为法人时填写")
    private String claimWorkScope;

    /**
     * 实际控制人类型，1-法定代表人，1-控股股东，3-实际控制人，4-负责人，仅领款人为法人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "实际控制人类型，1-法定代表人，1-控股股东，3-实际控制人，4-负责人，仅领款人为法人时填写")
    private Integer claimControlPersonType;

    /**
     * 实际控制人姓名,仅领款人为法人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "实际控制人姓名,仅领款人为法人时填写")
    private String claimControlPersonName;

    /**
     * 实际控制人证件类型,非必填，参见枚举值，仅领款人为法人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "实际控制人证件类型,非必填，参见枚举值，仅领款人为法人时填写")
    private String claimControlPersonCerttype;

    /**
     * 实际控制人证件号码，非必填，仅领款人为法人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "实际控制人证件号码，非必填，仅领款人为法人时填写")
    private String claimControlPersonCertid;

    /**
     * 实际控制人证件有效起期，非必填,YYYY/MM/DD，仅领款人为法人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "实际控制人证件有效起期，非必填,YYYY/MM/DD，仅领款人为法人时填写")
    private String claimControlPersonCertBeginDate;

    /**
     * 实际控制人证件有效终期,非必填,YYYY/MM/DD，仅领款人为法人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "实际控制人证件有效终期,非必填,YYYY/MM/DD，仅领款人为法人时填写")
    private String claimControlPersonCertEndDate;

    /**
     * 实际控制人移动电话,非必填，仅领款人为法人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "实际控制人移动电话,非必填，仅领款人为法人时填写")
    private String claimControlPersonMobile;

    /**
     * 代办人姓名，非必填，仅领款人为法人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "代办人姓名，非必填，仅领款人为法人时填写")
    private String claimRepresentativeName;

    /**
     * 代办人证件类型,非必填，仅领款人为法人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "代办人证件类型,非必填，仅领款人为法人时填写")
    private String claimRepresentativeCerttype;

    /**
     * 代办人证件号,非必填，仅领款人为法人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "代办人证件号,非必填，仅领款人为法人时填写")
    private String claimRepresentativeCertid;

    /**
     * 代办人证件有效起期,非必填，YYYY/MM/DD，仅领款人为法人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "代办人证件有效起期,非必填，YYYY/MM/DD，仅领款人为法人时填写")
    private String claimRepresentativeCertBeginDate;

    /**
     * 代办人证件有效终期,非必填，YYYY/MM/DD，仅领款人为法人时填写
     *
     * @mbg.generated
     */
    @Schema(description = "代办人证件有效终期,非必填，YYYY/MM/DD，仅领款人为法人时填写")
    private String claimRepresentativeCertEndDate;

    /**
     * 领款人证件有效起期,当赔付金额大于等于1万元，必录，文本格式，YYYY/MM/DD，10位
     *
     * @mbg.generated
     */
    @Schema(description = "领款人证件有效起期,当赔付金额大于等于1万元，必录，文本格式，YYYY/MM/DD，10位")
    private String claimPayeecertbegindate;

    /**
     * 领款人证件有效终期,当赔付金额大于等于1万元，必录，文本格式，YYYY/MM/DD，10位
     *
     * @mbg.generated
     */
    @Schema(description = "领款人证件有效终期,当赔付金额大于等于1万元，必录，文本格式，YYYY/MM/DD，10位")
    private String claimPayeecertenddate;

    /**
     * 领款人与主被保险人的关系，赔付金额大于等于1万元时必录，默认401-本人
     *
     * @mbg.generated
     */
    @Schema(description = "领款人与主被保险人的关系，赔付金额大于等于1万元时必录，默认401-本人")
    private String claimPayeeMainpersonRelation;

    /**
     * 受益人与主被保险人的关系，赔付金额大于等于1万元时必录，默认401-本人
     *
     * @mbg.generated
     */
    @Schema(description = "受益人与主被保险人的关系，赔付金额大于等于1万元时必录，默认401-本人")
    private String claimBeneficiaryMainpersonRelation;

    /**
     * 领款人经常居住地
     *
     * @mbg.generated
     */
    @Schema(description = "领款人经常居住地")
    private String claimOftenLiveAddress;

    /**
     * 银行类型代码
     *
     * @mbg.generated
     */
    @Schema(description = "银行类型代码")
    private String claimBankTypeCode;

    /**
     * 开户行区域编码
     *
     * @mbg.generated
     */
    @Schema(description = "开户行区域编码")
    private String claimBankAreaCode;

    /**
     * 0: 对公 1：对私
     *
     * @mbg.generated
     */
    @Schema(description = "0: 对公 1：对私")
    private Integer claimPayObj;

    /**
     * 永诚主共从共赔付比例 如 ：70:30
     *
     * @mbg.generated
     */
    @Schema(description = "永诚主共从共赔付比例 如 ：70:30")
    private String claimYcComratio;

    /**
     * 案件结论，1-正常赔付，0-拒赔
     *
     * @mbg.generated
     */
    @Schema(description = "案件结论，1-正常赔付，0-拒赔")
    private Integer claimConclusion;

    /**
     * 长安推送状态(1初始状态 4推送成功 -1推送失败, 10失败(中银), 11成功(中银))
     *
     * @mbg.generated
     */
    @Schema(description = "长安推送状态(1初始状态 4推送成功 -1推送失败, 10失败(中银), 11成功(中银))")
    private Integer claimCaStatus;

    /**
     * 长安推送错误原因
     *
     * @mbg.generated
     */
    @Schema(description = "长安推送错误原因")
    private String claimCaMemo;

    /**
     * 太保财批次号
     *
     * @mbg.generated
     */
    @Schema(description = "太保财批次号")
    private Integer claimBbrpc;

    /**
     * 瑞泰专用 公共账户金额
     *
     * @mbg.generated
     */
    @Schema(description = "瑞泰专用 公共账户金额")
    private BigDecimal claimPublicAmount;

    /**
     * 瑞泰专用 公共账户余额 
     *
     * @mbg.generated
     */
    @Schema(description = "瑞泰专用 公共账户余额 ")
    private BigDecimal claimPublicAccountBalance;

    /**
     * 瑞泰专用 个人账户赔付金额
     *
     * @mbg.generated
     */
    @Schema(description = "瑞泰专用 个人账户赔付金额")
    private BigDecimal claimPersonalAmount;

    /**
     * 湖南专用，默认不推送
     *
     * @mbg.generated
     */
    @Schema(description = "湖南专用，默认不推送")
    private Integer claimHnIspush;

    private String claimTpastatusSign;

    /**
     * 状态变更操作人
     *
     * @mbg.generated
     */
    @Schema(description = "状态变更操作人")
    private String claimStatuschangeUser;

    /**
     * 状态变更时间
     *
     * @mbg.generated
     */
    @Schema(description = "状态变更时间")
    private Date claimStatuschangeDate;

    /**
     * 山东国网流水号
     *
     * @mbg.generated
     */
    @Schema(description = "山东国网流水号")
    private String claimGuowangSerialNum;

    /**
     * 支付时间
     *
     * @mbg.generated
     */
    @Schema(description = "支付时间")
    private Date claimPayTime;

    /**
     * 影像件地址
     *
     * @mbg.generated
     */
    @Schema(description = "影像件地址")
    private String imagingpath;

    /**
     * 拒赔原因，整笔案件的，当结论为拒赔时，该字段将存放拒赔原因
     *
     * @mbg.generated
     */
    @Schema(description = "拒赔原因，整笔案件的，当结论为拒赔时，该字段将存放拒赔原因")
    private String claimUnCompensateCause;



    /**
     * This field was generated by MyBatis Generator.
     * This field corresponds to the database table tb_over_claim
     *
     * @mbg.generated
     */
    private static final long serialVersionUID = 1L;

    /**
     * 
     * @return claim_id 
     */
    public Integer getClaimId() {
        return claimId;
    }

    /**
     * 
     * @param claimId 
     */
    public void setClaimId(Integer claimId) {
        this.claimId = claimId;
    }

    /**
     * 批次号
     * @return claim_batch_code 批次号
     */
    public String getClaimBatchCode() {
        return claimBatchCode;
    }

    /**
     * 批次号
     * @param claimBatchCode 批次号
     */
    public void setClaimBatchCode(String claimBatchCode) {
        this.claimBatchCode = claimBatchCode;
    }

    /**
     * 主单号
     * @return claim_group_policy 主单号
     */
    public String getClaimGroupPolicy() {
        return claimGroupPolicy;
    }

    /**
     * 主单号
     * @param claimGroupPolicy 主单号
     */
    public void setClaimGroupPolicy(String claimGroupPolicy) {
        this.claimGroupPolicy = claimGroupPolicy;
    }

    /**
     * 赔案号
     * @return claim_claim_code 赔案号
     */
    public String getClaimClaimCode() {
        return claimClaimCode;
    }

    /**
     * 赔案号
     * @param claimClaimCode 赔案号
     */
    public void setClaimClaimCode(String claimClaimCode) {
        this.claimClaimCode = claimClaimCode;
    }

    /**
     * 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     * @return claim_claim_code_tb 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     */
    public String getClaimClaimCodeTb() {
        return claimClaimCodeTb;
    }

    /**
     * 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     * @param claimClaimCodeTb 专门用于给太保发送用的，其实就是“PK0000000"+caseCode
     */
    public void setClaimClaimCodeTb(String claimClaimCodeTb) {
        this.claimClaimCodeTb = claimClaimCodeTb;
    }

    /**
     * 赔案状态
     * @return claim_claim_status 赔案状态
     */
    public Integer getClaimClaimStatus() {
        return claimClaimStatus;
    }

    /**
     * 赔案状态
     * @param claimClaimStatus 赔案状态
     */
    public void setClaimClaimStatus(Integer claimClaimStatus) {
        this.claimClaimStatus = claimClaimStatus;
    }

    /**
     * 投保序号
     * @return claim_corp_code 投保序号
     */
    public String getClaimCorpCode() {
        return claimCorpCode;
    }

    /**
     * 投保序号
     * @param claimCorpCode 投保序号
     */
    public void setClaimCorpCode(String claimCorpCode) {
        this.claimCorpCode = claimCorpCode;
    }

    /**
     * 被保人身份证号
     * @return claim_person_certId 被保人身份证号
     */
    public String getClaimPersonCertid() {
        return claimPersonCertid;
    }

    /**
     * 被保人身份证号
     * @param claimPersonCertid 被保人身份证号
     */
    public void setClaimPersonCertid(String claimPersonCertid) {
        this.claimPersonCertid = claimPersonCertid;
    }

    /**
     * 保单号,个单号
     * @return claim_policy 保单号,个单号
     */
    public String getClaimPolicy() {
        return claimPolicy;
    }

    /**
     * 保单号,个单号
     * @param claimPolicy 保单号,个单号
     */
    public void setClaimPolicy(String claimPolicy) {
        this.claimPolicy = claimPolicy;
    }

    /**
     * 出险日期
     * @return claim_policy_date 出险日期
     */
    public String getClaimPolicyDate() {
        return claimPolicyDate;
    }

    /**
     * 出险日期
     * @param claimPolicyDate 出险日期
     */
    public void setClaimPolicyDate(String claimPolicyDate) {
        this.claimPolicyDate = claimPolicyDate;
    }

    /**
     * 出险类型
     * @return claim_policy_type 出险类型
     */
    public Integer getClaimPolicyType() {
        return claimPolicyType;
    }

    /**
     * 出险类型
     * @param claimPolicyType 出险类型
     */
    public void setClaimPolicyType(Integer claimPolicyType) {
        this.claimPolicyType = claimPolicyType;
    }

    /**
     * 
     * @return claim_accident_nature 
     */
    public String getClaimAccidentNature() {
        return claimAccidentNature;
    }

    /**
     * 
     * @param claimAccidentNature 
     */
    public void setClaimAccidentNature(String claimAccidentNature) {
        this.claimAccidentNature = claimAccidentNature;
    }

    /**
     * 出险经过与结果
     * @return claim_out_pass 出险经过与结果
     */
    public String getClaimOutPass() {
        return claimOutPass;
    }

    /**
     * 出险经过与结果
     * @param claimOutPass 出险经过与结果
     */
    public void setClaimOutPass(String claimOutPass) {
        this.claimOutPass = claimOutPass;
    }

    /**
     * 领款人姓名
     * @return claim_payee_name 领款人姓名
     */
    public String getClaimPayeeName() {
        return claimPayeeName;
    }

    /**
     * 领款人姓名
     * @param claimPayeeName 领款人姓名
     */
    public void setClaimPayeeName(String claimPayeeName) {
        this.claimPayeeName = claimPayeeName;
    }

    /**
     * 领款人证件号
     * @return claim_payee_certId 领款人证件号
     */
    public String getClaimPayeeCertid() {
        return claimPayeeCertid;
    }

    /**
     * 领款人证件号
     * @param claimPayeeCertid 领款人证件号
     */
    public void setClaimPayeeCertid(String claimPayeeCertid) {
        this.claimPayeeCertid = claimPayeeCertid;
    }

    /**
     * 领款人性别，1-男，2-女
     * @return claim_payee_gender 领款人性别，1-男，2-女
     */
    public String getClaimPayeeGender() {
        return claimPayeeGender;
    }

    /**
     * 领款人性别，1-男，2-女
     * @param claimPayeeGender 领款人性别，1-男，2-女
     */
    public void setClaimPayeeGender(String claimPayeeGender) {
        this.claimPayeeGender = claimPayeeGender;
    }

    /**
     * 手机号
     * @return claim_person_mobile 手机号
     */
    public String getClaimPersonMobile() {
        return claimPersonMobile;
    }

    /**
     * 手机号
     * @param claimPersonMobile 手机号
     */
    public void setClaimPersonMobile(String claimPersonMobile) {
        this.claimPersonMobile = claimPersonMobile;
    }

    /**
     * 领款人支付方式
     * @return claim_payee_pay_type 领款人支付方式
     */
    public String getClaimPayeePayType() {
        return claimPayeePayType;
    }

    /**
     * 领款人支付方式
     * @param claimPayeePayType 领款人支付方式
     */
    public void setClaimPayeePayType(String claimPayeePayType) {
        this.claimPayeePayType = claimPayeePayType;
    }

    /**
     * 领款人账号
     * @return claim_payee_account 领款人账号
     */
    public String getClaimPayeeAccount() {
        return claimPayeeAccount;
    }

    /**
     * 领款人账号
     * @param claimPayeeAccount 领款人账号
     */
    public void setClaimPayeeAccount(String claimPayeeAccount) {
        this.claimPayeeAccount = claimPayeeAccount;
    }

    /**
     * 开户行代码
     * @return claim_bank_code 开户行代码
     */
    public String getClaimBankCode() {
        return claimBankCode;
    }

    /**
     * 开户行代码
     * @param claimBankCode 开户行代码
     */
    public void setClaimBankCode(String claimBankCode) {
        this.claimBankCode = claimBankCode;
    }

    /**
     * 申请金额
     * @return claim_apply_amt 申请金额
     */
    public BigDecimal getClaimApplyAmt() {
        return claimApplyAmt;
    }

    /**
     * 申请金额
     * @param claimApplyAmt 申请金额
     */
    public void setClaimApplyAmt(BigDecimal claimApplyAmt) {
        this.claimApplyAmt = claimApplyAmt;
    }

    /**
     * 免赔额
     * @return claim_abtm_amt 免赔额
     */
    public BigDecimal getClaimAbtmAmt() {
        return claimAbtmAmt;
    }

    /**
     * 免赔额
     * @param claimAbtmAmt 免赔额
     */
    public void setClaimAbtmAmt(BigDecimal claimAbtmAmt) {
        this.claimAbtmAmt = claimAbtmAmt;
    }

    /**
     * 赔付金额
     * @return claim_compensate_amt 赔付金额
     */
    public BigDecimal getClaimCompensateAmt() {
        return claimCompensateAmt;
    }

    /**
     * 赔付金额
     * @param claimCompensateAmt 赔付金额
     */
    public void setClaimCompensateAmt(BigDecimal claimCompensateAmt) {
        this.claimCompensateAmt = claimCompensateAmt;
    }

    /**
     * 调整金额
     * @return claim_change_amt 调整金额
     */
    public BigDecimal getClaimChangeAmt() {
        return claimChangeAmt;
    }

    /**
     * 调整金额
     * @param claimChangeAmt 调整金额
     */
    public void setClaimChangeAmt(BigDecimal claimChangeAmt) {
        this.claimChangeAmt = claimChangeAmt;
    }

    /**
     * 结案日期
     * @return claim_end_date 结案日期
     */
    public String getClaimEndDate() {
        return claimEndDate;
    }

    /**
     * 结案日期
     * @param claimEndDate 结案日期
     */
    public void setClaimEndDate(String claimEndDate) {
        this.claimEndDate = claimEndDate;
    }

    /**
     * 险种代码
     * @return claim_type_code 险种代码
     */
    public String getClaimTypeCode() {
        return claimTypeCode;
    }

    /**
     * 险种代码
     * @param claimTypeCode 险种代码
     */
    public void setClaimTypeCode(String claimTypeCode) {
        this.claimTypeCode = claimTypeCode;
    }

    /**
     * 投保团体
     * @return claim_corp_name 投保团体
     */
    public String getClaimCorpName() {
        return claimCorpName;
    }

    /**
     * 投保团体
     * @param claimCorpName 投保团体
     */
    public void setClaimCorpName(String claimCorpName) {
        this.claimCorpName = claimCorpName;
    }

    /**
     * 主被保人姓名
     * @return claim_main_person_name 主被保人姓名
     */
    public String getClaimMainPersonName() {
        return claimMainPersonName;
    }

    /**
     * 主被保人姓名
     * @param claimMainPersonName 主被保人姓名
     */
    public void setClaimMainPersonName(String claimMainPersonName) {
        this.claimMainPersonName = claimMainPersonName;
    }

    /**
     * 主被保人身份证号
     * @return claim_main_person_certId 主被保人身份证号
     */
    public String getClaimMainPersonCertid() {
        return claimMainPersonCertid;
    }

    /**
     * 主被保人身份证号
     * @param claimMainPersonCertid 主被保人身份证号
     */
    public void setClaimMainPersonCertid(String claimMainPersonCertid) {
        this.claimMainPersonCertid = claimMainPersonCertid;
    }

    /**
     * 开户行名称
     * @return claim_bank_name 开户行名称
     */
    public String getClaimBankName() {
        return claimBankName;
    }

    /**
     * 开户行名称
     * @param claimBankName 开户行名称
     */
    public void setClaimBankName(String claimBankName) {
        this.claimBankName = claimBankName;
    }

    /**
     * AdjustmentGuid(理算ID)
     * @return claim_adjustment_guid AdjustmentGuid(理算ID)
     */
    public String getClaimAdjustmentGuid() {
        return claimAdjustmentGuid;
    }

    /**
     * AdjustmentGuid(理算ID)
     * @param claimAdjustmentGuid AdjustmentGuid(理算ID)
     */
    public void setClaimAdjustmentGuid(String claimAdjustmentGuid) {
        this.claimAdjustmentGuid = claimAdjustmentGuid;
    }

    /**
     * 发票金额
     * @return claim_bill_amt 发票金额
     */
    public BigDecimal getClaimBillAmt() {
        return claimBillAmt;
    }

    /**
     * 发票金额
     * @param claimBillAmt 发票金额
     */
    public void setClaimBillAmt(BigDecimal claimBillAmt) {
        this.claimBillAmt = claimBillAmt;
    }

    /**
     * 
     * @return claim_createTime 
     */
    public Date getClaimCreatetime() {
        return claimCreatetime;
    }

    /**
     * 
     * @param claimCreatetime 
     */
    public void setClaimCreatetime(Date claimCreatetime) {
        this.claimCreatetime = claimCreatetime;
    }

    /**
     * 
     * @return claim_updateTime 
     */
    public Date getClaimUpdatetime() {
        return claimUpdatetime;
    }

    /**
     * 
     * @param claimUpdatetime 
     */
    public void setClaimUpdatetime(Date claimUpdatetime) {
        this.claimUpdatetime = claimUpdatetime;
    }

    /**
     * 状态，默认1，0-太享福已撤案，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，4-已完成，-1-作废，    5-影像件踢回
     * @return claim_status 状态，默认1，0-太享福已撤案，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，4-已完成，-1-作废，    5-影像件踢回
     */
    public Integer getClaimStatus() {
        return claimStatus;
    }

    /**
     * 状态，默认1，0-太享福已撤案，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，4-已完成，-1-作废，    5-影像件踢回
     * @param claimStatus 状态，默认1，0-太享福已撤案，1-可以生成结案文件，2-成功生成结案文件，3-可以生成理赔通知书，4-已完成，-1-作废，    5-影像件踢回
     */
    public void setClaimStatus(Integer claimStatus) {
        this.claimStatus = claimStatus;
    }

    /**
     * 
     * @return claim_memo 
     */
    public String getClaimMemo() {
        return claimMemo;
    }

    /**
     * 
     * @param claimMemo 
     */
    public void setClaimMemo(String claimMemo) {
        this.claimMemo = claimMemo;
    }

    /**
     * TPA状态，默认1，0-已踢回，1-内网已审核，2-外网审核成功,3-TPA已执行踢回操作
     * @return claim_tpa_status TPA状态，默认1，0-已踢回，1-内网已审核，2-外网审核成功,3-TPA已执行踢回操作
     */
    public Integer getClaimTpaStatus() {
        return claimTpaStatus;
    }

    /**
     * TPA状态，默认1，0-已踢回，1-内网已审核，2-外网审核成功,3-TPA已执行踢回操作
     * @param claimTpaStatus TPA状态，默认1，0-已踢回，1-内网已审核，2-外网审核成功,3-TPA已执行踢回操作
     */
    public void setClaimTpaStatus(Integer claimTpaStatus) {
        this.claimTpaStatus = claimTpaStatus;
    }

    /**
     * 是否打款，默认1-未打款，2-已打款
     * @return claim_pay_status 是否打款，默认1-未打款，2-已打款
     */
    public Integer getClaimPayStatus() {
        return claimPayStatus;
    }

    /**
     * 是否打款，默认1-未打款，2-已打款
     * @param claimPayStatus 是否打款，默认1-未打款，2-已打款
     */
    public void setClaimPayStatus(Integer claimPayStatus) {
        this.claimPayStatus = claimPayStatus;
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
     * 报案日期，格式yyyy-MM-dd
     * @return claim_report_date 报案日期，格式yyyy-MM-dd
     */
    public String getClaimReportDate() {
        return claimReportDate;
    }

    /**
     * 报案日期，格式yyyy-MM-dd
     * @param claimReportDate 报案日期，格式yyyy-MM-dd
     */
    public void setClaimReportDate(String claimReportDate) {
        this.claimReportDate = claimReportDate;
    }

    /**
     * 分支机构，跟tb_branch表关联
     * @return claim_branch_code 分支机构，跟tb_branch表关联
     */
    public String getClaimBranchCode() {
        return claimBranchCode;
    }

    /**
     * 分支机构，跟tb_branch表关联
     * @param claimBranchCode 分支机构，跟tb_branch表关联
     */
    public void setClaimBranchCode(String claimBranchCode) {
        this.claimBranchCode = claimBranchCode;
    }

    /**
     * 分支机构名称，跟tb_branch表关联
     * @return claim_branch_name 分支机构名称，跟tb_branch表关联
     */
    public String getClaimBranchName() {
        return claimBranchName;
    }

    /**
     * 分支机构名称，跟tb_branch表关联
     * @param claimBranchName 分支机构名称，跟tb_branch表关联
     */
    public void setClaimBranchName(String claimBranchName) {
        this.claimBranchName = claimBranchName;
    }

    /**
     * 太保PKUP质检状态，1-未结案，2-已结案
     * @return tbClaimStatus 太保PKUP质检状态，1-未结案，2-已结案
     */
    public Integer getTbclaimstatus() {
        return tbclaimstatus;
    }

    /**
     * 太保PKUP质检状态，1-未结案，2-已结案
     * @param tbclaimstatus 太保PKUP质检状态，1-未结案，2-已结案
     */
    public void setTbclaimstatus(Integer tbclaimstatus) {
        this.tbclaimstatus = tbclaimstatus;
    }

    /**
     * 签收日期
     * @return claim_sign_date 签收日期
     */
    public String getClaimSignDate() {
        return claimSignDate;
    }

    /**
     * 签收日期
     * @param claimSignDate 签收日期
     */
    public void setClaimSignDate(String claimSignDate) {
        this.claimSignDate = claimSignDate;
    }

    /**
     * 领款人属性 必录、文本格式、0-自然人 1-法人
     * @return claim_payee_property 领款人属性 必录、文本格式、0-自然人 1-法人
     */
    public Integer getClaimPayeeProperty() {
        return claimPayeeProperty;
    }

    /**
     * 领款人属性 必录、文本格式、0-自然人 1-法人
     * @param claimPayeeProperty 领款人属性 必录、文本格式、0-自然人 1-法人
     */
    public void setClaimPayeeProperty(Integer claimPayeeProperty) {
        this.claimPayeeProperty = claimPayeeProperty;
    }

    /**
     * 领款人与被保险的关系，默认401-本人
     * @return claim_relation 领款人与被保险的关系，默认401-本人
     */
    public String getClaimRelation() {
        return claimRelation;
    }

    /**
     * 领款人与被保险的关系，默认401-本人
     * @param claimRelation 领款人与被保险的关系，默认401-本人
     */
    public void setClaimRelation(String claimRelation) {
        this.claimRelation = claimRelation;
    }

    /**
     * 国籍，太保枚举，默认为中国，仅领款人为自然人时填写
     * @return claim_nationality 国籍，太保枚举，默认为中国，仅领款人为自然人时填写
     */
    public String getClaimNationality() {
        return claimNationality;
    }

    /**
     * 国籍，太保枚举，默认为中国，仅领款人为自然人时填写
     * @param claimNationality 国籍，太保枚举，默认为中国，仅领款人为自然人时填写
     */
    public void setClaimNationality(String claimNationality) {
        this.claimNationality = claimNationality;
    }

    /**
     * 职业，太保枚举，默认：0000000-职业不详，仅领款人为自然人时填写
     * @return claim_professional 职业，太保枚举，默认：0000000-职业不详，仅领款人为自然人时填写
     */
    public String getClaimProfessional() {
        return claimProfessional;
    }

    /**
     * 职业，太保枚举，默认：0000000-职业不详，仅领款人为自然人时填写
     * @param claimProfessional 职业，太保枚举，默认：0000000-职业不详，仅领款人为自然人时填写
     */
    public void setClaimProfessional(String claimProfessional) {
        this.claimProfessional = claimProfessional;
    }

    /**
     * 经营范围，默认：未知，仅领款人为法人时填写
     * @return claim_work_scope 经营范围，默认：未知，仅领款人为法人时填写
     */
    public String getClaimWorkScope() {
        return claimWorkScope;
    }

    /**
     * 经营范围，默认：未知，仅领款人为法人时填写
     * @param claimWorkScope 经营范围，默认：未知，仅领款人为法人时填写
     */
    public void setClaimWorkScope(String claimWorkScope) {
        this.claimWorkScope = claimWorkScope;
    }

    /**
     * 实际控制人类型，1-法定代表人，1-控股股东，3-实际控制人，4-负责人，仅领款人为法人时填写
     * @return claim_control_person_type 实际控制人类型，1-法定代表人，1-控股股东，3-实际控制人，4-负责人，仅领款人为法人时填写
     */
    public Integer getClaimControlPersonType() {
        return claimControlPersonType;
    }

    /**
     * 实际控制人类型，1-法定代表人，1-控股股东，3-实际控制人，4-负责人，仅领款人为法人时填写
     * @param claimControlPersonType 实际控制人类型，1-法定代表人，1-控股股东，3-实际控制人，4-负责人，仅领款人为法人时填写
     */
    public void setClaimControlPersonType(Integer claimControlPersonType) {
        this.claimControlPersonType = claimControlPersonType;
    }

    /**
     * 实际控制人姓名,仅领款人为法人时填写
     * @return claim_control_person_name 实际控制人姓名,仅领款人为法人时填写
     */
    public String getClaimControlPersonName() {
        return claimControlPersonName;
    }

    /**
     * 实际控制人姓名,仅领款人为法人时填写
     * @param claimControlPersonName 实际控制人姓名,仅领款人为法人时填写
     */
    public void setClaimControlPersonName(String claimControlPersonName) {
        this.claimControlPersonName = claimControlPersonName;
    }

    /**
     * 实际控制人证件类型,非必填，参见枚举值，仅领款人为法人时填写
     * @return claim_control_person_certType 实际控制人证件类型,非必填，参见枚举值，仅领款人为法人时填写
     */
    public String getClaimControlPersonCerttype() {
        return claimControlPersonCerttype;
    }

    /**
     * 实际控制人证件类型,非必填，参见枚举值，仅领款人为法人时填写
     * @param claimControlPersonCerttype 实际控制人证件类型,非必填，参见枚举值，仅领款人为法人时填写
     */
    public void setClaimControlPersonCerttype(String claimControlPersonCerttype) {
        this.claimControlPersonCerttype = claimControlPersonCerttype;
    }

    /**
     * 实际控制人证件号码，非必填，仅领款人为法人时填写
     * @return claim_control_person_certId 实际控制人证件号码，非必填，仅领款人为法人时填写
     */
    public String getClaimControlPersonCertid() {
        return claimControlPersonCertid;
    }

    /**
     * 实际控制人证件号码，非必填，仅领款人为法人时填写
     * @param claimControlPersonCertid 实际控制人证件号码，非必填，仅领款人为法人时填写
     */
    public void setClaimControlPersonCertid(String claimControlPersonCertid) {
        this.claimControlPersonCertid = claimControlPersonCertid;
    }

    /**
     * 实际控制人证件有效起期，非必填,YYYY/MM/DD，仅领款人为法人时填写
     * @return claim_control_person_cert_begin_date 实际控制人证件有效起期，非必填,YYYY/MM/DD，仅领款人为法人时填写
     */
    public String getClaimControlPersonCertBeginDate() {
        return claimControlPersonCertBeginDate;
    }

    /**
     * 实际控制人证件有效起期，非必填,YYYY/MM/DD，仅领款人为法人时填写
     * @param claimControlPersonCertBeginDate 实际控制人证件有效起期，非必填,YYYY/MM/DD，仅领款人为法人时填写
     */
    public void setClaimControlPersonCertBeginDate(String claimControlPersonCertBeginDate) {
        this.claimControlPersonCertBeginDate = claimControlPersonCertBeginDate;
    }

    /**
     * 实际控制人证件有效终期,非必填,YYYY/MM/DD，仅领款人为法人时填写
     * @return claim_control_person_cert_end_date 实际控制人证件有效终期,非必填,YYYY/MM/DD，仅领款人为法人时填写
     */
    public String getClaimControlPersonCertEndDate() {
        return claimControlPersonCertEndDate;
    }

    /**
     * 实际控制人证件有效终期,非必填,YYYY/MM/DD，仅领款人为法人时填写
     * @param claimControlPersonCertEndDate 实际控制人证件有效终期,非必填,YYYY/MM/DD，仅领款人为法人时填写
     */
    public void setClaimControlPersonCertEndDate(String claimControlPersonCertEndDate) {
        this.claimControlPersonCertEndDate = claimControlPersonCertEndDate;
    }

    /**
     * 实际控制人移动电话,非必填，仅领款人为法人时填写
     * @return claim_control_person_mobile 实际控制人移动电话,非必填，仅领款人为法人时填写
     */
    public String getClaimControlPersonMobile() {
        return claimControlPersonMobile;
    }

    /**
     * 实际控制人移动电话,非必填，仅领款人为法人时填写
     * @param claimControlPersonMobile 实际控制人移动电话,非必填，仅领款人为法人时填写
     */
    public void setClaimControlPersonMobile(String claimControlPersonMobile) {
        this.claimControlPersonMobile = claimControlPersonMobile;
    }

    /**
     * 代办人姓名，非必填，仅领款人为法人时填写
     * @return claim_representative_name 代办人姓名，非必填，仅领款人为法人时填写
     */
    public String getClaimRepresentativeName() {
        return claimRepresentativeName;
    }

    /**
     * 代办人姓名，非必填，仅领款人为法人时填写
     * @param claimRepresentativeName 代办人姓名，非必填，仅领款人为法人时填写
     */
    public void setClaimRepresentativeName(String claimRepresentativeName) {
        this.claimRepresentativeName = claimRepresentativeName;
    }

    /**
     * 代办人证件类型,非必填，仅领款人为法人时填写
     * @return claim_representative_certType 代办人证件类型,非必填，仅领款人为法人时填写
     */
    public String getClaimRepresentativeCerttype() {
        return claimRepresentativeCerttype;
    }

    /**
     * 代办人证件类型,非必填，仅领款人为法人时填写
     * @param claimRepresentativeCerttype 代办人证件类型,非必填，仅领款人为法人时填写
     */
    public void setClaimRepresentativeCerttype(String claimRepresentativeCerttype) {
        this.claimRepresentativeCerttype = claimRepresentativeCerttype;
    }

    /**
     * 代办人证件号,非必填，仅领款人为法人时填写
     * @return claim_representative_certId 代办人证件号,非必填，仅领款人为法人时填写
     */
    public String getClaimRepresentativeCertid() {
        return claimRepresentativeCertid;
    }

    /**
     * 代办人证件号,非必填，仅领款人为法人时填写
     * @param claimRepresentativeCertid 代办人证件号,非必填，仅领款人为法人时填写
     */
    public void setClaimRepresentativeCertid(String claimRepresentativeCertid) {
        this.claimRepresentativeCertid = claimRepresentativeCertid;
    }

    /**
     * 代办人证件有效起期,非必填，YYYY/MM/DD，仅领款人为法人时填写
     * @return claim_representative_cert_begin_date 代办人证件有效起期,非必填，YYYY/MM/DD，仅领款人为法人时填写
     */
    public String getClaimRepresentativeCertBeginDate() {
        return claimRepresentativeCertBeginDate;
    }

    /**
     * 代办人证件有效起期,非必填，YYYY/MM/DD，仅领款人为法人时填写
     * @param claimRepresentativeCertBeginDate 代办人证件有效起期,非必填，YYYY/MM/DD，仅领款人为法人时填写
     */
    public void setClaimRepresentativeCertBeginDate(String claimRepresentativeCertBeginDate) {
        this.claimRepresentativeCertBeginDate = claimRepresentativeCertBeginDate;
    }

    /**
     * 代办人证件有效终期,非必填，YYYY/MM/DD，仅领款人为法人时填写
     * @return claim_representative_cert_end_date 代办人证件有效终期,非必填，YYYY/MM/DD，仅领款人为法人时填写
     */
    public String getClaimRepresentativeCertEndDate() {
        return claimRepresentativeCertEndDate;
    }

    /**
     * 代办人证件有效终期,非必填，YYYY/MM/DD，仅领款人为法人时填写
     * @param claimRepresentativeCertEndDate 代办人证件有效终期,非必填，YYYY/MM/DD，仅领款人为法人时填写
     */
    public void setClaimRepresentativeCertEndDate(String claimRepresentativeCertEndDate) {
        this.claimRepresentativeCertEndDate = claimRepresentativeCertEndDate;
    }

    /**
     * 领款人证件有效起期,当赔付金额大于等于1万元，必录，文本格式，YYYY/MM/DD，10位
     * @return claim_payeeCertBeginDate 领款人证件有效起期,当赔付金额大于等于1万元，必录，文本格式，YYYY/MM/DD，10位
     */
    public String getClaimPayeecertbegindate() {
        return claimPayeecertbegindate;
    }

    /**
     * 领款人证件有效起期,当赔付金额大于等于1万元，必录，文本格式，YYYY/MM/DD，10位
     * @param claimPayeecertbegindate 领款人证件有效起期,当赔付金额大于等于1万元，必录，文本格式，YYYY/MM/DD，10位
     */
    public void setClaimPayeecertbegindate(String claimPayeecertbegindate) {
        this.claimPayeecertbegindate = claimPayeecertbegindate;
    }

    /**
     * 领款人证件有效终期,当赔付金额大于等于1万元，必录，文本格式，YYYY/MM/DD，10位
     * @return claim_payeeCertEndDate 领款人证件有效终期,当赔付金额大于等于1万元，必录，文本格式，YYYY/MM/DD，10位
     */
    public String getClaimPayeecertenddate() {
        return claimPayeecertenddate;
    }

    /**
     * 领款人证件有效终期,当赔付金额大于等于1万元，必录，文本格式，YYYY/MM/DD，10位
     * @param claimPayeecertenddate 领款人证件有效终期,当赔付金额大于等于1万元，必录，文本格式，YYYY/MM/DD，10位
     */
    public void setClaimPayeecertenddate(String claimPayeecertenddate) {
        this.claimPayeecertenddate = claimPayeecertenddate;
    }

    /**
     * 领款人与主被保险人的关系，赔付金额大于等于1万元时必录，默认401-本人
     * @return claim_payee_mainPerson_relation 领款人与主被保险人的关系，赔付金额大于等于1万元时必录，默认401-本人
     */
    public String getClaimPayeeMainpersonRelation() {
        return claimPayeeMainpersonRelation;
    }

    /**
     * 领款人与主被保险人的关系，赔付金额大于等于1万元时必录，默认401-本人
     * @param claimPayeeMainpersonRelation 领款人与主被保险人的关系，赔付金额大于等于1万元时必录，默认401-本人
     */
    public void setClaimPayeeMainpersonRelation(String claimPayeeMainpersonRelation) {
        this.claimPayeeMainpersonRelation = claimPayeeMainpersonRelation;
    }

    /**
     * 受益人与主被保险人的关系，赔付金额大于等于1万元时必录，默认401-本人
     * @return claim_beneficiary_mainPerson_relation 受益人与主被保险人的关系，赔付金额大于等于1万元时必录，默认401-本人
     */
    public String getClaimBeneficiaryMainpersonRelation() {
        return claimBeneficiaryMainpersonRelation;
    }

    /**
     * 受益人与主被保险人的关系，赔付金额大于等于1万元时必录，默认401-本人
     * @param claimBeneficiaryMainpersonRelation 受益人与主被保险人的关系，赔付金额大于等于1万元时必录，默认401-本人
     */
    public void setClaimBeneficiaryMainpersonRelation(String claimBeneficiaryMainpersonRelation) {
        this.claimBeneficiaryMainpersonRelation = claimBeneficiaryMainpersonRelation;
    }

    /**
     * 领款人经常居住地
     * @return claim_often_live_address 领款人经常居住地
     */
    public String getClaimOftenLiveAddress() {
        return claimOftenLiveAddress;
    }

    /**
     * 领款人经常居住地
     * @param claimOftenLiveAddress 领款人经常居住地
     */
    public void setClaimOftenLiveAddress(String claimOftenLiveAddress) {
        this.claimOftenLiveAddress = claimOftenLiveAddress;
    }

    /**
     * 银行类型代码
     * @return claim_bank_type_code 银行类型代码
     */
    public String getClaimBankTypeCode() {
        return claimBankTypeCode;
    }

    /**
     * 银行类型代码
     * @param claimBankTypeCode 银行类型代码
     */
    public void setClaimBankTypeCode(String claimBankTypeCode) {
        this.claimBankTypeCode = claimBankTypeCode;
    }

    /**
     * 开户行区域编码
     * @return claim_bank_area_code 开户行区域编码
     */
    public String getClaimBankAreaCode() {
        return claimBankAreaCode;
    }

    /**
     * 开户行区域编码
     * @param claimBankAreaCode 开户行区域编码
     */
    public void setClaimBankAreaCode(String claimBankAreaCode) {
        this.claimBankAreaCode = claimBankAreaCode;
    }

    /**
     * 0: 对公 1：对私
     * @return claim_pay_obj 0: 对公 1：对私
     */
    public Integer getClaimPayObj() {
        return claimPayObj;
    }

    /**
     * 0: 对公 1：对私
     * @param claimPayObj 0: 对公 1：对私
     */
    public void setClaimPayObj(Integer claimPayObj) {
        this.claimPayObj = claimPayObj;
    }

    /**
     * 永诚主共从共赔付比例 如 ：70:30
     * @return claim_yc_comRatio 永诚主共从共赔付比例 如 ：70:30
     */
    public String getClaimYcComratio() {
        return claimYcComratio;
    }

    /**
     * 永诚主共从共赔付比例 如 ：70:30
     * @param claimYcComratio 永诚主共从共赔付比例 如 ：70:30
     */
    public void setClaimYcComratio(String claimYcComratio) {
        this.claimYcComratio = claimYcComratio;
    }

    /**
     * 案件结论，1-正常赔付，0-拒赔
     * @return claim_conclusion 案件结论，1-正常赔付，0-拒赔
     */
    public Integer getClaimConclusion() {
        return claimConclusion;
    }

    /**
     * 案件结论，1-正常赔付，0-拒赔
     * @param claimConclusion 案件结论，1-正常赔付，0-拒赔
     */
    public void setClaimConclusion(Integer claimConclusion) {
        this.claimConclusion = claimConclusion;
    }

    /**
     * 长安推送状态(1初始状态 4推送成功 -1推送失败, 10失败(中银), 11成功(中银))
     * @return claim_ca_status 长安推送状态(1初始状态 4推送成功 -1推送失败, 10失败(中银), 11成功(中银))
     */
    public Integer getClaimCaStatus() {
        return claimCaStatus;
    }

    /**
     * 长安推送状态(1初始状态 4推送成功 -1推送失败, 10失败(中银), 11成功(中银))
     * @param claimCaStatus 长安推送状态(1初始状态 4推送成功 -1推送失败, 10失败(中银), 11成功(中银))
     */
    public void setClaimCaStatus(Integer claimCaStatus) {
        this.claimCaStatus = claimCaStatus;
    }

    /**
     * 长安推送错误原因
     * @return claim_ca_memo 长安推送错误原因
     */
    public String getClaimCaMemo() {
        return claimCaMemo;
    }

    /**
     * 长安推送错误原因
     * @param claimCaMemo 长安推送错误原因
     */
    public void setClaimCaMemo(String claimCaMemo) {
        this.claimCaMemo = claimCaMemo;
    }

    /**
     * 太保财批次号
     * @return claim_bbrpc 太保财批次号
     */
    public Integer getClaimBbrpc() {
        return claimBbrpc;
    }

    /**
     * 太保财批次号
     * @param claimBbrpc 太保财批次号
     */
    public void setClaimBbrpc(Integer claimBbrpc) {
        this.claimBbrpc = claimBbrpc;
    }

    /**
     * 瑞泰专用 公共账户金额
     * @return claim_public_amount 瑞泰专用 公共账户金额
     */
    public BigDecimal getClaimPublicAmount() {
        return claimPublicAmount;
    }

    /**
     * 瑞泰专用 公共账户金额
     * @param claimPublicAmount 瑞泰专用 公共账户金额
     */
    public void setClaimPublicAmount(BigDecimal claimPublicAmount) {
        this.claimPublicAmount = claimPublicAmount;
    }

    /**
     * 瑞泰专用 公共账户余额 
     * @return claim_public_account_balance 瑞泰专用 公共账户余额 
     */
    public BigDecimal getClaimPublicAccountBalance() {
        return claimPublicAccountBalance;
    }

    /**
     * 瑞泰专用 公共账户余额 
     * @param claimPublicAccountBalance 瑞泰专用 公共账户余额 
     */
    public void setClaimPublicAccountBalance(BigDecimal claimPublicAccountBalance) {
        this.claimPublicAccountBalance = claimPublicAccountBalance;
    }

    /**
     * 瑞泰专用 个人账户赔付金额
     * @return claim_personal_amount 瑞泰专用 个人账户赔付金额
     */
    public BigDecimal getClaimPersonalAmount() {
        return claimPersonalAmount;
    }

    /**
     * 瑞泰专用 个人账户赔付金额
     * @param claimPersonalAmount 瑞泰专用 个人账户赔付金额
     */
    public void setClaimPersonalAmount(BigDecimal claimPersonalAmount) {
        this.claimPersonalAmount = claimPersonalAmount;
    }

    /**
     * 湖南专用，默认不推送
     * @return claim_hn_ispush 湖南专用，默认不推送
     */
    public Integer getClaimHnIspush() {
        return claimHnIspush;
    }

    /**
     * 湖南专用，默认不推送
     * @param claimHnIspush 湖南专用，默认不推送
     */
    public void setClaimHnIspush(Integer claimHnIspush) {
        this.claimHnIspush = claimHnIspush;
    }

    /**
     * 
     * @return claim_tpastatus_sign 
     */
    public String getClaimTpastatusSign() {
        return claimTpastatusSign;
    }

    /**
     * 
     * @param claimTpastatusSign 
     */
    public void setClaimTpastatusSign(String claimTpastatusSign) {
        this.claimTpastatusSign = claimTpastatusSign;
    }

    /**
     * 状态变更操作人
     * @return claim_statuschange_user 状态变更操作人
     */
    public String getClaimStatuschangeUser() {
        return claimStatuschangeUser;
    }

    /**
     * 状态变更操作人
     * @param claimStatuschangeUser 状态变更操作人
     */
    public void setClaimStatuschangeUser(String claimStatuschangeUser) {
        this.claimStatuschangeUser = claimStatuschangeUser;
    }

    /**
     * 状态变更时间
     * @return claim_statuschange_date 状态变更时间
     */
    public Date getClaimStatuschangeDate() {
        return claimStatuschangeDate;
    }

    /**
     * 状态变更时间
     * @param claimStatuschangeDate 状态变更时间
     */
    public void setClaimStatuschangeDate(Date claimStatuschangeDate) {
        this.claimStatuschangeDate = claimStatuschangeDate;
    }

    /**
     * 山东国网流水号
     * @return claim_guowang_serial_num 山东国网流水号
     */
    public String getClaimGuowangSerialNum() {
        return claimGuowangSerialNum;
    }

    /**
     * 山东国网流水号
     * @param claimGuowangSerialNum 山东国网流水号
     */
    public void setClaimGuowangSerialNum(String claimGuowangSerialNum) {
        this.claimGuowangSerialNum = claimGuowangSerialNum;
    }

    /**
     * 支付时间
     * @return claim_pay_time 支付时间
     */
    public Date getClaimPayTime() {
        return claimPayTime;
    }

    /**
     * 支付时间
     * @param claimPayTime 支付时间
     */
    public void setClaimPayTime(Date claimPayTime) {
        this.claimPayTime = claimPayTime;
    }

    /**
     * 影像件地址
     * @return ImagingPath 影像件地址
     */
    public String getImagingpath() {
        return imagingpath;
    }

    /**
     * 影像件地址
     * @param imagingpath 影像件地址
     */
    public void setImagingpath(String imagingpath) {
        this.imagingpath = imagingpath;
    }

    /**
     * 拒赔原因，整笔案件的，当结论为拒赔时，该字段将存放拒赔原因
     * @return claim_un_compensate_cause 拒赔原因，整笔案件的，当结论为拒赔时，该字段将存放拒赔原因
     */
    public String getClaimUnCompensateCause() {
        return claimUnCompensateCause;
    }

    /**
     * 拒赔原因，整笔案件的，当结论为拒赔时，该字段将存放拒赔原因
     * @param claimUnCompensateCause 拒赔原因，整笔案件的，当结论为拒赔时，该字段将存放拒赔原因
     */
    public void setClaimUnCompensateCause(String claimUnCompensateCause) {
        this.claimUnCompensateCause = claimUnCompensateCause;
    }

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table tb_over_claim
     *
     * @mbg.generated
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", claimId=").append(claimId);
        sb.append(", claimBatchCode=").append(claimBatchCode);
        sb.append(", claimGroupPolicy=").append(claimGroupPolicy);
        sb.append(", claimClaimCode=").append(claimClaimCode);
        sb.append(", claimClaimCodeTb=").append(claimClaimCodeTb);
        sb.append(", claimClaimStatus=").append(claimClaimStatus);
        sb.append(", claimCorpCode=").append(claimCorpCode);
        sb.append(", claimPersonCertid=").append(claimPersonCertid);
        sb.append(", claimPolicy=").append(claimPolicy);
        sb.append(", claimPolicyDate=").append(claimPolicyDate);
        sb.append(", claimPolicyType=").append(claimPolicyType);
        sb.append(", claimAccidentNature=").append(claimAccidentNature);
        sb.append(", claimOutPass=").append(claimOutPass);
        sb.append(", claimPayeeName=").append(claimPayeeName);
        sb.append(", claimPayeeCertid=").append(claimPayeeCertid);
        sb.append(", claimPayeeGender=").append(claimPayeeGender);
        sb.append(", claimPersonMobile=").append(claimPersonMobile);
        sb.append(", claimPayeePayType=").append(claimPayeePayType);
        sb.append(", claimPayeeAccount=").append(claimPayeeAccount);
        sb.append(", claimBankCode=").append(claimBankCode);
        sb.append(", claimApplyAmt=").append(claimApplyAmt);
        sb.append(", claimAbtmAmt=").append(claimAbtmAmt);
        sb.append(", claimCompensateAmt=").append(claimCompensateAmt);
        sb.append(", claimChangeAmt=").append(claimChangeAmt);
        sb.append(", claimEndDate=").append(claimEndDate);
        sb.append(", claimTypeCode=").append(claimTypeCode);
        sb.append(", claimCorpName=").append(claimCorpName);
        sb.append(", claimMainPersonName=").append(claimMainPersonName);
        sb.append(", claimMainPersonCertid=").append(claimMainPersonCertid);
        sb.append(", claimBankName=").append(claimBankName);
        sb.append(", claimAdjustmentGuid=").append(claimAdjustmentGuid);
        sb.append(", claimBillAmt=").append(claimBillAmt);
        sb.append(", claimCreatetime=").append(claimCreatetime);
        sb.append(", claimUpdatetime=").append(claimUpdatetime);
        sb.append(", claimStatus=").append(claimStatus);
        sb.append(", claimMemo=").append(claimMemo);
        sb.append(", claimTpaStatus=").append(claimTpaStatus);
        sb.append(", claimPayStatus=").append(claimPayStatus);
        sb.append(", claimheadnumber=").append(claimheadnumber);
        sb.append(", claimReportDate=").append(claimReportDate);
        sb.append(", claimBranchCode=").append(claimBranchCode);
        sb.append(", claimBranchName=").append(claimBranchName);
        sb.append(", tbclaimstatus=").append(tbclaimstatus);
        sb.append(", claimSignDate=").append(claimSignDate);
        sb.append(", claimPayeeProperty=").append(claimPayeeProperty);
        sb.append(", claimRelation=").append(claimRelation);
        sb.append(", claimNationality=").append(claimNationality);
        sb.append(", claimProfessional=").append(claimProfessional);
        sb.append(", claimWorkScope=").append(claimWorkScope);
        sb.append(", claimControlPersonType=").append(claimControlPersonType);
        sb.append(", claimControlPersonName=").append(claimControlPersonName);
        sb.append(", claimControlPersonCerttype=").append(claimControlPersonCerttype);
        sb.append(", claimControlPersonCertid=").append(claimControlPersonCertid);
        sb.append(", claimControlPersonCertBeginDate=").append(claimControlPersonCertBeginDate);
        sb.append(", claimControlPersonCertEndDate=").append(claimControlPersonCertEndDate);
        sb.append(", claimControlPersonMobile=").append(claimControlPersonMobile);
        sb.append(", claimRepresentativeName=").append(claimRepresentativeName);
        sb.append(", claimRepresentativeCerttype=").append(claimRepresentativeCerttype);
        sb.append(", claimRepresentativeCertid=").append(claimRepresentativeCertid);
        sb.append(", claimRepresentativeCertBeginDate=").append(claimRepresentativeCertBeginDate);
        sb.append(", claimRepresentativeCertEndDate=").append(claimRepresentativeCertEndDate);
        sb.append(", claimPayeecertbegindate=").append(claimPayeecertbegindate);
        sb.append(", claimPayeecertenddate=").append(claimPayeecertenddate);
        sb.append(", claimPayeeMainpersonRelation=").append(claimPayeeMainpersonRelation);
        sb.append(", claimBeneficiaryMainpersonRelation=").append(claimBeneficiaryMainpersonRelation);
        sb.append(", claimOftenLiveAddress=").append(claimOftenLiveAddress);
        sb.append(", claimBankTypeCode=").append(claimBankTypeCode);
        sb.append(", claimBankAreaCode=").append(claimBankAreaCode);
        sb.append(", claimPayObj=").append(claimPayObj);
        sb.append(", claimYcComratio=").append(claimYcComratio);
        sb.append(", claimConclusion=").append(claimConclusion);
        sb.append(", claimCaStatus=").append(claimCaStatus);
        sb.append(", claimCaMemo=").append(claimCaMemo);
        sb.append(", claimBbrpc=").append(claimBbrpc);
        sb.append(", claimPublicAmount=").append(claimPublicAmount);
        sb.append(", claimPublicAccountBalance=").append(claimPublicAccountBalance);
        sb.append(", claimPersonalAmount=").append(claimPersonalAmount);
        sb.append(", claimHnIspush=").append(claimHnIspush);
        sb.append(", claimTpastatusSign=").append(claimTpastatusSign);
        sb.append(", claimStatuschangeUser=").append(claimStatuschangeUser);
        sb.append(", claimStatuschangeDate=").append(claimStatuschangeDate);
        sb.append(", claimGuowangSerialNum=").append(claimGuowangSerialNum);
        sb.append(", claimPayTime=").append(claimPayTime);
        sb.append(", imagingpath=").append(imagingpath);
        sb.append(", claimUnCompensateCause=").append(claimUnCompensateCause);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}
