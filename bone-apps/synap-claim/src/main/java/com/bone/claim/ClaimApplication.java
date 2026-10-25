package com.bone.claim;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.metadata.sdk.support.config.MetadataAutoConfiguration;
import com.bone.metadata.sdk.support.config.SqlRepositoryAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * synapClaim 智能理赔平台启动类（2026-10-25 联调收尾补全）。
 *
 * <p>模块此前只有 DDD 四层代码与库内迁移（V1-V5）、无启动引导，服务不可运行； 本类补齐部署入口，使 {@code /api/v1/claims}
 * 链路（bone-claim-app:3013 → 网关 → 本服务）可端到端联调。
 *
 * <p>装配口径与 bone-integration / bone-blueprint 同款：
 *
 * <ul>
 *   <li>SDK 仓储代理：领域仓储接口继承 {@code com.bone.metadata.sdk.Repository}，由 {@code @EnableSqlRepositories}
 *       生成实现（无需手写持久层）；
 *   <li>SDK 自动配置经 {@code @Import} 显式引入（与 integration 一致，避免依赖类路径猜测）；
 *   <li>JWT 装配包 {@code com.bone.core.security.jwt} 显式扫描（网关签发的 token 在本服务验签）。
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"com.bone.claim", "com.bone.core.security.jwt"})
// 扫描域：领域仓储 + 幂等快照基础设施仓储（后者是技术设施、无业务不变量，
// 但 SDK 仓储代理需显式声明扫描包才能生成实现——与 bone-integration 的 Outbox 仓储同口径）。
@EnableSqlRepositories(
    basePackages = {
      "com.bone.claim.domain.repository",
      "com.bone.claim.infrastructure.idempotency"
    })
@Import({MetadataAutoConfiguration.class, SqlRepositoryAutoConfiguration.class})
public class ClaimApplication {
  public static void main(String[] args) {
    SpringApplication.run(ClaimApplication.class, args);
  }
}
