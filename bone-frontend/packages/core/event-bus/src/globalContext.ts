/**
 * 微前端全局上下文类型定义
 * Shell 应用通过 qiankun props 下发给各微应用
 */

/** 用户信息（从 JWT 解析后由 Shell 维护） */
export interface GlobalUser {
  id: number;
  username: string;
  realName?: string;
  avatarUrl?: string;
  tenantId: number;
  tenantName?: string;
  isAdmin: boolean;
}

/** 权限码列表 */
export interface GlobalPermissions {
  codes: string[];
  roles: string[];
}

/** 全局上下文（Shell → 微应用） */
export interface GlobalContext {
  token: string | null;
  user: GlobalUser | null;
  permissions: GlobalPermissions | null;
  theme: 'light' | 'dark';
  locale: 'zh-CN' | 'en-US';
}

/** 全局上下文变更事件 */
export interface GlobalContextChangeEvent {
  type: 'user' | 'token' | 'theme' | 'locale' | 'permissions';
  context: GlobalContext;
}

declare global {
  interface Window {
    __BONE_GLOBAL_CONTEXT__?: GlobalContext;
  }
}
