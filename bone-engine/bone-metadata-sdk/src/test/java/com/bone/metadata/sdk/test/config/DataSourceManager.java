package com.bone.metadata.sdk.test.config;

import com.bone.metadata.sdk.support.dataSource.DynamicDataSource;
import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 数据源管理器 - 用于测试的辅助类
 */
@Component
public class DataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DataSourceManager.class);
    
    @Autowired(required = false)
    private DynamicDataSource dynamicDataSource;
    
    /**
     * 切换到指定数据源
     * @param dataSourceName 数据源名称
     */
    public void switchDataSource(String dataSourceName) {
        if (dataSourceName != null) {
            DataSourceContextHolder.setDataSource(dataSourceName);
            log.debug("Switched to datasource: {}", dataSourceName);
        }
    }
    
    /**
     * 切换回默认数据源
     */
    public void switchToDefault() {
        DataSourceContextHolder.clearDataSource();
        log.debug("Switched to default datasource");
    }
    
    /**
     * 获取当前数据源
     * @return 当前数据源实例
     */
    public DataSource getCurrentDataSource() {
        if (dynamicDataSource != null) {
            return dynamicDataSource.getCurrentDataSource();
        }
        return null;
    }
    
    /**
     * 执行特定数据源的操作
     * @param dataSourceName 数据源名称
     * @param action 要执行的操作
     */
    public void executeWithDataSource(String dataSourceName, Runnable action) {
        try {
            switchDataSource(dataSourceName);
            action.run();
        } finally {
            switchToDefault();
        }
    }
}