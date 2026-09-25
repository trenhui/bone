package com.bone.iam.application.support;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.tenant.context.TenantContextRunner;
import com.bone.iam.domain.model.account.Account;
import com.bone.iam.domain.model.account.valueobject.Email;
import com.bone.iam.domain.model.account.valueobject.Username;
import com.bone.iam.domain.model.permission.Permission;
import com.bone.iam.domain.model.role.Role;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.PermissionRepository;
import com.bone.iam.domain.repository.RolePermissionRepository;
import com.bone.iam.domain.repository.RoleRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 租户管理员初始化（详设 §2.5 / §2.9）：创建租户时同事务产出「租户管理员角色 + 租户管理员账号 + 绑定」。
 *
 * <p>权限授予范围限定为<b>租户域</b>权限码（账号 / 角色 / 组织 / 菜单 / 审计），不含平台域（权限目录 {@code iam:permissions:*}、租户管理
 * {@code iam:tenants:*}、会话治理 {@code iam:sessions:*}）——平台/租户 管理员的授权边界由角色所绑权限码表达，而非新的账号类型（{@code
 * is_admin} 仍专指平台超管）。
 *
 * <p><b>不发 DomainEvent（E-5.4 豁免）</b>：本类的 save() 均为「租户创建」这一用例的<b>技术初始化中间态</b>——角色/账号/绑定随租户
 * 创建同事务产生，业务语义由租户创建入口统一表达（不单独发布"租户管理员已创建"事件）；任一步失败整体回滚，不存在独立的状态迁移。
 */
@Service
@NoDomainEvent
@RequiredArgsConstructor
public class TenantAdminBootstrapSupport {

  /** 租户管理员角色可被授予的权限码白名单（全部为租户域，平台域码即便登记也不会授出）。 */
  private static final Set<String> TENANT_ADMIN_PERMISSION_CODES =
      Set.of(
          "iam:accounts:read",
          "iam:accounts:write",
          "iam:roles:read",
          "iam:roles:write",
          "iam:depts:read",
          "iam:depts:write",
          "iam:menus:read",
          "iam:menus:write",
          "iam:audit:read",
          "iam:audit:write");

  private static final String INITIAL_PASSWORD_PREFIX = "Bone-";
  private static final SecureRandom RANDOM = new SecureRandom();

  private final RoleRepository roleRepository;
  private final AccountRepository accountRepository;
  private final AccountRoleBindingSupport accountRoleBindingSupport;
  private final RolePermissionRepository rolePermissionRepository;
  private final PermissionRepository permissionRepository;
  private final PasswordEncoder passwordEncoder;

  /** 初始化产物：管理员账号 id、登录用户名与<b>仅此一次可见</b>的初始密码（明文不落库）。 */
  public record TenantAdminAccount(Long accountId, String username, String initialPassword) {}

  /**
   * 为新租户初始化管理员角色与账号。必须在调用方事务内执行（{@link Propagation#MANDATORY}），与租户
   * 创建同生共死：任一步失败则整个租户创建回滚，避免产生"有租户无管理员"的悬空数据。
   *
   * <p><b>必须以 {@link TenantContextRunner#callAs} 切入新租户上下文执行</b>：创建租户的是平台管理员（上下文 tenantId=0），而 SDK
   * 写路径（ADR-0029 {@code resolveInsertTenantValue}）以可信上下文覆写实体的 {@code
   * tenantId}——若不切换上下文，角色/账号/绑定会被静默落到 {@code tenant_id=0}，租户管理员永远登录不出本租户身份。权限点与角色-权限绑定表为平台级数据（无
   * {@code tenant_id} 列），不受上下文影响。
   *
   * @param tenantId 已落库的租户 id
   * @param tenantCode 租户编码（用于派生角色编码与用户名）
   * @param adminEmail 管理员邮箱；为空时回退 {@code <username>@tenant.local}
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public TenantAdminAccount bootstrap(Long tenantId, String tenantCode, String adminEmail) {
    return TenantContextRunner.callAs(
        tenantId, () -> doBootstrap(tenantId, tenantCode, adminEmail));
  }

  private TenantAdminAccount doBootstrap(Long tenantId, String tenantCode, String adminEmail) {
    String codeLower = tenantCode.toLowerCase(Locale.ROOT);

    Role role =
        Role.create(
            "租户管理员", "TENANT_ADMIN_" + codeLower, "租户内置管理员角色（创建租户时自动初始化）", 1, tenantId, null);
    Long roleId = roleRepository.save(role);

    List<Long> permissionIds =
        permissionRepository.listAll().stream()
            .filter(p -> TENANT_ADMIN_PERMISSION_CODES.contains(p.getCode()))
            .map(Permission::getId)
            .toList();
    rolePermissionRepository.replaceBindingsForRole(roleId, permissionIds.toArray(Long[]::new));

    String username = codeLower + "_admin";
    String initialPassword = INITIAL_PASSWORD_PREFIX + randomSuffix();
    String email =
        adminEmail == null || adminEmail.isBlank() ? username + "@tenant.local" : adminEmail;
    Account account =
        Account.create(
            null,
            Username.of(username),
            passwordEncoder.encode(initialPassword),
            Email.of(email),
            null,
            "租户管理员",
            tenantId);
    accountRepository.save(account);

    accountRoleBindingSupport.replaceBindings(account.getId(), tenantId, new Long[] {roleId});
    return new TenantAdminAccount(account.getId(), username, initialPassword);
  }

  /** 初始密码 = 前缀 + 8 位随机字母数字（≥8 位且不属于常见弱口令清单）。 */
  private static String randomSuffix() {
    String alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
    StringBuilder sb = new StringBuilder(8);
    for (int i = 0; i < 8; i++) {
      sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
    }
    return sb.toString();
  }
}
