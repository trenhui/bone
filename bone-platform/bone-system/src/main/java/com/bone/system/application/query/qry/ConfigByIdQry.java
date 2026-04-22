package com.bone.system.application.query.qry;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 根据ID查询配置
 */
@Data
public class ConfigByIdQry {
    @NotNull(message = "配置ID不能为空")
    private Long id;
}
