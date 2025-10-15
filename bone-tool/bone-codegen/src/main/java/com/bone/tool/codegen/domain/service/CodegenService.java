package com.bone.tool.codegen.domain.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.bone.core.model.PageResult;
import com.bone.tool.codegen.application.dto.*;
import com.bone.tool.codegen.domain.entity.*;
import com.bone.tool.codegen.domain.repository.CodegenColumnRepository;
import com.bone.tool.codegen.domain.repository.CodegenTableRepository;
import com.bone.tool.codegen.infrastructure.config.CodegenEngine;
import com.bone.tool.codegen.domain.enums.ModelTypeEnum;
import com.bone.metadata.sdk.DataSourceConfigService;
import com.bone.metadata.sdk.query.criteria.Criteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * 代码生成服务
 * 提供代码生成相关的核心业务逻辑
 *
 * @author bone-team
 */
@Service
public class CodegenService {

    private static final Logger log = LoggerFactory.getLogger(CodegenService.class);

    private final CodegenTableRepository codegenTableRepository;
    private final CodegenColumnRepository codegenColumnRepository;
    private final CodegenEngine codegenEngine;
    private final DatabaseTableService databaseTableService;
    private final DataSourceConfigService dataSourceConfigService;
    private final TemplateEngine templateEngine;

    /**
     * 构造函数 - 使用构造函数注入，提高代码可测试性
     */
    @Autowired
    public CodegenService(
            CodegenTableRepository codegenTableRepository,
            CodegenColumnRepository codegenColumnRepository,
            CodegenEngine codegenEngine,
            DatabaseTableService databaseTableService,
            DataSourceConfigService dataSourceConfigService,
            TemplateEngine templateEngine) {
        this.codegenTableRepository = codegenTableRepository;
        this.codegenColumnRepository = codegenColumnRepository;
        this.codegenEngine = codegenEngine;
        this.databaseTableService = databaseTableService;
        this.dataSourceConfigService = dataSourceConfigService;
        this.templateEngine = templateEngine;
    }

    /**
     * 更新表配置
     * 
     * @param request 表配置更新请求
     * @throws IllegalArgumentException 当请求参数无效时抛出
     * @throws RuntimeException 当更新表配置失败时抛出
     */
    public void updateCodegenTable(CodegenUpdateRequest request) {
        // 参数验证
        validateCodegenUpdateRequest(request);
        
        try {
            Long tableId = request.getTable().getId();
            
            // 获取原表配置
            CodegenTable existingTable = codegenTableRepository.findById(tableId);
            if (existingTable == null) {
                log.error("表配置不存在，ID: {}", tableId);
                throw new RuntimeException("表配置不存在: " + tableId);
            }
            
            // 更新表配置
            CodegenTable codegenTable = BeanUtil.copyProperties(request.getTable(), CodegenTable.class);
            codegenTable.setId(tableId);
            // 保留创建时间等不可修改字段
            // 注意：这里假设CodegenTable实体类有相应的时间字段和setter方法
            // 如果实际字段名称不同，请根据实际情况调整
            
            codegenTableRepository.update(codegenTable);
            
            // 更新列配置
            updateCodegenColumns(tableId, request.getColumns());
            
            log.info("成功更新表配置，ID: {}", tableId);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新表配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("更新表配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证表配置更新请求参数
     * 
     * @param request 表配置更新请求
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    private void validateCodegenUpdateRequest(CodegenUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (request.getTable() == null) {
            throw new IllegalArgumentException("表配置信息不能为空");
        }
        if (request.getTable().getId() == null) {
            throw new IllegalArgumentException("表配置ID不能为空");
        }
    }
    
    /**
     * 更新表的列配置
     * 
     * @param tableId 表ID
     * @param columnRequests 列配置请求列表
     */
    private void updateCodegenColumns(Long tableId, List<CodegenColumnSaveRequest> columnRequests) {
        if (columnRequests != null) {
            for (CodegenColumnSaveRequest columnRequest : columnRequests) {
                CodegenColumn column = BeanUtil.copyProperties(columnRequest, CodegenColumn.class);
                column.setTableId(tableId);
                if (columnRequest.getId() != null) {
                    // 更新现有列
                    codegenColumnRepository.update(column);
                } else {
                    // 新增列
                    codegenColumnRepository.save(column);
                }
            }
        }
    }

    /**
     * 从数据库导入表结构
     * 
     * @param dataSourceConfigId 数据源配置ID
     * @param tableName 表名
     * @param moduleName 模块名称
     * @param packageName 包名称
     * @param scene 场景类型
     * @param modelType 模型类型
     * @return 导入的表配置ID
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws RuntimeException 当导入表结构失败时抛出
     */
    public Long importTableFromDB(Long dataSourceConfigId, String tableName, String moduleName, 
                                 String packageName, Integer scene, Integer modelType) {
        // 参数验证
        validateImportTableParams(dataSourceConfigId, tableName, moduleName, packageName);
        
        try {
            // 1. 获取数据库表信息
            TableInfo tableInfo = databaseTableService.getTable(dataSourceConfigId, tableName);
            if (tableInfo == null) {
                log.error("数据库表不存在: {}", tableName);
                throw new RuntimeException("数据库表不存在: " + tableName);
            }
            
            // 检查是否已存在相同表名的配置
            checkDuplicateTable(tableName);
            
            // 2. 创建表配置
            CodegenTable codegenTable = new CodegenTable();
            codegenTable.setDataSourceConfigId(dataSourceConfigId);
            codegenTable.setTableName(tableInfo.getName());
            codegenTable.setTableComment(tableInfo.getComment());
            codegenTable.setModuleName(moduleName);
            codegenTable.setPackgeName(packageName);
            codegenTable.setScene(scene);
            codegenTable.setClassName(tableInfo.getEntityName());
            codegenTable.setBusinessName(tableInfo.getComment());
            codegenTable.setTemplateType(1); // 默认普通模板
            codegenTable.setCreateTime(new Date());
            codegenTable.setUpdateTime(new Date());
            
            // 3. 保存表配置
            Long tableId = codegenTableRepository.save(codegenTable);
            
            // 4. 创建列配置
            if (CollUtil.isNotEmpty(tableInfo.getFields())) {
                saveCodegenColumns(tableId, tableInfo.getFields());
            }
            
            log.info("导入表结构成功: {}, 表ID: {}", tableName, tableId);
            return tableId;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("导入表结构失败: {}", e.getMessage(), e);
            throw new RuntimeException("导入表结构失败: " + e.getMessage());
        }
    }
    
    /**
     * 验证导入表结构的参数
     */
    private void validateImportTableParams(Long dataSourceConfigId, String tableName, String moduleName, String packageName) {
        if (dataSourceConfigId == null) {
            throw new IllegalArgumentException("数据源配置ID不能为空");
        }
        if (StrUtil.isEmpty(tableName)) {
            throw new IllegalArgumentException("表名不能为空");
        }
        if (StrUtil.isEmpty(moduleName)) {
            throw new IllegalArgumentException("模块名称不能为空");
        }
        if (StrUtil.isEmpty(packageName)) {
            throw new IllegalArgumentException("包名称不能为空");
        }
    }
    
    /**
     * 检查是否存在重复的表配置
     */
    private void checkDuplicateTable(String tableName) {
        Criteria<CodegenTable> criteria = Criteria.<CodegenTable>create()
                .eq("tableName", tableName);
        List<CodegenTable> existingTables = codegenTableRepository.findByCriteria(criteria);
        if (CollUtil.isNotEmpty(existingTables)) {
            throw new RuntimeException("已存在相同表名的配置: " + tableName);
        }
    }
    
    /**
     * 批量保存代码生成列配置
     */
    private void saveCodegenColumns(Long tableId, List<TableField> fields) {
        for (TableField field : fields) {
            CodegenColumn column = createCodegenColumn(tableId, field);
            codegenColumnRepository.save(column);
        }
    }

    /**
     * 批量从数据库导入表结构
     * 
     * @param dataSourceConfigId 数据源配置ID
     * @param tableNames 表名列表
     * @param moduleName 模块名称
     * @param packageName 包名称
     * @param scene 场景类型
     * @param modelType 模型类型
     * @return 导入的表配置ID列表
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws RuntimeException 当批量导入表结构失败时抛出
     */
    public List<Long> importTablesFromDB(Long dataSourceConfigId, List<String> tableNames, 
                                        String moduleName, String packageName, Integer scene, Integer modelType) {
        if (CollUtil.isEmpty(tableNames)) {
            throw new IllegalArgumentException("表名列表不能为空");
        }
        
        List<Long> tableIds = new ArrayList<>(tableNames.size());
        List<String> failedTables = new ArrayList<>();
        
        for (String tableName : tableNames) {
            try {
                Long tableId = importTableFromDB(dataSourceConfigId, tableName, moduleName, packageName, scene, modelType);
                tableIds.add(tableId);
            } catch (Exception e) {
                failedTables.add(tableName);
                log.error("导入表 {} 失败: {}", tableName, e.getMessage(), e);
                // 继续导入其他表，不中断整体操作
            }
        }
        
        if (CollUtil.isNotEmpty(failedTables)) {
            throw new RuntimeException("部分表导入失败: " + String.join(", ", failedTables));
        }
        
        log.info("批量导入表结构完成，成功导入 {} 张表", tableIds.size());
        return tableIds;
    }

    /**
     * 同步数据库表结构到代码生成配置
     * 
     * @param id 表配置ID
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws RuntimeException 当同步表结构失败时抛出
     */
    public void syncCodegenFromDB(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("表配置ID不能为空");
        }
        
        try {
            CodegenTable codegenTable = codegenTableRepository.findById(id);
            if (codegenTable == null) {
                log.error("表配置不存在，ID: {}", id);
                throw new RuntimeException("表配置不存在");
            }
            
            // 从数据库获取最新表结构
            TableInfo tableInfo = databaseTableService.getTable(
                    codegenTable.getDataSourceConfigId(), 
                    codegenTable.getTableName());
            
            if (tableInfo == null) {
                log.error("数据库表不存在: {}", codegenTable.getTableName());
                throw new RuntimeException("数据库表不存在: " + codegenTable.getTableName());
            }
            
            // 同步表配置
            syncCodegen0(codegenTable, tableInfo);
            
            log.info("成功同步表结构，表ID: {}, 表名: {}", id, codegenTable.getTableName());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("同步表结构失败: {}", e.getMessage(), e);
            throw new RuntimeException("同步表结构失败：" + e.getMessage());
        }
    }

    /**
     * 执行同步逻辑
     */
    private void syncCodegen0(CodegenTable codegenTable, TableInfo tableInfo) {
        try {
            // 更新表信息
            boolean hasUpdate = false;
            if (!StrUtil.equals(codegenTable.getTableComment(), tableInfo.getComment())) {
                codegenTable.setTableComment(tableInfo.getComment());
                hasUpdate = true;
            }
            
            if (hasUpdate) {
                codegenTable.setUpdateTime(new Date());
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
     * 
     * @param tableId 表ID
     * @param tableFields 数据库表字段列表
     */
    private void syncColumns(Long tableId, List<TableField> tableFields) {
        // 获取现有字段
        List<CodegenColumn> existingColumns = getColumnsByTableId(tableId);
        Map<String, CodegenColumn> columnMap = buildColumnMap(existingColumns);
        
        // 统计信息
        int addedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;
        
        // 处理新增和更新的字段
        if (CollUtil.isNotEmpty(tableFields)) {
            for (TableField field : tableFields) {
                String columnName = field.getName();
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
        
        // 删除不再存在的字段
        deletedCount = deleteObsoleteColumns(columnMap);
        
        log.debug("表ID: {} 的字段同步完成，新增: {} 个，更新: {} 个，删除: {} 个", 
                tableId, addedCount, updatedCount, deletedCount);
    }
    
    /**
     * 构建字段映射表
     */
    private Map<String, CodegenColumn> buildColumnMap(List<CodegenColumn> columns) {
        Map<String, CodegenColumn> columnMap = new HashMap<>(columns.size());
        for (CodegenColumn column : columns) {
            columnMap.put(column.getColumnName(), column);
        }
        return columnMap;
    }
    
    /**
     * 删除过时的字段
     */
    private int deleteObsoleteColumns(Map<String, CodegenColumn> obsoleteColumns) {
        int deletedCount = 0;
        for (CodegenColumn column : obsoleteColumns.values()) {
            try {
                codegenColumnRepository.deleteById(column.getId());
                deletedCount++;
            } catch (Exception e) {
                log.warn("删除字段失败: {} (ID: {})", column.getColumnName(), column.getId(), e);
                // 继续删除其他字段，单个字段删除失败不应影响整体操作
            }
        }
        return deletedCount;
    }

    /**
     * 根据数据源配置ID获取表定义列表
     */
    public List<CodegenTable> getCodegenTablesByDataSourceId(Long dataSourceConfigId) {
        if (dataSourceConfigId == null) {
            return Collections.emptyList();
        }
        // 使用Criteria构建查询条件
        Criteria<CodegenTable> criteria = Criteria.<CodegenTable>builder()
                .eq("dataSourceConfigId", dataSourceConfigId);
        return codegenTableRepository.findByCriteria(criteria);
    }
    
    /**
     * 获取表字段列表
     */
    public List<CodegenColumn> getColumnsByTableId(Long tableId) {
        if (tableId == null) {
            return Collections.emptyList();
        }
        // 使用Criteria构建查询条件
        Criteria<CodegenColumn> criteria = Criteria.<CodegenColumn>builder()
                .eq("tableId", tableId);
        return codegenColumnRepository.findByCriteria(criteria);
    }

    /**
     * 批量生成代码
     * @param tableIds 表ID列表
     * @param groupId 分组ID
     * @param modelType 模板类型
     * @return 生成的代码包（ZIP文件字节数组）
     */
    public byte[] generateBatchCode(List<Long> tableIds, String groupId, Integer modelType) {
        log.info("开始批量生成代码，表数量: {}, 分组ID: {}, 模板类型: {}", 
                tableIds.size(), groupId, modelType);
        
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             java.util.zip.ZipOutputStream zipOutputStream = new java.util.zip.ZipOutputStream(outputStream)) {
            
            // 顺序处理每个表以确保线程安全
            for (Long tableId : tableIds) {
                try {
                    // 获取表配置
                    CodegenTable table = codegenTableRepository.findById(tableId);
                    if (table == null) {
                        log.warn("表配置不存在: {}", tableId);
                        continue;
                    }
                    
                    // 获取表字段
                    List<CodegenColumn> columns = getColumnsByTableId(tableId);
                    
                    // 准备代码生成参数
                    Map<String, Object> params = prepareCodegenParams(table, columns, groupId, modelType);
                    
                    // 根据模板类型生成代码文件
                    Map<String, String> codeFiles = generateCodeFiles(params, modelType);
                    
                    // 将生成的代码文件添加到ZIP包中
                    for (Map.Entry<String, String> entry : codeFiles.entrySet()) {
                        String fileName = entry.getKey();
                        String fileContent = entry.getValue();
                        
                        // 创建ZIP条目
                        java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(fileName);
                        zipOutputStream.putNextEntry(zipEntry);
                        
                        // 写入文件内容
                        try (java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(
                                fileContent.getBytes(StandardCharsets.UTF_8))) {
                            byte[] buffer = new byte[4096];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {
                                zipOutputStream.write(buffer, 0, length);
                            }
                        }
                        
                        zipOutputStream.closeEntry();
                    }
                    
                    log.info("成功生成表 {} 的代码", table.getTableName());
                    
                } catch (Exception e) {
                    log.error("生成表 {} 的代码失败", tableId, e);
                    // 继续处理其他表，不中断整个批量操作
                }
            }
            
            // 完成ZIP打包
            zipOutputStream.finish();
            
            byte[] zipData = outputStream.toByteArray();
            log.info("代码生成完成，ZIP文件大小: {} KB", zipData.length / 1024);
            
            return zipData;
            
        } catch (Exception e) {
            log.error("批量生成代码失败", e);
            throw new RuntimeException("批量生成代码失败: " + e.getMessage());
        }
    }
    
    /**
     * 生成自定义代码
     * @param request 生成代码请求参数
     * @return 生成的代码ZIP文件字节数组
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws RuntimeException 当生成代码失败时抛出
     */
    public byte[] generateCustomCode(GenerateCustomCodeRequest request) {
        // 参数验证
        validateGenerateCustomCodeRequest(request);
        
        Long dataSourceConfigId = request.getDataSourceConfigId();
        List<String> tableNames = request.getTableNames();
        String modelType = request.getModelType();
        String scene = request.getScene();
        
        // 记录请求信息
        log.info("开始生成自定义代码: 数据源ID={}, 表数量={}, 模板类型={}, 场景={}, 项目名称={}", 
                dataSourceConfigId, tableNames.size(), modelType, scene, request.getProjectName());
        
        try {
            // 模拟表信息并生成代码
            List<CodegenTable> codegenTables = generateCodeForTables(request);
            
            // 打包成ZIP文件
            return packTablesToZip(codegenTables, tableNames.size());
        } catch (Exception e) {
            // 记录异常
            log.error("生成自定义代码失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成代码失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 验证生成自定义代码的请求参数
     * 
     * @param request 生成代码请求参数
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    private void validateGenerateCustomCodeRequest(GenerateCustomCodeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        
        if (request.getDataSourceConfigId() == null) {
            throw new IllegalArgumentException("数据源配置ID不能为空");
        }
        
        List<String> tableNames = request.getTableNames();
        if (tableNames == null || tableNames.isEmpty()) {
            throw new IllegalArgumentException("表名列表不能为空");
        }
        
        if (StrUtil.isEmpty(request.getModuleName())) {
            throw new IllegalArgumentException("模块名称不能为空");
        }
        
        if (StrUtil.isEmpty(request.getBasePackage())) {
            throw new IllegalArgumentException("基础包名不能为空");
        }
        
        if (StrUtil.isEmpty(request.getModelType())) {
            throw new IllegalArgumentException("模板类型不能为空");
        }
        
        if (StrUtil.isEmpty(request.getScene())) {
            throw new IllegalArgumentException("场景不能为空");
        }
    }
    
    /**
     * 为多个表生成代码
     * 
     * @param request 生成代码请求参数
     * @return 生成了代码的表列表
     */
    private List<CodegenTable> generateCodeForTables(GenerateCustomCodeRequest request) {
        List<CodegenTable> codegenTables = new ArrayList<>();
        List<String> tableNames = request.getTableNames();
        Long dataSourceConfigId = request.getDataSourceConfigId();
        Integer modelTypeInt = "saas".equals(request.getModelType()) ? 1 : 2;
        
        for (String tableName : tableNames) {
            // 创建表信息
            CodegenTable table = createCodegenTable(tableName, request);
            
            // 创建列信息
            List<CodegenColumn> columns = createDefaultColumns(tableName);
            
            // 准备生成参数
            Map<String, Object> params = prepareCodegenParams(table, columns, request);
            
            // 执行代码生成
            Map<String, String> codeFiles = generateCodeFiles(params, modelTypeInt);
            
            // 将生成的代码文件添加到表对象中
            table.setCodeFiles(codeFiles);
            codegenTables.add(table);
            
            log.debug("成功为表 {} 生成代码，生成文件数量: {}", tableName, codeFiles.size());
        }
        
        return codegenTables;
    }
    
    /**
     * 创建代码生成表对象
     * 
     * @param tableName 表名
     * @param request 生成代码请求参数
     * @return 代码生成表对象
     */
    private CodegenTable createCodegenTable(String tableName, GenerateCustomCodeRequest request) {
        CodegenTable table = new CodegenTable();
        table.setDataSourceConfigId(request.getDataSourceConfigId());
        table.setTableName(tableName);
        table.setModuleName(request.getModuleName());
        table.setPackgeName(request.getBasePackage());
        table.setScene("single".equals(request.getScene()) ? 1 : 2); // 1:单表, 2:批量
        table.setTableComment(tableName + "表");
        table.setClassName(convertToCamelCase(tableName, true));
        table.setClassComment(tableName + "表");
        
        return table;
    }
    
    /**
     * 创建默认的列信息
     * 
     * @param tableName 表名
     * @return 列信息列表
     */
    private List<CodegenColumn> createDefaultColumns(String tableName) {
        List<CodegenColumn> columns = new ArrayList<>();
        
        // 添加ID列
        CodegenColumn idColumn = new CodegenColumn();
        idColumn.setColumnName("id");
        idColumn.setDataType("bigint");
        idColumn.setJavaType("Long");
        idColumn.setDescription("主键ID");
        idColumn.setPrimaryKey(true);
        idColumn.setAutoIncrement(true);
        columns.add(idColumn);
        
        // 添加name列
        CodegenColumn nameColumn = new CodegenColumn();
        nameColumn.setColumnName("name");
        nameColumn.setDataType("varchar");
        nameColumn.setJavaType("String");
        nameColumn.setDescription(tableName + "名称");
        nameColumn.setPrimaryKey(false);
        columns.add(nameColumn);
        
        // 添加create_time列
        CodegenColumn createTimeColumn = new CodegenColumn();
        createTimeColumn.setColumnName("create_time");
        createTimeColumn.setDataType("datetime");
        createTimeColumn.setJavaType("LocalDateTime");
        createTimeColumn.setDescription("创建时间");
        createTimeColumn.setPrimaryKey(false);
        columns.add(createTimeColumn);
        
        // 添加update_time列
        CodegenColumn updateTimeColumn = new CodegenColumn();
        updateTimeColumn.setColumnName("update_time");
        updateTimeColumn.setDataType("datetime");
        updateTimeColumn.setJavaType("LocalDateTime");
        updateTimeColumn.setDescription("更新时间");
        updateTimeColumn.setPrimaryKey(false);
        columns.add(updateTimeColumn);
        
        return columns;
    }
    
    /**
     * 准备代码生成参数
     * 
     * @param table 表信息
     * @param columns 列信息列表
     * @param request 生成代码请求参数
     * @return 代码生成参数
     */
    private Map<String, Object> prepareCodegenParams(CodegenTable table, List<CodegenColumn> columns, 
            GenerateCustomCodeRequest request) {
        Map<String, Object> params = new HashMap<>();
        
        // 查找主键列
        CodegenColumn primaryKey = columns.stream()
                .filter(CodegenColumn::getPrimaryKey)
                .findFirst()
                .orElse(null);
        
        params.put("table", table);
        params.put("columns", columns);
        params.put("moduleName", request.getModuleName());
        params.put("packageName", request.getBasePackage());
        params.put("className", table.getClassName());
        params.put("classComment", table.getClassComment());
        params.put("datetime", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        params.put("date", new java.text.SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        params.put("modelType", "saas".equals(request.getModelType()) ? 1 : 2);
        params.put("scene", table.getScene());
        params.put("primaryKey", primaryKey);
        params.put("author", request.getAuthor());
        params.put("projectName", request.getProjectName());
        
        return params;
    }
    
    /**
     * 将多个表的代码打包成ZIP文件
     * 
     * @param codegenTables 生成了代码的表列表
     * @param tableCount 表数量
     * @return ZIP文件字节数组
     * @throws IOException 当打包失败时抛出
     */
    private byte[] packTablesToZip(List<CodegenTable> codegenTables, int tableCount) throws IOException {
        // 使用try-with-resources自动管理资源
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream, StandardCharsets.UTF_8)) {
            
            // 遍历每个表生成的代码文件，写入ZIP
            for (CodegenTable table : codegenTables) {
                Map<String, String> codeFiles = table.getCodeFiles();
                if (codeFiles != null) {
                    for (Map.Entry<String, String> entry : codeFiles.entrySet()) {
                        writeCodeFileToZip(zipOutputStream, entry.getKey(), entry.getValue());
                    }
                }
            }
            
            // 完成ZIP文件创建
            zipOutputStream.finish();
            
            // 返回ZIP文件字节数组
            byte[] zipData = byteArrayOutputStream.toByteArray();
            
            // 记录日志
            log.info("生成自定义代码完成: 表数量={}, ZIP文件大小={} KB", 
                    tableCount, Math.round(zipData.length / 1024.0 * 100) / 100.0);
            
            return zipData;
        }
    }
    
    /**
     * 将代码文件写入ZIP输出流
     * 
     * @param zipOutputStream ZIP输出流
     * @param fileName 文件名
     * @param fileContent 文件内容
     * @throws IOException 当写入失败时抛出
     */
    private void writeCodeFileToZip(ZipOutputStream zipOutputStream, String fileName, String fileContent) 
            throws IOException {
        // 创建ZIP条目
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOutputStream.putNextEntry(zipEntry);
        
        // 写入文件内容
        zipOutputStream.write(fileContent.getBytes(StandardCharsets.UTF_8));
        
        // 关闭当前ZIP条目
        zipOutputStream.closeEntry();
    }
    
    /**
     * 将下划线命名转换为驼峰命名
     * @param name 下划线命名字符串
     * @param firstLetterUpperCase 是否首字母大写
     * @return 驼峰命名字符串
     */
    private String convertToCamelCase(String name, boolean firstLetterUpperCase) {
        // 如果name为null，返回空字符串
        if (name == null) {
            return "";
        }
        
        StringBuilder result = new StringBuilder();
        boolean nextUpperCase = firstLetterUpperCase;
        
        for (int i = 0; i < name.length(); i++) {
            char currentChar = name.charAt(i);
            
            if (currentChar == '_') {
                nextUpperCase = true;
            } else {
                if (nextUpperCase) {
                    result.append(Character.toUpperCase(currentChar));
                    nextUpperCase = false;
                } else {
                    result.append(Character.toLowerCase(currentChar));
                }
            }
        }
        
        return result.toString();
    }
    
    /**
     * 从表信息构建代码生成表配置对象
     */
    private CodegenTable buildCodegenTableFromTableInfo(TableInfo tableInfo, GenerateCustomCodeRequest request) {
        CodegenTable table = new CodegenTable();
        table.setDataSourceConfigId(request.getDataSourceConfigId());
        table.setTableName(tableInfo.getName());
        table.setTableComment(tableInfo.getComment());
        table.setModuleName(request.getModuleName());
        table.setPackgeName(request.getBasePackage());
        // 移除对不存在的getBusinessName方法的调用
        // 确保scene字段类型正确，使用String类型
        // 假设scene字段在CodegenTable中是Integer类型
        table.setScene(1); // 默认单表场景
        
        // 从表名生成类名
        String className = convertToClassName(tableInfo.getName());
        table.setClassName(className);
        table.setClassComment(tableInfo.getComment());
        
        return table;
    }
    
    /**
     * 从表信息构建代码生成字段配置列表
     */
    private List<CodegenColumn> buildCodegenColumnsFromTableInfo(TableInfo tableInfo) {
        List<CodegenColumn> columns = new ArrayList<>();
        for (TableField field : tableInfo.getFields()) {
            CodegenColumn column = new CodegenColumn();
            column.setColumnName(field.getName());
            column.setDataType(field.getType());
            column.setDescription(field.getComment());
            column.setJavaType(getJavaTypeByDbType(field.getType()));
            column.setJavaField(field.getPropertyName());
            column.setPrimaryKey(field.isPrimaryKey());
            column.setNotNull(true);
            column.setInsertable(!field.isPrimaryKey());
            column.setUpdatable(!field.isPrimaryKey());
            column.setListable(true);
            column.setQueryable(true);
            column.setQueryType("eq");
            column.setShowType("input");
            
            columns.add(column);
        }
        return columns;
    }
    
    /**
     * 表名转类名
     */
    private String convertToClassName(String tableName) {
        // 去除前缀（如t_、sys_等）
        String className = tableName;
        if (className.contains("_")) {
            className = StrUtil.upperFirst(StrUtil.toCamelCase(className));
        } else {
            className = StrUtil.upperFirst(className);
        }
        return className;
    }
    
    /**
     * 批量生成代码（兼容旧接口）
     * @param tableIds 表ID列表
     * @return 生成的代码包（ZIP文件字节数组）
     */
    public byte[] generateBatchCode(List<Long> tableIds) {
        // 默认使用SaaS模式
        return generateBatchCode(tableIds, "default", 1);
    }

    /**
     * 批量生成代码
     * @param tableIds 表ID数组
     * @return 生成的代码包（ZIP文件字节数组）
     */
    public byte[] generateBatchCode(Long[] tableIds) {
        return generateBatchCode(java.util.Arrays.asList(tableIds));
    }
    
    /**
     * 准备代码生成参数 - 用于自定义代码生成
     */
    private Map<String, Object> prepareCodegenParams(GenerateCustomCodeRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("projectName", request.getProjectName());
        params.put("moduleName", request.getModuleName());
        params.put("basePackage", request.getBasePackage());
        params.put("datetime", new Date());
        return params;
    }
    
    /**
     * 准备代码生成参数
     * 
     * @param table 表配置信息
     * @param columns 列配置列表
     * @param groupId 分组ID
     * @param modelType 模型类型
     * @return 代码生成参数映射
     */
    private Map<String, Object> prepareCodegenParams(CodegenTable table, List<CodegenColumn> columns, 
                                                   String groupId, Integer modelType) {
        Map<String, Object> params = new HashMap<>();
        
        // 表基本信息
        params.put("table", table);
        params.put("columns", columns);
        params.put("moduleName", table.getModuleName());
        params.put("packageName", table.getPackgeName());
        params.put("className", table.getClassName());
        params.put("classComment", table.getClassComment());
        
        // 时间信息 - 使用Java 8日期时间API
        LocalDateTime now = LocalDateTime.now();
        params.put("datetime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        params.put("date", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        
        // 模板类型
        params.put("modelType", modelType);
        params.put("isSaas", modelType == ModelTypeEnum.SAAS.getType());
        params.put("isDdd", modelType == ModelTypeEnum.DDD.getType());
        
        // 场景类型
        Integer scene = table.getScene();
        params.put("scene", scene);
        params.put("isSingleTable", scene == 1);
        params.put("isMasterSlave", scene == 2);
        params.put("isTree", scene == 3);
        
        // 获取主键字段
        CodegenColumn primaryKey = findPrimaryKey(columns);
        params.put("primaryKey", primaryKey);
        
        // 字段分类
        Map<String, List<CodegenColumn>> columnGroups = groupColumns(columns);
        params.put("baseColumns", columnGroups.getOrDefault("base", Collections.emptyList()));
        params.put("businessColumns", columnGroups.getOrDefault("business", Collections.emptyList()));
        params.put("dateColumns", columnGroups.getOrDefault("date", Collections.emptyList()));
        params.put("stringColumns", columnGroups.getOrDefault("string", Collections.emptyList()));
        
        return params;
    }
    
    /**
     * 查找主键字段
     */
    private CodegenColumn findPrimaryKey(List<CodegenColumn> columns) {
        return columns.stream()
                .filter(CodegenColumn::getPrimaryKey)
                .findFirst().orElse(null);
    }
    
    /**
     * 对列进行分组
     */
    private Map<String, List<CodegenColumn>> groupColumns(List<CodegenColumn> columns) {
        Map<String, List<CodegenColumn>> groups = new HashMap<>();
        
        List<CodegenColumn> baseColumns = new ArrayList<>();
        List<CodegenColumn> businessColumns = new ArrayList<>();
        List<CodegenColumn> dateColumns = new ArrayList<>();
        List<CodegenColumn> stringColumns = new ArrayList<>();
        
        for (CodegenColumn column : columns) {
            if (column.getPrimaryKey()) {
                baseColumns.add(column);
            } else {
                businessColumns.add(column);
                
                // 根据Java类型进一步分组
                String javaType = column.getJavaType();
                if ("LocalDateTime".equals(javaType) || "Date".equals(javaType)) {
                    dateColumns.add(column);
                } else if ("String".equals(javaType)) {
                    stringColumns.add(column);
                }
            }
        }
        
        groups.put("base", baseColumns);
        groups.put("business", businessColumns);
        groups.put("date", dateColumns);
        groups.put("string", stringColumns);
        
        return groups;
    }
    
    /**
     * 生成代码文件
     * 
     * @param params 代码生成参数
     * @param modelType 模型类型
     * @return 生成的代码文件映射，键为文件路径，值为文件内容
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws RuntimeException 当代码生成失败时抛出
     */
    private Map<String, String> generateCodeFiles(Map<String, Object> params, Integer modelType) {
        if (params == null) {
            throw new IllegalArgumentException("代码生成参数不能为空");
        }
        
        if (modelType == null) {
            throw new IllegalArgumentException("模型类型不能为空");
        }
        
        // 验证必要参数
        CodegenTable table = (CodegenTable) params.get("table");
        if (table == null) {
            throw new IllegalArgumentException("表配置信息不能为空");
        }
        
        Map<String, String> codeFiles = new LinkedHashMap<>();
        
        try {
            // 根据模板类型选择不同的代码生成策略
            ModelTypeEnum modelTypeEnum = ModelTypeEnum.valueOf(modelType);
            
            // 策略模式选择代码生成方法
            switch (modelTypeEnum) {
                case SAAS:
                    generateSaasCodeFiles(params, codeFiles);
                    break;
                case DDD:
                    generateDddCodeFiles(params, codeFiles);
                    break;
                default:
                    throw new RuntimeException("不支持的模板类型: " + modelTypeEnum);
            }
            
            log.debug("代码生成完成，共生成 {} 个文件", codeFiles.size());
            return codeFiles;
            
        } catch (IllegalArgumentException e) {
            log.error("代码生成参数错误: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("生成代码文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成代码文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 生成SaaS模式的代码文件
     * 
     * @param params 代码生成参数
     * @param codeFiles 代码文件映射容器
     */
    private void generateSaasCodeFiles(Map<String, Object> params, Map<String, String> codeFiles) {
        CodegenTable table = (CodegenTable) params.get("table");
        String packageName = table.getPackgeName();
        String className = table.getClassName();
        String tableName = table.getTableName();
        
        // 构建目录路径前缀
        String packagePath = packageName.replace('.', '/');
        
        // 生成实体类
        addGeneratedFile(codeFiles, packagePath + "/domain/entity/" + className + ".java", 
                () -> generateEntityCode(params));
        
        // 生成DTO类
        addGeneratedFile(codeFiles, packagePath + "/application/dto/" + className + "DTO.java", 
                () -> generateDtoCode(params));
        
        // 生成Mapper接口
        addGeneratedFile(codeFiles, packagePath + "/infrastructure/mapper/" + className + "Mapper.java", 
                () -> generateMapperCode(params));
        
        // 生成Service接口和实现
        addGeneratedFile(codeFiles, packagePath + "/domain/service/" + className + "Service.java", 
                () -> generateServiceInterfaceCode(params));
        
        addGeneratedFile(codeFiles, packagePath + "/infrastructure/service/impl/" + className + "ServiceImpl.java", 
                () -> generateServiceImplCode(params));
        
        // 生成Controller
        addGeneratedFile(codeFiles, packagePath + "/adapter/controller/" + className + "Controller.java", 
                () -> generateControllerCode(params));
        
        // 生成SQL脚本
        addGeneratedFile(codeFiles, "sql/" + tableName + "_init.sql", 
                () -> generateSqlCode(params));
    }
    
    /**
     * 生成DDD模式的代码文件
     * 
     * @param params 代码生成参数
     * @param codeFiles 代码文件映射容器
     */
    private void generateDddCodeFiles(Map<String, Object> params, Map<String, String> codeFiles) {
        CodegenTable table = (CodegenTable) params.get("table");
        String packageName = table.getPackgeName();
        String className = table.getClassName();
        
        // 构建目录路径前缀
        String packagePath = packageName.replace('.', '/');
        
        // 生成聚合根
        addGeneratedFile(codeFiles, packagePath + "/domain/aggregate/" + className + ".java", 
                () -> generateAggregateCode(params));
        
        // 生成实体
        addGeneratedFile(codeFiles, packagePath + "/domain/entity/" + className + "Entity.java", 
                () -> generateDddEntityCode(params));
        
        // 生成领域服务
        addGeneratedFile(codeFiles, packagePath + "/domain/service/" + className + "DomainService.java", 
                () -> generateDomainServiceCode(params));
        
        // 生成应用服务
        addGeneratedFile(codeFiles, packagePath + "/application/service/" + className + "AppService.java", 
                () -> generateAppServiceCode(params));
        
        // 生成仓储接口
        addGeneratedFile(codeFiles, packagePath + "/domain/repository/" + className + "Repository.java", 
                () -> generateRepositoryCode(params));
        
        // 生成仓储实现
        addGeneratedFile(codeFiles, packagePath + "/infrastructure/repository/impl/" + className + "RepositoryImpl.java", 
                () -> generateRepositoryImplCode(params));
        
        // 生成Controller
        addGeneratedFile(codeFiles, packagePath + "/adapter/controller/" + className + "Controller.java", 
                () -> generateDddControllerCode(params));
    }
    
    /**
     * 添加生成的文件到代码文件映射中
     * 
     * @param codeFiles 代码文件映射
     * @param filePath 文件路径
     * @param codeGenerator 代码生成器函数
     */
    private void addGeneratedFile(Map<String, String> codeFiles, String filePath, Supplier<String> codeGenerator) {
        try {
            String fileContent = codeGenerator.get();
            if (StrUtil.isNotEmpty(fileContent)) {
                codeFiles.put(filePath, fileContent);
                log.debug("成功生成文件: {}", filePath);
            } else {
                log.warn("生成的文件内容为空: {}", filePath);
            }
        } catch (Exception e) {
            log.error("生成文件失败: {}", filePath, e);
            codeFiles.put(filePath, "// 生成失败: " + e.getMessage());
        }
    }
    
    /**
     * 通用代码生成方法，封装异常处理逻辑
     */
    private String generateCodeWithErrorHandling(String codeType, String errorPrefix, Supplier<String> codeGenerator) {
        try {
            return codeGenerator.get();
        } catch (Exception e) {
            log.error("生成{}代码失败", codeType, e);
            return errorPrefix + "生成失败: " + e.getMessage();
        }
    }

    // 使用模板引擎生成各种代码文件
    private String generateEntityCode(Map<String, Object> params) {
        return generateCodeWithErrorHandling("实体类", "// 实体类", () -> templateEngine.generateEntityCode(params));
    }
    
    private String generateDtoCode(Map<String, Object> params) {
        return generateCodeWithErrorHandling("DTO类", "// DTO类", () -> templateEngine.generateDtoCode(params));
    }
    
    private String generateMapperCode(Map<String, Object> params) {
        return generateCodeWithErrorHandling("Mapper接口", "// Mapper接口", () -> templateEngine.generateMapperCode(params));
    }
    
    private String generateServiceInterfaceCode(Map<String, Object> params) {
        return generateCodeWithErrorHandling("Service接口", "// Service接口", () -> templateEngine.generateServiceInterfaceCode(params));
    }
    
    private String generateServiceImplCode(Map<String, Object> params) {
        return generateCodeWithErrorHandling("Service实现", "// Service实现", () -> templateEngine.generateServiceImplCode(params));
    }
    
    private String generateControllerCode(Map<String, Object> params) {
        return generateCodeWithErrorHandling("Controller", "// Controller", () -> templateEngine.generateControllerCode(params));
    }
    
    private String generateSqlCode(Map<String, Object> params) {
        return generateCodeWithErrorHandling("SQL脚本", "-- SQL脚本", () -> templateEngine.generateSqlCode(params));
    }
    
    private String generateAggregateCode(Map<String, Object> params) {
        return generateCodeWithErrorHandling("DDD聚合根", "// DDD聚合根", () -> templateEngine.generateAggregateCode(params));
    }
    
    private String generateDddEntityCode(Map<String, Object> params) {
        return generateCodeWithErrorHandling("DDD实体", "// DDD实体", () -> templateEngine.generateDddEntityCode(params));
    }
    
    private String generateDomainServiceCode(Map<String, Object> params) {
        return generateCodeWithErrorHandling("DDD领域服务", "// DDD领域服务", () -> templateEngine.generateDomainServiceCode(params));
    }
    
    private String generateAppServiceCode(Map<String, Object> params) {
        return generateCodeWithErrorHandling("DDD应用服务", "// DDD应用服务", () -> templateEngine.generateAppServiceCode(params));
    }
    
    private String generateRepositoryCode(Map<String, Object> params) {
        return generateCodeWithErrorHandling("DDD仓储接口", "// DDD仓储接口", () -> templateEngine.generateRepositoryCode(params));
    }
    
    private String generateRepositoryImplCode(Map<String, Object> params) {
        return generateCodeWithErrorHandling("DDD仓储实现", "// DDD仓储实现", () -> templateEngine.generateRepositoryImplCode(params));
    }
    
    private String generateDddControllerCode(Map<String, Object> params) {
        return generateCodeWithErrorHandling("DDD控制器", "// DDD控制器", () -> templateEngine.generateDddControllerCode(params));
    }
    
    /**
     * 根据表ID生成代码
     * 
     * @param tableId 表ID
     * @param groupId 分组ID
     * @param modelType 模型类型
     * @return 生成的代码文件映射，键为文件路径，值为文件内容
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws RuntimeException 当代码生成失败时抛出
     */
    private Map<String, String> generateCodeByTableId(Long tableId, String groupId, Integer modelType) {
        // 参数验证
        if (tableId == null) {
            throw new IllegalArgumentException("表ID不能为空");
        }
        
        if (modelType == null) {
            throw new IllegalArgumentException("模型类型不能为空");
        }
        
        // 验证模型类型有效性
        ModelTypeEnum modelTypeEnum;
        try {
            modelTypeEnum = ModelTypeEnum.valueOf(modelType);
        } catch (Exception e) {
            throw new IllegalArgumentException("不支持的模型类型: " + modelType);
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 获取表配置
        CodegenTable codegenTable = codegenTableRepository.findById(tableId);
        if (codegenTable == null) {
            throw new RuntimeException("表配置不存在: " + tableId);
        }
            
            // 获取表的字段列表
            List<CodegenColumn> columns = getColumnsByTableId(tableId);
            if (CollUtil.isEmpty(columns)) {
                throw new RuntimeException("表字段配置不存在: " + tableId);
            }
            
            // 获取数据源配置
            Long dataSourceConfigId = codegenTable.getDataSourceConfigId();
            if (dataSourceConfigId == null) {
                throw new RuntimeException("表配置未关联数据源: " + tableId);
            }
            
            DataSourceConfig dataSourceConfig = dataSourceConfigService.getDataSourceConfig(dataSourceConfigId);
            if (dataSourceConfig == null) {
                throw new RuntimeException("数据源配置不存在: " + dataSourceConfigId);
            }
            
            // 准备参数
            List<CodegenTable> subTables = new ArrayList<>(); // 暂时没有子表
            List<List<CodegenColumn>> subColumnsList = new ArrayList<>(); // 暂时没有子表字段
            
            // 调用代码生成引擎
            Map<String, String> result = codegenEngine.execute(
                    codegenTable, columns, subTables, subColumnsList, 
                    dataSourceConfig, groupId, modelType);
            
            long endTime = System.currentTimeMillis();
            log.info("成功生成代码: 表名={}, 模型类型={}, 生成文件数={}, 耗时={}ms", 
                    codegenTable.getTableName(), modelTypeEnum, result.size(), (endTime - startTime));
            
            return result;
        } catch (IllegalArgumentException e) {
            log.error("生成代码参数错误: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("生成代码失败: 表ID={}, 错误信息={}", tableId, e.getMessage(), e);
            throw new RuntimeException("生成代码失败: " + e.getMessage());
        }
    }
    
    /**
     * 将生成的代码打包成zip文件
     * 
     * @param generatedCode 生成的代码文件映射，键为文件路径，值为文件内容
     * @param outputStream 输出流，用于写入zip文件
     * @throws IOException 当打包过程中发生IO错误时抛出
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    private void packCodeToZip(Map<String, String> generatedCode, OutputStream outputStream) throws IOException {
        // 参数验证
        if (generatedCode == null) {
            throw new IllegalArgumentException("生成的代码文件映射不能为空");
        }
        
        if (outputStream == null) {
            throw new IllegalArgumentException("输出流不能为空");
        }
        
        long startTime = System.currentTimeMillis();
        long totalBytes = 0;
        
        // 使用hutool的ZipUtil打包
        try (java.util.zip.ZipOutputStream zipOut = new java.util.zip.ZipOutputStream(outputStream)) {
            // 设置压缩级别
            zipOut.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION);
            
            for (Map.Entry<String, String> entry : generatedCode.entrySet()) {
                String fileName = entry.getKey();
                String content = entry.getValue();
                
                if (StrUtil.isEmpty(fileName)) {
                    log.warn("跳过空文件名的代码文件");
                    continue;
                }
                
                if (content == null) {
                    content = "";
                }
                
                try {
                    // 创建zip条目
                    java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(fileName);
                    zipOut.putNextEntry(zipEntry);
                    
                    // 写入内容
                    IoUtil.writeUtf8(zipOut, true, content);
                    totalBytes += content.getBytes(StandardCharsets.UTF_8).length;
                    
                    log.debug("成功添加文件到ZIP: {}, 大小: {}字节", fileName, content.length());
                } catch (IOException e) {
                    log.error("添加文件到ZIP失败: {}", fileName, e);
                    // 尝试继续打包其他文件
                    try {
                        zipOut.closeEntry();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        
        long endTime = System.currentTimeMillis();
        log.info("代码打包完成: 文件数={}, 原始大小={}KB, 耗时={}ms", 
                generatedCode.size(), totalBytes / 1024, (endTime - startTime));
    }

    /**
     * 更新列信息
     */
    private void updateColumn(CodegenColumn column, TableField field) {
        // 保留原有配置，只更新数据库相关信息
        boolean hasUpdate = false;
        
        if (!StrUtil.equals(column.getColumnName(), field.getName())) {
            column.setColumnName(field.getName());
            hasUpdate = true;
        }
        
        if (!StrUtil.equals(column.getDataType(), field.getType())) {
            column.setDataType(field.getType());
            // 更新Java类型
            column.setJavaType(getJavaTypeByDbType(field.getType()));
            hasUpdate = true;
        }
        
        if (!StrUtil.equals(column.getDescription(), field.getComment())) {
            column.setDescription(field.getComment());
            hasUpdate = true;
        }
        
        if (column.getPrimaryKey() != field.isPrimaryKey()) {
            column.setPrimaryKey(field.isPrimaryKey());
            hasUpdate = true;
        }
        
        if (hasUpdate) {
            // 调用Repository的update方法
            codegenColumnRepository.update(column);
        }
    }

    /**
     * 创建代码生成列配置
     * 
     * @param tableId 表ID
     * @param field 表字段信息
     * @return 创建的代码生成列配置对象
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    private CodegenColumn createCodegenColumn(Long tableId, TableField field) {
        // 参数验证
        if (tableId == null) {
            throw new IllegalArgumentException("表ID不能为空");
        }
        
        if (field == null) {
            throw new IllegalArgumentException("表字段信息不能为空");
        }
        
        String fieldName = field.getName();
        if (StrUtil.isEmpty(fieldName)) {
            throw new IllegalArgumentException("表字段名称不能为空");
        }
        
        String dataType = field.getType();
        if (StrUtil.isEmpty(dataType)) {
            throw new IllegalArgumentException("表字段类型不能为空: " + fieldName);
        }
        
        CodegenColumn column = new CodegenColumn();
        
        // 设置基本信息
        column.setTableId(tableId);
        column.setColumnName(fieldName);
        column.setDataType(dataType);
        column.setDescription(StrUtil.blankToDefault(field.getComment(), ""));
        
        // 设置Java类型和属性名
        String javaType = getJavaTypeByDbType(dataType);
        column.setJavaType(javaType);
        
        // 设置Java属性名，如果field没有提供则自动生成
        String javaField = field.getPropertyName();
        if (StrUtil.isEmpty(javaField)) {
            javaField = convertToCamelCase(fieldName, false);
        }
        column.setJavaField(javaField);
        
        // 设置主键信息
        boolean isPrimaryKey = field.isPrimaryKey();
        column.setPrimaryKey(isPrimaryKey);
        
        // 设置其他属性
        column.setNotNull(field.isPrimaryKey()); // 使用主键信息作为必填标志
        column.setInsertable(!isPrimaryKey && !field.isFill()); // 主键和自动填充字段不可插入
        column.setUpdatable(!isPrimaryKey && !field.isFill()); // 主键和自动填充字段不可更新
        column.setListable(true); // 默认在列表中显示
        column.setQueryable(true); // 默认可查询
        
        // 设置默认查询类型
        if (isPrimaryKey) {
            column.setQueryType("eq");
        } else if (javaType.equals("String")) {
            column.setQueryType("like");
        } else if (javaType.equals("Boolean")) {
            column.setQueryType("eq");
        } else if (DbTypeMapping.isDateTimeType(dataType)) {
            column.setQueryType("between");
        } else {
            column.setQueryType("eq");
        }
        
        // 设置默认显示类型
        String showType = "input";
        if (DbTypeMapping.isDateTimeType(dataType)) {
            showType = "date";
        } else if (javaType.equals("Boolean")) {
            showType = "switch";
        }
        column.setShowType(showType);
        
        // 设置填充信息
        if (field.isFill()) {
            column.setInsertable(false);
            column.setUpdatable(false);
        }
        
        // 设置创建时间和更新时间
        // 实际项目中，这里应该根据CodegenColumn实体类的实际字段进行调整
        
        log.debug("创建代码生成列配置: 表ID={}, 列名={}, Java类型={}", tableId, fieldName, javaType);
        
        return column;
    }

    /**
     * 数据库类型映射管理器
     * 负责将数据库类型转换为对应的Java类型，支持多种数据库方言
     */
    private static final class DbTypeMapping {
        // 使用HashMap存储类型映射规则
        private static final Map<String, String> TYPE_MAPPINGS = new HashMap<>();
        private static final Map<String, List<String>> DB_TYPE_GROUPS = new HashMap<>();
        
        static {
            // 初始化各种数据库类型到Java类型的映射规则
            TYPE_MAPPINGS.put("char", "String");
            TYPE_MAPPINGS.put("text", "String");
            TYPE_MAPPINGS.put("varchar", "String");
            TYPE_MAPPINGS.put("longtext", "String");
            TYPE_MAPPINGS.put("mediumtext", "String");
            TYPE_MAPPINGS.put("tinytext", "String");
            TYPE_MAPPINGS.put("int", "Integer");
            TYPE_MAPPINGS.put("smallint", "Integer");
            TYPE_MAPPINGS.put("integer", "Integer");
            TYPE_MAPPINGS.put("bigint", "Long");
            TYPE_MAPPINGS.put("float", "BigDecimal");
            TYPE_MAPPINGS.put("double", "BigDecimal");
            TYPE_MAPPINGS.put("decimal", "BigDecimal");
            TYPE_MAPPINGS.put("numeric", "BigDecimal");
            TYPE_MAPPINGS.put("date", "LocalDate");
            TYPE_MAPPINGS.put("datetime", "LocalDateTime");
            TYPE_MAPPINGS.put("timestamp", "LocalDateTime");
            TYPE_MAPPINGS.put("time", "LocalTime");
            TYPE_MAPPINGS.put("year", "Integer");
            TYPE_MAPPINGS.put("boolean", "Boolean");
            TYPE_MAPPINGS.put("bit", "Boolean");
            TYPE_MAPPINGS.put("bool", "Boolean");
            TYPE_MAPPINGS.put("tinyint(1)", "Boolean");
            TYPE_MAPPINGS.put("blob", "byte[]");
            TYPE_MAPPINGS.put("binary", "byte[]");
            TYPE_MAPPINGS.put("varbinary", "byte[]");
            TYPE_MAPPINGS.put("longblob", "byte[]");
            
            // 初始化类型分组
            initializeTypeGroups();
        }
        
        /**
         * 初始化类型分组
         */
        private static void initializeTypeGroups() {
            // 数字类型分组
            List<String> numericTypes = new ArrayList<>();
            numericTypes.add("tinyint");
            numericTypes.add("smallint");
            numericTypes.add("int");
            numericTypes.add("integer");
            numericTypes.add("bigint");
            numericTypes.add("float");
            numericTypes.add("double");
            numericTypes.add("decimal");
            numericTypes.add("numeric");
            DB_TYPE_GROUPS.put("NUMERIC", numericTypes);
            
            // 日期时间类型分组
            List<String> dateTimeTypes = new ArrayList<>();
            dateTimeTypes.add("date");
            dateTimeTypes.add("datetime");
            dateTimeTypes.add("timestamp");
            dateTimeTypes.add("time");
            dateTimeTypes.add("year");
            DB_TYPE_GROUPS.put("DATE_TIME", dateTimeTypes);
            
            // 字符串类型分组
            List<String> stringTypes = new ArrayList<>();
            stringTypes.add("char");
            stringTypes.add("varchar");
            stringTypes.add("text");
            stringTypes.add("longtext");
            stringTypes.add("mediumtext");
            stringTypes.add("tinytext");
            DB_TYPE_GROUPS.put("STRING", stringTypes);
            
            // 布尔类型分组
            List<String> booleanTypes = new ArrayList<>();
            booleanTypes.add("boolean");
            booleanTypes.add("bit");
            booleanTypes.add("bool");
            booleanTypes.add("tinyint(1)");
            DB_TYPE_GROUPS.put("BOOLEAN", booleanTypes);
            
            // 二进制类型分组
            List<String> binaryTypes = new ArrayList<>();
            binaryTypes.add("blob");
            binaryTypes.add("binary");
            binaryTypes.add("varbinary");
            binaryTypes.add("longblob");
            DB_TYPE_GROUPS.put("BINARY", binaryTypes);
        }
        
        /**
         * 获取数据库类型对应的Java类型
         * @param dbType 数据库类型
         * @return 对应的Java类型
         */
        public static String getJavaType(String dbType) {
            if (StrUtil.isBlank(dbType)) {
                return "String";
            }

            String lowerDbType = dbType.toLowerCase();
            
            // 先检查精确匹配的映射规则
            for (Map.Entry<String, String> entry : TYPE_MAPPINGS.entrySet()) {
                String dbTypeKey = entry.getKey();
                String javaTypeInfo = entry.getValue();

                // 如果数据库类型包含当前键，则返回对应的Java类型
                if (lowerDbType.contains(dbTypeKey)) {
                    return javaTypeInfo;
                }
            }
            
            // 尝试处理带参数的类型（如 varchar(255) -> varchar）
            if (lowerDbType.contains("(")) {
                String baseType = lowerDbType.substring(0, lowerDbType.indexOf("("));
                String result = getJavaType(baseType);
                if (!"String".equals(result) || baseType.equals(lowerDbType)) {
                    return result;
                }
            }
            
            // 尝试处理带修饰符的类型（如 int unsigned -> int）
            String cleanType = removeTypeModifiers(lowerDbType);
            if (!cleanType.equals(lowerDbType)) {
                String result = getJavaType(cleanType);
                if (!"String".equals(result)) {
                    return result;
                }
            }

            // 默认返回String
            return "String";
        }
        
        /**
         * 移除类型修饰符（如 unsigned、zerofill 等）
         */
        private static String removeTypeModifiers(String type) {
            // 常见的类型修饰符
            String[] modifiers = {" unsigned", " zerofill", " unsigned zerofill"};
            
            for (String modifier : modifiers) {
                if (type.endsWith(modifier)) {
                    return type.substring(0, type.length() - modifier.length());
                }
            }
            
            return type;
        }
        
        /**
         * 判断是否为日期时间类型
         * @param dbType 数据库类型
         * @return 是否为日期时间类型
         */
        public static boolean isDateTimeType(String dbType) {
            if (StrUtil.isBlank(dbType)) {
                return false;
            }
            
            String lowerDbType = dbType.toLowerCase();
            List<String> dateTimeTypes = DB_TYPE_GROUPS.get("DATE_TIME");
            
            if (dateTimeTypes != null) {
                for (String dateTimeType : dateTimeTypes) {
                    if (lowerDbType.contains(dateTimeType)) {
                        return true;
                    }
                }
            }
            
            return false;
        }
        
        /**
         * 判断是否为数字类型
         * @param dbType 数据库类型
         * @return 是否为数字类型
         */
        public static boolean isNumericType(String dbType) {
            if (StrUtil.isBlank(dbType)) {
                return false;
            }
            
            String lowerDbType = dbType.toLowerCase();
            List<String> numericTypes = DB_TYPE_GROUPS.get("NUMERIC");
            
            if (numericTypes != null) {
                for (String numericType : numericTypes) {
                    if (lowerDbType.contains(numericType)) {
                        return true;
                    }
                }
            }
            
            return false;
        }
        
        /**
         * 判断是否为布尔类型
         * @param dbType 数据库类型
         * @return 是否为布尔类型
         */
        public static boolean isBooleanType(String dbType) {
            if (StrUtil.isBlank(dbType)) {
                return false;
            }
            
            String lowerDbType = dbType.toLowerCase();
            List<String> booleanTypes = DB_TYPE_GROUPS.get("BOOLEAN");
            
            if (booleanTypes != null) {
                for (String booleanType : booleanTypes) {
                    if (lowerDbType.contains(booleanType)) {
                        return true;
                    }
                }
            }
            
            return false;
        }
        
        /**
         * 获取支持的数据库类型列表
         * @return 支持的数据库类型集合
         */
        public static Set<String> getSupportedDbTypes() {
            return new HashSet<>(TYPE_MAPPINGS.keySet());
        }
    }

    /**
     * 根据数据库类型获取对应的Java类型
     * 
     * @param dbType 数据库字段类型
     * @return 对应的Java类型
     */
    private String getJavaTypeByDbType(String dbType) {
        String javaType = DbTypeMapping.getJavaType(dbType);
        
        // 记录类型映射日志，便于调试和问题排查
        if (StrUtil.isNotEmpty(dbType)) {
            log.debug("数据库类型映射: {} -> {}", dbType, javaType);
        }
        
        return javaType;
    }
    


    /**
     * 删除表配置及其关联的列配置
     * @param id 表ID
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws RuntimeException 当删除操作失败时抛出
     */
    public void deleteTable(Long id) {
        // 参数验证
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("表ID必须为正整数");
        }
        
        long startTime = System.currentTimeMillis();
        log.debug("开始删除表配置，表ID: {}", id);
        
        try {
            // 首先检查表是否存在
            CodegenTable codegenTable = codegenTableRepository.findById(id);
            if (codegenTable == null) {
                log.warn("表配置不存在，无需删除，ID: {}", id);
                return;
            }
            
            // 获取表名用于日志记录
            String tableName = codegenTable.getTableName();
            
            // 删除关联的列配置
            int deletedColumnsCount = deleteColumnsByTableId(id);
            
            // 删除表配置
            codegenTableRepository.deleteById(id);
            
            long endTime = System.currentTimeMillis();
            log.info("成功删除表配置及其列配置，表ID: {}, 表名: {}, 删除列数量: {}, 耗时: {}ms", 
                     id, tableName, deletedColumnsCount, (endTime - startTime));
        } catch (Exception e) {
            log.error("删除表配置失败，表ID: {}", id, e);
            throw new RuntimeException("删除表配置失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 根据表ID删除所有关联的列配置
     * @param tableId 表ID
     * @return 成功删除的列数量
     */
    private int deleteColumnsByTableId(Long tableId) {
        // 参数验证
        if (tableId == null || tableId <= 0) {
            throw new IllegalArgumentException("表ID必须为正整数");
        }
        
        log.debug("开始删除表关联的列配置，表ID: {}", tableId);
        
        List<CodegenColumn> columns = getColumnsByTableId(tableId);
        if (CollUtil.isEmpty(columns)) {
            log.debug("表ID: {} 没有关联的列配置，无需删除", tableId);
            return 0;
        }
        
        // 记录成功删除的列数量
        int successCount = 0;
        List<Long> failedColumnIds = new ArrayList<>();
        
        // 批量删除优化：对于大量列数据，可以考虑分批次处理
        int totalColumns = columns.size();
        
        for (CodegenColumn column : columns) {
            try {
                if (column.getId() != null) {
                    codegenColumnRepository.deleteById(column.getId());
                    successCount++;
                } else {
                    log.warn("跳过删除无效列配置，表ID: {}, 列名: {}", 
                             tableId, column.getColumnName());
                }
            } catch (Exception e) {
                Long columnId = column.getId();
                failedColumnIds.add(columnId);
                log.warn("删除列配置失败，列ID: {}, 列名: {}", 
                         columnId, column.getColumnName(), e);
                // 继续删除其他列，单个列删除失败不应影响整体操作
            }
        }
        
        // 记录删除结果
        if (!failedColumnIds.isEmpty()) {
            log.warn("表ID: {} 的列配置部分删除失败，失败列数量: {}, 失败列ID: {}", 
                     tableId, failedColumnIds.size(), failedColumnIds);
        }
        
        log.debug("表ID: {} 的列配置删除完成，共 {} 列，成功删除 {} 列", 
                 tableId, totalColumns, successCount);
        
        return successCount;
    }
    
    /**
     * 预览代码生成结果
     * 
     * @param id 表ID
     * @param groupId 分组ID
     * @param modelType 模型类型（1=SaaS模式，2=DDD模式）
     * @return 代码文件映射，键为文件名，值为文件内容
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws RuntimeException 当代码生成失败时抛出
     */
    public Map<String, String> previewCode(Long id, String groupId, Integer modelType) {
        // 参数验证
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("表ID必须为正整数");
        }
        
        if (StrUtil.isEmpty(groupId)) {
            throw new IllegalArgumentException("分组ID不能为空");
        }
        
        if (modelType == null || (modelType != 1 && modelType != 2)) {
            throw new IllegalArgumentException("模型类型必须为1(SaaS模式)或2(DDD模式)");
        }
        
        long startTime = System.currentTimeMillis();
        log.debug("开始预览代码，表ID: {}, 分组ID: {}, 模型类型: {}", id, groupId, modelType);
        
        try {
            Map<String, String> codeFiles = generateCodeByTableId(id, groupId, modelType);
            
            long endTime = System.currentTimeMillis();
            log.info("代码预览完成，表ID: {}, 生成文件数量: {}, 耗时: {}ms", 
                     id, codeFiles.size(), (endTime - startTime));
            
            return codeFiles;
        } catch (Exception e) {
            log.error("代码预览失败，表ID: {}", id, e);
            throw new RuntimeException("代码预览失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取代码生成表的分页数据
     * 
     * @param reqVO 分页查询参数
     * @return 表分页结果
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    public PageResult<CodegenTable> getCodegenTablePage(CodegenTablePageRequest reqVO) {
        if (reqVO == null) {
            throw new IllegalArgumentException("分页查询参数不能为空");
        }
        
        long startTime = System.currentTimeMillis();
        log.debug("开始获取表定义分页数据，页码: {}, 每页条数: {}", 
                 reqVO.getPage(), reqVO.getSize());
        
        try {
            // 调用repository获取分页数据
            PageResult<CodegenTable> pageResult = codegenTableRepository.pageByCriteria(reqVO);
            
            long endTime = System.currentTimeMillis();
            log.info("获取表定义分页数据完成，总条数: {}, 耗时: {}ms", 
                     pageResult.getTotal(), (endTime - startTime));
            
            return pageResult;
        } catch (Exception e) {
            log.error("获取表定义分页数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表定义分页数据失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取表和字段的明细
     * 
     * @param tableId 表ID
     * @return 表和字段的明细响应
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws RuntimeException 当查询失败时抛出
     */
    public CodegenDetailResponse getCodegenDetail(Long tableId) {
        if (tableId == null || tableId <= 0) {
            throw new IllegalArgumentException("表ID必须为正整数");
        }
        
        long startTime = System.currentTimeMillis();
        log.debug("开始获取表和字段明细，表ID: {}", tableId);
        
        try {
            // 获取表配置
            CodegenTable codegenTable = codegenTableRepository.findById(tableId);
            if (codegenTable == null) {
                throw new RuntimeException("表配置不存在: " + tableId);
            }
            
            // 获取字段列表
            List<CodegenColumn> columns = getColumnsByTableId(tableId);
            
            // 转换为响应对象
            CodegenDetailResponse response = new CodegenDetailResponse();
            // 设置表信息
            if (codegenTable != null) {
                CodegenTableResponse tableResponse = new CodegenTableResponse();
                BeanUtil.copyProperties(codegenTable, tableResponse);
                response.setTable(tableResponse);
            }
            // 设置列信息
            if (columns != null && !columns.isEmpty()) {
                List<CodegenColumnResponse> columnResponses = new ArrayList<>(columns.size());
                for (CodegenColumn column : columns) {
                    CodegenColumnResponse columnResponse = new CodegenColumnResponse();
                    BeanUtil.copyProperties(column, columnResponse);
                    columnResponses.add(columnResponse);
                }
                response.setColumns(columnResponses);
            }
            
            long endTime = System.currentTimeMillis();
            log.info("获取表和字段明细完成，表ID: {}, 字段数量: {}, 耗时: {}ms", 
                     tableId, columns != null ? columns.size() : 0, (endTime - startTime));
            
            return response;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取表和字段明细失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表和字段明细失败: " + e.getMessage(), e);
        }
    }
}
