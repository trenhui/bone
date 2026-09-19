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
      // 框架统一异常处理器（统一信封 + 状态码翻译）现由 bone-web 的 auto-configuration 提供
      // （BoneWebExceptionAutoConfiguration，按 Bean 名 globalExceptionHandler 让路给模块自带实现），
      // 故此处不再需要显式扫描 com.bone.core.exception。
      "com.bone.core.capability",
      "com.bone.core.security.jwt",
      "com.bone.web",
      // 扩展引擎 SDK 自注册包：ExtensionRepositoryFactory、CacheManager 等 @Component 需被扫描才能装配，
      // 否则 ExtensionAutoConfiguration 因缺少依赖 bean 而无法加载（下单等扩展点调用失败）。
      "com.bone.engine.extension"
    })
@EnableDiscoveryClient
@EnableScheduling
// 本注解只用于扫描 Repository 接口，扫描域 = 领域仓储 + Outbox 基础设施仓储。
// Outbox 单列原因：它是消息投递技术设施（无业务不变量），其记录与仓储已下沉
// infrastructure/messaging/outbox，不再占用 domain 包；但 SDK 仓储代理需显式声明扫描包才能生成实现。
// 不再单列 domain.order：E-10.2 要求领域端口包唯一，订单明细仓储（OrderItemRepository）已归位
// domain.repository。曾把它挪进聚合包以"通过" CORE-06，属用包位置绕过门禁——CORE-06 已改为按被持久化的
// 聚合根类型计数，OrderItem 是 Order 聚合内实体（非聚合根），放哪个包都不会变成第二个聚合。
// infrastructure.query：支付读侧 @Sql 仓储（PaymentQueryAdapter）。它不在 domain.repository，
// 但同样由 SDK 代理生成实现，必须显式登记扫描包，否则 Bean 不注册（表现为注入失败，而非"SQL 找不到"）。
@EnableSqlRepositories(
    basePackages = {
      "com.bone.blueprint.domain.repository",
      "com.bone.blueprint.infrastructure.query",
      "com.bone.blueprint.infrastructure.messaging.outbox",
      "com.bone.blueprint.infrastructure.messaging.idempotency",
      "com.bone.blueprint.infrastructure.idempotency"
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
