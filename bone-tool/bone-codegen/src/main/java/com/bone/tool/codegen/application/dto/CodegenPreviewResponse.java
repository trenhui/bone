package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 代码生成预览响应
 */
@Schema(description = "代码生成预览响应")
@Data
public class CodegenPreviewResponse {

    @Schema(description = "文件路径", example = "java/com/bone/system/controller/SysUserController.java")
    private String filePath;

    @Schema(description = "代码内容")
    private String code;
}
