package com.netease.mis.bsm.budget.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
* 项目类型DTO
*
* @author 梅山源码
*/
@Data
@Schema(description = "项目类型DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProjectTypeDTO extends TenantAbstractDTO<Long> {

        /**
         * 项目类型code
         */
         @Schema(name = "项目类型code")
        private String projectTypeCode;
        /**
         * 项目类型名称
         */
         @Schema(name = "项目类型名称")
        private String projectTypeName;
        /**
         * 删除标识:0未删除，1删除
         */
         @Schema(name = "删除标识:0未删除，1删除")
        private String delFlag;
        /**
         * 预算控制策略id
         */
         @Schema(name = "预算控制策略id")
        private Long budgetStrategyId;
        /**
         * 项目类型层级，0为父级，1为子级
         */
         @Schema(name = "项目类型层级，0为父级，1为子级")
        private Integer projectTypeLevel;
        /**
         * 父项目类型id
         */
         @Schema(name = "父项目类型id")
        private Long parentProjectTypeId;
        /**
         * 是否启用，0未启用(失效)，1启用（生效）
         */
         @Schema(name = "是否启用，0未启用(失效)，1启用（生效）")
        private Integer enableFlag;
        /**
         * 需求组id
         */
         @Schema(name = "需求组id")
        private Long requirementGroupId;
        /**
         * 需求类型id
         */
         @Schema(name = "需求类型id")
        private Long requirementTypeId;
}
