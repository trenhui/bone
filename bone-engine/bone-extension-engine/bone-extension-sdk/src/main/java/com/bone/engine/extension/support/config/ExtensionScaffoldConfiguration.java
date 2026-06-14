package com.bone.engine.extension.support.config;

import com.bone.engine.extension.core.scaffold.ExtensionScaffoldGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 扩展点脚手架配置
 *
 * <p>配置扩展点脚手架生成器的Bean定义
 *
 * @since 1.0.0
 */
@Configuration
public class ExtensionScaffoldConfiguration {

  /**
   * 创建扩展点脚手架生成器
   *
   * @return 脚手架生成器实例
   */
  @Bean
  public ExtensionScaffoldGenerator extensionScaffoldGenerator() {
    return new ExtensionScaffoldGenerator();
  }
}
