package com.bone.smartmeta.engine.util;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;
import java.util.Objects;

/**
 * 通用工具类
 * 提供常用的工具方法，避免代码重复
 * <p>
 * <strong>已废弃：</strong>请使用bone-core中的工具类替代
 * </p>
 */
@Deprecated
public final class CommonUtils {

    private CommonUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 安全地从Map中获取String值
     * @param map Map对象
     * @param key 键名
     * @param defaultValue 默认值
     * @return 获取到的值或默认值
     * @deprecated 请使用 Spring框架的工具类
     */
    @Deprecated
    public static String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 安全地从Map中获取Boolean值
     * @param map Map对象
     * @param key 键名
     * @param defaultValue 默认值
     * @return 获取到的值或默认值
     * @deprecated 请使用 Spring框架的工具类
     */
    @Deprecated
    public static Boolean getBooleanValue(Map<String, Object> map, String key, Boolean defaultValue) {
        if (map == null || key == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    /**
     * 安全地从Map中获取Integer值
     * @param map Map对象
     * @param key 键名
     * @return 获取到的值或null
     * @deprecated 请使用 Spring框架的工具类
     */
    @Deprecated
    public static Integer getIntegerValue(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
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
     * @deprecated 请使用 Spring框架的工具类
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> deepCopyMap(Map<K, V> source) {
        if (source == null) {
            return null;
        }
        Map<K, V> copy = new HashMap<>();
        for (Map.Entry<K, V> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    /**
     * 深拷贝List对象
     * @param source 源List
     * @return 深拷贝后的List
     * @deprecated 请使用 Spring框架的工具类
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static <E> List<E> deepCopyList(List<E> source) {
        if (source == null) {
            return null;
        }
        List<E> copy = new ArrayList<>();
        copy.addAll(source);
        return copy;
    }

    /**
     * 创建线程安全的ConcurrentHashMap
     * @param initialCapacity 初始容量
     * @return ConcurrentHashMap实例
     * @deprecated 请使用 Spring框架的工具类
     */
    @Deprecated
    public static <K, V> Map<K, V> createConcurrentHashMap(int initialCapacity) {
        return new ConcurrentHashMap<>(initialCapacity);
    }

    /**
     * 验证字符串不为空
     * @param value 待验证的值
     * @param message 错误消息
     * @throws IllegalArgumentException 当值为空时抛出
     */
    public static void validateNotEmpty(String value, String message) {
        Assert.hasText(value, message);
    }

    /**
     * 验证对象不为空
     * @param object 待验证的对象
     * @param message 错误消息
     * @throws IllegalArgumentException 当对象为空时抛出
     */
    public static void validateNotNull(Object object, String message) {
        Assert.notNull(object, message);
    }
    
    /**
     * 检查对象是否为空
     * @param obj 待检查的对象
     * @return 如果对象为null或空集合/空字符串则返回true
     * @deprecated 请使用 {@link ObjectUtils#isEmpty(Object)}
     */
    @Deprecated
    public static boolean isEmpty(Object obj) {
        return ObjectUtils.isEmpty(obj);
    }
    
    /**
     * 检查对象是否不为空
     * @param obj 待检查的对象
     * @return 如果对象不为null且不为空集合/空字符串则返回true
     * @deprecated 请使用 Spring框架的工具类
     */
    @Deprecated
    public static boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }
    
    /**
     * 比较两个对象是否相等
     * @param obj1 第一个对象
     * @param obj2 第二个对象
     * @return 如果两个对象相等则返回true
     * @deprecated 请使用 Spring框架的工具类
     */
    @Deprecated
    public static boolean equals(Object obj1, Object obj2) {
        return Objects.equals(obj1, obj2);
    }
    
    /**
     * 比较两个值的大小关系
     * @param value1 第一个值
     * @param value2 第二个值
     * @return 如果value1小于value2返回-1，如果相等返回0，如果大于返回1
     * @deprecated 请使用 Spring框架的工具类
     */
    @Deprecated
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int compare(Object value1, Object value2) {
        if (value1 == null && value2 == null) {
            return 0;
        }
        if (value1 == null) {
            return -1;
        }
        if (value2 == null) {
            return 1;
        }
        if (value1 instanceof Comparable && value2 instanceof Comparable) {
            return ((Comparable) value1).compareTo(value2);
        }
        return value1.toString().compareTo(value2.toString());
    }
    
    /**
     * 获取对象的字符串表示，避免空指针异常
     * @param obj 待转换的对象
     * @return 对象的字符串表示或空字符串
     * @deprecated 请使用 Spring框架的工具类
     */
    @Deprecated
    public static String safeToString(Object obj) {
        return obj != null ? obj.toString() : "";
    }
}