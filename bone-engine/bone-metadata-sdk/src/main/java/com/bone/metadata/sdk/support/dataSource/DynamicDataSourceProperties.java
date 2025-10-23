package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源配置属性
 * 从配置文件读取数据源配置
 */
@ConfigurationProperties(prefix = "spring.datasource.dynamic")
public class DynamicDataSourceProperties {
    
    private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceProperties.class);
    
    /**
     * 主数据源名称，默认master
     */
    private String primary = "master";
    
    /**
     * 是否严格匹配数据源，默认false
     * true未匹配到指定数据源时抛异常，false使用默认数据源
     */
    private boolean strict = false;
    
    /**
     * 数据源配置
     */
    private Map<String, DataSourceProperties> datasource = new HashMap<>();
    
    /**
     * 缓存已创建的数据源实例，避免重复创建
     */
    private final Map<String, DataSource> dataSourceCache = new ConcurrentHashMap<>();
    
    /**
     * 从缓存获取或创建数据源
     * @param name 数据源名称
     * @return 数据源实例
     */
    public DataSource getDataSource(String name) {
        DataSourceProperties properties = datasource.get(name);
        if (properties == null) {
            throw new RuntimeException("DataSource not found: " + name);
        }
        return dataSourceCache.computeIfAbsent(name, key -> buildDataSource(properties));
    }
    
    /**
     * 从缓存获取或创建数据源
     * @param name 数据源名称
     * @param properties 数据源配置
     * @return 数据源实例
     */
    public DataSource getDataSource(String name, DataSourceProperties properties) {
        return dataSourceCache.computeIfAbsent(name, key -> buildDataSource(properties));
    }
    
    /**
     * 构建数据源实例
     * @param properties 数据源配置
     * @return 数据源实例，如果配置不完整则返回null
     */
    private DataSource buildDataSource(DataSourceProperties properties) {
        if (properties == null) {
            log.warn("Invalid datasource configuration, properties is null");
            return null;
        }
        
        // 如果配置了JNDI名称，则使用JNDI查找
        if (properties.getJndiName() != null && !properties.getJndiName().trim().isEmpty()) {
            log.debug("Creating JNDI datasource with name: {}", properties.getJndiName());
            return createJndiDataSource(properties.getJndiName());
        }
        
        // 检查必要的属性
        if (!StringUtils.hasText(properties.getUrl())) {
            log.warn("Invalid datasource configuration, url is required");
            return null;
        }
        
        try {
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
            
            log.debug("Creating datasource with url: {}", maskUrl(properties.getUrl()));
            DataSource dataSource = builder.build();
            
            // 应用数据源特定配置（如果有）
            if (properties.getConfiguration() != null && !properties.getConfiguration().isEmpty()) {
                applyConfiguration(dataSource, properties.getConfiguration());
            }
            
            return dataSource;
        } catch (Exception e) {
            log.error("Failed to create datasource with url: {}", maskUrl(properties.getUrl()), e);
            return null;
        }
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
        log.debug("Applying configuration to datasource of type: {}", dataSource.getClass().getName());
    }
    
    /**
     * 清除数据源缓存
     * 在应用关闭或重新加载配置时调用
     */
    public void clearCache() {
        dataSourceCache.forEach((name, dataSource) -> {
            log.info("Closing datasource: {}", name);
            // 大多数现代数据源实现都有close方法，但Java的DataSource接口没有定义它
            // 这里我们不做具体实现，避免对特定数据源的依赖
        });
        dataSourceCache.clear();
        log.info("DataSource cache cleared");
    }
    
    /**
     * 掩码化URL，用于日志输出，保护敏感信息
     * @param url 数据库URL
     * @return 掩码化后的URL
     */
    private String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        
        // 简单的掩码处理，实际项目中可以根据需要进行更复杂的处理
        return url.replaceAll("password=([^&]+)", "password=******");
    }
    
    // Getters and Setters
    public String getPrimary() {
        return primary;
    }
    
    public void setPrimary(String primary) {
        this.primary = primary;
    }
    
    public boolean isStrict() {
        return strict;
    }
    
    public void setStrict(boolean strict) {
        this.strict = strict;
    }
    
    public Map<String, DataSourceProperties> getDatasource() {
        return datasource;
    }
    
    public void setDatasource(Map<String, DataSourceProperties> datasource) {
        this.datasource = datasource != null ? datasource : new HashMap<>();
    }
    
    public Map<String, DataSource> getDataSourceCache() {
        return dataSourceCache;
    }
    
    /**
     * 内部类：数据源属性配置
     */
    public static class DataSourceProperties {
        
        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private String jndiName;
        private String type; // 数据源类型
        private Map<String, Object> configuration = new HashMap<>(); // 数据源特定配置
        
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
            this.configuration = configuration != null ? configuration : new HashMap<>();
        }
    }
}