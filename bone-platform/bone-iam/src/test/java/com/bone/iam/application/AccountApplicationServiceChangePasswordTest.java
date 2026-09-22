package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.ChangeMyPasswordCommand;
import com.bone.iam.application.policy.PasswordPolicyValidator;
import com.bone.iam.domain.model.account.Account;
import com.bone.iam.domain.model.account.vo.Email;
import com.bone.iam.domain.model.account.vo.Username;
import com.bone.iam.domain.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** AccountApplicationService#changePassword 单元测试（原 ChangeMyPasswordCommandHandler 逻辑已内联）。 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceChangePasswordTest {

  @Mock AccountRepository accountRepository;
  @Mock PasswordEncoder passwordEncoder;
  @Mock PasswordPolicyValidator passwordPolicyValidator;

  @InjectMocks AccountApplicationService accountApplicationService;

  @Test
  void changePasswordSuccessfully() {
    Account account =
        Account.create(1L, Username.of("bob"), "old-hash", Email.of("b@b.com"), null, null, 0L);
    when(accountRepository.findById(1L)).thenReturn(account);
    when(passwordEncoder.matches("old-pass", "old-hash")).thenReturn(true);
    when(passwordEncoder.encode("NewP@ss123")).thenReturn("new-hash");

    ChangeMyPasswordCommand cmd = new ChangeMyPasswordCommand();
    cmd.setAccountId(1L);
    cmd.setOldPassword("old-pass");
    cmd.setNewPassword("NewP@ss123");

    accountApplicationService.changePassword(cmd);

    verify(passwordPolicyValidator, times(1)).assertAcceptable("NewP@ss123");
    verify(accountRepository, times(1)).update(account);
  }

  @Test
  void wrongOldPasswordThrows() {
    Account account =
        Account.create(1L, Username.of("bob"), "old-hash", Email.of("b@b.com"), null, null, 0L);
    when(accountRepository.findById(1L)).thenReturn(account);
    when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

    ChangeMyPasswordCommand cmd = new ChangeMyPasswordCommand();
    cmd.setAccountId(1L);
    cmd.setOldPassword("wrong");
    cmd.setNewPassword("NewP@ss123");

    assertThatThrownBy(() -> accountApplicationService.changePassword(cmd))
        .isInstanceOf(BizException.class);
    verify(accountRepository, never()).update(any());
  }

  @Test
  void nullAccountIdThrows() {
    ChangeMyPasswordCommand cmd = new ChangeMyPasswordCommand();
    cmd.setAccountId(null);

    assertThatThrownBy(() -> accountApplicationService.changePassword(cmd))
        .isInstanceOf(BizException.class);
    verify(accountRepository, never()).findById(anyLong());
  }
}
