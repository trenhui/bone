package com.bone.metadata.sdk.support.security.service;

import com.bone.metadata.sdk.extension.plugin.PluginDescriptor;
import org.springframework.stereotype.Service;

import java.net.URL;

@Service
public class DatabaseSecurityService implements SecurityService {

    @Override
    public void validateConnection(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:mysql://")) {
            throw new SecurityException("不允许的数据库连接协议");
        }
    }

    @Override
    public void validateServiceRegistration(String pluginId, String serviceType) {
        // 只允许MySQL插件注册SQL方言服务
        if ("com.bone.metadata.plugin.mysql".equals(pluginId)) {
            if (!"SqlDialect".equals(serviceType)) {
                throw new SecurityException("MySQL插件只能注册SQL方言服务");
            }
        } else {
            throw new SecurityException("未授权的服务注册操作");
        }
    }

    @Override
    public void validateConfigUpdate(String pluginId) {
        // 只允许管理员角色更新配置
    }
}