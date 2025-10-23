package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态数据源实现
 */
public class DynamicDataSource extends AbstractRoutingDataSource {
    
    private static final Logger log = LoggerFactory.getLogger(DynamicDataSource.class);
    
    /**
     * 是否严格匹配数据源，默认false
     */
    private boolean strict = false;
    
    /**
     * 默认数据源名称
     */
    private String primary = "master";
    
    /**
     * 存储已注册的数据源
     */
    private final Map<Object, Object> registeredDataSources = new ConcurrentHashMap<>();
    
    @Override
    protected Object determineCurrentLookupKey() {
        Object lookupKey = DataSourceContextHolder.getCurrentLookupKey();
        
        // 如果没有指定数据源或指定的数据源不存在，根据严格模式决定行为
        if (lookupKey == null) {
            log.debug("No lookup key found in context, using primary datasource: {}", primary);
            return primary;
        }
        
        // 检查指定的数据源是否存在
        // 直接使用已注册的数据源映射
        if (!registeredDataSources.containsKey(lookupKey)) {
            String errorMsg = String.format("Datasource '%s' not found", lookupKey);
            log.warn(errorMsg);
            
            if (strict) {
                throw new RuntimeException(errorMsg);
            }
            
            // 非严格模式下，返回默认数据源
            log.debug("Datasource not found, using primary datasource: {}", primary);
            return primary;
        }
        
        log.debug("Current datasource lookup key: {}", lookupKey);
        return lookupKey;
    }
    
    @Override
    public void setTargetDataSources(Map<Object, Object> targetDataSources) {
        Assert.notEmpty(targetDataSources, "Target data sources must not be empty");
        
        // 保存到已注册数据源
        registeredDataSources.clear();
        registeredDataSources.putAll(targetDataSources);
        
        super.setTargetDataSources(targetDataSources);
        // 刷新目标数据源缓存
        afterPropertiesSet();
        
        log.info("Dynamic datasources initialized, available datasources: {}", targetDataSources.keySet());
    }
    
    /**
     * 添加单个数据源
     * @param key 数据源标识
     * @param dataSource 数据源
     */
    public void addDataSource(String key, DataSource dataSource) {
        Assert.notNull(key, "Data source key must not be null");
        Assert.notNull(dataSource, "Data source must not be null");
        
        registeredDataSources.put(key, dataSource);
        
        // 复制现有数据源到新的线程安全Map中
        Map<Object, Object> targetDataSources = new ConcurrentHashMap<>(registeredDataSources);
        
        // 更新目标数据源映射
        super.setTargetDataSources(targetDataSources);
        afterPropertiesSet();
        
        log.info("Added datasource: {}", key);
    }
    
    /**
     * 移除数据源
     * @param key 数据源标识
     */
    public void removeDataSource(String key) {
        Assert.notNull(key, "Data source key must not be null");
        
        // 不允许移除主数据源
        if (StringUtils.hasText(primary) && primary.equals(key)) {
            log.warn("Cannot remove primary datasource: {}", key);
            throw new RuntimeException("Cannot remove primary datasource: " + key);
        }
        
        registeredDataSources.remove(key);
        
        // 复制现有数据源到新的线程安全Map中
        Map<Object, Object> targetDataSources = new ConcurrentHashMap<>(registeredDataSources);
        
        // 更新目标数据源映射
        super.setTargetDataSources(targetDataSources);
        afterPropertiesSet();
        
        log.info("Removed datasource: {}", key);
    }
    
    /**
     * 获取所有可用的数据源
     * @return 数据源映射
     */
    public Map<Object, Object> getAllDataSources() {
        return new ConcurrentHashMap<>(registeredDataSources);
    }
    
    /**
     * 检查数据源是否存在
     * @param key 数据源标识
     * @return 是否存在
     */
    public boolean containsDataSource(String key) {
        return registeredDataSources.containsKey(key);
    }
    
    /**
     * 获取当前使用的数据源数量
     * @return 数据源数量
     */
    public int getDataSourceCount() {
        return registeredDataSources.size();
    }
    
    /**
     * 获取当前使用的数据源
     * @return 当前数据源实例
     */
    public DataSource getCurrentDataSource() {
        // 使用public方法获取当前数据源
        Object lookupKey = determineCurrentLookupKey();
        if (lookupKey != null && registeredDataSources.containsKey(lookupKey)) {
            return (DataSource) registeredDataSources.get(lookupKey);
        }
        return (DataSource) registeredDataSources.getOrDefault(primary, null);
    }
    
    // Getters and Setters
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
}
