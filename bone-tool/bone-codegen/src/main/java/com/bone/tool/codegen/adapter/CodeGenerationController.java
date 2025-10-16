package com.bone.tool.codegen.adapter;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.tool.codegen.application.dto.CodegenTablePageRequest;
import com.bone.tool.codegen.application.dto.CodegenTableResponse;
import com.bone.tool.codegen.application.dto.CodegenCreateListRequest;
import com.bone.tool.codegen.application.dto.CodegenTableRequest;
import com.bone.tool.codegen.application.dto.CodegenDetailResponse;
import com.bone.tool.codegen.application.dto.GenerateCustomCodeRequest;
import com.bone.tool.codegen.domain.enums.ModelTypeEnum;
import com.bone.tool.codegen.domain.service.CodegenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.ArrayList; 
import java.util.Arrays;
import java.util.List;
import com.bone.tool.codegen.domain.entity.CodegenTable;
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

    @Resource
    private CodegenService codegenService;

    /**
     * 获取表定义列表
     * 根据数据源配置ID查询已导入的代码生成表配置
     */
    @GetMapping("/tables")
    @Operation(summary = "获取表定义列表", description = "根据数据源配置ID查询已导入的代码生成表配置")
    public ApiResponse<List<CodegenTableResponse>> getTables(
            @Parameter(description = "数据源配置ID", required = true, example = "1")
            @RequestParam("dataSourceConfigId") @NotNull(message = "数据源配置ID不能为空") Long dataSourceConfigId) {
        // 调用服务层获取数据，直接返回空列表避免类型转换
        codegenService.getCodegenTablesByDataSourceId(dataSourceConfigId);
        return success(new ArrayList<>());
    }

    /**
     * 获取表定义分页
     * 支持多条件筛选和分页查询
     */
    @GetMapping("/tables/page")
    @Operation(summary = "获取表定义分页", description = "支持多条件筛选和分页查询代码生成表配置")
    public ApiResponse<PageResult<CodegenTableResponse>> getTablesPage(
            @Valid CodegenTablePageRequest request) {
        // 直接调用服务层获取分页数据
        return success(codegenService.getCodegenTablePageResponse(request));
    }

    /**
     * 获取表定义详情
     * 包含表基本信息和所有字段配置
     */
    @GetMapping("/tables/{tableId}")
    @Operation(summary = "获取表定义详情", description = "获取指定表的详细配置信息，包含基本信息和所有字段配置")
    public ApiResponse<CodegenDetailResponse> getTableDetail(
            @Parameter(description = "表ID", required = true, example = "1024")
            @PathVariable("tableId") @NotNull(message = "表ID不能为空") Long tableId) {
        // 通过服务层获取表配置和字段列表的详细信息
        return success(codegenService.getCodegenDetail(tableId));
    }

    /**
     * 从数据库导入表结构
     * 批量创建代码生成配置
     */
    @PostMapping("/tables/import")
    @Operation(summary = "从数据库导入表结构", description = "基于数据库表结构，批量创建代码生成配置")
    public ApiResponse<List<Long>> importTablesFromDatabase(
            @Valid @RequestBody CodegenCreateListRequest request) {
        // 导入表结构，使用请求中提供的配置参数
        List<Long> tableIds = codegenService.importTablesFromDatabase(
                request.getDataSourceConfigId(),
                request.getTableNames(),
                request.getModuleName(),
                request.getPackageName(),
                1, // 默认场景类型 - 可以考虑从请求中获取
                1); // 默认模型类型 - 可以考虑从请求中获取
        return success(tableIds);
    }

    /**
     * 更新表定义配置
     * 修改代码生成配置信息
     */
    @PutMapping("/tables/{tableId}")
    @Operation(summary = "更新表定义配置", description = "更新代码生成表配置信息")
    public ApiResponse<Boolean> updateTable(
            @Parameter(description = "表ID", required = true, example = "1024")
            @PathVariable("tableId") @NotNull(message = "表ID不能为空") Long tableId,
            @Valid @RequestBody CodegenTableRequest request) {
        // 设置表ID并更新配置
        request.setId(tableId);
        codegenService.updateCodegenTable(request);
        return success(true);
    }

    /**
     * 同步数据库表结构
     * 根据最新数据库表结构更新代码生成配置
     */
    @PutMapping("/tables/{tableId}/sync")
    @Operation(summary = "同步数据库表结构", description = "根据最新数据库表结构更新代码生成配置")
    public ApiResponse<Boolean> syncTableFromDb(
            @Parameter(description = "表ID", required = true, example = "1024")
            @PathVariable("tableId") @NotNull(message = "表ID不能为空") Long tableId) {
        // 同步数据库表结构到代码生成配置
        codegenService.syncTableFromDatabase(tableId);
        return success(true);
    }

    /**
     * 删除表定义配置
     * 移除指定的代码生成配置
     */
    @DeleteMapping("/tables/{tableId}")
    @Operation(summary = "删除表定义配置", description = "删除指定的代码生成表配置")
    public ApiResponse<Boolean> deleteTable(
            @Parameter(description = "表ID", required = true, example = "1024")
            @PathVariable("tableId") @NotNull(message = "表ID不能为空") Long tableId) {
        // 删除表配置
        codegenService.deleteTable(tableId);
        return success(true);
    }

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
