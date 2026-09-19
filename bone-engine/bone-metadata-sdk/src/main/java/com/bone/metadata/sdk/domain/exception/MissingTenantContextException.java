package com.bone.metadata.sdk.domain.exception;

/**
 * 失败关闭异常：查询/写入租户表时 {@code TenantContext.tenantId} 为 null。
 *
 * <p>SDK 自动租户过滤（ADR-0029）要求租户条件恒从可信的 {@code TenantContext} 注入。当表为租户表、 且未显式 {@code
 * Criteria.disableTenantFilter()} 时，若上下文无租户则抛出本异常，避免退化为 {@code tenant_id = NULL} 静默空结果或跨租户越权。
 *
 * <p>调用方应在请求线程由 JWT Filter 写入 {@code TenantContext}；后台作业 / Outbox 中继须先 {@code
 * TenantContext.setTenantId(...)} 再操作，或显式 {@code disableTenantFilter()}（须 {@code platform:*} 授权 +
 * 审计）。
 */
public class MissingTenantContextException extends SDKException {
  public MissingTenantContextException(String tableName) {
    super(
        "TENANT_CONTEXT_MISSING",
        "TenantContext.tenantId is required but was null while accessing tenant-scoped table: "
            + tableName
            + ". Set TenantContext.setTenantId(...) first, or use Criteria.disableTenantFilter() for legitimate cross-tenant operations.",
        null);
  }
}
