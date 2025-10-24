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



/**
 * 全局性能监控器
 * 提供应用级别的性能指标收集和上报
 */
export class PerformanceMonitor {
  private static instance: PerformanceMonitor;
  private startTime: number = Date.now();
  private metrics: Map<string, any[]> = new Map();
  private operationTimers: Map<string, number> = new Map();
  private enabled: boolean = true;

  // 私有构造函数
  private constructor() {
    // 初始化性能监控器
  }

  // 获取单例实例
  public static getInstance(): PerformanceMonitor {
    if (!PerformanceMonitor.instance) {
      PerformanceMonitor.instance = new PerformanceMonitor();
    }
    return PerformanceMonitor.instance;
  }

  // 记录性能指标
  public record(name: string, duration: number, details?: any): void {
    if (!this.enabled) return;
    
    const record = {
      name,
      duration,
      timestamp: Date.now(),
      details
    };
    
    if (!this.metrics.has(name)) {
      this.metrics.set(name, []);
    }
    
    this.metrics.get(name)!.push(record);
  }

  // 开始计时
  public startTimer(operation: string): void {
    if (!this.enabled) return;
    this.operationTimers.set(operation, performance.now());
  }

  // 结束计时
  public endTime(operation: string, details?: any): number {
    if (!this.enabled) return 0;
    
    const startTime = this.operationTimers.get(operation);
    if (!startTime) {
      return 0;
    }
    
    this.operationTimers.delete(operation);
    const duration = performance.now() - startTime;
    this.record(operation, duration, details);
    return duration;
  }

  // 获取指标统计数据
  public getMetrics(): any {
    const metrics: any = {};
    
    this.metrics.forEach((records, name) => {
      const durations = records.map((r: any) => r.duration);
      const total = durations.reduce((sum: number, d: number) => sum + d, 0);
      const count = records.length;
      
      if (count > 0) {
        metrics[name] = {
          count,
          avg: total / count,
          min: Math.min(...durations),
          max: Math.max(...durations),
          last: durations[durations.length - 1],
          total
        };
      }
    });
    
    return metrics;
  }

  // 导出指标数据
  public exportMetrics(): any {
    return this.getMetrics();
  }

  // 清除指标数据
  public clearMetrics(): void {
    this.metrics.clear();
    this.operationTimers.clear();
    this.startTime = Date.now();
  }

  // 检查指标是否超过阈值
  public isMetricAboveThreshold(name: string, threshold: number): boolean {
    const metrics = this.getMetrics();
    const metric = metrics[name];
    return !!metric && metric.last > threshold;
  }

  // 获取所有指标名称
  public getMetricNames(): string[] {
    return Array.from(this.metrics.keys());
  }

  // 获取指标历史记录
  public getMetricHistory(name: string, limit: number = 10): any[] {
    const records = this.metrics.get(name) || [];
    return records.slice(-limit);
  }

  // 测量函数执行时间
  public measureFunction<T>(name: string, fn: () => T, details?: any): T {
    if (!this.enabled) return fn();
    
    const start = performance.now();
    try {
      return fn();
    } finally {
      const duration = performance.now() - start;
      this.record(name, duration, details);
    }
  }

  // 测量异步函数执行时间
  public async measureAsyncFunction<T>(name: string, fn: () => Promise<T>, details?: any): Promise<T> {
    if (!this.enabled) return fn();
    
    const start = performance.now();
    try {
      return await fn();
    } finally {
      const duration = performance.now() - start;
      this.record(name, duration, details);
    }
  }

  // 收集并上报性能指标到服务器
  public async reportMetrics(url: string, additionalData?: any): Promise<boolean> {
    try {
      const metrics = this.exportMetrics();
      const data = {
        timestamp: Date.now(),
        metrics,
        appRuntime: this.getAppRuntime(),
        ...additionalData
      };

      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(data)
      });

      return response.ok;
    } catch (error) {
      console.error('Failed to report metrics:', error);
      return false;
    }
  }

  // 启用性能监控
  public enable(): void {
    this.enabled = true;
  }

  // 禁用性能监控
  public disable(): void {
    this.enabled = false;
  }

  // 检查是否启用
  public isEnabled(): boolean {
    return this.enabled;
  }

  // 添加自定义指标收集器
  public addCustomCollector(collector: { collect: () => void }): void {
    if (this.enabled) {
      collector.collect();
    }
  }

  // 获取应用运行时间
  public getAppRuntime(): number {
    return Date.now() - this.startTime;
  }

  // 重置应用运行时间
  public resetAppRuntime(): void {
    this.startTime = Date.now();
  }
}

export const BONE_RUNTIME_VERSION = '1.0.0';