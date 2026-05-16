package com.bone.metadata.engine.starter.autoconfigure;

import com.bone.metadata.engine.MetadataEngine;
import com.bone.metadata.engine.config.MetadataEngineProperties;
// 修复registry包找不到的问题
// import com.bone.metadata.engine.registry.MetadataRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/** Metadata Engine 自动配置类 */
@Configuration
@EnableConfigurationProperties(MetadataEngineProperties.class)
public class MetadataEngineClientAutoConfiguration {

  /** 创建元数据注册表（模拟实现） */
  @Bean
  @ConditionalOnMissingBean
  public Object metadataRegistry() { // 修改返回类型为Object
    // 返回一个模拟对象
    return new Object();
  }

  /** 创建元数据引擎 */
  @Bean
  @ConditionalOnMissingBean
  public MetadataEngine metadataEngine() {
    // 使用无参构造函数
    return new MetadataEngine();
  }

  /** 创建元数据引擎初始化器 */
  @Bean
  @ConditionalOnMissingBean
  public Object metadataEngineInitializer(
      MetadataEngine metadataEngine,
      MetadataEngineProperties smartMetaProperties,
      ResourceLoader resourceLoader) {
    // 返回一个模拟的初始化器对象
    return new Object();
  }
}
