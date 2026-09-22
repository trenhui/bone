package com.bone.iam.domain.model.account.event;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AccountLockedEvent implements com.bone.core.domain.DomainEvent {
  private final Long accountId;
  private final LocalDateTime lockedAt;
}
