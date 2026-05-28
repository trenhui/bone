/**
 * JWT 工具：浏览器侧 **无验签** 解码 payload，仅用于读取 scopes/userId/tenantId
 * 做路由守卫。安全可信仍由后端 {@code @PreAuthorize('sys:console:read')} 兜底
 * （详设 §5.0：service-side authoritative authorization）。
 *
 * <p>权限码契约保持与 {@code @bone/shared-types/bonePermissionCodes} 一致。
 * 这里复刻必要常量，避免 shell 增加 workspace 依赖。
 */

export type JwtPayload = {
  sub?: string;
  userId?: string;
  tenantId?: string;
  scopes?: string[];
  exp?: number;
};

const TOKEN_STORAGE_KEY = 'token';
const SCOPES_STORAGE_KEY = 'bone-scopes';

export const PermissionCodes = {
  SYS_CONSOLE_READ: 'sys:console:read',
} as const;

export function decodeJwt(token: string): JwtPayload | null {
  if (!token) {
    return null;
  }
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }
  try {
    const padded = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = atob(padded.padEnd(padded.length + ((4 - (padded.length % 4)) % 4), '='));
    const obj = JSON.parse(json);
    return obj as JwtPayload;
  } catch {
    return null;
  }
}

export function readScopes(): string[] {
  const cached = localStorage.getItem(SCOPES_STORAGE_KEY);
  if (cached) {
    try {
      const parsed = JSON.parse(cached);
      if (Array.isArray(parsed)) {
        return parsed.map(String);
      }
    } catch {
      // fall through
    }
  }
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (!token) {
    return [];
  }
  const payload = decodeJwt(token);
  return payload?.scopes ?? [];
}

export function persistScopesFromToken(token: string | null | undefined): void {
  if (!token) {
    localStorage.removeItem(SCOPES_STORAGE_KEY);
    return;
  }
  const payload = decodeJwt(token);
  const scopes = payload?.scopes ?? [];
  localStorage.setItem(SCOPES_STORAGE_KEY, JSON.stringify(scopes));
}

export function clearScopes(): void {
  localStorage.removeItem(SCOPES_STORAGE_KEY);
}

export function hasPermission(required: string | string[]): boolean {
  const scopes = readScopes();
  if (scopes.length === 0) {
    return false;
  }
  const list = Array.isArray(required) ? required : [required];
  return list.every((code) => scopes.includes(code));
}
