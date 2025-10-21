package com.bone.smartmeta.engine.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全配置类
 * 集成所有安全相关组件到Spring应用程序
 */
@Configuration
public class SecurityConfig {
    
    /**
     * 注意：当前配置仅包含自定义安全组件，不依赖Spring Security框架
     * 如果需要完整的Spring Security功能，需要在pom.xml中添加相应依赖
     */
    
    /**
     * 权限评估器Bean
     */
    @Bean
    public PermissionEvaluator permissionEvaluator() {
        return new DefaultPermissionEvaluator();
    }
    
    /**
     * 数据脱敏服务Bean
     */
    @Bean
    public DataMaskingService dataMaskingService() {
        return new DefaultDataMaskingService();
    }
    
    /**
     * 审计服务Bean
     */
    @Bean
    public AuditService auditService() {
        return new DefaultAuditService();
    }
    
    /**
     * 字段级安全管理器Bean
     */
    @Bean
    public FieldLevelSecurityManager fieldLevelSecurityManager(
            PermissionEvaluator permissionEvaluator,
            DataMaskingService dataMaskingService,
            AuditService auditService) {
        return new FieldLevelSecurityManager(permissionEvaluator, dataMaskingService, auditService);
    }
}