import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { clearScopes, decodeJwt, hasPermission, persistScopesFromToken, readScopes } from './jwt';

function buildJwt(payload: Record<string, unknown>): string {
  const base64 = (s: string) => btoa(s).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  const header = base64(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = base64(JSON.stringify(payload));
  return `${header}.${body}.fake-signature`;
}

describe('auth/jwt', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('decodeJwt 解析 payload 中的 scopes', () => {
    const token = buildJwt({ sub: 'admin', scopes: ['sys:console:read'] });
    expect(decodeJwt(token)?.scopes).toEqual(['sys:console:read']);
  });

  it('decodeJwt 对非法 token 返回 null', () => {
    expect(decodeJwt('not-a-jwt')).toBeNull();
    expect(decodeJwt('')).toBeNull();
  });

  it('persistScopesFromToken 后 readScopes 可读', () => {
    const token = buildJwt({ scopes: ['sys:console:read', 'iam:accounts:read'] });
    persistScopesFromToken(token);
    expect(readScopes()).toEqual(['sys:console:read', 'iam:accounts:read']);
  });

  it('clearScopes 清理后 readScopes 返回 []', () => {
    persistScopesFromToken(buildJwt({ scopes: ['sys:console:read'] }));
    clearScopes();
    expect(readScopes()).toEqual([]);
  });

  it('hasPermission 单权限码命中', () => {
    persistScopesFromToken(buildJwt({ scopes: ['sys:console:read'] }));
    expect(hasPermission('sys:console:read')).toBe(true);
    expect(hasPermission('iam:accounts:write')).toBe(false);
  });

  it('hasPermission 多权限码要求全部命中', () => {
    persistScopesFromToken(buildJwt({ scopes: ['sys:console:read', 'iam:accounts:read'] }));
    expect(hasPermission(['sys:console:read', 'iam:accounts:read'])).toBe(true);
    expect(hasPermission(['sys:console:read', 'integration:flows:write'])).toBe(false);
  });

  it('readScopes 无 token 时返回 []', () => {
    expect(readScopes()).toEqual([]);
  });
});
