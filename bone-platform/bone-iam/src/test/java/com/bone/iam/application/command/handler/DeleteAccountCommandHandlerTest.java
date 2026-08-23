package com.bone.iam.application.command.handler;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bone.iam.application.service.AccountRoleBindingService;
import com.bone.iam.domain.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteAccountCommandHandlerTest {

  @Mock AccountRepository accountRepository;
  @Mock AccountRoleBindingService accountRoleBindingService;

  DeleteAccountCommandHandler handler;

  @BeforeEach
  void setUp() {
    handler = new DeleteAccountCommandHandler(accountRepository, accountRoleBindingService);
  }

  @Test
  void deleteAccountClearsRolesThenDeletes() {
    handler.handle(42L);

    verify(accountRoleBindingService, times(1)).replaceBindings(eq(42L), eq(null), eq(new Long[0]));
    verify(accountRepository, times(1)).deleteById(42L);
  }
}
