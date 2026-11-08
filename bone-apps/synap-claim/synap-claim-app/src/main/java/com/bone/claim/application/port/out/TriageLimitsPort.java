package com.bone.claim.application.port.out;

import com.bone.claim.domain.model.decision.TriageLimits;

/**
 * 分诊阈值读取端口（§8.3 配置外部化 / §11.4 阈值外置）。
 *
 * <p><b>为什么是端口而不是直接依赖 {@code FeatureTenantToggleService}</b>：决策图执行器的关注点是「按租户拿到生效阈值」， 而不是「开关怎么存、JSON
 * 怎么解析」。抽成出站端口后，执行器单测可以传一个返回基线的 lambda，无需 mock 具体服务； 未来若阈值改由配置中心（Apollo/Nacos）下发，只换适配器，执行器不动。
 *
 * <p><b>契约</b>：实现方<b>必须永不返回 null、永不抛出</b>——读配置失败时返回基线而不是让决策链挂掉； 「配置坏了」与「决策失败」是两件事，前者只应降级为默认口径。
 */
@FunctionalInterface
public interface TriageLimitsPort {

  /**
   * 某租户生效的分诊阈值。
   *
   * @param tenantId 租户
   * @return 生效阈值 + 未生效项；无配置时返回 {@link TriageLimits#defaults()} 的包装
   */
  TriageLimits.Resolution triageLimits(long tenantId);
}
