package com.netease.mis.bsm.budget.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
* 预算操作配置DTO
*
* @author 梅山源码
*/
@Data
@Schema(description = "预算操作配置DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class OperationConfigDTO extends TenantAbstractDTO<Long> {

        /**
         * 来源系统
         */
         @Schema(name = "来源系统")
        private String sourceSystem;
        /**
         * 单据类型
         */
         @Schema(name = "单据类型")
        private String docType;
        /**
         * 状态提交、审核通过/拒绝、退回/撤回/删除、报价确认、付款单PRE状态、新建、删除、关闭
         */
         @Schema(name = "状态提交、审核通过/拒绝、退回/撤回/删除、报价确认、付款单PRE状态、新建、删除、关闭")
        private String status;
        /**
         * 执行顺序
         */
         @Schema(name = "执行顺序")
        private Long orderNumber;
        /**
         * 关联单据类型
         */
         @Schema(name = "关联单据类型")
        private String refDocType;
        /**
         * 预算变动类型消耗、释放、冻结、解冻
         */
         @Schema(name = "预算变动类型消耗、释放、冻结、解冻")
        private String budgetChangeType;
        /**
         * 预算类型费用流、资金流
         */
         @Schema(name = "预算类型费用流、资金流")
        private String feeType;
        /**
         * 费用类型待摊、费用
         */
         @Schema(name = "费用类型待摊、费用")
        private String expenseType;
        /**
         * 金额来源总金额、行金额
         */
         @Schema(name = "金额来源总金额、行金额")
        private String amountSource;
        /**
         * 删除标志，1删除，0未删除
         */
         @Schema(name = "删除标志，1删除，0未删除")
        private String delFlag;
        /**
         * 预算回滚标识，0：否，1：是
         */
         @Schema(name = "预算回滚标识，0：否，1：是")
        private String rollBackFlag;
        /**
         * 跳过预算策略控制标识，0：否；1：是
         */
         @Schema(name = "跳过预算策略控制标识，0：否；1：是")
        private String skipBudgetStrategyFlag;
}
