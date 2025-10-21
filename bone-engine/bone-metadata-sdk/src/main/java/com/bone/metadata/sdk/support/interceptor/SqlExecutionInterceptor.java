package com.bone.metadata.sdk.support.interceptor;

import com.bone.metadata.sdk.sql.executor.NestedMapSqlParameterSource;
import com.bone.metadata.sdk.support.config.MetadataSdkProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL执行拦截器：监控和记录所有SQL执行，包括执行时间、参数和异常情况。
 * 提供性能监控、慢查询告警、敏感信息保护等功能。
 */
@Aspect
@Order(1) // 设置较低优先级确保先于其他拦截器执行
public class SqlExecutionInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(SqlExecutionInterceptor.class);
    
    // 敏感参数键集合，用于日志掩码
    private static final Set<String> SENSITIVE_PARAM_KEYS = new HashSet<>();
    
    // 缓存常用SQL字符串，减少重复字符串对象创建
    private static final Map<String, String> SQL_STRING_CACHE = new ConcurrentHashMap<>(1000);

    /**
     * 全局慢查询阈值（毫秒）
     */
    private final long slowQueryThreshold;

    static {
        // 初始化敏感参数键集合
        SENSITIVE_PARAM_KEYS.add("password");
        SENSITIVE_PARAM_KEYS.add("passwd");
        SENSITIVE_PARAM_KEYS.add("pwd");
        SENSITIVE_PARAM_KEYS.add("token");
        SENSITIVE_PARAM_KEYS.add("secret");
        SENSITIVE_PARAM_KEYS.add("key");
        SENSITIVE_PARAM_KEYS.add("salt");
    }

    public SqlExecutionInterceptor(MetadataSdkProperties props) {
        this.slowQueryThreshold = props.getSlowQueryThreshold();
    }

    /**
     * 拦截 NamedParameterJdbcOperations 的核心方法
     */
    @Pointcut("execution(* org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations.query*(..))"
            + " || execution(* org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations.update(..))"
            + " || execution(* org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations.batchUpdate(..))")
    public void jdbcOperations() {
    }

    /**
     * 环绕通知：记录SQL执行信息，测量执行时间，处理异常。
     */
    @Around("jdbcOperations()")
    public Object aroundJdbcExecution(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();
        String sql = extractSql(args);
        Map<String, Object> params = extractParams(args);
        String methodName = pjp.getSignature().getName();

        // 对敏感参数进行掩码处理
        Map<String, Object> maskedParams = maskSensitiveParameters(params);
        
        // 执行SQL并计时
        long start = System.nanoTime();
        Object result = null;
        boolean success = true;
        
        try {
            result = pjp.proceed();
            return result;
        } catch (Exception e) {
            success = false;
            log.error("[SQL-ERROR] Method: {} failed with: {}. SQL: {}. Params: {}", 
                    methodName, e.getMessage(), sql, maskedParams, e);
            throw e; // 重新抛出异常，保留原始异常链
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            
            // 根据执行时间和结果状态选择日志级别
            if (success) {
                if (elapsedMs > slowQueryThreshold) {
                    log.warn("[SLOW-SQL] {}ms > {}ms | Method: {} | SQL: {} | Params: {}",
                            elapsedMs, slowQueryThreshold, methodName, sql, maskedParams);
                } else if (log.isDebugEnabled()) {
                    log.debug("[JDBC] {}ms | Method: {} | SQL: {} | Params: {}",
                            elapsedMs, methodName, sql, maskedParams);
                }
            }
            
            // 可以在这里添加性能统计收集逻辑，如发送到监控系统
        }
    }

    /**
     * 提取SQL语句并标准化
     */
    private String extractSql(Object[] args) {
        if (args.length > 0 && args[0] instanceof String raw) {
            // 使用缓存减少重复字符串处理
            return SQL_STRING_CACHE.computeIfAbsent(raw, 
                    k -> k.trim().replaceAll("\\s+", " "));
        }
        return "<unknown-sql>";
    }

    /**
     * 优化参数提取：重点适配 NestedMapSqlParameterSource + 深度解析嵌套对象
     */
    private Map<String, Object> extractParams(Object[] args) {
        if (args.length <= 1) {
            return Collections.emptyMap();
        }

        Object paramObj = args[1];
        // 1. 处理 Map 类型参数（安全转换）
        if (paramObj instanceof Map) {
            return deepResolveNestedParams(convertToTypedMap((Map<?, ?>) paramObj));
        }

        // 2. 处理 SqlParameterSource 子类（分类型适配）
        if (paramObj instanceof SqlParameterSource source) {
            // 2.1 适配自定义的 NestedMapSqlParameterSource
            if (source instanceof NestedMapSqlParameterSource nestedSource) {
                return extractNestedMapParams(nestedSource);
            }
            // 2.2 适配 Spring 内置的 MapSqlParameterSource
            if (source instanceof MapSqlParameterSource mapSource) {
                return deepResolveNestedParams(mapSource.getValues());
            }
            // 2.3 适配 Spring 内置的 BeanPropertySqlParameterSource
            if (source instanceof BeanPropertySqlParameterSource beanSource) {
                return extractBeanPropertySourceParams(beanSource);
            }
            // 2.4 其他未知 SqlParameterSource（容错处理）
            log.debug("未适配的 SqlParameterSource 类型：{}", source.getClass().getName());
            return Collections.emptyMap();
        }

        // 3. 处理普通 JavaBean 参数（直接解析属性）
        return extractBeanProperties(paramObj);
    }

    /**
     * 将 Map<?, ?> 安全转换为 Map<String, Object>
     */
    private Map<String, Object> convertToTypedMap(Map<?, ?> rawMap) {
        Map<String, Object> typedMap = new HashMap<>();
        if (rawMap == null) {
            return typedMap;
        }

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            try {
                // 键必须是 String 类型
                String key = (String) entry.getKey();
                // 值可以是任意类型，统一转为 Object
                Object value = entry.getValue();
                typedMap.put(key, value);
            } catch (ClassCastException e) {
                log.debug("参数键不是 String 类型，跳过: {}", entry.getKey());
            }
        }
        return typedMap;
    }

    /**
     * 提取 NestedMapSqlParameterSource 的参数：反射获取其内部 paramMap
     */
    private Map<String, Object> extractNestedMapParams(NestedMapSqlParameterSource nestedSource) {
        try {
            // 反射获取 NestedMapSqlParameterSource 的私有字段 paramMap
            Field paramMapField = NestedMapSqlParameterSource.class.getDeclaredField("paramMap");
            paramMapField.setAccessible(true);
            Map<?, ?> rawMap = (Map<?, ?>) paramMapField.get(nestedSource);

            // 安全转换为 Map<String, Object>
            Map<String, Object> paramMap = convertToTypedMap(rawMap);

            // 深度解析 paramMap 中的嵌套对象（如 request）
            return deepResolveNestedParams(paramMap);
        } catch (Exception e) {
            log.debug("提取 NestedMapSqlParameterSource 参数失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 提取 BeanPropertySqlParameterSource 的参数：反射获取原始 Bean 并解析属性
     */
    private Map<String, Object> extractBeanPropertySourceParams(BeanPropertySqlParameterSource beanSource) {
        try {
            Field beanField = BeanPropertySqlParameterSource.class.getDeclaredField("bean");
            beanField.setAccessible(true);
            Object bean = beanField.get(beanSource);
            return extractBeanProperties(bean);
        } catch (Exception e) {
            log.debug("提取 BeanPropertySqlParameterSource 参数失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 深度解析嵌套参数（如 paramMap 中的 request 对象）
     * 例：将 {request: UserSearchRequest(pageSize=10, pageNumber=1)} 解析为 {request.pageSize:10, request.pageNumber:1}
     */
    private Map<String, Object> deepResolveNestedParams(Map<String, Object> paramMap) {
        Map<String, Object> resolvedParams = new HashMap<>();
        for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // 跳过系统参数和非业务参数
            if ("_repoClass".equals(key) || key.startsWith("_") || key.contains("class")) {
                continue;
            }

            // 如果值是复杂对象（非基本类型/字符串/集合），则解析其属性
            if (value != null && !isBasicType(value.getClass())) {
                Map<String, Object> nestedProps = extractBeanProperties(value);
                // 拼接嵌套键（如 request.pageSize）
                nestedProps.forEach((nestedKey, nestedValue) ->
                        resolvedParams.put(key + "." + nestedKey, nestedValue)
                );
            } else {
                resolvedParams.put(key, value);
            }
        }
        return resolvedParams;
    }

    /**
     * 提取 JavaBean 的属性值（跳过 class 字段）
     */
    private Map<String, Object> extractBeanProperties(Object bean) {
        if (bean == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> properties = new HashMap<>();
        BeanWrapper beanWrapper = new BeanWrapperImpl(bean);
        for (PropertyDescriptor pd : beanWrapper.getPropertyDescriptors()) {
            String propName = pd.getName();
            if ("class".equals(propName)) {
                continue;
            }

            try {
                Object propValue = beanWrapper.getPropertyValue(propName);
                properties.put(propName, propValue);
            } catch (Exception e) {
                log.debug("获取属性 [{}] 值失败", propName);
                properties.put(propName, "<无法访问>");
            }
        }
        return properties;
    }

    /**
     * 判断是否为基本类型（含包装类、字符串、集合）
     */
    private boolean isBasicType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz == String.class
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class == clazz
                || Collection.class.isAssignableFrom(clazz)
                || Map.class.isAssignableFrom(clazz);
    }
    
    /**
     * 掩码敏感参数，保护密码、token等敏感信息
     */
    private Map<String, Object> maskSensitiveParameters(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Map<String, Object> maskedParams = new HashMap<>(params.size());
        
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // 检查是否为敏感参数
            String lowerKey = key.toLowerCase();
            boolean isSensitive = false;
            
            for (String sensitiveKey : SENSITIVE_PARAM_KEYS) {
                if (lowerKey.contains(sensitiveKey)) {
                    isSensitive = true;
                    break;
                }
            }
            
            maskedParams.put(key, isSensitive ? "***masked***" : value);
        }
        
        return maskedParams;
    }
}