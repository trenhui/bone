package com.bone.metadata.sdk.support.dataSource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
     * 是否严格匹配数据源，默认false
     * true: 未匹配到指定数据源时抛异常，false: 使用默认数据源
     */
    private boolean strict = false;
    
    /**
     * 默认数据源名称
     */
    private String primary = "master";
    
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
     * 缓存已创建的数据源实例，避免重复创建
     */
    private final Map<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();

    /**
     * 获取默认数据源实例
     * @return 默认数据源
     */
    public DataSource getDefaultDataSource() {
        return getDataSource("default", defaultDataSource);
    }
    
    /**
     * 获取主数据源实例
     * @return 主数据源
     */
    public DataSource getMaster() {
        return getDataSource("master", master);
    }

    /**
     * 获取从数据源实例
     * @return 从数据源
     */
    public DataSource getSlave() {
        return getDataSource("slave", slave);
    }
    
    /**
     * 获取所有自定义数据源实例
     * @return 自定义数据源映射
     */
    public Map<Object, Object> getDatasources() {
        Map<Object, Object> result = new LinkedHashMap<>();
        
        // 添加主从数据源
        DataSource masterDs = getMaster();
        DataSource slaveDs = getSlave();
        if (masterDs != null) {
            result.put("master", masterDs);
        }
        if (slaveDs != null) {
            result.put("slave", slaveDs);
        }
        
        // 添加自定义数据源
        for (Map.Entry<String, DataSourceProperties> entry : datasources.entrySet()) {
            String name = entry.getKey();
            DataSourceProperties properties = entry.getValue();
            DataSource dataSource = getDataSource(name, properties);
            if (dataSource != null) {
                result.put(name, dataSource);
            }
        }
        return result;
    }
    
    /**
     * 从缓存获取或创建数据源
     * @param name 数据源名称
     * @param properties 数据源配置
     * @return 数据源实例
     */
    private DataSource getDataSource(String name, DataSourceProperties properties) {
        return dataSourceCache.computeIfAbsent(name, key -> buildDataSource(properties));
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
        
        // 如果配置了JNDI名称，则使用JNDI查找
        if (StringUtils.hasText(properties.getJndiName())) {
            return createJndiDataSource(properties.getJndiName());
        }
        
        // 使用Spring Boot的DataSourceBuilder创建数据源
        DataSourceBuilder<?> builder = DataSourceBuilder.create();
        
        // 设置基础属性
        builder.url(properties.getUrl());
        
        if (StringUtils.hasText(properties.getUsername())) {
            builder.username(properties.getUsername());
        }
        
        if (StringUtils.hasText(properties.getPassword())) {
            builder.password(properties.getPassword());
        }
        
        if (StringUtils.hasText(properties.getDriverClassName())) {
            builder.driverClassName(properties.getDriverClassName());
        }
        
        // 如果配置了类型，则设置数据源类型
        if (StringUtils.hasText(properties.getType())) {
            try {
                Class<? extends DataSource> dataSourceType = Class.forName(properties.getType()).asSubclass(DataSource.class);
                builder.type(dataSourceType);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Failed to load data source type: " + properties.getType(), e);
            }
        }
        
        // 创建数据源
        DataSource dataSource = builder.build();
        
        // 应用数据源特定配置（如果有）
        if (properties.getConfiguration() != null && !properties.getConfiguration().isEmpty()) {
            applyConfiguration(dataSource, properties.getConfiguration());
        }
        
        return dataSource;
    }
    
    /**
     * 创建JNDI数据源
     * @param jndiName JNDI名称
     * @return 数据源实例
     */
    private DataSource createJndiDataSource(String jndiName) {
        JndiDataSourceLookup lookup = new JndiDataSourceLookup();
        return lookup.getDataSource(jndiName);
    }
    
    /**
     * 应用数据源配置
     * @param dataSource 数据源
     * @param configuration 配置属性
     */
    private void applyConfiguration(DataSource dataSource, Map<String, Object> configuration) {
        // 这个方法可以扩展，用于应用特定数据源的配置
        // 由于不同的数据源实现有不同的配置方式，这里做基础处理
        // 实际项目中可以根据具体的数据源类型进行配置
    }
    
    /**
     * 清除数据源缓存
     */
    public void clearCache() {
        // 清理缓存，但不尝试关闭数据源
        // 因为Java的DataSource接口没有定义close方法，避免对特定数据源的依赖
        dataSourceCache.clear();
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public boolean isStrict() {
        return strict;
    }
    
    public void setStrict(boolean strict) {
        this.strict = strict;
    }
    
    public String getPrimary() {
        return primary;
    }
    
    public void setPrimary(String primary) {
        this.primary = primary;
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
     * 获取数据源缓存
     */
    public Map<String, DataSource> getDataSourceCache() {
        return dataSourceCache;
    }
    
    /**
     * 内部类：数据源配置属性
     */
    public static class DataSourceProperties {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private String jndiName;
        private String type;
        private Map<String, Object> configuration = new LinkedHashMap<>();
        
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
        
        public String getJndiName() {
            return jndiName;
        }
        
        public void setJndiName(String jndiName) {
            this.jndiName = jndiName;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
        
        public Map<String, Object> getConfiguration() {
            return configuration;
        }
        
        public void setConfiguration(Map<String, Object> configuration) {
            this.configuration = configuration;
        }
    }
}