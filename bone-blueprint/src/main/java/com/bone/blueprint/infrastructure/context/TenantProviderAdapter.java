package com.bone.blueprint.infrastructure.context;

import com.bone.blueprint.application.port.out.TenantProvider;
import com.bone.core.tenant.context.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 租户上下文适配器：经 {@link TenantProvider} 端口向应用层提供当前租户 ID（基础设施实现）。
 *
 * <p><b>命名约定</b>：端口-适配器架构中，实现类统一用 {@code <Port>Adapter} 或 {@code <Port>Impl} 后缀， 使「接口 →
 * 实现」可一键跳转、可全局搜索。原名 {@code TenantSupport} 与端口名不一致，团队按接口名搜实现类会搜不到。
 *
 * <p><b>降级审计（E-4.4）</b>：异步分支（定时任务 / 线程池 / MQ 消费 / Outbox 中继）若未显式传递租户上下文， {@code TenantContext}
 * 为空。此时<b>禁止静默按 {@code null} 放行</b>（会导致全租户数据泄漏），统一降级为 平台租户 {@code DEFAULT_TENANT_ID} 并输出 warn
 * 审计日志（含调用来源与线程名），供排查遗漏「打洞」的异步入口； 已登记的定时任务打洞见模块 README「多租户 · 平台租户打洞登记」。
 */
@Slf4j
@Component
public class TenantProviderAdapter implements TenantProvider {

  /** 平台租户 ID：仅用于显式降级（异步分支）或经 README 登记的定时任务打洞。 */
  private static final long DEFAULT_TENANT_ID = 0L;

  @Override
  public long currentTenantId() {
    Long tenantId = TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      log.warn(
          "[TenantContext 缺失] 线程 {} 无租户上下文，降级为平台租户 tenantId={}，调用来源={}。"
              + "请确认该异步入口（定时任务/线程池/MQ/Outbox）已显式传递租户或已在 README 登记打洞（E-4.4）",
          Thread.currentThread().getName(),
          DEFAULT_TENANT_ID,
          callerHint());
      return DEFAULT_TENANT_ID;
    }
    return tenantId;
  }

  /** 取最近一帧调用来源（仅降级路径触发，开销可忽略）。 */
  private String callerHint() {
    return StackWalker.getInstance()
        .walk(
            frames ->
                frames
                    .skip(2)
                    .findFirst()
                    .map(f -> f.getClassName() + "#" + f.getMethodName())
                    .orElse("unknown"));
  }
}
