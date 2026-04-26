package com.bone.lowcode.infra.application.dto.page;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class UpdatePageDTO {

    private Long id;

    @Schema(description = "配置业务字段开关 0-关；1-开")
    private Byte businessFieldEnabled;
}
