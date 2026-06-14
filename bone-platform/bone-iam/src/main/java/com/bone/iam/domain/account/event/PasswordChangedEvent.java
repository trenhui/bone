package com.bone.iam.domain.account.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class PasswordChangedEvent implements com.bone.core.domain.DomainEvent {
  private final Long accountId;
}
