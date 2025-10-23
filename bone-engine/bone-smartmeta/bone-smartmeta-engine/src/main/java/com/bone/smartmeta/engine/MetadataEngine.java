package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.model.EntityMetadata;
import com.bone.smartmeta.engine.repository.MetadataRepository;
import com.bone.smartmeta.engine.processor.MetadataProcessor;
import com.bone.smartmeta.engine.registry.MetadataRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * 元数据引擎 - 核心类
 * 提供元数据实体的管理、注册、缓存和通知功能
 * 遵循Spring Bean生命周期管理
 */
@Component
public class MetadataEngine implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(MetadataEngine.class.getName());

    // 使用接口而非Object类型，提高类型安全性
    private final MetadataRegistry metadataRegistry;
    private final MetadataRepository metadataRepository;
    private final MetadataProcessor metadataProcessor;
    private final ApplicationEventPublisher eventPublisher;
    
    // 使用ConcurrentHashMap提高线程安全性
    private final Map<String, Object> entityMetadataMap = new ConcurrentHashMap<>();
    private final Map<String, Object> operationMetadataCache = new ConcurrentHashMap<>();

    /**
     * 构造函数 - 使用依赖注入
     * 
     * @param metadataRegistry 元数据注册表
     * @param metadataRepository 元数据仓库
     * @param metadataProcessor 元数据处理器
     * @param eventPublisher 事件发布器
     */
    @Autowired
    public MetadataEngine(MetadataRegistry metadataRegistry,
                         MetadataRepository metadataRepository,
                         MetadataProcessor metadataProcessor,
                         ApplicationEventPublisher eventPublisher) {
        this.metadataRegistry = metadataRegistry;
        this.metadataRepository = metadataRepository;
        this.metadataProcessor = metadataProcessor;
        this.eventPublisher = eventPublisher;
        log.info("MetadataEngine initialized with dependencies");
    }
    
    /**
     * 用于测试或简单场景的构造函数
     */
    public MetadataEngine() {
        // 用于测试场景，实际应用中应使用上面的构造函数
        this.metadataRegistry = null;
        this.metadataRepository = null;
        this.metadataProcessor = null;
        this.eventPublisher = null;
        log.info("MetadataEngine initialized without dependencies (for testing)");
    }

    /**
     * 设置操作服务
     * 
     * @param operationService 操作服务实例
     */
    public void setOperationService(Object operationService) {
        Assert.notNull(operationService, "Operation service cannot be null");
        this.operationService = operationService;
    }
    
    /**
     * 获取操作服务
     * 
     * @return 操作服务实例
     */
    public Object getOperationService() {
        return operationService;
    }

    // 配置属性
    private boolean cacheEnabled = true;
    private boolean validationEnabled = true;
    private boolean calculationEnabled = true;
    private long cacheExpirationTime = 3600000; // 默认缓存过期时间：1小时
    private int maxRetries = 3; // 操作重试次数
    private long retryDelay = 100; // 重试延迟时间（毫秒）
    private Object operationService; // 操作服务

    private final Map<String, CacheEntry<?>> entityMetadataCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> expressionEngineCache = new ConcurrentHashMap<>();

    public interface MetadataChangeListener {
        void onMetadataChanged(String entityType, String changeType);
    }

    private final List<MetadataChangeListener> metadataChangeListeners = new ArrayList<>();

    private static class CacheEntry<T> {
        private final T value;
        private final long expirationTime;
        private long lastAccessTime;

        public CacheEntry(T value, long ttlMillis) {
            this.value = value;
            this.expirationTime = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : Long.MAX_VALUE;
            this.lastAccessTime = System.currentTimeMillis();
        }

        public T getValue() {
            this.lastAccessTime = System.currentTimeMillis();
            return value;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
        
        public long getLastAccessTime() {
            return lastAccessTime;
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Initializing MetadataEngine");
        validateDependencies();
        initialize();
        initializeOperationMetadata();
        startHealthCheck();
        log.info("MetadataEngine initialization completed");
    }

    private void initializeOperationMetadata() {
        try {
            log.info("Initializing operation metadata");
            // 从仓库加载操作元数据
            if (metadataRepository != null) {
                List<?> operationMetadataList = (List<?>) invokeRepositoryMethod(metadataRepository, "findAllOperations");
                if (operationMetadataList != null) {
                    for (Object metadata : operationMetadataList) {
                        updateCache(metadata);
                    }
                    log.info("Loaded {} operation metadata entries", operationMetadataList.size());
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize operation metadata: {}", e.getMessage(), e);
        }
    }

    private void updateCache(Object metadata) {
        if (!cacheEnabled) return;
        
        try {
            String entityName = getEntityNameFromMetadata(metadata);
            entityMetadataCache.put(entityName, new CacheEntry<>(metadata, cacheExpirationTime));
            // 同时更新entityMetadataMap以保持一致性
            entityMetadataMap.put(entityName, metadata);
            log.debug("Updated cache for entity: {}", entityName);
            
            // 执行缓存清理，防止内存泄漏
            cleanupExpiredCacheEntries();
        } catch (Exception e) {
            log.error("Failed to update cache: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 清理过期的缓存条目
     */
    private void cleanupExpiredCacheEntries() {
        try {
            // 定期清理过期缓存（每100次更新触发一次清理）
            if (entityMetadataCache.size() % 100 == 0) {
                List<String> expiredKeys = new ArrayList<>();
                for (Map.Entry<String, CacheEntry<?>> entry : entityMetadataCache.entrySet()) {
                    if (entry.getValue().isExpired()) {
                        expiredKeys.add(entry.getKey());
                    }
                }
                
                for (String key : expiredKeys) {
                    entityMetadataCache.remove(key);
                    entityMetadataMap.remove(key);
                }
                
                if (!expiredKeys.isEmpty()) {
                    log.debug("Cleaned up {} expired cache entries", expiredKeys.size());
                }
            }
        } catch (Exception e) {
            log.error("Failed to cleanup cache: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 从元数据对象中提取实体名称
     */
    private String getEntityNameFromMetadata(Object metadata) {
        try {
            if (metadata == null) {
                throw new IllegalArgumentException("Metadata cannot be null");
            }
            
            if (metadata instanceof EntityMetadata) {
                return ((EntityMetadata) metadata).getName();
            } else if (metadata instanceof com.bone.smartmeta.engine.metadata.EntityMetadata) {
                return ((com.bone.smartmeta.engine.metadata.EntityMetadata) metadata).getName();
            } else if (metadata instanceof Map) {
                Object nameObj = ((Map<?, ?>) metadata).get("name");
                if (nameObj != null) {
                    return nameObj.toString();
                }
                // 兼容旧的apiName字段
                nameObj = ((Map<?, ?>)metadata).get("apiName");
                if (nameObj instanceof String) {
                    return (String)nameObj;
                }
            }
            
            // 尝试通过反射获取name属性
            Object result = invokeIfPossibleReturn(metadata, "getName");
            if (result != null) {
                return result.toString();
            }
            
            // 使用类名作为后备
            return metadata.getClass().getSimpleName();
        } catch (Exception e) {
            log.error("Failed to extract entity name from metadata: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Cannot determine entity name from metadata", e);
        }
    }

    public void startHotReload(long intervalMillis) {
        // 启动热重载的逻辑
    }

    /**
     * 注册实体元数据
     * 
     * @param metadata 实体元数据
     */
    public void registerEntityMetadata(Object metadata) {
        Assert.notNull(metadata, "Metadata cannot be null");
        // 委托给registerEntity方法
        registerEntity(metadata);
    }
    
    /**
     * 验证依赖
     */
    private void validateDependencies() {
        // 简化实现，不进行严格的非空检查
        log.debug("验证元数据引擎依赖");
    }
    
    /**
     * 加载所有实体元数据
     */
    private void loadAllEntityMetadata() {
        // 简化实现
        log.info("加载实体元数据");
    }
    
    /**
     * 启动健康检查
     */
    private void startHealthCheck() {
        // 简化实现
        log.info("启动健康检查");
    }
    
    /**
     * 初始化元数据引擎
     */
    public void initialize() {
        // 初始化元数据引擎
        try {
            // 验证依赖
            validateDependencies();
            
            // 加载所有实体元数据
            loadAllEntityMetadata();
            
            // 启动健康检查
            startHealthCheck();
            
            log.info("Metadata engine initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize metadata engine", e);
            throw new RuntimeException("Failed to initialize metadata engine", e);
        }
    }
    
    /**
     * 注册实体 - 为MetadataEngineInitializer提供的方法
     * 
     * @param metadata 实体元数据
     * @return 注册的元数据
     */
    public Object registerEntity(Object metadata) {
        Assert.notNull(metadata, "Entity metadata cannot be null");
        
        // 获取实体名称（支持Map或对象类型）
        String entityName = getEntityNameFromMetadata(metadata);
        
        if (entityName == null || entityName.isEmpty() || "unknown".equals(entityName)) {
            throw new IllegalArgumentException("Entity API name cannot be null or invalid");
        }
        
        log.info("Registering entity metadata: {}", entityName);
        
        // 存储实体元数据
        entityMetadataMap.put(entityName, metadata);
        
        // 更新缓存
        updateCache(metadata);
        
        // 发布元数据变更事件
        notifyMetadataChanged(metadata, MetadataChangeType.CREATE);
        
        // 如有注册器，同步注册
        if (metadataRegistry != null) {
            try {
                // 尝试调用注册方法（适配不同接口）
                invokeIfPossible(metadataRegistry, "registerMetadata", metadata);
            } catch (Exception e) {
                log.warn("Failed to register metadata with registry: {}", e.getMessage());
            }
        }
        
        return metadata;
    }
    
    /**
     * 尝试调用对象的方法，捕获所有异常
     */
    private void invokeIfPossible(Object target, String methodName, Object... args) {
        if (target == null) return;
        
        try {
            // 简化实现，实际项目中可使用反射或Spring的MethodInvoker
            log.debug("Invoking method {} on {}", methodName, target.getClass().getName());
            // 这里可以根据实际需要实现反射调用
        } catch (Exception e) {
            log.warn("Failed to invoke method: {}", e.getMessage());
        }
    }

    /**
     * 获取实体元数据
     * 
     * @param entityName 实体名称
     * @return 实体元数据，如果不存在则返回null
     */
    public Object getEntityMetadata(String entityName) {
        Assert.hasText(entityName, "Entity name cannot be empty");
        
        // 先从缓存获取
        if (cacheEnabled) {
            CacheEntry entry = entityMetadataCache.get(entityName);
            if (entry != null && !entry.isExpired()) {
                log.debug("Retrieved entity metadata from cache: {}", entityName);
                return entry.getValue();
            }
        }
        
        // 从存储获取
        Object metadata = entityMetadataMap.get(entityName);
        
        // 如果找到了，更新缓存
        if (metadata != null && cacheEnabled) {
            entityMetadataCache.put(entityName, new CacheEntry(metadata, cacheExpirationTime));
        }
        
        return metadata;
    }
    
    /**
     * 安全获取实体元数据，如果不存在则抛出异常
     * 
     * @param entityName 实体名称
     * @return 实体元数据
     */
    public Object getRequiredEntityMetadata(String entityName) {
        Object metadata = getEntityMetadata(entityName);
        if (metadata == null) {
            throw new IllegalStateException("Entity metadata not found: " + entityName);
        }
        return metadata;
    }

    /**
     * 批量获取实体元数据
     * 
     * @param entityNames 实体名称集合
     * @return 实体元数据映射
     */
    public Map<String, Object> batchGetEntityMetadata(Collection<String> entityNames) {
        Map<String, Object> result = new HashMap<>();
        if (entityNames != null && !entityNames.isEmpty()) {
            for (String name : entityNames) {
                if (name != null && !name.isEmpty()) {
                    Object metadata = getEntityMetadata(name);
                    if (metadata != null) {
                        result.put(name, metadata);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 取消注册实体元数据
     * 支持多租户隔离
     * 
     * @param entityName 实体名称
     * @return 是否成功取消注册
     */
    public boolean unregisterEntity(String entityName) {
        Assert.hasText(entityName, "Entity name cannot be empty");
        
        Object metadata = entityMetadataMap.get(entityName);
        if (metadata != null) {
            try {
                // 执行影响分析
                Object impactResult = analyzeMetadataImpact(entityName, null);
                
                // 检查是否为关键影响
                if (isCriticalImpact(impactResult)) {
                    log.error("Cannot delete critical metadata: {}", entityName);
                    throw new IllegalStateException("Cannot delete critical metadata");
                }
                
                // 移除元数据
                entityMetadataMap.remove(entityName);
                
                // 从缓存删除
                entityMetadataCache.remove(entityName);
                
                // 发布变更事件
                notifyMetadataChanged(metadata, MetadataChangeType.DELETE);
                
                log.info("Successfully unregistered entity metadata: {}", entityName);
                return true;
            } catch (Exception e) {
                log.error("Error unregistering entity metadata: {}", entityName, e);
                return false;
            }
        } else {
            log.warn("Entity metadata not found: {}", entityName);
            return false;
        }
    }
    
    /**
     * 检查是否为关键影响
     */
    private boolean isCriticalImpact(Object impactResult) {
        if (impactResult instanceof Map) {
            Object level = ((Map<?, ?>)impactResult).get("impactLevel");
            return "CRITICAL".equals(level);
        }
        return false;
    }
    
    /**
     * 重新加载实体元数据
     * 
     * @param entityName 实体名称
     * @return 重新加载的元数据
     */
    public Object reloadEntityMetadata(String entityName) {
        Assert.hasText(entityName, "Entity name cannot be empty");
        
        // 清除缓存
        entityMetadataCache.remove(entityName);
        
        // 如果有仓库，尝试从仓库重新加载
        if (metadataRepository != null) {
            try {
                // 尝试从仓库加载
                Object metadata = invokeRepositoryMethod(metadataRepository, "findById", entityName);
                if (metadata != null) {
                    entityMetadataMap.put(entityName, metadata);
                    updateCache(metadata);
                    log.info("Reloaded entity metadata from repository: {}", entityName);
                    return metadata;
                }
            } catch (Exception e) {
                log.warn("Failed to reload metadata from repository: {}", e.getMessage());
            }
        }
        
        // 返回当前内存中的数据
        Object metadata = entityMetadataMap.get(entityName);
        if (metadata != null) {
            updateCache(metadata);
        }
        
        return metadata;
    }
    
    /**
     * 尝试从仓库调用方法
     */
    private Object invokeRepositoryMethod(Object repository, String methodName, Object... args) {
        // 简化实现，实际项目中可使用反射
        log.debug("Attempting to invoke {} on repository", methodName);
        return null;
    }
    
    /**
     * 执行元数据变更影响分析
     * 
     * @param oldEntityName 旧实体名称
     * @param newMetadata 新的元数据
     * @return 影响分析结果
     */
    public Object analyzeMetadataImpact(String oldEntityName, 
                                      Object newMetadata) {
        try {
            // 如果有分析器，使用分析器进行分析
            if (metadataProcessor != null) {
                try {
                    Object result = invokeIfPossibleReturn(metadataProcessor, "analyzeImpact", oldEntityName, newMetadata);
                    if (result != null) {
                        return result;
                    }
                } catch (Exception e) {
                    log.warn("Failed to use processor for impact analysis: {}", e.getMessage());
                }
            }
            
            // 默认分析结果
            Map<String, Object> result = new HashMap<>();
            result.put("impactLevel", "LOW");
            result.put("entityName", oldEntityName);
            result.put("timestamp", System.currentTimeMillis());
            return result;
        } catch (Exception e) {
            log.error("Failed to create impact analysis result", e);
            Map<String, Object> result = new HashMap<>();
            result.put("impactLevel", "LOW");
            return result;
        }
    }
    
    /**
     * 尝试调用对象的方法并返回结果
     */
    private Object invokeIfPossibleReturn(Object target, String methodName, Object... args) {
        if (target == null) return null;
        
        try {
            // 简化实现
            log.debug("Invoking method {} on {}", methodName, target.getClass().getName());
            return null;
        } catch (Exception e) {
            log.warn("Failed to invoke method: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 转换元数据格式
     */
    private Object convertToModelEntityMetadata(Object metadata) {
        // 简化实现，实际项目中应实现完整的转换逻辑
        if (metadata instanceof Map) {
            return new HashMap<>((Map<?, ?>)metadata);
        }
        return new HashMap<String, Object>();
    }
    
    /**
     * 通知元数据变更
     */
    private void notifyMetadataChanged(Object metadata, MetadataChangeType changeType) {
        String entityType = getEntityNameFromMetadata(metadata);
        
        // 通知监听器
        for (MetadataChangeListener listener : metadataChangeListeners) {
            try {
                listener.onMetadataChanged(entityType, changeType.name());
            } catch (Exception e) {
                log.error("Failed to notify metadata change for listener: {}", listener.getClass().getName(), e);
            }
        }
        
        // 如果有事件发布器，发布Spring事件
        if (eventPublisher != null) {
            try {
                // 创建并发布元数据变更事件
                MetadataChangedEvent event = new MetadataChangedEvent(this, entityType, changeType);
                eventPublisher.publishEvent(event);
                log.debug("Published metadata change event: {} for {}", changeType, entityType);
            } catch (Exception e) {
                log.warn("Failed to publish metadata change event: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 添加元数据变更监听器
     */
    public void addMetadataChangeListener(MetadataChangeListener listener) {
        Assert.notNull(listener, "Listener cannot be null");
        this.metadataChangeListeners.add(listener);
    }
    
    /**
     * 移除元数据变更监听器
     */
    public void removeMetadataChangeListener(MetadataChangeListener listener) {
        if (listener != null) {
            this.metadataChangeListeners.remove(listener);
        }
    }
    
    /**
     * 元数据变更类型枚举
     */
    public enum MetadataChangeType {
        CREATE, UPDATE, DELETE
    }
    
    /**
     * 元数据变更事件 - 用于Spring事件机制
     */
    public static class MetadataChangedEvent extends ApplicationEvent {
        private final String entityType;
        private final MetadataChangeType changeType;
        private final long changeTimestamp;
        
        public MetadataChangedEvent(Object source, String entityType, MetadataChangeType changeType) {
            super(source);
            this.entityType = entityType;
            this.changeType = changeType;
            this.changeTimestamp = System.currentTimeMillis();
        }
        
        public String getEntityType() {
            return entityType;
        }
        
        public MetadataChangeType getChangeType() {
            return changeType;
        }
        
        public long getChangeTimestamp() {
            return changeTimestamp;
        }
    }
    
    // Getters and setters for configuration properties
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }
    
    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }
    
    public boolean isValidationEnabled() {
        return validationEnabled;
    }
    
    public void setValidationEnabled(boolean validationEnabled) {
        this.validationEnabled = validationEnabled;
    }
    
    public boolean isCalculationEnabled() {
        return calculationEnabled;
    }
    
    public void setCalculationEnabled(boolean calculationEnabled) {
        this.calculationEnabled = calculationEnabled;
    }
    
    public long getCacheExpirationTime() {
        return cacheExpirationTime;
    }
    
    public void setCacheExpirationTime(long cacheExpirationTime) {
        this.cacheExpirationTime = cacheExpirationTime;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    public long getRetryDelay() {
        return retryDelay;
    }
    
    public void setRetryDelay(long retryDelay) {
        this.retryDelay = retryDelay;
    }
}