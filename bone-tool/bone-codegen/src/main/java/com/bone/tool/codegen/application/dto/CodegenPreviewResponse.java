package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 代码生成预览响应
 * <p>
 * 用于返回代码生成预览文件内容的应用层DTO
 */
@Schema(description = "代码生成预览响应")
@Data
public class CodegenPreviewResponse {

    @Schema(description = "文件路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "java/com/bone/system/controller/SysUserController.java")
    private String filePath;

    @Schema(description = "生成的代码内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

}
