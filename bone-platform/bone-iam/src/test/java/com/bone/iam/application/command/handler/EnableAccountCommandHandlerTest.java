package com.bone.iam.application.command.handler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.NotFoundException;
import com.bone.iam.application.command.cmd.EnableAccountCommand;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.account.vo.AccountStatus;
import com.bone.iam.domain.account.vo.Email;
import com.bone.iam.domain.account.vo.Username;
import com.bone.iam.domain.repository.AccountRepository;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnableAccountCommandHandlerTest {

  @Mock AccountRepository accountRepository;

  EnableAccountCommandHandler handler;

  @BeforeEach
  void setUp() {
    handler = new EnableAccountCommandHandler(accountRepository);
  }

  @Test
  void enableDisabledAccountSuccessfully() {
    Account account = mkAccount();
    setField(account, "status", AccountStatus.DISABLED);
    when(accountRepository.findById(1L)).thenReturn(account);

    EnableAccountCommand cmd = new EnableAccountCommand();
    cmd.setId(1L);

    handler.handle(cmd);

    verify(accountRepository, times(1)).update(account);
  }

  @Test
  void enableNonExistentAccountThrowsNotFound() {
    when(accountRepository.findById(999L)).thenReturn(null);

    EnableAccountCommand cmd = new EnableAccountCommand();
    cmd.setId(999L);

    assertThatThrownBy(() -> handler.handle(cmd)).isInstanceOf(NotFoundException.class);
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
