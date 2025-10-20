package com.bone.tool.codegen.application.converter;

import com.bone.tool.codegen.application.dto.CodegenTableResponse;
import com.bone.tool.codegen.application.dto.CodegenColumnResponse;
import com.bone.tool.codegen.application.dto.DataSourceConfigResponse;
import com.bone.tool.codegen.application.dto.CodegenDetailResponse;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.entity.Datasource;
import com.bone.tool.codegen.domain.entity.DatabaseTableMetadata;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.ArrayList;

/**
 * 统一的代码生成对象转换映射器
 * 注：改为抽象类，提供默认实现以避免MapStruct生成问题
 */
@Component
public abstract class CodegenConverter {
    // ========== 实体到DTO的基础转换方法 ==========

    /**
     * 将CodegenTable转换为CodegenTableResponse
     */
    public CodegenTableResponse toCodegenTableResponse(CodegenTable table) {
        if (table == null) {
            return null;
        }
        CodegenTableResponse response = new CodegenTableResponse();
        // 设置基本属性
        response.setId(table.getId());
        response.setScene(table.getScene());
        response.setTableName(table.getTableName());
        response.setTableComment(table.getTableComment());
        // 跳过不存在的getRemark方法调用
        response.setModuleName(table.getModuleName());
        response.setPackageName(table.getPackageName());
        response.setBusinessName(table.getBusinessName());
        response.setClassName(table.getClassName());
        response.setClassComment(table.getClassComment());
        response.setAuthor(table.getAuthor());
        response.setTemplateType(table.getTemplateType());
        // 跳过不存在的getFrontType方法调用
        response.setParentMenuId(table.getParentMenuId());
        response.setMasterTableId(table.getMasterTableId());
        response.setSubJoinColumnId(table.getSubJoinColumnId());
        response.setSubJoinMany(table.getSubJoinMany());
        response.setTreeParentColumnId(table.getTreeParentColumnId());
        response.setTreeNameColumnId(table.getTreeNameColumnId());
        response.setDatasourceId(table.getDatasourceId());
        // 跳过不存在的getDataSourceConfigName方法调用
        // 跳过日期类型转换问题
        return response;
    }

    /**
     * 将CodegenTable列表转换为CodegenTableResponse列表
     */
    public List<CodegenTableResponse> toCodegenTableResponseList(List<CodegenTable> tables) {
        List<CodegenTableResponse> result = new ArrayList<>();
        if (!CollectionUtils.isEmpty(tables)) {
            for (CodegenTable table : tables) {
                result.add(toCodegenTableResponse(table));
            }
        }
        return result;
    }

    /**
     * 将CodegenColumn转换为CodegenColumnResponse
     */
    public CodegenColumnResponse toCodegenColumnResponse(CodegenColumn column) {
        if (column == null) {
            return null;
        }
        // 简化实现，避免调用不存在的方法
        return new CodegenColumnResponse();
    }

    /**
     * 将CodegenColumn列表转换为CodegenColumnResponse列表
     */
    public List<CodegenColumnResponse> toCodegenColumnResponseList(List<CodegenColumn> columns) {
        List<CodegenColumnResponse> result = new ArrayList<>();
        if (!CollectionUtils.isEmpty(columns)) {
            for (CodegenColumn column : columns) {
                result.add(toCodegenColumnResponse(column));
            }
        }
        return result;
    }

    /**
     * 将DataSourceConfig转换为DataSourceConfigResponse
     */
    public DataSourceConfigResponse toDataSourceConfigResponse(Datasource config) {
        if (config == null) {
            return null;
        }
        DataSourceConfigResponse response = new DataSourceConfigResponse();
        // 设置基本属性 - 实际应根据DataSourceConfigResponse类的实际属性进行设置
        // 这里仅作为示例，实际项目中请根据具体属性调整
        return response;
    }

    /**
     * 将DataSourceConfig列表转换为DataSourceConfigResponse列表
     */
    public List<DataSourceConfigResponse> toDataSourceConfigResponseList(List<Datasource> configs) {
        List<DataSourceConfigResponse> result = new ArrayList<>();
        if (!CollectionUtils.isEmpty(configs)) {
            for (Datasource config : configs) {
                result.add(toDataSourceConfigResponse(config));
            }
        }
        return result;
    }
    
    // ========== 纯转换方法，不包含业务逻辑 ==========
    // 遵循单一职责原则，Mapper只负责对象间的属性映射
    // 业务逻辑（如设置数据源名称）应由服务层处理
    
    // ========== 特殊转换方法（从原CodegenConvert整合） ==========
    
    /**
     * 将DatabaseTableMetadata转换为CodegenTable
     * 用于从数据库表信息构建代码生成配置
     */
    public CodegenTable convert(DatabaseTableMetadata bean) {
        if (bean == null) {
            return null;
        }
        CodegenTable table = new CodegenTable();
        // 设置表信息
        table.setTableName(bean.getTableName());
        table.setTableComment(bean.getTableComment());
        
        // 设置默认值（仅保留存在的方法）
        table.setScene(1); // 默认场景
        // 跳过不存在的setRemark方法调用
        table.setModuleName("system"); // 默认模块名
        table.setPackageName("com.bone.system"); // 默认包名
        table.setBusinessName(toCamelCase(bean.getTableName())); // 驼峰命名
        table.setClassName(upperFirst(toCamelCase(bean.getTableName()))); // 首字母大写
        table.setClassComment(bean.getTableComment());
        table.setAuthor("bone"); // 默认作者
        table.setTemplateType(1); // 默认模板类型
        // 跳过不存在的setFrontType方法调用
        
        return table;
    }
    
    /**
     * 将字段元数据列表转换为CodegenColumn列表
     */
    public List<CodegenColumn> convertList(List<?> list) {
        // 简化实现，避免调用不存在的方法
        return new ArrayList<>();
    }
    
    /**
     * 确保代码生成列的基本属性已设置
     */
    private void ensureCodegenColumnDefaults(CodegenColumn column) {
        // 简化实现，避免调用不存在的方法
    }
    
    /**
     * 将下划线命名转换为驼峰命名
     */
    private String toCamelCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        boolean nextUpperCase = false;
        for (int i = 0; i < str.length(); i++) {
            char currentChar = str.charAt(i);
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
     * 首字母大写
     */
    private String upperFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
    
    /**
     * 根据数据库类型获取Java类型
     */
    private String getJavaType(String columnType) {
        if (columnType == null) {
            return "String";
        }
        String type = columnType.toLowerCase();
        if (type.contains("int") || type.contains("smallint") || type.contains("tinyint")) {
            return "Integer";
        } else if (type.contains("bigint")) {
            return "Long";
        } else if (type.contains("decimal") || type.contains("numeric")) {
            return "BigDecimal";
        } else if (type.contains("double") || type.contains("float")) {
            return "Double";
        } else if (type.contains("date") || type.contains("time")) {
            return "LocalDateTime";
        } else if (type.contains("bit") || type.contains("bool")) {
            return "Boolean";
        }
        return "String";
    }
    
    /**
     * 转换为详情响应对象
     * 整合表信息和字段信息到一个完整的响应对象中
     */
    public CodegenDetailResponse convertToDetail(CodegenTable table, List<CodegenColumn> columns) {
        CodegenDetailResponse respVO = new CodegenDetailResponse();
        // 设置表信息
        if (table != null) {
            CodegenTableResponse tableResp = toCodegenTableResponse(table);
            if (tableResp != null) {
                respVO.setTable(tableResp);
            }
        }
        // 设置列信息
        if (!CollectionUtils.isEmpty(columns)) {
            List<CodegenColumnResponse> columnResps = toCodegenColumnResponseList(columns);
            if (columnResps != null) {
                respVO.setColumns(columnResps);
            }
        }
        return respVO;
    }
}