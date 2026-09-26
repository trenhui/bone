package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.CreateAccountCommand;
import com.bone.iam.application.support.AccountRoleBindingSupport;
import com.bone.iam.application.support.PasswordPolicyValidator;
import com.bone.iam.application.support.TenantQuotaEnforcer;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.model.account.Account;
import com.bone.iam.domain.model.account.valueobject.Email;
import com.bone.iam.domain.model.account.valueobject.Username;
import com.bone.iam.domain.repository.AccountRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * AccountApplicationService#create 单元测试（原 CreateAccountCommandHandler 逻辑已内联）。
 *
 * <p>覆盖三条主路径：
 *
 * <ol>
 *   <li>正常创建账号 → 返回 ID
 *   <li>用户名已存在 → 抛 BizException
 *   <li>密码策略不合规 → 抛异常
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AccountApplicationServiceCreateTest {

  @Mock AccountRepository accountRepository;
  @Mock PasswordEncoder passwordEncoder;
  @Mock AccountRoleBindingSupport accountRoleBindingSupport;
  @Mock PasswordPolicyValidator passwordPolicyValidator;
  @Mock TenantQuotaEnforcer tenantQuotaEnforcer;
  @Mock TenantProvider tenantProvider;
  @Mock com.bone.iam.domain.repository.DeptRepository deptRepository;

  @InjectMocks AccountApplicationService accountApplicationService;

  @BeforeEach
  void setUp() {
    // 缺省平台/无租户上下文（null）→ create 走命令传值的既有路径；个别用例再覆盖为具体租户。
    lenient().when(tenantProvider.currentTenantIdOrNull()).thenReturn(null);
  }

  @Test
  void createAccountSuccessfully() {
    CreateAccountCommand cmd = new CreateAccountCommand();
    cmd.setUsername("newuser");
    cmd.setPassword("P@ssw0rd123");
    cmd.setEmail("new@bone.com");
    cmd.setPhone("13800000000");
    cmd.setRealName("New User");
    cmd.setTenantId(1L);
    cmd.setDeptId(9L);
    cmd.setRoleIds(new Long[] {1L});

    when(accountRepository.findByUsernameInTenant("newuser")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("P@ssw0rd123")).thenReturn("hashed-pwd");
    when(deptRepository.findById(9L))
        .thenReturn(com.bone.iam.domain.model.dept.Dept.create("研发部", null, 1, 1, 1L));

    // 模拟 save 后 id 被回填
    when(accountRepository.save(any(Account.class)))
        .thenAnswer(
            invocation -> {
              Account acct = invocation.getArgument(0);
              setField(acct, "id", 42L);
              return acct.getId();
            });

    Long result = accountApplicationService.create(cmd);

    assertThat(result).isEqualTo(42L);
    verify(passwordPolicyValidator, times(1)).assertAcceptable("P@ssw0rd123");
    verify(tenantQuotaEnforcer, times(1)).assertCanAddAccount(1L);
    verify(accountRepository, times(1)).save(any(Account.class));
    verify(accountRoleBindingSupport, times(1)).replaceBindings(42L, 1L, new Long[] {1L});
  }

  /** 部门归属：本租户内部门可挂，跨租户部门一律拒绝（防把成员挂到别的租户的组织树上）。 */
  @Test
  void createAssignsDeptInSameTenantAndRejectsForeignDept() {
    CreateAccountCommand cmd = new CreateAccountCommand();
    cmd.setUsername("deptuser");
    cmd.setPassword("P@ssw0rd123");
    cmd.setEmail("dept@bone.com");
    cmd.setTenantId(100L);
    cmd.setDeptId(9L);
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(100L);
    when(accountRepository.findByUsernameInTenant("deptuser")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("P@ssw0rd123")).thenReturn("hashed-pwd");
    when(deptRepository.findById(9L))
        .thenReturn(com.bone.iam.domain.model.dept.Dept.create("研发部", null, 1, 1, 100L));
    when(accountRepository.save(any(Account.class)))
        .thenAnswer(
            invocation -> {
              Account acct = invocation.getArgument(0);
              setField(acct, "id", 43L);
              return acct.getId();
            });

    assertThat(accountApplicationService.create(cmd)).isEqualTo(43L);

    // 同一租户的另一部门 id：换成本租户外的部门 → 404（不泄露该 id 属于谁）
    when(deptRepository.findById(9L))
        .thenReturn(com.bone.iam.domain.model.dept.Dept.create("他租户部门", null, 1, 1, 200L));
    assertThatThrownBy(() -> accountApplicationService.create(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 404)
        .hasMessageContaining(com.bone.iam.common.IamErrorCodes.DEPT_NOT_FOUND);
  }

  @Test
  void duplicateUsernameThrowsBizException() {
    CreateAccountCommand cmd = new CreateAccountCommand();
    cmd.setUsername("existing");
    cmd.setPassword("P@ssw0rd123");
    cmd.setEmail("dup@bone.com");
    cmd.setTenantId(1L);

    Account existing =
        Account.create(
            1L, Username.of("existing"), "hash", Email.of("dup@bone.com"), null, null, 1L);
    when(accountRepository.findByUsernameInTenant("existing")).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> accountApplicationService.create(cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("用户名已存在");

    verify(accountRepository, never()).save(any());
    verify(accountRoleBindingSupport, never()).replaceBindings(anyLong(), anyLong(), any());
  }

  @Test
  void weakPasswordPropagatesException() {
    CreateAccountCommand cmd = new CreateAccountCommand();
    cmd.setUsername("newuser");
    cmd.setPassword("1");
    cmd.setEmail("new@bone.com");
    cmd.setTenantId(0L);

    org.mockito.Mockito.doThrow(new BizException(400, "密码太弱"))
        .when(passwordPolicyValidator)
        .assertAcceptable("1");

    assertThatThrownBy(() -> accountApplicationService.create(cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("密码太弱");

    verify(accountRepository, never()).findByUsernameInTenant(any());
    verify(accountRepository, never()).save(any());
  }

  @Test
  void nullTenantIdDefaultsToZero() {
    CreateAccountCommand cmd = new CreateAccountCommand();
    cmd.setUsername("user2");
    cmd.setPassword("P@ssw0rd123");
    cmd.setEmail("u2@bone.com");
    cmd.setTenantId(null);

    when(accountRepository.findByUsernameInTenant("user2")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("P@ssw0rd123")).thenReturn("hashed");
    cmd.setDeptId(8L);
    when(deptRepository.findById(8L))
        .thenReturn(com.bone.iam.domain.model.dept.Dept.create("平台部", null, 1, 1, 0L));

    when(accountRepository.save(any(Account.class)))
        .thenAnswer(
            invocation -> {
              Account acct = invocation.getArgument(0);
              setField(acct, "id", 99L);
              return acct.getId();
            });

    Long result = accountApplicationService.create(cmd);

    assertThat(result).isEqualTo(99L);
    verify(tenantQuotaEnforcer, times(1)).assertCanAddAccount(0L);
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
