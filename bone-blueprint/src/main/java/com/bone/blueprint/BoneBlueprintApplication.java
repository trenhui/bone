package com.bone.blueprint;

import com.bone.engine.extension.api.annotation.EnableExtensionPoints;
import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(
    basePackages = {
      "com.bone.blueprint",
      "com.bone.core.capability",
      "com.bone.core.security.jwt",
      "com.bone.web",
      // 扩展引擎 SDK 自注册包：ExtensionRepositoryFactory、CacheManager 等 @Component 需被扫描才能装配，
      // 否则 ExtensionAutoConfiguration 因缺少依赖 bean 而无法加载（下单等扩展点调用失败）。
      "com.bone.engine.extension"
    })
@EnableDiscoveryClient
@EnableScheduling
// 扫描域：领域仓储 + Outbox 基础设施仓储。
// 后者为何要单列：Outbox 是消息投递的技术设施（无业务不变量），其记录与仓储已下沉到
// infrastructure/messaging/outbox，不再占用 domain 包；但 SDK 仓储代理需显式声明扫描包才能生成实现。
// domain.order 单列原因：订单明细仓储（OrderItemRepository）作为 Order 聚合的子实体仓储，
// 需与聚合根同包（domain.order）以通过 R9 一事务一聚合门禁（R9 仅按 ..domain.repository.. 接口计数）。
@EnableSqlRepositories(
    basePackages = {
      "com.bone.blueprint.domain.repository",
      "com.bone.blueprint.domain.order",
      "com.bone.blueprint.infrastructure.messaging.outbox"
    })
@EnableExtensionPoints(
    basePackages = {
      "com.bone.blueprint.domain.extension",
      "com.bone.blueprint.infrastructure.extension"
    })
public class BoneBlueprintApplication {

  public static void main(String[] args) {
    SpringApplication.run(BoneBlueprintApplication.class, args);
  }
}
