package com.bone.metadata.sdk.support.dataSource;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/**
 * 在所有单例 bean 初始化完成后，将 Spring Boot 自动配置的原始 DataSource 设置为 DynamicDataSource 的默认数据源。
 *
 * <p>解决自引用循环问题：DynamicDataSource 继承 AbstractRoutingDataSource 实现了 DataSource 接口， 若在 {@code
 * dynamicDataSource()} 方法中通过 {@code ObjectProvider<DataSource>} 注入， Spring 会检测到自身正在创建而报循环依赖。通过
 * {@link SmartInitializingSingleton} 延迟到 所有 bean 创建完成后再获取原始 DataSource，从而打破循环。
 *
 * <p>同时在 {@link PostConstruct} 阶段也尝试设置默认数据源，确保在 bean 初始化阶段 （如其他 bean 的 {@code @PostConstruct} 或
 * {@code afterPropertiesSet}）需要查询数据库时， DynamicDataSource 已经有可用的默认数据源。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DynamicDataSourceInitializer
    implements SmartInitializingSingleton, ApplicationContextAware {

  private static final Logger log = LoggerFactory.getLogger(DynamicDataSourceInitializer.class);

  private final DynamicDataSource dynamicDataSource;
  private final DataSourceManager dataSourceManager;
  private final DataSourceProperties properties;
  private ApplicationContext applicationContext;
  private volatile boolean defaultDataSourceSet = false;

  public DynamicDataSourceInitializer(
      DynamicDataSource dynamicDataSource,
      DataSourceManager dataSourceManager,
      DataSourceProperties properties) {
    this.dynamicDataSource = dynamicDataSource;
    this.dataSourceManager = dataSourceManager;
    this.properties = properties;
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /**
   * 在 bean 初始化阶段尽早设置默认数据源，避免其他 bean 初始化时查询失败。 ApplicationContextAware 回调在 @PostConstruct 之前执行，因此
   * applicationContext 已可用。
   */
  @PostConstruct
  public void init() {
    trySetDefaultDataSource();
  }

  @Override
  public void afterSingletonsInstantiated() {
    // 如果 @PostConstruct 阶段未成功设置，在此重试
    if (!defaultDataSourceSet) {
      trySetDefaultDataSource();
    }
  }

  private void trySetDefaultDataSource() {
    if (defaultDataSourceSet) {
      return;
    }

    log.info("DynamicDataSourceInitializer: 尝试设置默认数据源");

    // 获取所有 DataSource bean，排除 DynamicDataSource 自身
    DataSource primaryDataSource = null;
    Map<String, DataSource> dataSources = applicationContext.getBeansOfType(DataSource.class);
    for (Map.Entry<String, DataSource> entry : dataSources.entrySet()) {
      if (!(entry.getValue() instanceof DynamicDataSource)) {
        primaryDataSource = entry.getValue();
        log.info("找到原始数据源 bean: {}", entry.getKey());
        break;
      }
    }

    // 如果没有找到原始 DataSource bean，尝试从 Spring 环境配置手动创建
    if (primaryDataSource == null) {
      log.info("未找到原始 DataSource bean，尝试从 spring.datasource.* 配置手动创建");
      primaryDataSource = createDataSourceFromEnvironment();
    }

    if (primaryDataSource != null) {
      String defaultName = properties.getDefaultDataSource();
      log.info("设置默认数据源: {}", defaultName);
      dynamicDataSource.setDefaultTargetDataSource(primaryDataSource);
      dataSourceManager.registerDataSource(defaultName, primaryDataSource);
      dynamicDataSource.addDataSource(defaultName, primaryDataSource);

      // 确保数据源已初始化
      if (!dynamicDataSource.isInitialized()) {
        try {
          dynamicDataSource.afterPropertiesSet();
        } catch (Exception e) {
          log.error("DynamicDataSource afterPropertiesSet 失败: {}", e.getMessage(), e);
        }
      }

      defaultDataSourceSet = true;

      log.info("默认数据源设置完成: {}", defaultName);
    } else {
      log.warn("未找到原始 DataSource bean，也无法从配置创建，DynamicDataSource 将没有默认数据源");
    }
  }

  /**
   * 从 Spring 环境配置（spring.datasource.*）手动创建 DataSource。 当 DynamicDataSource 替代了 Spring Boot 自动配置的
   * DataSource 时， 原始 HikariDataSource 不会被创建，需要手动从环境配置中构建。
   */
  private DataSource createDataSourceFromEnvironment() {
    try {
      Environment env = applicationContext.getEnvironment();
      String url = env.getProperty("spring.datasource.url");
      if (url == null || url.isBlank()) {
        log.debug("spring.datasource.url 未配置，无法手动创建 DataSource");
        return null;
      }

      log.info("未找到原始 DataSource bean，从 spring.datasource.* 配置手动创建");

      DataSourceBuilder<?> builder = DataSourceBuilder.create();
      builder.url(env.resolveRequiredPlaceholders(url));
      builder.username(env.getProperty("spring.datasource.username"));
      builder.password(env.getProperty("spring.datasource.password"));
      String driverClassName = env.getProperty("spring.datasource.driver-class-name");
      if (driverClassName != null && !driverClassName.isBlank()) {
        builder.driverClassName(driverClassName);
      }

      DataSource ds = builder.build();
      log.info("从 spring.datasource.* 配置手动创建 DataSource 成功");
      return ds;
    } catch (Exception e) {
      log.warn("从 spring.datasource.* 配置手动创建 DataSource 失败: {}", e.getMessage());
      return null;
    }
  }
}
