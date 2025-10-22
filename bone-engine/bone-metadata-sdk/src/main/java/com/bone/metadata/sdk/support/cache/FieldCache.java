package com.bone.metadata.sdk.support.cache;

import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bone.metadata.sdk.support.util.SqlUtil.toSnakeCase;

/**
 * 线程安全的字段缓存静态工具类
 * 采用高性能并发设计：StampedLock细粒度锁 + Caffeine缓存 + 缓存统计
 * 提供字段元数据管理、缓存预热、自动刷新和性能监控功能
 */
public final class FieldCache {
    private static final Logger log = LoggerFactory.getLogger(FieldCache.class);

    // 默认缓存配置
    private static final int DEFAULT_MAX_SIZE = 20000;
    private static final int DEFAULT_EXPIRE_HOURS = 360;

    // 使用ConcurrentHashMap保证类级加载的原子性
    private static final Map<Class<?>, StampedLock> classLocks = new ConcurrentHashMap<>();
    
    // 缓存统计指标
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);
    private static final AtomicLong loadSuccessCount = new AtomicLong(0);
    private static final AtomicLong loadFailureCount = new AtomicLong(0);

    // Caffeine缓存主体（线程安全）
    private static volatile Cache<Class<?>, Map<String, Field>> classFieldCache;

    private static volatile Cache<String, List<FieldMetadata>> metadataCache;

    static {
        initCache(DEFAULT_MAX_SIZE, DEFAULT_EXPIRE_HOURS);
    }

    // 私有构造防止实例化
    private FieldCache() {
    }

    /**
     * 初始化缓存配置（应用启动时调用）
     *
     * @param maxSize     最大缓存条目数
     * @param expireHours 过期时间（小时）
     */
    public static synchronized void initCache(int maxSize, int expireHours) {
        log.info("Initializing field cache with maxSize={}, expireHours={}", maxSize, expireHours);
        
        if (classFieldCache != null) {
            classFieldCache.invalidateAll();
        }
        classFieldCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(Duration.ofHours(expireHours))
                .recordStats()  // 启用Caffeine内部统计
                .removalListener((key, value, cause) -> {
                    if (cause == RemovalCause.EXPIRED || cause == RemovalCause.COLLECTED) {
                        log.debug("Cache entry removed for class: {}, cause: {}", key, cause);
                    }
                })
                .build();

        if (metadataCache != null) {
            metadataCache.invalidateAll();
        }
        metadataCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(Duration.ofHours(expireHours))
                .recordStats()
                .build();
        
        // 重置统计指标
        resetCacheStats();
    }

    public static List<FieldMetadata> getByCacheKey(String cacheKey) {
        List<FieldMetadata> cached = metadataCache.getIfPresent(cacheKey);
        return cached != null ? cached : List.of();
    }

    /**
     * 将字段元数据放入缓存
     */
    public static void putToCache(String cacheKey, List<FieldMetadata> metadata) {
        metadataCache.put(cacheKey, metadata);
    }

    public static void mergeFieldMetadataCache(String cacheKey, List<FieldMetadata> newList) {
        metadataCache.asMap().compute(cacheKey, (k, existing) -> {
            if (existing == null || existing.isEmpty()) {
                return newList;
            }

            Map<String, FieldMetadata> merged = existing.stream()
                    .collect(Collectors.toMap(f -> f.getName(), Function.identity()));

            for (FieldMetadata meta : newList) {
                merged.put(meta.getName(), meta);
            }

            return List.copyOf(merged.values());
        });
    }


    /**
     * 根据字段名获取字段（带缓存）
     */
    public static Field getFieldByName(Class<?> clazz, String fieldName) {
        return getCachedFields(clazz).get(fieldName);
    }

    /**
     * 根据列名获取字段（支持注解）
     */
    public static Field getFieldByColumn(Class<?> clazz, String columnName) {
        return getCachedFields(clazz).get(columnName.toLowerCase());
    }

    /**
     * 根据列名获取字段（支持注解）
     */
    public static boolean hasFieldByColumn(Class<?> clazz, String columnName) {
        Field field = getCachedFields(clazz).get(columnName.toLowerCase());
        if (field != null) return true;

        List<FieldMetadata> fieldMetadatas = getByCacheKey(MetadataSdkContext.getAppCode() + "." + clazz.getSimpleName());
        return fieldMetadatas != null && fieldMetadatas.stream().anyMatch(f -> f.getColumnName() != null && f.getColumnName().equalsIgnoreCase(columnName) || f.getName() != null && f.getName().equalsIgnoreCase(columnName));
    }


    /**
     * 获取类的字段映射（核心缓存逻辑）
     */
    public static Map<String, Field> getCachedFields(Class<?> clazz) {
        Map<String, Field> cached = classFieldCache.getIfPresent(clazz);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }
        
        cacheMisses.incrementAndGet();

        // 使用StampedLock替代ReentrantLock提高并发性能
        StampedLock lock = classLocks.computeIfAbsent(clazz, k -> new StampedLock());
        long stamp = lock.writeLock();
        try {
            // 双重检查锁定模式
            cached = classFieldCache.getIfPresent(clazz);
            if (cached == null) {
                cached = loadClassFields(clazz);
                classFieldCache.put(clazz, cached);
                loadSuccessCount.incrementAndGet();
            } else {
                // 其他线程可能已经加载完成
                cacheHits.incrementAndGet();
                cacheMisses.decrementAndGet();
            }
            return cached;
        } catch (Exception e) {
            loadFailureCount.incrementAndGet();
            log.error("Failed to load fields for class: {}", clazz.getName(), e);
            return Collections.emptyMap();
        } finally {
            lock.unlockWrite(stamp);
            // 使用tryRelease来移除锁，避免内存泄漏
            classLocks.remove(clazz, lock);
        }
    }

    /**
     * 加载类字段（线程安全加载）
     */
    private static Map<String, Field> loadClassFields(Class<?> clazz) {
        log.debug("Loading fields for class: {}", clazz.getName());
        Map<String, Field> fieldMap = new ConcurrentHashMap<>(32);

        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            Field[] declaredFields;
            try {
                declaredFields = currentClass.getDeclaredFields();
                for (Field field : declaredFields) {
                    processField(field, fieldMap);
                }
            } catch (SecurityException e) {
                log.warn("Security exception while accessing fields for class: {}", currentClass.getName(), e);
            }
            currentClass = currentClass.getSuperclass();
        }

        log.trace("Loaded {} fields for class: {}", fieldMap.size(), clazz.getName());
        return Collections.unmodifiableMap(fieldMap);
    }

    /**
     * 处理单个字段的元数据
     */
    private static void processField(Field field, Map<String, Field> fieldMap) {
        try {
            // 避免处理静态和合成字段以提高性能
            if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                return;
            }
            
            field.setAccessible(true);
            String fieldName = field.getName();

            // 字段名映射
            fieldMap.putIfAbsent(fieldName, field);

            // 列名映射 - 增加空格检查避免空字符串问题
            Column column = field.getAnnotation(Column.class);
            String columnName;
            if (column != null && column.name() != null && !column.name().trim().isEmpty()) {
                columnName = column.name().trim().toLowerCase();
            } else {
                columnName = toSnakeCase(field.getName()).toLowerCase();
            }
            fieldMap.putIfAbsent(columnName, field);

            if (log.isTraceEnabled()) {
                log.trace("Cached field: {} -> {}", fieldName, columnName);
            }
        } catch (Exception e) {
            log.error("Error processing field: {}", field.getName(), e);
        }
    }
    
    /**
     * 缓存预热方法 - 预加载多个类的字段信息
     * @param classes 需要预加载的类列表
     */
    public static void preloadClasses(List<Class<?>> classes) {
        if (classes == null || classes.isEmpty()) {
            return;
        }
        
        log.info("Preloading fields for {} classes", classes.size());
        long startTime = System.currentTimeMillis();
        
        classes.parallelStream().forEach(clazz -> {
            try {
                getCachedFields(clazz);
            } catch (Exception e) {
                log.error("Error during preloading fields for class: {}", clazz.getName(), e);
            }
        });
        
        log.info("Preloading completed in {} ms", System.currentTimeMillis() - startTime);
    }
    
    /**
     * 手动刷新指定类的字段缓存
     * @param clazz 需要刷新的类
     */
    public static void refreshClassCache(Class<?> clazz) {
        if (clazz == null) {
            return;
        }
        
        log.debug("Refreshing field cache for class: {}", clazz.getName());
        classFieldCache.invalidate(clazz);
        getCachedFields(clazz); // 立即重新加载
    }
    
    /**
     * 清除所有缓存
     */
    public static synchronized void clearAllCache() {
        log.info("Clearing all field cache");
        classFieldCache.invalidateAll();
        metadataCache.invalidateAll();
        resetCacheStats();
    }
    
    /**
     * 获取缓存统计信息
     */
    public static Map<String, Long> getCacheStats() {
        Map<String, Long> stats = new ConcurrentHashMap<>();
        stats.put("hits", cacheHits.get());
        stats.put("misses", cacheMisses.get());
        stats.put("loadSuccess", loadSuccessCount.get());
        stats.put("loadFailure", loadFailureCount.get());
        stats.put("totalOperations", cacheHits.get() + cacheMisses.get());
        
        // 添加Caffeine内置统计
        if (classFieldCache != null) {
            com.github.benmanes.caffeine.cache.stats.CacheStats caffeineStats = classFieldCache.stats();
            stats.put("evictionCount", caffeineStats.evictionCount());
            stats.put("evictionWeight", caffeineStats.evictionWeight());
        }
        
        return Collections.unmodifiableMap(stats);
    }
    
    /**
     * 重置缓存统计指标
     */
    private static void resetCacheStats() {
        cacheHits.set(0);
        cacheMisses.set(0);
        loadSuccessCount.set(0);
        loadFailureCount.set(0);
    }
}