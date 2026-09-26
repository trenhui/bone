package com.bone.iam.infrastructure.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.NamedContributor;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * IAM 模块健康检查：聚合 Spring Boot 自动装配的底层依赖检查，取代原先恒返 UP 的占位实现。
 *
 * <p><b>为何不再自造 JDBC 探活</b>：手写 {@code DataSource} 查询等于在本模块新增一处绕过 {@code bone-metadata-sdk} 的持久化入口，会撞
 * {@code sdk-persistence-bypass-baseline.json}「只可收缩、新增文件不得加入」的门禁。 而 {@code db} / {@code redis} 的探活由
 * Spring Boot 依据实际装配的连接工厂自动注册，聚合它们即可拿到 <strong>真实</strong>结果，且不新增任何绕过面。
 *
 * <p><b>为何用 {@code ApplicationContext#getBean} 延迟取 registry，而不是构造注入</b>：{@code
 * healthContributorRegistry} 会把所有 {@link HealthIndicator}（含本类）收为构造入参，构造期注入即形成 {@code
 * iamModuleHealthIndicator → healthContributorRegistry → iamModuleHealthIndicator} 循环引用， Spring
 * Boot 3 默认禁止循环引用，上下文直接启动失败。改为在 {@code health()} 内按需取用 —— 此时 registry 早已是已创建的单例，既拿到同一批指标，又不参与装配环。
 *
 * <p><b>状态归并口径</b>：任一被聚合项 DOWN ⇒ 整体 DOWN；全部 UP ⇒ UP；缺失项落 {@link Status#UNKNOWN} 并注明原因 —— 未接入的能力报
 * UNKNOWN，而不是像旧实现那样用静态 UP 冒充「已校验」。
 */
@Component
public class IamModuleHealthIndicator implements HealthIndicator {

  /** 被聚合的下游健康检查名（Spring Boot 在 registry 里注册的展示名）。 */
  private static final String[] AGGREGATED = {"db", "redis"};

  private final ApplicationContext context;

  public IamModuleHealthIndicator(ApplicationContext context) {
    this.context = context;
  }

  @Override
  public Health health() {
    HealthContributorRegistry registry = context.getBean(HealthContributorRegistry.class);

    Map<String, String> parts = new LinkedHashMap<>();
    for (String name : AGGREGATED) {
      parts.put(name, statusOf(registry, name));
    }
    Status overall = Status.UP;
    for (String code : parts.values()) {
      if (Status.DOWN.getCode().equals(code) || Status.OUT_OF_SERVICE.getCode().equals(code)) {
        overall = Status.DOWN;
        break;
      }
      if (!Status.UP.getCode().equals(code)) {
        overall = Status.UNKNOWN;
      }
    }

    Health.Builder builder = Status.DOWN.equals(overall) ? Health.down() : Health.status(overall);
    builder.withDetail("module", "bone-iam");
    parts.forEach((name, code) -> builder.withDetail(name, code));
    // Minio 不参与状态归并：本模块审计日志落库、不写对象存储，若把「未装配」算成 UNKNOWN，
    // 整体健康会恒为 UNKNOWN，探针永远判不死活（等于把 UNKNOWN 变成了新的 STUB）。
    // 因此它只作为说明性 detail 出现，并显式标注 notApplicable。
    builder.withDetail("minio", "notApplicable");
    builder.withDetail("minioDetail", "MinioClient 未在本模块装配（仅 pom 依赖），无可探活目标；审计日志归档落库而非对象存储");
    return builder.build();
  }

  /** 取 registry 中指定健康检查的状态；未注册时返回 {@code UNKNOWN}（不冒充 UP）。 */
  private static String statusOf(HealthContributorRegistry registry, String name) {
    HealthContributor contributor = registry.getContributor(name);
    return contributor == null ? Status.UNKNOWN.getCode() : worstStatusOf(contributor);
  }

  /** 单个 {@link HealthIndicator} 直接取状态；组合型（Boot 3 的 Redis）取最差子项。 */
  private static String worstStatusOf(HealthContributor contributor) {
    if (contributor instanceof HealthIndicator indicator) {
      return indicator.health().getStatus().getCode();
    }
    if (contributor instanceof CompositeHealthContributor composite) {
      String worst = Status.UP.getCode();
      for (NamedContributor<HealthContributor> named : composite) {
        String code = worstStatusOf(named.getContributor());
        if (Status.DOWN.getCode().equals(code)) {
          return code;
        }
        if (!Status.UP.getCode().equals(code)) {
          worst = code;
        }
      }
      return worst;
    }
    return Status.UNKNOWN.getCode();
  }
}
