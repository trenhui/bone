package com.bone.aiserver;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * bone-ai 参考宿主（blueprint 风格，非生产拓扑）。
 *
 * <p>把五个 bone-ai 库接线成一个可运行应用，并演示产品侧接入所需的最小装配： {@code @EnableSqlRepositories} 注册 rules / trace
 * 的仓储代理（SDK 动态生成，无需手写实现）。 生产形态是各产品服务以进程内 starter 依赖这些库、自行加本注解——本类是"怎么接"的活模板。
 */
@SpringBootApplication
@EnableSqlRepositories(
    basePackages = {
      "com.bone.airules.domain.repository",
      "com.bone.aitrace.domain.repository",
      "com.bone.aiorch.domain.hitl" // HITL 子域仓储（智能理赔人工终审）
    })
public class BoneAiApplication {

  public static void main(String[] args) {
    SpringApplication.run(BoneAiApplication.class, args);
  }
}
