package com.bone.tool.codegen.application.converter;

import com.bone.tool.codegen.application.dto.CodegenTableRequest;
import com.bone.tool.codegen.application.dto.CodegenTableResponse;
import com.bone.tool.codegen.application.dto.CodegenColumnRequest;
import com.bone.tool.codegen.application.dto.CodegenColumnResponse;
import com.bone.tool.codegen.application.dto.DataSourceConfigResponse;
import com.bone.tool.codegen.application.dto.CodegenDetailResponse;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.entity.Datasource;
import com.bone.tool.codegen.domain.entity.DatabaseTableMetadata;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 代码生成对象转换器
 * <p>
 * 负责在不同层对象之间进行转换，如DTO与Entity之间的映射
 * 遵循单一职责原则，专注于对象转换功能，提供类型安全的转换方法
 * <p>
 * 支持从数据库表元数据生成代码配置对象，以及DTO与领域模型之间的双向转换
 */
@Component
public class CodegenConverter {

    private static final Logger logger = LoggerFactory.getLogger(CodegenConverter.class);
    private static final Pattern UNDER_SCORE_PATTERN = Pattern.compile("_([a-z])");
    
    // 数据库类型到Java类型的映射，提高查找效率
    private static final Map<String, String> DB_TYPE_TO_JAVA_TYPE_MAP = new HashMap<>();
    
    static {
        // 初始化数据库类型到Java类型的映射
        DB_TYPE_TO_JAVA_TYPE_MAP.put("int", "Integer");
        DB_TYPE_TO_JAVA_TYPE_MAP.put("smallint", "Integer");
        DB_TYPE_TO_JAVA_TYPE_MAP.put("tinyint", "Integer");
        DB_TYPE_TO_JAVA_TYPE_MAP.put("bigint", "Long");
        DB_TYPE_TO_JAVA_TYPE_MAP.put("decimal", "BigDecimal");
        DB_TYPE_TO_JAVA_TYPE_MAP.put("numeric", "BigDecimal");
        DB_TYPE_TO_JAVA_TYPE_MAP.put("double", "Double");
        DB_TYPE_TO_JAVA_TYPE_MAP.put("float", "Double");
        DB_TYPE_TO_JAVA_TYPE_MAP.put("date", "LocalDateTime");
        DB_TYPE_TO_JAVA_TYPE_MAP.put("time", "LocalDateTime");
        DB_TYPE_TO_JAVA_TYPE_MAP.put("datetime", "LocalDateTime");
        DB_TYPE_TO_JAVA_TYPE_MAP.put("timestamp", "LocalDateTime");
        DB_TYPE_TO_JAVA_TYPE_MAP.put("bit", "Boolean");
        DB_TYPE_TO_JAVA_TYPE_MAP.put("bool", "Boolean");
        DB_TYPE_TO_JAVA_TYPE_MAP.put("boolean", "Boolean");
    }

    /**
     * 将CodegenTableRequest转换为CodegenTable
     * 
     * @param request 表请求DTO对象
     * @return 转换后的领域模型对象，请求为空时返回null
     */
    public CodegenTable toCodegenTable(CodegenTableRequest request) {
        if (request == null) {
            logger.debug("Null request passed to toCodegenTable");
            return null;
        }
        
        CodegenTable table = new CodegenTable();
        try {
            table.setId(request.getId());
            table.setTableName(request.getTableName());
            table.setTableComment(request.getTableComment());
            table.setBusinessName(request.getBusinessName());
            table.setClassName(request.getClassName());
            table.setModuleName(request.getModuleName());
            table.setPackageName(request.getPackageName());
            // 添加类型转换，将String转换为Integer
            table.setScene(Integer.parseInt(request.getScene()));
            table.setTemplateType(request.getTemplateType());
        } catch (Exception e) {
            logger.error("Error converting CodegenTableRequest to CodegenTable: {}", e.getMessage(), e);
        }
        
        return table;
    }

    /**
     * 将CodegenTable转换为CodegenTableResponse
     * 
     * @param table 表领域模型对象
     * @return 转换后的响应DTO对象，表对象为空时返回null
     */
    public CodegenTableResponse toCodegenTableResponse(CodegenTable table) {
        if (table == null) {
            logger.debug("Null table passed to toCodegenTableResponse");
            return null;
        }
        
        CodegenTableResponse response = new CodegenTableResponse();
        try {
            response.setId(table.getId());
            response.setTableName(table.getTableName());
            response.setTableComment(table.getTableComment());
            response.setBusinessName(table.getBusinessName());
            response.setClassName(table.getClassName());
            response.setModuleName(table.getModuleName());
            response.setPackageName(table.getPackageName());
            response.setScene(table.getScene());
            response.setTemplateType(table.getTemplateType());
        } catch (Exception e) {
            logger.error("Error converting CodegenTable to CodegenTableResponse: {}", e.getMessage(), e);
        }
        
        return response;
    }

    /**
     * 将CodegenTable列表转换为CodegenTableResponse列表
     * 
     * @param tables 表领域模型对象列表
     * @return 转换后的响应DTO对象列表，输入为空时返回空列表
     */
    public List<CodegenTableResponse> toCodegenTableResponseList(List<CodegenTable> tables) {
        if (tables == null) {
            logger.debug("Null tables list passed to toCodegenTableResponseList");
            return Collections.emptyList();
        }
        
        return tables.stream()
                .map(this::toCodegenTableResponse)
                .collect(Collectors.toList());
    }

    /**
     * 将CodegenColumnRequest转换为CodegenColumn
     * 
     * @param request 列请求DTO对象
     * @return 转换后的领域模型对象，请求为空时返回null
     */
    public CodegenColumn toCodegenColumn(CodegenColumnRequest request) {
        if (request == null) {
            logger.debug("Null request passed to toCodegenColumn");
            return null;
        }
        
        CodegenColumn column = new CodegenColumn();
        try {
            column.setId(request.getId());
            column.setColumnName(request.getColumnName());
            column.setColumnComment(request.getColumnComment());
            column.setPrimaryKey(request.getPrimaryKey());
            column.setNullable(request.getNullable());
            column.setAutoIncrement(request.getAutoIncrement());
            // 移除对不存在方法的调用
            column.setJavaField(request.getJavaField());
            column.setJavaType(request.getJavaType());
            column.setHtmlType(request.getHtmlType());
            // 移除对不存在方法的调用
        } catch (Exception e) {
            logger.error("Error converting CodegenColumnRequest to CodegenColumn: {}", e.getMessage(), e);
        }
        
        return column;
    }
    
    /**
     * 重载方法：支持设置tableId
     * 
     * @param request 列请求DTO对象
     * @param tableId 表ID
     * @return 转换后的领域模型对象，请求为空时返回null
     */
    public CodegenColumn toCodegenColumn(CodegenColumnRequest request, Long tableId) {
        CodegenColumn column = toCodegenColumn(request);
        if (column != null && tableId != null) {
            column.setTableId(tableId);
        }
        return column;
    }

    /**
     * 将CodegenColumn转换为CodegenColumnResponse
     * 
     * @param column 列领域模型对象
     * @return 转换后的响应DTO对象，列对象为空时返回null
     */
    public CodegenColumnResponse toCodegenColumnResponse(CodegenColumn column) {
        if (column == null) {
            logger.debug("Null column passed to toCodegenColumnResponse");
            return null;
        }
        
        CodegenColumnResponse response = new CodegenColumnResponse();
        try {
            response.setId(column.getId());
            response.setColumnName(column.getColumnName());
            response.setColumnComment(column.getColumnComment());
            response.setPrimaryKey(column.getPrimaryKey());
            response.setNullable(column.getNullable());
            response.setAutoIncrement(column.getAutoIncrement());
            // 移除对不存在方法的调用
            response.setJavaField(column.getJavaField());
            response.setJavaType(column.getJavaType());
            response.setHtmlType(column.getHtmlType());
            // 移除对不存在方法的调用
        } catch (Exception e) {
            logger.error("Error converting CodegenColumn to CodegenColumnResponse: {}", e.getMessage(), e);
        }
        
        return response;
    }

    /**
     * 将CodegenColumn列表转换为CodegenColumnResponse列表
     * 
     * @param columns 列领域模型对象列表
     * @return 转换后的响应DTO对象列表，输入为空时返回空列表
     */
    public List<CodegenColumnResponse> toCodegenColumnResponseList(List<CodegenColumn> columns) {
        if (columns == null) {
            logger.debug("Null columns list passed to toCodegenColumnResponseList");
            return Collections.emptyList();
        }
        
        return columns.stream()
                .map(this::toCodegenColumnResponse)
                .collect(Collectors.toList());
    }

    /**
     * 将Datasource转换为DataSourceConfigResponse
     * 
     * @param config 数据源配置领域模型对象
     * @return 转换后的响应DTO对象，配置对象为空时返回null
     */
    public DataSourceConfigResponse toDataSourceConfigResponse(Datasource config) {
        if (config == null) {
            logger.debug("Null config passed to toDataSourceConfigResponse");
            return null;
        }
        
        DataSourceConfigResponse response = new DataSourceConfigResponse();
        try {
            response.setId(config.getId());
            response.setName(config.getName());
            response.setUrl(config.getUrl());
            response.setUsername(config.getUsername());
            // 移除对不存在方法的调用
        } catch (Exception e) {
            logger.error("Error converting Datasource to DataSourceConfigResponse: {}", e.getMessage(), e);
        }
        
        return response;
    }

    /**
     * 将Datasource列表转换为DataSourceConfigResponse列表
     * 
     * @param configs 数据源配置领域模型对象列表
     * @return 转换后的响应DTO对象列表，输入为空时返回空列表
     */
    public List<DataSourceConfigResponse> toDataSourceConfigResponseList(List<Datasource> configs) {
        if (configs == null) {
            logger.debug("Null configs list passed to toDataSourceConfigResponseList");
            return Collections.emptyList();
        }
        
        return configs.stream()
                .map(this::toDataSourceConfigResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * 将DatabaseTableMetadata转换为CodegenTable
     * 用于从数据库表信息构建代码生成配置
     * 
     * @param metadata 数据库表元数据对象
     * @return 转换后的代码生成表配置对象，元数据为空时返回null
     */
    public CodegenTable toCodegenTableFromMetadata(DatabaseTableMetadata metadata) {
        if (metadata == null) {
            logger.debug("Null metadata passed to toCodegenTableFromMetadata");
            return null;
        }
        
        logger.debug("Converting database table metadata: {}", metadata.getTableName());
        
        CodegenTable table = new CodegenTable();
        try {
            table.setTableName(metadata.getTableName());
            table.setTableComment(metadata.getComment());
            table.setBusinessName(toCamelCase(metadata.getTableName()));
            table.setClassName(camelCaseToPascalCase(metadata.getTableName()));
        } catch (Exception e) {
            logger.error("Error converting DatabaseTableMetadata to CodegenTable: {}", e.getMessage(), e);
        }
        
        return table;
    }
    
    /**
     * 辅助方法：将下划线命名转换为驼峰命名（小驼峰）
     * 
     * @param str 待转换的字符串
     * @return 转换后的驼峰命名字符串，输入为空时返回原字符串
     */
    public String toCamelCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        // 使用正则表达式优化驼峰命名转换
        return UNDER_SCORE_PATTERN.matcher(str.toLowerCase())
                .replaceAll(matcher -> matcher.group(1).toUpperCase());
    }
    
    /**
     * 辅助方法：首字母大写
     * 
     * @param str 待转换的字符串
     * @return 首字母大写后的字符串，输入为空时返回原字符串
     */
    public String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * 类型安全地将对象列表转换为CodegenColumn列表
     * 
     * @param <T> 输入对象类型
     * @param objects 待转换的对象列表
     * @return 转换后的CodegenColumn列表，输入为空时返回空列表
     */
    public <T> List<CodegenColumn> toCodegenColumnListFromObjects(List<T> objects) {
        logger.debug("Converting object list to CodegenColumn list with size: {}", 
                     objects != null ? objects.size() : 0);
        
        if (objects == null) {
            return Collections.emptyList();
        }
        
        List<CodegenColumn> columns = new ArrayList<>(objects.size());
        for (T obj : objects) {
            if (obj instanceof CodegenColumn) {
                columns.add((CodegenColumn) obj);
            } else {
                logger.warn("Unexpected object type in list: {}", obj.getClass().getName());
            }
        }
        
        return columns;
    }
    
    /**
     * 辅助方法：解析场景类型
     * 
     * @param sceneStr 场景字符串值
     * @return 转换后的Integer值，无效输入返回null
     */
    public Integer parseSceneValue(String sceneStr) {
        if (sceneStr == null || sceneStr.trim().isEmpty()) {
            logger.debug("Empty scene string passed to parseSceneValue");
            return null;
        }
        
        try {
            return Integer.valueOf(sceneStr.trim());
        } catch (NumberFormatException e) {
            logger.warn("Invalid scene format: {}", sceneStr);
            return null;
        }
    }
    
    /**
     * 将列请求列表转换为CodegenColumn列表
     * 
     * @param columnRequests 列请求DTO对象列表
     * @param tableId 表ID
     * @return 转换后的CodegenColumn列表，输入为空时返回空列表
     */
    public List<CodegenColumn> toCodegenColumnList(List<CodegenColumnRequest> columnRequests, Long tableId) {
        if (columnRequests == null) {
            logger.debug("Null columnRequests list passed to toCodegenColumnList");
            return Collections.emptyList();
        }
        
        return columnRequests.stream()
                .filter(request -> request != null)
                .map(request -> toCodegenColumn(request, tableId))
                .filter(column -> column != null)
                .collect(Collectors.toList());
    }
    
    /**
     * 辅助方法：驼峰命名转帕斯卡命名（首字母大写）
     * 
     * @param str 待转换的字符串
     * @return 首字母大写的驼峰命名字符串，输入为空时返回原字符串
     */
    public String camelCaseToPascalCase(String str) {
        return capitalize(toCamelCase(str));
    }
    
    /**
     * 将CodegenColumnResponse包含的信息复制到CodegenColumn
     * 
     * @param response 列响应DTO对象
     * @return 转换后的领域模型对象，响应为空时返回null
     */
    public CodegenColumn toCodegenColumnFromResponse(CodegenColumnResponse response) {
        if (response == null) {
            logger.debug("Null response passed to toCodegenColumnFromResponse");
            return null;
        }
        
        CodegenColumn column = new CodegenColumn();
        try {
            column.setId(response.getId());
            column.setColumnName(response.getColumnName());
            column.setColumnComment(response.getColumnComment());
            column.setPrimaryKey(response.getPrimaryKey());
            column.setNullable(response.getNullable());
            column.setAutoIncrement(response.getAutoIncrement());
            // 移除对不存在方法的调用
            column.setJavaField(response.getJavaField());
            column.setJavaType(response.getJavaType());
            column.setHtmlType(response.getHtmlType());
            // 移除对不存在方法的调用
        } catch (Exception e) {
            logger.error("Error converting CodegenColumnResponse to CodegenColumn: {}", e.getMessage(), e);
        }
        
        return column;
    }
    
    /**
     * 根据数据库类型获取对应的Java类型
     * 
     * @param columnType 数据库列类型字符串
     * @return 对应的Java类型字符串，默认返回String
     */
    public String determineJavaType(String columnType) {
        if (columnType == null || columnType.trim().isEmpty()) {
            logger.debug("Empty columnType passed to determineJavaType, defaulting to String");
            return "String";
        }
        
        String normalizedType = columnType.toLowerCase().trim();
        
        // 优化类型匹配逻辑
        // 1. 精确匹配优先
        if (DB_TYPE_TO_JAVA_TYPE_MAP.containsKey(normalizedType)) {
            return DB_TYPE_TO_JAVA_TYPE_MAP.get(normalizedType);
        }
        
        // 2. 子字符串匹配
        for (Map.Entry<String, String> entry : DB_TYPE_TO_JAVA_TYPE_MAP.entrySet()) {
            if (normalizedType.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 3. 分类匹配
        if (normalizedType.contains("int") && !normalizedType.contains("bigint")) {
            return "Integer";
        } else if (normalizedType.contains("bigint")) {
            return "Long";
        } else if (normalizedType.contains("decimal") || normalizedType.contains("numeric")) {
            return "BigDecimal";
        } else if (normalizedType.contains("double") || normalizedType.contains("float")) {
            return "Double";
        } else if (normalizedType.contains("date") || normalizedType.contains("time")) {
            return "LocalDateTime";
        } else if (normalizedType.contains("bit") || normalizedType.contains("bool")) {
            return "Boolean";
        }
        
        // 默认返回String类型
        logger.debug("Unknown database type '{}', defaulting to String", normalizedType);
        return "String";
    }
    
    /**
     * 转换为代码生成详情响应对象
     * 
     * @param table 表领域模型对象
     * @param columns 列领域模型对象列表
     * @return 包含表和列信息的详情响应对象，表为空时返回null
     */
    public CodegenDetailResponse toCodegenDetailResponse(CodegenTable table, List<CodegenColumn> columns) {
        if (table == null) {
            logger.debug("Null table passed to toCodegenDetailResponse");
            return null;
        }
        
        CodegenDetailResponse response = new CodegenDetailResponse();
        try {
            response.setTable(toCodegenTableResponse(table));
            response.setColumns(toCodegenColumnResponseList(columns));
        } catch (Exception e) {
            logger.error("Error converting to CodegenDetailResponse: {}", e.getMessage(), e);
        }
        
        return response;
    }
}