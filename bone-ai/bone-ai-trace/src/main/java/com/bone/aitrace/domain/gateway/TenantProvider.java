package com.bone.aitrace.domain.gateway;

/** 租户取值端口（领域层不直连 TenantContext，实现下沉 infrastructure，E-2）。 */
public interface TenantProvider {

  /** 当前租户 ID（Long 形式；未设置或非数值时由实现决定兜底）。 */
  Long currentTenantId();
}
