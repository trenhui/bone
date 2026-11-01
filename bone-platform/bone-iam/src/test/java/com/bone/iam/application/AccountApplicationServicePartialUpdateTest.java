package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.iam.application.command.UpdateAccountCommand;
import com.bone.iam.application.query.mapper.AccountDtoMapper;
import com.bone.iam.application.support.AccountRoleBindingSupport;
import com.bone.iam.application.support.PasswordPolicyValidator;
import com.bone.iam.application.support.TenantQuotaEnforcer;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.model.account.Account;
import com.bone.iam.domain.model.account.valueobject.Email;
import com.bone.iam.domain.model.account.valueobject.Username;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.DeptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 账号「部分更新」契约：只提交部分字段时，**未提交的资料字段不得被清空**。
 *
 * <p><b>为什么这是数据丢失级缺陷</b>：仓储层是全列 UPDATE，聚合原先无条件覆盖 {@code realName / phone}，
 * 于是「只改角色」这类编辑请求会把用户的真实姓名与手机号抹掉；而 {@code email} 又根本没进更新路径——
 * 前端表单提交了也被静默丢弃，管理员改邮箱永远不生效。两者在界面上都表现为「资料莫名变了 / 改不动」。
 */
class AccountApplicationServicePartialUpdateTest {

  private final AccountRepository accountRepository = mock(AccountRepository.class);
  private final AccountRoleBindingSupport roleBindingSupport =
      mock(AccountRoleBindingSupport.class);

  private AccountApplicationService service() {
    return new AccountApplicationService(
        accountRepository,
        mock(PasswordEncoder.class),
        roleBindingSupport,
        mock(PasswordPolicyValidator.class),
        mock(TenantQuotaEnforcer.class),
        mock(AccountDtoMapper.class),
        mock(TenantProvider.class),
        mock(DeptRepository.class));
  }

  private Account existing() {
    return Account.create(
        9104L, Username.of("emp1"), "hash", Email.of("emp1@bone.local"), "13800001111", "林知远", 0L);
  }

  private UpdateAccountCommand command() {
    UpdateAccountCommand cmd = new UpdateAccountCommand();
    cmd.setId(9104L);
    return cmd;
  }

  private Account captured() {
    ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository).update(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("只提交角色时，真实姓名与手机号不得被清空")
  void keepsProfileFieldsWhenOnlyRolesSubmitted() {
    when(accountRepository.findById(9104L)).thenReturn(existing());
    UpdateAccountCommand cmd = command();
    cmd.setRoleIds(new Long[] {1L});

    service().update(cmd);

    Account saved = captured();
    assertThat(saved.getRealName()).isEqualTo("林知远");
    assertThat(saved.getPhone()).isEqualTo("13800001111");
    assertThat(saved.getEmail().value()).isEqualTo("emp1@bone.local");
  }

  @Test
  @DisplayName("提交的姓名与手机号照常生效")
  void appliesSubmittedProfileFields() {
    when(accountRepository.findById(9104L)).thenReturn(existing());
    UpdateAccountCommand cmd = command();
    cmd.setRealName("林知远改");
    cmd.setPhone("13900002222");

    service().update(cmd);

    Account saved = captured();
    assertThat(saved.getRealName()).isEqualTo("林知远改");
    assertThat(saved.getPhone()).isEqualTo("13900002222");
  }

  @Test
  @DisplayName("邮箱可更新（此前被静默丢弃，管理员改邮箱永远不生效）")
  void appliesSubmittedEmail() {
    when(accountRepository.findById(9104L)).thenReturn(existing());
    UpdateAccountCommand cmd = command();
    cmd.setEmail("new-mail@bone.local");

    service().update(cmd);

    assertThat(captured().getEmail().value()).isEqualTo("new-mail@bone.local");
  }

  @Test
  @DisplayName("managerId=0 表示清空主管（与 null=不变更区分开）")
  void managerIdZeroClearsManager() {
    Account account = existing();
    account.changeManager(9105L);
    when(accountRepository.findById(9104L)).thenReturn(account);
    UpdateAccountCommand cmd = command();
    cmd.setManagerId(0L);

    service().update(cmd);

    assertThat(captured().getManagerId()).isNull();
  }

  @Test
  @DisplayName("不传主管时保持原值（null = 不变更），且不触碰角色绑定")
  void nullManagerKeepsExistingManager() {
    Account account = existing();
    account.changeManager(9105L);
    when(accountRepository.findById(9104L)).thenReturn(account);
    UpdateAccountCommand cmd = command();
    cmd.setRealName("只改名字");

    service().update(cmd);

    assertThat(captured().getManagerId()).isEqualTo(9105L);
    verify(roleBindingSupport, never()).replaceBindings(any(), any(), any());
  }
}
