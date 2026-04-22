package com.bone.iam.domain.model.user.event;

import com.bone.core.domain.DomainEvent;
import com.bone.iam.domain.model.user.User;
import com.bone.iam.domain.model.user.vo.UserId;

public record UserCreatedEvent(UserId userId, String username, String email, Long tenantId) implements DomainEvent {
    public UserCreatedEvent(User user) {
        this(user.getId(), user.getUsername().value(), user.getEmail().value(), user.getTenantId());
    }
}