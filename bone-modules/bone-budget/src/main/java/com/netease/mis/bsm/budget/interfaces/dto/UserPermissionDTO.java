package com.netease.mis.bsm.budget.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
* 预算用户权限DTO
*
* @author 梅山源码
*/
@Data
@Schema(description = "预算用户权限DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UserPermissionDTO extends TenantAbstractDTO<Long> {

        /**
         * 来源系统
         */
         @Schema(name = "来源系统")
        private String sourceSystem;
        /**
         * 用户邮箱
         */
         @Schema(name = "用户邮箱")
        private String email;
        /**
         * 操作
         */
         @Schema(name = "操作")
        private String operate;
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
