package com.bone.core.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map工具类
 * 提供统一的Map操作方法，避免代码重复
 * <p>
 * 包含Map创建、操作、获取值等通用方法
 * </p>
 */
public final class MapUtils {

    private MapUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 检查Map是否为空
     * @param map 待检查的Map
     * @return 如果Map为null或为空则返回true
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }
    
    /**
     * 检查Map是否不为空
     * @param map 待检查的Map
     * @return 如果Map不为null且不为空则返回true
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    /**
     * 安全地从Map中获取String值
     * @param map Map对象
     * @param key 键名
     * @param defaultValue 默认值
     * @return 获取到的值或默认值
     */
    public static String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    /**
     * 安全地从Map中获取Boolean值
     * @param map Map对象
     * @param key 键名
     * @param defaultValue 默认值
     * @return 获取到的值或默认值
     */
    public static Boolean getBooleanValue(Map<String, Object> map, String key, Boolean defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    /**
     * 安全地从Map中获取Integer值
     * @param map Map对象
     * @param key 键名
     * @return 获取到的值或null
     */
    public static Integer getIntegerValue(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 深拷贝Map对象
     * @param source 源Map
     * @return 深拷贝后的Map
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> deepCopyMap(Map<K, V> source) {
        if (source == null) {
            return null;
        }
        
        Map<K, V> copy = source instanceof LinkedHashMap ? 
                new LinkedHashMap<>(source.size()) : 
                new HashMap<>(source.size());
        
        for (Map.Entry<K, V> entry : source.entrySet()) {
            V value = entry.getValue();
            if (value instanceof Map) {
                copy.put(entry.getKey(), (V) deepCopyMap((Map<?, ?>) value));
            } else if (value instanceof java.util.List) {
                copy.put(entry.getKey(), (V) CollectionUtils.deepCopyList((java.util.List<?>) value));
            } else {
                // 对于基本类型和不可变对象，直接使用
                copy.put(entry.getKey(), value);
            }
        }
        
        return copy;
    }

    /**
     * 创建线程安全的ConcurrentHashMap
     * @param initialCapacity 初始容量
     * @return ConcurrentHashMap实例
     */
    public static <K, V> Map<K, V> createConcurrentHashMap(int initialCapacity) {
        return new ConcurrentHashMap<>(initialCapacity);
    }

    /**
     * 创建一个空的不可变Map
     * @return 空的不可变Map
     */
    public static <K, V> Map<K, V> emptyMap() {
        return java.util.Collections.emptyMap();
    }

    /**
     * 获取Map中的第一个值
     * @param map Map对象
     * @return 第一个值或null
     */
    public static <K, V> V getFirstValue(Map<K, V> map) {
        if (isEmpty(map)) {
            return null;
        }
        return map.values().iterator().next();
    }
}