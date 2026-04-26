package com.bone.tpa.sdk.masterdb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @TableName tb_over_drug
 */

@Data
public class TbOverDrug implements Serializable {
    /**
     *
     */
    @TableId(value = "drugId", type = IdType.AUTO)
    private Integer drugId;

    /**
     * 太享福理赔编号
     */
    private String drugBatchCode;

    /**
     * TPA赔案号
     */
    private String drugClaimCode;

    /**
     * 第三方赔案号
     */
    private String drugClaimCodeTb;

    /**
     * 团单号
     */
    private String drugGroupPolicy;

    /**
     * 个单号
     */
    private String drugPolicy;

    /**
     * 发票号
     */
    private String drugBillCode;

    /**
     * 药品及诊疗项目
     */
    private String drugTreatmentProject;

    /**
     * 商品名，默认为空
     */
    private String drugGoodsName;

    /**
     * 剂型，参见枚举值，有则填无则为空
     */
    private String drugDosageForm;

    /**
     * ？
     */
    private String drugCostProjectCode;

    /**
     * 费用项目，必填,参见枚举值，长大N项目
     */
    private String drugCostProject;

    /**
     * 单价限额，为空
     */
    private Long drugOnePriceLimit;

    /**
     * 使用次数限定，为空
     */
    private String drugUseNumberLimit;

    /**
     * 备注，为空
     */
    private String drugNote;

    /**
     * 医保类别，参见枚举值
     */
    private String drugYbType;

    /**
     * 数量
     */
    private BigDecimal drugCount;

    /**
     * 单价
     */
    private BigDecimal drugOnePrice;

    /**
     * 金额
     */
    private BigDecimal drugAmt;

    /**
     * 自付比例
     */
    private String drugSincePayProportion;

    /**
     * 自付金额
     */
    private BigDecimal drugSincePayAmt;

    /**
     * 是否为责任费用扣除，必填，0-否1-是，根据保单规则，因保单规则而扣除费用的情况此处填写是，由于正常的医保条款（乙类、丙类）下扣除的费用则此处填否。
     */
    private Integer drugIsDutyDeduct;

    /**
     *
     */
    private Date drugCreatetime;

    /**
     *
     */
    private Date drugUpdatetime;

    /**
     * 状态，默认1-正常，-1作废
     */
    private Integer drugStatus;

    /**
     * 发票id
     */
    private String billId;

    /**
     * 扣费比例
     */
    private String drugDeductProportion;

    /**
     * 扣费金额
     */
    private BigDecimal drugDeductAmt;

    /**
     * 第三方金额
     */
    private BigDecimal drugThirdAmt;

    /**
     * 不合理金额
     */
    private BigDecimal drugUnreasonableAmt;

    /**
     *
     */
    private String drugMemo;

    /**
     * 统筹金额
     */
    private BigDecimal drugPlanPayAmt;
    /**
     * 项目表ID
     */
    private String projectId;
    /**
     * 剔费金额
     */
    private BigDecimal drugExclusionAmt;




    /**
     * 赔付金额
     */
    private BigDecimal drugCompensateAmt;
    /**
     * 部分自付
     */
    private BigDecimal partSelfPayAmt;

    /**
     * 是否医保;0-否;1-是
     */
    private Integer medicalInsurance;


    private static final long serialVersionUID = 1L;
}
