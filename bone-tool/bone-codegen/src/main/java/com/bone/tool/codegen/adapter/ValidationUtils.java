package com.bone.tool.codegen.adapter;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Map;

/**
 * 参数验证工具类
 * <p>
 * 提供统一的参数验证方法，避免在多个服务方法中重复实现验证逻辑
 * 使用标准的断言异常，便于全局异常处理器捕获并返回标准化错误响应
 */
public class ValidationUtils {
    
    /**
     * 验证参数不为null
     * 
     * @param param 参数对象
     * @param paramName 参数名称
     * @throws IllegalArgumentException 当参数为null时抛出异常
     */
    public static void requireNonNull(Object param, String paramName) {
        Assert.notNull(param, paramName + "不能为空");
    }
    
    /**
     * 验证多个参数不为null
     * 
     * @param params 参数名和参数值的映射
     * @throws IllegalArgumentException 当任意参数为null时抛出异常
     */
    public static void requireNonNulls(Map<String, Object> params) {
        requireNonNull(params, "参数映射");
        
        params.forEach((paramName, param) -> requireNonNull(param, paramName));
    }
    
    /**
     * 验证字符串不为空（null或空字符串）
     * 
     * @param param 字符串参数
     * @param paramName 参数名称
     * @throws IllegalArgumentException 当字符串为空时抛出异常
     */
    public static void requireNonEmpty(String param, String paramName) {
        if (StringUtils.isEmpty(param)) {
            throw new IllegalArgumentException(paramName + "不能为空");
        }
    }
    
    /**
     * 验证字符串不为空白（null、空字符串或只包含空白字符）
     * 
     * @param param 字符串参数
     * @param paramName 参数名称
     * @throws IllegalArgumentException 当字符串为空白时抛出异常
     */
    public static void requireNonBlank(String param, String paramName) {
        if (StringUtils.isBlank(param)) {
            throw new IllegalArgumentException(paramName + "不能为空且不能只包含空白字符");
        }
    }
    
    /**
     * 验证集合不为空（null或不包含元素）
     * 
     * @param param 集合参数
     * @param paramName 参数名称
     * @throws IllegalArgumentException 当集合为空时抛出异常
     */
    public static void requireNonEmpty(Collection<?> param, String paramName) {
        Assert.notEmpty(param, paramName + "不能为空集合");
    }
    
    /**
     * 验证映射不为空（null或不包含键值对）
     * 
     * @param param 映射参数
     * @param paramName 参数名称
     * @throws IllegalArgumentException 当映射为空时抛出异常
     */
    public static void requireNonEmpty(Map<?, ?> param, String paramName) {
        if (CollectionUtils.isEmpty(param)) {
            throw new IllegalArgumentException(paramName + "不能为空映射");
        }
    }
    
    /**
     * 验证数组不为空（null或长度为0）
     * 
     * @param param 数组参数
     * @param paramName 参数名称
     * @throws IllegalArgumentException 当数组为空时抛出异常
     */
    public static void requireNonEmpty(Object[] param, String paramName) {
        Assert.notEmpty(param, paramName + "不能为空数组");
    }
    
    /**
     * 验证数值大于0
     * 
     * @param param 数值参数
     * @param paramName 参数名称
     * @throws IllegalArgumentException 当数值不大于0时抛出异常
     */
    public static void requirePositive(Number param, String paramName) {
        requireNonNull(param, paramName);
        if (param.doubleValue() <= 0) {
            throw new IllegalArgumentException(paramName + "必须大于0");
        }
    }
    
    /**
     * 验证数值大于等于0
     * 
     * @param param 数值参数
     * @param paramName 参数名称
     * @throws IllegalArgumentException 当数值小于0时抛出异常
     */
    public static void requireNonNegative(Number param, String paramName) {
        requireNonNull(param, paramName);
        if (param.doubleValue() < 0) {
            throw new IllegalArgumentException(paramName + "不能小于0");
        }
    }
    
    /**
     * 验证条件为true
     * 
     * @param condition 条件表达式
     * @param message 错误消息
     * @throws IllegalArgumentException 当条件为false时抛出异常
     */
    public static void requireTrue(boolean condition, String message) {
        Assert.isTrue(condition, message);
    }
}