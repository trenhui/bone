import { MicroApplication } from './micro-application';
import { AppLifecycleHooks, MicroAppConfig } from './types';

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
  
  // 应用配置缓存
  private appConfig?: MicroAppConfig;
  private currentContainer: HTMLElement | null = null;
  private currentProps: Record<string, any> = {};

  /**
   * 构造函数
   * @param app 微应用实例
   */
  constructor(private app: MicroApplication) {
    // 初始化时注册默认的全局hooks
    this.registerGlobalHooks();
    
    // 设置事件监听器
    this.setupEventListeners();
    
    // 缓存应用配置
    try {
      this.appConfig = this.app.config;
    } catch (error) {
      console.warn('Failed to get app config:', error);
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
      const appId = this.appConfig?.id || this.app.config.id;
      // 监听应用钩子注册事件
      this.eventBus.on(`app:${appId}:register-hooks`, (hooks: AppLifecycleHooks) => {
        this.registerHooks(hooks);
      });
    } catch (error) {
      console.warn('Failed to setup event listeners:', error);
    }
  }
  
  /**
   * 注册生命周期钩子
   * @param hooks 钩子对象
   */
  registerHooks(hooks: AppLifecycleHooks): void {
    this.hooks = {
      ...this.hooks,
      ...hooks
    };
    
    try {
      const appId = this.appConfig?.id || this.app.config.id;
      console.log(`Registered lifecycle hooks for app ${appId}:`, 
        Object.keys(hooks).join(', '));
    } catch (error) {
      console.warn('Failed to log hook registration:', error);
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
    const appId = this.app.config.id;
    
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
      this.handleError(error, 'bootstrap');
      throw error;
    }
  }

  /**
   * 挂载阶段 - 将应用渲染到容器
   * @param container 容器元素
   * @param props 传递的属性
   */
  async mount(container: HTMLElement, props?: Record<string, any>): Promise<void> {
    const appId = this.app.config.id;
    
    try {
      // 确保容器存在
      if (!container) {
        throw new Error('Container element is required for mounting');
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
      if (this.hooks.mount) {
        await this.safeExecuteHook('mount', () => this.hooks.mount!(container, props));
      } else {
        // 尝试兼容不同的导出格式
        if (this.appExports && this.appExports.default) {
          // 处理ES模块默认导出
          const defaultExport = this.appExports.default;
          if (typeof defaultExport === 'function') {
            await this.safeExecuteHook('mount', () => defaultExport(container, props));
          }
        }
      }
      
      // 调用 afterMount 钩子
      if (this.hooks.afterMount) {
        await this.safeExecuteHook('afterMount', () => this.hooks.afterMount!());
      }
      
      this.eventBus.emit('app:mount:after', { appId, container, props });
      console.log(`App ${appId} mounted successfully`);
    } catch (error) {
      this.handleError(error, 'mount');
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
    const appId = this.app.config.id;
    
    try {
      console.log(`Unmounting app: ${appId}`);
      this.eventBus.emit('app:unmount:before', { appId, container: this.currentContainer });
      
      // 调用 beforeUnmount 钩子
      if (this.hooks.beforeUnmount) {
        await this.safeExecuteHook('beforeUnmount', () => this.hooks.beforeUnmount!());
      }
      
      // 优先使用应用导出的unmount钩子
      if (this.hooks.unmount) {
        await this.safeExecuteHook('unmount', () => this.hooks.unmount!());
      }
      
      // 调用 afterUnmount 钩子
      if (this.hooks.afterUnmount) {
        await this.safeExecuteHook('afterUnmount', () => this.hooks.afterUnmount!());
      }
      
      // 清理容器内容
      if (this.currentContainer) {
        this.cleanupContainer(this.currentContainer);
      }
      
      // 重置状态
      this.currentContainer = null;
      this.currentProps = {};
      
      this.eventBus.emit('app:unmount:after', { appId });
      console.log(`App ${appId} unmounted successfully`);
    } catch (error) {
      this.handleError(error, 'unmount');
      throw error;
    }
  }

  /**
   * 销毁阶段 - 完全清理应用
   */
  async destroy(): Promise<void> {
    // 优先使用应用导出的destroy钩子
    if (this.hooks.destroy) {
      await this.safeExecuteHook('destroy', () => this.hooks.destroy!());
    }
    
    // 清理内部状态
    this.appExports = {};
    this.hooks = {};
  }

  /**
   * 安全执行生命周期钩子
   * @param phase 生命周期阶段
   * @param hook 要执行的钩子函数
   */
  private async safeExecuteHook(phase: string, hook: () => Promise<void> | void): Promise<void> {
    const startTime = performance.now();
    
    try {
      const result = hook();
      if (result instanceof Promise) {
        await result;
      }
      
      const duration = performance.now() - startTime;
      console.log(`App ${this.app.config.id} ${phase} hook executed in ${duration.toFixed(2)}ms`);
      
      // 上报钩子执行性能
      this.eventBus.emit('app:lifecycle:performance', {
        appId: this.app.config.id,
        hookName: phase,
        duration
      });
    } catch (error) {
      console.error(`Error in lifecycle hook ${phase}:`, error);
      throw error; // 重新抛出错误，让上层处理
    }
  }
  
  /**
   * 清理容器
   * @param container 容器元素
   */
  private cleanupContainer(container: HTMLElement): void {
    try {
      // 移除所有子元素
      while (container.firstChild) {
        container.removeChild(container.firstChild);
      }
    } catch (error) {
      console.warn(`Failed to cleanup container for app ${this.app.config.id}:`, error);
    }
  }
  
  /**
   * 处理错误
   * @param error 错误对象
   * @param phase 生命周期阶段
   */
  private handleError(error: Error, phase: string): void {
    const appId = this.app.config.id;
    
    console.error(`Error in app ${appId} ${phase} phase:`, error);
    
    // 触发错误事件
    this.eventBus.emit('app:error', {
      appId,
      phase,
      error
    });
    
    // 调用应用的错误处理钩子
    if (this.hooks.error) {
      try {
        this.hooks.error(error);
      } catch (hookError) {
        console.error(`Error in error hook for app ${appId}:`, hookError as Error);
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
    this.hooks = {};
    this.appExports = {};
    this.currentContainer = null;
    this.currentProps = {};
    console.log(`App lifecycle for ${this.app.config.id} has been reset`);
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