package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.supplier.GuardVerdict;
import com.bone.masterdata.domain.model.supplier.SupplierOnboardingContext;

/**
 * 供应商准入闸门（{@code ONBOARDING_APPROVED}）的守卫端口。
 *
 * <p><b>为什么准入也需要守卫</b>（2026-11-16 独立审查 C-03 修复）：spec:54 明确要求「变更请求全段已批准 + 至少存在 1 个
 * ACTIVE_VENDOR」同时成立才可迁移到 {@code ACTIVE}。此前该迁移被标为无需守卫，等于「有事件就批」—— 一个还没通过财务侧开通闸门的供应商可以直接变成合作中供应商，与
 * spec 冲突。
 *
 * <p>与 {@link SupplierExitGuard} 一样返回「布尔结论 + 原因码」，不返回硬编码 true：恒真守卫会让 「未经闸门不得准入」这条 Scenario
 * 无论上游状态如何都通过（假绿）。
 */
public interface SupplierOnboardingGuard {

  /** 准入闸门：变更请求全段批准且至少一个 ACTIVE_VENDOR 才通过。 */
  GuardVerdict canActivate(SupplierOnboardingContext context);
}
