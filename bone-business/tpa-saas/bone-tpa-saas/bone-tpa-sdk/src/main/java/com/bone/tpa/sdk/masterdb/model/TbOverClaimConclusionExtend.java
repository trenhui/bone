package com.bone.tpa.sdk.masterdb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 *
 */
@Data
public class TbOverClaimConclusionExtend implements Serializable {

    @TableId(value="id",type = IdType.AUTO)
    private Long id;

    /**
     * 赔案号
     *
     * @mbg.generated
     */
    @Schema(description = "赔案号")
    private String claimCode;

    /**
     * 责任代码
     *
     * @mbg.generated
     */
    @Schema(description = "责任代码")
    private String dutyCode;

    /**
     * 1正常 -1作废
     *
     * @mbg.generated
     */
    @Schema(description = "1正常 -1作废")
    private Byte claimStatus;

    /**
     * 1公账2个账区分标识
     *
     * @mbg.generated
     */
    @Schema(description = "1公账2个账区分标识")
    private String publicPersonalFlag;

    /**
     * 0正常 1删除
     *
     * @mbg.generated
     */
    @Schema(description = "0正常 1删除")
    private Byte deleted;

    /**
     * 创建时间
     *
     * @mbg.generated
     */
    @Schema(description = "创建时间")
    private Date createTime;

    /**
     * 修改时间
     *
     * @mbg.generated
     */
    @Schema(description = "修改时间")
    private Date updateTime;

    /**
     * 备注
     *
     * @mbg.generated
     */
    @Schema(description = "备注")
    private String memo;

    /**
     * tb_over_claim_conclusion表id
     *
     * @mbg.generated
     */
    @Schema(description = "责任id")
    private Long conclusionId;

    @Schema(description = "险种名称")
    @TableField("risk_name")
    private String riskName;

    @Schema(description = "0:医疗型；1:津贴型；2:给付型")
    private Integer conclusionType;
    @Schema(description = "责任扣减方式 1直付 2TPA")
    private Integer deductionType;
    @Schema(description = "扣减保单号")
    private String deductGroupPolicy;
    @Schema(description = "累计免赔额")
    @TableField("sum_abtm_amt")
    private BigDecimal sumAbtmAmt;

    @Schema(description = "合理比例")
    @TableField("reasonable_ratio")
    private BigDecimal reasonableRatio;
    @Schema(description = "部分自费比例")
    @TableField("partial_self_payment_ratio")
    private BigDecimal partialSelfPaymentRatio;
    @Schema(description = "自费比例")
    @TableField("self_payment_ratio")
    private BigDecimal selfPaymentRatio;
    @Schema(description = "是否承担合理 0否 1是")
    @TableField("is_cover_reasonable")
    private Integer isCoverReasonable;
    @Schema(description = "是否承担部分自费 0否 1是")
    @TableField("is_cover_partial_self_payment")
    private Integer isCoverPartialSelfPayment;
    @Schema(description = "是否承担自费 0否 1是")
    @TableField("is_cover_self_payment")
    private Integer isCoverSelfPayment;

    @Schema(description = "责任配置模式 1.模式一 2.模式二")
    private Integer responsibilityPattern;

}
