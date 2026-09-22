package com.bone.iam.application;

import com.bone.core.model.PageResult;
import com.bone.iam.application.binding.AccountRoleBindingService;
import com.bone.iam.application.command.ChangeMyPasswordCommand;
import com.bone.iam.application.command.CreateAccountCommand;
import com.bone.iam.application.command.DisableAccountCommand;
import com.bone.iam.application.command.EnableAccountCommand;
import com.bone.iam.application.command.ResetPasswordCommand;
import com.bone.iam.application.command.UpdateAccountCommand;
import com.bone.iam.application.command.UpdateMyProfileCommand;
import com.bone.iam.application.policy.PasswordPolicyValidator;
import com.bone.iam.application.policy.TenantQuotaEnforcer;
import com.bone.iam.application.query.dto.AccountDTO;
import com.bone.iam.application.query.mapper.AccountDtoMapper;
import com.bone.iam.application.query.qry.AccountPageQuery;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.model.account.Account;
import com.bone.iam.domain.model.account.valueobject.AccountStatus;
import com.bone.iam.domain.model.account.valueobject.Email;
import com.bone.iam.domain.model.account.valueobject.Username;
import com.bone.iam.domain.repository.AccountRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号应用服务（Application Service First）——账号类用例的唯一入口。
 *
 * <p>原 {@code command.handler} 下的 9 个 Handler 与 {@code query.handler} 的 {@code
 * AccountPageQueryHandler} / {@code AccountDetailQueryHandler} 逻辑已全量内联于此（E-3.11 一次性大爆炸收敛）。
 * Controller 只依赖本类。各方法语义 / 异常 / 事务边界与原 Handler 一致（HTTP 契约不变）。
 *
 * <p>本类不出现读侧 DSL 与 {@code TenantContext}：分页条件下沉 {@link AccountRepository#findAccountPage}（本聚合读），
 * 租户取值走 {@link TenantProvider} 端口（E-2 / E-4.2）。
 */
@Service
@RequiredArgsConstructor
public class AccountApplicationService {

  private final AccountRepository accountRepository;
  private final PasswordEncoder passwordEncoder;
  private final AccountRoleBindingService accountRoleBindingService;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final TenantQuotaEnforcer tenantQuotaEnforcer;
  private final AccountDtoMapper accountDtoMapper;
  private final TenantProvider tenantProvider;

  @Transactional
  public Long create(CreateAccountCommand cmd) {
    passwordPolicyValidator.assertAcceptable(cmd.getPassword());
    long tenantId = cmd.getTenantId() != null ? cmd.getTenantId() : 0L;
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

    // id 由数据库 AUTO_INCREMENT 生成，metadata-sdk insert 后会回填
    Account account =
        Account.create(
            null, username, passwordHash, email, cmd.getPhone(), cmd.getRealName(), tenantId);
    accountRepository.save(account);
    accountRoleBindingService.replaceBindings(
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
      accountRoleBindingService.replaceBindings(
          account.getId(), account.getTenantId(), cmd.getRoleIds());
    }
  }

  @Transactional
  public void delete(Long id) {
    accountRoleBindingService.replaceBindings(id, null, new Long[0]);
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
    PageResult<Account> result =
        accountRepository.findAccountPage(
            qry.getKeyword(),
            qry.getStatus() != null ? AccountStatus.of(qry.getStatus()) : null,
            effectiveTenant,
            qry.getPage(),
            qry.getSize());
    List<AccountDTO> dtoList =
        result.getRecords().stream().map(AccountApplicationService::toPageDto).toList();
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
              dto.setRoleIds(accountRoleBindingService.listRoleIds(id).toArray(Long[]::new));
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
    dto.setLastLoginAt(account.getLastLoginAt());
    dto.setLastLoginIp(account.getLastLoginIp());
    dto.setCreatedAt(account.getCreatedAt());
    dto.setUpdatedAt(account.getUpdatedAt());
    return dto;
  }
}
