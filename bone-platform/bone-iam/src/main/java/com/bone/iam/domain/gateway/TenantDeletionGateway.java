package com.bone.iam.domain.gateway;

/** 租户级联清理（删除租户前 purge 子表数据）。实现位于 infrastructure。 */
public interface TenantDeletionGateway {

  /**
   * 物理删除/软删租户下 IAM 域数据（不含平台租户 {@code tenantId=0}）。
   *
   * @param tenantId 租户主键
   */
  void purgeTenantData(long tenantId);
}
