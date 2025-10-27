package com.bone.core.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 集合工具类
 * 提供统一的集合操作方法，避免代码重复
 */
public final class CollectionUtils {

    private CollectionUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 检查集合是否为空
     * @param collection 待检查的集合
     * @return 如果集合为null或为空则返回true
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }
    
    /**
     * 检查集合是否不为空
     * @param collection 待检查的集合
     * @return 如果集合不为null且不为空则返回true
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }
    
    /**
     * 检查Map是否为空
     * @param map 待检查的Map
     * @return 如果Map为null或为空则返回true
     * @deprecated 使用 {@link MapUtils#isEmpty(Map)} 代替
     */
    @Deprecated
    public static boolean isEmpty(Map<?, ?> map) {
        return MapUtils.isEmpty(map);
    }
    
    /**
     * 检查Map是否不为空
     * @param map 待检查的Map
     * @return 如果Map不为null且不为空则返回true
     * @deprecated 使用 {@link MapUtils#isNotEmpty(Map)} 代替
     */
    @Deprecated
    public static boolean isNotEmpty(Map<?, ?> map) {
        return MapUtils.isNotEmpty(map);
    }
    
    /**
     * 深拷贝Map对象
     * @param source 源Map
     * @return 深拷贝后的Map
     * @deprecated 使用 {@link MapUtils#deepCopyMap(Map)} 代替
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> deepCopyMap(Map<K, V> source) {
        return MapUtils.deepCopyMap(source);
    }

    /**
     * 深拷贝List对象
     * @param source 源List
     * @return 深拷贝后的List
     */
    @SuppressWarnings("unchecked")
    public static <E> List<E> deepCopyList(List<E> source) {
        if (source == null) {
            return null;
        }
        
        List<E> copy = new ArrayList<>(source.size());
        
        for (E element : source) {
            if (element instanceof Map) {
                copy.add((E) deepCopyMap((Map<?, ?>) element));
            } else if (element instanceof List) {
                copy.add((E) deepCopyList((List<?>) element));
            } else {
                // 对于基本类型和不可变对象，直接使用
                copy.add(element);
            }
        }
        
        return copy;
    }

    /**
     * 创建线程安全的ConcurrentHashMap
     * @param initialCapacity 初始容量
     * @return ConcurrentHashMap实例
     * @deprecated 使用 {@link MapUtils#createConcurrentHashMap(int)} 代替
     */
    @Deprecated
    public static <K, V> Map<K, V> createConcurrentHashMap(int initialCapacity) {
        return MapUtils.createConcurrentHashMap(initialCapacity);
    }
    
    /**
     * 创建一个空的不可变集合
     * @return 空的不可变集合
     */
    public static <T> List<T> emptyList() {
        return Collections.emptyList();
    }
    
    /**
     * 创建一个空的不可变Map
     * @return 空的不可变Map
     * @deprecated 使用 {@link MapUtils#emptyMap()} 代替
     */
    @Deprecated
    public static <K, V> Map<K, V> emptyMap() {
        return MapUtils.emptyMap();
    }
    
    /**
     * 合并两个集合
     * @param first 第一个集合
     * @param second 第二个集合
     * @return 合并后的新集合
     */
    public static <T> List<T> merge(List<T> first, List<T> second) {
        List<T> result = new ArrayList<>();
        if (first != null) {
            result.addAll(first);
        }
        if (second != null) {
            result.addAll(second);
        }
        return result;
    }
    
    /**
     * 获取Map中的第一个值
     * @param map Map对象
     * @return 第一个值或null
     * @deprecated 使用 {@link MapUtils#getFirstValue(Map)} 代替
     */
    @Deprecated
    public static <K, V> V getFirstValue(Map<K, V> map) {
        return MapUtils.getFirstValue(map);
    }
}