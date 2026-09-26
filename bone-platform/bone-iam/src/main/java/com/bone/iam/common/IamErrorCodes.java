package com.bone.iam.common;

/**
 * bone-iam 业务错误码（登记见 {@code doc/architecture/Bone-错误码登记.md} §6 {@code IAM_} 表）。
 *
 * <p><b>为何需要这一层，而不是直接写中文消息</b>：{@code BizException} 的第一个参数是 <em>HTTP 状态</em>（int）， 业务码只能以字符串拼进
 * message——{@code throw new BizException(404, "应用不存在")} 让前端、监控、告警只能按中文 message 分类，文案一改聚合口径即断（错误码登记
 * §2「可聚合」「可 i18n」、§17「禁止把 HTTP 码与业务码混为同一个整数」）。
 *
 * <p><b>用法</b>：抛出走 {@link IamErrors#of(String, Object)}——{@code throw
 * IamErrors.of(IamErrorCodes.APPLICATION_NOT_FOUND, id)}。HTTP 状态由 {@link IamErrors} 的「码 → 状态」表提供，
 * <strong>不要</strong>在抛出点再手写状态数字：状态与码各写一处即会漂移，且不一致时无机制发现。
 *
 * <p><b>本类只承载「稳定的业务码字符串 + 语义」</b>，不承载状态（真源见 {@link IamErrors}），也不承载文案。
 *
 * <p><b>命名</b>：{@code IAM_} 为身份域前缀，格式 {@code {DOMAIN_PREFIX}_{SNAKE_CASE_REASON}}（错误码登记 §3.1）。
 */
public final class IamErrorCodes {

  // ===== 认证 / 令牌（IAM_LOGIN_* / IAM_ACCOUNT_* / IAM_REFRESH_*）=====

  /** 用户名或密码错误（不区分账号是否存在，避免账号枚举）。 */
  public static final String LOGIN_FAILED = "IAM_LOGIN_FAILED";

  /** 连续失败次数超限导致账号锁定。 */
  public static final String ACCOUNT_LOCKED = "IAM_ACCOUNT_LOCKED";

  /** 账号被管理员禁用。 */
  public static final String ACCOUNT_DISABLED = "IAM_ACCOUNT_DISABLED";

  /** 刷新令牌被复用（疑似泄露），已吊销该账号全部会话。 */
  public static final String REFRESH_TOKEN_REUSE = "IAM_REFRESH_TOKEN_REUSE";

  /** 刷新令牌未提供。 */
  public static final String REFRESH_TOKEN_REQUIRED = "IAM_REFRESH_TOKEN_REQUIRED";

  /** 刷新令牌非法 / 过期 / 信息缺失。 */
  public static final String REFRESH_TOKEN_INVALID = "IAM_REFRESH_TOKEN_INVALID";

  /** 刷新令牌轮换未能产出新令牌（服务端契约破坏）。 */
  public static final String REFRESH_TOKEN_ROTATE_FAILED = "IAM_REFRESH_TOKEN_ROTATE_FAILED";

  // ===== 密码（IAM_*_PASSWORD_*）=====

  /** 新密码不满足强度策略（也用于账号创建时的初始密码）。 */
  public static final String WEAK_PASSWORD = "IAM_WEAK_PASSWORD";

  /** 自助改密时旧密码校验失败。 */
  public static final String OLD_PASSWORD_MISMATCH = "IAM_OLD_PASSWORD_MISMATCH";

  // ===== 账号（IAM_ACCOUNT_*）=====

  /** 账号不存在（含跨租户不可见）。 */
  public static final String ACCOUNT_NOT_FOUND = "IAM_ACCOUNT_NOT_FOUND";

  /** 本租户内用户名已存在（唯一键 {@code uk_iam_account_username}）。 */
  public static final String USERNAME_CONFLICT = "IAM_USERNAME_CONFLICT";

  /** 账号状态迁移不合法（如对已禁用账号重复禁用）。 */
  public static final String ACCOUNT_STATUS_CONFLICT = "IAM_ACCOUNT_STATUS_CONFLICT";

  // ===== 角色 / 权限（IAM_ROLE_* / IAM_PERMISSION_*）=====

  /** 角色不存在（含跨租户不可见）。 */
  public static final String ROLE_NOT_FOUND = "IAM_ROLE_NOT_FOUND";

  /** 授予角色权限时未提供角色 id。 */
  public static final String ROLE_ID_REQUIRED = "IAM_ROLE_ID_REQUIRED";

  /** 权限不存在（含跨租户不可见）。 */
  public static final String PERMISSION_NOT_FOUND = "IAM_PERMISSION_NOT_FOUND";

  /**
   * 平台域权限码（resource_path 为 tenants / permissions / sessions）不可授予租户角色。
   *
   * <p>租户管理员可读取共享权限目录，但把平台域码绑进本租户角色即等于自授平台能力（平台域接口仅 {@code hasAuthority} 校验），须在绑定侧拒绝。
   */
  public static final String PERMISSION_PLATFORM_ONLY = "IAM_PERMISSION_PLATFORM_ONLY";

  // ===== 会话（IAM_SESSION_*）=====

  /** 会话不存在（含跨租户不可见，与"参数缺失"分属不同语义）。 */
  public static final String SESSION_NOT_FOUND = "IAM_SESSION_NOT_FOUND";

  /** 会话 id 未提供。 */
  public static final String SESSION_ID_REQUIRED = "IAM_SESSION_ID_REQUIRED";

  // ===== 组织（IAM_DEPT_* / IAM_MENU_*）=====

  /** 部门不存在（含跨租户不可见）。 */
  public static final String DEPT_NOT_FOUND = "IAM_DEPT_NOT_FOUND";

  /** 归属部门为必填项（创建账号必须指定主部门；编辑时不允许清空归属部门）。 */
  public static final String DEPT_REQUIRED = "IAM_DEPT_REQUIRED";

  /** 菜单不存在（含跨租户不可见）。 */
  public static final String MENU_NOT_FOUND = "IAM_MENU_NOT_FOUND";

  // ===== 审计（IAM_AUDIT_*）=====

  /** 审计设置未提供或为空。 */
  public static final String AUDIT_SETTINGS_REQUIRED = "IAM_AUDIT_SETTINGS_REQUIRED";

  // ===== 当前账号自助（IAM_PROFILE_*）=====

  /** 请求主体与目标账号不一致（越权访问他人 profile）。 */
  public static final String PROFILE_OWNERSHIP_DENIED = "IAM_PROFILE_OWNERSHIP_DENIED";

  // ===== 租户（IAM_TENANT_*）=====

  /** 跨租户访问被拒绝（防 IDOR）。 */
  public static final String TENANT_ACCESS_DENIED = "IAM_TENANT_ACCESS_DENIED";

  /** 平台租户（id = 0）不允许删除。 */
  public static final String TENANT_DELETE_FORBIDDEN = "IAM_TENANT_DELETE_FORBIDDEN";

  /** 租户不存在。 */
  public static final String TENANT_NOT_FOUND = "IAM_TENANT_NOT_FOUND";

  /** 租户编码已存在。 */
  public static final String TENANT_CODE_CONFLICT = "IAM_TENANT_CODE_CONFLICT";

  /** 租户配额（账号数 / 角色数）已达上限。 */
  public static final String TENANT_QUOTA_EXCEEDED = "IAM_TENANT_QUOTA_EXCEEDED";

  /** 租户 id 未提供。 */
  public static final String TENANT_ID_REQUIRED = "IAM_TENANT_ID_REQUIRED";

  /** 租户配额入参非法（如账号数 / 角色数为负数）。 */
  public static final String TENANT_QUOTA_INVALID = "IAM_TENANT_QUOTA_INVALID";

  // ===== 应用 / 模块（IAM_APPLICATION_* / IAM_MODULE_* / IAM_USER_*）=====

  /** 应用不存在（含跨租户不可见）。 */
  public static final String APPLICATION_NOT_FOUND = "IAM_APPLICATION_NOT_FOUND";

  /** 模块不存在（含跨租户不可见）。 */
  public static final String MODULE_NOT_FOUND = "IAM_MODULE_NOT_FOUND";

  /** 授予应用权限时未提供应用 id。 */
  public static final String APPLICATION_ID_REQUIRED = "IAM_APPLICATION_ID_REQUIRED";

  /** 授予应用权限时未提供被授权用户 id。 */
  public static final String USER_ID_REQUIRED = "IAM_USER_ID_REQUIRED";

  /** 传入的应用内角色不是合法取值。 */
  public static final String APP_ROLE_INVALID = "IAM_APP_ROLE_INVALID";

  // ===== 能力未启用（IAM_SSO_* / IAM_MFA_*）=====

  /** SSO / IdP 未配置或回调未实现。 */
  public static final String SSO_NOT_CONFIGURED = "IAM_SSO_NOT_CONFIGURED";

  /** MFA 未在当前版本 / IdP 中启用。 */
  public static final String MFA_NOT_AVAILABLE = "IAM_MFA_NOT_AVAILABLE";

  private IamErrorCodes() {}
}
