package com.netease.mis.bsm.budget.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
* 预算规则DTO
*
* @author 梅山源码
*/
@Data
@Schema(description = "预算规则DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BudgetRuleDTO extends TenantAbstractDTO<Long> {

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
         * 管控范围项目类：项目、事前申请单；非项目为空
         */
         @Schema(name = "管控范围项目类：项目、事前申请单；非项目为空")
        private String controlScope;
        /**
         * 维度1
         */
         @Schema(name = "维度1")
        private String dim1Value;
        /**
         * 维度1
         */
         @Schema(name = "维度1")
        private String dim2Value;
        /**
         * 维度3
         */
         @Schema(name = "维度3")
        private String dim3Value;
        /**
         * 维度4
         */
         @Schema(name = "维度4")
        private String dim4Value;
        /**
         * 维度5
         */
         @Schema(name = "维度5")
        private String dim5Value;
        /**
         * 维度6
         */
         @Schema(name = "维度6")
        private String dim6Value;
        /**
         * 维度7
         */
         @Schema(name = "维度7")
        private String dim7Value;
        /**
         * 维度8
         */
         @Schema(name = "维度8")
        private String dim8Value;
        /**
         * 维度9
         */
         @Schema(name = "维度9")
        private String dim9Value;
        /**
         * 管控周期年:YEAR,季度:QUARTER,月:MONTH
         */
         @Schema(name = "管控周期年:YEAR,季度:QUARTER,月:MONTH")
        private String controlPeriod;
        /**
         * 单据范围
         */
         @Schema(name = "单据范围")
        private String docScope;
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
