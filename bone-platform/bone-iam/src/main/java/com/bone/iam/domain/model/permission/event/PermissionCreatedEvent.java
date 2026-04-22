package com.bone.iam.domain.model.permission.event;

import com.bone.core.domain.DomainEvent;
import com.bone.iam.domain.model.permission.Permission;
import com.bone.iam.domain.model.permission.vo.PermissionType;

public record PermissionCreatedEvent(Long permissionId, String code, String name, PermissionType type) implements DomainEvent {
    public PermissionCreatedEvent(Permission permission) {
        this(permission.getId().getValue(), permission.getCode().value(), permission.getName(), permission.getType());
    }
}