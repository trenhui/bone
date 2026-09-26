package com.bone.iam.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.model.PageResult;
import com.bone.iam.application.command.ChangeMyPasswordCommand;
import com.bone.iam.application.command.CreateAccountCommand;
import com.bone.iam.application.command.DisableAccountCommand;
import com.bone.iam.application.command.EnableAccountCommand;
import com.bone.iam.application.command.ResetPasswordCommand;
import com.bone.iam.application.command.UpdateAccountCommand;
import com.bone.iam.application.command.UpdateMyProfileCommand;
import com.bone.iam.application.query.dto.AccountDTO;
import com.bone.iam.application.query.mapper.AccountDtoMapper;
import com.bone.iam.application.query.qry.AccountPageQuery;
import com.bone.iam.application.support.AccountRoleBindingSupport;
import com.bone.iam.application.support.PasswordPolicyValidator;
import com.bone.iam.application.support.TenantQuotaEnforcer;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.model.account.Account;
import com.bone.iam.domain.model.account.valueobject.AccountStatus;
import com.bone.iam.domain.model.account.valueobject.Email;
import com.bone.iam.domain.model.account.valueobject.Username;
import com.bone.iam.domain.model.dept.Dept;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.DeptRepository;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号应用服务（Application Service First）——账号类用例的唯一入口。
 *
 * <p>/*
 *
 * <p>原 {@code command.handler} 下的 9 个 Handler 与 {@code query.handler} 的 {@code
 * AccountPageQueryHandler} / {@code AccountDetailQueryHandler} 逻辑已全量内联于此（E-3.11 一次性大爆炸收敛）。
 * Controller 只依赖本类。各方法语义 / 异常 / 事务边界与原 Handler 一致（HTTP 契约不变）。
 *
 * <p>/*
 *
 * <p>本类不出现读侧 DSL 与 {@code TenantContext}：分页条件下沉 {@link AccountRepository#findAccountPage}（本聚合读），
 * 租户取值走 {@link TenantProvider} 端口（E-2 / E-4.2）。
 */
/*
 * <p><b>不发 DomainEvent 豁免（E-5.4）</b>：本服务管理的聚合（Account）当前不发布领域事件，其创建/启用/禁用/锁定/改密均属内部状态迁移、下游无上下文需感知；若将来接入事件发布，须改为调用 publishFrom 并移除本豁免。
 */
@Service
@RequiredArgsConstructor
@NoDomainEvent
public class AccountApplicationService {

  private final AccountRepository accountRepository;
  private final PasswordEncoder passwordEncoder;
  private final AccountRoleBindingSupport accountRoleBindingSupport;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final TenantQuotaEnforcer tenantQuotaEnforcer;
  private final AccountDtoMapper accountDtoMapper;
  private final TenantProvider tenantProvider;

  /** 跨聚合读：仅用于部门归属校验、子树展开与部门名装配（{@link Dept} 属另一聚合，只取 ID 与名称）。 */
  private final DeptRepository deptRepository;

  @Transactional
  public Long create(CreateAccountCommand cmd) {
    passwordPolicyValidator.assertAcceptable(cmd.getPassword());
    // 写侧租户归属：租户上下文 > 0 时强制本租户（命令传其他 tenantId 视为越权企图，直接忽略，详设 §2.9）；
    // 平台租户（0）/无上下文（平台管理员代操作）才接受命令传入值，均缺省落平台租户 0。
    long tenantId;
    Long fromContext = tenantProvider.currentTenantIdOrNull();
    if (fromContext != null && fromContext != 0L) {
      tenantId = fromContext;
    } else {
      tenantId = cmd.getTenantId() != null ? cmd.getTenantId() : 0L;
    }
    tenantQuotaEnforcer.assertCanAddAccount(tenantId);
    Username username = Username.of(cmd.getUsername());
    Email email = Email.of(cmd.getEmail());

    // 检查用户名是否已存在（**本租户内**：唯一键是 uk_iam_account_username (tenant_id, username)，
    // 跨租户允许重名。这里必须用本租户内查找 findByUsernameInTenant —— 登录专用的跨租户查找
    // findByUsernameForLoginAllTenants 会把「本租户唯一」错误升级为「全平台唯一」，导致不同租户同名被误判 409。）
    if (accountRepository.findByUsernameInTenant(username.value()).isPresent()) {
      throw IamErrors.of(IamErrorCodes.USERNAME_CONFLICT, "用户名已存在: " + cmd.getUsername());
    }

    String passwordHash = passwordEncoder.encode(cmd.getPassword());

    // 归属部门为必填项：所有账号必须归属一个（主）部门
    if (cmd.getDeptId() == null) {
      throw IamErrors.of(IamErrorCodes.DEPT_REQUIRED, "归属部门为必填项");
    }

    // id 由数据库 AUTO_INCREMENT 生成，metadata-sdk insert 后会回填
    Account account =
        Account.create(
            null, username, passwordHash, email, cmd.getPhone(), cmd.getRealName(), tenantId);
    account.changeDept(assertDeptBelongsToTenant(cmd.getDeptId(), tenantId));
    accountRepository.save(account);
    accountRoleBindingSupport.replaceBindings(
        account.getId(), account.getTenantId(), cmd.getRoleIds());
    return account.getId();
  }

  @Transactional
  public void update(UpdateAccountCommand cmd) {
    Account account = accountRepository.findById(cmd.getId());
    if (account == null) {
      throw IamErrors.of(IamErrorCodes.ACCOUNT_NOT_FOUND, "账户不存在");
    }
    account.updateProfile(cmd.getRealName(), cmd.getPhone(), null);
    if (cmd.getDeptId() != null) {
      // 语义：null=不变更（兼容未传该字段的存量调用方）；0=清空（已禁止，归属部门必填）；其他=本租户内已存在的部门
      if (cmd.getDeptId() == 0L) {
        throw IamErrors.of(IamErrorCodes.DEPT_REQUIRED, "归属部门为必填项，不能清空");
      }
      account.changeDept(assertDeptBelongsToTenant(cmd.getDeptId(), account.getTenantId()));
    }
    if (cmd.getStatus() != null) {
      AccountStatus status = AccountStatus.of(cmd.getStatus());
      if (status == AccountStatus.ENABLED && account.getStatus() != AccountStatus.ENABLED) {
        account.enable();
      } else if (status == AccountStatus.DISABLED
          && account.getStatus() != AccountStatus.DISABLED) {
        account.disable();
      }
    }
    accountRepository.update(account);
    if (cmd.getRoleIds() != null) {
      accountRoleBindingSupport.replaceBindings(
          account.getId(), account.getTenantId(), cmd.getRoleIds());
    }
  }

  @Transactional
  public void delete(Long id) {
    accountRoleBindingSupport.replaceBindings(id, null, new Long[0]);
    accountRepository.deleteById(id);
  }

  @Transactional
  public void enable(EnableAccountCommand cmd) {
    Account account = accountRepository.findById(cmd.getId());
    if (account == null) {
      throw IamErrors.of(IamErrorCodes.ACCOUNT_NOT_FOUND, "账户不存在");
    }
    try {
      account.enable();
    } catch (IllegalStateException ex) {
      throw IamErrors.of(IamErrorCodes.ACCOUNT_STATUS_CONFLICT, ex.getMessage());
    }
    accountRepository.update(account);
  }

  @Transactional
  public void disable(DisableAccountCommand cmd) {
    Account account = accountRepository.findById(cmd.getId());
    if (account == null) {
      throw IamErrors.of(IamErrorCodes.ACCOUNT_NOT_FOUND, "账户不存在");
    }
    try {
      account.disable();
    } catch (IllegalStateException ex) {
      throw IamErrors.of(IamErrorCodes.ACCOUNT_STATUS_CONFLICT, ex.getMessage());
    }
    accountRepository.update(account);
  }

  @Transactional
  public void resetPassword(ResetPasswordCommand cmd) {
    passwordPolicyValidator.assertAcceptable(cmd.getNewPassword());
    Account account = accountRepository.findById(cmd.getId());
    if (account == null) {
      throw IamErrors.of(IamErrorCodes.ACCOUNT_NOT_FOUND, "账户不存在");
    }
    String passwordHash = passwordEncoder.encode(cmd.getNewPassword());
    account.updatePassword(passwordHash);
    accountRepository.update(account);
  }

  /**
   * 自助改密：校验旧密码 → 应用弱口令策略 → 重哈希 → 更新 {@code password_updated_at}。
   *
   * <p>/*
   *
   * <p>注意：不会自动吊销 refresh token；如需"改密即下线"，前端应在改密成功后调用 {@code DELETE /accounts/{id}/sessions}（需
   * {@code iam:sessions:write} 权限）或登出。
   */
  @Transactional
  public void changePassword(ChangeMyPasswordCommand cmd) {
    if (cmd.getAccountId() == null) {
      throw IamErrors.of(IamErrorCodes.PROFILE_OWNERSHIP_DENIED);
    }
    Account account = accountRepository.findById(cmd.getAccountId());
    if (account == null) {
      throw IamErrors.of(IamErrorCodes.ACCOUNT_NOT_FOUND);
    }
    if (cmd.getOldPassword() == null
        || !passwordEncoder.matches(cmd.getOldPassword(), account.getPasswordHash())) {
      throw IamErrors.of(IamErrorCodes.OLD_PASSWORD_MISMATCH, "原密码不正确");
    }
    passwordPolicyValidator.assertAcceptable(cmd.getNewPassword());
    account.updatePassword(passwordEncoder.encode(cmd.getNewPassword()));
    accountRepository.update(account);
  }

  /** 自助更新本账号 profile；不允许跨账号操作。 */
  @Transactional
  public void updateMyProfile(UpdateMyProfileCommand cmd) {
    if (cmd.getAccountId() == null) {
      throw IamErrors.of(IamErrorCodes.PROFILE_OWNERSHIP_DENIED);
    }
    Account account = accountRepository.findById(cmd.getAccountId());
    if (account == null) {
      throw IamErrors.of(IamErrorCodes.ACCOUNT_NOT_FOUND);
    }
    account.updateProfile(cmd.getRealName(), cmd.getPhone(), cmd.getAvatarUrl());
    accountRepository.update(account);
  }

  @Transactional(readOnly = true)
  public PageResult<AccountDTO> page(AccountPageQuery qry) {
    Long effectiveTenant = resolveTenantFilter(qry.getTenantId());
    List<Dept> deptTree = qry.getDeptId() != null ? deptRepository.listAll() : List.of();
    PageResult<Account> result =
        accountRepository.findAccountPage(
            qry.getKeyword(),
            qry.getStatus() != null ? AccountStatus.of(qry.getStatus()) : null,
            effectiveTenant,
            resolveDeptSubtree(qry.getDeptId(), deptTree),
            qry.getPage(),
            qry.getSize());
    Map<Long, String> deptNames = deptNameIndex(deptTree);
    List<AccountDTO> dtoList =
        result.getRecords().stream()
            .map(
                account -> {
                  AccountDTO dto = toPageDto(account);
                  dto.setDeptName(deptNames.get(account.getDeptId()));
                  return dto;
                })
            .toList();
    return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
  }

  /** 账号详情（含角色 id）；非平台租户不可查看其他租户账号（防 IDOR，详设 §3.4 / §4.8）。 */
  @Transactional(readOnly = true)
  public Optional<AccountDTO> detail(Long id) {
    return Optional.ofNullable(accountRepository.findById(id))
        .filter(this::visibleToCaller)
        .map(
            account -> {
              AccountDTO dto = accountDtoMapper.toDto(account);
              dto.setRoleIds(accountRoleBindingSupport.listRoleIds(id).toArray(Long[]::new));
              if (account.getDeptId() != null) {
                // deptName 是展示态派生值，不落库；租户内回查。平台管理员（tenant 0）视角下租户部门不可见时自然降级为 null。
                Dept dept = deptRepository.findById(account.getDeptId());
                if (dept != null) {
                  dto.setDeptName(dept.getName());
                }
              }
              return dto;
            });
  }

  private boolean visibleToCaller(Account account) {
    Long caller = tenantProvider.currentTenantIdOrNull();
    return caller == null || caller == 0L || caller.equals(account.getTenantId());
  }

  /** 非平台租户（&gt; 0）强制按其过滤；平台租户（0）/无上下文回退到查询参数（详设 §3.4 / §4.8）。 */
  private Long resolveTenantFilter(Long fromQuery) {
    Long fromContext = tenantProvider.currentTenantIdOrNull();
    if (fromContext != null && fromContext != 0L) {
      return fromContext;
    }
    return fromQuery;
  }

  /**
   * 展开部门子树（含节点自身）。点父部门要能看到子部门成员——与组织树的点击预期一致；{@code visited} 兼作环保护
   * （部门树理论上无环，但脏数据/并发移动父节点时不能把查询打进死循环）。
   */
  private static Collection<Long> resolveDeptSubtree(Long deptId, List<Dept> deptTree) {
    if (deptId == null || deptTree.isEmpty()) {
      return null;
    }
    Map<Long, List<Dept>> childrenByParent =
        deptTree.stream()
            .filter(d -> d.getParentId() != null)
            .collect(Collectors.groupingBy(Dept::getParentId));
    Set<Long> visited = new HashSet<>();
    Deque<Long> queue = new ArrayDeque<>();
    queue.add(deptId);
    while (!queue.isEmpty()) {
      Long current = queue.poll();
      if (!visited.add(current)) {
        continue;
      }
      childrenByParent.getOrDefault(current, List.of()).forEach(child -> queue.add(child.getId()));
    }
    return visited;
  }

  private static Map<Long, String> deptNameIndex(List<Dept> deptTree) {
    Map<Long, String> index = new HashMap<>();
    deptTree.forEach(dept -> index.put(dept.getId(), dept.getName()));
    return index;
  }

  /** 部门必须存在且属于目标租户——防止把成员挂到别的租户的部门上（跨租户 IDOR）。 */
  private Long assertDeptBelongsToTenant(Long deptId, Long tenantId) {
    Dept dept = deptRepository.findById(deptId);
    if (dept == null || !java.util.Objects.equals(dept.getTenantId(), tenantId)) {
      throw IamErrors.of(IamErrorCodes.DEPT_NOT_FOUND, "部门不存在或不属于当前租户: " + deptId);
    }
    return dept.getId();
  }

  private static AccountDTO toPageDto(Account account) {
    AccountDTO dto = new AccountDTO();
    dto.setId(account.getId());
    dto.setUsername(account.getUsername().value());
    dto.setEmail(account.getEmail().value());
    dto.setPhone(account.getPhone());
    dto.setRealName(account.getRealName());
    dto.setAvatarUrl(account.getAvatarUrl());
    dto.setStatus(account.getStatus() != null ? account.getStatus().getCode() : null);
    dto.setIsAdmin(account.isAdmin());
    dto.setTenantId(account.getTenantId());
    dto.setDeptId(account.getDeptId());
    dto.setLastLoginAt(account.getLastLoginAt());
    dto.setLastLoginIp(account.getLastLoginIp());
    dto.setCreatedAt(account.getCreatedAt());
    dto.setUpdatedAt(account.getUpdatedAt());
    return dto;
  }
}
