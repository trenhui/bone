package com.bone.metadata.engine.starter.config;

import com.bone.metadata.engine.MetadataEngine;
import com.bone.metadata.engine.ValidationEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Metadata Engine Starter 自动配置类 */
@Configuration
public class MetadataEngineStarterAutoConfiguration {

  /** 创建元数据相关配置属性 */
  @Bean
  @ConfigurationProperties(prefix = "smartmeta")
  public MetadataEngineProperties smartMetaProperties() {
    return new MetadataEngineProperties();
  }

  /** 创建元数据注册表（模拟实现） */
  @Bean
  @ConditionalOnMissingBean
  public Object metadataRegistry() {
    // 返回一个模拟对象
    return new Object();
  }

  /** 创建元数据引擎 */
  @Bean
  @ConditionalOnMissingBean
  @Lazy
  public MetadataEngine metadataEngine() {
    // 使用无参构造函数
    return new MetadataEngine();
  }

  /** 创建验证引擎 */
  @Bean
  @ConditionalOnMissingBean
  public ValidationEngine validationEngine() {
    // 使用无参构造函数
    return new ValidationEngine();
  }
}

/** 简化的Metadata Engine属性类，用于自动配置 */
class MetadataEngineProperties {
  // 基本配置属性
  private boolean cacheEnabled = true;
  private boolean validationEnabled = true;
  private long cacheExpirationTime = 3600000;

  // Getter和Setter方法
  public boolean isCacheEnabled() {
    return cacheEnabled;
  }

  public void setCacheEnabled(boolean cacheEnabled) {
    this.cacheEnabled = cacheEnabled;
  }

  public boolean isValidationEnabled() {
    return validationEnabled;
  }

  public void setValidationEnabled(boolean validationEnabled) {
    this.validationEnabled = validationEnabled;
  }

  public long getCacheExpirationTime() {
    return cacheExpirationTime;
  }

  public void setCacheExpirationTime(long cacheExpirationTime) {
    this.cacheExpirationTime = cacheExpirationTime;
  }
}
