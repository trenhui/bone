package com.bone.metadata.sdk.support.dataSource;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 数据源切换拦截器
 * 拦截带有@DS注解的方法，自动切换数据源
 */
public class DataSourceAnnotationInterceptor implements MethodInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(DataSourceAnnotationInterceptor.class);
    
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 获取当前调用的方法和目标类
        Method method = invocation.getMethod();
        Class<?> targetClass = invocation.getThis().getClass();
        
        // 获取方法上的@DS注解
        DS methodAnnotation = getAnnotation(method, DS.class);
        
        // 获取类上的@DS注解
        DS classAnnotation = getAnnotation(targetClass, DS.class);
        
        // 确定最终使用的数据源名称
        String targetDataSource = resolveDataSource(methodAnnotation, classAnnotation);
        
        // 如果需要切换数据源
        if (StringUtils.hasText(targetDataSource)) {
            // 记录切换信息
            log.debug("Switching datasource to '{}' for method: {}.{}", 
                    targetDataSource, targetClass.getSimpleName(), method.getName());
            
            // 保存原始数据源上下文栈深度，用于判断是否需要清理
            int originalDepth = DataSourceContextHolder.getContextStackDepth();
            boolean forceSwitch = methodAnnotation != null && methodAnnotation.force();
            
            try {
                // 设置目标数据源
                DataSourceContextHolder.setDataSource(targetDataSource);
                
                // 执行原始方法
                return invocation.proceed();
            } catch (Throwable e) {
                log.error("Error occurred while invoking method with datasource: {}", targetDataSource, e);
                throw e;
            } finally {
                // 清理数据源上下文
                cleanupDataSourceContext(originalDepth);
                log.debug("Restored datasource context for method: {}.{}", 
                        targetClass.getSimpleName(), method.getName());
            }
        } else {
            // 不需要切换数据源，直接执行原始方法
            return invocation.proceed();
        }
    }
    
    /**
     * 解析最终使用的数据源名称
     * 方法级别注解优先级高于类级别注解
     * @param methodAnnotation 方法上的注解
     * @param classAnnotation 类上的注解
     * @return 数据源名称
     */
    private String resolveDataSource(DS methodAnnotation, DS classAnnotation) {
        // 方法级别注解优先
        if (methodAnnotation != null) {
            return methodAnnotation.value();
        }
        
        // 其次使用类级别注解
        if (classAnnotation != null) {
            return classAnnotation.value();
        }
        
        // 默认返回null，表示使用当前数据源
        return null;
    }
    
    /**
     * 获取注解实例（考虑继承关系）
     * @param element 方法或类
     * @param annotationType 注解类型
     * @return 注解实例
     */
    private <A extends Annotation> A getAnnotation(Object element, Class<A> annotationType) {
        if (element instanceof Method) {
            return AnnotationUtils.findAnnotation((Method) element, annotationType);
        } else if (element instanceof Class) {
            return AnnotationUtils.findAnnotation((Class<?>) element, annotationType);
        }
        return null;
    }
    
    /**
     * 清理数据源上下文
     * 确保上下文栈恢复到原始深度，避免内存泄漏
     * @param originalDepth 原始深度
     */
    private void cleanupDataSourceContext(int originalDepth) {
        int currentDepth = DataSourceContextHolder.getContextStackDepth();
        
        // 如果当前深度大于原始深度，需要清理多余的上下文
        while (currentDepth > originalDepth) {
            DataSourceContextHolder.clearDataSource();
            currentDepth = DataSourceContextHolder.getContextStackDepth();
        }
        
        // 如果原始深度为0，且清理后仍然有上下文，说明出现了异常情况，强制清理
        if (originalDepth == 0 && DataSourceContextHolder.hasDataSource()) {
            log.warn("Unexpected datasource context detected, cleaning all contexts");
            DataSourceContextHolder.clearAll();
        }
    }
}