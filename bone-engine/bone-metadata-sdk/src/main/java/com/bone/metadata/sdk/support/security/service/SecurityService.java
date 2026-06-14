package com.bone.metadata.sdk.support.security.service;

/** 安全服务接口 */
public interface SecurityService {

  /**
   * 验证服务注册权限
   *
   * @param pluginId 插件ID
   * @param serviceType 服务类型名
   */
  void validateServiceRegistration(String pluginId, String serviceType);

  /**
   * 验证连接
   *
   * @param connectionUrl 连接URL
   */
  void validateConnection(String connectionUrl);

  /**
   * 验证配置更新权限
   *
   * @param pluginId 插件ID
   */
  void validateConfigUpdate(String pluginId);
}
