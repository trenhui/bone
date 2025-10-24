import { Sandbox, SandboxConfig, SandboxStatus } from '../types';

interface PropertyDescriptorMap {
  [key: string]: PropertyDescriptor;
}

/**
 * 快照沙箱
 * 基于快照原理的沙箱，在应用执行前后保存和恢复全局状态
 */
export class SnapshotSandbox implements Sandbox {
  private config: SandboxConfig;
  private globalContext: Window;
  private addedProperties: Set<string> = new Set();
  private modifiedProperties: Map<string, PropertyDescriptor> = new Map();
  private isRunning: boolean = false;
  private isMounted: boolean = false;
  private appExports: any = {};
  private appContext: Record<string, any> = {};

  /**
   * 构造函数
   * @param config 沙箱配置
   */
  constructor(config: SandboxConfig) {
    this.config = config;
    this.globalContext = window;
  }

  /**
   * 保存当前全局状态快照
   */
  private saveSnapshot(): void {
    // 清理之前的记录
    this.addedProperties.clear();
    this.modifiedProperties.clear();
  }

  /**
   * 恢复全局状态
   */
  private restoreSnapshot(): void {
    // 恢复修改的属性
    this.modifiedProperties.forEach((descriptor, key) => {
      if (Object.prototype.hasOwnProperty.call(this.globalContext, key)) {
        try {
          Object.defineProperty(this.globalContext, key, descriptor as PropertyDescriptor);
        } catch (error) {
          console.warn(`Failed to restore property ${key}:`, error);
        }
      }
    });

    // 删除新增的属性
    this.addedProperties.forEach(key => {
      try {
        delete (this.globalContext as any)[key];
      } catch (error) {
        console.warn(`Failed to delete property ${key}:`, error);
      }
    });
  }

  /**
   * 记录全局状态变化
   */
  private recordChanges(): void {
    const currentKeys = Object.keys(this.globalContext);
    
    // 记录新增的属性
    currentKeys.forEach(key => {
      // 检查是否在白名单中或应该被忽略的属性
      if (this.shouldIgnoreProperty(key)) {
        return;
      }

      // 检查是否是新添加的属性
      if (!this.modifiedProperties.has(key)) {
        this.addedProperties.add(key);
      }
    });
  }

  /**
   * 检查是否应该忽略某个属性
   * @param key 属性名
   */
  private shouldIgnoreProperty(key: string): boolean {
    // 忽略安全列表中的属性
    if (this.config.allowedGlobals?.includes(key)) {
      return true;
    }
    
    // 忽略特殊属性
    const ignoredProperties = [
      'window', 'document', 'location', 'history', 'navigator', 'screen',
      'console', 'setTimeout', 'setInterval', 'clearTimeout', 'clearInterval',
      'XMLHttpRequest', 'fetch', 'localStorage', 'sessionStorage',
      '__proto__', 'constructor', 'prototype'
    ];
    
    return ignoredProperties.includes(key) || 
           key.startsWith('__') || 
           key.startsWith('webkit');
  }

  /**
   * 获取属性描述符
   * @param key 属性名
   */
  private getPropertyDescriptor(key: string): PropertyDescriptor | null {
    try {
      const descriptor = Object.getOwnPropertyDescriptor(this.globalContext, key);
      return descriptor !== undefined ? descriptor : null;
    } catch (error) {
      return null;
    }
  }

  /**
   * 捕获并记录属性修改
   * @param key 属性名
   */
  private captureProperty(key: string): void {
    if (this.shouldIgnoreProperty(key)) {
      return;
    }

    const descriptor = this.getPropertyDescriptor(key);
    if (descriptor && !this.modifiedProperties.has(key)) {
      this.modifiedProperties.set(key, descriptor);
    }
  }

  /**
   * 执行代码
   * @param code 要执行的代码
   */
  async execute(code: string): Promise<any> {
    this.saveSnapshot();
    this.isRunning = true;

    try {
      // 创建执行上下文
      const context = {
        window: this.globalContext,
        document: document,
        location: location,
        history: history,
        self: this.globalContext,
        globalThis: this.globalContext,
        exports: this.appExports,
        module: { exports: this.appExports }
      };

      // 预处理代码，捕获全局变量声明
      const preprocessedCode = this.preprocessCode(code);

      // 使用Function构造函数执行代码
      const func = new Function(
        'window', 'document', 'location', 'history', 'self', 'globalThis', 'exports', 'module',
        preprocessedCode
      );

      // 执行代码前捕获所有现有属性
      Object.keys(this.globalContext).forEach(key => {
        this.captureProperty(key);
      });

      // 执行代码
      const result = func(
        context.window,
        context.document,
        context.location,
        context.history,
        context.self,
        context.globalThis,
        context.exports,
        context.module
      );

      // 记录执行后的变化
      this.recordChanges();

      return result || this.appExports;
    } catch (error) {
      console.error('Error executing code in snapshot sandbox:', error);
      throw error;
    } finally {
      this.isRunning = false;
    }
  }

  /**
   * 预处理代码，用于捕获全局变量
   * @param code 原始代码
   */
  private preprocessCode(code: string): string {
    // 简单的预处理，在代码执行前后添加钩子
    return `
      try {
        ${code}
        return module.exports || exports;
      } catch (e) {
        console.error('Error in snapshot sandbox:', e);
        throw e;
      }
    `;
  }

  /**
   * 评估资源
   * @param code 代码字符串
   */
  async eval(code: string): Promise<any> {
    try {
      this.isRunning = true;
      return this.execute(code);
    } catch (error) {
      console.error('Error evaluating resources in snapshot sandbox:', error);
      throw error;
    } finally {
      this.isRunning = false;
    }
  }

  /**
   * 挂载沙箱
   */
  mount(): void {
    this.isMounted = true;
    this.saveSnapshot();
  }

  /**
   * 卸载沙箱
   */
  unmount(): void {
    this.isMounted = false;
    this.restoreSnapshot();
  }

  /**
   * 销毁沙箱
   */
  destroy(): void {
    this.unmount();
    this.appExports = {};
    this.appContext = {};
    this.addedProperties.clear();
    this.modifiedProperties.clear();
  }

  /**
   * 获取代理窗口对象
   * 注意：快照沙箱没有真正的代理窗口，返回全局窗口
   */
  getProxyWindow(): Window {
    return this.globalContext;
  }

  /**
   * 获取沙箱状态
   */
  getStatus(): SandboxStatus {
    return {
      appId: this.config.appId || '',
      loaded: true,
      mounted: this.isMounted,
      running: this.isRunning,
      activePropertiesCount: this.modifiedProperties.size,
      resourceCount: 0,
      sideEffectsCount: this.modifiedProperties.size + this.addedProperties.size
    } as SandboxStatus;
  }

  /**
   * 手动添加全局属性到沙箱
   * @param key 属性名
   * @param value 属性值
   */
  addGlobalProperty(key: string, value: any): void {
    if (!this.shouldIgnoreProperty(key)) {
      // 记录原始属性
      this.captureProperty(key);
      
      // 设置新值
      (this.globalContext as any)[key] = value;
      
      // 标记为新增属性
      this.addedProperties.add(key);
    }
  }

  /**
   * 获取沙箱内的全局属性
   * @param key 属性名
   */
  getGlobalProperty(key: string): any {
    if (this.appContext.hasOwnProperty(key)) {
      return this.appContext[key];
    }
    return (this.globalContext as any)[key];
  }

  /**
   * 清空沙箱状态
   */
  clear(): void {
    this.restoreSnapshot();
    this.appExports = {};
    this.appContext = {};
  }

  /**
   * 设置应用导出
   * @param exports 导出对象
   */
  setExports(exports: any): void {
    this.appExports = exports;
    this.appContext.exports = exports;
  }

  /**
   * 获取当前的快照信息
   */
  getSnapshotInfo(): {
    addedProperties: string[];
    modifiedProperties: string[];
  } {
    return {
      addedProperties: Array.from(this.addedProperties),
      modifiedProperties: Array.from(this.modifiedProperties.keys())
    };
  }
}