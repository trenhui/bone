package com.netease.mis.bsm.budget.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
* 预算MPC转移项目DTO
*
* @author 梅山源码
*/
@Data
@Schema(description = "预算MPC转移项目DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProjectTransferDTO extends TenantAbstractDTO<Long> {

        /**
         * 来源系统
         */
         @Schema(name = "来源系统")
        private String sourceSystem;
        /**
         * MPC需求号
         */
         @Schema(name = "MPC需求号")
        private String requirementId;
        /**
         * 原项目
         */
         @Schema(name = "原项目")
        private String originalProjectId;
        /**
         * 新项目
         */
         @Schema(name = "新项目")
        private String newProjectId;
        /**
         * 删除标志，1删除，0未删除
         */
         @Schema(name = "删除标志，1删除，0未删除")
        private String delFlag;
}
