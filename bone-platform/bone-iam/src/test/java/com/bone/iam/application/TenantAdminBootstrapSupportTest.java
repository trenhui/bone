package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.iam.application.support.AccountRoleBindingSupport;
import com.bone.iam.application.support.TenantAdminBootstrapSupport;
import com.bone.iam.application.support.TenantAdminBootstrapSupport.TenantAdminAccount;
import com.bone.iam.domain.model.account.Account;
import com.bone.iam.domain.model.permission.Permission;
import com.bone.iam.domain.model.role.Role;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.PermissionRepository;
import com.bone.iam.domain.repository.RolePermissionRepository;
import com.bone.iam.domain.repository.RoleRepository;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link TenantAdminBootstrapSupport#bootstrap} 契约测试（详设 §2.9）。
 *
 * <p>覆盖：租户管理员角色编码/归属、权限绑定只含租户域白名单码（平台域码不授出）、账号派生用户名与 邮箱回退、账号-角色绑定，以及初始密码强度（≥8 位且非弱口令清单）。
 */
@ExtendWith(MockitoExtension.class)
class TenantAdminBootstrapSupportTest {

  @Mock RoleRepository roleRepository;

  @Mock AccountRepository accountRepository;

  @Mock AccountRoleBindingSupport accountRoleBindingSupport;

  @Mock RolePermissionRepository rolePermissionRepository;

  @Mock PermissionRepository permissionRepository;

  @Mock PasswordEncoder passwordEncoder;

  @InjectMocks TenantAdminBootstrapSupport support;

  /** Permission 无公有构造/setter，测试中以 mock 构造目录条目；getId 用 lenient（平台域码不会被消费）。 */
  private static Permission perm(long id, String code) {
    Permission p = mock(Permission.class);
    org.mockito.Mockito.lenient().when(p.getId()).thenReturn(id);
    when(p.getCode()).thenReturn(code);
    return p;
  }

  @Test
  void bootstrapsRoleAccountAndBindingsWithinTenantScope() {
    when(roleRepository.save(any(Role.class))).thenReturn(500L);
    when(accountRepository.save(any(Account.class)))
        .thenAnswer(
            inv -> {
              Account a = inv.getArgument(0);
              a.setId(900L);
              return 900L;
            });
    when(passwordEncoder.encode(any(CharSequence.class))).thenReturn("$2a$10$hash");
    // 目录中混入平台域码：iam:permissions:write / iam:tenants:write / iam:sessions:write 不得授出租户管理员
    // 注意：先完成 perm() 内部的 mock 打桩，再对外层 listAll() 打桩，避免 UnfinishedStubbingException
    List<Permission> catalog =
        List.of(
            perm(1L, "iam:accounts:read"),
            perm(2L, "iam:accounts:write"),
            perm(3L, "iam:roles:read"),
            perm(4L, "iam:roles:write"),
            perm(5L, "iam:depts:read"),
            perm(6L, "iam:depts:write"),
            perm(7L, "iam:menus:read"),
            perm(8L, "iam:menus:write"),
            perm(9L, "iam:audit:read"),
            perm(10L, "iam:audit:write"),
            perm(11L, "iam:permissions:write"),
            perm(12L, "iam:tenants:write"),
            perm(13L, "iam:sessions:write"));
    when(permissionRepository.listAll()).thenReturn(catalog);

    TenantAdminAccount result = support.bootstrap(77L, "ACME", "boss@acme.com");

    assertThat(result.accountId()).isEqualTo(900L);
    assertThat(result.username()).isEqualTo("acme_admin");
    assertThat(result.initialPassword()).startsWith("Bone-");
    assertThat(result.initialPassword().length()).isGreaterThanOrEqualTo(8);

    ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
    verify(roleRepository).save(roleCaptor.capture());
    assertThat(roleCaptor.getValue().getCode()).isEqualTo("TENANT_ADMIN_acme");
    assertThat(roleCaptor.getValue().getTenantId()).isEqualTo(77L);

    verify(rolePermissionRepository)
        .replaceBindingsForRole(
            eq(500L),
            argThat(
                ids ->
                    ids != null
                        && ids.length == 10
                        && Arrays.stream(ids).noneMatch(id -> id >= 11L))); // 平台域码未授出

    ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository).save(accountCaptor.capture());
    assertThat(accountCaptor.getValue().getTenantId()).isEqualTo(77L);
    assertThat(accountCaptor.getValue().getEmail().value()).isEqualTo("boss@acme.com");

    verify(accountRoleBindingSupport)
        .replaceBindings(eq(900L), eq(77L), argThat(r -> r != null && r.length == 1));
  }

  @Test
  void blankAdminEmailFallsBackToLocalAddress() {
    when(roleRepository.save(any(Role.class))).thenReturn(501L);
    when(accountRepository.save(any(Account.class)))
        .thenAnswer(
            inv -> {
              Account a = inv.getArgument(0);
              a.setId(901L);
              return 901L;
            });
    when(passwordEncoder.encode(any(CharSequence.class))).thenReturn("$2a$10$hash");
    when(permissionRepository.listAll()).thenReturn(List.of());

    TenantAdminAccount result = support.bootstrap(78L, "BETA", "  ");

    assertThat(result.username()).isEqualTo("beta_admin");

    ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
    verify(accountRepository).save(accountCaptor.capture());
    assertThat(accountCaptor.getValue().getEmail().value()).isEqualTo("beta_admin@tenant.local");
  }
}
