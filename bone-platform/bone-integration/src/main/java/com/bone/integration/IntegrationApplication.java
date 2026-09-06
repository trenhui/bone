package com.bone.integration;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.metadata.sdk.support.config.MetadataAutoConfiguration;
import com.bone.metadata.sdk.support.config.SqlRepositoryAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.bone.integration", "com.bone.core.security.jwt"})
@EnableScheduling
// 扫描域：领域仓储 + Outbox 基础设施仓储。
// 后者为何要单列：Outbox 是消息投递的技术设施（无业务不变量），其记录与仓储已下沉到
// infrastructure/messaging/outbox，不再占用 domain 包；但 SDK 仓储代理需显式声明扫描包才能生成实现。
@EnableSqlRepositories(
    basePackages = {
      "com.bone.integration.domain.repository",
      "com.bone.integration.infrastructure.messaging.outbox"
    })
@Import({MetadataAutoConfiguration.class, SqlRepositoryAutoConfiguration.class})
public class IntegrationApplication {
  public static void main(String[] args) {
    SpringApplication.run(IntegrationApplication.class, args);
  }
}
