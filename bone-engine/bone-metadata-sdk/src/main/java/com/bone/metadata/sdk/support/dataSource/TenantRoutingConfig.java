package com.bone.metadata.sdk.support.dataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 多租户数据源自动路由配置
 * 提供不依赖Servlet API的多租户数据源切换工具类
 * 用户可以根据自己的框架适配这个工具类
 */
public class TenantRoutingConfig {

    /**
     * 多租户数据源工具类
     * 不直接依赖Servlet API，提供通用的租户数据源切换功能
     */
    @Component
    public static class TenantDataSourceHelper {
        private static final Logger log = LoggerFactory.getLogger(TenantDataSourceHelper.class);
        private static final String TENANT_DATASOURCE_PREFIX = "tenant_";

        /**
         * 切换到指定租户的数据源
         * @param tenantId 租户ID
         * @return 是否成功切换数据源
         */
        public boolean switchToTenantDataSource(String tenantId) {
            if (tenantId != null && !tenantId.trim().isEmpty()) {
                String datasourceName = TENANT_DATASOURCE_PREFIX + tenantId.trim();
                try {
                    DataSourceContextHolder.setDataSource(datasourceName);
                    log.debug("已切换到租户数据源: {}", datasourceName);
                    return true;
                } catch (Exception e) {
                    log.error("切换到租户数据源失败: {}", datasourceName, e);
                    // 数据源不存在时不影响业务继续执行，使用默认数据源
                    return false;
                }
            }
            return false;
        }

        /**
         * 从租户数据源切换回默认数据源
         */
        public void resetTenantDataSource() {
            // 确保清理线程上下文中的数据源信息
            if (DataSourceContextHolder.hasActiveDataSource()) {
                String cleared = DataSourceContextHolder.clearDataSource();
                log.debug("已重置租户数据源: {}", cleared);
            }
        }
        
        /**
         * 执行租户相关的操作
         * @param tenantId 租户ID
         * @param action 要执行的操作
         */
        public void doWithTenant(String tenantId, Runnable action) {
            boolean switched = false;
            try {
                switched = switchToTenantDataSource(tenantId);
                action.run();
            } finally {
                if (switched) {
                    resetTenantDataSource();
                }
            }
        }
        
        /**
         * 执行租户相关的操作并返回结果
         * @param tenantId 租户ID
         * @param action 要执行的操作
         * @param <T> 返回值类型
         * @return 操作结果
         */
        public <T> T doWithTenantAndReturn(String tenantId, java.util.function.Supplier<T> action) {
            boolean switched = false;
            try {
                switched = switchToTenantDataSource(tenantId);
                return action.get();
            } finally {
                if (switched) {
                    resetTenantDataSource();
                }
            }
        }
    }
    
    /**
     * Spring Web框架适配器示例（注释掉，由用户根据需要启用）
     * 要使用此适配器，用户需要：
     * 1. 取消注释此类
     * 2. 添加servlet-api依赖
     * 3. 确保Spring Web在类路径中
     */
    /*
    @Configuration
    @ConditionalOnWebApplication
    static class SpringWebTenantAdapter {
        private static final String TENANT_ID_HEADER = "X-Tenant-ID";
        
        @Component
        static class TenantInterceptor implements HandlerInterceptor {
            private static final Logger log = LoggerFactory.getLogger(TenantInterceptor.class);
            private final TenantDataSourceHelper tenantHelper;
            
            public TenantInterceptor(TenantDataSourceHelper tenantHelper) {
                this.tenantHelper = tenantHelper;
            }
            
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String tenantId = request.getHeader(TENANT_ID_HEADER);
                tenantHelper.switchToTenantDataSource(tenantId);
                return true;
            }
            
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                      Object handler, Exception ex) {
                tenantHelper.resetTenantDataSource();
            }
        }
        
        @Bean
        public WebMvcConfigurer tenantWebConfig(TenantInterceptor tenantInterceptor) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(tenantInterceptor)
                            .addPathPatterns("/**")
                            .excludePathPatterns("/error", "/actuator/**");
                    log.info("Multi-tenant interceptor registered");
                }
            };
        }
    }
    */
}