package com.bone.system.domain.gateway;

/**
 * 当前租户取值端口。
 *
 * <p>为什么要有它：业务层直调 {@code TenantContext.get*()} 会把「租户从哪来」散落到每个用例里， 异步线程丢失时表现为「静默变成平台数据」，且无法审计（E-2
 * 门禁已禁止 application/domain/adapter 直调）。 收敛到一个接口后，实现（上下文 / 定时任务固定值 / 测试桩）可替换而不动用例。
 */
public interface TenantProvider {

  /** 当前租户；无法确定时返回 {@code null}（调用方按平台语义兜 0）。 */
  Long currentTenantIdOrNull();
}
