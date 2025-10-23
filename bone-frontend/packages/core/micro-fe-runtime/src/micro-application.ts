import { MicroAppConfig, AppStatus, AppLifecycleHooks, RouteRule, ErrorContext } from './types';
import { getEventBus } from '@bone/core/event-bus';
import { Sandbox } from './types';
import { SandboxFactory } from './sandbox';
import { ErrorHandler } from './error-handler';
import { PerformanceMonitor } from './performance-monitor';
import { SecurityPolicyEnforcer } from './security-policy-enforcer';
import { ResourceCache } from './resource-cache';
import { AppLifecycle } from './app-lifecycle';

// 运行时版本常量
export const BONE_RUNTIME_VERSION = '1.0.0';

/**
 * 微前端应用核心类
 * 管理应用的完整生命周期、沙箱隔离、性能监控等
 */
export class MicroApplication {
  private status: AppStatus = 'NOT_LOADED';
  private sandbox: Sandbox | null = null;
  private lifecycle: AppLifecycle;
  private errorHandler: ErrorHandler;
  private performanceMonitor: PerformanceMonitor;
  private securityEnforcer: SecurityPolicyEnforcer;
  private resourceCache: ResourceCache;
  private eventBus = getEventBus();
  private containerElement: HTMLElement | null = null;

  /**
   * 获取当前应用状态
   */
  get currentStatus(): AppStatus {
    return this.status;
  }

  /**
   * 获取应用配置
   */
  get config(): MicroAppConfig {
    return this.config;
  }

  /**
   * 构造函数
   * @param config 应用配置
   */
  constructor(private config: MicroAppConfig) {
    this.lifecycle = new AppLifecycle(this);
    this.errorHandler = new ErrorHandler(config.id);
    this.performanceMonitor = new PerformanceMonitor(config.id);
    this.securityEnforcer = new SecurityPolicyEnforcer(config.metadata?.permissions || []);
    this.resourceCache = new ResourceCache(config.id);
    
    // 如果配置了容器，先获取容器元素
    if (config.container) {
      this.containerElement = typeof config.container === 'string' 
        ? document.querySelector(config.container) 
        : config.container;
    }
  }

  /**
   * 检查应用是否应该激活
   * @param path 当前路由路径
   * @returns 是否应该激活
   */
  isActive(path: string): boolean {
    const rule = this.config.activeRule;
    
    if (typeof rule === 'string') {
      return path === rule || path.startsWith(`${rule}/`);
    } else if (rule instanceof RegExp) {
      return rule.test(path);
    } else if (typeof rule === 'function') {
      return rule(path);
    }
    
    return false;
  }

  /**
   * 加载应用资源
   */
  private async loadResources(): Promise<any> {
    const entries = Array.isArray(this.config.entry) ? this.config.entry : [this.config.entry];
    const resources: any[] = [];

    try {
      for (const entry of entries) {
        const response = await fetch(entry);
        if (!response.ok) {
          throw new Error(`Failed to load resource: ${entry}, status: ${response.status}`);
        }
        
        if (entry.endsWith('.js') || entry.endsWith('.mjs')) {
          resources.push(await response.text());
        } else if (entry.endsWith('.json')) {
          resources.push(await response.json());
        } else {
          resources.push(await response.text());
        }
      }

      return resources;
    } catch (error) {
      this.errorHandler.handle(error as Error, {
        phase: 'resourceLoad',
        appInfo: this.config
      });
      throw error;
    }
  }

  /**
   * 加载应用
   */
  async load(): Promise<void> {
    if (this.status !== 'NOT_LOADED') return;
    
    // 安全策略检查
    if (!this.securityEnforcer.checkPermission('load')) {
      throw new SecurityError(`App ${this.config.id} doesn't have permission to load`);
    }
    
    const loadStartTime = performance.now();
    this.status = 'LOADING';
    
    try {
      // 1. 资源加载性能标记
      const resourcesStartTime = performance.now();
      
      // 尝试从缓存获取资源
      let resources = this.resourceCache.get('main');
      if (!resources) {
        // 2. 加载应用资源
        resources = await this.loadResources();
        // 缓存资源
        this.resourceCache.set('main', resources);
      }
      
      this.performanceMonitor.record('resourceLoad', performance.now() - resourcesStartTime);
      
      // 3. 创建沙箱环境
      const sandboxStartTime = performance.now();
      this.sandbox = await SandboxFactory.create(this.config);
      this.performanceMonitor.record('sandboxCreate', performance.now() - sandboxStartTime);
      
      // 4. 执行应用代码
      const evalStartTime = performance.now();
      await this.sandbox.eval?.(resources);
      this.performanceMonitor.record('codeEval', performance.now() - evalStartTime);
      
      // 5. 调用应用生命周期
      await this.lifecycle.bootstrap();
      
      this.status = 'NOT_MOUNTED';
      this.performanceMonitor.record('totalLoad', performance.now() - loadStartTime);
      
      // 记录应用加载成功
      this.performanceMonitor.reportSuccess('appLoad');
    } catch (error) {
      this.status = 'LOAD_ERROR';
      
      // 记录错误并上报
      this.errorHandler.handle(error as Error, {
        phase: 'load',
        appInfo: this.config
      });
      
      throw error;
    }
  }

  /**
   * 挂载应用
   * @param container 容器元素或props对象（当传入字符串时，会查找对应的DOM元素）
   * @param props 传递给应用的属性
   */
  async mount(containerOrProps?: HTMLElement | string | Record<string, any>, props?: Record<string, any>): Promise<void> {
    if (this.status !== 'NOT_MOUNTED') return;
    
    // 处理参数
    let container: HTMLElement | null = null;
    let actualProps = props || {};
    
    if (typeof containerOrProps === 'string') {
      container = document.querySelector(containerOrProps);
      if (!container) {
        throw new Error(`Container element not found: ${containerOrProps}`);
      }
    } else if (containerOrProps instanceof HTMLElement) {
      container = containerOrProps;
    } else if (typeof containerOrProps === 'object') {
      container = this.containerElement || (this.config.container ? document.querySelector(this.config.container) : null);
      actualProps = { ...actualProps, ...containerOrProps };
    } else if (!container && this.containerElement) {
      container = this.containerElement;
    }
    
    if (!container) {
      throw new Error('No container provided or found');
    }
    
    // 保存容器引用
    this.containerElement = container;
    
    const mountStartTime = performance.now();
    this.status = 'MOUNTING';
    
    try {
      // 增强的属性传递，包含上下文信息
      const mountProps = {
        ...this.config.props,
        ...actualProps,
        __boneContext: {
          appId: this.config.id,
          version: this.config.version,
          runtime: { version: BONE_RUNTIME_VERSION }
        }
      };
      
      // 执行沙箱挂载
      this.sandbox?.mount?.();
      
      await this.lifecycle.mount(container, mountProps);
      this.status = 'MOUNTED';
      
      this.performanceMonitor.record('mount', performance.now() - mountStartTime);
    } catch (error) {
      this.status = 'MOUNT_ERROR';
      
      // 记录错误并上报
      this.errorHandler.handle(error as Error, {
        phase: 'mount',
        appInfo: this.config
      });
      
      throw error;
    }
  }

  /**
   * 卸载应用
   */
  async unmount(): Promise<void> {
    if (this.status !== 'MOUNTED') return;
    
    const unmountStartTime = performance.now();
    this.status = 'UNMOUNTING';
    
    try {
      await this.lifecycle.unmount();
      
      // 执行沙箱卸载
      if (this.sandbox) {
        this.sandbox.unmount?.();
      }
      
      this.status = 'NOT_MOUNTED';
      this.performanceMonitor.record('unmount', performance.now() - unmountStartTime);
    } catch (error) {
      this.status = 'UNMOUNT_ERROR';
      
      // 记录错误并上报
      this.errorHandler.handle(error as Error, {
        phase: 'unmount',
        appInfo: this.config
      });
      
      throw error;
    }
  }

  /**
   * 更新应用
   * @param props 更新的属性
   */
  async update(props?: Record<string, any>): Promise<void> {
    if (this.status !== 'MOUNTED') {
      throw new Error(`Cannot update app in ${this.status} state`);
    }
    
    const updateStartTime = performance.now();
    this.status = 'UPDATING';
    
    try {
      const updateProps = {
        ...props,
        __boneContext: {
          appId: this.config.id,
          version: this.config.version,
          runtime: { version: BONE_RUNTIME_VERSION },
          updateTime: Date.now()
        }
      };
      
      await this.lifecycle.update(updateProps);
      this.status = 'MOUNTED';
      
      this.performanceMonitor.record('update', performance.now() - updateStartTime);
    } catch (error) {
      this.status = 'UPDATE_ERROR';
      
      // 记录错误并上报
      this.errorHandler.handle(error as Error, {
        phase: 'update',
        appInfo: this.config
      });
      
      throw error;
    }
  }

  /**
   * 销毁应用
   */
  async destroy(): Promise<void> {
    try {
      // 先卸载
      if (this.status === 'MOUNTED') {
        await this.unmount();
      }
      
      // 调用销毁钩子
      await this.lifecycle.destroy();
      
      // 销毁沙箱
      if (this.sandbox) {
        this.sandbox.destroy?.();
        this.sandbox = null;
      }
      
      // 清空缓存
      this.resourceCache.clear();
      
      // 重置状态
      this.status = 'NOT_LOADED';
    } catch (error) {
      // 记录错误并上报
      this.errorHandler.handle(error as Error, {
        phase: 'destroy',
        appInfo: this.config
      });
      
      throw error;
    }
  }

  /**
   * 获取应用性能指标
   */
  getPerformanceMetrics() {
    return this.performanceMonitor.getMetrics();
  }

  /**
   * 获取应用沙箱状态
   */
  getSandboxStatus() {
    return this.sandbox?.getStatus?.();
  }
}
}