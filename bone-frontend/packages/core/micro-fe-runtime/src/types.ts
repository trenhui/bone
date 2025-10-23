/**
 * 微前端应用配置接口
 */
export interface MicroAppConfig {
  /** 应用唯一标识 */
  id: string;
  /** 应用名称 */
  name: string;
  /** 版本号 */
  version: string;
  /** 入口地址（支持多个入口） */
  entry: string | string[];
  /** 激活规则 */
  activeRule: RouteRule;
  /** 容器选择器 */
  container?: string;
  /** 初始化属性 */
  props?: Record<string, any>;
  /** 应用元数据 */
  metadata?: AppMetadata;
  /** 沙箱配置 */
  sandbox?: SandboxConfig;
  /** 生命周期钩子 */
  lifecycle?: Partial<AppLifecycleHooks>;
}

/**
 * 应用元数据接口
 */
export interface AppMetadata {
  /** 应用描述 */
  description?: string;
  /** 作者信息 */
  author?: string;
  /** 依赖的其他应用 */
  dependencies?: string[];
  /** 所需权限 */
  permissions?: string[];
  /** 技术栈信息 */
  technology?: {
    framework: 'react' | 'vue' | 'angular' | 'svelte';
    version: string;
  };
}

/**
 * 路由规则类型
 */
export type RouteRule = string | RegExp | ((path: string) => boolean);

/**
 * 应用状态枚举
 */
export type AppStatus = 
  | 'NOT_LOADED'      // 未加载
  | 'LOADING'         // 加载中
  | 'NOT_MOUNTED'     // 已加载但未挂载
  | 'MOUNTING'        // 挂载中
  | 'MOUNTED'         // 已挂载
  | 'UNMOUNTING'      // 卸载中
  | 'UPDATING'        // 更新中
  | 'LOAD_ERROR'      // 加载错误
  | 'MOUNT_ERROR'     // 挂载错误
  | 'UNMOUNT_ERROR'   // 卸载错误
  | 'UPDATE_ERROR';   // 更新错误

/**
 * 应用生命周期钩子接口
 */
export interface AppLifecycleHooks {
  /**
   * 应用引导阶段，在资源加载后执行
   */
  bootstrap?: () => Promise<void> | void;
  
  /**
   * 挂载前钩子
   */
  beforeMount?: () => Promise<void> | void;
  
  /**
   * 应用挂载阶段
   * @param container 容器元素
   * @param props 传递的属性
   */
  mount?: (container: HTMLElement, props?: Record<string, any>) => Promise<void> | void;
  
  /**
   * 挂载后钩子
   */
  afterMount?: () => Promise<void> | void;
  
  /**
   * 应用更新阶段
   * @param props 更新的属性
   */
  update?: (props?: Record<string, any>) => Promise<void> | void;
  
  /**
   * 卸载前钩子
   */
  beforeUnmount?: () => Promise<void> | void;
  
  /**
   * 应用卸载阶段
   */
  unmount?: () => Promise<void> | void;
  
  /**
   * 卸载后钩子
   */
  afterUnmount?: () => Promise<void> | void;
  
  /**
   * 应用销毁阶段
   */
  destroy?: () => Promise<void> | void;
  
  /**
   * 错误处理钩子
   */
  error?: (error: Error) => void;
}

/**
 * 沙箱类型枚举
 */
export enum SandboxType {
  Proxy = 'proxy',
  Iframe = 'iframe',
  Snapshot = 'snapshot'
}

/**
 * 沙箱配置接口
 */
export interface SandboxConfig {
  /** 是否启用沙箱 */
  enabled?: boolean;
  /** 沙箱类型 */
  type?: SandboxType;
  /** 是否启用严格模式 */
  strictMode?: boolean;
  /** 是否严格隔离CSS */
  strictIsolation?: boolean;
  /** 允许访问的全局属性白名单 */
  allowedGlobals?: string[];
  /** 代理window属性的黑名单 */
  blacklistedProps?: string[];
  /** 应用ID */
  appId?: string;
  /** 资源限制 */
  resourceLimits?: ResourceLimits;
}

/**
 * 性能指标接口
 */
export interface PerformanceMetrics {
  /** 资源加载时间 */
  resourceLoad?: number;
  /** 沙箱创建时间 */
  sandboxCreate?: number;
  /** 代码执行时间 */
  codeEval?: number;
  /** 总加载时间 */
  totalLoad?: number;
  /** 挂载时间 */
  mount?: number;
  /** 卸载时间 */
  unmount?: number;
  /** 更新时间 */
  update?: number;
}

/**
 * 错误上下文接口
 */
export interface ErrorContext {
  /** 错误发生的阶段 */
  phase: string;
  /** 应用信息 */
  appInfo: MicroAppConfig;
  /** 其他上下文信息 */
  [key: string]: any;
}

/**
 * 资源缓存配置接口
 */
export interface ResourceCacheConfig {
  /** 是否启用缓存 */
  enabled?: boolean;
  /** 缓存过期时间（毫秒） */
  ttl?: number;
  /** 最大缓存大小（字节） */
  maxSize?: number;
}

// 沙箱状态类型
export interface SandboxStatus {
  appId: string;
  loaded: boolean;
  mounted: boolean;
  running: boolean;
  activePropertiesCount: number;
  resourceCount: number;
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