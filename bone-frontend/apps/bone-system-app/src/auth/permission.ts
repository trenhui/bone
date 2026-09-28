/**
 * 路由级权限守卫（S-4）：与 packages/shared-types/bonePermissionCodes 及 IAM 详设 §3.7 对齐。
 *
 * <p>仅做 UX 增强——真正的授权校验在后端 {@code @PreAuthorize}（sys:console:read 等）兜底。
 *
 * <p>读取顺序：优先 Shell 经 {@code window.__BONE_GLOBAL_CONTEXT__} 下发的 {@code permissions.codes}，
 * 回退到本地 JWT 解码 scopes（与 bone-shell/src/auth/jwt.ts 同源逻辑，避免增加 workspace 依赖）。
 */
import { BonePermissionCodes } from '../../../../packages/shared-types/src/bonePermissionCodes';

export { BonePermissionCodes };
export type BonePermissionCode = string;

function readScopesFromToken(): string[] {
  const token = localStorage.getItem('token');
  if (!token) {
    return [];
  }
  const parts = token.split('.');
  if (parts.length !== 3) {
    return [];
  }
  try {
    const padded = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = atob(padded.padEnd(padded.length + ((4 - (padded.length % 4)) % 4), '='));
    const obj = JSON.parse(json) as { scopes?: string[] };
    return obj.scopes ?? [];
  } catch {
    return [];
  }
}

function readScopes(): string[] {
  const ctx = (
    window as unknown as { __BONE_GLOBAL_CONTEXT__?: { permissions?: { codes?: string[] } } }
  ).__BONE_GLOBAL_CONTEXT__;
  if (ctx?.permissions?.codes && ctx.permissions.codes.length > 0) {
    return ctx.permissions.codes;
  }
  return readScopesFromToken();
}

export function hasPermission(required: string | string[]): boolean {
  const scopes = readScopes();
  if (scopes.length === 0) {
    return false;
  }
  const list = Array.isArray(required) ? required : [required];
  return list.every((code) => scopes.includes(code));
}
