package com.bone.iam.domain.account.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AccountEnabledEvent implements com.bone.core.domain.DomainEvent {
    private final Long accountId;
}
