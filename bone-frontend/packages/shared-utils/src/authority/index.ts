/**
 * BONE 前端统一权限判定（纯函数，无 React / 无网络依赖）。
 *
 * ## 为什么要有这一层
 *
 * 改造前权限判断是**三份互不相干的实现**：
 * `bone-shell/src/auth/jwt.ts`、`bone-system-app/src/auth/permission.ts`，其余 6 个
 * 微应用则完全没有。判定入口不统一 ⇒ 「改了 A 处行为，B 处的按钮还是老样子」，
 * 也让「IAM 里给角色授了权，前端按钮却没反应」这类问题无从定位。
 *
 * 本模块是**唯一**判定实现，UI 层（`@bone/ui` 的 `<Auth>` / `<AuthButton>`）与
 * 路由守卫都从这里取答案。
 *
 * ## 权限来源的三级降级（顺序不可乱）
 *
 * 1. Shell 下发的 `__BONE_GLOBAL_CONTEXT__.permissions.codes`
 *    —— 权威来源：登录后由 `/api/v1/iam/me` 的 scopes 落地，随租户/角色刷新而更新。
 * 2. `localStorage['bone-scopes']` —— 刷新页面后 ctx 重建前的过渡态。
 * 3. JWT payload 的 `scopes` —— 微应用**独立开发/direct 访问**（不经 Shell）时的兜底，
 *    否则 standalone 模式下所有按钮都会消失，开发者会以为是新功能坏了。
 *
 * ## 默认拒绝（fail-closed）
 *
 * 三级都没取到 ⇒ `codes` 为空 ⇒ `hasPermission` 返回 `false`。宁可「没权限看不见」，
 * 也不能「判不出来就放行」——前端只是 UX 层，真正的门禁永远在后端 `@PreAuthorize`，
 * 前端放行不会造成越权，但会让用户看到点不动的按钮，是纯粹的体验噪音。
 */

/** JWT 解析后的最小载荷（只取用到的字段）。 */
export interface BoneJwtAuthorityPayload {
  userId?: string;
  tenantId?: string;
  scopes?: string[];
}

/** Shell 与 IAM 打通后的完整权限快照。 */
export interface BonePermissionSnapshot {
  /** 权限码清单（`{domain}:{resource}:{action}`）。 */
  codes: string[];
  /** 角色 code 清单（来自 `GET /api/v1/iam/me/roles`）。 */
  roles: string[];
  /** 菜单树里登记的按钮权限点（type=2 节点），见 MenuNode.type。 */
  actions?: string[];
  /** 当前生效租户。 */
  tenantId?: string;
}

export const SCOPES_STORAGE_KEY = 'bone-scopes';

/** 权限快照变更事件：Shell 刷新权限后广播，UI 订阅重渲染。 */
export const PERMISSIONS_CHANGE_EVENT = 'bone:permissions:change';

function decodeBase64Url(segment: string): string | null {
  try {
    const pad = '='.repeat((4 - (segment.length % 4)) % 4);
    const normalized = (segment + pad).replace(/-/g, '+').replace(/_/g, '/');
    const binary = atob(normalized);
    // UTF-8 安全解码：直接用 fromCharCode 处理多字节会产出乱码（中文 username 曾因此错位）
    const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
    return new TextDecoder().decode(bytes);
  } catch {
    return null;
  }
}

/**
 * 解析 JWT payload（**不验签**）。
 *
 * 不验签是刻意的：这里只是读取载荷中的 scopes 做 UI 决策，签名校验属于网关/后端的职责。
 * 前端拿着验签结果也拦不住伪造；真正被伪造的请求由后端 `@PreAuthorize` 拒绝。
 */
export function readAuthorityFromToken(token?: string | null): BoneJwtAuthorityPayload | null {
  if (!token) {
    return null;
  }
  const parts = token.split('.');
  if (parts.length < 2) {
    return null;
  }
  const json = decodeBase64Url(parts[1]);
  if (!json) {
    return null;
  }
  try {
    const payload = JSON.parse(json) as BoneJwtAuthorityPayload;
    if (payload && Array.isArray(payload.scopes)) {
      return payload;
    }
    return payload ?? null;
  } catch {
    return null;
  }
}

/** localStorage 里的 token key（与 Shell 登录后的落地点保持一致）。 */
export const TOKEN_STORAGE_KEY = 'token';

/** 读作用域缓存（Shell 登录后由 `persistScopesFromToken` 落地）。 */
export function readStoredScopes(): string[] {
  try {
    const raw = localStorage.getItem(SCOPES_STORAGE_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw) as unknown;
    return Array.isArray(parsed) ? (parsed as string[]) : [];
  } catch {
    return [];
  }
}

/** 从全局上下文读权限快照（Shell 通过 qiankun props 下发）。 */
export function readGlobalPermissions(): BonePermissionSnapshot | null {
  if (typeof window === 'undefined') {
    return null;
  }
  const ctx = (window as unknown as { __BONE_GLOBAL_CONTEXT__?: Record<string, unknown> })
    .__BONE_GLOBAL_CONTEXT__;
  const permissions = ctx?.permissions as Partial<BonePermissionSnapshot> | undefined;
  if (!permissions || !Array.isArray(permissions.codes)) {
    return null;
  }
  return {
    codes: permissions.codes,
    roles: Array.isArray(permissions.roles) ? permissions.roles : [],
    actions: Array.isArray(permissions.actions) ? permissions.actions : undefined,
    tenantId: permissions.tenantId,
  };
}

/**
 * 当前生效权限码（三级降级后的结果）。
 *
 * @param ctx 可选上下文快照；不传则走 window 全局上下文
 */
export function readPermissionCodes(ctx?: BonePermissionSnapshot | null): string[] {
  const snapshot = ctx ?? readGlobalPermissions();
  if (snapshot && snapshot.codes.length > 0) {
    return snapshot.codes;
  }
  const stored = readStoredScopes();
  if (stored.length > 0) {
    return stored;
  }
  let token: string | null = null;
  try {
    token = localStorage.getItem(TOKEN_STORAGE_KEY);
  } catch {
    token = null;
  }
  return readAuthorityFromToken(token)?.scopes ?? [];
}

/** 当前生效角色 code（可能为空——旧 Shell 未下发 roles 时即为空数组）。 */
export function readRoleCodes(ctx?: BonePermissionSnapshot | null): string[] {
  const snapshot = ctx ?? readGlobalPermissions();
  return snapshot?.roles ?? [];
}

/**
 * 单个权限码匹配（支持按段通配）。
 *
 * 通配样例：`iam:accounts:write` 被 `iam:*:write` / `iam:accounts:*` / `*` 命中。
 * 逐段比较而非字符串前缀匹配 —— `'iam:accounts:write'.startsWith('iam:acc')`
 * 这类写法会把 `iam:accounts2:write` 也算进来。
 */
export function matchesPermissionCode(granted: readonly string[], required: string): boolean {
  if (!required) {
    return false;
  }
  if (granted.includes(required) || granted.includes('*')) {
    return true;
  }
  const requiredSegments = required.split(':');
  return granted.some((code) => {
    if (code === required) {
      return true;
    }
    if (!code.includes('*')) {
      return false;
    }
    const grantedSegments = code.split(':');
    if (grantedSegments.length !== requiredSegments.length) {
      return false;
    }
    return grantedSegments.every(
      (seg, i) => seg === '*' || requiredSegments[i] === '*' || seg === requiredSegments[i],
    );
  });
}

/**
 * **AND** 语义：要求列表里的码全部命中才算有权限。
 *
 * 多个码之间是"且"不是"或"——`['order:orders:read', 'order:orders:write']` 表示
 * 「既要能看又要能改」。需要"任一命中"的场景用 {@link hasAnyPermission}。
 */
export function hasPermission(
  required: string | string[] | undefined | null,
  codes?: readonly string[],
): boolean {
  if (!required || (Array.isArray(required) && required.length === 0)) {
    // 不声明权限码 = 不做权限门禁（如纯展示区块）。UI 层以此为"显式无条件可见"。
    return true;
  }
  const granted = codes ?? readPermissionCodes();
  const list = Array.isArray(required) ? required : [required];
  return list.every((code) => matchesPermissionCode(granted, code));
}

/** **OR** 语义：任一码命中即通过（多个可选入口共用一个按钮时用）。 */
export function hasAnyPermission(codeList: string[], codes?: readonly string[]): boolean {
  if (codeList.length === 0) {
    return true;
  }
  const granted = codes ?? readPermissionCodes();
  return codeList.some((code) => matchesPermissionCode(granted, code));
}
