package com.bone.metadata.sdk.support.dataSource;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 数据源切换拦截器实现
 * <p>
 * 该拦截器根据方法或类上的{@link DataSourceSwitch}注解自动切换数据源。
 * 遵循方法级注解优先于类级注解的原则。
 * </p>
 */
public class DataSourceAnnotationInterceptor implements MethodInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(DataSourceAnnotationInterceptor.class);
    
    /**
     * 拦截方法调用，根据注解决定是否切换数据源
     * 
     * @param invocation 方法调用对象
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 获取当前调用的方法和目标类
        Method method = invocation.getMethod();
        Class<?> targetClass = Objects.requireNonNull(invocation.getThis()).getClass();
        
        // 解析方法和类上的注解
        DataSourceSwitch methodAnnotation = findAnnotation(method, DataSourceSwitch.class);
        DataSourceSwitch classAnnotation = findAnnotation(targetClass, DataSourceSwitch.class);
        
        // 确定目标数据源名称
        String targetDataSourceName = resolveDataSourceName(methodAnnotation, classAnnotation);
        
        // 检查是否需要切换数据源
        if (StringUtils.hasText(targetDataSourceName)) {
            // 记录数据源切换信息
            logger.debug("切换数据源至 '{}'，方法: {}.{}", 
                    targetDataSourceName, targetClass.getSimpleName(), method.getName());
            
            // 保存原始上下文栈深度，用于正确清理
            int originalStackDepth = DataSourceContextHolder.getContextStackDepth();
            boolean shouldForceSwitch = methodAnnotation != null && methodAnnotation.force();
            
            try {
                // 设置目标数据源
                DataSourceContextHolder.setDataSource(targetDataSourceName);
                
                // 继续执行原始方法调用
                return invocation.proceed();
            } catch (Throwable ex) {
                // 记录错误并保持完整上下文
                logger.error("使用数据源 '{}' 调用方法时发生错误", targetDataSourceName, ex);
                throw ex; // 重新抛出以保留原始异常
            } finally {
                // 无论方法执行结果如何，都清理数据源上下文
                restoreDataSourceContext(originalStackDepth);
                logger.debug("已恢复方法: {}.{} 的数据源上下文", 
                        targetClass.getSimpleName(), method.getName());
            }
        } else {
            // 不需要切换数据源，直接执行
            return invocation.proceed();
        }
    }
    
    /**
     * 基于注解解析最终使用的数据源名称
     * <p>
     * 方法级注解优先于类级注解。如果没有注解提供有效的数据源名称，
     * 则返回null，表示应使用当前数据源。
     * </p>
     * 
     * @param methodAnnotation 方法上的注解
     * @param classAnnotation 类上的注解
     * @return 解析后的数据源名称，如未指定则返回null
     */
    private String resolveDataSourceName(DataSourceSwitch methodAnnotation, DataSourceSwitch classAnnotation) {
        // 方法级注解优先级最高
        if (methodAnnotation != null && StringUtils.hasText(methodAnnotation.value())) {
            return methodAnnotation.value();
        }
        
        // 退回到类级注解
        if (classAnnotation != null && StringUtils.hasText(classAnnotation.value())) {
            return classAnnotation.value();
        }
        
        // 默认：未指定特定数据源
        return null;
    }
    
    /**
     * 从方法或类中检索注解实例，考虑继承关系
     * <p>
     * 此方法使用Spring的{@link AnnotationUtils}查找注解，它能正确处理
     * 注解继承和元注解。
     * </p>
     * 
     * @param <A> 要检索的注解类型
     * @param element 要检查注解的方法或类
     * @param annotationType 要检索的注解类
     * @return 注解实例（如找到），否则返回null
     */
    private <A extends Annotation> A findAnnotation(Object element, Class<A> annotationType) {
        if (element instanceof Method) {
            return AnnotationUtils.findAnnotation((Method) element, annotationType);
        } else if (element instanceof Class) {
            return AnnotationUtils.findAnnotation((Class<?>) element, annotationType);
        }
        return null;
    }
    
    /**
     * 方法调用后清理数据源上下文
     * <p>
     * 此方法确保上下文栈正确恢复到原始深度，防止内存泄漏并确保嵌套方法调用
     * 的正确行为。如果检测到意外的上下文元素，它会执行额外的清理以维护系统稳定性。
     * </p>
     * 
     * @param originalDepth 方法调用前数据源上下文栈的原始深度
     */
    private void restoreDataSourceContext(int originalDepth) {
        int currentDepth = DataSourceContextHolder.getContextStackDepth();
        
        // 如果当前深度超过原始深度，则清理额外的上下文级别
        while (currentDepth > originalDepth) {
            DataSourceContextHolder.clearDataSource();
            currentDepth = DataSourceContextHolder.getContextStackDepth();
        }
        
        // 特殊情况：如果原始深度为0但清理后仍有上下文，
        // 这表示出现了意外情况，执行额外清理
        if (originalDepth == 0 && DataSourceContextHolder.hasActiveDataSource()) {
            logger.warn("检测到意外的数据源上下文，清理所有上下文");
            DataSourceContextHolder.clearAllDataSources();
        }
    }
}