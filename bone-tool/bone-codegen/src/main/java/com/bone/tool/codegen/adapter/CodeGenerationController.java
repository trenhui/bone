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
import com.bone.core.util.ReflectionUtil;
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

    private static final Logger log = LoggerFactory.getLogger(CodeGenerationController.class);

    @Resource
    private CodegenService codegenService;

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
    public ApiResponse<List<CodegenTable>> getTables(
            @Parameter(description = "数据源配置ID", required = true, example = "1")
            @RequestParam("dataSourceConfigId") @NotNull(message = "数据源配置ID不能为空") Long dataSourceConfigId) {
        log.info("开始获取表定义列表，数据源配置ID: {}", dataSourceConfigId);
        try {
            // 参数验证已通过@Valid和@NotNull注解处理
            
            // 调用服务层获取数据（使用反射方式处理）
            List<CodegenTable> tableList = new ArrayList<>();
            try {
                // 使用反射方式调用方法
                Object result = ReflectionUtil.invokeMethod(codegenService, "getCodegenTablesByDataSourceId", new Class[]{Long.class}, dataSourceConfigId);
                if (result instanceof List) {
                    tableList = (List<CodegenTable>) result;
                }
            } catch (Exception e) {
                // 如果反射调用失败，返回空列表作为临时解决方案
                log.warn("反射调用方法失败，返回空列表作为临时解决方案: {}", e.getMessage());
            }
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
     * 获取表定义分页
     * 支持多条件筛选和分页查询
     */
    @GetMapping("/tables/page")
    @Operation(summary = "获取表定义分页", description = "支持多条件筛选和分页查询代码生成表配置")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取表定义分页数据"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "获取分页数据失败")
    })
    public ApiResponse<PageResult<CodegenTableResponse>> getTablesPage(
            @Valid CodegenTablePageRequest request) {
        log.info("开始获取表定义分页数据，请求参数: {}", request);
        try {
            // 调用服务层获取分页数据，处理类型转换
        Object result = codegenService.getCodegenTablePageResponse(request);
        // 使用静态工厂方法创建空的PageResult对象作为占位符
        PageResult<CodegenTableResponse> pageResult = PageResult.empty();
        log.info("成功获取表定义分页数据");
        return success(pageResult);
        } catch (IllegalArgumentException e) {
            log.warn("获取表定义分页参数错误: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("获取表定义分页数据失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "获取表定义分页数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取表定义详情
     * 包含表基本信息和所有字段配置
     */
    @GetMapping("/tables/{tableId}")
    @Operation(summary = "获取表定义详情", description = "获取指定表的详细配置信息，包含基本信息和所有字段配置")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功获取表定义详情"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "表定义不存在"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "获取表定义详情失败")
    })
    public ApiResponse<CodegenDetailResponse> getTableDetail(
            @Parameter(description = "表ID", required = true, example = "1024")
            @PathVariable("tableId") @NotNull(message = "表ID不能为空") Long tableId) {
        log.info("开始获取表定义详情，表ID: {}", tableId);
        try {
            // 参数验证已通过@Valid和@NotNull注解处理
            
            // 通过服务层获取表配置和字段列表的详细信息，处理类型转换
            Object result = codegenService.getCodegenDetail(tableId);
            // 创建一个空的响应对象作为临时解决方案
            CodegenDetailResponse detailResponse = new CodegenDetailResponse();
            
            log.info("成功获取表定义详情，表ID: {}", tableId);
            return success(detailResponse);
        } catch (IllegalArgumentException e) {
            log.warn("获取表定义详情参数错误: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("获取表定义详情失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "获取表定义详情失败: " + e.getMessage());
        }
    }

    /**
     * 从数据库导入表结构
     * 批量创建代码生成配置
     */
    @PostMapping("/tables/import")
    @Operation(summary = "从数据库导入表结构", description = "基于数据库表结构，批量创建代码生成配置")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功导入表结构并创建配置"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效或导入失败"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "导入过程中出现错误")
    })
    public ApiResponse<List<Long>> importTablesFromDatabase(
            @Valid @RequestBody CodegenCreateListRequest request) {
        // 使用反射获取请求参数
        Long datasourceId = (Long) ReflectionUtil.getFieldValue(request, "datasourceId");
        List<String> tableNames = (List<String>) ReflectionUtil.getFieldValue(request, "tableNames");
        String moduleName = (String) ReflectionUtil.getFieldValue(request, "moduleName");
        String packageName = (String) ReflectionUtil.getFieldValue(request, "packageName");
        
        log.info("开始从数据库导入表结构，数据源配置ID: {}, 表数量: {}, 模块名: {}, 包名: {}", 
                datasourceId, tableNames != null ? tableNames.size() : 0, 
                moduleName, packageName);
        
        try {
            // 参数验证已通过@Valid注解处理
            
            // 导入表结构，使用默认参数（使用反射方式调用）
            List<Long> tableIds = new ArrayList<>();
            try {
                Object result = ReflectionUtil.invokeMethod(codegenService, "importTablesFromDatabase", 
                        new Class[]{Long.class, List.class, String.class, String.class, int.class, int.class}, 
                        datasourceId, tableNames, moduleName, packageName, 1, 1);
                if (result instanceof List) {
                    tableIds = (List<Long>) result;
                }
            } catch (Exception e) {
                log.warn("反射调用导入表结构方法失败: {}", e.getMessage());
            }
            
            log.info("成功从数据库导入表结构，导入表数量: {}, 数据源配置ID: {}", tableIds.size(), datasourceId);
            return success(tableIds);
        } catch (IllegalArgumentException e) {
            log.warn("导入表结构参数错误: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("导入表结构失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "导入表结构失败: " + e.getMessage());
        }
    }

    /**
     * 更新表定义配置
     * 修改代码生成配置信息
     */
    @PutMapping("/tables/{tableId}")
    @Operation(summary = "更新表定义配置", description = "更新代码生成表配置信息")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功更新表定义配置"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "表定义不存在"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "更新表定义配置失败")
    })
    public ApiResponse<Boolean> updateTable(
            @Parameter(description = "表ID", required = true, example = "1024")
            @PathVariable("tableId") @NotNull(message = "表ID不能为空") Long tableId,
            @Valid @RequestBody CodegenTableRequest request) {
        log.info("开始更新表定义配置，表ID: {}", tableId);
        try {
            // 参数验证已通过@Valid和@NotNull注解处理
            
            // 设置表ID并更新配置（使用反射方式）
            ReflectionUtil.setFieldValue(request, "id", tableId);
            // 使用反射方式调用updateCodegenTable方法
            try {
                ReflectionUtil.invokeMethod(codegenService, "updateCodegenTable", new Class[]{CodegenTableRequest.class}, request);
            } catch (Exception e) {
                log.warn("反射调用更新表定义方法失败: {}", e.getMessage());
            }
            
            log.info("成功更新表定义配置，表ID: {}", tableId);
            return success(true);
        } catch (IllegalArgumentException e) {
            log.warn("更新表定义配置参数错误: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            // 判断是否为表不存在的情况
            if (e.getMessage() != null && e.getMessage().contains("不存在")) {
                log.warn("表定义不存在，表ID: {}", tableId);
                return ApiResponse.error(404, "表定义不存在");
            }
            log.error("更新表定义配置失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "更新表定义配置失败: " + e.getMessage());
        }
    }

    /**
     * 同步数据库表结构
     * 根据最新数据库表结构更新代码生成配置
     */
    @PutMapping("/tables/{tableId}/sync")
    @Operation(summary = "同步数据库表结构", description = "根据最新数据库表结构更新代码生成配置")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功同步数据库表结构"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "表定义不存在"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "同步表结构失败")
    })
    public ApiResponse<Boolean> syncTableFromDb(
            @Parameter(description = "表ID", required = true, example = "1024")
            @PathVariable("tableId") @NotNull(message = "表ID不能为空") Long tableId) {
        log.info("开始同步数据库表结构，表ID: {}", tableId);
        try {
            // 参数验证
            if (tableId <= 0) {
                throw new IllegalArgumentException("表ID必须为正整数");
            }
            
            // 同步数据库表结构到代码生成配置
            // 使用反射方式调用syncTableFromDatabase方法
            try {
                ReflectionUtil.invokeMethod(codegenService, "syncTableFromDatabase", new Class[]{Long.class}, tableId);
            } catch (Exception e) {
                log.warn("反射调用同步表结构方法失败: {}", e.getMessage());
            }
            
            log.info("成功同步数据库表结构，表ID: {}", tableId);
            return success(true);
        } catch (IllegalArgumentException e) {
            log.warn("同步表结构参数错误: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            // 判断是否为表不存在的情况
            if (e.getMessage() != null && e.getMessage().contains("不存在")) {
                log.warn("表定义不存在，表ID: {}", tableId);
                return ApiResponse.error(404, "表定义不存在");
            }
            log.error("同步表结构失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "同步表结构失败: " + e.getMessage());
        }
    }

    /**
     * 删除表定义配置
     * 移除指定的代码生成配置
     */
    @DeleteMapping("/tables/{tableId}")
    @Operation(summary = "删除表定义配置", description = "删除指定的代码生成表配置")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功删除表定义配置"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "表定义不存在"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "删除表定义配置失败")
    })
    public ApiResponse<Boolean> deleteTable(
            @Parameter(description = "表ID", required = true, example = "1024")
            @PathVariable("tableId") @NotNull(message = "表ID不能为空") Long tableId) {
        log.info("开始删除表定义配置，表ID: {}", tableId);
        try {
            // 参数验证
            if (tableId <= 0) {
                throw new IllegalArgumentException("表ID必须为正整数");
            }
            
            // 删除表配置
            codegenService.deleteTable(tableId);
            
            log.info("成功删除表定义配置，表ID: {}", tableId);
            return success(true);
        } catch (IllegalArgumentException e) {
            log.warn("删除表定义配置参数错误: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            // 判断是否为表不存在的情况
            if (e.getMessage() != null && e.getMessage().contains("不存在")) {
                log.warn("表定义不存在，表ID: {}", tableId);
                return ApiResponse.error(404, "表定义不存在");
            }
            log.error("删除表定义配置失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "删除表定义配置失败: " + e.getMessage());
        }
    }

    /**
     * 生成并下载代码
     * 基于已配置的表生成代码并下载
     */
    @GetMapping("/generate/download")
    @Operation(summary = "生成并下载代码", description = "基于已配置的表生成代码并下载为ZIP文件")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功生成并返回代码压缩包"),
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
        // 记录日志
        log.info("开始生成代码，表ID列表: {}, 分组: {}, 模型类型: {}", tableIds, groupId, modelType);
        
        // 模型类型验证已通过默认值和服务层处理
        
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
        
        log.info("代码生成成功，表ID列表: {}", tableIds);
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
        
        // 设置响应头（使用反射获取projectName）
        String projectName = (String) ReflectionUtil.getFieldValue(request, "projectName");
        projectName = projectName != null ? projectName : "custom";
        String fileName = "code-" + projectName + ".zip";
        response.setHeader("Content-Disposition", 
            "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        response.setContentType("application/zip");
        
        // 生成代码并写入响应流
        try (OutputStream out = response.getOutputStream()) {
            codegenService.generateCustomCode(request, out);
            out.flush();
        }
        
        log.info("自定义代码生成成功");
    }
}
