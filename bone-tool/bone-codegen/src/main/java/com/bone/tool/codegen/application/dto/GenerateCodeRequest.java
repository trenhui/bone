package com.bone.tool.codegen.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

/**
 * 代码生成请求
 */
@Schema(description = "代码生成请求")
@Data
public class GenerateCodeRequest {
    
    @Schema(description = "表ID列表", example = "[1,2,3]")
    @NotEmpty(message = "表ID列表不能为空")
    private List<Long> tableIds;
    
    @Schema(description = "分组ID", example = "system")
    private String groupId;
    
    @Schema(description = "模板类型", example = "1")
    private Integer modelType;
}