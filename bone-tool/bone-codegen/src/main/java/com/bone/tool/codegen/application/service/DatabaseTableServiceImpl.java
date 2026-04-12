package com.bone.tool.codegen.application.service;

import com.bone.core.model.PageResult;
import com.bone.tool.codegen.application.dto.CodegenTablePageRequest;
import com.bone.tool.codegen.application.dto.CodegenTableRequest;
import com.bone.tool.codegen.application.dto.CodegenColumnRequest;
import com.bone.tool.codegen.application.dto.CodegenDetailResponse;
import com.bone.tool.codegen.application.dto.CodegenTableResponse;
import com.bone.tool.codegen.application.converter.CodegenConverter;
import com.bone.tool.codegen.domain.entity.Datasource;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.entity.DatabaseTableMetadata;
import com.bone.tool.codegen.domain.repository.DataSourceConfigRepository;
import com.bone.tool.codegen.domain.repository.CodegenTableRepository;
import com.bone.tool.codegen.domain.repository.CodegenColumnRepository;
import com.bone.tool.codegen.domain.repository.DatabaseTableRepository;
import com.bone.tool.codegen.domain.exception.CodegenBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bone.tool.codegen.domain.enums.ErrorCodeConstants.DATA_SOURCE_CONFIG_NOT_OK;

/**
 * 数据库表领域服务实现类
 * <p>
 * 负责数据库表结构信息的获取、解析和处理，为代码生成提供底层数据源支持
 * 支持多数据库类型的表信息查询、导入、同步和管理功能
 * 
 * @author bone-team
 */
@Service
public class DatabaseTableServiceImpl implements DatabaseTableService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseTableServiceImpl.class);

    // 使用final修饰注入的字段，确保不可变性
    private final DataSourceConfigRepository dataSourceConfigRepository;
    private final CodegenTableRepository codegenTableRepository;
    private final CodegenColumnRepository codegenColumnRepository;
    private final DatabaseTableRepository databaseTableRepository;
    private final CodegenConverter codegenConverter;

    /**
     * 构造函数 - 依赖注入
     * 
     * @param dataSourceConfigRepository 数据源配置仓库
     * @param codegenTableRepository 代码生成表仓库
     * @param codegenColumnRepository 代码生成列仓库
     * @param databaseTableRepository 数据库表仓库
     * @param codegenConverter 代码生成转换器
     * @throws IllegalArgumentException 当任何依赖项为null时抛出
     */
    @Autowired
    public DatabaseTableServiceImpl(
            DataSourceConfigRepository dataSourceConfigRepository,
            CodegenTableRepository codegenTableRepository,
            CodegenColumnRepository codegenColumnRepository,
            DatabaseTableRepository databaseTableRepository,
            CodegenConverter codegenConverter) {
        // 参数验证
        if (dataSourceConfigRepository == null) {
            throw new CodegenBusinessException(400, "数据源配置仓库不能为空");
        }
        if (codegenTableRepository == null) {
            throw new CodegenBusinessException(400, "代码生成表仓库不能为空");
        }
        if (codegenColumnRepository == null) {
            throw new CodegenBusinessException(400, "代码生成列仓库不能为空");
        }
        if (databaseTableRepository == null) {
            throw new CodegenBusinessException(400, "数据库表仓库不能为空");
        }
        if (codegenConverter == null) {
            throw new CodegenBusinessException(400, "代码生成转换器不能为空");
        }
        
        this.dataSourceConfigRepository = dataSourceConfigRepository;
        this.codegenTableRepository = codegenTableRepository;
        this.codegenColumnRepository = codegenColumnRepository;
        this.databaseTableRepository = databaseTableRepository;
        this.codegenConverter = codegenConverter;
    }

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
    @Override
    public List<DatabaseTableMetadata> getTableList(Long dataSourceConfigId, String nameLike, String commentLike) {
        if (dataSourceConfigId == null) {
            throw new CodegenBusinessException(400, "数据源ID不能为空");
        }
        
        // 获取完整的表列表
        List<DatabaseTableMetadata> tableMetadataList = retrieveTableListFromDatabase(dataSourceConfigId, null);
        
        // 根据条件过滤表名
        if (StringUtils.hasText(nameLike)) {
            tableMetadataList = filterTableByName(tableMetadataList, nameLike);
        }
        
        // 根据条件过滤表注释
        if (StringUtils.hasText(commentLike)) {
            tableMetadataList = filterTableByComment(tableMetadataList, commentLike);
        }
        
        return tableMetadataList;
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
    @Override
    public List<DatabaseTableMetadata> getTables(Long dataSourceConfigId, List<String> tableNames) {
        if (dataSourceConfigId == null) {
            throw new CodegenBusinessException(400, "数据源配置ID不能为空");
        }
        
        if (CollectionUtils.isEmpty(tableNames)) {
            logger.debug("表名列表为空，返回空列表");
            return Collections.emptyList();
        }
        
        List<DatabaseTableMetadata> tableMetadataList = new ArrayList<>(tableNames.size());
        
        for (String tableName : tableNames) {
            if (StringUtils.hasText(tableName)) {
                DatabaseTableMetadata tableMetadata = retrieveTableMetadata(dataSourceConfigId, tableName);
                if (tableMetadata != null) {
                    tableMetadataList.add(tableMetadata);
                }
            }
        }
        
        logger.debug("成功获取 {} 个表的详细信息", tableMetadataList.size());
        return tableMetadataList;
    }
    
    /**
     * 内部方法：从数据库获取表列表的具体实现
     * 
     * @param dataSourceConfigId 数据源配置ID
     * @param schema 数据库模式
     * @return 表信息列表
     */
    private List<DatabaseTableMetadata> retrieveTableListFromDatabase(Long dataSourceConfigId, String schema) {
        try {
            return databaseTableRepository.getTableList(dataSourceConfigId, schema);
        } catch (Exception e) {
            logger.error("获取表列表失败，数据源ID: {}, schema: {}", dataSourceConfigId, schema, e);
            // 出错时返回空列表，避免上层调用失败
            return Collections.emptyList();
        }
    }
    
    /**
     * 根据名称过滤表列表
     * 
     * @param tableMetadataList 表元数据列表
     * @param nameLike 名称匹配模式
     * @return 过滤后的表元数据列表
     */
    private List<DatabaseTableMetadata> filterTableByName(List<DatabaseTableMetadata> tableMetadataList, String nameLike) {
        if (CollectionUtils.isEmpty(tableMetadataList) || !StringUtils.hasText(nameLike)) {
            return tableMetadataList;
        }
        
        String lowerCaseNameLike = nameLike.toLowerCase();
        return tableMetadataList.stream()
                .filter(table -> table != null && table.getName() != null &&
                        table.getName().toLowerCase().contains(lowerCaseNameLike))
                .collect(Collectors.toList());
    }
    
    /**
     * 根据注释过滤表列表
     * 
     * @param tableMetadataList 表元数据列表
     * @param commentLike 注释匹配模式
     * @return 过滤后的表元数据列表
     */
    private List<DatabaseTableMetadata> filterTableByComment(List<DatabaseTableMetadata> tableMetadataList, String commentLike) {
        if (CollectionUtils.isEmpty(tableMetadataList) || !StringUtils.hasText(commentLike)) {
            return tableMetadataList;
        }
        
        String lowerCaseCommentLike = commentLike.toLowerCase();
        return tableMetadataList.stream()
                .filter(table -> table != null && StringUtils.hasText(table.getComment()) && 
                        table.getComment().toLowerCase().contains(lowerCaseCommentLike))
                .collect(Collectors.toList());
    }
    
    /**
     * 内部方法：获取单个表信息的具体实现
     * 
     * @param dataSourceConfigId 数据源配置ID
     * @param tableName 表名
     * @return 表信息
     */
    private DatabaseTableMetadata retrieveTableMetadata(Long dataSourceConfigId, String tableName) {
        try {
            return databaseTableRepository.getTableInfo(dataSourceConfigId, tableName);
        } catch (Exception e) {
            logger.error("获取表信息失败，表名: {}, 数据源ID: {}", tableName, dataSourceConfigId, e);
            return null; // 表不存在或查询失败时返回null
        }
    }
    
    /**
     * 根据数据源配置ID获取表定义列表
     * 
     * @param dataSourceConfigId 数据源配置ID
     * @return 表定义列表
     * @throws IllegalArgumentException 当数据源配置ID为空时抛出
     */
    @Override
    public List<CodegenTable> getCodegenTablesByDataSourceId(Long dataSourceConfigId) {
        if (dataSourceConfigId == null) {
            return Collections.emptyList();
        }
        try {
            List<CodegenTable> list = codegenTableRepository.selectListByDataSourceConfigId(dataSourceConfigId);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            logger.error("获取代码生成表配置失败，数据源ID: {}", dataSourceConfigId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<CodegenTable> findCodegenTable(Long datasourceId, String tableName) {
        if (datasourceId == null || !StringUtils.hasText(tableName)) {
            return Optional.empty();
        }
        return getCodegenTablesByDataSourceId(datasourceId).stream()
                .filter(t -> tableName.equals(t.getTableName()))
                .findFirst();
    }

    @Override
    public PageResult<CodegenTableResponse> pageCodegenTables(CodegenTablePageRequest request) {
        if (request == null) {
            throw new CodegenBusinessException(400, "请求参数不能为空");
        }
        List<CodegenTable> allTables = getCodegenTablesByDataSourceId(request.getDataSourceConfigId());
        List<CodegenTable> filtered = filterCodegenTablesForPage(allTables, request);
        int total = filtered.size();
        int pageNo = request.getPage() != null ? request.getPage() : 1;
        int pageSize = request.getSize() != null ? request.getSize() : 10;
        int start = Math.max(0, (pageNo - 1) * pageSize);
        List<CodegenTable> pageSlice = filtered.stream()
                .skip(start)
                .limit(pageSize)
                .collect(Collectors.toList());
        List<CodegenTableResponse> records = codegenConverter.toCodegenTableResponseList(pageSlice);
        return PageResult.of(records, (long) total, pageNo, pageSize);
    }

    private List<CodegenTable> filterCodegenTablesForPage(List<CodegenTable> tables, CodegenTablePageRequest request) {
        if (CollectionUtils.isEmpty(tables)) {
            return Collections.emptyList();
        }
        return tables.stream()
                .filter(table -> {
                    boolean match = true;
                    if (StringUtils.hasText(request.getTableName())) {
                        match = match && table.getTableName() != null
                                && table.getTableName().contains(request.getTableName());
                    }
                    if (StringUtils.hasText(request.getTableComment())) {
                        match = match && table.getTableComment() != null
                                && table.getTableComment().contains(request.getTableComment());
                    }
                    if (StringUtils.hasText(request.getClassName())) {
                        match = match && table.getClassName() != null
                                && table.getClassName().contains(request.getClassName());
                    }
                    return match;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 获取表定义详情
     * 
     * @param tableId 表ID
     * @return 表定义详情响应
     * @throws IllegalArgumentException 当表ID为空时抛出
     * @throws RuntimeException 当表配置不存在时抛出
     */
    @Override
    public CodegenDetailResponse getCodegenDetail(Long tableId) {
        if (tableId == null) {
            throw new CodegenBusinessException(400, "表ID不能为空");
        }
        
        // 获取表配置
        CodegenTable codegenTable = codegenTableRepository.findById(tableId)
            .orElseThrow(() -> new RuntimeException("表配置不存在，ID: " + tableId));
        
        // 获取字段列表
        List<CodegenColumn> columns = retrieveColumnsByTableId(tableId);
        
        // 使用转换器构建详情响应
        return codegenConverter.toCodegenDetailResponse(codegenTable, columns); // 修正方法名，从convertToDetail改为toCodegenDetailResponse
    }
    
    /**
     * 从数据库导入单个表结构
     * 
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
    @Override
    public Long importTableFromDatabase(Long dataSourceConfigId, String tableName, String moduleName,
                                       String packageName, Integer sceneType, Integer modelType) {
        validateImportParameters(dataSourceConfigId, tableName, moduleName, packageName);
        
        try {
            // 获取数据库表信息
            DatabaseTableMetadata tableMetadata = retrieveTableMetadata(dataSourceConfigId, tableName);
            if (tableMetadata == null) {
                throw new RuntimeException("表不存在: " + tableName);
            }
            
            // 创建并保存代码生成表配置
            CodegenTable codegenTable = createCodegenTableFromMetadata(dataSourceConfigId, tableMetadata, 
                    moduleName, packageName, sceneType, modelType);
            Long savedTableId = codegenTableRepository.save(codegenTable);
            
            // 导入字段信息
            importColumns(savedTableId, tableMetadata.getFields());
            
            logger.info("成功导入表: {}", tableName);
            return savedTableId;
        } catch (RuntimeException e) {
            logger.error("导入表失败: {}", tableName, e);
            throw e;
        }
    }
    
    /**
     * 验证导入参数
     * 
     * @param dataSourceConfigId 数据源配置ID
     * @param tableName 表名
     * @param moduleName 模块名
     * @param packageName 包名
     * @throws IllegalArgumentException 当必要参数为空时抛出
     */
    private void validateImportParameters(Long dataSourceConfigId, String tableName, 
                                         String moduleName, String packageName) {
        if (dataSourceConfigId == null) {
            throw new CodegenBusinessException(400, "数据源配置ID不能为空");
        }
        if (!StringUtils.hasText(tableName)) {
            throw new CodegenBusinessException(400, "表名不能为空");
        }
        if (!StringUtils.hasText(moduleName)) {
            throw new CodegenBusinessException(400, "模块名不能为空");
        }
        if (!StringUtils.hasText(packageName)) {
            throw new CodegenBusinessException(400, "包名不能为空");
        }
    }
    
    /**
     * 根据表元数据创建代码生成表配置
     * 
     * @param dataSourceConfigId 数据源配置ID
     * @param tableMetadata 表元数据
     * @param moduleName 模块名
     * @param packageName 包名
     * @param sceneType 场景类型
     * @param modelType 模型类型
     * @return 代码生成表配置对象
     */
    private CodegenTable createCodegenTableFromMetadata(Long dataSourceConfigId, DatabaseTableMetadata tableMetadata,
                                                      String moduleName, String packageName,
                                                      Integer sceneType, Integer modelType) {
        CodegenTable codegenTable = new CodegenTable();
        codegenTable.setDatasourceId(dataSourceConfigId);
        codegenTable.setTableName(tableMetadata.getTableName());
        codegenTable.setTableComment(tableMetadata.getComment() != null ? tableMetadata.getComment() : "");
        codegenTable.setModuleName(moduleName);
        codegenTable.setPackageName(packageName);
        codegenTable.setScene(sceneType);
        codegenTable.setTemplateType(modelType);
        
        // 使用Date类型以兼容父类方法
        Date now = new Date();
        codegenTable.setCreateTime(now);
        codegenTable.setUpdateTime(now);
        
        return codegenTable;
    }
    
    /**
     * 从数据库导入表结构
     * 
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
    @Override
    public List<Long> importTablesFromDatabase(Long dataSourceConfigId, List<String> tableNames,
                                             String moduleName, String packageName,
                                             Integer sceneType, Integer modelType) {
        if (dataSourceConfigId == null) {
            throw new CodegenBusinessException(400, "数据源配置ID不能为空");
        }
        if (CollectionUtils.isEmpty(tableNames)) {
            throw new CodegenBusinessException(400, "表名列表不能为空");
        }
        if (!StringUtils.hasText(moduleName)) {
            throw new CodegenBusinessException(400, "模块名不能为空");
        }
        if (!StringUtils.hasText(packageName)) {
            throw new CodegenBusinessException(400, "包名不能为空");
        }
        
        List<Long> importedTableIds = new ArrayList<>(tableNames.size());
        
        for (String tableName : tableNames) {
            if (!StringUtils.hasText(tableName)) {
                logger.warn("跳过空表名");
                continue;
            }
            
            try {
                // 获取数据库表信息
                DatabaseTableMetadata tableMetadata = retrieveTableMetadata(dataSourceConfigId, tableName);
                if (tableMetadata == null) {
                    logger.warn("跳过不存在的表: {}", tableName);
                    continue;
                }
                
                // 创建代码生成表配置
                CodegenTable codegenTable = createCodegenTableFromMetadata(dataSourceConfigId, tableMetadata, 
                        moduleName, packageName, sceneType, modelType);
                
                // 保存表配置
                Long savedTableId = codegenTableRepository.save(codegenTable);
                
                // 导入字段信息
                List<CodegenColumn> fields = tableMetadata.getFields();
                if (fields != null && !fields.isEmpty()) {
                    importColumns(savedTableId, fields);
                }
                
                importedTableIds.add(savedTableId);
                logger.info("成功导入表: {}", tableName);
            } catch (Exception e) {
                logger.error("导入表失败: {}", tableName, e);
                throw new RuntimeException("导入表失败: " + tableName, e);
            }
        }
        
        return importedTableIds;
    }
    
    /**
     * 导入表字段
     * 
     * @param tableId 表ID
     * @param fields 字段列表
     */
    private void importColumns(Long tableId, List<CodegenColumn> fields) {
        if (CollectionUtils.isEmpty(fields)) {
            return;
        }
        
        fields.stream()
            .filter(Objects::nonNull)
            .forEach(field -> {
                // 设置表ID
                field.setTableId(tableId);
                codegenColumnRepository.save(field);
            });
    }
    
    /**
     * 更新表定义配置
     * 
     * @param request 更新请求
     * @throws IllegalArgumentException 当请求参数无效时抛出
     * @throws RuntimeException 当表配置不存在或更新失败时抛出
     */
    @Override
    public void updateCodegenTable(CodegenTableRequest request) {
        // 参数验证
        if (request == null) {
            throw new CodegenBusinessException(400, "请求参数不能为空");
        }
        final Long tableId = request.getId();
        if (tableId == null) {
            throw new CodegenBusinessException(400, "表ID不能为空");
        }
        
        logger.debug("开始更新表配置，ID: {}", tableId);
        
        try {
            // 查询现有表配置
            CodegenTable existingTable = codegenTableRepository.findById(tableId)
                .orElseThrow(() -> new RuntimeException("表配置不存在，ID: " + tableId));
            
            // 更新表配置基本信息
            updateTableBaseInfo(existingTable, request);
            
            // 保存表配置更新
            codegenTableRepository.update(existingTable);
            
            // 如果有列配置，更新列信息
            List<CodegenColumnRequest> columns = request.getColumns();
            if (columns != null && !columns.isEmpty()) {
                logger.debug("开始更新列配置，表ID: {}, 列数量: {}", tableId, columns.size());
                updateColumns(tableId, columns);
            }
            
            logger.info("表配置更新成功，ID: {}", tableId);
        } catch (RuntimeException e) {
            logger.error("表配置更新失败，ID: {}", tableId, e);
            throw e;
        }
    }
    
    /**
     * 更新表的基本信息
     * 
     * @param existingTable 现有表配置
     * @param request 更新请求
     */
    private void updateTableBaseInfo(CodegenTable existingTable, CodegenTableRequest request) {
        // 直接在现有表对象上更新属性
        org.springframework.beans.BeanUtils.copyProperties(request, existingTable, 
                "id", "createTime", "updateTime", "deleted", "createBy", "updateBy");
        
        // 更新时间戳
        existingTable.setUpdateTime(new Date());
    }
    
    /**
     * 同步数据库表结构到代码生成配置
     * 
     * @param tableId 表配置ID
     * @throws IllegalArgumentException 当表配置ID为空时抛出
     * @throws RuntimeException 当同步失败时抛出
     */
    @Override
    public void syncTableFromDatabase(Long tableId) {
        if (tableId == null) {
            throw new CodegenBusinessException(400, "表配置ID不能为空");
        }

        try {
            CodegenTable codegenTable = codegenTableRepository.findById(tableId)
                .orElseThrow(() -> {
                    logger.error("表配置不存在，ID: {}", tableId);
                    return new RuntimeException("表配置不存在，ID: " + tableId);
                });

            // 获取数据源配置ID和表名
            Long datasourceId = codegenTable.getDatasourceId();
            String tableName = codegenTable.getTableName();
            
            DatabaseTableMetadata tableMetadata = retrieveTableMetadata(datasourceId, tableName);

            if (tableMetadata == null) {
                logger.error("数据库表不存在: {}", tableName);
                throw new RuntimeException("数据库表不存在: " + tableName);
            }

            // 执行同步操作
            syncTableStructure(codegenTable, tableMetadata);

            logger.info("成功同步表结构，表ID: {}, 表名: {}", tableId, tableName);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("同步表结构失败: {}", e.getMessage(), e);
            throw new RuntimeException("同步表结构失败：" + e.getMessage());
        }
    }
    
    /**
     * 执行表结构同步逻辑
     * 
     * @param codegenTable 代码生成表配置
     * @param tableMetadata 表信息
     */
    private void syncTableStructure(CodegenTable codegenTable, DatabaseTableMetadata tableMetadata) {
        try {
            // 更新表信息
            boolean hasTableUpdate = updateTableComment(codegenTable, tableMetadata.getComment());
            
            if (hasTableUpdate) {
                codegenTable.setUpdateTime(new Date());
                codegenTableRepository.update(codegenTable);
            }

            // 同步字段
            syncColumns(codegenTable.getId(), tableMetadata.getFields());
        } catch (Exception e) {
            logger.error("同步表结构细节失败: {}", e.getMessage(), e);
            throw new RuntimeException("同步失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新表注释
     * 
     * @param codegenTable 代码生成表配置
     * @param newComment 新注释
     * @return 是否有更新
     */
    private boolean updateTableComment(CodegenTable codegenTable, String newComment) {
        String currentComment = codegenTable.getTableComment();
        if (!StringUtils.pathEquals(currentComment, newComment)) {
            codegenTable.setTableComment(newComment);
            return true;
        }
        return false;
    }
    
    /**
     * 同步表的字段信息
     * 
     * @param tableId 表ID
     * @param tableFields 表字段列表
     */
    private void syncColumns(Long tableId, List<CodegenColumn> tableFields) {
        // 获取现有字段
        List<CodegenColumn> existingColumns = retrieveColumnsByTableId(tableId);
        Map<String, CodegenColumn> columnMap = buildColumnNameMap(existingColumns);

        // 统计信息
        int addedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;

        // 处理新增和更新的字段
        if (!CollectionUtils.isEmpty(tableFields)) {
            for (CodegenColumn field : tableFields) {
                if (field != null) {
                    String columnName = field.getColumnName();
                    if (columnMap.containsKey(columnName)) {
                        // 更新现有字段
                        CodegenColumn existingColumn = columnMap.get(columnName);
                        updateColumn(existingColumn, field);
                        codegenColumnRepository.update(existingColumn);
                        columnMap.remove(columnName); // 从待删除列表中移除
                        updatedCount++;
                    } else {
                        // 新增字段
                        CodegenColumn newColumn = createCodegenColumn(tableId, field);
                        codegenColumnRepository.save(newColumn);
                        addedCount++;
                    }
                }
            }
        }

        // 删除不再存在的字段
        deletedCount = deleteObsoleteColumns(columnMap);

        logger.debug("表ID: {} 的字段同步完成，新增: {} 个，更新: {} 个，删除: {} 个",
                tableId, addedCount, updatedCount, deletedCount);
    }
    
    /**
     * 构建字段映射表（按列名）
     * 
     * @param columns 字段列表
     * @return 字段名到字段对象的映射
     */
    private Map<String, CodegenColumn> buildColumnNameMap(List<CodegenColumn> columns) {
        if (CollectionUtils.isEmpty(columns)) {
            return new HashMap<>();
        }
        
        return columns.stream()
            .filter(Objects::nonNull)
            .filter(column -> column.getColumnName() != null)
            .collect(Collectors.toMap(CodegenColumn::getColumnName, Function.identity()));
    }
    
    /**
     * 删除过时的字段
     * 
     * @param obsoleteColumns 过时的字段映射
     * @return 删除的字段数量
     */
    private int deleteObsoleteColumns(Map<String, CodegenColumn> obsoleteColumns) {
        int count = 0;
        for (CodegenColumn column : obsoleteColumns.values()) {
            if (column != null) {
                try {
                    codegenColumnRepository.deleteById(column.getId());
                    count++;
                } catch (Exception e) {
                    logger.error("删除过时字段失败，ID: {}", column.getId(), e);
                }
            }
        }
        return count;
    }
    
    /**
     * 更新列配置
     * 
     * @param tableId 表ID
     * @param columnRequests 列配置请求列表
     */
    private void updateColumns(Long tableId, List<CodegenColumnRequest> columnRequests) {
        if (CollectionUtils.isEmpty(columnRequests)) {
            return;
        }
        
        // 获取现有字段
        List<CodegenColumn> existingColumns = retrieveColumnsByTableId(tableId);
        Map<Long, CodegenColumn> columnIdMap = buildColumnIdMap(existingColumns);
        
        // 更新或新增字段
        processColumnRequests(tableId, columnRequests, columnIdMap);
        
        // 删除不在请求列表中的字段
        deleteUnrequestedColumns(columnIdMap);
    }
    
    /**
     * 构建字段ID映射表
     * 
     * @param columns 字段列表
     * @return 字段ID到字段对象的映射
     */
    private Map<Long, CodegenColumn> buildColumnIdMap(List<CodegenColumn> columns) {
        if (CollectionUtils.isEmpty(columns)) {
            return Collections.emptyMap();
        }
        
        return columns.stream()
                .collect(Collectors.toMap(CodegenColumn::getId, Function.identity()));
    }
    
    /**
     * 处理列配置请求
     * 
     * @param tableId 表ID
     * @param columnRequests 列配置请求列表
     * @param columnIdMap 字段ID映射表
     */
    private void processColumnRequests(Long tableId, List<CodegenColumnRequest> columnRequests,
                                     Map<Long, CodegenColumn> columnIdMap) {
        columnRequests.stream()
            .filter(Objects::nonNull)
            .forEach(request -> {
                try {
                    processSingleColumnRequest(tableId, request, columnIdMap);
                } catch (Exception e) {
                    logger.error("处理列配置失败，列名: {}", request.getColumnName(), e);
                }
            });
    }
    
    /**
     * 处理单个列配置请求
     * 
     * @param tableId 表ID
     * @param request 列配置请求
     * @param columnIdMap 字段ID映射表
     */
    private void processSingleColumnRequest(Long tableId, CodegenColumnRequest request, 
                                          Map<Long, CodegenColumn> columnIdMap) {
        // 获取请求中的ID
        Long requestId = request.getId();
        
        // 使用CodegenConverter进行转换
        CodegenColumn column = codegenConverter.toCodegenColumn(request);
        // 单独设置表ID
        column.setTableId(tableId);
        
        Date now = new Date();
        
        if (requestId != null && columnIdMap.containsKey(requestId)) {
            // 更新现有字段，保留时间戳
            CodegenColumn existingColumn = columnIdMap.get(requestId);
            column.setCreateTime(existingColumn.getCreateTime());
            column.setUpdateTime(now);
            
            codegenColumnRepository.update(column);
            columnIdMap.remove(requestId);
        } else {
            // 新增字段
            column.setCreateTime(now);
            column.setUpdateTime(now);
            codegenColumnRepository.save(column);
        }
    }
    
    /**
     * 删除未在请求列表中的列
     * 
     * @param columnIdMap 字段ID映射表
     */
    private void deleteUnrequestedColumns(Map<Long, CodegenColumn> columnIdMap) {
        columnIdMap.values().forEach(column -> {
            try {
                codegenColumnRepository.deleteById(column.getId());
                logger.debug("删除字段成功，ID: {}", column.getId());
            } catch (Exception e) {
                logger.error("删除字段失败，ID: {}", column.getId(), e);
            }
        });
    }
    
    /**
     * 获取表字段列表
     * 
     * @param tableId 表ID
     * @return 字段列表
     */
    public List<CodegenColumn> getColumnsByTableId(Long tableId) {
        // 保留原方法名以兼容测试
        return retrieveColumnsByTableId(tableId);
    }
    
    /**
     * 获取表字段列表（内部实现）
     * 
     * @param tableId 表ID
     * @return 字段列表
     */
    public List<CodegenColumn> retrieveColumnsByTableId(Long tableId) {
        if (tableId == null) {
            return Collections.emptyList();
        }
        
        try {
            return codegenColumnRepository.findByCriteria(tableId);
        } catch (Exception e) {
            logger.error("获取表字段列表失败，表ID: {}", tableId, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 创建代码生成字段配置
     * 
     * @param tableId 表ID
     * @param sourceColumn 源字段
     * @return 代码生成字段配置
     */
    private CodegenColumn createCodegenColumn(Long tableId, CodegenColumn sourceColumn) {
        CodegenColumn column = new CodegenColumn();
        
        // 设置表ID
        column.setTableId(tableId);
        
        // 设置创建时间和更新时间
        Date now = new Date();
        column.setCreateTime(now);
        column.setUpdateTime(now);
        
        // 复制源字段的其他属性
        if (sourceColumn != null) {
            // 使用BeanUtils进行属性复制
            org.springframework.beans.BeanUtils.copyProperties(sourceColumn, column, 
                "id", "tableId", "createTime", "updateTime", "deleted", "createBy", "updateBy");
        }
        
        return column;
    }
    
    /**
     * 更新字段配置
     * 
     * @param targetColumn 目标字段
     * @param sourceColumn 源字段
     */
    private void updateColumn(CodegenColumn targetColumn, CodegenColumn sourceColumn) {
        if (targetColumn == null || sourceColumn == null) {
            return;
        }
        
        try {
            // 直接更新基础信息，不再使用反射
            updateBasicColumnProperties(targetColumn, sourceColumn);
            
            // 如果Java字段或类型为空，则从源字段复制
            updateJavaFieldPropertiesIfEmpty(targetColumn, sourceColumn);
            
            // 更新时间戳
            targetColumn.setUpdateTime(new Date());
        } catch (Exception e) {
            logger.error("更新字段配置失败", e);
        }
    }
    
    /**
     * 更新基础字段属性
     * 
     * @param target 目标字段
     * @param source 源字段
     */
    private void updateBasicColumnProperties(CodegenColumn target, CodegenColumn source) {
        // 直接设置属性，不再使用反射
        target.setColumnComment(source.getColumnComment());
        target.setPrimaryKey(source.getPrimaryKey());
        target.setNullable(source.getNullable());
        target.setAutoIncrement(source.getAutoIncrement());
    }
    
    /**
     * 仅当目标字段为空时更新Java相关属性
     * 
     * @param target 目标字段
     * @param source 源字段
     */
    private void updateJavaFieldPropertiesIfEmpty(CodegenColumn target, CodegenColumn source) {
        // 如果Java字段为空，从源字段复制
        if (target.getJavaField() == null && source.getJavaField() != null) {
            target.setJavaField(source.getJavaField());
        }
        
        if (target.getJavaType() == null && source.getJavaType() != null) {
            target.setJavaType(source.getJavaType());
        }
        
        if (target.getHtmlType() == null && source.getHtmlType() != null) {
            target.setHtmlType(source.getHtmlType());
        }
    }
    
    /**
     * 删除表配置
     * 
     * @param tableId 表ID
     * @throws IllegalArgumentException 当表ID为空时抛出
     * @throws RuntimeException 当删除失败时抛出
     */
    @Override
    public void deleteTable(Long tableId) {
        if (tableId == null) {
            throw new CodegenBusinessException(400, "表ID不能为空");
        }
        
        try {
            // 先删除相关的字段配置
            deleteTableColumns(tableId);
            
            // 然后删除表配置
            codegenTableRepository.deleteById(tableId);
            
            logger.info("删除表配置成功，表ID: {}", tableId);
        } catch (CodegenBusinessException e) {
            throw e;
        } catch (Exception e) {
            logger.error("删除表配置失败，表ID: {}", tableId, e);
            throw new CodegenBusinessException(500, "删除表配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除表的所有列配置
     * 
     * @param tableId 表ID
     */
    private void deleteTableColumns(Long tableId) {
        List<CodegenColumn> columns = retrieveColumnsByTableId(tableId);
        if (!CollectionUtils.isEmpty(columns)) {
            columns.stream()
                .filter(column -> column.getId() != null)
                .forEach(column -> {
                    try {
                        codegenColumnRepository.deleteById(column.getId());
                    } catch (Exception e) {
                        logger.error("删除列配置失败，列ID: {}", column.getId(), e);
                        throw new CodegenBusinessException(500, "删除列配置失败: " + e.getMessage());
                    }
                });
        }
    }
}