package com.bone.iam.domain.model.permission.event;

import com.bone.core.domain.DomainEvent;
import com.bone.iam.domain.model.permission.Permission;
import com.bone.iam.domain.model.permission.vo.PermissionId;

public record PermissionCreatedEvent(PermissionId permissionId, String permissionName, String permissionCode, Long tenantId) implements DomainEvent {
    public PermissionCreatedEvent(Permission permission) {
        this(permission.getId(), permission.getName(), permission.getCode().value(), null);
    }
}
