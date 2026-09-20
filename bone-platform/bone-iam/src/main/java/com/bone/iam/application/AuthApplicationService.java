package com.bone.iam.application;

import com.bone.core.capability.Capability;
import com.bone.core.exception.BizException;
import com.bone.core.exception.NotFoundException;
import com.bone.core.security.jwt.JwtConfig;
import com.bone.core.tenant.context.TenantContextRunner;
import com.bone.iam.application.command.cmd.LoginCommand;
import com.bone.iam.application.command.cmd.RefreshTokenCommand;
import com.bone.iam.application.config.IamPasswordProperties;
import com.bone.iam.application.port.out.TokenBlacklistPort;
import com.bone.iam.application.service.AuthService;
import com.bone.iam.application.service.PasswordPolicyValidator;
import com.bone.iam.application.service.RoleHierarchyResolver;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.account.AccountRole;
import com.bone.iam.domain.account.vo.AccountStatus;
import com.bone.iam.domain.gateway.AccessTokenIssuer;
import com.bone.iam.domain.gateway.AccountAuthorityCache;
import com.bone.iam.domain.gateway.RefreshTokenIssuer;
import com.bone.iam.domain.permission.DefaultPermissionCodes;
import com.bone.iam.domain.permission.Permission;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.iam.domain.repository.AccountRoleRepository;
import com.bone.iam.domain.repository.PermissionRepository;
import com.bone.iam.domain.repository.RolePermissionRepository;
import com.bone.iam.domain.role.RolePermission;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证应用服务（Application Service First 收敛点）——登录 / 刷新令牌用例的唯一入口。
 *
 * <p>原 {@code LoginCommandHandler}、{@code RefreshTokenCommandHandler} 与 {@code
 * AccountAuthoritiesQueryHandler}（权限码解析）逻辑已全量内联于此（E-3.11 一次性大爆炸收敛）。 Controller 只依赖本类。各方法语义 / 异常 /
 * 事务边界与原 Handler 完全一致（HTTP 契约不变）。
 *
 * <p>权限码解析的读侧 DSL 已下沉到各域仓储的 {@code default} 方法（E-4.2）：本类零 DSL、零 {@code
 * TenantContext}。「登录需显式声明租户上下文」的语义由 {@link TenantContextRunner} 承载（它是执行器而非 {@code TenantContext}
 * 直读，不属 E-4.4 约束对象）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthApplicationService {

  private final AuthService authService;
  private final AccountRepository accountRepository;
  private final AccountRoleRepository accountRoleRepository;
  private final RolePermissionRepository rolePermissionRepository;
  private final PermissionRepository permissionRepository;
  private final AccessTokenIssuer accessTokenIssuer;
  private final RefreshTokenIssuer refreshTokenIssuer;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final IamPasswordProperties passwordProperties;
  private final AccountAuthorityCache accountAuthorityCache;
  private final RoleHierarchyResolver roleHierarchyResolver;
  private final TokenBlacklistPort tokenBlacklistPort;
  private final JwtConfig jwtConfig;

  /**
   * 登出用例：把请求携带的访问令牌拉黑至其自然过期。
   *
   * <p>原实现散落在 {@code AuthController}（需注入 {@code infrastructure} 的令牌解析器与黑名单服务，违反 E-10.1）；
   * 现收口到应用层，入站适配器只传递请求头。令牌非法 / 缺失时静默成功（登出幂等，不泄露令牌有效性）。
   *
   * @param authorizationHeader 原始 {@code Authorization} 头（可含 {@code Bearer } 前缀）；可为 null
   */
  @Transactional
  public void logout(String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.isBlank()) {
      return;
    }
    accessTokenIssuer
        .parse(authorizationHeader)
        .ifPresent(
            principal ->
                tokenBlacklistPort.blacklist(
                    accessTokenIssuer.stripBearerToken(authorizationHeader),
                    Duration.ofMillis(jwtConfig.getExpirationMs())));
  }

  /**
   * 登录编排：解析账号 → 检查锁定/禁用 → 校验密码 → 失败计数 / 成功清零 → 颁发 access + refresh token。
   *
   * <p>账号锁定与计数依赖 {@link Account#recordLoginFailure()}，阈值由 {@link IamPasswordProperties}（默认 5 次/30
   * 分钟）声明，对齐详设 §7.1 / IAM-19。
   *
   * <p>能力声明标在方法上而非类上（{@code @Capability} 已支持 METHOD 目标）：一次类只承载一个能力的旧 Handler
   * 形态被废除后，元数据随用例方法走，无需再为声明能力而套同义 Handler（AS-01）。
   */
  @Capability(
      name = "Login",
      description = "账号登录",
      inputSchema = "{\"username\": \"string\", \"password\": \"string\"}",
      outputSchema = "{\"token\": \"string\", \"account\": {}}",
      idempotent = false,
      cost = 1,
      retryable = false,
      timeout = 5)
  @Transactional
  public Map<String, Object> login(LoginCommand cmd) {
    Account account =
        authService
            .findByUsername(cmd.getUsername())
            .orElseThrow(() -> BizException.of(401, IamErrorCodes.LOGIN_FAILED + ": 用户名或密码错误"));

    // TenantContextRunner 对 null tenantId 是快速失败（NPE），而 AuthController 只捕获 BizException，
    // NPE 会逃逸成 500 —— 这里先判空，按登录失败处理。
    if (account.getTenantId() == null) {
      throw BizException.of(401, IamErrorCodes.LOGIN_FAILED + ": 用户名或密码错误");
    }

    if (account.getStatus() == AccountStatus.DISABLED) {
      throw BizException.of(403, IamErrorCodes.ACCOUNT_DISABLED + ": 账号已禁用，请联系管理员");
    }
    if (account.isLocked()) {
      long remainingSec =
          account.getLockedAt() == null
              ? 0
              : Math.max(
                  0, Duration.between(LocalDateTime.now(), account.getLockedAt()).getSeconds());
      throw BizException.of(
          423, IamErrorCodes.ACCOUNT_LOCKED + ": 账号已锁定，剩余 " + remainingSec + " 秒");
    }

    if (!authService.matches(cmd.getPassword(), account)) {
      // 登录请求没有 JWT，TenantContext 为空；写 iam_account 属租户表操作，
      // 必须按账号所属租户显式声明上下文（ADR-0031 D3），否则被 ADR-0029 失败关闭拦下。
      TenantContextRunner.runAs(
          account.getTenantId(),
          () -> {
            account.recordLoginFailure(
                passwordProperties.getLockoutThreshold(), passwordProperties.getLockoutMinutes());
            accountRepository.update(account);
          });
      throw BizException.of(401, IamErrorCodes.LOGIN_FAILED + ": 用户名或密码错误");
    }

    return TenantContextRunner.callAs(
        account.getTenantId(),
        () -> {
          account.recordLoginSuccess(cmd.getClientIp());
          accountRepository.update(account);

          List<String> scopes = resolvePermissionCodes(account.getId(), account.isAdmin());
          String token =
              accessTokenIssuer.issueAccessToken(
                  account.getId(), account.getUsername().value(), account.getTenantId(), scopes);
          String refreshToken = refreshTokenIssuer.issue(account.getId(), account.getTenantId());

          boolean weak = passwordPolicyValidator.requiresPasswordChange(cmd.getPassword());
          boolean expired = isPasswordExpired(account);

          Map<String, Object> result = new HashMap<>();
          result.put("token", token);
          result.put("refreshToken", refreshToken);
          result.put("account", account);
          result.put("requirePasswordChange", weak || expired);
          return result;
        });
  }

  @Transactional
  public Map<String, String> refreshToken(RefreshTokenCommand cmd) {
    if (cmd == null || cmd.getRefreshToken() == null || cmd.getRefreshToken().isBlank()) {
      throw BizException.of(400, "刷新令牌不能为空");
    }
    Map<String, String> rotated;
    try {
      rotated = refreshTokenIssuer.rotate(cmd.getRefreshToken());
    } catch (IllegalArgumentException ex) {
      throw BizException.of(401, ex.getMessage());
    }
    // /refresh 与 /login 一样没有 JWT，TenantContext 为空；而 findById 读 iam_account（租户表）
    // 会被 ADR-0029 失败关闭拦下 → 500。租户由 refresh token 自身携带，显式声明后再访问租户表。
    // 两个 key 一并校验：任何一个缺失都按无效令牌 401 处理，不让 NumberFormatException 逃逸成 500。
    String rawAccountId = rotated.get("accountId");
    String rawTenantId = rotated.get("tenantId");
    if (rawAccountId == null || rawTenantId == null) {
      throw BizException.of(401, "刷新令牌无效：缺少账号或租户信息");
    }
    long accountId = Long.parseLong(rawAccountId);
    Long tenantId = Long.valueOf(rawTenantId);
    return TenantContextRunner.callAs(
        tenantId,
        () -> {
          Account account = accountRepository.findById(accountId);
          if (account == null) {
            throw NotFoundException.of("账户不存在或已禁用");
          }
          List<String> scopes = resolvePermissionCodes(account.getId(), account.isAdmin());
          String accessToken =
              accessTokenIssuer.issueAccessToken(
                  account.getId(), account.getUsername().value(), account.getTenantId(), scopes);
          String newRefreshToken = rotated.get("refreshToken");
          if (newRefreshToken == null) {
            throw BizException.of(500, "刷新令牌轮换失败：未返回新的刷新令牌");
          }
          Map<String, String> result = new HashMap<>();
          result.put("accessToken", accessToken);
          result.put("refreshToken", newRefreshToken);
          return result;
        });
  }

  /**
   * 按账号解析 RBAC 权限码（经 {@code iam_account_role} → {@code iam_role_permission} → {@code
   * iam_permission.code}）。
   */
  @Transactional(readOnly = true)
  public List<String> resolvePermissionCodes(Long accountId, boolean adminAccount) {
    if (accountId == null) {
      return List.of();
    }
    return accountAuthorityCache
        .get(accountId)
        .orElseGet(
            () -> {
              List<String> resolved = resolveFromDatabase(accountId, adminAccount);
              accountAuthorityCache.put(accountId, resolved);
              return resolved;
            });
  }

  private List<String> resolveFromDatabase(Long accountId, boolean adminAccount) {
    try {
      List<AccountRole> accountRoles = accountRoleRepository.findByAccount(accountId);
      log.debug(
          "Found {} account-role associations for account {}", accountRoles.size(), accountId);
      if (accountRoles.isEmpty()) {
        return adminAccount ? DefaultPermissionCodes.adminFallback() : List.of();
      }
      List<Long> directRoleIds =
          accountRoles.stream().map(AccountRole::getRoleId).distinct().toList();
      // 展开 parent_role_id 闭包，使继承角色的权限码自动并入（详设 §3.2 / IAM-22）。
      Set<Long> closure = roleHierarchyResolver.resolveClosure(directRoleIds);
      List<Long> closureRoleIds = List.copyOf(closure);
      List<RolePermission> rolePermissions = rolePermissionRepository.findByRoles(closureRoleIds);
      if (rolePermissions.isEmpty()) {
        return adminAccount ? DefaultPermissionCodes.adminFallback() : List.of();
      }
      List<Long> permissionIds =
          rolePermissions.stream().map(RolePermission::getPermissionId).distinct().toList();
      List<Permission> permissions = permissionRepository.findByIds(permissionIds);
      Set<String> codes = new LinkedHashSet<>();
      for (Permission permission : permissions) {
        if (permission.getCode() != null && !permission.getCode().isBlank()) {
          codes.add(permission.getCode());
        }
      }
      // 管理员账户始终合并 fallback 权限，避免因数据库数据不完整导致权限缺失
      if (adminAccount) {
        codes.addAll(DefaultPermissionCodes.adminFallback());
      }
      return new ArrayList<>(codes);
    } catch (Exception e) {
      log.error(
          "Failed to query account roles for account {}, using admin fallback: {}",
          accountId,
          e.getMessage());
      // 数据库数据异常时，管理员账户使用 fallback 权限
      return adminAccount ? DefaultPermissionCodes.adminFallback() : List.of();
    }
  }

  private boolean isPasswordExpired(Account account) {
    int maxAge = passwordProperties.getMaxAgeDays();
    if (maxAge <= 0 || account.getPasswordUpdatedAt() == null) {
      return false;
    }
    return account.getPasswordUpdatedAt().plusDays(maxAge).isBefore(LocalDateTime.now());
  }
}
