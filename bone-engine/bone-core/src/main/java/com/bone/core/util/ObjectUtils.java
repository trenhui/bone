package com.bone.core.util;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * 对象工具类
 * 提供统一的对象操作方法，避免代码重复
 * <p>
 * 包含对象判空、比较、转换等通用操作
 * </p>
 */
public final class ObjectUtils {

    private ObjectUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 检查对象是否为空
     * @param obj 待检查的对象
     * @return 如果对象为null或空集合/空字符串则返回true
     */
    public static boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        }
        
        if (obj instanceof String str) {
            return str.trim().isEmpty();
        }
        
        if (obj instanceof Collection<?> coll) {
            return coll.isEmpty();
        }
        
        if (obj instanceof Map<?, ?> map) {
            return map.isEmpty();
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