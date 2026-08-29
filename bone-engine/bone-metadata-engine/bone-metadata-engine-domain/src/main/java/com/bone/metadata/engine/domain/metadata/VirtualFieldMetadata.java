package com.bone.metadata.engine.domain.metadata;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** 虚拟字段元数据类 支持从外部数据源或动态计算获取的字段数据 */
@Getter
@Setter
public class VirtualFieldMetadata extends SmartFieldMetadata {

  // 虚拟字段提供程序
  private String provider;

  // 提供程序配置参数
  private Map<String, Object> configuration = new HashMap<>();

  // 数据刷新策略
  private RefreshStrategy refreshStrategy = RefreshStrategy.ON_DEMAND;

  // 缓存有效期（毫秒）
  private long cacheTtl = 600000; // 默认10分钟

  // 是否需要异步加载
  private boolean asyncLoading = false;

  // 数据源URL或标识符
  private String dataSource;

  // 数据转换模板
  private String transformationTemplate;

  /** 刷新策略枚举 */
  public enum RefreshStrategy {
    // 按需加载
    ON_DEMAND,
    // 每次访问时刷新
    ALWAYS_REFRESH,
    // 定时刷新
    SCHEDULED,
    // 基于事件刷新
    EVENT_BASED
  }

  /** 添加配置参数 */
  public void addConfiguration(String key, Object value) {
    configuration.put(key, value);
  }

  /** 获取配置参数 */
  public <T> T getConfiguration(String key, Class<T> type) {
    Object value = configuration.get(key);
    if (value != null && type.isInstance(value)) {
      return type.cast(value);
    }
    return null;
  }

  /** 检查是否有配置 */
  public boolean hasConfiguration() {
    return !configuration.isEmpty();
  }

  /** 设置提供程序 */
  public void setProvider(String provider) {
    this.provider = provider;
    // 避免直接访问父类的private字段virtual
    // this.virtual = true; // 注释掉这行
  }
}
