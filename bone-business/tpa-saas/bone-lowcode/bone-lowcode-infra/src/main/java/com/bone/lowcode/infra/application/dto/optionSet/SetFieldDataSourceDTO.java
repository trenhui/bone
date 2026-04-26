package com.bone.lowcode.infra.application.dto.optionSet;

import com.bone.lowcode.infra.infrastructure.persistence.dto.FieldExtraConfig;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetFieldDataSourceDTO {

    /**
     * 主体code
     */
    private String bizIdentityCode;

    /**
     * 字段code
     */
    @NotEmpty(message = "字段code不能为空")
    private String fieldCode;

    /**
     * 数据源类型
     */
    @NotNull(message = "数据源类型不能为空")
    private Byte dataSourceType;

    /**
     * 数据源code
     */
    @NotEmpty(message = "数据源code不能为空")
    private String dataSourceCode;


    private FieldExtraConfig extraConfig = new FieldExtraConfig();
}
