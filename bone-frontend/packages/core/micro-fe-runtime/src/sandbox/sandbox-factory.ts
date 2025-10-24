import { Sandbox, SandboxConfig, SandboxType, MicroAppConfig } from '../types';

// 定义沙箱类型常量
export const SANDBOX_TYPES = {
  PROXY: 'proxy' as SandboxType,
  IFRAME: 'iframe' as SandboxType,
  SNAPSHOT: 'snapshot' as SandboxType
};

/**
 * 沙箱工厂类
 * 负责创建不同类型的沙箱实例
 */
export class SandboxFactory {
  /**
   * 创建沙箱实例
   * @param config 应用配置
   * @returns 沙箱实例
   */
  static async create(config: MicroAppConfig): Promise<Sandbox> {
    // 获取沙箱配置
    const sandboxConfig = this.getSandboxConfig(config);
    
    // 根据沙箱类型创建对应的沙箱实例
    switch (sandboxConfig.type) {
      case SANDBOX_TYPES.PROXY:
        return this.createProxySandbox(sandboxConfig);
      case SANDBOX_TYPES.IFRAME:
        return this.createIframeSandbox(sandboxConfig);
      case SANDBOX_TYPES.SNAPSHOT:
        return this.createSnapshotSandbox(sandboxConfig);
      default:
        return this.createProxySandbox(sandboxConfig);
    }
  }
  
  /**
   * 创建沙箱实例（非异步方法）
   * @param config 沙箱配置
   */
  // 静态导入沙箱类以支持TypeScript编译
  private static EnhancedProxySandboxClass: any = null;
  private static IframeSandboxClass: any = null;
  private static SnapshotSandboxClass: any = null;
  
  // 初始化沙箱类的静态方法
  private static initSandboxClasses(): void {
    try {
      const { EnhancedProxySandbox } = require('./enhanced-proxy-sandbox');
      const { IframeSandbox } = require('./iframe-sandbox');
      const { SnapshotSandbox } = require('./snapshot-sandbox');
      
      this.EnhancedProxySandboxClass = EnhancedProxySandbox;
      this.IframeSandboxClass = IframeSandbox;
      this.SnapshotSandboxClass = SnapshotSandbox;
    } catch (error) {
      console.error('Failed to initialize sandbox classes:', error);
    }
  }
  
  static createSandbox(config: SandboxConfig): Sandbox {
    // 懒加载沙箱类
    if (!this.EnhancedProxySandboxClass) {
      this.initSandboxClasses();
    }
    
    const sandboxType = (config as any).type || SANDBOX_TYPES.PROXY;
    
    switch (sandboxType) {
      case 'proxy':
        if (this.EnhancedProxySandboxClass) {
          return new this.EnhancedProxySandboxClass(config);
        }
        throw new Error('EnhancedProxySandbox class not loaded');
      case 'iframe':
        if (this.IframeSandboxClass) {
          return new this.IframeSandboxClass(config);
        }
        throw new Error('IframeSandbox class not loaded');
      case 'snapshot':
        if (this.SnapshotSandboxClass) {
          return new this.SnapshotSandboxClass(config);
        }
        throw new Error('SnapshotSandbox class not loaded');
      default:
        throw new Error(`Unsupported sandbox type: ${sandboxType}`);
    }
  }

  /**
   * 获取沙箱配置
   * @param config 应用配置
   * @returns 沙箱配置
   */
  private static getSandboxConfig(config: MicroAppConfig): SandboxConfig {
    const defaultConfig: SandboxConfig = {
      type: SANDBOX_TYPES.PROXY,
      appId: config.id,
      enabled: true,
      strictMode: false
    };

    // 基础配置对象
    let sandboxConfig: any = {
      ...defaultConfig,
      // 这些属性虽然不在SandboxConfig类型中定义，但在运行时需要
      whiteList: ['document', 'window', 'location'],
      blackList: ['eval', 'Function', 'window.top', 'window.parent']
    };

    // 如果配置了sandbox选项，合并配置
    if (config.metadata?.sandbox) {
      sandboxConfig = {
        ...sandboxConfig,
        ...config.metadata.sandbox
      };
    }

    return sandboxConfig as SandboxConfig;
  }

  /**
   * 创建代理沙箱
   * @param config 沙箱配置
   * @returns 代理沙箱实例
   */
  private static async createProxySandbox(config: SandboxConfig): Promise<Sandbox> {
    try {
      // 导入代理沙箱实现
      const { EnhancedProxySandbox } = await import('./enhanced-proxy-sandbox');
      return new EnhancedProxySandbox(config);
    } catch (error) {
      console.error('Failed to create EnhancedProxySandbox:', error);
      throw error;
    }
  }

  /**
   * 创建iframe沙箱
   * @param config 沙箱配置
   * @returns iframe沙箱实例
   */
  private static async createIframeSandbox(config: SandboxConfig): Promise<Sandbox> {
    try {
      const { IframeSandbox } = await import('./iframe-sandbox');
      return new IframeSandbox(config);
    } catch (error) {
      console.error('Failed to create IframeSandbox:', error);
      // 返回简单实现作为降级方案
      return {
        eval: async (code: string) => {},
        unmount: () => {},
        destroy: () => {}
      } as Sandbox;
    }
  }

  /**
   * 创建快照沙箱
   * @param config 沙箱配置
   * @returns 快照沙箱实例
   */
  private static async createSnapshotSandbox(config: SandboxConfig): Promise<Sandbox> {
    try {
      const { SnapshotSandbox } = await import('./snapshot-sandbox');
      return new SnapshotSandbox(config);
    } catch (error) {
      console.error('Failed to create SnapshotSandbox:', error);
      // 返回简单实现作为降级方案
      return {
        eval: async (code: string) => {},
        unmount: () => {},
        destroy: () => {}
      } as Sandbox;
    }
  }

  /**
   * 注册自定义沙箱类型
   * @param type 沙箱类型名称
   * @param factory 创建函数
   */
  static registerSandboxType(type: string, factory: (config: SandboxConfig) => Promise<Sandbox>): void {
    // 这里可以实现自定义沙箱类型的注册逻辑
    console.warn(`Sandbox type registration is not fully implemented yet: ${type}`);
  }

  /**
   * 获取支持的沙箱类型列表
   */
  static getSupportedSandboxTypes(): SandboxType[] {
    return [SANDBOX_TYPES.PROXY, SANDBOX_TYPES.IFRAME, SANDBOX_TYPES.SNAPSHOT];
  }

  /**
   * 根据应用特性推荐沙箱类型
   * @param config 应用配置
   */
  static recommendSandboxType(config: MicroAppConfig): SandboxType {
    const { metadata } = config;
    
    // 根据应用技术栈推荐沙箱类型
    if (metadata?.technology?.framework === 'react') {
      return SANDBOX_TYPES.PROXY; // React应用适合使用代理沙箱
    } else if (metadata?.technology?.framework === 'vue') {
      return SANDBOX_TYPES.PROXY; // Vue应用也适合使用代理沙箱
    } else if (metadata?.technology?.framework === 'angular') {
      return SANDBOX_TYPES.IFRAME; // Angular应用可能需要iframe沙箱以提供更好的隔离
    }

    // 如果需要严格隔离，推荐iframe沙箱
    if (metadata?.sandbox?.strictMode) {
      return SANDBOX_TYPES.IFRAME;
    }

    // 默认使用代理沙箱
    return SANDBOX_TYPES.PROXY;
  }
  
  /**
   * 根据应用元数据创建沙箱
   * @param appMetadata 应用元数据
   */
  static createSandboxFromAppMetadata(appMetadata: any): Sandbox {
    // 从应用元数据中提取沙箱配置
    const sandboxConfig: SandboxConfig = {
      enabled: true,
      strictMode: false
    };
    
    // 如果存在沙箱配置，则合并
    if (appMetadata && typeof appMetadata === 'object') {
      Object.assign(sandboxConfig, {
        enabled: appMetadata.sandbox?.enabled ?? true,
        strictMode: appMetadata.sandbox?.strictMode ?? false,
        allowedGlobals: appMetadata.sandbox?.allowedGlobals,
        blacklistedProps: appMetadata.sandbox?.blacklistedProps
      });
    }
    
    return this.createSandbox(sandboxConfig);
  }
}