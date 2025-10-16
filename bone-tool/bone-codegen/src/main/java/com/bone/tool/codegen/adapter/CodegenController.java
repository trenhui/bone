package com.bone.tool.codegen.adapter;

import com.bone.tool.codegen.application.dto.GenerateCustomCodeRequest;
import com.bone.tool.codegen.domain.service.CodegenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 代码生成管理控制器
 * <p>
 * 提供代码生成功能的RESTful API接口
 * 遵循RESTful设计风格，确保接口清晰、直观
 * 
 * @author bone-team
 */
@Tag(name = "代码生成管理")
@RestController
@RequestMapping("/api/v1/code-generation")
@Validated
public class CodeGenController {

    @Resource
    private CodegenService codegenService;

    /**
     * 生成并下载代码
     * 基于已配置的表生成代码并下载
     */
    @GetMapping("/generate/download")
    @Operation(summary = "生成并下载代码", description = "基于已配置的表生成代码并下载为ZIP文件")
    public void generateAndDownloadCode(
            @Parameter(description = "表ID列表", required = true, example = "1,2,3")
            @RequestParam("tableIds") @NotEmpty(message = "表ID列表不能为空") List<Long> tableIds,
            @Parameter(description = "分组名称", example = "default")
            @RequestParam(value = "groupId", defaultValue = "default") String groupId,
            @Parameter(description = "模型类型: 1-SaaS, 2-单租户", example = "1")
            @RequestParam(value = "modelType", defaultValue = "1") Integer modelType,
            HttpServletResponse response) throws IOException {
        // 生成代码并写入响应流
        try (OutputStream out = response.getOutputStream()) {
            codegenService.generateBatchCodes(tableIds, groupId, modelType, out);
            
            // 设置响应头
            response.setContentType("application/zip");
            String fileName = tableIds.size() == 1 ? "code-single.zip" : "code-multi.zip";
            response.setHeader("Content-Disposition", 
                "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            out.flush();
        }
    }
    
    /**
     * 自定义生成代码
     * 支持更灵活的代码生成配置
     */
    @PostMapping("/generate/custom")
    @Operation(summary = "自定义生成代码", description = "使用自定义配置生成代码")
    public void generateCustomCode(
            @Valid @RequestBody GenerateCustomCodeRequest request,
            HttpServletResponse response) throws IOException {
        // 设置响应头
        String fileName = "code-" + request.getProjectName() + ".zip";
        response.setHeader("Content-Disposition", 
            "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        response.setContentType("application/zip");
        
        // 生成代码并写入响应流
        try (OutputStream out = response.getOutputStream()) {
            codegenService.generateCustomCode(request, out);
            out.flush();
        }
    }
}
