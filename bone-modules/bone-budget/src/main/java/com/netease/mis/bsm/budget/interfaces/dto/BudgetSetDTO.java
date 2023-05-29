package com.netease.mis.bsm.budget.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
* 预算设置DTO
*
* @author 梅山源码
*/
@Data
@Schema(description = "预算设置DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BudgetSetDTO extends TenantAbstractDTO<Long> {

        /**
         * 来源系统
         */
         @Schema(name = "来源系统")
        private String sourceSystem;
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
