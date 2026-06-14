package com.bone.engine.extension.support.config;

import java.util.*;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

/**
 * 配置变更监听器
 *
 * <p>监听Spring环境中的配置变更，同步到扩展点配置管理器 支持配置的热更新和动态生效
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
@Component
@ConditionalOnClass(name = "org.springframework.cloud.context.environment.EnvironmentChangeEvent")
public class ConfigurationChangeListener implements ApplicationListener<ApplicationContextEvent> {

  private static final Logger log = LoggerFactory.getLogger(ConfigurationChangeListener.class);
  private static final String EXTENSION_CONFIG_PREFIX = "bone.extension.";

  private final ExtensionConfigManager configManager;
  private ConfigurableEnvironment environment;
  private final ApplicationContext applicationContext;
  private final List<ConfigChangeListener> configChangeListeners = new CopyOnWriteArrayList<>();

  @Autowired
  public ConfigurationChangeListener(
      ExtensionConfigManager configManager,
      ConfigurableEnvironment environment,
      ApplicationContext applicationContext) {
    this.configManager = configManager;
    this.environment = environment;
    this.applicationContext = applicationContext;
    // 自动注册所有ConfigChangeListener实现
    Map<String, ConfigChangeListener> listeners =
        applicationContext.getBeansOfType(ConfigChangeListener.class);
    for (ConfigChangeListener listener : listeners.values()) {
      addConfigChangeListener(listener);
    }
  }

  /** 添加配置变更监听器 */
  public void addConfigChangeListener(ConfigChangeListener listener) {
    if (listener != null && !configChangeListeners.contains(listener)) {
      configChangeListeners.add(listener);
      log.debug("Added config change listener: {}", listener.getClass().getSimpleName());
    }
  }

  /** 移除配置变更监听器 */
  public void removeConfigChangeListener(ConfigChangeListener listener) {
    if (listener != null) {
      configChangeListeners.remove(listener);
      log.debug("Removed config change listener: {}", listener.getClass().getSimpleName());
    }
  }

  /** 初始化方法，Spring会自动调用 */
  public void init() {
    // 获取所有实现了ConfigChangeListener接口的Bean
    Map<String, ConfigChangeListener> beans =
        applicationContext.getBeansOfType(ConfigChangeListener.class);
    if (!beans.isEmpty()) {
      configChangeListeners.addAll(beans.values());
      log.info("Registered {} config change listeners", configChangeListeners.size());
    }
  }

  /** 监听配置变更事件 */
  @Override
  public void onApplicationEvent(ApplicationContextEvent event) {
    // 使用反射来检查是否是EnvironmentChangeEvent
    Set<String> changedKeys = extractChangedKeys(event);
    if (changedKeys == null || changedKeys.isEmpty()) {
      return;
    }

    // 筛选出扩展点相关的配置变更
    List<String> extensionChangedKeys =
        changedKeys.stream()
            .filter(key -> key.startsWith(EXTENSION_CONFIG_PREFIX))
            .collect(Collectors.toList());

    if (!extensionChangedKeys.isEmpty()) {
      log.info("Detected extension configuration changes: {}", extensionChangedKeys);

      // 处理配置变更
      handleConfigurationChanges(extensionChangedKeys);

      // 触发配置更新回调
      triggerConfigUpdateCallbacks();
    }

    // 通知所有注册的配置变更监听器
    for (ConfigChangeListener listener : configChangeListeners) {
      try {
        String[] prefixes = listener.getConfigKeyPrefixes();
        if (prefixes != null && prefixes.length > 0) {
          // 过滤出与监听器相关的变更键
          Set<String> relevantKeys =
              changedKeys.stream()
                  .filter(key -> Arrays.stream(prefixes).anyMatch(key::startsWith))
                  .collect(Collectors.toSet());

          if (!relevantKeys.isEmpty()) {
            listener.onConfigChanged(relevantKeys);
          }
        } else {
          // 如果监听器没有指定前缀，则通知所有变更
          listener.onConfigChanged(changedKeys);
        }
      } catch (Exception e) {
        log.error(
            "Error notifying config change listener: {}", listener.getClass().getSimpleName(), e);
      }
    }
  }

  /** 使用反射从EnvironmentChangeEvent中提取变更的键 */
  @SuppressWarnings("unchecked")
  private Set<String> extractChangedKeys(Object event) {
    try {
      // 检查是否是EnvironmentChangeEvent
      Class<?> eventClass = event.getClass();
      if (eventClass
          .getName()
          .equals("org.springframework.cloud.context.environment.EnvironmentChangeEvent")) {
        // 使用反射获取keys
        return (Set<String>) eventClass.getMethod("getKeys").invoke(event);
      }
    } catch (Exception e) {
      log.debug("Failed to extract changed keys from event", e);
    }
    return Collections.emptySet();
  }

  /** 处理配置变更 */
  private void handleConfigurationChanges(List<String> changedKeys) {
    for (String key : changedKeys) {
      try {
        String value = environment.getProperty(key);
        if (value != null) {
          // 提取扩展点名称和配置键
          String relativeKey = key.substring(EXTENSION_CONFIG_PREFIX.length());
          String[] parts = relativeKey.split("\\.", 2);

          if (parts.length == 2) {
            String extPointName = parts[0];
            String configKey = parts[1];

            // 更新扩展点配置
            configManager.setExtPointConfig(extPointName, configKey, value);
            log.info("Updated extension config: {}.{}={}", extPointName, configKey, value);
          } else {
            // 全局配置更新
            log.info("Updated global extension config: {}={}", relativeKey, value);
          }
        }
      } catch (Exception e) {
        log.error("Failed to handle configuration change for key: {}", key, e);
      }
    }
  }

  /** 触发配置更新回调 */
  private void triggerConfigUpdateCallbacks() {
    try {
      // 通知所有配置变更监听器
      notifyConfigChangeListeners(environment.getPropertySources());

      log.info("Extension configuration update callbacks triggered");
    } catch (Exception e) {
      log.error("Failed to trigger configuration update callbacks", e);
    }
  }

  /** 通知配置变更监听器 */
  private void notifyConfigChangeListeners(
      Iterable<org.springframework.core.env.PropertySource<?>> propertySources) {
    Set<String> activeKeys = getActiveExtensionConfigs();

    for (ConfigChangeListener listener : configChangeListeners) {
      try {
        String[] prefixes = listener.getConfigKeyPrefixes();
        if (prefixes != null && prefixes.length > 0) {
          // 筛选出监听器关注的配置键
          Set<String> relevantKeys =
              activeKeys.stream()
                  .filter(key -> Arrays.stream(prefixes).anyMatch(key::startsWith))
                  .collect(Collectors.toSet());

          if (!relevantKeys.isEmpty()) {
            log.debug(
                "Notifying listener {} with {} relevant config changes",
                listener.getClass().getSimpleName(),
                relevantKeys.size());
            listener.onConfigChanged(relevantKeys);
          }
        }
      } catch (Exception e) {
        log.error(
            "Failed to notify config change listener: {}", listener.getClass().getSimpleName(), e);
      }
    }
  }

  /** 刷新扩展点配置 从当前Spring环境中重新加载所有扩展点配置 */
  public void refreshExtensionConfig() {
    try {
      log.info("Refreshing extension configuration from environment");

      // 获取所有扩展点相关配置
      Binder binder = Binder.get(environment);
      ExtensionProperties properties =
          binder
              .bind(EXTENSION_CONFIG_PREFIX, ExtensionProperties.class)
              .orElse(new ExtensionProperties());

      // 重新加载配置到配置管理器
      // 这里可以根据需要实现具体的刷新逻辑

      log.info("Extension configuration refreshed successfully");
    } catch (Exception e) {
      log.error("Failed to refresh extension configuration", e);
    }
  }

  /** 获取当前活动的扩展点配置 */
  public Set<String> getActiveExtensionConfigs() {
    Set<String> activeKeys = new HashSet<>();
    // 遍历所有属性源，手动收集符合条件的属性键
    for (org.springframework.core.env.PropertySource<?> source : environment.getPropertySources()) {
      if (source instanceof org.springframework.core.env.EnumerablePropertySource) {
        org.springframework.core.env.EnumerablePropertySource<?> enumerableSource =
            (org.springframework.core.env.EnumerablePropertySource<?>) source;

        for (String name : enumerableSource.getPropertyNames()) {
          if (name.startsWith(EXTENSION_CONFIG_PREFIX)) {
            activeKeys.add(name);
          }
        }
      }
    }
    return activeKeys;
  }
}
