package com.bone.metadata.sdk.test.config;

import java.lang.reflect.Method;

/**
 * 简化的数据源注解拦截器
 * 移除了所有外部依赖
 */
public class DataSourceAnnotationInterceptor {
    
    /**
     * 数据源上下文持有者
     */
    private final SimpleDataSourceContextHolder contextHolder;
    
    /**
     * 简化的构造器
     */
    public DataSourceAnnotationInterceptor() {
        this.contextHolder = SimpleDataSourceContextHolder.getInstance();
    }
    
    /**
     * 模拟方法调用
     */
    public Object invoke(SimpleMethodInvocation invocation) throws Throwable {
        // 获取方法上的数据源信息
        Method method = invocation.getMethod();
        String dataSourceName = getDataSourceFromAnnotation(method);
        
        // 如果方法上没有数据源信息，尝试从类上获取
        if (dataSourceName == null) {
            dataSourceName = getDataSourceFromAnnotation(invocation.getTargetClass());
        }
        
        String currentDataSource = null;
        boolean clearDataSource = false;
        
        try {
            if (dataSourceName != null && !dataSourceName.isEmpty()) {
                // 保存当前数据源
                currentDataSource = contextHolder.getCurrentLookupKey();
                clearDataSource = true;
                
                // 切换到指定的数据源
                contextHolder.setDataSource(dataSourceName);
                System.out.println("切换到数据源: " + dataSourceName + " 方法: " + method.getName());
            }
            
            // 执行原始方法
            return invocation.proceed();
        } finally {
            // 恢复数据源
            if (clearDataSource) {
                if (currentDataSource != null) {
                    contextHolder.setDataSource(currentDataSource);
                } else {
                    contextHolder.clearDataSource();
                }
                System.out.println("恢复数据源: " + (currentDataSource != null ? currentDataSource : "默认"));
            }
        }
    }
    
    /**
     * 从方法注解获取数据源名称
     */
    private String getDataSourceFromAnnotation(Method method) {
        // 简化版本：这里只是模拟从注解获取数据源
        // 在实际实现中，这里会使用反射获取@DS注解的值
        if (method.getName().startsWith("query")) {
            return "slave";
        } else if (method.getName().startsWith("insert") || 
                  method.getName().startsWith("update") || 
                  method.getName().startsWith("delete")) {
            return "master";
        }
        return null;
    }
    
    /**
     * 从类注解获取数据源名称
     */
    private String getDataSourceFromAnnotation(Class<?> targetClass) {
        // 简化版本：这里只是模拟从注解获取数据源
        // 在实际实现中，这里会使用反射获取@DS注解的值
        return null;
    }
    
    /**
     * 简化的方法调用接口
     */
    public interface SimpleMethodInvocation {
        Method getMethod();
        Object proceed() throws Throwable;
        Object getThis();
        Class<?> getTargetClass();
    }
    
    /**
     * 简化的数据源上下文持有者
     */
    public static class SimpleDataSourceContextHolder {
        private static final SimpleDataSourceContextHolder INSTANCE = new SimpleDataSourceContextHolder();
        private static final ThreadLocal<String> CONTEXT_HOLDER = new ThreadLocal<>();
        
        private SimpleDataSourceContextHolder() {
        }
        
        public static SimpleDataSourceContextHolder getInstance() {
            return INSTANCE;
        }
        
        public void setDataSource(String dataSourceName) {
            CONTEXT_HOLDER.set(dataSourceName);
        }
        
        public String getCurrentLookupKey() {
            return CONTEXT_HOLDER.get();
        }
        
        public void clearDataSource() {
            CONTEXT_HOLDER.remove();
        }
    }
}