/**
 * IAM 业务错误码 → 文案映射表（M-6）。
 *
 * 与后端 {@code IamErrorCodes.java}（{@code IAM_} 前缀）一一对应；新增 / 修改业务码时须同步此处。
 * 真源仍是后端：本表只承载「稳定业务码 → 中文文案」，不承载 HTTP 状态（状态由后端 {@code IamErrors} 提供）。
 *
 * 用法：在捕获后端错误的分支用 {@link resolveIamErrorMessage} 解析——
 * 优先按 {@code errorCode} 命中本表（便于后续 i18n 切换），其次回退到后端 {@code displayMessage}，最后回退到原始 {@code message}。
 * 注意：不要在此处引入 i18n 语言包依赖边（与 shared-services 的 apiClient 设计一致，翻译留给消费端）。
 */

/** IAM 业务码 → 中文文案（key 必须 === 后端 IamErrorCodes 常量值） */
export const IAM_ERROR_MESSAGES: Record<string, string> = {
  IAM_LOGIN_FAILED: '用户名或密码错误',
  IAM_ACCOUNT_LOCKED: '账号已被锁定，请稍后再试',
  IAM_ACCOUNT_DISABLED: '账号已被禁用',
  IAM_REFRESH_TOKEN_REUSE: '刷新令牌已被使用，会话已失效，请重新登录',
  IAM_REFRESH_TOKEN_REQUIRED: '缺少刷新令牌',
  IAM_REFRESH_TOKEN_INVALID: '刷新令牌无效或已过期',
  IAM_REFRESH_TOKEN_ROTATE_FAILED: '令牌轮换失败，请重新登录',
  IAM_WEAK_PASSWORD: '密码强度不足',
  IAM_OLD_PASSWORD_MISMATCH: '原密码错误',
  IAM_ACCOUNT_NOT_FOUND: '账号不存在',
  IAM_USERNAME_CONFLICT: '用户名已存在',
  IAM_ACCOUNT_STATUS_CONFLICT: '账号状态冲突',
  IAM_ROLE_NOT_FOUND: '角色不存在',
  IAM_ROLE_ID_REQUIRED: '缺少角色标识',
  IAM_PERMISSION_NOT_FOUND: '权限不存在',
  IAM_PERMISSION_PLATFORM_ONLY: '不能将平台级权限授予租户角色',
  IAM_AUTHORITY_RESOLVE_FAILED: '权限解析失败，请稍后重试',
  IAM_SESSION_NOT_FOUND: '会话不存在',
  IAM_SESSION_ID_REQUIRED: '缺少会话标识',
  IAM_DEPT_NOT_FOUND: '部门不存在',
  IAM_DEPT_REQUIRED: '归属部门为必填项',
  IAM_MENU_NOT_FOUND: '菜单不存在',
  IAM_AUDIT_SETTINGS_REQUIRED: '审计设置不能为空',
  IAM_PROFILE_OWNERSHIP_DENIED: '无权访问他人资料',
  IAM_TENANT_ACCESS_DENIED: '禁止跨租户访问',
  IAM_TENANT_DELETE_FORBIDDEN: '平台租户不可删除',
  IAM_TENANT_NOT_FOUND: '租户不存在',
  IAM_TENANT_CODE_CONFLICT: '租户编码已存在',
  IAM_TENANT_QUOTA_EXCEEDED: '租户配额已达上限',
  IAM_TENANT_ID_REQUIRED: '缺少租户标识',
  IAM_TENANT_QUOTA_INVALID: '租户配额参数非法',
  IAM_APPLICATION_NOT_FOUND: '应用不存在',
  IAM_MODULE_NOT_FOUND: '模块不存在',
  IAM_APPLICATION_ID_REQUIRED: '缺少应用标识',
  IAM_USER_ID_REQUIRED: '缺少用户标识',
  IAM_APP_ROLE_INVALID: '应用角色取值非法',
  IAM_SSO_NOT_CONFIGURED: 'SSO 未配置或暂不可用',
  IAM_MFA_NOT_AVAILABLE: 'MFA 未启用',
};

/** 后端错误对象上挂的字段（由 shared-services 的 apiClient 响应拦截器注入）。 */
export interface IamErrorLike {
  errorCode?: string | null;
  displayMessage?: string | null;
  message?: string | null;
}

/**
 * 解析 IAM 后端错误应展示的文案：优先命中业务码映射，其次回退 displayMessage / message。
 * 返回 undefined 表示无可展示信息（调用方应使用本地兜底文案）。
 *
 * 入参放宽为 {@code unknown}：既可直接传 axios / 后端错误对象（挂有 errorCode / displayMessage），
 * 也可传 catch 的 {@code unknown} 变量或后端 {@code ApiResponse}（仅有 message 时回退到它）。
 */
export function resolveIamErrorMessage(err?: unknown): string | undefined {
  if (!err || typeof err !== 'object') {
    return undefined;
  }
  const e = err as Record<string, unknown>;
  const code = typeof e.errorCode === 'string' ? e.errorCode : undefined;
  if (code && IAM_ERROR_MESSAGES[code]) {
    return IAM_ERROR_MESSAGES[code];
  }
  const display = typeof e.displayMessage === 'string' ? e.displayMessage : undefined;
  const message = typeof e.message === 'string' ? e.message : undefined;
  return display ?? message;
}

/**
 * 判定后端错误是否为「无访问权限」：统一按 HTTP 403 识别（四态之「无权限」态）。
 *
 * 后端 IAM 未为列表接口登记独立的权限业务码，鉴权拒绝统一以 403 返回，
 * 由 apiClient 响应拦截器挂在 {@code error.response.status} 上（见 shared-services）。
 */
export function isForbiddenError(err?: unknown): boolean {
  if (err && typeof err === 'object') {
    const e = err as Record<string, unknown>;
    const response = e.response as Record<string, unknown> | undefined;
    if (response && response.status === 403) {
      return true;
    }
  }
  return false;
}
