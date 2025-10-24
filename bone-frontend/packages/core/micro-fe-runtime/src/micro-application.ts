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
    return this._config;
  }
  
  /**
   * 私有配置引用，避免getter中的递归调用
   */
  private _config: MicroAppConfig;

  /**
   * 构造函数
   * @param config 应用配置
   */
  constructor(config: MicroAppConfig) {
    // 存储配置引用，避免getter中的递归调用
    this._config = config;
    
    // 初始化各个管理器
    this.lifecycle = new AppLifecycle(this);
    this.errorHandler = new ErrorHandler(config.id);
    this.performanceMonitor = new PerformanceMonitor(config.id);
    this.securityEnforcer = new SecurityPolicyEnforcer(config.metadata?.permissions || []);
    this.resourceCache = new ResourceCache(config.id);
    
    // 初始化容器元素
    this.initializeContainer(config.container);
    
    // 记录应用创建
    this.performanceMonitor.record('create', 0);
  }
  
  /**
   * 安全初始化容器元素
   */
  private initializeContainer(container?: string | HTMLElement): void {
    try {
      if (container) {
        this.containerElement = typeof container === 'string' 
          ? document.querySelector(container) 
          : container;
        
        // 验证容器有效性
        if (this.containerElement && !(this.containerElement instanceof HTMLElement)) {
          console.warn(`Container for app ${this._config.id} is not a valid HTMLElement`);
          this.containerElement = null;
        }
      }
    } catch (error) {
      this.errorHandler.handle(error as Error, { 
        phase: 'init', 
        additionalInfo: { action: 'initializeContainer', containerType: typeof container } 
      });
      this.containerElement = null;
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
  /**
   * 加载应用资源
   */
  private async loadResources(): Promise<any> {
    try {
      const entries = Array.isArray(this._config.entry) ? this._config.entry : [this._config.entry];
      const resources: any[] = [];
      const loadPromises = [];

      // 并行加载所有资源以提高性能
      for (const entry of entries) {
        const loadPromise = this.loadSingleResource(entry)
          .then(resource => {
            resources.push(resource);
            return resource;
          })
          .catch(error => {
            // 记录单个资源加载失败，但继续尝试其他资源
            const resourceError = new Error(`Failed to load resource: ${entry}, ${error instanceof Error ? error.message : String(error)}`);
            this.errorHandler.handle(resourceError, {
              phase: 'resourceLoad',
              additionalInfo: { 
                resourceUrl: entry,
                isSingleResourceFailure: true
              }
            });
            return null; // 返回null表示加载失败
          });
        
        loadPromises.push(loadPromise);
      }

      await Promise.all(loadPromises);
      
      // 检查是否所有资源都加载失败
      const validResources = resources.filter(r => r !== null);
      if (validResources.length === 0) {
        throw new Error('Failed to load all application resources');
      }

      return validResources;
    } catch (error) {
      this.errorHandler.handle(error as Error, {
        phase: 'resourceLoad',
        appInfo: this._config
      });
      throw error;
    }
  }
  
  /**
   * 加载单个资源
   */
  private async loadSingleResource(entry: string): Promise<any> {
    // 检查URL是否有效
    if (!entry || typeof entry !== 'string') {
      throw new Error('Invalid resource URL');
    }
    
    // 添加超时处理
    const fetchWithTimeout = (url: string, options = {}, timeout = 30000) => {
      return Promise.race([
        fetch(url, options),
        new Promise<never>((_, reject) => 
          setTimeout(() => reject(new Error(`Resource fetch timeout: ${url}`)), timeout)
        )
      ]);
    };
    
    const response = await fetchWithTimeout(entry);
    
    if (!response.ok) {
      throw new Error(`Failed to load resource: ${entry}, status: ${response.status}`);
    }
    
    // 根据资源类型进行不同处理
    const contentType = response.headers.get('content-type');
    
    if (entry.endsWith('.json') || (contentType && contentType.includes('application/json'))) {
      return await response.json();
    } else if (entry.endsWith('.js') || entry.endsWith('.mjs') || entry.endsWith('.ts') || entry.endsWith('.tsx')) {
      return await response.text();
    } else {
      // 默认作为文本处理
      return await response.text();
    }
  }

  /**
   * 加载应用
   */
  /**
   * 加载应用
   */
  async load(): Promise<void> {
    // 状态检查
    if (this.status !== 'NOT_LOADED') {
      console.warn(`Cannot load app ${this._config.id} in ${this.status} state`);
      return;
    }
    
    // 安全策略检查
    if (!this.securityEnforcer.checkPermission('load')) {
      const permissionError = new SecurityError(`App ${this._config.id} doesn't have permission to load`);
      this.status = 'LOAD_ERROR';
      this.errorHandler.handle(permissionError, {
        phase: 'load',
        additionalInfo: { action: 'permissionCheck' }
      });
      throw permissionError;
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
      this.sandbox = await SandboxFactory.create(this._config);
      this.performanceMonitor.record('sandboxCreate', performance.now() - sandboxStartTime);
      
      // 4. 执行应用代码
      const evalStartTime = performance.now();
      try {
        if (this.sandbox && typeof this.sandbox.eval === 'function') {
          await this.sandbox.eval(resources);
        } else {
          throw new Error('Sandbox eval function is not available');
        }
      } catch (evalError) {
        // 专门处理代码执行错误
        const wrappedError = new Error(`Failed to execute app code: ${evalError instanceof Error ? evalError.message : String(evalError)}`);
        wrappedError.stack = evalError instanceof Error ? evalError.stack : wrappedError.stack;
        throw wrappedError;
      }
      this.performanceMonitor.record('codeEval', performance.now() - evalStartTime);
      
      // 5. 调用应用生命周期
      await this.lifecycle.bootstrap();
      
      this.status = 'NOT_MOUNTED';
      this.performanceMonitor.record('totalLoad', performance.now() - loadStartTime);
      
      // 记录应用加载成功
      this.performanceMonitor.reportSuccess('appLoad');
      
      // 触发加载成功事件
      this.eventBus.emit('app:load:success', { 
        appId: this._config.id,
        loadTime: performance.now() - loadStartTime
      });
      
    } catch (error) {
      this.status = 'LOAD_ERROR';
      
      // 记录错误并上报
      const errorContext = {
        phase: 'load',
        appInfo: this._config,
        additionalInfo: {
          attemptedState: this.status,
          loadDuration: performance.now() - loadStartTime
        }
      };
      
      this.errorHandler.handle(error as Error, errorContext);
      
      // 触发加载失败事件
      this.eventBus.emit('app:load:error', { 
        appId: this._config.id,
        error: error as Error,
        context: errorContext
      });
      
      // 尝试清理已创建的资源
      this.cleanupOnLoadError();
      
      throw error;
    }
  }
  
  /**
   * 加载错误时的清理
   */
  private cleanupOnLoadError(): void {
    try {
      // 清理沙箱
      if (this.sandbox) {
        this.sandbox.destroy?.();
        this.sandbox = null;
      }
      
      // 清理缓存
      this.resourceCache.clear();
    } catch (cleanupError) {
      console.warn('Failed to cleanup after load error:', cleanupError);
    }
  }

  /**
   * 挂载应用
   * @param container 容器元素或props对象（当传入字符串时，会查找对应的DOM元素）
   * @param props 传递给应用的属性
   */
  /**
   * 挂载应用
   */
  async mount(containerOrProps?: HTMLElement | string | Record<string, any>, props?: Record<string, any>): Promise<void> {
    // 状态检查
    if (this.status !== 'NOT_MOUNTED') {
      const errorMsg = `Cannot mount app ${this._config.id} in ${this.status} state`;
      console.warn(errorMsg);
      if (this.status === 'LOAD_ERROR' || this.status === 'MOUNT_ERROR') {
        // 对于错误状态，尝试重新加载
        console.log(`Attempting to reload app ${this._config.id} before mounting`);
        await this.load();
      } else {
        return;
      }
    }
    
    // 处理参数
    let container: HTMLElement | null = null;
    let actualProps = props || {};
    
    try {
      // 解析容器和属性
      container = this.resolveContainer(containerOrProps);
      
      // 如果containerOrProps是对象，则合并到属性中
      if (typeof containerOrProps === 'object' && !(containerOrProps instanceof HTMLElement)) {
        actualProps = { ...actualProps, ...containerOrProps };
      }
      
      // 验证容器
      if (!container || !(container instanceof HTMLElement)) {
        throw new Error('No valid container provided or found');
      }
      
      // 保存容器引用
      this.containerElement = container;
      
      const mountStartTime = performance.now();
      this.status = 'MOUNTING';
      
      // 增强的属性传递，包含上下文信息
      const mountProps = {
        ...this._config.props,
        ...actualProps,
        __boneContext: {
          appId: this._config.id,
          version: this._config.version,
          runtime: { version: BONE_RUNTIME_VERSION },
          mountTime: Date.now()
        }
      };
      
      // 执行沙箱挂载
      if (this.sandbox && typeof this.sandbox.mount === 'function') {
        try {
          await this.sandbox.mount();
        } catch (sandboxMountError) {
          // 记录沙箱挂载错误，但继续尝试
          this.errorHandler.handle(sandboxMountError as Error, {
            phase: 'mount',
            additionalInfo: { action: 'sandboxMount' }
          });
        }
      }
      
      // 调用生命周期挂载
      await this.lifecycle.mount(container, mountProps);
      this.status = 'MOUNTED';
      
      const mountDuration = performance.now() - mountStartTime;
      this.performanceMonitor.record('mount', mountDuration);
      
      // 触发挂载成功事件
      this.eventBus.emit('app:mount:success', { 
        appId: this._config.id,
        container,
        mountTime: mountDuration
      });
      
    } catch (error) {
      this.status = 'MOUNT_ERROR';
      
      // 记录错误并上报
      const errorContext = {
        phase: 'mount',
        appInfo: this._config,
        additionalInfo: {
          containerType: containerOrProps ? typeof containerOrProps : 'undefined',
          hasValidContainer: !!container && container instanceof HTMLElement
        }
      };
      
      this.errorHandler.handle(error as Error, errorContext);
      
      // 触发挂载失败事件
      this.eventBus.emit('app:mount:error', { 
        appId: this._config.id,
        error: error as Error,
        context: errorContext
      });
      
      // 清理容器
      if (container && container instanceof HTMLElement) {
        try {
          while (container.firstChild) {
            container.removeChild(container.firstChild);
          }
        } catch (cleanupError) {
          console.warn('Failed to cleanup container after mount error:', cleanupError);
        }
      }
      
      throw error;
    }
  }
  
  /**
   * 解析容器元素
   */
  private resolveContainer(containerOrProps?: HTMLElement | string | Record<string, any>): HTMLElement | null {
    let container: HTMLElement | null = null;
    
    // 1. 如果是字符串，尝试选择器查询
    if (typeof containerOrProps === 'string') {
      container = document.querySelector(containerOrProps);
      if (!container) {
        throw new Error(`Container element not found: ${containerOrProps}`);
      }
    }
    // 2. 如果是HTMLElement，直接使用
    else if (containerOrProps instanceof HTMLElement) {
      container = containerOrProps;
    }
    // 3. 尝试使用已有的容器引用
    else if (this.containerElement) {
      container = this.containerElement;
    }
    // 4. 尝试从配置中获取
    else if (this._config.container) {
      container = typeof this._config.container === 'string' 
        ? document.querySelector(this._config.container) 
        : this._config.container;
    }
    
    return container;
  }

  /**
   * 卸载应用
   */
  /**
   * 卸载应用
   */
  async unmount(): Promise<void> {
    // 状态检查
    if (this.status !== 'MOUNTED') {
      console.warn(`Cannot unmount app ${this._config.id} in ${this.status} state`);
      // 即使不是MOUNTED状态，也尝试清理资源
      if (this.status === 'MOUNT_ERROR' || this.status === 'UNMOUNT_ERROR') {
        this.cleanupResources();
      }
      return;
    }
    
    const unmountStartTime = performance.now();
    this.status = 'UNMOUNTING';
    
    // 定义清理资源的函数
    const cleanupResources = () => {
      try {
        // 执行沙箱卸载
        if (this.sandbox) {
          try {
            this.sandbox.unmount?.();
          } catch (sandboxUnmountError) {
            console.warn('Failed to unmount sandbox:', sandboxUnmountError);
          }
        }
        
        // 清理容器引用
        this.containerElement = null;
      } catch (cleanupError) {
        console.warn('Error during resource cleanup:', cleanupError);
      }
    };
    
    try {
      // 调用生命周期卸载
      await this.lifecycle.unmount();
      
      // 清理资源
      cleanupResources();
      
      this.status = 'NOT_MOUNTED';
      
      const unmountDuration = performance.now() - unmountStartTime;
      this.performanceMonitor.record('unmount', unmountDuration);
      
      // 触发卸载成功事件
      this.eventBus.emit('app:unmount:success', { 
        appId: this._config.id,
        unmountTime: unmountDuration
      });
      
    } catch (error) {
      this.status = 'UNMOUNT_ERROR';
      
      // 即使出错也强制清理资源
      cleanupResources();
      
      // 记录错误并上报
      const errorContext = {
        phase: 'unmount',
        appInfo: this._config
      };
      
      this.errorHandler.handle(error as Error, errorContext);
      
      // 触发卸载失败事件
      this.eventBus.emit('app:unmount:error', { 
        appId: this._config.id,
        error: error as Error,
        context: errorContext
      });
      
      throw error;
    }
  }

  /**
   * 更新应用
   * @param props 更新的属性
   */
  /**
   * 更新应用
   */
  async update(props?: Record<string, any>): Promise<void> {
    // 状态检查
    if (this.status !== 'MOUNTED') {
      const errorMsg = `Cannot update app ${this._config.id} in ${this.status} state`;
      console.error(errorMsg);
      throw new Error(errorMsg);
    }
    
    const updateStartTime = performance.now();
    this.status = 'UPDATING';
    
    try {
      const updateProps = {
        ...props,
        __boneContext: {
          appId: this._config.id,
          version: this._config.version,
          runtime: { version: BONE_RUNTIME_VERSION },
          updateTime: Date.now(),
          updateCount: (this.performanceMonitor.getMetrics().updateCount || 0) + 1
        }
      };
      
      await this.lifecycle.update(updateProps);
      this.status = 'MOUNTED';
      
      const updateDuration = performance.now() - updateStartTime;
      this.performanceMonitor.record('update', updateDuration);
      
      // 触发更新成功事件
      this.eventBus.emit('app:update:success', { 
        appId: this._config.id,
        updateTime: updateDuration,
        hasProps: !!props
      });
      
    } catch (error) {
      this.status = 'UPDATE_ERROR';
      
      // 记录错误并上报
      const errorContext = {
        phase: 'update',
        appInfo: this._config,
        additionalInfo: { hasProps: !!props }
      };
      
      this.errorHandler.handle(error as Error, errorContext);
      
      // 触发更新失败事件
      this.eventBus.emit('app:update:error', { 
        appId: this._config.id,
        error: error as Error,
        context: errorContext
      });
      
      // 尝试回滚到稳定状态
      this.status = 'MOUNTED';
      
      throw error;
    }
  }
  
  /**
   * 强制重新加载应用
   */
  async reload(): Promise<void> {
    const appId = this._config.id;
    console.log(`Reloading app: ${appId}`);
    
    try {
      // 先卸载
      if (this.status === 'MOUNTED') {
        await this.unmount();
      }
      
      // 清理资源
      this.cleanupResources();
      
      // 重置状态
      this.status = 'NOT_LOADED';
      
      // 重新加载
      await this.load();
      
      // 如果之前是挂载状态，重新挂载
      if (this.containerElement && this.status === 'NOT_MOUNTED') {
        await this.mount(this.containerElement);
      }
      
      console.log(`App ${appId} reloaded successfully`);
      
    } catch (error) {
      this.errorHandler.handle(error as Error, {
        phase: 'reload',
        appInfo: this._config
      });
      throw error;
    }
  }
  
  /**
   * 清理资源
   */
  private cleanupResources(): void {
    try {
      // 清理沙箱
      if (this.sandbox) {
        try {
          this.sandbox.destroy?.();
        } catch (destroyError) {
          console.warn('Failed to destroy sandbox:', destroyError);
        }
        this.sandbox = null;
      }
      
      // 清理容器
      if (this.containerElement) {
        try {
          while (this.containerElement.firstChild) {
            this.containerElement.removeChild(this.containerElement.firstChild);
          }
        } catch (cleanupError) {
          console.warn('Failed to cleanup container:', cleanupError);
        }
        this.containerElement = null;
      }
      
      // 不清理缓存，允许后续复用
    } catch (error) {
      console.error('Error during resource cleanup:', error);
    }
  }
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
  /**
   * 销毁应用
   */
  async destroy(): Promise<void> {
    const appId = this._config.id;
    console.log(`Destroying app: ${appId}`);
    
    // 无论发生什么，都要尝试销毁资源
    const forceDestroyResources = () => {
      try {
        // 销毁沙箱
        if (this.sandbox) {
          try {
            this.sandbox.destroy?.();
          } catch (destroyError) {
            console.warn('Failed to destroy sandbox during force cleanup:', destroyError);
          }
          this.sandbox = null;
        }
        
        // 清空缓存
        this.resourceCache.clear();
        
        // 重置状态
        this.status = 'NOT_LOADED';
        
      } catch (cleanupError) {
        console.error('Failed to cleanup resources during force destroy:', cleanupError);
      }
    };
    
    try {
      // 先卸载
      if (this.status === 'MOUNTED') {
        try {
          await this.unmount();
        } catch (unmountError) {
          // 记录卸载错误，但继续销毁流程
          this.errorHandler.handle(unmountError as Error, {
            phase: 'destroy',
            additionalInfo: { action: 'unmount' }
          });
        }
      }
      
      // 调用生命周期销毁
      await this.lifecycle.destroy();
      
      // 清理资源
      forceDestroyResources();
      
      // 触发销毁成功事件
      this.eventBus.emit('app:destroy:success', { appId });
      
      console.log(`App ${appId} destroyed successfully`);
      
    } catch (error) {
      // 记录错误并上报
      const errorContext = {
        phase: 'destroy',
        appInfo: this._config,
        additionalInfo: { attemptedState: this.status }
      };
      
      this.errorHandler.handle(error as Error, errorContext);
      
      // 强制销毁资源
      forceDestroyResources();
      
      // 触发销毁失败事件
      this.eventBus.emit('app:destroy:error', { 
        appId,
        error: error as Error,
        context: errorContext
      });
      
      throw error;
    }
  }
  
  /**
   * 获取应用诊断信息
   */
  getDiagnostics(): Record<string, any> {
    return {
      appId: this._config.id,
      status: this.status,
      version: this._config.version,
      hasContainer: !!this.containerElement,
      hasSandbox: !!this.sandbox,
      sandboxStatus: this.sandbox?.getStatus?.(),
      performance: this.performanceMonitor.getMetrics(),
      errorStats: this.errorHandler.getStatistics()
    };
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