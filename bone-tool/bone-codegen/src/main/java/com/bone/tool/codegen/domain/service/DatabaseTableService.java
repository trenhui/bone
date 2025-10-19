package com.bone.tool.codegen.domain.service;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import com.bone.tool.codegen.infrastructure.util.ReflectionUtil;
import com.bone.core.model.PageResult;
// 移除不存在的Criteria导入
// import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tool.codegen.application.dto.CodegenTablePageRequest;
import com.bone.tool.codegen.application.dto.CodegenTableRequest;
import com.bone.tool.codegen.application.dto.CodegenDetailResponse;
import com.bone.tool.codegen.application.converter.CodegenConverter;
import com.bone.tool.codegen.domain.entity.Datasource;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.entity.DatabaseTableMetadata;
import com.bone.tool.codegen.domain.repository.DataSourceConfigRepository;
import com.bone.tool.codegen.domain.repository.CodegenTableRepository;
import com.bone.tool.codegen.domain.repository.CodegenColumnRepository;
import com.bone.tool.codegen.domain.repository.DatabaseTableRepository;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.bone.tool.codegen.domain.enums.ErrorCodeConstants.DATA_SOURCE_CONFIG_NOT_OK;

/**
 * 数据库表领域服务
 * 负责数据库表结构信息的获取、解析和处理，为代码生成提供底层数据源支持
 * 支持多数据库类型的表信息查询
 *
 * @author bone-team
 */
@Service
public class DatabaseTableService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTableService.class);

    @Resource
    private DataSourceConfigRepository dataSourceConfigRepository;
    
    @Resource
    private CodegenTableRepository codegenTableRepository;
    
    @Resource
    private CodegenColumnRepository codegenColumnRepository;
    
    @Resource
    private DatabaseTableRepository databaseTableRepository;
    
    @Resource
    private CodegenConverter codegenConverter;

    /**
     * 获取数据库表列表
     * <p>
     * 基于表名称和表描述进行模糊匹配，从指定数据源获取表信息
     *
     * @param dataSourceConfigId 数据源配置ID
     * @param nameLike 表名称（模糊匹配）
     * @param commentLike 表描述（模糊匹配）
     * @return 表信息列表
     * @throws IllegalArgumentException 当数据源配置ID为空时抛出
     */
    public List<DatabaseTableMetadata> getTableList(Long dataSourceConfigId, String nameLike, String commentLike) {
        Assert.notNull(dataSourceConfigId, "数据源ID不能为空");
        
        List<DatabaseTableMetadata> tableInfoList = getTableList0(dataSourceConfigId, null);
        
        // 根据条件过滤
        if (StringUtils.hasText(nameLike)) {
            tableInfoList = tableInfoList.stream()
                    .filter(table -> table != null && table.getName() != null &&
                            table.getName().toLowerCase().contains(nameLike.toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        if (StringUtils.hasText(commentLike)) {
            tableInfoList = tableInfoList.stream()
                    .filter(table -> table != null && StringUtils.hasText(table.getComment()) && 
                            table.getComment().toLowerCase().contains(commentLike.toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        return tableInfoList;
    }
    
    /**
     * 批量获取指定表信息
     * <p>
     * 支持自定义选择多个表获取详细信息
     *
     * @param dataSourceConfigId 数据源配置ID
     * @param tableNames 表名列表
     * @return 表信息列表
     * @throws IllegalArgumentException 当数据源配置ID为空时抛出
     */
    public List<DatabaseTableMetadata> getTables(Long dataSourceConfigId, List<String> tableNames) {
        Assert.notNull(dataSourceConfigId, "数据源配置ID不能为空");
        if (CollectionUtils.isEmpty(tableNames)) {
            return Collections.emptyList();
        }
        
        List<DatabaseTableMetadata> tableInfos = new ArrayList<>(tableNames.size());
        for (String tableName : tableNames) {
            if (StringUtils.hasText(tableName)) {
                DatabaseTableMetadata tableInfo = getTable(dataSourceConfigId, tableName);
                if (tableInfo != null) {
                    tableInfos.add(tableInfo);
                }
            }
        }
        
        return tableInfos;
    }
    
    /**
     * 内部方法：获取表列表的具体实现
     * @param dataSourceConfigId 数据源配置ID
     * @param schema 数据库模式
     * @return 表信息列表
     */
    private List<DatabaseTableMetadata> getTableList0(Long dataSourceConfigId, String schema) {
        try {
            return databaseTableRepository.getTableList(dataSourceConfigId, schema);
        } catch (Exception e) {
            log.error("获取表列表失败，数据源ID: {}", dataSourceConfigId, e);
            // 出错时返回空列表，避免上层调用失败
            return Collections.emptyList();
        }
    }
    
    /**
     * 内部方法：获取单个表信息的具体实现
     * @param dataSourceConfigId 数据源配置ID
     * @param tableName 表名
     * @return 表信息
     */
    private DatabaseTableMetadata getTable(Long dataSourceConfigId, String tableName) {
        try {
            return databaseTableRepository.getTableInfo(dataSourceConfigId, tableName);
        } catch (Exception e) {
            log.error("获取表信息失败，表名: {}, 数据源ID: {}", tableName, dataSourceConfigId, e);
            return null; // 表不存在或查询失败时返回null
        }
    }
    
    /**
     * 根据数据源配置ID获取表定义列表
     * @param dataSourceConfigId 数据源配置ID
     * @return 表定义列表
     */
    public List<CodegenTable> getCodegenTablesByDataSourceId(Long dataSourceConfigId) {
        if (dataSourceConfigId == null) {
            return Collections.emptyList();
        }
        // 简化实现，返回空列表
        // 在实际应用中应该根据dataSourceConfigId查询表配置
        return Collections.emptyList();
    }
    
    /**
     * 获取代码生成表分页响应
     * @param request 分页请求
     * @return 分页结果
     */
    public PageResult<CodegenTable> getCodegenTablePageResponse(CodegenTablePageRequest request) {
        Assert.notNull(request, "请求参数不能为空");
        
        // 简化实现，使用静态工厂方法
        // 在实际应用中应该实现真正的分页查询逻辑
        try {
            return PageResult.of(Collections.emptyList(), 0L, 1, 10);
        } catch (Exception e) {
            throw new RuntimeException("暂不支持分页查询");
        }
    }
    
    /**
     * 获取表定义详情
     * @param tableId 表ID
     * @return 表定义详情响应
     * @throws IllegalArgumentException 当表ID为空时抛出
     * @throws RuntimeException 当表配置不存在时抛出
     */
    public CodegenDetailResponse getCodegenDetail(Long tableId) {
        Assert.notNull(tableId, "表ID不能为空");
        
        // 获取表配置
        CodegenTable codegenTable = codegenTableRepository.findById(tableId)
            .orElseThrow(() -> new RuntimeException("表配置不存在，ID: " + tableId));
        
        // 获取字段列表
        List<CodegenColumn> columns = getColumnsByTableId(tableId);
        
        // 构建详情响应
        CodegenDetailResponse response = new CodegenDetailResponse();
        ReflectionUtil.setFieldValue(response, "table", codegenConverter.toCodegenTableResponse(codegenTable));
        ReflectionUtil.setFieldValue(response, "columns", codegenConverter.toCodegenColumnResponseList(columns));
        
        return response;
    }
    
    /**
     * 从数据库导入单个表结构
     * @param dataSourceConfigId 数据源配置ID
     * @param tableName 表名
     * @param moduleName 模块名
     * @param packageName 包名
     * @param sceneType 场景类型
     * @param modelType 模型类型
     * @return 导入的表ID
     * @throws IllegalArgumentException 当必要参数为空时抛出
     * @throws RuntimeException 当导入失败时抛出
     */
    public Long importTableFromDatabase(Long dataSourceConfigId, String tableName, String moduleName,
                                       String packageName, Integer sceneType, Integer modelType) {
        Assert.notNull(dataSourceConfigId, "数据源配置ID不能为空");
        Assert.hasText(tableName, "表名不能为空");
        Assert.hasText(moduleName, "模块名不能为空");
        Assert.hasText(packageName, "包名不能为空");
        
        try {
            // 获取数据库表信息
            DatabaseTableMetadata tableInfo = getTable(dataSourceConfigId, tableName);
            if (tableInfo == null) {
                throw new RuntimeException("表不存在: " + tableName);
            }
            
            // 创建代码生成表配置
            CodegenTable codegenTable = new CodegenTable();
            ReflectionUtil.setFieldValue(codegenTable, "datasourceId", dataSourceConfigId);
            ReflectionUtil.setFieldValue(codegenTable, "tableName", tableName);
            String tableComment = (String) ReflectionUtil.getFieldValue(tableInfo, "tableComment");
            ReflectionUtil.setFieldValue(codegenTable, "tableComment", tableComment);
            ReflectionUtil.setFieldValue(codegenTable, "moduleName", moduleName);
            ReflectionUtil.setFieldValue(codegenTable, "packageName", packageName);
            ReflectionUtil.setFieldValue(codegenTable, "scene", sceneType);
            ReflectionUtil.setFieldValue(codegenTable, "templateType", modelType);
            ReflectionUtil.setFieldValue(codegenTable, "createTime", new java.util.Date());
            ReflectionUtil.setFieldValue(codegenTable, "updateTime", new java.util.Date());
            
            // 保存表配置
            Long savedTableId = codegenTableRepository.save(codegenTable);
            
            // 导入字段信息
            importColumns(savedTableId, tableInfo.getFields());
            
            return savedTableId;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("导入表失败: {}", tableName, e);
            throw new RuntimeException("导入表失败: " + tableName, e);
        }
    }
    
    /**
     * 从数据库导入表结构
     * @param dataSourceConfigId 数据源配置ID
     * @param tableNames 表名列表
     * @param moduleName 模块名
     * @param packageName 包名
     * @param sceneType 场景类型
     * @param modelType 模型类型
     * @return 导入的表ID列表
     * @throws IllegalArgumentException 当必要参数为空时抛出
     * @throws RuntimeException 当导入失败时抛出
     */
    public List<Long> importTablesFromDatabase(Long dataSourceConfigId, List<String> tableNames,
                                             String moduleName, String packageName,
                                             Integer sceneType, Integer modelType) {
        Assert.notNull(dataSourceConfigId, "数据源配置ID不能为空");
        Assert.notEmpty(tableNames, "表名列表不能为空");
        Assert.hasText(moduleName, "模块名不能为空");
        Assert.hasText(packageName, "包名不能为空");
        
        List<Long> tableIds = new ArrayList<>(tableNames.size());
        
        for (String tableName : tableNames) {
            if (!StringUtils.hasText(tableName)) {
                log.warn("跳过空表名");
                continue;
            }
            
            try {
                // 获取数据库表信息
                DatabaseTableMetadata tableInfo = getTable(dataSourceConfigId, tableName);
                if (tableInfo == null) {
                    log.warn("跳过不存在的表: {}", tableName);
                    continue;
                }
                
                // 创建代码生成表配置
                CodegenTable codegenTable = new CodegenTable();
                ReflectionUtil.setFieldValue(codegenTable, "datasourceId", dataSourceConfigId);
                ReflectionUtil.setFieldValue(codegenTable, "tableName", tableName);
                String tableComment = (String) ReflectionUtil.getFieldValue(tableInfo, "comment");
                ReflectionUtil.setFieldValue(codegenTable, "tableComment", tableComment);
                ReflectionUtil.setFieldValue(codegenTable, "moduleName", moduleName);
                ReflectionUtil.setFieldValue(codegenTable, "packageName", packageName);
                ReflectionUtil.setFieldValue(codegenTable, "scene", sceneType);
                ReflectionUtil.setFieldValue(codegenTable, "templateType", modelType);
                ReflectionUtil.setFieldValue(codegenTable, "createTime", new java.util.Date());
                ReflectionUtil.setFieldValue(codegenTable, "updateTime", new java.util.Date());
                
                // 保存表配置
                Long savedTableId = codegenTableRepository.save(codegenTable);
                
                // 导入字段信息
                importColumns(savedTableId, tableInfo.getFields());
                
                tableIds.add(savedTableId);
            } catch (Exception e) {
                log.error("导入表失败: {}", tableName, e);
                throw new RuntimeException("导入表失败: " + tableName, e);
            }
        }
        
        return tableIds;
    }
    
    /**
     * 导入表字段
     * @param tableId 表ID
     * @param fields 字段列表
     */
    private void importColumns(Long tableId, List<CodegenColumn> fields) {
        if (CollectionUtils.isEmpty(fields)) {
            return;
        }
        
        for (CodegenColumn field : fields) {
            ReflectionUtil.setFieldValue(field, "tableId", tableId);
            codegenColumnRepository.save(field);
        }
    }
    
    /**
     * 更新表定义配置
     * @param request 更新请求
     * @throws IllegalArgumentException 当请求参数无效时抛出
     * @throws RuntimeException 当表配置不存在时抛出
     */
    public void updateCodegenTable(CodegenTableRequest request) {
        // 验证参数
        Assert.notNull(request, "请求参数不能为空");
        Long id = (Long) ReflectionUtil.getFieldValue(request, "id");
        Assert.notNull(id, "表ID不能为空");
        // 使用反射获取字段值
        String moduleName = (String) ReflectionUtil.getFieldValue(request, "moduleName");
        String packageName = (String) ReflectionUtil.getFieldValue(request, "packageName");
        Assert.hasText(moduleName, "模块名不能为空");
        Assert.hasText(packageName, "包名不能为空");
        
        // 获取原有表配置
        CodegenTable codegenTable = codegenTableRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("表配置不存在"));
        
        // 更新表配置
        // 使用反射获取和设置字段值
        String className = (String) ReflectionUtil.getFieldValue(request, "className");
        Integer templateType = (Integer) ReflectionUtil.getFieldValue(request, "templateType");
        
        ReflectionUtil.setFieldValue(codegenTable, "moduleName", moduleName);
        ReflectionUtil.setFieldValue(codegenTable, "packageName", packageName);
        ReflectionUtil.setFieldValue(codegenTable, "className", className);
        ReflectionUtil.setFieldValue(codegenTable, "scene", templateType); // 使用templateType作为scene值
        ReflectionUtil.setFieldValue(codegenTable, "templateType", templateType);
        ReflectionUtil.setFieldValue(codegenTable, "updateTime", new java.util.Date());
        
        codegenTableRepository.update(codegenTable);
    }
    
    /**
     * 同步数据库表结构到代码生成配置
     * @param id 表配置ID
     * @throws IllegalArgumentException 当表配置ID为空时抛出
     * @throws RuntimeException 当同步失败时抛出
     */
    public void syncTableFromDatabase(Long id) {
        Assert.notNull(id, "表配置ID不能为空");

        try {
            CodegenTable codegenTable = codegenTableRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("表配置不存在，ID: {}", id);
                    return new RuntimeException("表配置不存在，ID: " + id);
                });

            // 从数据库获取最新表结构
            Long datasourceId = (Long) ReflectionUtil.getFieldValue(codegenTable, "datasourceId");
            String tableName = (String) ReflectionUtil.getFieldValue(codegenTable, "tableName");
            
            DatabaseTableMetadata tableInfo = getTable(datasourceId, tableName);

            if (tableInfo == null) {
                log.error("数据库表不存在: {}", tableName);
                throw new RuntimeException("数据库表不存在: " + tableName);
            }

            // 同步表配置
            syncCodegen0(codegenTable, tableInfo);

            log.info("成功同步表结构，表ID: {}, 表名: {}", id, tableName);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("同步表结构失败: {}", e.getMessage(), e);
            throw new RuntimeException("同步表结构失败：" + e.getMessage());
        }
    }
    
    /**
     * 执行同步逻辑
     * @param codegenTable 代码生成表配置
     * @param tableInfo 表信息
     */
    private void syncCodegen0(CodegenTable codegenTable, DatabaseTableMetadata tableInfo) {
        try {
            // 更新表信息
            boolean hasUpdate = false;
            String currentComment = (String) ReflectionUtil.getFieldValue(codegenTable, "tableComment");
            String newComment = (String) ReflectionUtil.getFieldValue(tableInfo, "comment");
            if (!StringUtils.pathEquals(currentComment, newComment)) {
                ReflectionUtil.setFieldValue(codegenTable, "tableComment", newComment);
                hasUpdate = true;
            }

            if (hasUpdate) {
                codegenTable.setUpdateTime(new java.util.Date());
                codegenTableRepository.update(codegenTable);
            }

            // 同步字段
            syncColumns(codegenTable.getId(), tableInfo.getFields());
        } catch (Exception e) {
            log.error("同步表结构细节失败: {}", e.getMessage(), e);
            throw new RuntimeException("同步失败：" + e.getMessage());
        }
    }
    
    /**
     * 同步表的字段信息
     * @param tableId 表ID
     * @param tableFields 表字段列表
     */
    private void syncColumns(Long tableId, List<CodegenColumn> tableFields) {
        // 获取现有字段
        List<CodegenColumn> existingColumns = getColumnsByTableId(tableId);
        Map<String, CodegenColumn> columnMap = buildColumnMap(existingColumns);

        // 统计信息
        int addedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;

        // 处理新增和更新的字段
        if (!CollectionUtils.isEmpty(tableFields)) {
            for (CodegenColumn field : tableFields) {
                if (field != null) {
                    String columnName = (String) ReflectionUtil.getFieldValue(field, "columnName");
                    if (StringUtils.hasText(columnName)) {
                        CodegenColumn column = columnMap.get(columnName);

                    if (column == null) {
                        // 新增字段
                        column = createCodegenColumn(tableId, field);
                        codegenColumnRepository.save(column);
                        addedCount++;
                    } else {
                        // 更新字段
                        updateColumn(column, field);
                        updatedCount++;
                        // 从映射中移除，剩余的就是需要删除的
                        columnMap.remove(columnName);
                    }
                    }
                }
            }
        }

        // 删除不再存在的字段
        deletedCount = deleteObsoleteColumns(columnMap);

        log.debug("表ID: {} 的字段同步完成，新增: {} 个，更新: {} 个，删除: {} 个",
                tableId, addedCount, updatedCount, deletedCount);
    }
    
    /**
     * 构建字段映射表
     * @param columns 字段列表
     * @return 字段名到字段对象的映射
     */
    private Map<String, CodegenColumn> buildColumnMap(List<CodegenColumn> columns) {
        Map<String, CodegenColumn> columnMap = new HashMap<>(columns.size());
        for (CodegenColumn column : columns) {
            if (column != null) {
                String columnName = (String) ReflectionUtil.getFieldValue(column, "columnName");
                if (StringUtils.hasText(columnName)) {
                    columnMap.put(columnName, column);
                }
            }
        }
        return columnMap;
    }
    
    /**
     * 删除过时的字段
     * @param obsoleteColumns 过时的字段映射
     * @return 删除的字段数量
     */
    private int deleteObsoleteColumns(Map<String, CodegenColumn> obsoleteColumns) {
        int deletedCount = 0;
        for (CodegenColumn column : obsoleteColumns.values()) {
            try {
                Long columnId = (Long) ReflectionUtil.getFieldValue(column, "id");
                codegenColumnRepository.deleteById(columnId);
                deletedCount++;
            } catch (Exception e) {
                String columnName = (String) ReflectionUtil.getFieldValue(column, "columnName");
                Long columnId = (Long) ReflectionUtil.getFieldValue(column, "id");
                log.warn("删除字段失败: {} (ID: {})", columnName, columnId, e);
                // 继续删除其他字段，单个字段删除失败不应影响整体操作
            }
        }
        return deletedCount;
    }
    
    /**
     * 获取表字段列表
     * @param tableId 表ID
     * @return 字段列表
     */
    public List<CodegenColumn> getColumnsByTableId(Long tableId) {
        if (tableId == null) {
            return Collections.emptyList();
        }
        // 使用findByCriteria方法查询字段列表
        // 在测试环境中，这个方法会被模拟返回测试数据
        try {
            return codegenColumnRepository.findByCriteria(tableId);
        } catch (Exception e) {
            log.error("获取表字段列表失败，表ID: {}", tableId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 创建代码生成字段配置
     * @param tableId 表ID
     * @param sourceColumn 源字段
     * @return 代码生成字段配置
     */
    private CodegenColumn createCodegenColumn(Long tableId, CodegenColumn sourceColumn) {
        CodegenColumn column = new CodegenColumn();
        
        // 使用反射设置字段值
        ReflectionUtil.setFieldValue(column, "tableId", tableId);
        
        // 从sourceColumn获取字段值
        String columnName = (String) ReflectionUtil.getFieldValue(sourceColumn, "columnName");
        String dataType = (String) ReflectionUtil.getFieldValue(sourceColumn, "dataType");
        String sourceJavaType = (String) ReflectionUtil.getFieldValue(sourceColumn, "javaType");
        String columnComment = (String) ReflectionUtil.getFieldValue(sourceColumn, "columnComment");
        Boolean primaryKey = (Boolean) ReflectionUtil.getFieldValue(sourceColumn, "primaryKey");
        Boolean autoIncrement = (Boolean) ReflectionUtil.getFieldValue(sourceColumn, "autoIncrement");
        
        // 设置到新创建的column对象
        ReflectionUtil.setFieldValue(column, "columnName", columnName);
        ReflectionUtil.setFieldValue(column, "dataType", dataType);
        ReflectionUtil.setFieldValue(column, "javaType", sourceJavaType);
        ReflectionUtil.setFieldValue(column, "columnComment", columnComment);
        ReflectionUtil.setFieldValue(column, "primaryKey", primaryKey);
        ReflectionUtil.setFieldValue(column, "autoIncrement", autoIncrement);
        
        // 设置默认值
        ReflectionUtil.setFieldValue(column, "enableCreate", true);
        ReflectionUtil.setFieldValue(column, "enableUpdate", primaryKey != null && !primaryKey); // 主键不能更新
        ReflectionUtil.setFieldValue(column, "enableQuery", true);
        ReflectionUtil.setFieldValue(column, "showInList", true);
        ReflectionUtil.setFieldValue(column, "htmlType", "input");
        
        return column;
    }
    
    /**
     * 更新字段配置
     * @param column 目标字段
     * @param sourceColumn 源字段
     */
    private void updateColumn(CodegenColumn column, CodegenColumn sourceColumn) {
        boolean hasUpdate = false;
        
        // 比较并更新dataType字段
        String columnDataType = (String) ReflectionUtil.getFieldValue(column, "dataType");
        String sourceDataType = (String) ReflectionUtil.getFieldValue(sourceColumn, "dataType");
        if (!StringUtils.pathEquals(columnDataType, sourceDataType)) {
            ReflectionUtil.setFieldValue(column, "dataType", sourceDataType);
            hasUpdate = true;
        }
        
        // 比较并更新columnComment字段
        String columnComment = (String) ReflectionUtil.getFieldValue(column, "columnComment");
        String sourceColumnComment = (String) ReflectionUtil.getFieldValue(sourceColumn, "columnComment");
        if (!StringUtils.pathEquals(columnComment, sourceColumnComment)) {
            ReflectionUtil.setFieldValue(column, "columnComment", sourceColumnComment);
            hasUpdate = true;
        }
        
        // 比较并更新primaryKey字段
        Boolean sourcePrimaryKey = (Boolean) ReflectionUtil.getFieldValue(sourceColumn, "primaryKey");
        Boolean columnPrimaryKey = (Boolean) ReflectionUtil.getFieldValue(column, "primaryKey");
        if (sourcePrimaryKey != null && !sourcePrimaryKey.equals(columnPrimaryKey)) {
            ReflectionUtil.setFieldValue(column, "primaryKey", sourcePrimaryKey);
            hasUpdate = true;
        }
        
        // 更新Java类型
        String columnJavaType = (String) ReflectionUtil.getFieldValue(column, "javaType");
        String sourceColumnJavaType = (String) ReflectionUtil.getFieldValue(sourceColumn, "javaType");
        if (!StringUtils.pathEquals(columnJavaType, sourceColumnJavaType)) {
            ReflectionUtil.setFieldValue(column, "javaType", sourceColumnJavaType);
            hasUpdate = true;
        }
        
        // 比较并更新autoIncrement字段
        Boolean sourceAutoIncrement = (Boolean) ReflectionUtil.getFieldValue(sourceColumn, "autoIncrement");
        Boolean columnAutoIncrement = (Boolean) ReflectionUtil.getFieldValue(column, "autoIncrement");
        if (sourceAutoIncrement != null && !sourceAutoIncrement.equals(columnAutoIncrement)) {
            ReflectionUtil.setFieldValue(column, "autoIncrement", sourceAutoIncrement);
            hasUpdate = true;
        }
        
        // 如果有更新，则保存
        if (hasUpdate) {
            codegenColumnRepository.update(column);
        }
    }
    
    /**
     * 删除表配置
     * @param tableId 表ID
     * @throws IllegalArgumentException 当表ID为空时抛出
     */
    public void deleteTable(Long tableId) {
        Assert.notNull(tableId, "表ID不能为空");
        
        try {
            // 先删除相关的字段配置
            List<CodegenColumn> columns = getColumnsByTableId(tableId);
            if (!CollectionUtils.isEmpty(columns)) {
                for (CodegenColumn column : columns) {
                    Long columnId = (Long) ReflectionUtil.getFieldValue(column, "id");
                    if (columnId != null) {
                        codegenColumnRepository.deleteById(columnId);
                    }
                }
            }
            
            // 然后删除表配置
            codegenTableRepository.deleteById(tableId);
        } catch (Exception e) {
            log.error("删除表配置失败，表ID: {}", tableId, e);
            throw new RuntimeException("删除表配置失败: " + e.getMessage(), e);
        }
    }
}
