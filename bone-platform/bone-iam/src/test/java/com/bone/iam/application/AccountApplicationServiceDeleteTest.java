package com.bone.iam.application;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bone.iam.application.service.AccountRoleBindingService;
import com.bone.iam.domain.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** AccountApplicationService#delete 单元测试（原 DeleteAccountCommandHandler 逻辑已内联）。 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceDeleteTest {

  @Mock AccountRepository accountRepository;
  @Mock AccountRoleBindingService accountRoleBindingService;

  @InjectMocks AccountApplicationService accountApplicationService;

  @Test
  void deleteAccountClearsRolesThenDeletes() {
    accountApplicationService.delete(42L);

    verify(accountRoleBindingService, times(1)).replaceBindings(eq(42L), eq(null), eq(new Long[0]));
    verify(accountRepository, times(1)).deleteById(42L);
  }
}
