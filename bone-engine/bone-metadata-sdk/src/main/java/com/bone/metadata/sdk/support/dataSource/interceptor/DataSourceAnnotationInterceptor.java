package com.bone.metadata.sdk.support.dataSource.interceptor;

import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;
import com.bone.metadata.sdk.support.dataSource.annotation.DS;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * 数据源注解拦截器
 * 拦截带有@DS注解的方法或类，并根据注解的值切换数据源
 */
@Aspect
@Component
@Order(-1) // 确保在事务拦截器之前执行
public class DataSourceAnnotationInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(DataSourceAnnotationInterceptor.class);
    
    /**
     * 拦截带有@DS注解的方法或类
     * @param joinPoint 连接点
     * @param ds 数据源注解（从方法上获取）
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    @Around("@annotation(ds) || @within(ds)")
    public Object around(ProceedingJoinPoint joinPoint, DS ds) throws Throwable {
        // 如果方法上没有DS注解，尝试从类上获取
        if (ds == null) {
            Class<?> targetClass = joinPoint.getTarget().getClass();
            ds = AnnotationUtils.findAnnotation(targetClass, DS.class);
        }
        
        // 如果类上也没有DS注解，尝试从方法上获取（通过反射）
        if (ds == null) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            ds = AnnotationUtils.findAnnotation(method, DS.class);
        }
        
        String dataSourceName = "master"; // 默认使用master数据源
        if (ds != null) {
            dataSourceName = ds.value();
        }
        
        boolean hasPrevious = DataSourceContextHolder.hasDataSource();
        String previousDataSource = null;
        
        try {
            if (log.isDebugEnabled()) {
                log.debug("Switching to data source: {}", dataSourceName);
            }
            previousDataSource = DataSourceContextHolder.setDataSource(dataSourceName);
            return joinPoint.proceed();
        } finally {
            // 恢复数据源上下文
            if (log.isDebugEnabled()) {
                log.debug("Restoring data source context, previous: {}", previousDataSource);
            }
            
            // 清理当前设置的数据源
            DataSourceContextHolder.clearDataSource();
            
            // 如果之前有数据源，确保不会被清除（支持嵌套）
            if (!hasPrevious && DataSourceContextHolder.hasDataSource()) {
                DataSourceContextHolder.clearAll();
            }
        }
    }
}