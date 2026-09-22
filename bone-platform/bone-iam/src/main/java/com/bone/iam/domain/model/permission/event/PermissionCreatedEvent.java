package com.bone.iam.domain.model.permission.event;

import com.bone.core.domain.DomainEvent;
import com.bone.iam.domain.model.permission.Permission;
import lombok.Getter;

@Getter
public class PermissionCreatedEvent implements DomainEvent {
  private final Long permissionId;
  private final String permissionCode;

  public PermissionCreatedEvent(Permission permission) {
    this.permissionId = permission.getId();
    this.permissionCode = permission.getCode();
  }
}
