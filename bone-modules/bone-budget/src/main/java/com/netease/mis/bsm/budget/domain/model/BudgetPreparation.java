package com.netease.mis.bsm.budget.domain.model;

import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 预算编制
 *
 * @author 梅山源码
 */
@Table("budget_preparation")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetPreparation extends TenantAbstractEntity<Long> {
        /**
         * 来源系统
         */
    private String sourceSystem;
        /**
         * 管控方式
         */
    private String controlMethod;
        /**
         * 维值1
         */
    private Long dim1ValueId;
        /**
         * 维值2
         */
    private Long dim2ValueId;
        /**
         * 维值3
         */
    private Long dim3ValueId;
        /**
         * 维值4
         */
    private Long dim4ValueId;
        /**
         * 维值5
         */
    private Long dim5ValueId;
        /**
         * 维值6
         */
    private Long dim6ValueId;
        /**
         * 维值7
         */
    private Long dim7ValueId;
        /**
         * 维值8
         */
    private Long dim8ValueId;
        /**
         * 维值9
         */
    private Long dim9ValueId;
        /**
         * 预算类型项目/事前申请单
         */
    private String budgetType;
        /**
         * 项目编号
         */
    private String projectNumber;
        /**
         * 项目名称
         */
    private String projectName;
        /**
         * 项目ID
         */
    private Long projectId;
        /**
         * 子项目ID
         */
    private Long subProjectId;
        /**
         * 事件ID
         */
    private Long eventId;
        /**
         * 事前申请单号
         */
    private String requisitionNumber;
        /**
         * 事前申请单ID
         */
    private Long requisitionId;
        /**
         * 年度
         */
    private Long year;
        /**
         * 周期
         */
    private String period;
        /**
         * 币种
         */
    private String currencyCode;
        /**
         * 预算金额
         */
    private BigDecimal budgetAmount;
        /**
         * 已冻结金额
         */
    private BigDecimal frozenAmount;
        /**
         * 已消耗金额(资金流)
         */
    private BigDecimal consumedAmountCash;
        /**
         * 已消耗金额(费用流)
         */
    private BigDecimal consumedAmountCost;
        /**
         * 剩余可用金额
         */
    private BigDecimal availableAmount;
        /**
         * 预算编制人
         */
    private String budgeter;
        /**
         * 状态1生效，0失效
         */
    private String status;
        /**
         * 删除标志，1删除，0未删除
         */
    private String delFlag;
        /**
         * 修改标记
         */
    private String updateFlag;
}

