package com.bone.core.domain;

import com.bone.core.domain.entity.Tenantable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 多租户聚合根：{@link AggregateRoot} + {@code tenantId}。不继承 {@link
 * com.bone.core.tenant.TenantAbstractEntity}，避免与域内 {@link java.time.LocalDateTime}
 * 审计字段冲突；审计由子类自行声明（见 bone-iam {@code Account}）。
 */
@Getter
public abstract class TenantAggregateRoot<ID> extends AggregateRoot<ID> implements Tenantable<ID> {

  @Schema(description = "租户id")
  private ID tenantId;

  @Override
  public ID getTenantId() {
    return tenantId;
  }

  public void setTenantId(ID tenantId) {
    this.tenantId = tenantId;
  }
}
