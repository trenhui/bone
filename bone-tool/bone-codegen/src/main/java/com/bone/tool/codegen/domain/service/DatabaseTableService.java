package com.bone.tool.codegen.domain.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.bone.tool.codegen.domain.entity.DataSourceConfig;
import com.bone.tool.codegen.domain.entity.TableField;
import com.bone.tool.codegen.domain.entity.TableInfo;
import com.bone.tool.codegen.domain.repository.DataSourceConfigRepository;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    /**
     * 获取数据库表列表
     * <p>
     * 基于表名称和表描述进行模糊匹配，从指定数据源获取表信息
     *
     * @param dataSourceConfigId 数据源配置ID
     * @param nameLike 表名称（模糊匹配）
     * @param commentLike 表描述（模糊匹配）
     * @return 表信息列表
     */
    public List<TableInfo> getTableList(Long dataSourceConfigId, String nameLike, String commentLike) {
        Assert.notNull(dataSourceConfigId, "数据源配置ID不能为空");
        
        List<TableInfo> tableInfoList = getTableList0(dataSourceConfigId, null);
        
        // 根据条件过滤
        if (StrUtil.isNotEmpty(nameLike)) {
            tableInfoList = tableInfoList.stream()
                    .filter(table -> table.getName().toLowerCase().contains(nameLike.toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        if (StrUtil.isNotEmpty(commentLike)) {
            tableInfoList = tableInfoList.stream()
                    .filter(table -> StrUtil.isNotEmpty(table.getComment()) && 
                            table.getComment().toLowerCase().contains(commentLike.toLowerCase()))
                    .collect(Collectors.toList());
        }
        
        // 按表名排序
        tableInfoList.sort(Comparator.comparing(TableInfo::getName));
        
        return tableInfoList;
    }
    
    /**
     * 获取数据库表列表（简化版）
     * <p>
     * 根据数据源配置ID获取所有表信息
     *
     * @param dataSourceConfigId 数据源配置ID
     * @return 表信息列表
     */
    public List<TableInfo> getTableList(Long dataSourceConfigId) {
        return getTableList(dataSourceConfigId, null, null);
    }
    
    /**
     * 批量获取指定表信息
     * <p>
     * 支持自定义选择多个表获取详细信息
     *
     * @param dataSourceConfigId 数据源配置ID
     * @param tableNames 表名列表
     * @return 表信息列表
     */
    public List<TableInfo> getTables(Long dataSourceConfigId, List<String> tableNames) {
        Assert.notNull(dataSourceConfigId, "数据源配置ID不能为空");
        if (CollUtil.isEmpty(tableNames)) {
            return new ArrayList<>();
        }
        
        List<TableInfo> tableInfos = new ArrayList<>(tableNames.size());
        for (String tableName : tableNames) {
            TableInfo tableInfo = getTable(dataSourceConfigId, tableName);
            if (tableInfo != null) {
                tableInfos.add(tableInfo);
            }
        }
        
        return tableInfos;
    }

    /**
     * 获取指定数据库表信息
     *
     * @param dataSourceConfigId 数据源配置ID
     * @param tableName 表名称
     * @return 表信息
     */
    public TableInfo getTable(Long dataSourceConfigId, String tableName) {
        Assert.notNull(dataSourceConfigId, "数据源配置ID不能为空");
        Assert.notEmpty(tableName, "表名称不能为空");
        
        List<TableInfo> tableInfoList = getTableList0(dataSourceConfigId, tableName);
        return CollUtil.isEmpty(tableInfoList) ? null : tableInfoList.get(0);
    }

    /**
     * 获取表信息列表的内部实现方法
     * 负责通过JDBC连接数据库并查询元数据获取表信息
     *
     * @param dataSourceConfigId 数据源配置ID
     * @param name 指定表名，为null时查询所有表
     * @return 表信息列表
     */
    private List<TableInfo> getTableList0(Long dataSourceConfigId, String name) {
        // 获取数据源配置
        DataSourceConfig dataSourceConfig = dataSourceConfigRepository.findById(dataSourceConfigId);
        Assert.notNull(dataSourceConfig, "数据源配置不存在");
        
        Connection conn = null;
        List<TableInfo> tableInfoList = new ArrayList<>();
        
        try {
            // 建立数据库连接
            conn = getConnection(dataSourceConfig);
            DatabaseMetaData metaData = conn.getMetaData();
            
            // 获取数据库类型
            String databaseProductName = metaData.getDatabaseProductName().toLowerCase();
            
            // 获取表名模式
            String schemaPattern = getSchemaPattern(conn, databaseProductName, dataSourceConfig);
            String catalogPattern = databaseProductName.contains("oracle") ? null : getCatalog(conn);
            
            // 查询表信息
            try (ResultSet rs = metaData.getTables(
                    catalogPattern, 
                    schemaPattern, 
                    name, 
                    new String[]{"TABLE"})) {
                
                while (rs.next()) {
                    TableInfo tableInfo = new TableInfo();
                    tableInfo.setName(rs.getString("TABLE_NAME"));
                    tableInfo.setComment(rs.getString("REMARKS"));
                    
                    // 获取表的字段信息
                    List<TableField> fields = getTableFields(conn, metaData, catalogPattern, schemaPattern, tableInfo.getName());
                    tableInfo.setFields(fields);
                    
                    // 设置实体类名和字段名（可根据需要自定义转换规则）
                    tableInfo.setEntityName(convertToClassName(tableInfo.getName()));
                    tableInfo.setFieldName(convertToFieldName(tableInfo.getName()));
                    
                    tableInfoList.add(tableInfo);
                }
            }
            
        } catch (Exception e) {
            log.error("获取数据库表信息失败", e);
            throw new RuntimeException("Error getting database tables", e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    log.error("关闭数据库连接失败", e);
                }
            }
        }
        
        return tableInfoList;
    }
    
    /**
     * 获取数据库连接
     */
    private Connection getConnection(DataSourceConfig dataSourceConfig) throws SQLException {
        return DriverManager.getConnection(
                dataSourceConfig.getUrl(),
                dataSourceConfig.getUsername(),
                dataSourceConfig.getPassword()
        );
    }
    
    /**
     * 获取表的字段信息
     */
    private List<TableField> getTableFields(Connection conn, DatabaseMetaData metaData, 
                                           String catalog, String schema, String tableName) throws SQLException {
        List<TableField> fields = new ArrayList<>();
        
        // 获取主键信息
        List<String> primaryKeys = getPrimaryKeys(metaData, catalog, schema, tableName);
        
        // 获取字段信息
        try (ResultSet rs = metaData.getColumns(catalog, schema, tableName, null)) {
            while (rs.next()) {
                TableField field = new TableField();
                field.setName(rs.getString("COLUMN_NAME"));
                field.setType(rs.getString("TYPE_NAME"));
                field.setComment(rs.getString("REMARKS"));
                field.setPrimaryKey(primaryKeys.contains(field.getName()));
                field.setPropertyName(convertToFieldName(field.getName()));
                field.setFill(isAutoFillField(field.getName()));
                
                fields.add(field);
            }
        }
        
        return fields;
    }
    
    /**
     * 获取表的主键字段列表
     */
    private List<String> getPrimaryKeys(DatabaseMetaData metaData, 
                                       String catalog, String schema, String tableName) throws SQLException {
        List<String> primaryKeys = new ArrayList<>();
        
        try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
        }
        
        return primaryKeys;
    }
    
    /**
     * 获取数据库schema模式
     */
    private String getSchemaPattern(Connection conn, String databaseProductName, DataSourceConfig dataSourceConfig) throws SQLException {
        if (databaseProductName.contains("mysql") || databaseProductName.contains("postgresql")) {
            return null;
        } else if (databaseProductName.contains("oracle")) {
            return dataSourceConfig.getUsername().toUpperCase();
        } else {
            return conn.getSchema();
        }
    }
    
    /**
     * 获取数据库catalog
     */
    private String getCatalog(Connection conn) throws SQLException {
        String url = conn.getMetaData().getURL();
        if (url.contains("mysql")) {
            // 从URL中提取数据库名
            int startIndex = url.lastIndexOf("/") + 1;
            int endIndex = url.contains("?") ? url.indexOf("?") : url.length();
            if (startIndex < endIndex) {
                return url.substring(startIndex, endIndex);
            }
        }
        return conn.getCatalog();
    }
    
    /**
     * 将表名转换为类名（驼峰命名，首字母大写）
     */
    private String convertToClassName(String tableName) {
        if (StrUtil.isBlank(tableName)) {
            return tableName;
        }
        
        // 移除前缀
        tableName = removeTablePrefix(tableName);
        
        StringBuilder result = new StringBuilder();
        boolean nextUpperCase = true;
        
        for (char c : tableName.toCharArray()) {
            if (c == '_' || c == '-') {
                nextUpperCase = true;
            } else {
                result.append(nextUpperCase ? Character.toUpperCase(c) : Character.toLowerCase(c));
                nextUpperCase = false;
            }
        }
        
        return result.toString();
    }
    
    /**
     * 将表名/字段名转换为Java字段名（驼峰命名，首字母小写）
     */
    private String convertToFieldName(String name) {
        String className = convertToClassName(name);
        if (StrUtil.isBlank(className)) {
            return className;
        }
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }
    
    /**
     * 移除表名前缀
     */
    private String removeTablePrefix(String tableName) {
        // 可以配置需要移除的表名前缀
        String[] prefixes = {"t_", "sys_", "tb_"};
        for (String prefix : prefixes) {
            if (tableName.toLowerCase().startsWith(prefix)) {
                return tableName.substring(prefix.length());
            }
        }
        return tableName;
    }
    
    /**
     * 判断是否为自动填充字段
     */
    private boolean isAutoFillField(String fieldName) {
        String lowerFieldName = fieldName.toLowerCase();
        return lowerFieldName.contains("create_time") || 
               lowerFieldName.contains("update_time") || 
               lowerFieldName.contains("create_by") || 
               lowerFieldName.contains("update_by");
    }
}
