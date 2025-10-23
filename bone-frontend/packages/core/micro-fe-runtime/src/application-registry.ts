import { MicroAppConfig, AppStatus } from './types';
import { MicroApplication } from './micro-application';
import { RouterManager } from './router-manager';
import { getEventBus } from '@bone/core/event-bus';

export class ApplicationRegistry {
  private apps: Map<string, MicroApplication> = new Map();
  private activeApps: Set<string> = new Set();
  private eventBus = getEventBus();
  private static instance: ApplicationRegistry;
  private routerManager: RouterManager;

  /**
   * 私有构造函数，实现单例模式
   */
  private constructor() {
    // 初始化路由管理器
    this.routerManager = new RouterManager(this);
    
    // 初始化事件监听
    this.initEventListeners();
  }

  // 注册应用
  register(config: MicroAppConfig): void {
    if (this.apps.has(config.name)) {
      console.warn(`App ${config.name} already registered`);
      return;
    }

    const app = new MicroApplication(config);
    this.apps.set(config.name, app);
    console.log(`App ${config.name} registered successfully`);
  }

  // 批量注册应用
  registerApps(configs: MicroAppConfig[]): void {
    configs.forEach(config => this.register(config));
  }

  // 获取应用实例
  getApp(appName: string): MicroApplication | undefined {
    return this.apps.get(appName);
  }
  
  /**
   * 初始化事件监听
   */
  private initEventListeners(): void {
    // 监听应用注册事件
    this.eventBus.on('app:register', (data: { config: MicroAppConfig }) => {
      this.registerApp(data.config);
    });
    
    // 监听路由预加载事件
    this.eventBus.on('route:preload', (data: { path: string }) => {
      this.routerManager.preloadAppsByPath(data.path);
    });
  }

  /**
   * 启动路由管理器
   */
  startRouting(): void {
    this.routerManager.start();
  }

  /**
   * 停止路由管理器
   */
  stopRouting(): void {
    this.routerManager.stop();
  }

  /**
   * 激活应用
   * @param appId 应用ID
   */
  async activateApp(appId: string): Promise<void> {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`App ${appId} not found`);
    }

    try {
      // 确保应用已加载
      await app.load();
      
      // 挂载应用
      const container = this.getAppContainer(appId);
      await app.mount(container);
    } catch (error) {
      console.error(`Failed to activate app ${appId}:`, error);
      throw error;
    }
  }

  /**
   * 停用应用
   * @param appId 应用ID
   */
  async deactivateApp(appId: string): Promise<void> {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`App ${appId} not found`);
    }

    try {
      // 卸载应用
      await app.unmount();
    } catch (error) {
      console.error(`Failed to deactivate app ${appId}:`, error);
      throw error;
    }
  }

  /**
   * 预加载应用
   * @param appId 应用ID
   */
  async preloadApp(appId: string): Promise<void> {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`App ${appId} not found`);
    }

    try {
      // 只加载不挂载
      await app.load();
    } catch (error) {
      console.error(`Failed to preload app ${appId}:`, error);
      throw error;
    }
  }

  /**
   * 获取应用容器
   * @param appId 应用ID
   */
  private getAppContainer(appId: string): HTMLElement {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`App ${appId} not found`);
    }

    const containerSelector = app.getConfig().container;
    let container: HTMLElement;

    if (containerSelector) {
      const element = typeof containerSelector === 'string' 
        ? document.querySelector(containerSelector) 
        : containerSelector;
      
      if (element && element instanceof HTMLElement) {
        container = element;
      } else {
        throw new Error(`Container not found for app ${appId}`);
      }
    } else {
      // 如果没有指定容器，创建默认容器
      container = document.createElement('div');
      container.id = `bone-app-${appId}`;
      container.className = 'bone-app-container';
      document.body.appendChild(container);
    }

    return container;
  }

  /**
   * 获取当前激活的应用列表
   */
  getActiveApps(): string[] {
    return this.routerManager.getActiveApps();
  }

  /**
   * 手动刷新路由匹配
   */
  refreshRouting(): void {
    this.routerManager.refresh();
  }

  /**
   * 获取路由管理器实例
   */
  getRouterManager(): RouterManager {
    return this.routerManager;
  }

  // 获取所有已注册的应用
  getAllApps(): MicroApplication[] {
    return Array.from(this.apps.values());
  }

  // 根据路由激活应用
  async activateAppByRoute(path: string): Promise<void> {
    this.currentPath = path;
    
    const appsToActivate: string[] = [];
    const appsToDeactivate: string[] = [];

    // 确定需要激活和停用的应用
    this.apps.forEach((app, appName) => {
      if (app.isActive(path)) {
        if (!this.activeApps.has(appName)) {
          appsToActivate.push(appName);
        }
      } else {
        if (this.activeApps.has(appName)) {
          appsToDeactivate.push(appName);
        }
      }
    });

    // 先停用不需要的应用
    for (const appName of appsToDeactivate) {
      await this.deactivateApp(appName);
    }

    // 再激活需要的应用
    for (const appName of appsToActivate) {
      await this.activateApp(appName);
    }
  }

  // 手动激活应用
  async activateApp(appName: string, props?: Record<string, any>): Promise<void> {
    const app = this.apps.get(appName);
    if (!app) {
      throw new Error(`App ${appName} not found`);
    }

    try {
      // 如果应用未加载，先加载
      if (app.currentStatus === 'NOT_LOADED' || app.currentStatus === 'LOAD_ERROR') {
        await app.load();
      }

      // 挂载应用
      await app.mount(props);
      this.activeApps.add(appName);
      console.log(`App ${appName} activated successfully`);
      
      // 发布应用挂载事件
      this.eventBus.emit('app:mounted', { 
        appId: appName 
      });
    } catch (error) {
      console.error(`Failed to activate app ${appName}:`, error);
      // 发布应用错误事件
      this.eventBus.emit('app:error', { 
        appId: appName, 
        error: error instanceof Error ? error : new Error(String(error)),
        phase: 'activate'
      });
      throw error;
    }
  }

  // 停用应用
  async deactivateApp(appName: string): Promise<void> {
    const app = this.apps.get(appName);
    if (!app) {
      throw new Error(`App ${appName} not found`);
    }

    try {
      await app.unmount();
      this.activeApps.delete(appName);
      console.log(`App ${appName} deactivated successfully`);
      
      // 发布应用卸载事件
      this.eventBus.emit('app:unmounted', { 
        appId: appName 
      });
    } catch (error) {
      console.error(`Failed to deactivate app ${appName}:`, error);
      // 发布应用错误事件
      this.eventBus.emit('app:error', { 
        appId: appName, 
        error: error instanceof Error ? error : new Error(String(error)),
        phase: 'deactivate'
      });
      throw error;
    }
  }

  // 停用所有活跃的应用
  async deactivateActiveApps(): Promise<void> {
    const activeAppNames = Array.from(this.activeApps);
    for (const appName of activeAppNames) {
      await this.deactivateApp(appName);
    }
  }

  // 更新应用
  async updateApp(appName: string, props?: Record<string, any>): Promise<void> {
    const app = this.apps.get(appName);
    if (!app) {
      throw new Error(`App ${appName} not found`);
    }

    try {
      await app.update(props);
      console.log(`App ${appName} updated successfully`);
    } catch (error) {
      console.error(`Failed to update app ${appName}:`, error);
      throw error;
    }
  }

  // 卸载应用
  unregisterApp(appName: string): void {
    const app = this.apps.get(appName);
    if (!app) {
      console.warn(`App ${appName} not found`);
      return;
    }

    // 先停用应用
    if (this.activeApps.has(appName)) {
      this.deactivateApp(appName).catch(console.error);
    }

    // 销毁应用实例
    app.destroy();
    this.apps.delete(appName);
    this.activeApps.delete(appName);
    
    console.log(`App ${appName} unregistered successfully`);
  }

  // 其他方法保持不变

  // 获取应用状态
  getAppStatus(appName: string): AppStatus | undefined {
    const app = this.apps.get(appName);
    return app?.currentStatus;
  }

  // 更新路由状态
  updateRouteState(path: string): void {
    this.currentPath = path;
  }

  // 获取当前路由路径
  getCurrentPath(): string {
    return this.currentPath;
  }

  // 清空注册表
  clear(): void {
    // 停用并销毁所有应用
    this.apps.forEach((app, appName) => {
      app.destroy();
    });

    this.apps.clear();
    this.activeApps.clear();
    this.currentPath = '';
  }

  // 获取应用容器元素
  private getContainer(appId: string): HTMLElement {
    const app = this.apps.get(appId);
    if (app?.config.container) {
      const container = document.querySelector(app.config.container);
      if (container instanceof HTMLElement) {
        return container;
      }
    }
    
    // 默认容器
    const defaultContainer = document.createElement('div');
    defaultContainer.id = `app-container-${appId}`;
    document.body.appendChild(defaultContainer);
    return defaultContainer;
  }
}

// 创建单例实例
let registryInstance: ApplicationRegistry | null = null;

// 获取应用注册表实例
export function getApplicationRegistry(): ApplicationRegistry {
  if (!registryInstance) {
    registryInstance = new ApplicationRegistry();
  }
  return registryInstance;
}

// 导出 ApplicationRegistry 类，便于直接实例化和扩展
export { ApplicationRegistry };