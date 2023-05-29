package com.netease.mis.bsm.budget.domain.model;

import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;
import java.util.*;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 预算规则
 *
 * @author 梅山源码
 */
@Table("budget_rule")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetRule extends TenantAbstractEntity<Long> {
        /**
         * 来源系统
         */
    private String sourceSystem;
        /**
         * 管控方式项目、非项目
         */
    private String controlMethod;
        /**
         * 管控范围项目类：项目、事前申请单；非项目为空
         */
    private String controlScope;
        /**
         * 维度1
         */
    private String dim1Value;
        /**
         * 维度1
         */
    private String dim2Value;
        /**
         * 维度3
         */
    private String dim3Value;
        /**
         * 维度4
         */
    private String dim4Value;
        /**
         * 维度5
         */
    private String dim5Value;
        /**
         * 维度6
         */
    private String dim6Value;
        /**
         * 维度7
         */
    private String dim7Value;
        /**
         * 维度8
         */
    private String dim8Value;
        /**
         * 维度9
         */
    private String dim9Value;
        /**
         * 管控周期年:YEAR,季度:QUARTER,月:MONTH
         */
    private String controlPeriod;
        /**
         * 单据范围
         */
    private String docScope;
        /**
         * 启用不标志1启用，0不启用
         */
    private String enableFlag;
        /**
         * 删除标志，1删除，0未删除
         */
    private String delFlag;
}

