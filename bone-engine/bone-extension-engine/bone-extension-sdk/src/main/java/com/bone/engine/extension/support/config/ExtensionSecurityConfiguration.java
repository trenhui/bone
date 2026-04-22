package com.bone.engine.extension.support.config;

import com.bone.engine.extension.core.security.ExtensionPermissionManager;
import com.bone.engine.extension.core.security.ExtensionSignatureVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 扩展点安全配置
 * <p>
 * 配置扩展点的安全相关Bean，包括签名验证器
 * </p>
 *
 * @since 1.0.0
 */
@Configuration
public class ExtensionSecurityConfiguration {

    /**
     * 创建扩展点签名验证器
     *
     * @return 签名验证器实例
     */
    @Bean
    public ExtensionSignatureVerifier extensionSignatureVerifier() {
        return new ExtensionSignatureVerifier();
    }

    /**
     * 创建扩展点权限管理器
     *
     * @return 权限管理器实例
     */
    @Bean
    public ExtensionPermissionManager extensionPermissionManager() {
        return new ExtensionPermissionManager();
    }
}
