package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;

/**
 * ss_claim_invoice DO
 *
 * @author 0
 */
@Table("ss_claim_invoice")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ClaimInvoice extends ExtraStoreBase<Long> {

    /**
     * 关联赔案id
     */
    private Long relatedId;
    /**
     * 唯一的uuid
     */
    private String invoiceUuid;
    /**
     * 票据号码
     */
    private String invoiceNo;
    /**
     * 电子票据代码
     */
    private String claimInvoiceId;

    /**
     * 录入方式
     */
    private String inputTypeCn;
    /**
     * 电子发票号
     */
    private String eInvoiceNo;
    /**
     * 票据验证码
     */
    private String verificationCode;

    /**
     * 发票验真结果code
     *
     */
    private String verifyValid;

    /**
     * 发票验真次数
     */
    private Integer verifyValidTimes;

    /**
     * 影像件是否被ocr处理
     * 0 没处理过 1 处理过了
     */
    private Integer ocrFlag;

    /**
     * 开票日期
     */
    private Date invoiceDate;
    /**
     * 医疗发票类型
     */
    private String invoiceType;

    /**
     * 票据类型，走选项集
     */
    private String billType;

    /**
     * 见枚举 InvoicePaperType
     * 电子或者纸质
     */
    private String paperType;

    /**
     * 票据类型中文
     */
    private String billTypeCn;
    /**
     * 就诊类型
     */
    private String visitType;

    /**
     * 就诊类型中文
     */
    private String visitTypeCn;
    /**
     * 出险原因
     */
    private String outInsureReason;
    /**
     * 出险原因中文
     */
    private String outInsureReasonCn;

    /**
     * 发票姓名
     */
    private String invoiceName;
    /**
     * 适用责任
     */
    private String relateLiability;
    /**
     * 医疗机构类型code
     */
    private String medicalInstitutionType;

    /**
     * 医疗机构类型中文
     */
    private String medicalInstitutionTypeCn;
    /**
     * 是否有医保
     */
    private Integer hasYb;
    /**
     * 医保类型
     */
    private String ybType;

    /**
     * 医保类型中文
     */
    private String ybTypeCn;

    /**
     * 医院code
     */
    private String hospitalCode;
    /**
     * 医院名称
     */
    private String hospitalName;
    /**
     * 医院等级
     */
    private String hospitalLevel;
    /**
     * 医院性质code
     */
    private String hospitalType;

    /**
     * 医院省市区
     */
    private String hospitalRegion;

    /**
     * 诊断code
     */
    private String diagnosis;

    /**
     * 诊断中文
     */
    private String diagnosisCn;
    /**
     * 是否重疾
     */
    private Integer severeFlag;
    /**
     * 重疾名称
     */
    private String severeName;

    /**
     * 重疾code
     */
    private String severeNameCode;

    /**
     *  是否慢病
     */
    private Integer chronicFlag;
    /**
     * 慢病名称ode
     */
    private String chronicName;
    /**
     * 慢病名称code
     */
    private String chronicCode;
    /**
     * 医院特征
     */
    private String hospitalProperties;
    /**
     * 发票备注
     */
    private String remark;
    /**
     * 就诊日期
     */
    @NotNull(message = "就诊日期不能为空")
    private Date visitDate;
    /**
     * 住院期间
     */
    private String hospitalPeriod;
    /**
     * 住院科别code
     */
    private String hospitalDepartment;

    /**
     * 住院科别名称
     */
    private String hospitalDepartmentName;
    /**
     * 住院天数
     */
    private Integer hospitalDays;
    /**
     * 津贴天数
     */
    private Integer subsidyDays;
    /**
     * 发票总费用
     */
    @NotNull(message = "发票总费用不能为空")
    @Positive(message = "发票总费用必须大于0")
    private BigDecimal totalAmount;

    /**
     * 医保基金总支付
     */
    private BigDecimal totalMedicalFundPayment;

    /**
     *其他基金金额
     * example : 12.23
     * 如果传入null，则设置成null
     */
    private BigDecimal otherFundAmount ;

    /**
     * 基本统筹金额
     */
    private BigDecimal basicPoolingAmount;
    /**
     * 统筹起付线
     */
    private BigDecimal poolingThreshold;
    /**
     * 统筹赔付比例（0-1小数形式）
     */
    private BigDecimal poolingReimbursementRate;
    /**
     * 自付一（医保内自付）
     */
    private BigDecimal selfPayPart1Amount;
    /**
     * 自付二（医保外自付）(乙类自付)
     */
    private BigDecimal selfPayPart2Amount;
    /**
     * 总自费金额
     */
    private BigDecimal totalSelfPayAmount;
    /**
     * 丙类自费（完全自费项目）
     */
    private BigDecimal classCSelfPayAmount;
    /**
     * 超限价自付（超医保限价部分）
     */
    private BigDecimal excessLimitSelfPayAmount;
    /**
     * 第三方已赔付金额
     */
    private BigDecimal thirdPartyPaidAmount;
    /**
     * 合理费用金额
     */
    private BigDecimal validAmount;
    /**
     * 不合理费用金额
     */
    private BigDecimal invalidAmount;

    /**
     * 个人账户
     */
    private String accountNo;


//    //发票下的，费用项目概况
//    private List<InvoiceProject> invoiceProjects;
}
