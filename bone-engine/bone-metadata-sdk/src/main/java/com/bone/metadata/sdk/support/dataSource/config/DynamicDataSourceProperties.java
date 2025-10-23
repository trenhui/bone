package com.bone.metadata.sdk.support.dataSource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动态数据源属性配置
 */
@ConfigurationProperties(prefix = "dynamic.datasource")
public class DynamicDataSourceProperties {
    
    /**
     * 是否启用动态数据源
     */
    private boolean enabled = false;
    
    /**
     * 默认数据源配置
     */
    private DataSourceProperties defaultDataSource = new DataSourceProperties();
    
    /**
     * 主库数据源配置
     */
    private DataSourceProperties master = new DataSourceProperties();
    
    /**
     * 从库数据源配置
     */
    private DataSourceProperties slave = new DataSourceProperties();
    
    /**
     * 自定义数据源配置
     */
    private Map<String, DataSourceProperties> datasources = new LinkedHashMap<>();
    
    /**
     * 获取默认数据源实例
     * @return 默认数据源
     */
    public DataSource getDefaultDataSource() {
        return buildDataSource(defaultDataSource);
    }
    
    /**
     * 获取主数据源实例
     * @return 主数据源
     */
    public DataSource getMaster() {
        return buildDataSource(master);
    }
    
    /**
     * 获取从数据源实例
     * @return 从数据源
     */
    public DataSource getSlave() {
        return buildDataSource(slave);
    }
    
    /**
     * 获取所有自定义数据源实例
     * @return 自定义数据源映射
     */
    public Map<Object, Object> getDatasources() {
        Map<Object, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, DataSourceProperties> entry : datasources.entrySet()) {
            DataSource dataSource = buildDataSource(entry.getValue());
            if (dataSource != null) {
                result.put(entry.getKey(), dataSource);
            }
        }
        return result;
    }
    
    /**
     * 构建数据源实例
     * @param properties 数据源配置
     * @return 数据源实例，如果配置不完整则返回null
     */
    private DataSource buildDataSource(DataSourceProperties properties) {
        if (properties == null || !StringUtils.hasText(properties.getUrl())) {
            return null;
        }
        
        return DataSourceBuilder.create()
                .url(properties.getUrl())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .driverClassName(properties.getDriverClassName())
                .build();
    }
    
    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public DataSourceProperties getDefaultDataSourceProperties() {
        return defaultDataSource;
    }
    
    public void setDefaultDataSource(DataSourceProperties defaultDataSource) {
        this.defaultDataSource = defaultDataSource;
    }
    
    public DataSourceProperties getMasterProperties() {
        return master;
    }
    
    public void setMaster(DataSourceProperties master) {
        this.master = master;
    }
    
    public DataSourceProperties getSlaveProperties() {
        return slave;
    }
    
    public void setSlave(DataSourceProperties slave) {
        this.slave = slave;
    }
    
    public void setDatasources(Map<String, DataSourceProperties> datasources) {
        this.datasources = datasources;
    }
    
    /**
     * 内部类：数据源配置属性
     */
    public static class DataSourceProperties {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private Map<String, String> hikari = new LinkedHashMap<>();
        
        // Getters and Setters
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
        
        public String getDriverClassName() {
            return driverClassName;
        }
        
        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }
        
        public Map<String, String> getHikari() {
            return hikari;
        }
        
        public void setHikari(Map<String, String> hikari) {
            this.hikari = hikari;
        }
    }
}