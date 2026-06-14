package com.bone.iam.domain.permission.event;

import com.bone.core.domain.DomainEvent;
import com.bone.iam.domain.permission.Permission;
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
