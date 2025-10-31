package com.bone.metadata.sdk.support.cache;

import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bone.metadata.sdk.support.util.SqlUtil.toSnakeCase;

/**
 * 线程安全的字段缓存静态工具类
 * 采用双重缓存设计：类级锁控制字段加载 + Caffeine缓存结果
 */
public final class FieldCache {
    private static final Logger log = LoggerFactory.getLogger(FieldCache.class);

    // 默认缓存配置
    private static final int DEFAULT_MAX_SIZE = 20000;
    private static final int DEFAULT_EXPIRE_HOURS = 360;

    // 使用ConcurrentHashMap保证类级加载的原子性
    private static final Map<Class<?>, ReentrantLock> classLocks = new ConcurrentHashMap<>();

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
        if (classFieldCache != null) {
            classFieldCache.invalidateAll();
        }
        classFieldCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(expireHours, TimeUnit.HOURS)
                .build();

        if (metadataCache != null) {
            metadataCache.invalidateAll();
        }
        metadataCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(expireHours, TimeUnit.HOURS)
                .build();
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
                    .collect(Collectors.toMap(FieldMetadata::getName, Function.identity()));

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
        return fieldMetadatas != null && fieldMetadatas.stream().anyMatch(f -> f.getColumnName().equalsIgnoreCase(columnName)|| f.getName().equalsIgnoreCase(columnName));
    }


    /**
     * 获取类的字段映射（核心缓存逻辑）
     */
    public static Map<String, Field> getCachedFields(Class<?> clazz) {
        Map<String, Field> cached = classFieldCache.getIfPresent(clazz);
        if (cached != null) return cached;

        // 使用类级锁保证并发安全
        ReentrantLock lock = classLocks.computeIfAbsent(clazz, k -> new ReentrantLock());
        lock.lock();
        try {
            // 双重检查锁定模式
            cached = classFieldCache.getIfPresent(clazz);
            if (cached == null) {
                cached = loadClassFields(clazz);
                classFieldCache.put(clazz, cached);
            }
            return cached;
        } finally {
            lock.unlock();
            classLocks.remove(clazz);
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
            for (Field field : currentClass.getDeclaredFields()) {
                processField(field, fieldMap);
            }
            currentClass = currentClass.getSuperclass();
        }

        return Collections.unmodifiableMap(fieldMap);
    }

    /**
     * 处理单个字段的元数据
     */
    private static void processField(Field field, Map<String, Field> fieldMap) {
        try {
            field.setAccessible(true);
            String fieldName = field.getName();

            // 字段名映射
            fieldMap.putIfAbsent(fieldName, field);

            // 列名映射
            Column column = field.getAnnotation(Column.class);
            String columnName = (column != null && !column.name().isEmpty())
                    ? column.name().toLowerCase()
                    : toSnakeCase(field.getName()).toLowerCase();
            fieldMap.putIfAbsent(columnName, field);

           // log.trace("Cached field: {} -> {}", fieldName, columnName);
        } catch (Exception e) {
            log.error("Error processing field: {}", field.getName(), e);
        }
    }
}