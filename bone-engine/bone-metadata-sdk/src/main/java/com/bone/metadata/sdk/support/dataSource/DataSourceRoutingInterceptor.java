package com.bone.metadata.sdk.support.dataSource;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;

/**
 * 数据源路由拦截器，用于基于注解实现动态数据源切换。
 * 拦截带有@DataSource注解的方法调用，在方法执行前切换到指定的数据源，
 * 方法执行后恢复为原数据源。
 */
public class DataSourceRoutingInterceptor implements MethodInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(DataSourceRoutingInterceptor.class);
    
    private final DataSourceManager dataSourceManager;
    
    /**
     * 构造函数
     * 
     * @param dataSourceManager 数据源管理器
     */
    public DataSourceRoutingInterceptor(DataSourceManager dataSourceManager) {
        this.dataSourceManager = dataSourceManager;
    }
    
    /**
     * 拦截方法调用，实现数据源的动态切换
     * 
     * @param invocation 方法调用对象
     * @return 方法执行结果
     * @throws Throwable 方法执行过程中抛出的异常
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 保存原始数据源名称，用于方法执行后恢复
        String originalDataSourceName = dataSourceManager.getCurrentDataSourceName();
        boolean dataSourceSwitched = false;
        
        try {
            // 查找方法或类上的@DataSource注解
            DataSource annotation = findDataSourceAnnotation(invocation);
            
            if (annotation != null) {
                String targetDataSource = annotation.value();
                
                // 切换到指定的数据源
                if (dataSourceManager.switchDataSource(targetDataSource)) {
                    dataSourceSwitched = true;
                    logger.debug("已切换数据源: {}", targetDataSource);
                } else {
                    logger.warn("数据源切换失败，使用当前数据源: {}", targetDataSource);
                }
            }
            
            // 执行原始方法
            return invocation.proceed();
        } finally {
            // 方法执行完成后恢复原始数据源
            if (dataSourceSwitched && originalDataSourceName != null) {
                dataSourceManager.switchDataSource(originalDataSourceName);
                logger.debug("已恢复数据源: {}", originalDataSourceName);
            }
        }
    }
    
    /**
     * 查找方法或类上的@DataSource注解
     * 
     * @param invocation 方法调用对象
     * @return 找到的@DataSource注解，如果没有找到返回null
     */
    private DataSource findDataSourceAnnotation(MethodInvocation invocation) {
        // 先检查方法上的注解
        Method method = invocation.getMethod();
        DataSource methodAnnotation = AnnotationUtils.findAnnotation(method, DataSource.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        
        // 然后检查类上的注解
        Class<?> targetClass = invocation.getThis().getClass();
        return AnnotationUtils.findAnnotation(targetClass, DataSource.class);
    }
}