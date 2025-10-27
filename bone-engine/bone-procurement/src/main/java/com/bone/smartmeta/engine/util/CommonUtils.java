package com.bone.smartmeta.engine.util;

import org.springframework.util.Assert;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
            } else if (value instanceof List) {
                copy.put(entry.getKey(), (V) deepCopyList((List<?>) value));
            } else {
                // 对于基本类型和不可变对象，直接使用
                copy.put(entry.getKey(), value);
            }
        }
        
        return copy;
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
     */
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
     */
    @SuppressWarnings("rawtypes")
    public static boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        }
        
        if (obj instanceof String) {
            return ((String) obj).trim().isEmpty();
        }
        
        if (obj instanceof Collection) {
            return ((Collection) obj).isEmpty();
        }
        
        if (obj instanceof Map) {
            return ((Map) obj).isEmpty();
        }
        
        if (obj.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(obj) == 0;
        }
        
        return false;
    }
    
    /**
     * 检查对象是否不为空
     * @param obj 待检查的对象
     * @return 如果对象不为null且不为空集合/空字符串则返回true
     */
    public static boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }
    
    /**
     * 比较两个对象是否相等
     * @param obj1 第一个对象
     * @param obj2 第二个对象
     * @return 如果两个对象相等则返回true
     */
    public static boolean equals(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) {
            return true;
        }
        
        if (obj1 == null || obj2 == null) {
            return false;
        }
        
        return obj1.equals(obj2);
    }
    
    /**
     * 比较两个值的大小关系
     * @param value1 第一个值
     * @param value2 第二个值
     * @return 如果value1小于value2返回-1，如果相等返回0，如果大于返回1
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static int compare(Object value1, Object value2) {
        if (value1 == value2) return 0;
        if (value1 == null) return -1;
        if (value2 == null) return 1;
        
        // 处理相同类型的比较
        if (value1.getClass().equals(value2.getClass())) {
            if (value1 instanceof Comparable) {
                try {
                    return ((Comparable) value1).compareTo(value2);
                } catch (ClassCastException e) {
                    // 处理比较失败的情况，回退到字符串比较
                }
            }
        }
        
        // 数值类型比较
        if (value1 instanceof Number && value2 instanceof Number) {
            double d1 = ((Number) value1).doubleValue();
            double d2 = ((Number) value2).doubleValue();
            return Double.compare(d1, d2);
        }
        
        // 日期类型比较
        if (value1 instanceof Date && value2 instanceof Date) {
            return ((Date) value1).compareTo((Date) value2);
        }
        
        // 回退到字符串比较
        return value1.toString().compareTo(value2.toString());
    }
    
    /**
     * 获取对象的字符串表示，避免空指针异常
     * @param obj 待转换的对象
     * @return 对象的字符串表示或空字符串
     */
    public static String safeToString(Object obj) {
        return obj == null ? "" : obj.toString();
    }
}