package com.bone.core.util;

/**
 * 异常处理工具类
 */
public class ExceptionUtils {
    /**
     * 获取异常的详细信息
     */
    public static String getErrorMessage(Exception e) {
        if (e == null) {
            return "Unknown error";
        }
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
    
    /**
     * 处理异常并返回友好信息
     */
    public static String handleException(Exception e) {
        return getErrorMessage(e);
    }
}