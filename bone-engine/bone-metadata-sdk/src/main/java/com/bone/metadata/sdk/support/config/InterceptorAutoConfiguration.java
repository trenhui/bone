package com.bone.metadata.sdk.support.config;

import com.bone.metadata.sdk.support.audit.AuditService;
import com.bone.metadata.sdk.support.audit.LoggingAuditService;
import com.bone.metadata.sdk.support.interceptor.*;
import com.bone.metadata.sdk.support.security.service.DefaultMetaPermissionService;
import com.bone.metadata.sdk.support.security.service.MetaPermissionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MetadataSdkProperties.class)
public class InterceptorAutoConfiguration {


    @Bean
    @ConditionalOnMissingBean(SecurityInterceptor.class)
    public SecurityInterceptor securityInterceptor(MetaPermissionService permissionService) {
        return new SecurityInterceptor(permissionService);
    }

    @Bean
    @ConditionalOnMissingBean(SqlExecutionInterceptor.class)
    public SqlExecutionInterceptor sqlExecutionInterceptor(MetadataSdkProperties props) {
        return new SqlExecutionInterceptor(props);
    }

    @Bean
    @ConditionalOnMissingBean(ExtensionMonitorInterceptor.class)
    public ExtensionMonitorInterceptor extensionMonitorInterceptor() {
        return new ExtensionMonitorInterceptor();
    }


    @Bean
    @ConditionalOnMissingBean(PerformanceInterceptor.class)
    public PerformanceInterceptor performanceInterceptor() {
        return new PerformanceInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(AuditService.class)
    public AuditService loggingAuditService() {
        return new LoggingAuditService();
    }

    @Bean
    @ConditionalOnMissingBean(MetaPermissionService.class)
    public MetaPermissionService metaPermissionService() {
        return new DefaultMetaPermissionService();
    }
}