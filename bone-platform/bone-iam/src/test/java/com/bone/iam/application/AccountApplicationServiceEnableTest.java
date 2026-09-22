package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.EnableAccountCommand;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.model.account.Account;
import com.bone.iam.domain.model.account.valueobject.AccountStatus;
import com.bone.iam.domain.model.account.valueobject.Email;
import com.bone.iam.domain.model.account.valueobject.Username;
import com.bone.iam.domain.repository.AccountRepository;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** AccountApplicationService#enable 单元测试（原 EnableAccountCommandHandler 逻辑已内联）。 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceEnableTest {

  @Mock AccountRepository accountRepository;

  @InjectMocks AccountApplicationService accountApplicationService;

  @Test
  void enableDisabledAccountSuccessfully() {
    Account account = mkAccount();
    setField(account, "status", AccountStatus.DISABLED);
    when(accountRepository.findById(1L)).thenReturn(account);

    EnableAccountCommand cmd = new EnableAccountCommand();
    cmd.setId(1L);

    accountApplicationService.enable(cmd);

    verify(accountRepository, times(1)).update(account);
  }

  @Test
  void enableNonExistentAccountThrowsNotFound() {
    when(accountRepository.findById(999L)).thenReturn(null);

    EnableAccountCommand cmd = new EnableAccountCommand();
    cmd.setId(999L);

    assertThatThrownBy(() -> accountApplicationService.enable(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 404)
        .hasMessageContaining(IamErrorCodes.ACCOUNT_NOT_FOUND);
  }

  private static Account mkAccount() {
    return Account.create(1L, Username.of("bob"), "hash", Email.of("b@b.com"), null, null, 0L);
  }

  private static void setField(Object obj, String name, Object value) {
    try {
      Field f = findField(obj.getClass(), name);
      f.setAccessible(true);
      f.set(obj, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
    Class<?> c = cls;
    while (c != null) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException ignored) {
        c = c.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }
}
