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
@TableName("tb_over_project")
@Schema(description="项目表")
public class TbOverProject implements Serializable {

    @Schema(description = "主键")
    @TableId(value = "project_id", type = IdType.AUTO)
    private Long projectId;

    @Schema(description = "太享福理赔编号")
    @TableField("project_batch_code")
    private String projectBatchCode;

    @Schema(description = "团单号")
    @TableField("project_group_policy")
    private String projectGroupPolicy;

    @Schema(description = "个单号")
    @TableField("project_policy")
    private String projectPolicy;

    @Schema(description = "TPA赔案号")
    @TableField("project_claim_code")
    private String projectClaimCode;

    @Schema(description = "第三方赔案号，给太保的")
    @TableField("project_claim_code_tb")
    private String projectClaimCodeTb;

    @Schema(description = "发票号码")
    @TableField("project_bill_code")
    private String projectBillCode;

    @Schema(description = "费用项目编号，太保枚举")
    @TableField("project_cost_project_code")
    private String projectCostProjectCode;

    @Schema(description = "费用项目名称，太保枚举")
    @TableField("project_cost_project")
    private String projectCostProject;

    @Schema(description = "发票金额")
    @TableField("project_bill_amt")
    private BigDecimal projectBillAmt;

    @Schema(description = "自费")
    @TableField("project_self_pay_amt")
    private BigDecimal projectSelfPayAmt;

    @Schema(description = "部分自负")
    @TableField("project_self_cash_amt")
    private BigDecimal projectSelfCashAmt;

    @Schema(description = "医保报销，统筹支付")
    @TableField("project_plan_pay_amt")
    private BigDecimal projectPlanPayAmt;

    @Schema(description = "保险报销额度")
    @TableField("project_insu_pay_amt")
    private BigDecimal projectInsuPayAmt;

    @Schema(description = "第三方给付金额")
    @TableField("project_thirdParty_pay_amt")
    private BigDecimal projectThirdPartyPayAmt;

    @Schema(description = "手工扣费金额，调整金额")
    @TableField("project_change_amt")
    private BigDecimal projectChangeAmt;

    @Schema(description = "费用扣除原因代码")
    @TableField("project_amt_deduction_cause_code")
    private String projectAmtDeductionCauseCode;

    @Schema(description = "其他费用扣除原因")
    @TableField("project_other_amt_deduction_cause")
    private String projectOtherAmtDeductionCause;

    @Schema(description = "创建时间")
    @TableField("project_createTime")
    private Date projectCreateTime;

    @Schema(description = "修改时间")
    @TableField("project_updateTime")
    private Date projectUpdateTime;

    @Schema(description = "状态，默认1-正常，-1-作废")
    @TableField("project_status")
    private Integer projectStatus;

    @Schema(description = "")
    @TableField("project_memo")
    private String projectMemo;

    @Schema(description = "发票id")
    @TableField("bill_id")
    private Long billId;

    @Schema(description = "不合理费用")
    @TableField("not_reasonable_amount")
    private BigDecimal notReasonableAmount;

    @Schema(description = "合理金额")
    @TableField("project_reasonable_amount")
    private BigDecimal projectReasonableAmount;

    @Schema(description = "扣除比例")
    @TableField("project_deduction_radio")
    private BigDecimal projectDeductionRadio;

    @Schema(description = "扣除金额")
    @TableField("project_deduction_amount")
    private BigDecimal projectDeductionAmount;

    @Schema(description = "备注")
    @TableField("project_remark")
    private String projectRemark;

    @Schema(description = "收据费用流水号")
    @TableField("serial_no")
    private Long serialNo;
}
