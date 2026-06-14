package com.bone.engine.extension.support.config;

import java.util.Set;

/**
 * 配置变更监听器接口
 *
 * <p>组件实现此接口可以监听配置变更事件并做出相应响应，例如清理缓存、重新加载配置等
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
public interface ConfigChangeListener {

  /**
   * 处理配置变更事件
   *
   * @param changedKeys 发生变更的配置键集合
   */
  void onConfigChanged(Set<String> changedKeys);

  /**
   * 获取此监听器关注的配置键前缀
   *
   * @return 配置键前缀数组
   */
  String[] getConfigKeyPrefixes();
}
