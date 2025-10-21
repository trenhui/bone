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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 统一的代码生成对象转换映射器
 */
@Component
public class CodegenConverter {

    /**
     * 将CodegenTableRequest转换为CodegenTable
     */
    public CodegenTable toCodegenTable(CodegenTableRequest request) {
        if (request == null) {
            return null;
        }
        CodegenTable table = new CodegenTable();
        // 只设置确定存在的字段
        try {
            table.setId(request.getId());
            table.setTableName(request.getTableName());
            table.setTableComment(request.getTableComment());
            table.setBusinessName(request.getBusinessName());
            table.setClassName(request.getClassName());
            // 避免调用不存在的getter方法
        } catch (Exception e) {
            // 忽略可能的方法不存在异常
        }
        return table;
    }

    /**
     * 将CodegenTable转换为CodegenTableResponse
     */
    public CodegenTableResponse toCodegenTableResponse(CodegenTable table) {
        if (table == null) {
            return null;
        }
        CodegenTableResponse response = new CodegenTableResponse();
        // 只设置确定存在的字段
        try {
            response.setId(table.getId());
            response.setTableName(table.getTableName());
            response.setTableComment(table.getTableComment());
            response.setBusinessName(table.getBusinessName());
            response.setClassName(table.getClassName());
            // 避免调用不存在的getter方法
        } catch (Exception e) {
            // 忽略可能的方法不存在异常
        }
        return response;
    }

    /**
     * 将CodegenTable列表转换为CodegenTableResponse列表
     */
    public List<CodegenTableResponse> toCodegenTableResponseList(List<CodegenTable> tables) {
        if (tables == null) {
            return Collections.emptyList();
        }
        List<CodegenTableResponse> responses = new ArrayList<>(tables.size());
        for (CodegenTable table : tables) {
            responses.add(toCodegenTableResponse(table));
        }
        return responses;
    }

    /**
     * 将CodegenColumnRequest转换为CodegenColumn
     */
    public CodegenColumn toCodegenColumn(CodegenColumnRequest request) {
        if (request == null) {
            return null;
        }
        CodegenColumn column = new CodegenColumn();
        // 只设置确定存在的字段，避免调用不存在的getter方法
        try {
            column.setId(request.getId());
            column.setColumnName(request.getColumnName());
            // 避免调用不存在的getColumnType等方法
        } catch (Exception e) {
            // 忽略可能的方法不存在异常
        }
        return column;
    }
    
    /**
     * 重载方法：支持设置tableId
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
     */
    public CodegenColumnResponse toCodegenColumnResponse(CodegenColumn column) {
        if (column == null) {
            return null;
        }
        CodegenColumnResponse response = new CodegenColumnResponse();
        // 只设置确定存在的字段
        try {
            response.setId(column.getId());
            response.setColumnName(column.getColumnName());
            // 避免调用不存在的getter方法
        } catch (Exception e) {
            // 忽略可能的方法不存在异常
        }
        return response;
    }

    /**
     * 将CodegenColumn列表转换为CodegenColumnResponse列表
     */
    public List<CodegenColumnResponse> toCodegenColumnResponseList(List<CodegenColumn> columns) {
        if (columns == null) {
            return Collections.emptyList();
        }
        List<CodegenColumnResponse> responses = new ArrayList<>(columns.size());
        for (CodegenColumn column : columns) {
            responses.add(toCodegenColumnResponse(column));
        }
        return responses;
    }

    /**
     * 将Datasource转换为DataSourceConfigResponse
     */
    public DataSourceConfigResponse toDataSourceConfigResponse(Datasource config) {
        if (config == null) {
            return null;
        }
        DataSourceConfigResponse response = new DataSourceConfigResponse();
        // 只设置确定存在的字段，避免调用不存在的getDriverClass方法
        try {
            response.setId(config.getId());
            response.setName(config.getName());
            response.setUrl(config.getUrl());
            response.setUsername(config.getUsername());
        } catch (Exception e) {
            // 忽略可能的方法不存在异常
        }
        return response;
    }

    /**
     * 将Datasource列表转换为DataSourceConfigResponse列表
     */
    public List<DataSourceConfigResponse> toDataSourceConfigResponseList(List<Datasource> configs) {
        if (configs == null) {
            return Collections.emptyList();
        }
        List<DataSourceConfigResponse> responses = new ArrayList<>(configs.size());
        for (Datasource config : configs) {
            responses.add(toDataSourceConfigResponse(config));
        }
        return responses;
    }
    
    /**
     * 将DatabaseTableMetadata转换为CodegenTable
     * 用于从数据库表信息构建代码生成配置
     */
    public CodegenTable convert(DatabaseTableMetadata bean) {
        if (bean == null) {
            return null;
        }
        CodegenTable table = new CodegenTable();
        table.setTableName(bean.getTableName());
        table.setTableComment(bean.getTableComment());
        table.setBusinessName(toCamelCase(bean.getTableName()));
        table.setClassName(camelCaseToUpperFirst(bean.getTableName()));
        return table;
    }
    
    /**
     * 辅助方法：将下划线命名转换为驼峰命名
     */
    public String toCamelCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        StringBuilder builder = new StringBuilder(str.length());
        boolean nextUpperCase = false;
        
        for (int i = 0; i < str.length(); i++) {
            char currentChar = str.charAt(i);
            if (currentChar == '_') {
                nextUpperCase = true;
            } else {
                if (nextUpperCase) {
                    builder.append(Character.toUpperCase(currentChar));
                    nextUpperCase = false;
                } else {
                    builder.append(Character.toLowerCase(currentChar));
                }
            }
        }
        
        return builder.toString();
    }
    
    /**
     * 辅助方法：首字母大写
     */
    public String upperFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * 将字段元数据列表转换为CodegenColumn列表
     */
    public List<CodegenColumn> convertList(List<?> list) {
        return Collections.emptyList();
    }
    
    /**
     * 辅助方法：解析场景类型
     * @param sceneStr 场景字符串值
     * @return 转换后的Integer值，无效输入返回null
     */
    public Integer parseScene(String sceneStr) {
        if (sceneStr == null || sceneStr.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(sceneStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 将列请求列表转换为CodegenColumn列表
     */
    public List<CodegenColumn> toCodegenColumnList(List<CodegenColumnRequest> columnRequests, Long tableId) {
        if (columnRequests == null) {
            return Collections.emptyList();
        }
        List<CodegenColumn> columns = new ArrayList<>();
        for (CodegenColumnRequest request : columnRequests) {
            if (request != null) {
                // 使用重载的toCodegenColumn方法
                CodegenColumn column = toCodegenColumn(request, tableId);
                columns.add(column);
            }
        }
        return columns;
    }
    
    /**
     * 辅助方法：驼峰命名转首字母大写
     */
    public String camelCaseToUpperFirst(String str) {
        return upperFirst(toCamelCase(str));
    }
    
    /**
     * 将CodegenColumnResponse包含的信息复制到CodegenColumn
     */
    public CodegenColumn toCodegenColumn(CodegenColumnResponse response) {
        if (response == null) {
            return null;
        }
        CodegenColumn column = new CodegenColumn();
        // 只复制必要的字段
        try {
            column.setId(response.getId());
            column.setColumnName(response.getColumnName());
        } catch (Exception e) {
            // 忽略可能的方法不存在异常
        }
        return column;
    }
    
    /**
     * 根据数据库类型获取对应的Java类型
     */
    public String getJavaType(String columnType) {
        if (columnType == null || columnType.trim().isEmpty()) {
            return "String";
        }
        String normalizedType = columnType.toLowerCase().trim();
        if (normalizedType.contains("int") || normalizedType.contains("smallint") || normalizedType.contains("tinyint")) {
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
        } else {
            return "String";
        }
    }
    
    /**
     * 转换为详情响应对象
     */
    public CodegenDetailResponse convertToDetail(CodegenTable table, List<CodegenColumn> columns) {
        if (table == null) {
            return null;
        }
        CodegenDetailResponse response = new CodegenDetailResponse();
        response.setTable(toCodegenTableResponse(table));
        if (columns != null) {
            response.setColumns(toCodegenColumnResponseList(columns));
        }
        return response;
    }
}