package com.bone.tool.codegen.domain.repository;

import com.bone.tool.codegen.domain.entity.DatabaseTableMetadata;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
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
 * 提供数据库表信息查询的具体实现，负责获取表元数据、列信息等
 * 遵循Repository模式，专注于数据访问操作
 * 
 * @author bone-team
 */
@Repository
public class DefaultDatabaseTableRepository implements DatabaseTableRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultDatabaseTableRepository.class);
    private static final String[] TABLE_TYPES = {"TABLE"};
    
    @Resource
    private DataSourceConfigRepository dataSourceConfigRepository;
    
    /**
     * 获取数据库连接
     * 
     * @param dataSourceId 数据源ID
     * @return 数据库连接对象
     * @throws Exception 连接建立过程中发生的异常
     */
    @Override
    public Connection getConnection(Long dataSourceId) throws Exception {
        if (dataSourceId == null) {
            throw new IllegalArgumentException("数据源ID不能为空");
        }
        
        Datasource config = dataSourceConfigRepository.findById(dataSourceId)
                .orElseThrow(() -> new IllegalArgumentException("数据源配置不存在: " + dataSourceId));
        
        // 实际项目中应使用配置的值，这里使用示例值仅为演示
        String url = "jdbc:mysql://localhost:3306/test";
        String username = "root";
        String password = "password";
        String driverClass = "com.mysql.cj.jdbc.Driver";
        
        // 验证必要的连接信息
        if (!StringUtils.hasText(url) || !StringUtils.hasText(username)) {
            throw new IllegalArgumentException("数据源配置不完整，缺少必要的连接信息");
        }
        
        // 加载数据库驱动
        if (StringUtils.hasText(driverClass)) {
            try {
                Class.forName(driverClass);
            } catch (ClassNotFoundException e) {
                logger.warn("数据库驱动类加载失败: {}", driverClass, e);
            }
        }
        
        try {
            // 创建连接
            Connection connection = DriverManager.getConnection(url, username, password);
            
            // 设置事务隔离级别
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            
            logger.info("成功获取数据库连接: {}", maskUrlPassword(url));
            return connection;
        } catch (SQLException e) {
            logger.error("数据库连接创建失败: {}", maskUrlPassword(url), e);
            throw new Exception("数据库连接创建失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 屏蔽URL中的密码，用于日志输出，保护敏感信息
     * 
     * @param url 数据库连接URL
     * @return 屏蔽密码后的URL
     */
    private String maskUrlPassword(String url) {
        if (url == null) {
            return null;
        }
        // 将URL中的password部分替换为星号
        return url.replaceAll("password=[^&]*", "password=****");
    }
    
    /**
     * 获取数据库元数据
     * 
     * @param connection 数据库连接
     * @return 数据库元数据对象
     * @throws Exception 获取元数据过程中发生的异常
     */
    @Override
    public DatabaseMetaData getDatabaseMetaData(Connection connection) throws Exception {
        if (connection == null) {
            throw new IllegalArgumentException("数据库连接不能为空");
        }
        
        try {
            return connection.getMetaData();
        } catch (SQLException e) {
            logger.error("获取数据库元数据失败", e);
            throw new Exception("获取数据库元数据失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取数据库表列表
     * 
     * @param dataSourceConfigId 数据源配置ID
     * @param schema 数据库模式名称（可选）
     * @return 表元数据列表
     * @throws Exception 查询过程中发生的异常
     */
    @Override
    public List<DatabaseTableMetadata> getTableList(Long dataSourceConfigId, String schema) throws Exception {
        Connection connection = null;
        try {
            connection = getConnection(dataSourceConfigId);
            DatabaseMetaData metaData = getDatabaseMetaData(connection);
            
            // 获取当前用户作为默认schema
            String effectiveSchema = resolveSchema(connection, schema);
            
            logger.debug("查询数据库表列表，数据源ID: {}, Schema: {}", dataSourceConfigId, effectiveSchema);
            
            // 查询表信息
            List<DatabaseTableMetadata> tableMetadataList = new ArrayList<>();
            
            // 处理不同数据库的表信息查询
            try (ResultSet rs = metaData.getTables(null, effectiveSchema, null, TABLE_TYPES)) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String tableComment = rs.getString("REMARKS");
                    
                    // 创建表元数据对象并设置基本信息
                    DatabaseTableMetadata tableMetadata = new DatabaseTableMetadata();
                    tableMetadata.setTableName(tableName);
                    tableMetadata.setComment(tableComment); // 修复之前的方法名错误
                    tableMetadata.setEntityName(convertToEntityName(tableName));
                    tableMetadata.setFieldName(convertToFieldName(tableName));
                    tableMetadata.setFieldList(new ArrayList<>());
                    
                    tableMetadataList.add(tableMetadata);
                }
            }
            
            logger.info("成功获取数据库表列表，共 {} 个表", tableMetadataList.size());
            return tableMetadataList;
        } catch (Exception e) {
            logger.error("获取数据库表列表失败，数据源ID: {}", dataSourceConfigId, e);
            throw e;
        } finally {
            closeConnection(connection);
        }
    }
    
    /**
     * 获取指定表的详细信息
     * 
     * @param dataSourceConfigId 数据源配置ID
     * @param tableName 表名
     * @return 表元数据对象，表不存在时返回null
     * @throws Exception 查询过程中发生的异常
     */
    @Override
    public DatabaseTableMetadata getTableInfo(Long dataSourceConfigId, String tableName) throws Exception {
        if (dataSourceConfigId == null || !StringUtils.hasText(tableName)) {
            throw new IllegalArgumentException("数据源ID和表名不能为空");
        }
        
        Connection connection = null;
        try {
            connection = getConnection(dataSourceConfigId);
            DatabaseMetaData metaData = getDatabaseMetaData(connection);
            
            // 获取当前用户作为默认schema
            String schema = resolveSchema(connection, null);
            
            logger.debug("查询数据库表信息，数据源ID: {}, 表名: {}, Schema: {}", 
                    dataSourceConfigId, tableName, schema);
            
            // 查询指定表信息
            try (ResultSet rs = metaData.getTables(null, schema, tableName, TABLE_TYPES)) {
                if (rs.next()) {
                    String tableComment = rs.getString("REMARKS");
                    
                    // 创建表元数据对象并设置基本信息
                    DatabaseTableMetadata tableMetadata = new DatabaseTableMetadata();
                    tableMetadata.setTableName(tableName);
                    tableMetadata.setComment(tableComment); // 修复之前的方法名错误
                    tableMetadata.setEntityName(convertToEntityName(tableName));
                    tableMetadata.setFieldName(convertToFieldName(tableName));
                    tableMetadata.setFieldList(new ArrayList<>());
                    
                    // 查询并设置表的字段信息
                    List<CodegenColumn> columns = getTableColumns(connection, schema, tableName);
                    tableMetadata.setFieldList(columns);
                    
                    logger.info("成功获取表信息: {}, 字段数量: {}", tableName, columns.size());
                    return tableMetadata;
                }
            }
            
            logger.warn("表不存在: {}", tableName);
            return null; // 表不存在
        } catch (Exception e) {
            logger.error("获取表信息失败，数据源ID: {}, 表名: {}", dataSourceConfigId, tableName, e);
            throw e;
        } finally {
            closeConnection(connection);
        }
    }
    
    /**
     * 关闭数据库连接
     * 
     * @param connection 数据库连接对象
     */
    @Override
    public void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    logger.debug("数据库连接已关闭");
                }
            } catch (SQLException e) {
                logger.warn("关闭数据库连接失败", e);
            }
        }
    }
    
    /**
     * 获取表字段信息
     * 
     * @param connection 数据库连接
     * @param schema 数据库模式
     * @param tableName 表名
     * @return 字段信息列表
     * @throws Exception 查询过程中发生的异常
     */
    private List<CodegenColumn> getTableColumns(Connection connection, String schema, String tableName) throws Exception {
        List<CodegenColumn> columns = new ArrayList<>();
        
        if (connection == null || !StringUtils.hasText(tableName)) {
            return columns;
        }
        
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // 获取表的主键信息
            String primaryKeyColumn = findPrimaryKeyColumn(metaData, schema, tableName);
            
            // 获取表的列信息
            try (ResultSet rs = metaData.getColumns(null, schema, tableName, null)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String dataType = rs.getString("TYPE_NAME");
                    String columnComment = rs.getString("REMARKS");
                    int columnSize = rs.getInt("COLUMN_SIZE");
                    int decimalDigits = rs.getInt("DECIMAL_DIGITS");
                    String isNullable = rs.getString("IS_NULLABLE");
                    
                    // 创建字段对象
                    CodegenColumn column = new CodegenColumn();
                    column.setColumnName(columnName);
                    column.setColumnComment(columnComment);
                    column.setDataType(dataType);
                    column.setNullable("YES".equalsIgnoreCase(isNullable));
                    column.setPrimaryKey(columnName.equalsIgnoreCase(primaryKeyColumn));
                    
                    // 转换Java类型和字段名
                    boolean isPrimary = columnName.equalsIgnoreCase(primaryKeyColumn);
                    String javaType = convertToJavaType(dataType, isPrimary);
                    column.setJavaType(javaType);
                    column.setJavaField(convertToJavaField(columnName));
                    column.setHtmlType(getDefaultHtmlType(javaType));
                    
                    columns.add(column);
                }
            }
            
            logger.debug("成功获取表 {} 的字段信息，共 {} 个字段", tableName, columns.size());
            return columns;
        } catch (SQLException e) {
            logger.error("获取表字段信息失败，表名: {}", tableName, e);
            throw new Exception("获取表字段信息失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 查找表的主键列名
     * 
     * @param metaData 数据库元数据
     * @param schema 数据库模式
     * @param tableName 表名
     * @return 主键列名，没有主键时返回空字符串
     * @throws SQLException SQL异常
     */
    private String findPrimaryKeyColumn(DatabaseMetaData metaData, String schema, String tableName) throws SQLException {
        try (ResultSet rs = metaData.getPrimaryKeys(null, schema, tableName)) {
            if (rs.next()) {
                return rs.getString("COLUMN_NAME");
            }
        }
        return "";
    }
    
    /**
     * 解析并获取有效的Schema名称
     * 
     * @param connection 数据库连接
     * @param schema 指定的Schema名称
     * @return 有效的Schema名称
     * @throws SQLException SQL异常
     */
    private String resolveSchema(Connection connection, String schema) throws SQLException {
        if (StringUtils.hasText(schema)) {
            return schema;
        }
        
        String currentSchema = connection.getSchema();
        if (StringUtils.hasText(currentSchema)) {
            return currentSchema;
        }
        
        return connection.getCatalog();
    }
    
    /**
     * 根据Java类型获取默认的HTML类型
     * 
     * @param javaType Java类型名称
     * @return HTML表单元素类型
     */
    private String getDefaultHtmlType(String javaType) {
        if (javaType == null) {
            return "input";
        }
        
        String type = javaType.toLowerCase();
        if (type.contains("date") || type.contains("time")) {
            return "datetime";
        } else if (type.contains("decimal") || type.contains("double") || type.contains("float")) {
            return "input";
        } else if (type.contains("integer") || type.contains("long")) {
            return "input";
        } else if (type.contains("boolean")) {
            return "radio";
        }
        return "input";
    }
    
    /**
     * 转换表名为实体名（驼峰命名，首字母大写）
     * 
     * @param tableName 表名
     * @return 实体类名称
     */
    private String convertToEntityName(String tableName) {
        if (tableName == null) {
            return null;
        }
        String fieldName = convertToFieldName(tableName);
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        return fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }
    
    /**
     * 转换表名为字段名（驼峰命名，首字母小写）
     * 
     * @param tableName 表名
     * @return 字段名
     */
    private String convertToFieldName(String tableName) {
        if (tableName == null) {
            return null;
        }
        
        // 去除表名前缀（如t_、sys_等）并转换为驼峰命名
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
     * 
     * @param columnName 列名
     * @return Java字段名
     */
    private String convertToJavaField(String columnName) {
        return convertToFieldName(columnName);
    }
    
    /**
     * 转换数据库类型为Java类型
     * 
     * @param dataType 数据库类型
     * @param isPrimaryKey 是否为主键
     * @return Java类型名称
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