package com.bone.claim.domain.model.reconciliation;

/**
 * 对账租户投影（全租户扫描返回，定时任务专用）。
 *
 * <p>仅含 {@code tenantId}，供 {@code ReconciliationJob} 枚举需对账的租户后逐租户 {@code runAs} 处理。
 */
public record ReconTenantRef(Long tenantId) {}
