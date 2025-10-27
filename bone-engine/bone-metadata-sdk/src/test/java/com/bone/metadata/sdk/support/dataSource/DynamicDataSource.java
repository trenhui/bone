package com.bone.metadata.sdk.support.dataSource;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 简化的动态数据源实现
 * 根据上下文选择不同的数据源
 */
public class DynamicDataSource implements DataSource {
    
    /**
     * 默认数据源
     */
    private DataSource defaultDataSource;
    
    /**
     * 目标数据源映射
     */
    private Map<Object, Object> targetDataSources;
    
    /**
     * 设置默认数据源
     */
    public void setDefaultTargetDataSource(DataSource defaultDataSource) {
        this.defaultDataSource = defaultDataSource;
    }
    
    /**
     * 设置目标数据源映射
     */
    public void setTargetDataSources(Map<Object, Object> targetDataSources) {
        this.targetDataSources = targetDataSources;
    }
    
    /**
     * 获取当前数据源
     */
    protected DataSource determineCurrentDataSource() {
        String dataSourceKey = DataSourceContextHolder.getCurrentLookupKey();
        
        if (dataSourceKey != null && targetDataSources != null) {
            DataSource dataSource = (DataSource) targetDataSources.get(dataSourceKey);
            if (dataSource != null) {
                System.out.println("使用数据源: " + dataSourceKey);
                return dataSource;
            }
        }
        
        System.out.println("使用默认数据源");
        return defaultDataSource;
    }
    
    @Override
    public Connection getConnection() throws SQLException {
        return determineCurrentDataSource().getConnection();
    }
    
    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return determineCurrentDataSource().getConnection(username, password);
    }
    
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return determineCurrentDataSource().unwrap(iface);
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return determineCurrentDataSource().isWrapperFor(iface);
    }
    
    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return determineCurrentDataSource().getLogWriter();
    }
    
    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        determineCurrentDataSource().setLogWriter(out);
    }
    
    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        determineCurrentDataSource().setLoginTimeout(seconds);
    }
    
    @Override
    public int getLoginTimeout() throws SQLException {
        return determineCurrentDataSource().getLoginTimeout();
    }
    
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return determineCurrentDataSource().getParentLogger();
    }
}