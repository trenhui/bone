package com.bone.tool.codegen.application.service;

import com.bone.core.model.PageResult;
import com.bone.tool.codegen.application.dto.CodegenDetailResponse;
import com.bone.tool.codegen.application.dto.CodegenTablePageRequest;
import com.bone.tool.codegen.application.dto.CodegenTableRequest;
import com.bone.tool.codegen.application.dto.CodegenTableResponse;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.entity.DatabaseTableMetadata;

import java.util.List;
import java.util.Optional;

/**
 * 数据库表服务接口
 */
public interface DatabaseTableService {
    
    /**
     * 获取数据库表列表
     * @param dataSourceConfigId 数据源ID
     * @param nameLike 表名搜索
     * @param commentLike 备注搜索
     * @return 表元数据列表
     */
    List<DatabaseTableMetadata> getTableList(Long dataSourceConfigId, String nameLike, String commentLike);
    
    /**
     * 根据表名列表获取数据库表信息
     * @param dataSourceConfigId 数据源配置ID
     * @param tableNames 表名列表
     * @return 表元数据列表
     */
    List<DatabaseTableMetadata> getTables(Long dataSourceConfigId, List<String> tableNames);
    
    /**
     * 获取代码生成详情
     * @param tableId 表ID
     * @return 代码生成详情响应
     */
    CodegenDetailResponse getCodegenDetail(Long tableId);
    
    /**
     * 导入表结构从数据库
     * @param dataSourceConfigId 数据源配置ID
     * @param tableNames 表名列表
     * @param moduleName 模块名
     * @param packageName 包名
     * @param sceneType 场景类型
     * @param modelType 模型类型
     * @return 导入的表ID列表
     */
    List<Long> importTablesFromDatabase(Long dataSourceConfigId, List<String> tableNames, String moduleName, 
                                       String packageName, Integer sceneType, Integer modelType);
    
    /**
     * 导入单个表从数据库
     * @param dataSourceConfigId 数据源配置ID
     * @param tableName 表名
     * @param moduleName 模块名
     * @param packageName 包名
     * @param sceneType 场景类型
     * @param modelType 模型类型
     * @return 导入的表ID
     */
    Long importTableFromDatabase(Long dataSourceConfigId, String tableName, String moduleName, 
                               String packageName, Integer sceneType, Integer modelType);
    
    /**
     * 同步表结构从数据库
     * @param id 表配置ID
     */
    void syncTableFromDatabase(Long id);
    
    /**
     * 更新表定义配置
     * @param request 更新请求
     */
    void updateCodegenTable(CodegenTableRequest request);
    
    /**
     * 删除表
     * @param tableId 表ID
     */
    void deleteTable(Long tableId);
    
    /**
     * 根据数据源ID获取代码生成表配置
     * @param dataSourceConfigId 数据源配置ID
     * @return 代码生成表配置列表
     */
    List<CodegenTable> getCodegenTablesByDataSourceId(Long dataSourceConfigId);

    /**
     * 按数据源与物理表名解析已导入的代码生成配置
     */
    Optional<CodegenTable> findCodegenTable(Long datasourceId, String tableName);

    /**
     * 分页查询代码生成表配置（应用层组装 DTO）
     */
    PageResult<CodegenTableResponse> pageCodegenTables(CodegenTablePageRequest request);
}