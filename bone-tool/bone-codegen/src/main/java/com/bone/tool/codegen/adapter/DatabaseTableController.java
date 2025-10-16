package com.bone.tool.codegen.adapter;

import cn.hutool.core.collection.CollectionUtil;
import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.tool.codegen.application.converter.CodegenConverter;
import com.bone.tool.codegen.application.dto.CodegenCreateListRequest;
import com.bone.tool.codegen.application.dto.CodegenDetailResponse;
import com.bone.tool.codegen.application.dto.CodegenTablePageRequest;
import com.bone.tool.codegen.application.dto.CodegenTableRequest;
import com.bone.tool.codegen.application.dto.CodegenTableResponse;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.entity.TableInfo;
import com.bone.tool.codegen.domain.service.DatabaseTableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bone.core.model.ApiResponse.success;

/**
 * 数据库表 控制器
 * <p>
 * 提供数据库表信息查询和代码生成表配置管理的RESTful API接口
 * 
 * @author bone-team
 */
@Tag(name = "数据库表管理", description = "提供数据库表信息查询和代码生成表配置管理功能")
@RestController
@RequestMapping({"/api/v1/database-tables", "/api/v1/code-generation/tables"})
@Validated
public class DatabaseTableController {

    @Resource
    private DatabaseTableService databaseTableService;
    
    @Resource
    private CodegenConverter codegenConverter;

    // 数据库表信息查询相关接口
    @GetMapping("/original")
    @Operation(summary = "获取数据库表列表")
    @Parameter(name = "dataSourceConfigId", description = "数据源配置ID", required = true)
    @Parameter(name = "nameLike", description = "表名称模糊匹配")
    @Parameter(name = "commentLike", description = "表描述模糊匹配")
    public ApiResponse<List<TableInfo>> getTableList(
            @RequestParam("dataSourceConfigId") Long dataSourceConfigId,
            @RequestParam(value = "nameLike", required = false) String nameLike,
            @RequestParam(value = "commentLike", required = false) String commentLike) {
        
        List<TableInfo> tableList = databaseTableService.getTableList(dataSourceConfigId, nameLike, commentLike);
        return success(tableList);
    }
    
    // 兼容旧路径，保持API向后兼容
    @GetMapping("/api/v1/codegen/database-table/list")
    @Operation(summary = "获得数据库的表和字段（兼容旧路径）")
    public ApiResponse<List<TableInfo>> getDatabaseTableList(@RequestParam("dataSourceConfigId") Long dataSourceConfigId) {
        List<TableInfo> tables = databaseTableService.getTableList(dataSourceConfigId);
        return success(tables);
    }

    @GetMapping("/original/all")
    @Operation(summary = "获取所有数据库表")
    @Parameter(name = "dataSourceConfigId", description = "数据源配置ID", required = true)
    public ApiResponse<List<TableInfo>> getAllTables(@RequestParam("dataSourceConfigId") Long dataSourceConfigId) {
        
        List<TableInfo> tableList = databaseTableService.getTableList(dataSourceConfigId);
        return success(tableList);
    }

    @GetMapping("/original/{tableName}")
    @Operation(summary = "获取表详情")
    @Parameter(name = "dataSourceConfigId", description = "数据源配置ID", required = true)
    @Parameter(name = "tableName", description = "表名称", required = true)
    public ApiResponse<TableInfo> getTable(
            @RequestParam("dataSourceConfigId") Long dataSourceConfigId,
            @PathVariable("tableName") String tableName) {
        
        TableInfo tableInfo = databaseTableService.getTable(dataSourceConfigId, tableName);
        return success(tableInfo);
    }

    @PostMapping("/original/batch")
    @Operation(summary = "批量获取表信息")
    @Parameter(name = "dataSourceConfigId", description = "数据源配置ID", required = true)
    public ApiResponse<List<TableInfo>> getTables(
            @RequestParam("dataSourceConfigId") Long dataSourceConfigId,
            @RequestBody List<String> tableNames) {
        
        if (CollectionUtil.isEmpty(tableNames)) {
            return success(CollectionUtil.newArrayList());
        }
        
        List<TableInfo> tableInfos = databaseTableService.getTables(dataSourceConfigId, tableNames);
        return success(tableInfos);
    }
    
    // 代码生成表配置管理相关接口
    @GetMapping
    @Operation(summary = "获取表定义列表", description = "根据数据源配置ID查询已导入的代码生成表配置")
    public ApiResponse<List<CodegenTableResponse>> getTables(
            @Parameter(description = "数据源配置ID", required = true, example = "1")
            @RequestParam("dataSourceConfigId") @NotNull(message = "数据源配置ID不能为空") Long dataSourceConfigId) {
        // 调用服务层获取数据，使用CodegenMapper进行类型转换
        List<CodegenTable> tables = databaseTableService.getCodegenTablesByDataSourceId(dataSourceConfigId);
        List<CodegenTableResponse> responses = codegenConverter.toCodegenTableResponseList(tables);
        return success(responses);
    }

    @GetMapping("/page")
    @Operation(summary = "获取表定义分页", description = "支持多条件筛选和分页查询代码生成表配置")
    public ApiResponse<PageResult<CodegenTableResponse>> getTablesPage(
            @Valid CodegenTablePageRequest request) {
        // 直接调用服务层获取分页数据
        return success(databaseTableService.getCodegenTablePageResponse(request));
    }

    @GetMapping("/{tableId}")
    @Operation(summary = "获取表定义详情", description = "获取指定表的详细配置信息，包含基本信息和所有字段配置")
    public ApiResponse<CodegenDetailResponse> getTableDetail(
            @Parameter(description = "表ID", required = true, example = "1024")
            @PathVariable("tableId") @NotNull(message = "表ID不能为空") Long tableId) {
        // 通过服务层获取表配置和字段列表的详细信息
        return success(databaseTableService.getCodegenDetail(tableId));
    }

    @PostMapping("/import")
    @Operation(summary = "从数据库导入表结构", description = "基于数据库表结构，批量创建代码生成配置")
    public ApiResponse<List<Long>> importTablesFromDatabase(
            @Valid @RequestBody CodegenCreateListRequest request) {
        // 导入表结构，使用请求中提供的配置参数
        List<Long> tableIds = databaseTableService.importTablesFromDatabase(
                request.getDataSourceConfigId(),
                request.getTableNames(),
                request.getModuleName(),
                request.getPackageName(),
                1, // 默认场景类型
                1); // 默认模型类型
        return success(tableIds);
    }

    @PutMapping("/{tableId}")
    @Operation(summary = "更新表定义配置", description = "更新代码生成表配置信息")
    public ApiResponse<Boolean> updateTable(
            @Parameter(description = "表ID", required = true, example = "1024")
            @PathVariable("tableId") @NotNull(message = "表ID不能为空") Long tableId,
            @Valid @RequestBody CodegenTableRequest request) {
        // 设置表ID并更新配置
        request.setId(tableId);
        databaseTableService.updateCodegenTable(request);
        return success(true);
    }

    @PutMapping("/{tableId}/sync")
    @Operation(summary = "同步数据库表结构", description = "根据最新数据库表结构更新代码生成配置")
    public ApiResponse<Boolean> syncTableFromDb(
            @Parameter(description = "表ID", required = true, example = "1024")
            @PathVariable("tableId") @NotNull(message = "表ID不能为空") Long tableId) {
        // 同步数据库表结构到代码生成配置
        databaseTableService.syncTableFromDatabase(tableId);
        return success(true);
    }

    @DeleteMapping("/{tableId}")
    @Operation(summary = "删除表定义配置", description = "删除指定的代码生成表配置")
    public ApiResponse<Boolean> deleteTable(
            @Parameter(description = "表ID", required = true, example = "1024")
            @PathVariable("tableId") @NotNull(message = "表ID不能为空") Long tableId) {
        // 删除表配置
        databaseTableService.deleteTable(tableId);
        return success(true);
    }
}