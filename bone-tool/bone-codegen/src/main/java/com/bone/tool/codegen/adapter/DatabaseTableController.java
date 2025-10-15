package com.bone.tool.codegen.adapter;

import cn.hutool.core.collection.CollectionUtil;
import com.bone.core.model.ApiResponse;
import com.bone.tool.codegen.domain.entity.TableInfo;
import com.bone.tool.codegen.domain.service.DatabaseTableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bone.core.model.ApiResponse.success;

/**
 * 数据库表 控制器
 * <p>
 * 提供数据库表信息查询的RESTful API接口，支持获取表列表、表详情等功能
 * 为代码生成提供表选择功能的后端支持
 * 
 * @author bone-team
 */
@Tag(name = "数据库表管理", description = "提供数据库表信息的查询功能，支持表选择")
@RestController
@RequestMapping("/api/v1/database-tables")
public class DatabaseTableController {

    @Resource
    private DatabaseTableService databaseTableService;

    @GetMapping
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

    @GetMapping("/all")
    @Operation(summary = "获取所有数据库表")
    @Parameter(name = "dataSourceConfigId", description = "数据源配置ID", required = true)
    public ApiResponse<List<TableInfo>> getAllTables(@RequestParam("dataSourceConfigId") Long dataSourceConfigId) {
        
        List<TableInfo> tableList = databaseTableService.getTableList(dataSourceConfigId);
        return success(tableList);
    }

    @GetMapping("/{tableName}")
    @Operation(summary = "获取表详情")
    @Parameter(name = "dataSourceConfigId", description = "数据源配置ID", required = true)
    @Parameter(name = "tableName", description = "表名称", required = true)
    public ApiResponse<TableInfo> getTable(
            @RequestParam("dataSourceConfigId") Long dataSourceConfigId,
            @PathVariable("tableName") String tableName) {
        
        TableInfo tableInfo = databaseTableService.getTable(dataSourceConfigId, tableName);
        return success(tableInfo);
    }

    @PostMapping("/batch")
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
}