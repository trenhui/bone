package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 动态数据源实现，扩展Spring的AbstractRoutingDataSource。
 * <p>
 * 提供线程安全的数据源管理和路由功能，支持运行时数据源切换、注册和移除。
 * 实现与DataSourceManager的双向集成，确保数据源状态的一致性。
 * 支持严格模式和容错模式操作。
 * </p>
 * <p>
 * 遵循组合优于继承的设计原则，在Spring框架基础上扩展数据源管理能力。
 * </p>
 */
public class DynamicDataSource extends AbstractRoutingDataSource {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamicDataSource.class);
    
    /**
     * 是否对数据源使用严格匹配，默认为false（容错模式）
     */
    private boolean strictMode = false;
    
    /**
     * 默认主数据源标识符
     */
    private String defaultDataSourceKey = DataSourceContextHolder.DEFAULT_DATASOURCE;
    
    /**
     * 获取默认数据源键
     * 
     * @return 默认数据源键
     */
    public String getDefaultDataSourceKey() {
        return defaultDataSourceKey;
    }
    
    /**
     * 设置默认数据源键
     * 
     * @param defaultDataSourceKey 要设置的默认数据源键
     */
    public void setDefaultDataSourceKey(String defaultDataSourceKey) {
        Assert.hasText(defaultDataSourceKey, "Default data source key cannot be empty");
        this.defaultDataSourceKey = defaultDataSourceKey;
    }
    
    /**
     * 存储注册数据源的线程安全映射
     */
    private final Map<Object, Object> registeredDataSources = new ConcurrentHashMap<>();
    
    /**
     * 用于双向集成的数据源管理器
     */
    private DataSourceManager dataSourceManager;
    
    /**
     * 数据源变更监听器集合
     */
    private final Set<DataSourceChangeListener> changeListeners = ConcurrentHashMap.newKeySet();
    
    /**
     * 默认构造函数
     */
    public DynamicDataSource() {
        super();
        logger.debug("创建默认动态数据源实例");
    }
    
    /**
     * 带数据源管理器的构造函数
     * 
     * @param dataSourceManager 数据源管理器
     */
    public DynamicDataSource(DataSourceManager dataSourceManager) {
        this.dataSourceManager = dataSourceManager;
        logger.debug("创建带管理器的动态数据源实例");
    }
    
    /**
     * 带有初始数据源映射的构造函数
     * 
     * @param targetDataSources 初始目标数据源
     * @throws IllegalArgumentException 如果targetDataSources为空
     */
    public DynamicDataSource(Map<Object, Object> targetDataSources) {
        Assert.notEmpty(targetDataSources, "初始数据源不能为空");
        logger.debug("使用初始数据源创建动态数据源");
        setTargetDataSources(targetDataSources);
    }
    
    /**
     * 添加数据源变更监听器
     * 
     * @param listener 要添加的数据源变更监听器
     */
    public void addChangeListener(DataSourceChangeListener listener) {
        if (listener != null) {
            changeListeners.add(listener);
            logger.debug("已添加数据源变更监听器: {}", listener.getClass().getSimpleName());
        }
    }
    
    /**
     * 移除数据源变更监听器
     * 
     * @param listener 要移除的数据源变更监听器
     */
    public void removeChangeListener(DataSourceChangeListener listener) {
        if (listener != null) {
            changeListeners.remove(listener);
            logger.debug("已移除数据源变更监听器: {}", listener.getClass().getSimpleName());
        }
    }
    
    /**
     * 向所有注册的监听器触发数据源添加事件
     * 
     * @param key 数据源标识符
     * @param dataSource 数据源实例
     */
    private void fireDataSourceAddedEvent(String key, DataSource dataSource) {
        for (DataSourceChangeListener listener : changeListeners) {
            try {
                listener.onDataSourceAdded(key, dataSource);
            } catch (Exception e) {
                logger.error("触发数据源添加事件失败", e);
            }
        }
    }
    
    /**
     * 向所有注册的监听器触发数据源移除事件
     * 
     * @param key 数据源标识符
     */
    private void fireDataSourceRemovedEvent(String key) {
        for (DataSourceChangeListener listener : changeListeners) {
            try {
                listener.onDataSourceRemoved(key);
            } catch (Exception e) {
                logger.error("触发数据源移除事件失败", e);
            }
        }
    }
    
    /**
     * 数据源变更监听器接口
     */
    public interface DataSourceChangeListener {
        /**
         * 当数据源被添加时调用
         * 
         * @param key 数据源标识符
         * @param dataSource 数据源实例
         */
        void onDataSourceAdded(String key, DataSource dataSource);
        
        /**
         * 当数据源被移除时调用
         * 
         * @param key 数据源标识符
         */
        void onDataSourceRemoved(String key);
    }
    
    @Override
    protected Object determineCurrentLookupKey() {
        try {
            // 1. 解析当前数据源标识符
            Object lookupKey = resolveDataSourceLookupKey();
            
            // 2. 如果未指定数据源，返回默认数据源
            if (lookupKey == null) {
                logger.debug("未指定数据源，使用默认数据源: {}", defaultDataSourceKey);
                return defaultDataSourceKey;
            }
            
            // 3. 确保lookupKey为字符串类型
            String dataSourceKey = String.valueOf(lookupKey);
            
            // 4. 检查数据源是否存在
            if (!registeredDataSources.containsKey(dataSourceKey)) {
                String errorMessage = String.format("数据源 '%s' 不存在于已注册的数据源中", dataSourceKey);
                logger.warn(errorMessage);
                
                // 在严格模式下抛出异常
                if (strictMode) {
                    throw new DataSourceNotFoundException(errorMessage);
                }
                
                // 在容错模式下使用默认数据源
                logger.debug("在容错模式下，使用默认数据源替代不存在的数据源: {}", defaultDataSourceKey);
                return defaultDataSourceKey;
            }
            
            // 5. 返回有效的数据源标识符
            logger.debug("已确定当前数据源标识符: {}", dataSourceKey);
            return dataSourceKey;
        } catch (DataSourceNotFoundException e) {
            // 直接重新抛出自定义异常以确保正确传播
            throw e;
        } catch (Exception e) {
            // 捕获其他异常并根据严格模式处理
            logger.error("确定数据源标识符时出错", e);
            
            if (strictMode) {
                throw new RuntimeException("数据源路由失败: " + e.getMessage(), e);
            }
            
            // 在容错模式下返回默认数据源
            logger.debug("当发生异常时使用默认数据源: {}", defaultDataSourceKey);
            return defaultDataSourceKey;
        }
    }
    
    /**
     * 自定义异常，表示请求的数据源未找到
     * <p>
     * 在严格模式下使用，以清晰标识错误类型
     */
    public static class DataSourceNotFoundException extends RuntimeException {
        public DataSourceNotFoundException(String message) {
            super(message);
        }
    }
    
    /**
     * 安全地确定当前数据源查找键，不抛出异常
     * 
     * @return 数据源查找键，如果发生错误则返回默认数据源键
     */
    protected Object safelyDetermineCurrentLookupKey() {
        try {
            // 在安全模式下，即使是严格模式也不抛出异常
            boolean originalStrictMode = this.strictMode;
            try {
                this.strictMode = false;
                return determineCurrentLookupKey();
            } finally {
                this.strictMode = originalStrictMode;
            }
        } catch (DataSourceNotFoundException e) {
            // 对数据源未找到异常的特殊处理，记录日志但返回默认数据源
            logger.warn("数据源未找到，在安全模式下使用默认数据源: {}", e.getMessage());
            return defaultDataSourceKey;
        } catch (Exception e) {
            // 处理所有其他异常
            logger.error("确定当前数据源查找键时发生异常，使用默认数据源", e);
            return defaultDataSourceKey;
        }
    }
    
    /**
     * 解析数据源查找键，支持多种检索渠道
     * 
     * @return 数据源查找键
     */
    protected Object resolveDataSourceLookupKey() {
        // 1. 从线程上下文获取
        String lookupKey = DataSourceContextHolder.getCurrentDataSource();
        if (lookupKey != null) {
            logger.trace("从线程上下文获取数据源标识符: {}", lookupKey);
            return lookupKey;
        }
        
        // 2. 如果数据源管理器存在，从管理器获取当前数据源名称
        if (dataSourceManager != null) {
            String managerLookupKey = dataSourceManager.getCurrentDataSourceName();
            if (managerLookupKey != null) {
                logger.trace("从数据源管理器获取数据源标识符: {}", managerLookupKey);
                return managerLookupKey;
            }
        }
        
        logger.trace("未找到有效的数据源标识符");
        return null;
    }
    
    @Override
    public void setTargetDataSources(Map<Object, Object> targetDataSources) {
        Assert.notEmpty(targetDataSources, "Target data sources cannot be empty");
        
        // 验证所有值都是DataSource类型
        for (Map.Entry<Object, Object> entry : targetDataSources.entrySet()) {
            Assert.isInstanceOf(DataSource.class, entry.getValue(), 
                String.format("数据源 %s 必须是 javax.sql.DataSource 类型", entry.getKey()));
            Assert.isInstanceOf(String.class, entry.getKey(), 
                String.format("数据源标识符 %s 必须是 String 类型", entry.getKey()));
        }
        
        // 保存到已注册的数据源
        registeredDataSources.clear();
        registeredDataSources.putAll(targetDataSources);
        
        // 与数据源管理器同步
        if (dataSourceManager != null) {
            for (Map.Entry<Object, Object> entry : targetDataSources.entrySet()) {
                try {
                    dataSourceManager.registerDataSource((String) entry.getKey(), (DataSource) entry.getValue());
                } catch (Exception e) {
                    logger.error("无法将数据源与数据源管理器同步: {}", entry.getKey(), e);
                }
            }
        }
        
        // Fire data source added events
        for (Map.Entry<Object, Object> entry : targetDataSources.entrySet()) {
            fireDataSourceAddedEvent((String) entry.getKey(), (DataSource) entry.getValue());
        }
        
        // Update Spring's target data sources and refresh cache
        super.setTargetDataSources(targetDataSources);
        afterPropertiesSet();
        
        logger.info("Dynamic data source initialization completed, registered {} data sources: {}", 
                targetDataSources.size(), targetDataSources.keySet());
    }
    
    /**
     * Registers a new data source
     * 
     * @param key the data source identifier
     * @param dataSource the data source instance
     * @return true if the data source was successfully added, false otherwise
     * @throws IllegalArgumentException if parameters are invalid
     */
    public boolean addDataSource(String key, DataSource dataSource) {
        Assert.hasText(key, "Data source identifier cannot be empty");
        Assert.notNull(dataSource, "Data source instance cannot be null");
        
        // Check if data source already exists
        if (registeredDataSources.containsKey(key)) {
            logger.warn("Data source[{}] already exists, failed to add", key);
            return false;
        }
        
        // Register data source
        registeredDataSources.put(key, dataSource);
        
        // Sync with data source manager
        if (dataSourceManager != null) {
            try {
                dataSourceManager.registerDataSource(key, dataSource);
                logger.debug("Synced adding data source with data source manager: {}", key);
            } catch (Exception e) {
                logger.error("Failed to sync adding data source with data source manager: {}", key, e);
                // Sync failure doesn't affect data source addition, only log
            }
        }
        
        // Update target data sources mapping
        updateTargetDataSources();
        
        // Trigger data source added event
        fireDataSourceAddedEvent(key, dataSource);
        
        logger.info("Successfully added data source: {}", key);
        return true;
    }
    
    /**
     * Updates an existing data source
     * 
     * @param key the data source identifier
     * @param dataSource the new data source instance
     * @return true if the update was successful, false otherwise
     * @throws IllegalArgumentException if parameters are invalid
     */
    public boolean updateDataSource(String key, DataSource dataSource) {
        Assert.hasText(key, "Data source identifier cannot be empty");
        Assert.notNull(dataSource, "Data source instance cannot be null");
        
        // Check if exists
        if (!registeredDataSources.containsKey(key)) {
            logger.warn("Data source[{}] does not exist, update failed", key);
            return false;
        }
        
        // Replace old data source with new data source
        registeredDataSources.put(key, dataSource);
        
        // Sync with data source manager
        if (dataSourceManager != null) {
            try {
                dataSourceManager.registerDataSource(key, dataSource);
                logger.debug("Synced data source update with data source manager: {}", key);
            } catch (Exception e) {
                logger.error("Failed to sync data source update with data source manager: {}", key, e);
            }
        }
        
        // Update target data sources mapping
        updateTargetDataSources();
        
        logger.info("Successfully updated data source: {}", key);
        return true;
    }
    
    /**
     * Updates the target data sources mapping
     */
    private void updateTargetDataSources() {
        // Copy existing data sources to a new thread-safe Map
        Map<Object, Object> targetDataSources = new ConcurrentHashMap<>(registeredDataSources);
        super.setTargetDataSources(targetDataSources);
        
        // Refresh Spring's data source cache
        afterPropertiesSet();
        
        logger.debug("Updated target data sources mapping, current data source count: {}", targetDataSources.size());
    }
    
    /**
     * Unregisters an existing data source
     * 
     * @param key the data source identifier
     * @return true if the data source was successfully removed, false otherwise
     * @throws IllegalArgumentException if parameters are invalid
     */
    public boolean removeDataSource(String key) {
        Assert.hasText(key, "Data source identifier cannot be empty");
        
        // Do not allow removal of default data source
        if (defaultDataSourceKey.equals(key)) {
            logger.warn("Prohibited from removing default data source: {}", key);
            return false;
        }
        
        // Check if data source exists
        if (!registeredDataSources.containsKey(key)) {
            logger.warn("Data source[{}] does not exist, failed to remove", key);
            return false;
        }
        
        // Remove data source
        registeredDataSources.remove(key);
        
        // Sync with data source manager
        if (dataSourceManager != null) {
            try {
                dataSourceManager.unregisterDataSource(key);
                logger.debug("Synced removing data source with data source manager: {}", key);
            } catch (Exception e) {
                logger.error("Failed to sync removing data source with data source manager: {}", key, e);
            }
        }
        
        // Update target data sources mapping
        updateTargetDataSources();
        
        // Trigger data source removed event
        fireDataSourceRemovedEvent(key);
        
        logger.info("Successfully removed data source: {}", key);
        return true;
    }
    
    /**
     * Registers multiple data sources in batch
     * 
     * @param dataSources the data sources mapping
     * @return the number of successfully registered data sources
     * @throws IllegalArgumentException if parameters are invalid
     */
    public int registerDataSources(Map<String, DataSource> dataSources) {
        Assert.notNull(dataSources, "Data sources mapping cannot be null");
        
        int successCount = 0;
        for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
            if (addDataSource(entry.getKey(), entry.getValue())) {
                successCount++;
            }
        }
        
        logger.info("Batch data source registration completed, successful: {}, total: {}", successCount, dataSources.size());
        return successCount;
    }
    
    /**
     * Removes multiple data sources in batch
     * 
     * @param keys the list of data source identifiers
     * @return the number of successfully removed data sources
     */
    public int removeDataSources(Set<String> keys) {
        Assert.notNull(keys, "Data source identifiers list cannot be null");
        
        int successCount = 0;
        for (String key : keys) {
            // Skip primary data source
            if (defaultDataSourceKey.equals(key)) {
                continue;
            }
            
            if (removeDataSource(key)) {
                successCount++;
            }
        }
        
        logger.info("Batch data source removal completed, successful: {}, total: {}", successCount, keys.size());
        return successCount;
    }
    
    /**
     * Safely removes a data source without throwing exceptions
     * 
     * @param key the data source identifier
     * @return true if successfully removed
     */
    public boolean safelyRemoveDataSource(String key) {
        try {
            return removeDataSource(key);
        } catch (Exception e) {
            logger.error("Exception occurred while removing data source: {}", key, e);
            return false;
        }
    }
    
    /**
     * Resets data sources, keeping only the primary data source
     */
    public void resetDataSources() {
        Set<String> keysToRemove = new HashSet<>();
        for (Object key : registeredDataSources.keySet()) {
            keysToRemove.add((String) key);
        }
        keysToRemove.remove(defaultDataSourceKey); // Keep primary data source
        
        for (String key : keysToRemove) {
            safelyRemoveDataSource(key);
        }
        
        logger.info("Data sources have been reset, keeping only primary data source: {}", defaultDataSourceKey);
    }
    
    /**
     * Gets all available data sources
     * 
     * @return an unmodifiable copy of the data sources mapping
     */
    public Map<String, DataSource> getAllDataSources() {
        Map<String, DataSource> result = new ConcurrentHashMap<>();
        for (Map.Entry<Object, Object> entry : registeredDataSources.entrySet()) {
            result.put((String) entry.getKey(), (DataSource) entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }
    
    /**
     * Gets all data source identifiers
     * 
     * @return the set of data source identifiers
     */
    public Set<String> getAllDataSourceKeys() {
        Set<String> keys = new HashSet<>();
        for (Object key : registeredDataSources.keySet()) {
            keys.add((String) key);
        }
        return Collections.unmodifiableSet(keys);
    }
    
    /**
     * Executes an operation with a return value using the specified data source
     * 
     * @param key the data source identifier
     * @param action the operation to execute
     * @param <T> the operation return type
     * @return the operation result
     * @throws IllegalArgumentException if parameters are invalid
     */
    public <T> T executeWithDataSource(String key, Supplier<T> action) {
        Assert.hasText(key, "Data source identifier cannot be empty");
        Assert.notNull(action, "Operation to execute cannot be null");
        
        // Check if data source exists
        if (!containsDataSource(key)) {
            if (strictMode) {
                throw new IllegalArgumentException("Data source does not exist: " + key);
            }
            logger.warn("Data source[{}] does not exist, will use primary data source for execution", key);
            key = defaultDataSourceKey;
        }
        
        logger.debug("Starting to execute operation using data source[{}]", key);
        
        // Use DataSourceContextHolder's safe method to execute operation
        return DataSourceContextHolder.executeInDataSourceWithResult(key, action);
    }
    
    /**
     * Executes an operation without return value using the specified data source
     * 
     * @param key the data source identifier
     * @param action the operation to execute
     * @throws IllegalArgumentException if parameters are invalid
     */
    public void executeWithDataSource(String key, Runnable action) {
        Assert.hasText(key, "Data source identifier cannot be empty");
        Assert.notNull(action, "Operation to execute cannot be null");
        
        // Check if data source exists
        if (!containsDataSource(key)) {
            if (strictMode) {
                throw new IllegalArgumentException("Data source does not exist: " + key);
            }
            logger.warn("Data source[{}] does not exist, will use primary data source for execution", key);
            key = defaultDataSourceKey;
        }
        
        logger.debug("Starting to execute operation using data source[{}]", key);
        
        // Use DataSourceContextHolder's safe method to execute operation
        DataSourceContextHolder.executeInDataSource(key, action);
    }
    
    /**
     * Checks if a data source exists
     * 
     * @param key the data source identifier
     * @return true if the data source exists
     */
    public boolean containsDataSource(String key) {
        return key != null && registeredDataSources.containsKey(key);
    }
    
    /**
     * Checks if a data source is in a valid state
     * 
     * @param key the data source identifier
     * @return true if the data source is valid
     */
    public boolean isValidDataSource(String key) {
        if (!containsDataSource(key)) {
            return false;
        }
        
        try {
            DataSource dataSource = (DataSource) registeredDataSources.get(key);
            // Simple check if data source is available
            try (var connection = dataSource.getConnection()) {
                return connection.isValid(1);
            }
        } catch (Exception e) {
            logger.error("Failed to check data source validity: {}", key, e);
            return false;
        }
    }
    
    /**
     * Gets the current number of registered data sources
     * 
     * @return the number of data sources
     */
    public int getDataSourceCount() {
        return registeredDataSources.size();
    }
    
    /**
     * Checks if there is only the primary data source
     * 
     * @return true if there is only the primary data source
     */
    public boolean hasOnlyPrimaryDataSource() {
        return registeredDataSources.size() == 1 && registeredDataSources.containsKey(defaultDataSourceKey);
    }
    
    /**
     * Checks if strict mode is enabled
     * 
     * @return true if strict mode is enabled
     */
    public boolean isStrictModeEnabled() {
        return strictMode;
    }
    
    /**
     * Gets the currently used data source
     * 
     * @return the current data source instance
     */
    public DataSource getCurrentDataSource() {
        // In strict mode, directly use determineCurrentLookupKey, which will throw an exception when data source doesn't exist
        // In lenient mode, use safe method to avoid exceptions
        Object lookupKey = strictMode ? determineCurrentLookupKey() : safelyDetermineCurrentLookupKey();
        
        if (lookupKey != null && registeredDataSources.containsKey(lookupKey)) {
            return (DataSource) registeredDataSources.get(lookupKey);
        }
        
        // If lookup key is invalid, an exception should have been thrown in strict mode
        return (DataSource) registeredDataSources.getOrDefault(defaultDataSourceKey, null);
    }
    
    /**
     * Gets the identifier of the currently used data source
     * 
     * @return the current data source identifier
     */
    public String getCurrentDataSourceKey() {
        Object lookupKey = safelyDetermineCurrentLookupKey();
        return lookupKey != null ? lookupKey.toString() : defaultDataSourceKey;
    }
    
    /**
     * Gets a data source by its identifier
     * 
     * @param key the data source identifier
     * @return the data source instance, or null if it doesn't exist
     */
    public DataSource getDataSource(String key) {
        return key != null ? (DataSource) registeredDataSources.get(key) : null;
    }
    
    /**
     * Safely gets a data source, ensuring a non-null return value
     * 
     * @param key the data source identifier
     * @return the data source instance, or the primary data source if the specified data source doesn't exist
     */
    public DataSource getSafeDataSource(String key) {
        DataSource dataSource = getDataSource(key);
        if (dataSource == null) {
            logger.warn("Data source[{}] does not exist, returning primary data source", key);
            return getDataSource(defaultDataSourceKey);
        }
        return dataSource;
    }
    
    // Getters and Setters
    public boolean isStrictMode() {
        return strictMode;
    }
    
    public void setStrictMode(boolean strictMode) {
        this.strictMode = strictMode;
        logger.info("Data source strict mode has been {}", strictMode ? "enabled" : "disabled");
    }
    
    public String getPrimaryDataSourceKey() {
        return defaultDataSourceKey;
    }
    
    public void setPrimaryDataSourceKey(String primaryDataSourceKey) {
        Assert.hasText(primaryDataSourceKey, "Primary data source identifier cannot be empty");
        this.defaultDataSourceKey = primaryDataSourceKey;
        logger.info("Primary data source identifier has been set to: {}", primaryDataSourceKey);
    }
    
    public DataSourceManager getDataSourceManager() {
        return dataSourceManager;
    }
    
    public void setDataSourceManager(DataSourceManager dataSourceManager) {
        this.dataSourceManager = dataSourceManager;
        logger.debug("Data source manager has been set");
        
        // Two-way sync data source information
        if (dataSourceManager != null) {
            try {
                // Sync currently registered data sources to the manager
                for (Map.Entry<Object, Object> entry : registeredDataSources.entrySet()) {
                    try {
                        dataSourceManager.registerDataSource((String) entry.getKey(), (DataSource) entry.getValue());
                    } catch (Exception e) {
                        logger.error("Failed to sync data source to manager: {}", entry.getKey(), e);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to sync with data source manager", e);
            }
        }
    }
    
    @Override
    public String toString() {
        return "DynamicDataSource{" +
                "defaultDataSourceKey='" + defaultDataSourceKey + '\'' +
                ", strictMode=" + strictMode +
                ", dataSourceCount=" + registeredDataSources.size() +
                ", dataSourceKeys=" + getAllDataSourceKeys() +
                ", hasDataSourceManager=" + (dataSourceManager != null) +
                '}';
    }
}
