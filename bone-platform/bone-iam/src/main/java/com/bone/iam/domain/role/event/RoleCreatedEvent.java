package com.bone.iam.domain.role.event;

import com.bone.core.domain.DomainEvent;
import com.bone.iam.domain.role.Role;
import lombok.Getter;

@Getter
public class RoleCreatedEvent implements DomainEvent {
    private final Long roleId;
    private final String roleName;

    public RoleCreatedEvent(Role role) {
        this.roleId = role.getId();
        this.roleName = role.getName();
    }
}
