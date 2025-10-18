package com.bone.tool.codegen.domain.repository;

import com.bone.tool.codegen.domain.entity.DatabaseTableMetadata;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.repository.DataSourceConfigRepository;
import com.bone.tool.codegen.domain.entity.Datasource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 数据库表仓库默认实现
 * 提供数据库表信息查询的具体实现
 * 
 * @author bone-team
 */
@Repository
public class DefaultDatabaseTableRepository implements DatabaseTableRepository {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultDatabaseTableRepository.class);
    
    @Resource
    private DataSourceConfigRepository dataSourceConfigRepository;
    
    @Override
    public Connection getConnection(Long datasourceId) throws Exception {
        Datasource config = dataSourceConfigRepository.findById(datasourceId);
        if (config == null) {
            throw new IllegalArgumentException("数据源配置不存在: " + datasourceId);
        }
        
        // 获取数据库连接
        Connection connection = DriverManager.getConnection(
                config.getUrl(), 
                config.getUsername(), 
                config.getPassword());
        
        // 设置事务隔离级别（可选）
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        
        return connection;
    }
    
    @Override
    public DatabaseMetaData getDatabaseMetaData(Connection connection) throws Exception {
        return connection.getMetaData();
    }
    
    @Override
    public List<DatabaseTableMetadata> getTableList(Long dataSourceConfigId, String schema) throws Exception {
        Connection connection = null;
        try {
            connection = getConnection(dataSourceConfigId);
            DatabaseMetaData metaData = getDatabaseMetaData(connection);
            
            // 获取当前用户作为默认schema
            if (!StringUtils.hasText(schema)) {
                schema = connection.getSchema();
                if (!StringUtils.hasText(schema)) {
                    schema = connection.getCatalog();
                }
            }
            
            // 查询表信息
            List<DatabaseTableMetadata> tableInfos = new ArrayList<>();
            
            // 处理不同数据库的表信息查询
            try (ResultSet rs = metaData.getTables(null, schema, null, new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String tableComment = rs.getString("REMARKS");
                    
                    DatabaseTableMetadata tableInfo = new DatabaseTableMetadata();
                    tableInfo.setTableName(tableName);
                    tableInfo.setTableComment(tableComment);
                    tableInfo.setEntityName(convertToEntityName(tableName));
                    tableInfo.setFieldName(convertToFieldName(tableName));
                    
                    // 获取表字段信息
                    tableInfo.setFieldList(getTableColumns(connection, schema, tableName));
                    
                    tableInfos.add(tableInfo);
                }
            }
            
            return tableInfos;
        } finally {
            closeConnection(connection);
        }
    }
    
    @Override
    public DatabaseTableMetadata getTableInfo(Long dataSourceConfigId, String tableName) throws Exception {
        Connection connection = null;
        try {
            connection = getConnection(dataSourceConfigId);
            DatabaseMetaData metaData = getDatabaseMetaData(connection);
            
            // 获取当前用户作为默认schema
            String schema = connection.getSchema();
            if (!StringUtils.hasText(schema)) {
                schema = connection.getCatalog();
            }
            
            // 查询指定表信息
            try (ResultSet rs = metaData.getTables(null, schema, tableName, new String[]{"TABLE"})) {
                if (rs.next()) {
                    String tableComment = rs.getString("REMARKS");
                    
                    DatabaseTableMetadata tableInfo = new DatabaseTableMetadata();
                    tableInfo.setTableName(tableName);
                    tableInfo.setTableComment(tableComment);
                    tableInfo.setEntityName(convertToEntityName(tableName));
                    tableInfo.setFieldName(convertToFieldName(tableName));
                    
                    // 获取表字段信息
                    tableInfo.setFieldList(getTableColumns(connection, schema, tableName));
                    
                    return tableInfo;
                }
            }
            
            return null; // 表不存在
        } finally {
            closeConnection(connection);
        }
    }
    
    @Override
    public void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.warn("关闭数据库连接失败", e);
            }
        }
    }
    
    /**
     * 获取表字段信息
     */
    private List<CodegenColumn> getTableColumns(Connection connection, String schema, String tableName) throws Exception {
        DatabaseMetaData metaData = connection.getMetaData();
        List<CodegenColumn> columns = new ArrayList<>();
        
        // 获取主键信息
        ResultSet primaryKeys = null;
        try {
            primaryKeys = metaData.getPrimaryKeys(null, schema, tableName);
            List<String> pkColumns = new ArrayList<>();
            while (primaryKeys.next()) {
                pkColumns.add(primaryKeys.getString("COLUMN_NAME"));
            }
            
            // 获取列信息
            try (ResultSet columnsRs = metaData.getColumns(null, schema, tableName, null)) {
                while (columnsRs.next()) {
                    CodegenColumn column = new CodegenColumn();
                    String columnName = columnsRs.getString("COLUMN_NAME");
                    String dataType = columnsRs.getString("TYPE_NAME");
                    String columnComment = columnsRs.getString("REMARKS");
                    
                    column.setColumnName(columnName);
                    column.setDataType(dataType);
                    column.setColumnComment(columnComment);
                    column.setPrimaryKey(pkColumns.contains(columnName));
                    column.setJavaField(convertToJavaField(columnName));
                    column.setJavaType(convertToJavaType(dataType, column.getPrimaryKey()));
                    
                    columns.add(column);
                }
            }
        } finally {
            if (primaryKeys != null) {
                try {
                    primaryKeys.close();
                } catch (SQLException e) {
                    log.warn("关闭主键结果集失败", e);
                }
            }
        }
        
        return columns;
    }
    
    /**
     * 转换表名为实体名（驼峰命名，首字母大写）
     */
    private String convertToEntityName(String tableName) {
        if (tableName == null) {
            return null;
        }
        String fieldName = convertToFieldName(tableName);
        return fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }
    
    /**
     * 转换表名为字段名（驼峰命名，首字母小写）
     */
    private String convertToFieldName(String tableName) {
        if (tableName == null) {
            return null;
        }
        
        // 去除表名前缀（如t_、sys_等）
        String name = tableName;
        if (name.contains("_")) {
            StringBuilder fieldName = new StringBuilder();
            String[] parts = name.split("_");
            for (int i = 0; i < parts.length; i++) {
                if (i == 0) {
                    fieldName.append(parts[i].toLowerCase());
                } else {
                    fieldName.append(parts[i].substring(0, 1).toUpperCase())
                            .append(parts[i].substring(1).toLowerCase());
                }
            }
            return fieldName.toString();
        }
        
        return name.toLowerCase();
    }
    
    /**
     * 转换列名为Java字段名（驼峰命名，首字母小写）
     */
    private String convertToJavaField(String columnName) {
        return convertToFieldName(columnName);
    }
    
    /**
     * 转换数据库类型为Java类型
     */
    private String convertToJavaType(String dataType, boolean isPrimaryKey) {
        if (dataType == null) {
            return "String";
        }
        
        String type = dataType.toLowerCase(Locale.ROOT);
        
        // 数字类型映射
        if (type.contains("int") || type.contains("tinyint") || type.contains("smallint")) {
            return isPrimaryKey ? "Long" : "Integer";
        } else if (type.contains("bigint")) {
            return "Long";
        } else if (type.contains("float") || type.contains("double") || 
                 type.contains("decimal") || type.contains("numeric")) {
            return "BigDecimal";
        } 
        // 日期时间类型映射
        else if (type.contains("date")) {
            return "LocalDate";
        } else if (type.contains("time")) {
            return "LocalTime";
        } else if (type.contains("datetime") || type.contains("timestamp")) {
            return "LocalDateTime";
        }
        // 布尔类型映射
        else if (type.contains("bool") || type.contains("bit")) {
            return "Boolean";
        }
        // 其他类型默认为String
        return "String";
    }
}