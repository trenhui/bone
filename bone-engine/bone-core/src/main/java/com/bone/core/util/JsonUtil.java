package com.bone.core.util;

/**
 * JSON处理工具类
 */
public class JsonUtil {
    /**
     * 将对象转换为JSON字符串
     */
    public static String toJson(Object obj) {
        // 简化实现，实际项目中应使用Jackson或Gson等库
        if (obj == null) {
            return "null";
        }
        return obj.toString();
    }
    
    /**
     * 解析JSON字符串为对象
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        // 简化实现，实际项目中应使用Jackson或Gson等库
        return null;
    }
}