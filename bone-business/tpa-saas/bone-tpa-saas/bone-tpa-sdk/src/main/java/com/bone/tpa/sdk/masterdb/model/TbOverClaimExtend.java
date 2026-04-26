package com.bone.tpa.sdk.masterdb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 * 理赔结案扩展表
 * </p>
 *
 * @author wangxiaokun
 * @since 2022-02-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tb_over_claim_extend")
@Schema(description="理赔结案扩展表")
public class TbOverClaimExtend implements Serializable {

    @Schema(description = "主键")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "赔案号")
    @TableField("claim_code")
    private String claimCode;

    @Schema(description = "主险人身份证有效起期")
    @TableField("claim_main_person_begindate")
    private Date claimMainPersonBegindate;

    @Schema(description = "主险人身份证有效止期")
    @TableField("claim_main_person_enddate")
    private Date claimMainPersonEnddate;

    @Schema(description = "赔案影像件是否为tif 0否,1是")
    @TableField("claim_image_is_tif")
    private Byte claimImageIsTif;

    @Schema(description = "关联公账赔案号")
    @TableField("claim_associated_public_code")
    private String claimAssociatedPublicCode;

    @Schema(description = "领款人类型")
    @TableField("claim_payee_type")
    private String claimPayeeType;

    @Schema(description = "复制赔案标记 (0普通赔案、1复制赔案)")
    @TableField("copy_case_flag")
    private Byte copyCaseFlag;

    @Schema(description = "主险人手机号")
    @TableField("claim_main_person_mobile")
    private String claimMainPersonMobile;

    @Schema(description = "审核人")
    @TableField("reviewed_by")
    private String reviewedBy;

    @Schema(description = "领款人开户省")
    @TableField("claim_payee_bank_province")
    private String claimPayeeBankProvince;

    @Schema(description = "领款人开户市")
    @TableField("claim_payee_bank_city")
    private String claimPayeeBankCity;

    @Schema(description = "领款人证件类型")
    @TableField("claim_payee_cert_type")
    private String claimPayeeCertType;

    @Schema(description = "领款人与主被保人关系")
    @TableField("claim_payee_relation")
    private String claimPayeeRelation;

    @Schema(description = "领款人行业名称")
    @TableField("payment_industry_name")
    private String paymentIndustryName;

    @Schema(description = "领款人行业类别")
    @TableField("payment_industry_category")
    private String paymentIndustryCategory;

    @Schema(description = "年收入")
    @TableField("annual_income")
    private String annualIncome;

    @Schema(description = "执照有效起期")
    @TableField("payment_license_valid_start")
    private String paymentLicenseValidStart;

    @Schema(description = "执照有效止期")
    @TableField("payment_license_valid_end")
    private String paymentLicenseValidEnd;

    @Schema(description = "行业代码")
    @TableField("industry_code")
    private String industryCode;

    @Schema(description = "行业")
    @TableField("industry")
    private String industry;

    @Schema(description = "注册资本金")
    @TableField("registered_capital")
    private String registeredCapital;

    @Schema(description = "注册资本金币种")
    @TableField("capital_currency")
    private String capitalCurrency;

    @Schema(description = "控股人姓名")
    @TableField("claim_holder_person_name")
    private String claimHolderPersonName;

    @Schema(description = "控股人证件类型")
    @TableField("claim_holder_person_certType")
    private String claimHolderPersonCertType;

    @Schema(description = "控股人证件号码")
    @TableField("claim_holder_person_certId")
    private String claimHolderPersonCertId;

    @Schema(description = "控股人证件有效起期")
    @TableField("claim_holder_person_cert_begin_date")
    private String claimHolderPersonCertBeginDate;

    @Schema(description = "控股人证件有效止期")
    @TableField("claim_holder_person_cert_end_date")
    private String claimHolderPersonCertEndDate;

    @Schema(description = "分单号")
    @TableField("insured_num")
    private String insuredNum;

    @Schema(description = "出险人电话")
    @TableField("phone")
    private String phone;

    @Schema(description = "出险人国籍")
    @TableField("nationality")
    private String nationality;

    @Schema(description = "出险人职业代码")
    @TableField("job")
    private String job;

    @Schema(description = "出险人职业代码版本号")
    @TableField("job_version_no")
    private String jobVersionNo;

    @Schema(description = "受益人国籍")
    @TableField("bene_nationality")
    private String beneNationality;

    @Schema(description = "受益人职业")
    @TableField("bene_job")
    private String beneJob;

    @Schema(description = "受益人职业代码版本号")
    @TableField("bene_job_version_no")
    private String beneJobVersionNo;

    @Schema(description = "单位营业场所")
    @TableField("company_address")
    private String companyAddress;

    @Schema(description = "单位营业范围")
    @TableField("company_busi_scope")
    private String companyBusiScope;

    @Schema(description = "出险人证件有效起期")
    @TableField("claim_person_cert_begin_date")
    private Date claimPersonCertBeginDate;

    @Schema(description = "出险人证件有效止期")
    @TableField("claim_person_cert_end_date")
    private Date claimPersonCertEndDate;

    @Schema(description = "出险人性别")
    @TableField("claim_person_sex")
    private Integer claimPersonSex;

    @Schema(description = "出险人出生日期")
    @TableField("claim_person_birthday")
    private Date claimPersonBirthday;

    @Schema(description = "领款人手机号")
    @TableField("claim_payee_phone")
    private String claimPayeePhone;

    @Schema(description = "出险地点")
    @TableField("danger_place")
    private String dangerPlace;

    @Schema(description = "出险地点代码")
    @TableField("danger_area_code")
    private String dangerAreaCode;

    @Schema(description = "领款单位法人类型 1.法定代表人 2.负责人 3.控股股东/实际控股人")
    @TableField("claim_iegal_person_type")
    private Long claimIegalPersonType;

    @Schema(description = "单位证件类型 1-营业执照号码, 2-组织机构代码, 3-税务登记证号码, 4-其他, 5-社会信用代码, 7-社会团体法人登记证书, 8-事业单位法人证书, 6-纳税人识别号'")
    @TableField("company_certi_type")
    private Long companyCertiType;

    @Schema(description = "法人名称")
    @TableField("lpa_real_name")
    private String lpaRealName;

    @Schema(description = "法人证件类型")
    @TableField("lpa_certi_type")
    private String lpaCertiType;

    @Schema(description = "法人证件号码")
    @TableField("lpa_certi_code")
    private String lpaCertiCode;

    @Schema(description = "法人证件有效起期")
    @TableField("lpa_certi_start_date")
    private Date lpaCertiStartDate;

    @Schema(description = "法人证件有效止期")
    @TableField("lpa_certi_end_date")
    private Date lpaCertiEndDate;

    @Schema(description = "法人联系地址")
    @TableField("lpa_address")
    private String lpaAddress;

    @Schema(description = "单位证件类型描述")
    @TableField("company_certi_type_desc")
    private String companyCertiTypeDesc;

    @Schema(description = "受益人姓名")
    @TableField("bene_name")
    private String beneName;

    @Schema(description = "受益人证件类型")
    @TableField("bene_certi_type")
    private String beneCertiType;

    @Schema(description = "受益人证件号码")
    @TableField("bene_certi_code")
    private String beneCertiCode;

    @Schema(description = "受益人性别 1-男，2-女")
    @TableField("bene_gender")
    private Long beneGender;

    @Schema(description = "受益人证件有效起期")
    @TableField("bene_certi_start_date")
    private Date beneCertiStartDate;

    @Schema(description = "受益人证件有效止期")
    @TableField("bene_certi_end_date")
    private Date beneCertiEndDate;

    @Schema(description = "受益人联系电话")
    @TableField("bene_tel")
    private String beneTel;

    @Schema(description = "受益人联系地址")
    @TableField("bene_address")
    private String beneAddress;

    @Schema(description = "受益人与领款人关系")
    @TableField("payee_bene_relation")
    private String payeeBeneRelation;

    @Schema(description = "领款人联系地址")
    @TableField("payee_address")
    private String payeeAddress;

    @Schema(description = "领款人职业")
    @TableField("payee_job")
    private String payeeJob;

    @Schema(description = "领款人职业版本")
    @TableField("payee_job_version_no")
    private String payeeJobVersionNo;

    @Schema(description = "领款人国籍")
    @TableField("payee_nationality")
    private String payeeNationality;

    @Schema(description = "出险人地址")
    @TableField("claim_person_address")
    private String claimPersonAddress;


    @Schema(description = "单位名称")
    @TableField("company_name")
    private String companyName;

    @Schema(description = "单位证件号码")
    @TableField("company_certi_code")
    private String companyCertiCode;

    @Schema(description = "单位证件有效起期")
    @TableField("company_certi_start_date")
    private Date companyCertiStartDate;

    @Schema(description = "单位证件有效止期")
    @TableField("company_certi_end_date")
    private Date companyCertiEndDate;

    @Schema(description = "出险人与主被保人关系")
    @TableField("claim_zb_relation")
    private String claimZbRelation;

    @Schema(description = "计划名称")
    @TableField("plan_name")
    private String planName;

    @Schema(description = "例外原因")
    @TableField("exception_reason")
    private String exceptionReason;

    @Schema(description = "开户支行名称")
    @TableField("branch_bank_name")
    private String branchBankName;

    @Schema(description = "领款人类别")
    @TableField("payee_cate")
    private String payeeCate;

    @Schema(description = "医院所在省")
    @TableField("claim_hospital_province")
    private String claimHospitalProvince;

    @Schema(description = "医院所在市")
    @TableField("claim_hospital_city")
    private String claimHospitalCity;

    @Schema(description = "医院所在区")
    @TableField("claim_hospital_area")
    private String claimHospitalArea;

    @Schema(description = "医院详细地址")
    @TableField("claim_hospital_address")
    private String claimHospitalAddress;

    @Schema(description = "案件渠道")
    @TableField("claim_channel")
    private String claimChannel;


    @Schema(description = "事故描述")
    @TableField("accident_desc")
    private String accidentDesc;

    @Schema(description = "受益人与被保人关系")
    @TableField("claim_bene_relation")
    private String claimBeneRelation;

    @Schema(description = "受益人出生日期")
    @TableField("bene_birthday")
    private Date beneBirthday;

    @Schema(description = "领款人出生日期")
    @TableField("payee_birthday")
    private Date payeeBirthday;

    @Schema(description = "审核人")
    @TableField("audit_person")
    private String auditPerson;

    @Schema(description = "审核日期")
    @TableField("audit_date")
    private Date auditDate;

    @Schema(description = "复核人")
    @TableField("exam_person")
    private String examPerson;

    @Schema(description = "复核日期")
    @TableField("exam_date")
    private Date examDate;

    @Schema(description = "tpa批次号")
    @TableField("claim_batch_code_tpa")
    private String claimBatchCodeTpa;

    @Schema(description = "是否vip(0-否 1-是)")
    @TableField("is_vip")
    private Integer isVip;

    // @Schema(description = "申请类型")
    // private String claimApplyType;

    @Schema(description = "身体状况")
    private String physicalCondition;

    @Schema(description = "出险地点")
    private String accidentLocation;

    @Schema(description = "出险地点类型")
    private String accidentLocationType;

    @Schema(description = "出险地点类型")
    private String accidentLocationTypeText;

    @Schema(description = "出险地点（省/直辖市）")
    @TableField("accident_location_province")
    private String accidentLocationProvince;

    @Schema(description = "出险地点（市级）")
    @TableField("accident_location_city")
    private String accidentLocationCity;

    @Schema(description = "出险地点（县/区）")
    @TableField("accident_location_county")
    private String accidentLocationCounty;

    @Schema(description = "损伤外部原因")
    @TableField("external_cause_of_injury")
    private String externalCauseOfInjury;

    @Schema(description = "代理人身份")
    @TableField("agent_type")
    private String agentType;

    @Schema(description = "代理手续齐备")
    @TableField("is_formal_ities_complete")
    private String isFormalItiesComplete;

    @Schema(description = "证件核查情况")
    @TableField("is_identity_check")
    private String isIdentityCheck;

    @Schema(description = "第三人与被保险人关系")
    @TableField("pay_third_reason")
    private String payThirdReason;

    @Schema(description = "支付第三人理由")
    @TableField("return_reason")
    private String returnReason;

    @Schema(description = "赔案业务模式")
    private String claimBusinessMode;

    //上海新增三个字段,其中一个已经存在
    @Schema(description = "领款人与投保人关系")
    @TableField("claim_payee_holder_relation")
    private String claimPayeeHolderRelation;

    @Schema(description = "受益人与投保人关系")
    @TableField("claim_bene_holder_relation")
    private String claimBeneHolderRelation;

    @Schema(description = "关系类型（1-本人，10-连带，20-家属）")
    @TableField("relation_type")
    private Integer relationType;

    @Schema(description = "申请类型")
    private String claimApplyType;

    @Schema(description = "受益人属性")
    private String beneProperty;


    @Schema(description = "受益人与投保人关系")
    @TableField("case_source")
    private String caseSource;
    @Schema(description = "永诚赔付一级原因")
    private String cancelType;

    @Schema(description = "永诚赔付二级原因")
    private String cancelReasonCode;

    @Schema(description = "签收时间")
    @TableField("claim_sign_time")
    private Date claimSignTime;

    @Schema(description = "主险人地址")
    private String claimMainPersonAddress;

    @Schema(description = "领款人账户属性 1-存折 2-银行卡")
    private String claimPayeeBankAcctAttr;

    @Schema(description = "申请人与被保人的关系")
    private String applicantToInsuredRelation;

    @Schema(description = "主险人职业")
    private String claimMainPersonProfessional;

    @Schema(description = "vip等级")
    @TableField("vip_level")
    private Integer vipLevel;

    @Schema(description = "第三方案件号")
    private String thirdPartyCaseNo;

    @Schema(description = "首次报案标识 1-首次报案;0-非首次报案")
    @TableField("first_report_flag")
    private Integer firstReportFlag;

    @Schema(description = "业务类型（1、综合医疗 2、医疗基金）")
    public String claimBusinessType;

    @Schema(description = "是否需要合并复核 0-不需要；1-需要")
    @TableField("review_merge_flag")
    private Integer reviewMergeFlag;

    @Schema(description = "合并复核祖先赔案号/报案号")
    @TableField("review_merge_parent_claim_code")
    private String reviewMergeParentClaimCode;

    @Schema(description = "出险原因代码")
    @TableField("damage_code")
    private String damageCode;

    @Schema(description = "理赔类型")
    @TableField("compensate_type")
    private String compensateType;

    @Schema(description = "赔案类型")
    @TableField("claim_type")
    private String claimType;

    @Schema(description = "理赔申请人类型")
    @TableField("application_type")
    private String applicationType;

    @Schema(description = "估损类型")
    @TableField("loss_fee_type")
    private String lossFeeType;

    @Schema(description = "被保险人与投保人关系")
    @TableField("insured_relation")
    private String insuredRelation;

    @Schema(description = "支付对象说明")
    @TableField("payment_text")
    private String paymentText;

    @Schema(description = "支付人性质")
    private String payerCategory;

    @Schema(description = "补充资料原件状态: 1-待补充; 2-已补充; 3-无需补充")
    @TableField("rp_data_state")
    private String rpDataState;

    @Schema(description = "线下案件签收类型（线下票据、医保取数）")
    private String offlineSignType;

    @Schema(description = "是否进入复核;1-是/0-否")
    @TableField("review_flag")
    private String reviewFlag;

    @Schema(description = "处理类型: 18-永诚控额;")
    @TableField("handle_type")
    private String handleType;

    @Schema(description = "额度冻结入参")
    @TableField("freeze_amount_params")
    private String freezeAmountParams;
    @Schema(description = "是否存在受托人 0-否,1-是")
    @TableField("is_exist_trustee")
    private Integer isExistTrustee;

    @Schema(description = "主客户号")
    @TableField("main_customer_no")
    private String mainCustomerNo;

    @Schema(description = "受益人类别 0-身故受益人,1-生存受益人")
    @TableField("beneficiary_class")
    private Integer beneficiaryClass;

    @Schema(description = "1 -基金 2-企补")
    @TableField("policy_attribute")
    private Integer policyAttribute;

    @Schema(description = "冻结流水号")
    private String freezeSerialNo;

    @Schema(description = "冻结时间")
    private String freezeTime;

    @Schema(description = "死亡标识")
    private Integer dieFlag;

    @Schema(description = "主被保险人出生日期")
    private String claimMainPersonBirthday;

    @Schema(description = "主被保险人性别")
    private Integer claimMainPersonGender;

    @Schema(description = "主被保险人国籍")
    private String claimMainPersonNationality;

    @Schema(description = "主被保险人所在省代码")
    private String claimMainPersonProvinceCode;

    @Schema(description = "主被保险人所在市代码")
    private String claimMainPersonCityCode;

    @Schema(description = "主被保险人所在区县代码")
    private String claimMainPersonCountyCode;

    @Schema(description = "主被保险人证件有效期是否长期")
    private Integer claimMainPersonCertIsLongTerm;

    @Schema(description = "出险人证件有效期是否长期")
    private Integer claimPersonCertIsLongTerm;

    @Schema(description = "受益人证件有效期是否长期")
    private Integer beneCertIsLongTerm;

    @Schema(description = "受托人类型")
    private String assigneeType;

    @Schema(description = "受托人姓名")
    private String assigneeName;

    @Schema(description = "受托人性别")
    private String assigneeGender;

    @Schema(description = "受托人证件类型")
    private String assigneeCertType;

    @Schema(description = "受托人证件号")
    @TableField("assignee_cert_Id")
    private String assigneeCertId;

    @Schema(description = "受托人出生日期")
    private String assigneeBirthday;

    @Schema(description = "受托人证件有效期是否长期")
    private String assigneeCertIsLongTerm;

    @Schema(description = "受托人证件有效起期")
    private String assigneeCertStartDate;

    @Schema(description = "受托人证件有效止期")
    private String assigneeCertEndDate;

    @Schema(description = "与受托人关系")
    private String assigneeRelation;

    @Schema(description = "受托人国籍")
    private String assigneeNationality;

    @Schema(description = "受益人税收居民身份码值")
    private String beneTaxResidencyStatus;

    @Schema(description = "出险人国标职业代码")
    private String claimPersonJobCode;

    @Schema(description = "受益人所在省代码")
    private String beneProvinceCode;

    @Schema(description = "受益人所在市代码")
    private String beneCityCode;

    @Schema(description = "受益人所在区代码")
    private String beneCountyCode;

    @Schema(description = "出险人省代码")
    private String claimPersonProvinceCode;

    @Schema(description = "出险人市代码")
    private String claimPersonCityCode ;

    @Schema(description = "出险人区代码")
    private String claimPersonCountyCode;

    @Schema(description = "鉴定日期")
    private String claimAppraisalDate;

    @Schema(description = "特许案件标记(0-无,1-猝死,2-交通意外免责,3-阳性既往病史)")
    private Integer specialCaseMark;

    @Schema(description = "中邮流程版本 老系统: V1.0、新系统: V2.0")
    private String chinaPostVersion;
}
