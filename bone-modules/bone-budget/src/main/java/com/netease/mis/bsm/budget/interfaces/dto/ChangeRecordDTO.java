package com.netease.mis.bsm.budget.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.math.BigDecimal;

/**
* 预算变动记录DTO
*
* @author 梅山源码
*/
@Data
@Schema(description = "预算变动记录DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ChangeRecordDTO extends TenantAbstractDTO<Long> {

        /**
         * 来源系统
         */
         @Schema(name = "来源系统")
        private String sourceSystem;
        /**
         * 单据类型
         */
         @Schema(name = "单据类型")
        private String docCategory;
        /**
         * 单据编号
         */
         @Schema(name = "单据编号")
        private String docNumber;
        /**
         * 单据ID
         */
         @Schema(name = "单据ID")
        private Long docId;
        /**
         * 单据行ID
         */
         @Schema(name = "单据行ID")
        private Long docLineId;
        /**
         * 状态提交、拒绝、关闭复审、还款复审
         */
         @Schema(name = "状态提交、拒绝、关闭复审、还款复审")
        private String status;
        /**
         * 预算类型项目/事前申请单
         */
         @Schema(name = "预算类型项目/事前申请单")
        private String budgetType;
        /**
         * 费用流/资金流
         */
         @Schema(name = "费用流/资金流")
        private String feeType;
        /**
         * 项目编号
         */
         @Schema(name = "项目编号")
        private String projectNumber;
        /**
         * 项目ID
         */
         @Schema(name = "项目ID")
        private Long projectId;
        /**
         * 子项目ID
         */
         @Schema(name = "子项目ID")
        private Long subProjectId;
        /**
         * 事件ID
         */
         @Schema(name = "事件ID")
        private Long eventId;
        /**
         * 事前申请单编号
         */
         @Schema(name = "事前申请单编号")
        private String requisitionNumber;
        /**
         * 事前申请单ID
         */
         @Schema(name = "事前申请单ID")
        private Long requisitionId;
        /**
         * mpc需求单id
         */
         @Schema(name = "mpc需求单id")
        private Long mpcRequisitionId;
        /**
         * mpc需求单号
         */
         @Schema(name = "mpc需求单号")
        private String mpcRequisitionNumber;
        /**
         * 单据行关联单据类型
         */
         @Schema(name = "单据行关联单据类型")
        private String refDocType;
        /**
         * 单据行关联单据号
         */
         @Schema(name = "单据行关联单据号")
        private String refDocNumber;
        /**
         * 单据行关联单据号
         */
         @Schema(name = "单据行关联单据号")
        private Long refDocId;
        /**
         * 单据行关联单据行ID
         */
         @Schema(name = "单据行关联单据行ID")
        private Long refLineId;
        /**
         * 维值1
         */
         @Schema(name = "维值1")
        private Long dim1ValueId;
        /**
         * 维值2
         */
         @Schema(name = "维值2")
        private Long dim2ValueId;
        /**
         * 维值3
         */
         @Schema(name = "维值3")
        private Long dim3ValueId;
        /**
         * 维值4
         */
         @Schema(name = "维值4")
        private Long dim4ValueId;
        /**
         * 维值5
         */
         @Schema(name = "维值5")
        private Long dim5ValueId;
        /**
         * 维值6
         */
         @Schema(name = "维值6")
        private Long dim6ValueId;
        /**
         * 维值7
         */
         @Schema(name = "维值7")
        private Long dim7ValueId;
        /**
         * 维值8
         */
         @Schema(name = "维值8")
        private Long dim8ValueId;
        /**
         * 维值9
         */
         @Schema(name = "维值9")
        private Long dim9ValueId;
        /**
         * 年度
         */
         @Schema(name = "年度")
        private Long year;
        /**
         * 周期
         */
         @Schema(name = "周期")
        private String period;
        /**
         * 预算变动类别冻结、解冻、消耗、释放、人为调整
         */
         @Schema(name = "预算变动类别冻结、解冻、消耗、释放、人为调整")
        private String budgetChangeType;
        /**
         * 币种
         */
         @Schema(name = "币种")
        private String currencyCode;
        /**
         * 单据金额(单据币种金额)
         */
         @Schema(name = "单据金额(单据币种金额)")
        private BigDecimal docAmount;
        /**
         * 当前冻结金额
         */
         @Schema(name = "当前冻结金额")
        private BigDecimal currentFreezeAmount;
        /**
         * 当前消耗金额
         */
         @Schema(name = "当前消耗金额")
        private BigDecimal currentUseAmount;
        /**
         * 已冻结金额
         */
         @Schema(name = "已冻结金额")
        private BigDecimal freezeAmount;
        /**
         * 已消耗金额(资金流)
         */
         @Schema(name = "已消耗金额(资金流)")
        private BigDecimal consumedAmountCash;
        /**
         * 已消耗金额(费用流)
         */
         @Schema(name = "已消耗金额(费用流)")
        private BigDecimal consumedAmountCost;
        /**
         * 剩余可用金额
         */
         @Schema(name = "剩余可用金额")
        private BigDecimal availableAmount;
        /**
         * 变动记录人
         */
         @Schema(name = "变动记录人")
        private String changeRecorder;
        /**
         * 变动记录说明
         */
         @Schema(name = "变动记录说明")
        private String changeDescription;
        /**
         * 预算变动记录状态C正常，R冲销
         */
         @Schema(name = "预算变动记录状态C正常，R冲销")
        private String recordStatus;
        /**
         * 删除标志，1删除，0未删除
         */
         @Schema(name = "删除标志，1删除，0未删除")
        private String delFlag;
        /**
         * 是否最后一次更新记录，0：否，1：是
         */
         @Schema(name = "是否最后一次更新记录，0：否，1：是")
        private String lastRecordFlag;
        /**
         * 处理批次ID，每一整单一个操作作为一个批次
         */
         @Schema(name = "处理批次ID，每一整单一个操作作为一个批次")
        private String batchId;
}
