/**
 * 前端权限码常量（单一真源）。
 *
 * <p>后端 {@code claim:policy:write} / {@code claim:quota:write} 等权限点在配置面多处复用，
 * 收口为常量后，后端改码时前端编译期即可发现遗漏（魔法字符串无法在编译期捕获）。
 */
export const PERMISSIONS = {
  POLICY_READ: 'claim:policy:read',
  POLICY_WRITE: 'claim:policy:write',
  QUOTA_READ: 'claim:quota:read',
  QUOTA_WRITE: 'claim:quota:write',
} as const;

export type PermissionCode = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];
