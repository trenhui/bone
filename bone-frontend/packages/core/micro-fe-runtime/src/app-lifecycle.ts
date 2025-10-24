import { MicroApplication } from './micro-application';
import { AppLifecycleHooks, MicroAppConfig, ErrorContext } from './types';
import { ErrorHandler } from './error-handler';

// 本地事件总线实现（替代外部依赖）
class LocalEventBus {
  private events: Record<string, Function[]> = {};
  
  on(event: string, handler: Function) {
    if (!this.events[event]) {
      this.events[event] = [];
    }
    this.events[event].push(handler);
  }
  
  emit(event: string, data: any) {
    if (this.events[event]) {
      this.events[event].forEach(handler => handler(data));
    }
  }
  
  off(event: string, handler?: Function) {
    if (handler) {
      this.events[event] = this.events[event]?.filter(h => h !== handler) || [];
    } else {
      delete this.events[event];
    }
  }
}

const localEventBus = new LocalEventBus();
const getEventBus = () => localEventBus;

/**
 * 应用生命周期管理器
 * 负责协调应用的各个生命周期阶段
 */
export class AppLifecycle {
  private hooks: AppLifecycleHooks = {};
  private appExports: any = {};
  private eventBus = getEventBus();
  private errorHandler: ErrorHandler;
  
  // 应用配置缓存
  private appConfig?: MicroAppConfig;
  private currentContainer: HTMLElement | null = null;
  private currentProps: Record<string, any> = {};
  private hookTimeouts: Record<string, number> = {};
  private defaultHookTimeout = 30000; // 默认钩子超时时间30秒

  /**
   * 构造函数
   * @param app 微应用实例
   */
  constructor(private app: MicroApplication) {
    // 初始化错误处理器
    const appId = this.getAppId();
    this.errorHandler = new ErrorHandler(appId);
    
    // 初始化时注册默认的全局hooks
    this.registerGlobalHooks();
    
    // 设置事件监听器
    this.setupEventListeners();
    
    // 缓存应用配置
    try {
      this.appConfig = this.app.config;
    } catch (error) {
      this.errorHandler.handle(error as Error, { phase: 'init', additionalInfo: { action: 'getAppConfig' } });
    }
  }

  /**
   * 注册全局生命周期钩子
   */
  private registerGlobalHooks(): void {
    // 可以在这里注册一些通用的生命周期钩子
  }
  
  /**
   * 设置事件监听器
   */
  private setupEventListeners(): void {
    try {
      const appId = this.getAppId();
      // 监听应用钩子注册事件
      this.eventBus.on(`app:${appId}:register-hooks`, (hooks: AppLifecycleHooks) => {
        this.registerHooks(hooks);
      });
    } catch (error) {
      this.errorHandler.handle(error as Error, { phase: 'init', additionalInfo: { action: 'setupEventListeners' } });
    }
  }
  
  /**
   * 注册生命周期钩子
   * @param hooks 钩子对象
   */
  registerHooks(hooks: AppLifecycleHooks): void {
    try {
      // 验证钩子类型
      Object.entries(hooks).forEach(([key, value]) => {
        if (value !== null && value !== undefined && typeof value !== 'function') {
          console.warn(`Hook ${key} is not a function, skipping registration`);
          delete hooks[key as keyof AppLifecycleHooks];
        }
      });
      
      this.hooks = {
        ...this.hooks,
        ...hooks
      };
      
      const appId = this.getAppId();
      console.log(`Registered lifecycle hooks for app ${appId}:`, 
        Object.keys(hooks).filter(h => typeof this.hooks[h as keyof AppLifecycleHooks] === 'function').join(', '));
    } catch (error) {
      this.errorHandler.handle(error as Error, { phase: 'init', additionalInfo: { action: 'registerHooks' } });
    }
  }

  /**
   * 设置应用导出的生命周期钩子
   * @param exports 应用导出的内容
   */
  setAppExports(exports: any): void {
    this.appExports = exports;
    
    // 从应用导出中提取生命周期钩子
    if (exports) {
      this.hooks = {
        bootstrap: exports.bootstrap,
        mount: exports.mount,
        update: exports.update,
        unmount: exports.unmount,
        destroy: exports.destroy
      };
    }
  }

  /**
   * 引导阶段 - 应用初始化
   */
  async bootstrap(): Promise<void> {
    const appId = this.getAppId();
    
    try {
      console.log(`Bootstrapping app: ${appId}`);
      this.eventBus.emit('app:bootstrap:before', { appId });
      
      // 优先使用应用导出的bootstrap钩子
      if (this.hooks.bootstrap) {
        await this.safeExecuteHook('bootstrap', () => this.hooks.bootstrap!());
      } else if (this.appExports && typeof this.appExports === 'function') {
        // 兼容旧版本，如果导出的是函数，则作为bootstrap使用
        await this.safeExecuteHook('bootstrap', () => this.appExports());
      }
      
      this.eventBus.emit('app:bootstrap:after', { appId });
      console.log(`App ${appId} bootstrapped successfully`);
    } catch (error) {
      this.handleError(error as Error, 'bootstrap');
      throw error;
    }
  }

  /**
   * 挂载阶段 - 将应用渲染到容器
   * @param container 容器元素
   * @param props 传递的属性
   */
  async mount(container: HTMLElement, props?: Record<string, any>): Promise<void> {
    const appId = this.getAppId();
    
    try {
      // 确保容器存在
      if (!container || !(container instanceof HTMLElement)) {
        throw new Error('Valid container element is required for mounting');
      }

      console.log(`Mounting app: ${appId}`);
      this.eventBus.emit('app:mount:before', { appId, container, props });
      
      // 保存当前容器和属性
      this.currentContainer = container;
      this.currentProps = props || {};
      
      // 调用 beforeMount 钩子
      if (this.hooks.beforeMount) {
        await this.safeExecuteHook('beforeMount', () => this.hooks.beforeMount!());
      }
      
      // 优先使用应用导出的mount钩子
      let mountSuccess = false;
      
      if (this.hooks.mount) {
        await this.safeExecuteHook('mount', () => this.hooks.mount!(container, props));
        mountSuccess = true;
      } else {
        // 尝试兼容不同的导出格式
        if (this.appExports && this.appExports.default) {
          // 处理ES模块默认导出
          const defaultExport = this.appExports.default;
          if (typeof defaultExport === 'function') {
            await this.safeExecuteHook('mount', () => defaultExport(container, props));
            mountSuccess = true;
          }
        }
      }
      
      // 如果没有找到合适的mount方法，抛出错误
      if (!mountSuccess) {
        throw new Error(`No valid mount method found for app ${appId}`);
      }
      
      // 调用 afterMount 钩子
      if (this.hooks.afterMount) {
        await this.safeExecuteHook('afterMount', () => this.hooks.afterMount!());
      }
      
      this.eventBus.emit('app:mount:after', { appId, container, props });
      console.log(`App ${appId} mounted successfully`);
    } catch (error) {
      // 出错时清理容器
      if (container && container instanceof HTMLElement) {
        try {
          this.cleanupContainer(container);
        } catch (cleanupError) {
          console.warn('Failed to cleanup container after mount error:', cleanupError);
        }
      }
      
      this.handleError(error as Error, 'mount');
      throw error;
    }
  }

  /**
   * 更新阶段 - 更新应用状态
   * @param props 更新的属性
   */
  async update(props?: Record<string, any>): Promise<void> {
    const appId = this.app.config.id;
    
    try {
      console.log(`Updating app: ${appId}`);
      this.eventBus.emit('app:update:before', { appId, props });
      
      // 更新当前属性
      this.currentProps = {
        ...this.currentProps,
        ...(props || {})
      };
      
      // 如果存在update钩子，则执行
      if (this.hooks.update) {
        await this.safeExecuteHook('update', () => this.hooks.update!(this.currentProps));
      }
      
      this.eventBus.emit('app:update:after', { appId, props });
      console.log(`App ${appId} updated successfully`);
    } catch (error) {
      this.handleError(error, 'update');
      throw error;
    }
  }

  /**
   * 卸载阶段 - 清理应用资源
   */
  async unmount(): Promise<void> {
    const appId = this.getAppId();
    
    try {
      console.log(`Unmounting app: ${appId}`);
      this.eventBus.emit('app:unmount:before', { appId, container: this.currentContainer });
      
      // 调用 beforeUnmount 钩子
      if (this.hooks.beforeUnmount) {
        try {
          await this.safeExecuteHook('beforeUnmount', () => this.hooks.beforeUnmount!());
        } catch (beforeUnmountError) {
          this.errorHandler.handle(beforeUnmountError as Error, 
            this.buildErrorContext('unmount', { hook: 'beforeUnmount' }));
          // 继续执行unmount，不因beforeUnmount失败而中断
        }
      }
      
      // 优先使用应用导出的unmount钩子
      if (this.hooks.unmount) {
        try {
          await this.safeExecuteHook('unmount', () => this.hooks.unmount!());
        } catch (unmountHookError) {
          this.errorHandler.handle(unmountHookError as Error, 
            this.buildErrorContext('unmount', { hook: 'unmount' }));
          // 继续执行清理，不因unmount钩子失败而中断
        }
      }
      
      // 调用 afterUnmount 钩子
      if (this.hooks.afterUnmount) {
        try {
          await this.safeExecuteHook('afterUnmount', () => this.hooks.afterUnmount!());
        } catch (afterUnmountError) {
          this.errorHandler.handle(afterUnmountError as Error, 
            this.buildErrorContext('unmount', { hook: 'afterUnmount' }));
        }
      }
      
      // 强制清理容器内容，确保资源释放
      let containerCleanupSuccess = false;
      if (this.currentContainer) {
        try {
          this.cleanupContainer(this.currentContainer);
          containerCleanupSuccess = true;
        } catch (cleanupError) {
          this.errorHandler.handle(cleanupError as Error, 
            this.buildErrorContext('unmount', { action: 'cleanupContainer' }));
        }
      }
      
      // 强制重置状态
      this.currentContainer = null;
      this.currentProps = {};
      
      this.eventBus.emit('app:unmount:after', { 
        appId, 
        containerCleanupSuccess 
      });
      console.log(`App ${appId} unmounted ${containerCleanupSuccess ? 'successfully' : 'with cleanup warnings'}`);
    } catch (error) {
      // 即使出错也强制重置关键状态
      this.currentContainer = null;
      this.currentProps = {};
      
      this.handleError(error as Error, 'unmount');
      throw error;
    }
  }

  /**
   * 销毁阶段 - 完全清理应用
   */
  async destroy(): Promise<void> {
    const appId = this.getAppId();
    
    try {
      console.log(`Destroying app: ${appId}`);
      
      // 优先使用应用导出的destroy钩子
      if (this.hooks.destroy) {
        try {
          await this.safeExecuteHook('destroy', () => this.hooks.destroy!());
        } catch (destroyHookError) {
          this.errorHandler.handle(destroyHookError as Error, 
            this.buildErrorContext('destroy', { hook: 'destroy' }));
          // 继续执行清理，不因destroy钩子失败而中断
        }
      }
      
      // 清理所有定时器和事件监听器
      Object.values(this.hookTimeouts).forEach(timeoutId => clearTimeout(timeoutId));
      this.hookTimeouts = {};
      
      // 清理内部状态
      this.appExports = {};
      this.hooks = {};
      this.currentContainer = null;
      this.currentProps = {};
      
      // 触发销毁完成事件
      this.eventBus.emit('app:destroy:after', { appId });
      console.log(`App ${appId} destroyed successfully`);
    } catch (error) {
      this.handleError(error as Error, 'destroy');
      // 即使销毁出错，也强制清理关键状态
      this.reset();
      throw error;
    }
  }

  /**
   * 安全执行生命周期钩子
   * @param phase 生命周期阶段
   * @param hook 要执行的钩子函数
   */
  /**
   * 安全执行生命周期钩子，包含超时保护
   * @param phase 生命周期阶段
   * @param hook 要执行的钩子函数
   * @param timeout 超时时间（毫秒）
   */
  private async safeExecuteHook(phase: string, hook: () => Promise<void> | void, timeout: number = this.defaultHookTimeout): Promise<void> {
    const startTime = performance.now();
    const appId = this.getAppId();
    const hookId = `${appId}:${phase}:${Date.now()}`;
    
    // 创建超时Promise
    const timeoutPromise = new Promise<never>((_, reject) => {
      this.hookTimeouts[hookId] = window.setTimeout(() => {
        reject(new Error(`Lifecycle hook ${phase} timed out after ${timeout}ms`));
      }, timeout);
    });
    
    try {
      // 使用Promise.race实现超时保护
      const result = hook();
      const promiseResult = result instanceof Promise ? result : Promise.resolve();
      
      await Promise.race([promiseResult, timeoutPromise]);
      
      const duration = performance.now() - startTime;
      console.log(`App ${appId} ${phase} hook executed in ${duration.toFixed(2)}ms`);
      
      // 上报钩子执行性能
      this.eventBus.emit('app:lifecycle:performance', {
        appId,
        hookName: phase,
        duration,
        success: true
      });
    } catch (error) {
      const duration = performance.now() - startTime;
      console.error(`Error in lifecycle hook ${phase}:`, error);
      
      // 上报错误性能
      this.eventBus.emit('app:lifecycle:performance', {
        appId,
        hookName: phase,
        duration,
        success: false,
        error: error instanceof Error ? error.message : String(error)
      });
      
      throw error; // 重新抛出错误，让上层处理
    } finally {
      // 清理超时定时器
      if (this.hookTimeouts[hookId]) {
        clearTimeout(this.hookTimeouts[hookId]);
        delete this.hookTimeouts[hookId];
      }
    }
  }
  
  /**
   * 清理容器
   * @param container 容器元素
   */
  private cleanupContainer(container: HTMLElement): void {
    try {
      if (!container || !(container instanceof HTMLElement)) {
        console.warn('Invalid container element for cleanup');
        return;
      }
      
      // 使用更高效的方式清理容器
      container.textContent = '';
      
      // 对于可能的复杂场景，添加额外的清理措施
      // 移除所有事件监听器（通过替换元素）
      const newContainer = container.cloneNode(false) as HTMLElement;
      if (container.parentNode) {
        container.parentNode.replaceChild(newContainer, container);
      }
      
    } catch (error) {
      this.errorHandler.handle(error as Error, 
        this.buildErrorContext('cleanup', { action: 'cleanupContainer' }));
      // 即使出错，也尝试最后一种清理方式
      try {
        if (container && container instanceof HTMLElement) {
          while (container.firstChild) {
            container.removeChild(container.firstChild);
          }
        }
      } catch (fallbackError) {
        console.error(`Failed to cleanup container even with fallback method:`, fallbackError);
      }
    }
  }
  
  /**
   * 处理错误
   * @param error 错误对象
   * @param phase 生命周期阶段
   */
  /**
   * 获取应用ID的安全方法
   */
  private getAppId(): string {
    try {
      return this.appConfig?.id || this.app.config.id;
    } catch (error) {
      return 'unknown-app';
    }
  }
  
  /**
   * 构建错误上下文
   */
  private buildErrorContext(phase: string, additionalInfo?: Record<string, any>): ErrorContext {
    return {
      appId: this.getAppId(),
      phase,
      appInfo: this.appConfig || {},
      additionalInfo: {
        ...additionalInfo,
        currentPropsKeys: Object.keys(this.currentProps || {}),
        hasContainer: !!this.currentContainer
      }
    };
  }
  
  /**
   * 处理错误
   * @param error 错误对象
   * @param phase 生命周期阶段
   * @param additionalInfo 额外信息
   */
  private handleError(error: Error, phase: string, additionalInfo?: Record<string, any>): void {
    const appId = this.getAppId();
    const errorContext = this.buildErrorContext(phase, additionalInfo);
    
    // 使用错误处理器处理错误
    this.errorHandler.handle(error, errorContext);
    
    // 触发错误事件
    this.eventBus.emit('app:error', {
      appId,
      phase,
      error,
      context: errorContext
    });
    
    // 调用应用的错误处理钩子
    if (this.hooks.error) {
      try {
        this.safeExecuteHook('error', () => this.hooks.error!(error, errorContext), 5000); // 错误钩子超时5秒
      } catch (hookError) {
        this.errorHandler.handle(hookError as Error, {
          ...errorContext,
          additionalInfo: {
            ...additionalInfo,
            errorInErrorHook: true
          }
        });
      }
    }
  }
  
  /**
   * 获取当前容器
   */
  getCurrentContainer(): HTMLElement | null {
    return this.currentContainer;
  }
  
  /**
   * 获取当前属性
   */
  getCurrentProps(): Record<string, any> {
    return { ...this.currentProps };
  }
  
  /**
   * 检查是否已注册特定钩子
   * @param hookName 钩子名称
   */
  hasHook(hookName: keyof AppLifecycleHooks): boolean {
    return typeof this.hooks[hookName] === 'function';
  }
  
  /**
   * 重置生命周期状态
   */
  reset(): void {
    const appId = this.getAppId();
    
    // 清理所有定时器
    Object.values(this.hookTimeouts).forEach(timeoutId => clearTimeout(timeoutId));
    this.hookTimeouts = {};
    
    // 重置核心状态
    this.hooks = {};
    this.appExports = {};
    this.currentContainer = null;
    this.currentProps = {};
    
    console.log(`App lifecycle for ${appId} has been reset`);
  }

  /**
   * 获取当前已注册的钩子信息
   */
  getHooksInfo(): Record<string, boolean> {
    return {
      bootstrap: !!this.hooks.bootstrap,
      mount: !!this.hooks.mount,
      update: !!this.hooks.update,
      unmount: !!this.hooks.unmount,
      destroy: !!this.hooks.destroy
    };
  }
}