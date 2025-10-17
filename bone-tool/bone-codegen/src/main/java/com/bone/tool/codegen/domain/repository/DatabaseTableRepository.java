package com.bone.tool.codegen.domain.repository;

import com.bone.tool.codegen.domain.entity.TableInfo;
import java.util.List;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * 数据库表仓库接口
 * 提供数据库表信息查询的抽象层
 * 
 * @author bone-team
 */
public interface DatabaseTableRepository {
    
    /**
     * 获取数据库连接
     * @param dataSourceConfigId 数据源配置ID
     * @return 数据库连接
     * @throws Exception 连接失败时抛出异常
     */
    Connection getConnection(Long dataSourceConfigId) throws Exception;
    
    /**
     * 获取数据库元数据
     * @param connection 数据库连接
     * @return 数据库元数据
     * @throws Exception 获取元数据失败时抛出异常
     */
    DatabaseMetaData getDatabaseMetaData(Connection connection) throws Exception;
    
    /**
     * 获取数据库表列表
     * @param dataSourceConfigId 数据源配置ID
     * @param schema 数据库模式
     * @return 表信息列表
     * @throws Exception 查询失败时抛出异常
     */
    List<TableInfo> getTableList(Long dataSourceConfigId, String schema) throws Exception;
    
    /**
     * 获取数据库表信息
     * @param dataSourceConfigId 数据源配置ID
     * @param tableName 表名
     * @return 表信息
     * @throws Exception 查询失败时抛出异常
     */
    TableInfo getTableInfo(Long dataSourceConfigId, String tableName) throws Exception;
    
    /**
     * 关闭数据库连接
     * @param connection 数据库连接
     */
    void closeConnection(Connection connection);
}