package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.iam.application.command.ResetPasswordCommand;
import com.bone.iam.application.policy.PasswordPolicyValidator;
import com.bone.iam.domain.model.account.Account;
import com.bone.iam.domain.model.account.valueobject.Email;
import com.bone.iam.domain.model.account.valueobject.Username;
import com.bone.iam.domain.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** AccountApplicationService#resetPassword 单元测试（原 ResetPasswordCommandHandler 逻辑已内联）。 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceResetPasswordTest {

  @Mock AccountRepository accountRepository;
  @Mock PasswordEncoder passwordEncoder;
  @Mock PasswordPolicyValidator passwordPolicyValidator;

  @InjectMocks AccountApplicationService accountApplicationService;

  @Test
  void resetPasswordSuccessfully() {
    Account account =
        Account.create(1L, Username.of("bob"), "old-hash", Email.of("b@b.com"), null, null, 0L);
    when(accountRepository.findById(1L)).thenReturn(account);
    when(passwordEncoder.encode("NewP@ss123")).thenReturn("new-hash");

    ResetPasswordCommand cmd = new ResetPasswordCommand();
    cmd.setId(1L);
    cmd.setNewPassword("NewP@ss123");

    accountApplicationService.resetPassword(cmd);

    verify(passwordPolicyValidator, times(1)).assertAcceptable("NewP@ss123");
    verify(passwordEncoder, times(1)).encode("NewP@ss123");
    verify(accountRepository, times(1)).update(account);
  }

  @Test
  void resetPasswordForNonExistentAccountThrows() {
    when(accountRepository.findById(999L)).thenReturn(null);

    ResetPasswordCommand cmd = new ResetPasswordCommand();
    cmd.setId(999L);
    cmd.setNewPassword("NewP@ss123");

    assertThatThrownBy(() -> accountApplicationService.resetPassword(cmd))
        .isInstanceOf(RuntimeException.class);
  }
}
