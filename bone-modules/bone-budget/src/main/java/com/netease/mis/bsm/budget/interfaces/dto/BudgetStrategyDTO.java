package com.netease.mis.bsm.budget.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.math.BigDecimal;

/**
* 预算策略DTO
*
* @author 梅山源码
*/
@Data
@Schema(description = "预算策略DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BudgetStrategyDTO extends TenantAbstractDTO<Long> {

        /**
         * 来源系统
         */
         @Schema(name = "来源系统")
        private String sourceSystem;
        /**
         * 管控方式项目、非项目
         */
         @Schema(name = "管控方式项目、非项目")
        private String controlMethod;
        /**
         * 策略代码
         */
         @Schema(name = "策略代码")
        private String strategyCode;
        /**
         * 策略名称
         */
         @Schema(name = "策略名称")
        private String strategyName;
        /**
         * 策略控制类型
         */
         @Schema(name = "策略控制类型")
        private String strategyType;
        /**
         * 预警比例
         */
         @Schema(name = "预警比例")
        private BigDecimal warningRatio;
        /**
         * 拦截比例
         */
         @Schema(name = "拦截比例")
        private BigDecimal interceptRatio;
        /**
         * 拦截金额(CNY)
         */
         @Schema(name = "拦截金额(CNY)")
        private BigDecimal interceptAmount;
        /**
         * 预警文案
         */
         @Schema(name = "预警文案")
        private String warningMessage;
        /**
         * 拦截文案
         */
         @Schema(name = "拦截文案")
        private String interceptMessage;
        /**
         * 启用不标志1启用，0不启用
         */
         @Schema(name = "启用不标志1启用，0不启用")
        private String enableFlag;
        /**
         * 删除标志，1删除，0未删除
         */
         @Schema(name = "删除标志，1删除，0未删除")
        private String delFlag;
}
