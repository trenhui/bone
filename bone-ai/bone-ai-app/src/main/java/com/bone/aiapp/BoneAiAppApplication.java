package com.bone.aiapp;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * bone-ai 生产拓扑聚合（区别于 bone-ai-server 参考宿主）。
 *
 * <p>把八个 AI 节点库聚合为可部署的生产 Spring Boot 应用：依赖各库后，其 {@code .imports} 自动配置 （RagApplicationService /
 * IntegrationApplicationService / AlertNotificationApplicationService / TraceService 等）即被装配；
 * 本类仅负责注册各 SDK 仓储代理 + 暴露统一 HTTP 能力 + 健康检查（Actuator）。
 */
@SpringBootApplication
@EnableSqlRepositories(
    basePackages = {
      "com.bone.airules.domain.repository",
      "com.bone.aitrace.domain.repository",
      "com.bone.aiorch.domain.hitl",
      "com.bone.airag.domain.repository",
      "com.bone.aiapp.eval.domain.repository"
    })
public class BoneAiAppApplication {

  public static void main(String[] args) {
    SpringApplication.run(BoneAiAppApplication.class, args);
  }
}
