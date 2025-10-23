package com.bone.metadata.sdk.test.config;

import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;

/**
 * 数据源注解拦截器 - 用于测试的辅助类
 */
public class DataSourceAnnotationInterceptor implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DataSourceAnnotationInterceptor.class);
    
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 获取方法上的@DS注解
        Method method = invocation.getMethod();
        com.bone.metadata.sdk.support.dataSource.annotation.DS ds = AnnotationUtils.findAnnotation(method, 
                com.bone.metadata.sdk.support.dataSource.annotation.DS.class);
        
        // 如果方法上没有注解，尝试获取类上的注解
        if (ds == null) {
            ds = AnnotationUtils.findAnnotation(invocation.getThis().getClass(), 
                    com.bone.metadata.sdk.support.dataSource.annotation.DS.class);
        }
        
        String currentDataSource = null;
        boolean clearDataSource = false;
        
        try {
            if (ds != null) {
                // 保存当前数据源
                currentDataSource = DataSourceContextHolder.getCurrentLookupKey();
                clearDataSource = true;
                
                // 切换到注解指定的数据源
                String dataSourceName = ds.value();
                DataSourceContextHolder.setDataSource(dataSourceName);
                log.debug("Switched to datasource: {} for method: {}", 
                        dataSourceName, method.getName());
            }
            
            // 执行原始方法
            return invocation.proceed();
        } finally {
            // 恢复数据源上下文
            if (clearDataSource) {
                if (currentDataSource != null) {
                    DataSourceContextHolder.setDataSource(currentDataSource);
                } else {
                    DataSourceContextHolder.clearDataSource();
                }
            }
        }
    }
}