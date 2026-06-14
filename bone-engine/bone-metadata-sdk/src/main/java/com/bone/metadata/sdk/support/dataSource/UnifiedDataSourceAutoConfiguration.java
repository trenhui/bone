package com.bone.metadata.sdk.support.dataSource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** 统一数据源自动配置类 合并了动态数据源和多数据源的配置功能，提供统一的数据源管理和切换支持 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "bone.metadata.datasource",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnClass({DynamicDataSource.class, DataSourceContextHolder.class})
@EnableConfigurationProperties(com.bone.metadata.sdk.support.dataSource.DataSourceProperties.class)
@AutoConfigureAfter({DataSourceAutoConfiguration.class})
@Import({DynamicDataSourceAspectConfiguration.class})
public class UnifiedDataSourceAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(UnifiedDataSourceAutoConfiguration.class);

  @Autowired private com.bone.metadata.sdk.support.dataSource.DataSourceProperties properties;

  /**
   * 创建数据源管理器，负责多数据源的注册、获取和切换逻辑
   *
   * @return 数据源管理器实例
   */
  @Bean
  @ConditionalOnMissingBean
  public DataSourceManager dataSourceManager() {
    log.info("创建数据源管理器");
    DefaultDataSourceManager manager = new DefaultDataSourceManager();

    // 设置配置参数
    manager.setDefaultDataSourceName(properties.getDefaultDataSource());
    manager.setStrictMode(properties.isStrict());
    manager.setMaxDataSourceCount(properties.getMaxDataSourceCount());

    return manager;
  }

  /**
   * 创建动态数据源实例
   *
   * <p>不通过方法参数注入 DataSource，避免自引用循环问题 （DynamicDataSource 继承 AbstractRoutingDataSource 实现了
   * DataSource 接口， 若通过 ObjectProvider&lt;DataSource&gt; 注入，Spring 会检测到自身正在创建而报循环依赖）。 默认数据源由 {@link
   * DynamicDataSourceInitializer} 在所有单例 bean 初始化完成后设置。
   *
   * @param dataSourceManager 数据源管理器
   * @return 动态数据源实例
   */
  @Bean
  @Primary
  @ConditionalOnMissingBean(DynamicDataSource.class)
  public DynamicDataSource dynamicDataSource(DataSourceManager dataSourceManager) {
    log.info("初始化DynamicDataSource");

    DynamicDataSource dynamicDataSource = new DynamicDataSource(dataSourceManager);

    // 设置数据源映射
    Map<Object, Object> targetDataSources = new ConcurrentHashMap<>();

    // 添加从配置文件中读取的数据源
    if (properties.getDataSources() != null && !properties.getDataSources().isEmpty()) {
      properties
          .getDataSources()
          .forEach(
              (name, dsConfig) -> {
                DataSource dataSource = properties.getDataSource(name, dsConfig);
                if (dataSource != null) {
                  targetDataSources.put(name, dataSource);
                  dataSourceManager.registerDataSource(name, dataSource);
                  log.info("注册数据源: {}", name);
                }
              });
    }

    // 设置目标数据源
    if (!targetDataSources.isEmpty()) {
      dynamicDataSource.setTargetDataSources(targetDataSources);
    }

    // 设置严格模式
    dynamicDataSource.setStrictMode(properties.isStrict());

    // 设置主数据源名称
    dynamicDataSource.setDefaultDataSourceKey(properties.getDefaultDataSource());

    // 初始化数据源（不含默认数据源，由 DynamicDataSourceInitializer 后续设置）
    // 注意：如果 targetDataSources 为空，AbstractRoutingDataSource.afterPropertiesSet() 会抛异常，
    // 因此只在有目标数据源时才初始化
    if (!targetDataSources.isEmpty()) {
      try {
        dynamicDataSource.afterPropertiesSet();
      } catch (Exception e) {
        throw new RuntimeException("DynamicDataSource 初始化失败", e);
      }
    }

    log.info("DynamicDataSource初始化成功（默认数据源将由Initializer设置），共注册{}个数据源", targetDataSources.size());
    return dynamicDataSource;
  }

  /**
   * 在所有单例 bean 初始化完成后，将 Spring Boot 自动配置的原始 DataSource 设置为 DynamicDataSource 的默认数据源，避免 bean
   * 创建阶段的自引用循环。
   */
  @Bean
  @ConditionalOnBean(DynamicDataSource.class)
  public DynamicDataSourceInitializer dynamicDataSourceInitializer(
      DynamicDataSource dynamicDataSource, DataSourceManager dataSourceManager) {
    log.info("创建DynamicDataSourceInitializer");
    return new DynamicDataSourceInitializer(dynamicDataSource, dataSourceManager, properties);
  }

  /**
   * 创建动态数据源事务管理器
   *
   * @param dynamicDataSource 动态数据源
   * @return 事务管理器
   */
  @Bean
  @ConditionalOnBean(DynamicDataSource.class)
  public PlatformTransactionManager dynamicTransactionManager(DynamicDataSource dynamicDataSource) {
    return new DataSourceTransactionManager(dynamicDataSource);
  }

  /**
   * 创建数据源健康检查指示器，用于监控所有数据源的连接状态
   *
   * @param dataSourceManager 数据源管理器
   * @return 数据源健康检查指示器
   */
  @Bean
  @ConditionalOnMissingBean
  public DataSourceHealthChecker dataSourceHealthChecker(DataSourceManager dataSourceManager) {
    log.info("创建数据源健康检查器");
    DataSourceHealthChecker checker = new DataSourceHealthChecker(dataSourceManager);
    checker.setCheckInterval(properties.getHealthCheckIntervalMs() / 1000); // 转换为秒
    checker.setCheckTimeout(properties.getHealthCheckRetryCount() * 1000); // 转换为毫秒作为超时时间
    return checker;
  }

  // 数据源切换拦截器由DynamicDataSourceAspectConfiguration提供，避免重复定义
}
