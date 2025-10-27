package com.bone.core.util;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * 字符串工具类
 * <p>
 * <b>注意：部分通用对象操作方法已迁移到ObjectUtils、MapUtils等专用工具类</b>
 * </p>
 */
public final class StringUtils {

    private StringUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 检查对象是否为空
     * @param obj 待检查的对象
     * @return 如果对象为null或空集合/空字符串则返回true
     * @deprecated 使用 {@link ObjectUtils#isEmpty(Object)} 代替
     */
    @Deprecated
    public static boolean isEmpty(Object obj) {
        return ObjectUtils.isEmpty(obj);
    }
    
    /**
     * 检查对象是否不为空
     * @param obj 待检查的对象
     * @return 如果对象不为null且不为空集合/空字符串则返回true
     * @deprecated 使用 {@link ObjectUtils#isNotEmpty(Object)} 代替
     */
    @Deprecated
    public static boolean isNotEmpty(Object obj) {
        return ObjectUtils.isNotEmpty(obj);
    }
    
    /**
     * 检查字符串是否有文本内容
     * @param str 待检查的字符串
     * @return 如果字符串不为null且不为空字符串则返回true
     */
    public static boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }
    
    /**
     * 获取对象的字符串表示，避免空指针异常
     * @param obj 待转换的对象
     * @return 对象的字符串表示或空字符串
     * @deprecated 使用 {@link ObjectUtils#safeToString(Object)} 代替
     */
    @Deprecated
    public static String safeToString(Object obj) {
        return ObjectUtils.safeToString(obj);
    }
    
    /**
     * 比较两个对象是否相等
     * @param obj1 第一个对象
     * @param obj2 第二个对象
     * @return 如果两个对象相等则返回true
     * @deprecated 使用 {@link ObjectUtils#equals(Object, Object)} 代替
     */
    @Deprecated
    public static boolean equals(Object obj1, Object obj2) {
        return ObjectUtils.equals(obj1, obj2);
    }
    
    /**
     * 比较两个值的大小关系
     * @param value1 第一个值
     * @param value2 第二个值
     * @return 如果value1小于value2返回-1，如果相等返回0，如果大于返回1
     * @deprecated 使用 {@link ObjectUtils#compare(Object, Object)} 代替
     */
    @Deprecated
    public static int compare(Object value1, Object value2) {
        return ObjectUtils.compare(value1, value2);
    }
    
    /**
     * 连接字符串数组
     * @param parts 字符串数组
     * @return 连接后的字符串
     */
    public static String concat(String... parts) {
        return String.join("", parts);
    }
    
    /**
     * 检查集合是否包含指定元素
     * @param collection 集合
     * @param item 要检查的元素
     * @return 如果集合包含元素则返回true
     */
    public static boolean contains(Object collection, Object item) {
        if (collection == null || item == null) return false;
        if (collection instanceof Collection<?> coll) return coll.contains(item);
        if (collection instanceof String str) return str.contains(item.toString());
        return false;
    }
    
    /**
     * 安全地从Map中获取String值
     * @param map Map对象
     * @param key 键名
     * @param defaultValue 默认值
     * @return 获取到的值或默认值
     * @deprecated 使用 {@link MapUtils#getStringValue(Map, String, String)} 代替
     */
    @Deprecated
    public static String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        return MapUtils.getStringValue(map, key, defaultValue);
    }
    
    /**
     * 安全地从Map中获取Boolean值
     * @param map Map对象
     * @param key 键名
     * @param defaultValue 默认值
     * @return 获取到的值或默认值
     * @deprecated 使用 {@link MapUtils#getBooleanValue(Map, String, Boolean)} 代替
     */
    @Deprecated
    public static Boolean getBooleanValue(Map<String, Object> map, String key, Boolean defaultValue) {
        return MapUtils.getBooleanValue(map, key, defaultValue);
    }
}