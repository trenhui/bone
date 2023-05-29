package com.netease.mis.bsm.budget.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
* 项目成员DTO
*
* @author 梅山源码
*/
@Data
@Schema(description = "项目成员DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProjectEmployeeDTO extends TenantAbstractDTO<Long> {

        /**
         * 删除标识:0未删除，1删除
         */
         @Schema(name = "删除标识:0未删除，1删除")
        private String delFlag;
        /**
         * 项目id
         */
         @Schema(name = "项目id")
        private Long projectId;
        /**
         * 员工id
         */
         @Schema(name = "员工id")
        private Long employeeId;
}
