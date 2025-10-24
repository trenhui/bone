import { RouteRule, MicroAppConfig } from './types';
import { ApplicationRegistry } from './application-registry';
import { getEventBus } from './shared/event-bus';

/**
 * 路由管理器
 * 负责监听URL变化，根据路由规则激活和停用微应用
 */
export class RouterManager {
  private eventBus = getEventBus();
  private applicationRegistry: ApplicationRegistry;
  private activeApps: Set<string> = new Set();
  private isStarted: boolean = false;
  private currentPath: string = '';
  private currentParams: Record<string, string> = {};

  /**
   * 构造函数
   * @param registry 应用注册表实例
   */
  constructor(registry: ApplicationRegistry) {
    this.applicationRegistry = registry;
  }

  /**
   * 启动路由管理器
   */
  start(): void {
    if (this.isStarted) {
      return;
    }

    this.isStarted = true;
    try {
      this.currentPath = window.location.pathname;
      
      // 监听路由变化事件
      window.addEventListener('popstate', this.handleRouteChange.bind(this));
      window.addEventListener('hashchange', this.handleRouteChange.bind(this));
    } catch (error) {
      console.warn('Failed to initialize router event listeners:', error);
    }
    
    // 初始路由匹配
    this.matchRoutes();
    
    console.log('Router manager started');
  }

  /**
   * 停止路由管理器
   */
  stop(): void {
    if (!this.isStarted) {
      return;
    }

    this.isStarted = false;
    window.removeEventListener('popstate', this.handleRouteChange.bind(this));
    window.removeEventListener('hashchange', this.handleRouteChange.bind(this));
    
    console.log('Router manager stopped');
  }

  /**
   * 处理路由变化
   */
  private handleRouteChange(): void {
    try {
      const newPath = window.location.pathname;
      
      if (newPath !== this.currentPath) {
        this.currentPath = newPath;
        this.matchRoutes();
      }
    } catch (error) {
      console.error('Error handling route change:', error);
    }
  }

  /**
   * 匹配路由规则，激活和停用应用
   */
  private async matchRoutes(): Promise<void> {
    const path = window.location.pathname;
    const hash = window.location.hash;
    const fullPath = path + hash;
    
    // 获取所有已注册的应用
    const apps = this.applicationRegistry.getAllApps();
    
    // 收集需要激活和停用的应用
    const toActivate: string[] = [];
    const toDeactivate: string[] = [];
    
    apps.forEach(app => {
      const shouldActivate = this.matchRoute(app.config.activeRule, fullPath, path, hash);
      const isCurrentlyActive = this.activeApps.has(app.config.id);
      
      if (shouldActivate && !isCurrentlyActive) {
        toActivate.push(app.config.id);
      } else if (!shouldActivate && isCurrentlyActive) {
        toDeactivate.push(app.config.id);
      }
    });
    
    // 先停用需要停用的应用
    for (const appId of toDeactivate) {
      await this.deactivateApp(appId);
    }
    
    // 然后激活需要激活的应用
    for (const appId of toActivate) {
      await this.activateApp(appId);
    }
  }

  /**
   * 匹配单个路由规则
   * @param rule 路由规则
   * @param fullPath 完整路径
   * @param pathname 路径部分
   * @param hash hash部分
   */
  private matchRoute(rule: RouteRule, fullPath: string, pathname: string, hash: string): boolean {
    try {
      // 如果是字符串，当作路径前缀匹配
      if (typeof rule === 'string') {
        return pathname.startsWith(rule);
      }
      
      // 如果是正则表达式，直接匹配
      if (rule instanceof RegExp) {
        return rule.test(fullPath);
      }
      
      // 如果是函数，调用函数判断（调整为只接受一个参数）
    if (typeof rule === 'function') {
      return rule(fullPath);
    }
      
      // 如果是对象，支持多种匹配模式
      if (typeof rule === 'object' && rule !== null) {
        const ruleObj = rule as {
          path?: string | string[];
          regexp?: RegExp;
          hash?: string | string[];
          query?: Record<string, string | string[]>;
          fn?: (path: string, pathname: string, hash: string) => boolean;
        };
        
        // 路径匹配
        if (ruleObj.path) {
          const paths = Array.isArray(ruleObj.path) ? ruleObj.path : [ruleObj.path];
          const pathMatched = paths.some(p => pathname.startsWith(p));
          if (!pathMatched) return false;
        }
        
        // 正则匹配
        if (ruleObj.regexp && !ruleObj.regexp.test(fullPath)) {
          return false;
        }
        
        // Hash匹配
        if (ruleObj.hash) {
          const hashes = Array.isArray(ruleObj.hash) ? ruleObj.hash : [ruleObj.hash];
          const hashMatched = hashes.some(h => hash === h || hash.startsWith(h + '?'));
          if (!hashMatched) return false;
        }
        
        // 查询参数匹配
        if (ruleObj.query) {
          const searchParams = new URLSearchParams(window.location.search);
          
          for (const [key, value] of Object.entries(ruleObj.query)) {
            const paramValue = searchParams.get(key);
            
            if (Array.isArray(value)) {
              if (!value.includes(paramValue || '')) {
                return false;
              }
            } else if (paramValue !== value) {
              return false;
            }
          }
        }
        
        // 自定义函数匹配
        if (ruleObj.fn && !ruleObj.fn(fullPath, pathname, hash)) {
          return false;
        }
        
        return true;
      }
      
      return false;
    } catch (error) {
      console.error('Error matching route:', error);
      return false;
    }
  }

  /**
   * 激活应用
   * @param appId 应用ID
   */
  private async activateApp(appId: string): Promise<void> {
    try {
      console.log(`Activating app: ${appId}`);
      
      // 触发应用激活前事件
      this.eventBus.emit('app:will-activate', { appId });
      
      // 激活应用
      await this.applicationRegistry.activateApp(appId);
      
      // 添加到激活应用集合
      this.activeApps.add(appId);
      
      // 触发应用激活后事件
      this.eventBus.emit('app:activated', { appId });
      
      console.log(`App ${appId} activated successfully`);
    } catch (error) {
      console.error(`Failed to activate app ${appId}:`, error);
      
      // 触发应用激活失败事件
      this.eventBus.emit('app:activate-error', { appId, error });
    }
  }

  /**
   * 停用应用
   * @param appId 应用ID
   */
  private async deactivateApp(appId: string): Promise<void> {
    try {
      console.log(`Deactivating app: ${appId}`);
      
      // 触发应用停用前事件
      this.eventBus.emit('app:will-deactivate', { appId });
      
      // 停用应用
      await this.applicationRegistry.deactivateApp(appId);
      
      // 从激活应用集合中移除
      this.activeApps.delete(appId);
      
      // 触发应用停用后事件
      this.eventBus.emit('app:deactivated', { appId });
      
      console.log(`App ${appId} deactivated successfully`);
    } catch (error) {
      console.error(`Failed to deactivate app ${appId}:`, error);
      
      // 触发应用停用失败事件
      this.eventBus.emit('app:deactivate-error', { appId, error });
    }
  }

  /**
   * 手动触发路由匹配
   */
  refresh(): void {
    this.matchRoutes();
  }

  /**
   * 获取当前激活的应用列表
   */
  getActiveApps(): string[] {
    return Array.from(this.activeApps);
  }

  /**
   * 获取当前路由信息
   */
  getCurrentRoute(): {
    path: string;
    hash: string;
    search: string;
    params: Record<string, string>;
  } {
    return {
      path: window.location.pathname,
      hash: window.location.hash,
      search: window.location.search,
      params: this.currentParams
    };
  }

  /**
   * 预加载路由对应的应用
   * @param path 路径
   */
  async preloadAppsByPath(path: string): Promise<void> {
    const apps = this.applicationRegistry.getAllApps();
    
    for (const app of apps) {
      if (this.matchRoute(app.config.activeRule, path, path, '')) {
        await this.applicationRegistry.preloadApp(app.config.id);
      }
    }
  }

  /**
   * 注册路由变化监听器
   * @param callback 回调函数
   * @returns 取消监听的函数
   */
  onRouteChange(callback: (route: ReturnType<typeof this.getCurrentRoute>) => void): () => void {
    const handler = () => {
      callback(this.getCurrentRoute());
    };
    
    window.addEventListener('popstate', handler);
    window.addEventListener('hashchange', handler);
    
    return () => {
      window.removeEventListener('popstate', handler);
      window.removeEventListener('hashchange', handler);
    };
  }
}