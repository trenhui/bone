package com.bone.iam.domain.model.account.event;

import com.bone.iam.domain.model.account.Account;
import lombok.Getter;

@Getter
public class AccountCreatedEvent implements com.bone.core.domain.DomainEvent {
  private final Long accountId;
  private final String username;
  private final Long tenantId;

  public AccountCreatedEvent(Account account) {
    this.accountId = account.getId();
    this.username = account.getUsername().value();
    this.tenantId = account.getTenantId();
  }
}
