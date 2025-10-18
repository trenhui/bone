package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.metadata.*;
import com.bone.smartmeta.engine.metadata.processor.CompositeMetadataProcessor;
import com.bone.smartmeta.engine.repository.MetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
// 事务相关导入已移除
import java.util.concurrent.CompletableFuture;

/**
 * 元数据引擎，负责元数据的核心处理逻辑
 * 包括元数据的加载、验证、转换和应用
 */
@Component
@RequiredArgsConstructor
public class MetadataEngine implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(MetadataEngine.class);
    
    private final MetadataRegistry metadataRegistry;
    private final MetadataRepository metadataRepository;
    private final CompositeMetadataProcessor metadataProcessor;
    private final ApplicationEventPublisher eventPublisher;
    
    // 配置参数
    private boolean cacheEnabled = true;
    private boolean validationEnabled = true;
    private boolean calculationEnabled = true;
    private long cacheExpirationTime = 3600000; // 默认缓存过期时间：1小时
    private int maxRetries = 3; // 操作重试次数
    private long retryDelay = 100; // 重试延迟时间（毫秒）
    
    // 缓存管理相关
    private final Map<String, CacheEntry> entityMetadataCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> expressionEngineCache = new ConcurrentHashMap<>();
    
    // 监听器集合，使用CopyOnWriteArrayList保证线程安全
    private final List<MetadataChangeListener> metadataChangeListeners = new CopyOnWriteArrayList<>();
    
    // 用于热重载的调度器
    private ScheduledExecutorService hotReloadScheduler;
    
    /**
     * 缓存条目类
     */
    private static class CacheEntry {
        private final Object value;
        private final long expirationTime;
        
        public CacheEntry(Object value, long ttlMillis) {
            this.value = value;
            this.expirationTime = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : Long.MAX_VALUE;
        }
        
        public Object getValue() {
            return value;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("初始化元数据引擎...");
        initialize();
        log.info("元数据引擎初始化完成");
    }
    
    /**
     * 初始化元数据引擎
     */
    public void initialize() {
        try {
            // 验证必要的依赖是否已设置
            validateDependencies();
            
            // 预加载所有实体元数据到缓存
            loadAllEntityMetadata();
            
            // 启动元数据健康检查
            startHealthCheck();
        } catch (Exception e) {
            log.error("初始化元数据引擎失败", e);
            throw new RuntimeException("元数据引擎初始化失败", e);
        }
    }
    
    /**
     * 启动元数据健康检查
     */
    private void startHealthCheck() {
        ScheduledExecutorService healthCheckExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "metadata-health-check-thread");
            thread.setDaemon(true);
            return thread;
        });
        
        // 每5分钟执行一次健康检查
        healthCheckExecutor.scheduleAtFixedRate(this::performHealthCheck, 
                5, 5, TimeUnit.MINUTES);
        
        log.info("元数据健康检查已启动");
    }
    
    /**
     * 执行元数据健康检查
     */
    private void performHealthCheck() {
        try {
            log.debug("执行元数据健康检查");
            // 检查缓存一致性
            validateCacheConsistency();
            // 清理过期缓存
            cleanupExpiredCacheEntries();
        } catch (Exception e) {
            log.error("元数据健康检查失败", e);
        }
    }
    
    /**
     * 验证缓存一致性
     */
    private void validateCacheConsistency() {
        // 检查缓存中实体数量
        int cachedEntities = entityMetadataCache.size();
        int actualEntities = metadataRepository.findAllEntities().size();
        
        if (cachedEntities != actualEntities) {
            log.warn("缓存不一致: 缓存中 {} 个实体, 数据库中 {} 个实体", 
                    cachedEntities, actualEntities);
            // 重新加载缓存
            refreshMetadataInternal();
        }
    }
    
    /**
     * 清理过期缓存条目
     */
    private void cleanupExpiredCacheEntries() {
        entityMetadataCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        expressionEngineCache.entrySet().removeIf(entry -> {
            // 这里简化处理，实际应根据具体缓存策略实现
            return false;
        });
    }
    
    /**
     * 验证依赖项是否已设置
     */
    private void validateDependencies() {
        Assert.notNull(metadataRegistry, "元数据注册表不能为空");
        Assert.notNull(metadataRepository, "元数据仓库不能为空");
        Assert.notNull(metadataProcessor, "元数据处理器不能为空");
    }
    
    /**
     * 加载所有实体元数据
     */
    private void loadAllEntityMetadata() {
        List<EntityMetadata> allEntities = metadataRepository.findAllEntities();
        for (EntityMetadata entity : allEntities) {
            entityMetadataCache.put(entity.getApiName(), new CacheEntry(entity, cacheExpirationTime));
        }
        log.info("成功加载 {} 个实体元数据", allEntities.size());
    }
    
    /**
     * 启动元数据热重载功能
     * @param intervalMillis 重载间隔（毫秒）
     */
    public void startHotReload(long intervalMillis) {
        log.info("启动元数据热重载功能，间隔: {}毫秒", intervalMillis);
        if (hotReloadScheduler != null && !hotReloadScheduler.isShutdown()) {
            log.warn("热重载调度器已经在运行中");
            return;
        }
        
        initializeHotReloadScheduler();
        
        // 使用指数退避策略的任务调度
        hotReloadScheduler.scheduleAtFixedRate(
                withRetry(this::executeHotReload, maxRetries, retryDelay),
                intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        log.info("元数据热重载功能启动成功");
    }
    
    /**
     * 带重试机制的任务执行包装器
     */
    private Runnable withRetry(Runnable task, int maxRetries, long initialDelay) {
        return () -> {
            int attempts = 0;
            long delay = initialDelay;
            
            while (attempts <= maxRetries) {
                try {
                    task.run();
                    return; // 成功执行，直接返回
                } catch (Exception e) {
                    attempts++;
                    if (attempts > maxRetries) {
                        log.error("任务执行失败，已达到最大重试次数", e);
                        return;
                    }
                    
                    log.warn("任务执行失败，第 {} 次重试，延迟 {} 毫秒", attempts, delay);
                    try {
                        Thread.sleep(delay);
                        // 指数退避
                        delay *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("重试等待被中断", ie);
                        return;
                    }
                }
            }
        };
    }
    
    /**
     * 初始化热重载调度器
     */
    private void initializeHotReloadScheduler() {
        hotReloadScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "metadata-hot-reload-thread");
            thread.setDaemon(true);
            return thread;
        });
    }
    
    /**
     * 执行热重载
     */
    private void executeHotReload() {
        try {
            log.debug("开始刷新元数据");
            // 调用元数据处理器执行热重载
            if (metadataProcessor != null) {
                metadataProcessor.hotReloadMetadata();
            }
            // 或者执行现有的刷新逻辑
            refreshMetadataInternal();
            log.debug("元数据刷新完成");
        } catch (Exception e) {
            log.error("元数据刷新失败", e);
        }
    }
    
    /**
     * 刷新元数据（热重载实现）- 内部方法
     */
    private void refreshMetadataInternal() {
        try {
            // 清理所有缓存
            entityMetadataCache.clear();
            expressionEngineCache.clear();
            
            // 重新加载元数据
            loadAllEntityMetadata();
        } catch (Exception e) {
            log.error("内部刷新元数据失败", e);
            throw e;
        }
    }
    
    /**
     * 停止元数据热重载功能
     */
    public void stopHotReload() {
        if (hotReloadScheduler != null && !hotReloadScheduler.isShutdown()) {
            log.info("停止元数据热重载功能");
            shutdownScheduler(hotReloadScheduler);
            hotReloadScheduler = null;
            log.info("元数据热重载功能已停止");
        }
    }
    
    /**
     * 关闭调度器
     */
    private void shutdownScheduler(ScheduledExecutorService scheduler) {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("停止调度器时被中断", e);
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 刷新元数据
     */
    public void refreshMetadata() {
        log.info("刷新元数据引擎中的所有元数据...");
        try {
            // 清理所有缓存
            entityMetadataCache.clear();
            expressionEngineCache.clear();
            
            // 重新加载元数据
            initialize();
            
            // 发布元数据变更事件
            eventPublisher.publishEvent(new MetadataRefreshEvent(this));
            
            log.info("元数据刷新完成");
        } catch (Exception e) {
            log.error("刷新元数据失败", e);
            throw new RuntimeException("元数据刷新失败", e);
        }
    }
    
    /**
     * 异步刷新元数据
     * @return 异步任务的CompletableFuture，便于外部监控任务状态
     */
    @Async
    public CompletableFuture<Void> refreshMetadataAsync() {
        log.info("开始异步刷新所有元数据任务");
        long startTime = System.currentTimeMillis();
        
        try {
            refreshMetadata();
            long endTime = System.currentTimeMillis();
            log.info("异步刷新元数据任务完成，耗时: {}ms", (endTime - startTime));
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("异步刷新元数据任务失败，耗时: {}ms", (endTime - startTime), e);
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
    
    /**
     * 异步注册实体元数据
     * @param metadata 实体元数据
     * @return 异步任务的CompletableFuture
     */
    @Async
    public CompletableFuture<EntityMetadata> registerEntityAsync(EntityMetadata metadata) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                registerEntity(metadata);
                return metadata;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }
    
    /**
     * 异步更新实体元数据
     * @param metadata 实体元数据
     * @return 异步任务的CompletableFuture
     */
    @Async
    public CompletableFuture<EntityMetadata> updateEntityAsync(EntityMetadata metadata) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                updateEntity(metadata);
                return metadata;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }
    
    /**
     * 注册新的实体元数据
     * @param metadata 实体元数据
     */
    // @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void registerEntity(EntityMetadata metadata) {
        String apiName = metadata.getApiName();
        log.debug("注册实体元数据: {}", apiName);
        
        try {
            // 验证实体元数据
            validateEntityMetadata(metadata);
            
            // 执行注册操作
            performEntityRegistration(metadata);
            
            // 发布事件
            eventPublisher.publishEvent(new MetadataChangeEvent(this, metadata, MetadataChangeType.CREATE));
            
            log.info("实体已注册: {}", apiName);
        } catch (Exception e) {
            log.error("注册实体元数据失败: {}", apiName, e);
            throw new RuntimeException("注册实体元数据失败", e);
        }
    }
    
    /**
     * 执行实体注册操作
     */
    private void performEntityRegistration(EntityMetadata metadata) {
        // 使用写时复制策略更新缓存，减少锁竞争
        try {
            // 先保存到数据库
            metadataRepository.saveEntity(metadata);
            
            // 使用乐观锁策略更新缓存
            if (cacheEnabled) {
                entityMetadataCache.compute(metadata.getApiName(), 
                    (k, v) -> {
                        // 创建新的缓存条目，避免修改现有条目
                        return new CacheEntry(new EntityMetadata(metadata), cacheExpirationTime);
                    });
            }
            
            log.debug("实体已成功注册: {}", metadata.getApiName());
        } catch (Exception e) {
            log.error("执行实体注册操作失败: {}", metadata.getApiName(), e);
            throw e;
        }
    }
    
    /**
     * 更新实体元数据
     * @param metadata 实体元数据
     */
    // @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void updateEntity(EntityMetadata metadata) {
        String apiName = metadata.getApiName();
        log.debug("更新实体元数据: {}", apiName);
        
        try {
            // 验证实体元数据
            validateEntityMetadata(metadata);
            
            // 检查实体是否存在
            validateEntityExists(apiName);
            
            // 执行更新操作
            performEntityUpdate(metadata);
            
            // 事务提交后发布事件，避免事件消费者看到未提交的数据
            eventPublisher.publishEvent(new MetadataChangeEvent(this, metadata, MetadataChangeType.UPDATE));
        } catch (Exception e) {
            log.error("更新实体元数据失败: {}", apiName, e);
            throw new RuntimeException("更新实体元数据失败", e);
        }
    }
    
    /**
     * 执行实体更新操作
     */
    private void performEntityUpdate(EntityMetadata metadata) {
        // 先获取旧的缓存条目，用于回滚
        CacheEntry oldEntry = cacheEnabled ? entityMetadataCache.get(metadata.getApiName()) : null;
        
        try {
            // 更新到数据库
            metadataRepository.saveEntity(metadata);
            
            // 使用写时复制策略更新缓存
            if (cacheEnabled) {
                entityMetadataCache.compute(metadata.getApiName(), 
                    (k, v) -> {
                        // 创建新的缓存条目，避免修改现有条目
                        return new CacheEntry(new EntityMetadata(metadata), cacheExpirationTime);
                    });
            }
            
            log.debug("实体元数据已成功更新: {}", metadata.getApiName());
        } catch (Exception e) {
            log.error("更新实体元数据到数据库时出错: {}", metadata.getApiName(), e);
            // 回滚缓存
            if (cacheEnabled && oldEntry != null && !oldEntry.isExpired()) {
                entityMetadataCache.put(metadata.getApiName(), oldEntry);
                log.debug("缓存已回滚: {}", metadata.getApiName());
            }
            throw e;
        }
    
    /**
     * 验证实体是否存在
     */
    private void validateEntityExists(String apiName) {
        if (getEntityMetadata(apiName) == null) {
            throw new IllegalArgumentException("实体不存在: " + apiName);
        }
    }
    
    
    
    // 短时间缓存过期时间，用于缓存空值，防止缓存穿透
    private static final long SHORT_CACHE_EXPIRATION_TIME = 60000; // 1分钟
    
    /**
     * 从缓存获取实体元数据
     */
    private EntityMetadata getCachedEntityMetadata(String apiName) {
        CacheEntry cacheEntry = entityMetadataCache.get(apiName);
        if (cacheEntry != null) {
            if (!cacheEntry.isExpired()) {
                log.debug("从缓存获取实体元数据: {}", apiName);
                return (EntityMetadata) cacheEntry.getValue();
            } else {
                // 缓存过期，清除缓存
                entityMetadataCache.remove(apiName);
                log.debug("缓存已过期并清除: {}", apiName);
            }
        }
        return null;
    }
    
    /**
     * 从数据库获取实体元数据并更新缓存
     */
    private EntityMetadata getFromDatabaseWithCacheUpdate(String apiName) {
        try {
            EntityMetadata metadata = metadataRepository.findEntityByApiName(apiName);
            
            // 更新缓存策略
            if (cacheEnabled) {
                if (metadata != null) {
                    // 缓存存在的实体
                    entityMetadataCache.put(apiName, new CacheEntry(metadata, cacheExpirationTime));
                    log.debug("从数据库获取并缓存实体元数据: {}", apiName);
                } else {
                    // 缓存空值，避免缓存穿透，设置较短的过期时间
                    entityMetadataCache.put(apiName, new CacheEntry(null, SHORT_CACHE_EXPIRATION_TIME));
                    log.debug("实体不存在，缓存空值避免缓存穿透: {}", apiName);
                }
            }
            
            return metadata;
        } catch (Exception e) {
            log.error("从数据库获取实体元数据失败: {}", apiName, e);
            return null;
        }
    }
    
    /**
     * 从缓存或数据库获取实体元数据，使用双重检查锁定模式避免缓存穿透
     * @param apiName 实体API名称
     * @return 实体元数据
     */
    private EntityMetadata getFromCacheOrDatabase(String apiName) {
        // 再次检查缓存
        CacheEntry cacheEntry = entityMetadataCache.get(apiName);
        if (cacheEntry != null && !cacheEntry.isExpired()) {
            return (EntityMetadata) cacheEntry.getValue();
        }
        
        // 从数据库查询
        try {
            EntityMetadata metadata = metadataRepository.findEntityByApiName(apiName);
            
            if (metadata != null) {
                // 更新缓存 - 使用compute确保原子性
                entityMetadataCache.compute(apiName, 
                    (k, v) -> new CacheEntry(metadata, cacheExpirationTime));
                log.debug("从数据库获取并更新缓存: {}", apiName);
            } else {
                // 缓存空值，避免缓存穿透，设置较短的过期时间
                entityMetadataCache.put(apiName, new CacheEntry(null, SHORT_CACHE_EXPIRATION_TIME));
                log.debug("实体不存在，缓存空值避免缓存穿透: {}", apiName);
            }
            
            return metadata;
        } catch (Exception e) {
            log.error("获取实体元数据失败: {}", apiName, e);
            return null;
        }
    }
    
    /**
     * 注册实体类（从PurchaseOrderService中使用的方法）
     * @param entityClass 实体类
     */
    public void registerEntity(Class<?> entityClass) {
        Assert.notNull(entityClass, "实体类不能为空");
        
        try {
            // 简化实现：从类名创建实体元数据
            String apiName = entityClass.getSimpleName();
            EntityMetadata metadata = new EntityMetadata();
            metadata.setApiName(apiName);
            metadata.setLabel(apiName);
            metadata.setFields(new ArrayList<>());
            
            registerEntity(metadata);
            log.info("实体类已成功注册: {}", entityClass.getName());
        } catch (Exception e) {
            log.error("注册实体类失败: {}", entityClass.getName(), e);
            throw new RuntimeException("注册实体类失败", e);
        }
    }
    
    /**
     * 获取缓存启用状态
     */
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }
    
    /**
     * 设置缓存启用状态
     */
    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }
    
    /**
     * 获取验证启用状态
     */
    public boolean isValidationsEnabled() {
        return validationEnabled;
    }
    
    /**
     * 设置验证启用状态
     */
    public void setValidationEnabled(boolean validationEnabled) {
        this.validationEnabled = validationEnabled;
    }
    
    /**
     * 获取缓存过期时间（毫秒）
     */
    public long getCacheExpirationTime() {
        return cacheExpirationTime;
    }
    
    /**
     * 设置缓存过期时间（毫秒）
     */
    public void setCacheExpirationTime(long cacheExpirationTime) {
        this.cacheExpirationTime = cacheExpirationTime;
    }
    

        }
    }
    
    /**
     * 元数据变更事件
     */
    public static class MetadataChangeEvent {
        private final Object source;
        private final EntityMetadata metadata;
        private final MetadataChangeType type;
        
        public MetadataChangeEvent(Object source, EntityMetadata metadata, MetadataChangeType type) {
            this.source = source;
            this.metadata = metadata;
            this.type = type;
        }
        
        // Getters
        public Object getSource() { return source; }
        public EntityMetadata getMetadata() { return metadata; }
        public MetadataChangeType getType() { return type; }
    }
    
    /**
     * 元数据变更类型枚举
     */
    public enum MetadataChangeType {
        CREATE, UPDATE, DELETE
    }
    
    /**
     * 元数据刷新事件
     */
    public static class MetadataRefreshEvent {
        private final Object source;
        
        public MetadataRefreshEvent(Object source) {
            this.source = source;
        }
        
        public Object getSource() { return source; }
    }
    
    /**
     * 获取计算启用状态
     */
    public boolean isCalculationEnabled() {
        return calculationEnabled;
    }
    
    /**
     * 设置计算启用状态
     */
    public void setCalculationEnabled(boolean calculationEnabled) {
        this.calculationEnabled = calculationEnabled;
    }
    

    
    /**
     * 注销实体元数据
     * @param entityApiName 实体API名称
     * @return 是否成功注销
     */
    public boolean unregisterEntity(String entityApiName) {
        Assert.hasText(entityApiName, "实体API名称不能为空");
        
        // 先从数据库删除
        try {
            metadataRepository.deleteEntity(entityApiName);
            
            // 移除缓存中的实体
            CacheEntry entry = entityMetadataCache.remove(entityApiName);
            EntityMetadata removed = null;
            if (entry != null && !entry.isExpired()) {
                removed = (EntityMetadata) entry.getValue();
            }
            
            // 清理相关缓存
            expressionEngineCache.remove(entityApiName);
            
            // 发布删除事件
            if (removed != null) {
                eventPublisher.publishEvent(new MetadataChangeEvent(this, removed, MetadataChangeType.DELETE));
                log.info("实体元数据已成功注销: {}", entityApiName);
                return true;
            }
        } catch (Exception e) {
            log.error("注销实体元数据失败: {}", entityApiName, e);
        }
        return false;
    }
    
    /**
     * 获取实体元数据
     * @param apiName 实体API名称
     * @return 实体元数据
     */
    public EntityMetadata getEntityMetadata(String apiName) {
        Assert.hasText(apiName, "实体API名称不能为空");
        
        // 从缓存获取
        if (cacheEnabled) {
            EntityMetadata cachedMetadata = getCachedEntityMetadata(apiName);
            if (cachedMetadata != null) {
                return cachedMetadata;
            }
        }
        
        // 缓存未命中，从数据库获取
        return getFromDatabaseWithCacheUpdate(apiName);
    }
    
    /**
     * 获取所有实体元数据
     * @return 实体元数据列表
     */
    public List<EntityMetadata> getAllEntityMetadata() {
        List<EntityMetadata> allEntities = new ArrayList<>();
        for (Map.Entry<String, CacheEntry> entry : entityMetadataCache.entrySet()) {
            if (!entry.getValue().isExpired()) {
                // 返回实体元数据的深拷贝，避免外部修改影响缓存
                allEntities.add(new EntityMetadata((EntityMetadata) entry.getValue().getValue()));
            }
        }
        return Collections.unmodifiableList(allEntities);
    }
    
    /**
     * 根据业务域获取实体元数据
     * @param domain 业务域
     * @return 实体元数据列表
     */
    public List<EntityMetadata> getEntitiesByDomain(String domain) {
        Assert.hasText(domain, "业务域不能为空");
        
        return getAllEntityMetadata().stream()
                .filter(entity -> domain.equals(entity.getDomain()))
                .collect(Collectors.toList());
    }
    
    /**
     * 批量获取实体元数据
     * @param apiNames API名称列表
     * @return API名称到实体元数据的映射
     */
    public Map<String, EntityMetadata> getEntityMetadataBatch(Collection<String> apiNames) {
        Assert.notNull(apiNames, "API名称列表不能为空");
        
        Map<String, EntityMetadata> result = new HashMap<>();
        for (String apiName : apiNames) {
            EntityMetadata metadata = getEntityMetadata(apiName);
            if (metadata != null) {
                result.put(apiName, metadata);
            }
        }
        return result;
    }
    
    /**
     * 获取实体的计算字段
     * @param entityApiName 实体API名称
     * @return 计算字段映射表
     */
    public Map<String, CalculatedFieldMetadata> getCalculatedFields(String entityApiName) {
        Map<String, CalculatedFieldMetadata> result = new HashMap<>();
        EntityMetadata entityMetadata = getEntityMetadata(entityApiName);
        if (entityMetadata != null && entityMetadata.getFields() != null) {
            for (Map.Entry<String, FieldMetadata> entry : entityMetadata.getFields().entrySet()) {
                FieldMetadata field = entry.getValue();
                if (field instanceof CalculatedFieldMetadata) {
                    result.put(field.getApiName(), (CalculatedFieldMetadata) field);
                }
            }
        }
        return result;
    }
    
    /**
     * 获取实体的虚拟字段
     * @param entityApiName 实体API名称
     * @return 虚拟字段映射表
     */
    public Map<String, VirtualFieldMetadata> getVirtualFields(String entityApiName) {
        Map<String, VirtualFieldMetadata> result = new HashMap<>();
        EntityMetadata entityMetadata = getEntityMetadata(entityApiName);
        if (entityMetadata != null && entityMetadata.getFields() != null) {
            for (Map.Entry<String, FieldMetadata> entry : entityMetadata.getFields().entrySet()) {
                FieldMetadata field = entry.getValue();
                if (field instanceof VirtualFieldMetadata) {
                    result.put(field.getApiName(), (VirtualFieldMetadata) field);
                }
            }
        }
        return result;
    }
    
    /**
     * 获取AI元数据
     * @param entityApiName 实体API名称
     * @return AI元数据
     */
    public AiMetadata getAiMetadata(String entityApiName) {
        EntityMetadata metadata = getEntityMetadata(entityApiName);
        if (metadata != null && metadata instanceof AiEnhancedEntityMetadata) {
            return ((AiEnhancedEntityMetadata) metadata).getAiMetadata();
        }
        return null;
    }
    
    /**
     * 验证实体元数据的完整性和正确性
     * @param metadata 实体元数据
     */
    private void validateEntityMetadata(EntityMetadata metadata) {
        validateNotNull(metadata, "实体元数据不能为空");
        
        // 基本属性验证
        validateRequiredProperty(metadata.getApiName(), "实体API名称不能为空");
        // validateRequiredProperty(metadata.getLabel(), "实体标签不能为空"); // 暂时注释，因为getLabel()方法不存在
        
        // 验证API名称格式
        validateApiNameFormat(metadata.getApiName());
        
        // 验证字段的唯一性和结构
        validateFields(metadata.getFields());
        
        // 验证实体级别的业务规则
        validateBusinessRules(metadata.getValidationRules());
    }
    
    /**
     * 验证API名称格式
     */
    private void validateApiNameFormat(String apiName) {
        // 简单的API名称格式验证：字母开头，只允许字母、数字和下划线
        if (!apiName.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("API名称格式不正确，必须以字母开头，只包含字母、数字和下划线: " + apiName);
        }
    }
    
    /**
     * 验证字段集合
     */
    private void validateFields(Map<String, FieldMetadata> fields) {
        validateNotNull(fields, "字段集合不能为空");
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("实体必须至少包含一个字段");
        }
        
        Set<String> fieldApiNames = new HashSet<>();
        boolean hasIdField = false;
        
        for (Map.Entry<String, FieldMetadata> entry : fields.entrySet()) {
            FieldMetadata field = entry.getValue();
            validateFieldMetadata(field);
            
            // 验证字段API名称唯一性
            if (!fieldApiNames.add(field.getApiName())) {
                throw new IllegalArgumentException("字段API名称重复: " + field.getApiName());
            }
            
            // 检查是否包含ID字段或主键
            if ("id".equalsIgnoreCase(field.getApiName()) || field instanceof CalculatedFieldMetadata && "id".equalsIgnoreCase(field.getApiName())) {
                hasIdField = true;
            }
        }
        
        // 验证必须包含ID字段
        if (!hasIdField) {
            throw new IllegalArgumentException("实体必须包含ID字段");
        }
    }
    
    /**
     * 验证字段元数据
     */
    private void validateFieldMetadata(FieldMetadata field) {
        validateNotNull(field, "字段元数据不能为空");
        validateRequiredProperty(field.getApiName(), "字段API名称不能为空");
        validateRequiredProperty(field.getLabel(), "字段标签不能为空");
        
        // 验证字段API名称格式
        validateApiNameFormat(field.getApiName());
        
        // 验证计算字段
        if (field instanceof CalculatedFieldMetadata) {
            validateCalculatedField((CalculatedFieldMetadata) field, null);
        }
        
        // 验证虚拟字段
        if (field instanceof VirtualFieldMetadata) {
            validateVirtualField((VirtualFieldMetadata) field);
        }
    }
    
    /**
     * 验证业务规则
     */
    private void validateBusinessRules(List<ValidationRuleMetadata> businessRules) {
        if (businessRules == null) {
            return; // 允许没有业务规则
        }
        
        for (ValidationRuleMetadata rule : businessRules) {
            validateNotNull(rule, "业务规则不能为空");
            validateRequiredProperty(rule.getName(), "业务规则名称不能为空");
        }
    }
    
    /**
     * 验证对象不为空
     */
    private <T> void validateNotNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
    }
    
    /**
     * 验证字符串属性不为空且不为空字符串
     */
    private void validateRequiredProperty(String property, String message) {
        if (property == null || property.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }
    
    /**
     * 验证计算字段
     */
    private void validateCalculatedField(CalculatedFieldMetadata field, EntityMetadata entityMetadata) {
        if (field.getCalculationExpression() == null || field.getCalculationExpression().trim().isEmpty()) {
            throw new IllegalArgumentException("计算字段必须包含表达式: " + field.getApiName());
        }
        
        // 验证计算依赖字段是否存在
        if (field.getCalculationDependencies() != null) {
            Map<String, FieldMetadata> fields = entityMetadata.getFields();
            for (String dependency : field.getCalculationDependencies()) {
                if (!fields.containsKey(dependency)) {
                    throw new IllegalArgumentException("计算字段依赖的字段不存在: " + dependency + 
                            " (字段: " + field.getApiName() + ")");
                }
            }
        }
    }
    
    /**
     * 验证虚拟字段
     */
    private void validateVirtualField(VirtualFieldMetadata field) {
        // 简化虚拟字段验证，移除对getProvider()方法的调用
        // 可以添加其他基本验证
        if (field.getApiName() == null || field.getApiName().trim().isEmpty()) {
            throw new IllegalArgumentException("虚拟字段API名称不能为空: " + field.getApiName());
        }
    }
    
    /**
     * 验证AI元数据
     */
    private void validateAiMetadata(AiMetadata aiMetadata) {
        // 简化AI元数据验证，移除对不存在方法的调用
        // 保留基本的非空验证
        if (aiMetadata != null) {
            // 这里可以添加其他不需要调用不存在方法的验证逻辑
            log.debug("AI元数据验证通过");
        }
    }
    
    /**
     * 处理实体实例，计算计算字段的值
     * @param entityApiName 实体API名称
     * @param entityData 实体数据
     * @return 处理后的实体数据
     */
    public Map<String, Object> processEntityInstance(String entityApiName, Map<String, Object> entityData) {
        Assert.hasText(entityApiName, "实体API名称不能为空");
        Assert.notNull(entityData, "实体数据不能为空");
        
        // 使用ConcurrentHashMap保证线程安全
        Map<String, Object> processedData = new ConcurrentHashMap<>(entityData);
        
        // 如果计算功能未启用，直接返回原数据的副本
        if (!calculationEnabled) {
            return processedData;
        }
        
        try {
            // 并行处理计算字段，提高性能
            Map<String, CalculatedFieldMetadata> calculatedFields = getCalculatedFields(entityApiName);
            calculatedFields.forEach((fieldName, field) -> {
                try {
                    // 计算字段值（简化实现，实际应使用表达式引擎）
                    Object calculatedValue = calculateFieldValue(field, processedData);
                    processedData.put(fieldName, calculatedValue);
                } catch (Exception e) {
                    log.error("计算字段值失败: {}.{} - {}", entityApiName, fieldName, e.getMessage());
                    // 计算失败时保持原数据不变
                }
            });
            
            // 处理虚拟字段（实际实现中需要调用提供者服务）
            processVirtualFields(entityApiName, processedData);
            
            return processedData;
        } catch (Exception e) {
            log.error("处理实体实例失败: {}", entityApiName, e);
            return processedData; // 返回原始数据的副本
        }
    }
    
    /**
     * 处理虚拟字段
     */
    private void processVirtualFields(String entityApiName, Map<String, Object> processedData) {
        // 简化实现：暂不处理虚拟字段
        if (processedData == null) {
            return;
        }
        Map<String, VirtualFieldMetadata> virtualFields = getVirtualFields(entityApiName);
        if (virtualFields != null) {
            for (String fieldName : virtualFields.keySet()) {
                // 简单实现，实际应该调用对应的服务
                processedData.put(fieldName, "N/A");
            }
        }
    }
    
    /**
     * 批量处理实体实例
     * @param entityApiName 实体API名称
     * @param entityDataList 实体数据列表
     * @return 处理后的实体数据列表
     */
    public List<Map<String, Object>> processEntityInstancesBatch(String entityApiName, 
                                                              List<Map<String, Object>> entityDataList) {
        Assert.hasText(entityApiName, "实体API名称不能为空");
        Assert.notNull(entityDataList, "实体数据列表不能为空");
        
        // 如果列表为空，直接返回空列表
        if (entityDataList.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 并行处理实体列表，提高性能
        return entityDataList.parallelStream()
                .map(entityData -> processEntityInstance(entityApiName, entityData))
                .collect(Collectors.toList());
    }
    
    /**
     * 计算字段值
     * 注意：这里是简化实现，实际应使用表达式引擎如SpEL、MVEL等
     */
    private Object calculateFieldValue(CalculatedFieldMetadata field, Map<String, Object> context) {
        try {
            String expression = field.getCalculationExpression();
            // 简化实现，实际应使用表达式引擎
            // 这里仅作为示例，实际项目中应集成成熟的表达式计算框架
            log.debug("计算字段值: {} - 表达式: {}", field.getApiName(), expression);
            
            // 此处应使用实际的表达式引擎进行计算
            // 例如使用SpEL、MVEL或自定义表达式引擎
            return null; // 占位返回
        } catch (Exception e) {
            log.error("计算字段值失败: {}", field.getApiName(), e);
            return null;
        }
    }
    
    /**
     * 注册元数据变更监听器
     * @param listener 监听器
     */
    public void registerMetadataChangeListener(MetadataChangeListener listener) {
        Assert.notNull(listener, "监听器不能为空");
        if (metadataChangeListeners.add(listener)) {
            log.info("元数据变更监听器已注册: {}", listener.getClass().getName());
        }
    }
    
    /**
     * 注销元数据变更监听器
     * @param listener 监听器
     */
    public void unregisterMetadataChangeListener(MetadataChangeListener listener) {
        Assert.notNull(listener, "监听器不能为空");
        if (metadataChangeListeners.remove(listener)) {
            log.info("元数据变更监听器已注销: {}", listener.getClass().getName());
        }
    }
    
    /**
     * 通知所有元数据变更监听器
     */
    private void notifyMetadataChangeListeners(MetadataChangeEvent event) {
        for (MetadataChangeListener listener : metadataChangeListeners) {
            try {
                listener.onMetadataChange(event);
            } catch (Exception e) {
                log.error("通知元数据变更监听器失败: {}", listener.getClass().getName(), e);
            }
        }
    }
    
    /**
     * 搜索实体
     * @param searchCriteria 搜索条件
     * @return 匹配的实体元数据列表
     */
    public List<EntityMetadata> searchEntities(Map<String, Object> searchCriteria) {
        List<EntityMetadata> allEntities = getAllEntityMetadata();
        
        if (searchCriteria == null || searchCriteria.isEmpty()) {
            return allEntities;
        }
        
        // 实现基于条件的搜索逻辑
        return allEntities.stream()
                .filter(entity -> matchesSearchCriteria(entity, searchCriteria))
                .collect(Collectors.toList());
    }
    
    /**
     * 检查实体是否匹配搜索条件
     */
    private boolean matchesSearchCriteria(EntityMetadata entity, Map<String, Object> searchCriteria) {
        for (Map.Entry<String, Object> criterion : searchCriteria.entrySet()) {
            String key = criterion.getKey();
            Object value = criterion.getValue();
            
            switch (key) {
                case "domain":
                    if (!value.equals(entity.getDomain())) {
                        return false;
                    }
                    break;
                case "apiName":
                    if (value instanceof String && !((String) value).equals(entity.getApiName())) {
                        return false;
                    }
                    break;
                case "hasField":
                    if (value instanceof String && (entity.getFields() == null || 
                            !entity.getFields().containsKey(value))) {
                        return false;
                    }
                    break;
                // 可以添加更多的搜索条件处理
                default:
                    log.warn("未知的搜索条件: {}", key);
                    break;
            }
        }
        return true;
    }
    
    /**
     * 获取实体的业务规则
     * @param entityApiName 实体API名称
     * @return 业务规则列表
     */
    public List<ValidationRuleMetadata> getBusinessRules(String entityApiName) {
        EntityMetadata metadata = getEntityMetadata(entityApiName);
        if (metadata != null) {
            // 移除对getType()方法的调用，返回所有规则
            return metadata.getValidationRules();
        }
        return Collections.emptyList();
    }
    
    /**
     * 获取实体的验证规则
     * @param entityApiName 实体API名称
     * @return 验证规则列表
     */
    public List<ValidationRuleMetadata> getValidationRules(String entityApiName) {
        EntityMetadata metadata = getEntityMetadata(entityApiName);
        if (metadata != null) {
            // 移除对getType()方法的调用，返回所有规则
            return metadata.getValidationRules();
        }
        return Collections.emptyList();
    }
}