package com.bone.claim.domain.model.claim.projection;

/**
 * 全租户扫描用的最小赔案引用投影（ADR-0031 租户作用域显式化）。
 *
 * <p>仅 {@code adapter.schedule} 的全租户批处理使用：携带 {@code tenantId} 以便调度侧逐租户切换执行上下文， 避免隐式 {@code
 * CurrentTenant} 导致串租户越权（ADR-0031 / ADR-0034）。
 *
 * <p>刻意不含任何业务字段（金额 / 结论 / 证件哈希），防止全表扫描把敏感字段一次性载入内存，也避免投影随业务演进 膨胀；需要业务字段时按 {@code claimId} 走租户内二次查询。
 */
public record ClaimTenantRef(Long tenantId, Long claimId, String claimNo, String status) {}
