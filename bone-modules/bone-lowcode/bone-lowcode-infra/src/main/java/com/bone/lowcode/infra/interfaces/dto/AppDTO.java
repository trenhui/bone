package com.bone.lowcode.infra.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import jakarta.validation.constraints.*;

/**
 * 应用基础DTO，提供给创建、修改、详细、查询子DTO使用
 *
 * @author renhui.trh
 */
@Data
@Schema(description = "应用DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppDTO extends TenantAbstractDTO<Long> {

    @Schema(name = "名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "名称不能为空")
    private String name;

    @Schema(name = "编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "编码不能为空")
    private String code;

    @Schema(name = "状态", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态(0 正常 1 待上线 2 下线)")
    private Integer status;

    @Schema(name = "备注")
    private String remark;
}
