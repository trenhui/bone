package com.bone.boot;

import com.bone.metadata.MetadataApplication;
import org.springframework.boot.SpringApplication;

/**
 * 运行时组合根（composition root）。
 *
 * <p>本模块不包含任何业务/平台逻辑，仅负责<strong>部署期装配</strong>：复用 {@link MetadataApplication} 的启动配置（组件扫描根 {@code
 * com.bone.metadata} + Spring Boot 自动配置加载），并把各业务应用模块 （bone-app-expense / bone-accounting-engine /
 * bone-app-purchase）以自动配置形式挂入 classpath。
 *
 * <p>由此保证：bone-metadata-server 作为纯平台，<strong>不反向依赖</strong>任何业务模块；业务能力随 jar 挂载而生效。
 */
public class BoneBootApplication {

  public static void main(String[] args) {
    SpringApplication.run(MetadataApplication.class, args);
  }
}
