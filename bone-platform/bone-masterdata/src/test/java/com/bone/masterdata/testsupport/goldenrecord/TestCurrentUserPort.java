package com.bone.masterdata.testsupport.goldenrecord;

import com.bone.core.exception.DomainException;
import com.bone.masterdata.domain.gateway.CurrentUserPort;

/** 登录态联调夹具：租户可切换，用于验证「自报租户」被 fail-closed 拒绝。 */
public final class TestCurrentUserPort implements CurrentUserPort {

  private Long tenantId = 1L;
  private Long userId = 99L;

  public void setTenantId(Long tenantId) {
    this.tenantId = tenantId;
  }

  /** 分段审批需要切换具名账号（提交人 / 各段 specialist / 应用人）。 */
  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public void clearTenant() {
    this.tenantId = null;
  }

  @Override
  public Long currentUserId() {
    return userId;
  }

  @Override
  public Long requireUserId() {
    if (userId == null) {
      throw new DomainException("无登录用户");
    }
    return userId;
  }

  @Override
  public Long currentTenantId() {
    return tenantId;
  }

  @Override
  public Long requireTenantId() {
    if (tenantId == null) {
      throw new DomainException("无登录租户");
    }
    return tenantId;
  }
}
