package com.bone.tools.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 代码生成表和字段的修改 Request VO")
@Data
public class CodegenUpdateRequest {

    private CodegenTableSaveRequest table;

    private List<CodegenColumnSaveRequest> columns;

}
