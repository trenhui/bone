package com.bone.tools.codegen.application.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 代码生成表和字段的明细 Response VO")
@Data
public class CodegenDetailResponse {

    @Schema(description = "表定义")
    private CodegenTableResponse table;

    @Schema(description = "字段定义")
    private List<CodegenColumnResponse> columns;

}
