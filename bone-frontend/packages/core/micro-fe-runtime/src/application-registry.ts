import { MicroApplication, MicroAppConfig } from './micro-application';
import { AppStatus } from './types';

export class ApplicationRegistry {
  private apps: Map<string, MicroApplication> = new Map();
  private activeApps: Set<string> = new Set();
  private currentPath: string = '';

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
    } catch (error) {
      console.error(`Failed to activate app ${appName}:`, error);
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
    } catch (error) {
      console.error(`Failed to deactivate app ${appName}:`, error);
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

  // 获取活跃的应用
  getActiveApps(): string[] {
    return Array.from(this.activeApps);
  }

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
}

// 创建单例实例
let registryInstance: ApplicationRegistry | null = null;

export function getApplicationRegistry(): ApplicationRegistry {
  if (!registryInstance) {
    registryInstance = new ApplicationRegistry();
  }
  return registryInstance;
}