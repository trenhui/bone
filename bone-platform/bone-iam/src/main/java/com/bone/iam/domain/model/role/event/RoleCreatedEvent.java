package com.bone.iam.domain.model.role.event;

import com.bone.core.domain.DomainEvent;
import com.bone.iam.domain.model.role.Role;
import com.bone.iam.domain.model.role.vo.RoleId;

public record RoleCreatedEvent(RoleId roleId, String roleName, Long tenantId) implements DomainEvent {
    public RoleCreatedEvent(Role role) {
        this(role.getId(), role.getName().value(), role.getTenantId());
    }
}