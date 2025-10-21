package com.bone.tool.codegen.adapter;

import com.bone.core.model.ApiResponse;
import com.bone.tool.codegen.application.dto.CodegenTableResponse;
import com.bone.tool.codegen.application.dto.GenerateCustomCodeRequest;

import com.bone.tool.codegen.domain.service.CodegenServiceInterface;
import com.bone.tool.codegen.domain.service.DatabaseTableServiceInterface;
import com.bone.tool.codegen.application.converter.CodegenConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.bone.core.model.ApiResponse.success;

/**
 * 代码生成管理控制器
 * <p>
 * 提供代码生成配置管理和代码生成的RESTful API接口
 * 遵循RESTful设计风格，确保接口清晰、直观
 * 
 * @author bone-team
 */
@Tag(name = "代码生成管理")
@RestController
@RequestMapping("/api/v1/code-generation")
@Validated
public class CodeGenerationController {
      private static final Logger log = LoggerFactory.getLogger(CodeGenerationController.class);
      
      @Resource
      private CodegenServiceInterface codegenService;
      
      @Resource
      private DatabaseTableServiceInterface databaseTableService;
      
      @Resource
      private CodegenConverter codegenConverter;
    /**
     * 获取表定义列表
     * 根据数据源配置ID查询已导入的代码生成表配置
     */
    @GetMapping("/tables")
    @Operation(summary = "获取表定义列表", description = "根据数据源配置ID查询已导入的代码生成表配置")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取表定义列表"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "获取表定义列表失败")
    })
    public ApiResponse<List<CodegenTableResponse>> getTables(
            @Parameter(description = "数据源配置ID", required = true, example = "1")
            @RequestParam("dataSourceConfigId") @NotNull(message = "数据源配置ID不能为空") Long dataSourceConfigId) {
        log.info("开始获取表定义列表，数据源配置ID: {}", dataSourceConfigId);
        try {
            // 直接调用数据库表服务获取数据
            List<com.bone.tool.codegen.domain.entity.CodegenTable> tables = databaseTableService.getCodegenTablesByDataSourceId(dataSourceConfigId);
            // 使用converter批量转换为响应对象列表
            List<CodegenTableResponse> tableList = codegenConverter.toCodegenTableResponseList(tables);
            log.info("成功获取表定义列表，数据源配置ID: {}，表数量: {}", dataSourceConfigId, tableList.size());
            return success(tableList);
        } catch (IllegalArgumentException e) {
            log.warn("获取表定义列表参数错误: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("获取表定义列表失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "获取表定义列表失败: " + e.getMessage());
        }
    }

    /**
     * 批量生成代码
     * 根据表ID列表批量生成代码并下载
     */
    @GetMapping("/generate/batch")
    @Operation(summary = "批量生成代码", description = "根据表ID列表批量生成代码并下载")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功生成并下载代码压缩包"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "代码生成过程中出现错误")
    })
    public void generateAndDownloadCode(
            @Parameter(description = "表ID列表", required = true, example = "1,2,3")
            @RequestParam("tableIds") @NotEmpty(message = "表ID列表不能为空") List<Long> tableIds,
            @Parameter(description = "分组名称", example = "default")
            @RequestParam(value = "groupId", defaultValue = "default") String groupId,
            @Parameter(description = "模型类型: 1-SaaS, 2-单租户", example = "1")
            @RequestParam(value = "modelType", defaultValue = "1") Integer modelType,
            HttpServletResponse response) throws IOException {
        log.info("开始生成代码，表ID列表: {}, 分组: {}, 模型类型: {}", tableIds, groupId, modelType);
        
        try {
            // 设置响应头
            response.setContentType("application/zip");
            String fileName = tableIds.size() == 1 ? "code-single.zip" : "code-multi.zip";
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            
            // 生成代码并写入响应流
            try (OutputStream out = response.getOutputStream()) {
                codegenService.generateBatchCodes(tableIds, groupId, modelType, out);
                out.flush();
            }
            
            log.info("代码生成成功，表ID列表: {}", tableIds);
        } catch (Exception e) {
            log.error("代码生成失败: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (OutputStream out = response.getOutputStream()) {
                out.write(("生成代码失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    
    /**
     * 自定义生成代码
     * 支持更灵活的代码生成配置
     */
    @PostMapping("/generate/custom")
    @Operation(summary = "自定义生成代码", description = "使用自定义配置生成代码")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功生成并返回自定义代码压缩包"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "自定义代码生成失败")
    })
    public void generateCustomCode(
            @Valid @RequestBody GenerateCustomCodeRequest request,
            HttpServletResponse response) throws IOException {
        log.info("开始自定义生成代码");
        
        try {
            // 使用反射方式获取项目名称，如果为空则使用默认值
            String projectName = "custom";
            try {
                Object projectNameValue = request.getClass().getDeclaredField("projectName").get(request);
                if (projectNameValue != null) {
                    projectName = projectNameValue.toString();
                }
            } catch (Exception e) {
                log.warn("获取项目名称失败，使用默认值: custom");
            }
            
            // 设置响应头
            String fileName = "code-" + projectName + ".zip";
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            
            // 生成自定义代码并写入响应流
            try (OutputStream out = response.getOutputStream()) {
                // 直接调用服务方法，该方法会将代码写入输出流
                codegenService.generateCustomCode(request, out);
                out.flush();
            }
            
            log.info("自定义代码生成成功，项目名称: {}", projectName);
        } catch (Exception e) {
            log.error("自定义代码生成失败: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (OutputStream out = response.getOutputStream()) {
                out.write(("生成自定义代码失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
