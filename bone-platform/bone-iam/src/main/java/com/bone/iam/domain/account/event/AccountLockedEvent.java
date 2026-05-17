package com.bone.iam.domain.account.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public class AccountLockedEvent implements com.bone.core.domain.DomainEvent {
    private final Long accountId;
    private final LocalDateTime lockedAt;
}
