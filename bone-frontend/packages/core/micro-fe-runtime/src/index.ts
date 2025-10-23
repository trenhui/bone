import { MicroApplication } from './micro-application';
import { ApplicationRegistry } from './application-registry';
import { MicroAppConfig, AppStatus, AppLifecycleHooks, RouteRule, SandboxConfig, SandboxStatus, SandboxType } from './types';
import { SandboxFactory } from './sandbox/sandbox-factory';

// 创建全局应用注册表实例
let applicationRegistryInstance: ApplicationRegistry | null = null;

/**
 * 获取应用注册表实例（单例模式）
 */
export function getApplicationRegistry(): ApplicationRegistry {
  if (!applicationRegistryInstance) {
    applicationRegistryInstance = new ApplicationRegistry();
  }
  return applicationRegistryInstance;
}

/**
 * 微前端运行时核心API
 */
// 导出类型
export type {
  MicroAppConfig,
  AppStatus,
  AppLifecycleHooks,
  RouteRule,
  SandboxConfig,
  SandboxStatus,
  SandboxType
};

// 导出核心类
export {
  MicroApplication,
  ApplicationRegistry,
  SandboxFactory
};

/**
 * 微前端运行时核心API
 */
export const MicroFeRuntime = {
  /**
   * 注册微应用
   */
  registerApp: (config: MicroAppConfig) => {
    return getApplicationRegistry().register(config);
  },

  /**
   * 批量注册微应用
   */
  registerApps: (configs: MicroAppConfig[]) => {
    return getApplicationRegistry().registerApps(configs);
  },

  /**
   * 根据路由激活应用
   */
  activateByRoute: (path: string) => {
    return getApplicationRegistry().activateAppByRoute(path);
  },

  /**
   * 手动激活应用
   */
  activateApp: (appName: string, props?: Record<string, any>) => {
    return getApplicationRegistry().activateApp(appName, props);
  },

  /**
   * 停用应用
   */
  deactivateApp: (appName: string) => {
    return getApplicationRegistry().deactivateApp(appName);
  },

  /**
   * 停用所有活跃应用
   */
  deactivateActiveApps: () => {
    return getApplicationRegistry().deactivateActiveApps();
  },

  /**
   * 获取应用实例
   */
  getApp: (appName: string) => {
    return getApplicationRegistry().getApp(appName);
  },

  /**
   * 获取所有注册的应用
   */
  getAllApps: () => {
    return getApplicationRegistry().getAllApps();
  },

  /**
   * 获取活跃的应用列表
   */
  getActiveApps: () => {
    return getApplicationRegistry().getActiveApps();
  },

  /**
   * 获取应用状态
   */
  getAppStatus: (appName: string) => {
    return getApplicationRegistry().getAppStatus(appName);
  },

  /**
   * 检查应用是否活跃
   */
  isAppActive: (appName: string) => {
    const registry = getApplicationRegistry();
    const activeApps = registry.getActiveApps();
    return activeApps.includes(appName);
  },

  /**
   * 创建沙箱实例
   */
  createSandbox: (config: SandboxConfig) => {
    return SandboxFactory.createSandbox(config);
  },

  /**
   * 初始化微前端环境
   */
  init: () => {
    // 确保注册表已初始化
    getApplicationRegistry();
    console.log('MicroFE Runtime initialized');
  }
};

// 默认导出
export default MicroFeRuntime;

// 导出版本信息
export const BONE_RUNTIME_VERSION = '1.0.0';