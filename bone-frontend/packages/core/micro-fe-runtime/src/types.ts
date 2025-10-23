// 应用状态类型
export type AppStatus = 'NOT_LOADED' | 'LOADING' | 'NOT_MOUNTED' | 'MOUNTING' | 'MOUNTED' | 'UNMOUNTING' | 'LOAD_ERROR' | 'MOUNT_ERROR' | 'UNMOUNT_ERROR';

// 路由规则类型
export type RouteRule = string | RegExp | ((path: string) => boolean);

// 沙箱状态类型
export interface SandboxStatus {
  appId: string;
  running: boolean;
  activePropertiesCount: number;
  sideEffectsCount: number;
}

// 资源限制接口
export interface ResourceLimits {
  maxMemoryMB: number;
  maxExecutionTimeMS: number;
}

// 应用安全策略接口
export interface AppSecurityPolicy {
  check(app: any, action: string, resource: any): Promise<{ allowed: boolean; reason?: string }>;
}

// 沙箱安全策略接口
export interface SandboxSecurityPolicy {
  checkAccess(type: string, prop: string | symbol): 'allow' | 'block' | 'wrap';
  checkWrite(type: string, prop: string | symbol): boolean;
  checkDelete(type: string, prop: string | symbol): boolean;
}

// 沙箱接口
export interface Sandbox {
  mount?: () => void;
  unmount?: () => void;
  getGlobalState?: () => Record<string, any>;
  execute?: (code: string) => any;
  eval?: (code: string) => Promise<any>;
  destroy?: () => void;
  getStatus?: () => SandboxStatus;
  getProxyWindow?: () => Window;
}