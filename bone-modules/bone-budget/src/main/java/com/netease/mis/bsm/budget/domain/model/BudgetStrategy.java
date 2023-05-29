package com.netease.mis.bsm.budget.domain.model;

import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 预算策略
 *
 * @author 梅山源码
 */
@Table("budget_strategy")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetStrategy extends TenantAbstractEntity<Long> {
        /**
         * 来源系统
         */
    private String sourceSystem;
        /**
         * 管控方式项目、非项目
         */
    private String controlMethod;
        /**
         * 策略代码
         */
    private String strategyCode;
        /**
         * 策略名称
         */
    private String strategyName;
        /**
         * 策略控制类型
         */
    private String strategyType;
        /**
         * 预警比例
         */
    private BigDecimal warningRatio;
        /**
         * 拦截比例
         */
    private BigDecimal interceptRatio;
        /**
         * 拦截金额(CNY)
         */
    private BigDecimal interceptAmount;
        /**
         * 预警文案
         */
    private String warningMessage;
        /**
         * 拦截文案
         */
    private String interceptMessage;
        /**
         * 启用不标志1启用，0不启用
         */
    private String enableFlag;
        /**
         * 删除标志，1删除，0未删除
         */
    private String delFlag;
}

