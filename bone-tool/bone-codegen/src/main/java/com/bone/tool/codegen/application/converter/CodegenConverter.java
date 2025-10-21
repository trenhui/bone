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
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 统一的代码生成对象转换映射器
 */
@Mapper(componentModel = "spring")
public interface CodegenConverter {
    // ========== 实体到DTO的基础转换方法 ==========

    /**
     * 将CodegenTable转换为CodegenTableResponse
     */
    CodegenTableResponse toCodegenTableResponse(CodegenTable table);

    /**
     * 将CodegenTable列表转换为CodegenTableResponse列表
     */
    List<CodegenTableResponse> toCodegenTableResponseList(List<CodegenTable> tables);

    /**
     * 将CodegenColumn转换为CodegenColumnResponse
     */
    CodegenColumnResponse toCodegenColumnResponse(CodegenColumn column);

    /**
     * 将CodegenColumn列表转换为CodegenColumnResponse列表
     */
    List<CodegenColumnResponse> toCodegenColumnResponseList(List<CodegenColumn> columns);

    /**
     * 将DataSourceConfig转换为DataSourceConfigResponse
     */
    DataSourceConfigResponse toDataSourceConfigResponse(Datasource config);

    /**
     * 将DataSourceConfig列表转换为DataSourceConfigResponse列表
     */
    List<DataSourceConfigResponse> toDataSourceConfigResponseList(List<Datasource> configs);
    
    // ========== 纯转换方法，不包含业务逻辑 ==========
    // 遵循单一职责原则，Mapper只负责对象间的属性映射
    // 业务逻辑（如设置数据源名称）应由服务层处理
    
    /**
     * 将DatabaseTableMetadata转换为CodegenTable
     * 用于从数据库表信息构建代码生成配置
     */
    @Mapping(source = "tableName", target = "businessName", qualifiedByName = "toCamelCase")
    @Mapping(source = "tableName", target = "className", qualifiedByName = "camelCaseToUpperFirst")
    CodegenTable convert(DatabaseTableMetadata bean);
    

    
    /**
     * 辅助方法：将下划线命名转换为驼峰命名
     */
    @Named("toCamelCase")
    default String toCamelCase(String str) {
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
    @Named("upperFirst")
    default String upperFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * 将字段元数据列表转换为CodegenColumn列表
     */
    default List<CodegenColumn> convertList(List<?> list) {
        return Collections.emptyList();
    }
    
    /**
     * 将CodegenTableRequest转换为CodegenTable
     */
    @Mapping(target = "scene", expression = "java(parseScene(request.getScene()))")
    CodegenTable toCodegenTable(CodegenTableRequest request);
    
    /**
     * 辅助方法：解析场景类型
     * @param sceneStr 场景字符串值
     * @return 转换后的Integer值，无效输入返回null
     */
    default Integer parseScene(String sceneStr) {
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
     * 将CodegenColumnRequest转换为CodegenColumn
     */
    @Mapping(source = "tableId", target = "tableId")
    CodegenColumn toCodegenColumn(CodegenColumnRequest request, Long tableId);
    
    /**
     * 将CodegenColumnRequest列表转换为CodegenColumn列表
     */
    default List<CodegenColumn> toCodegenColumnList(List<CodegenColumnRequest> requests, Long tableId) {
        if (requests == null) {
            return Collections.emptyList();
        }
        List<CodegenColumn> result = new ArrayList<>(requests.size());
        for (CodegenColumnRequest request : requests) {
            CodegenColumn column = toCodegenColumn(request, tableId);
            if (column != null) {
                result.add(column);
            }
        }
        return result;
    }
    
    /**
     * 辅助方法：驼峰命名转首字母大写
     */
    @Named("camelCaseToUpperFirst")
    default String camelCaseToUpperFirst(String str) {
        return upperFirst(toCamelCase(str));
    }
    
    /**
     * 根据数据库类型获取对应的Java类型
     */
    @Named("getJavaType")
    default String getJavaType(String columnType) {
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
    CodegenDetailResponse convertToDetail(CodegenTable table, List<CodegenColumn> columns);
    

    

}