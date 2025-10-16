package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 代码生成详情响应
 */
@Schema(description = "代码生成详情响应")
@Data
public class CodegenDetailResponse {

    @Schema(description = "表配置信息")
    private CodegenTableResponse table;

    @Schema(description = "列配置列表")
    private List<CodegenColumnResponse> columns;
}
