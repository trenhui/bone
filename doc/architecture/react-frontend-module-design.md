# React Frontend Micro-Architecture Design

## 1. Overview

本文档详细描述了Bone企业级开发平台的React前端微应用架构设计方案。该方案基于业界最佳实践，旨在构建一个高度可扩展、高性能、易维护的前端应用框架，支持多团队并行协作开发，实现业务模块的独立部署和运行。通过微前端架构，我们将大型前端应用拆分为多个小型、松耦合的微应用，每个微应用可以由独立团队负责，使用适合其业务场景的技术栈。

## 2. Architecture Design

### 2.1 Micro-Frontend Architecture Overview

Bone前端采用现代混合式微前端架构，基于无界框架(wujie)实现，结合了基座模式和去中心化模式的优点，具有以下核心特性：

- **Decentralized Development**: 支持多个团队独立开发、测试和部署微应用，降低团队间协作复杂度
- **Technology Agnostic**: 微应用可以使用不同的技术栈开发，兼容React、Vue、Angular等主流框架
- **High Performance**: 通过智能预加载、资源缓存、懒加载等机制显著提升用户体验
- **Secure Isolation**: 采用多层级沙箱技术确保微应用间的安全隔离，防止全局变量污染和样式冲突
- **Unified Management**: 提供统一的应用注册、路由管理、生命周期控制和权限管理
- **Seamless Communication**: 实现主应用与微应用、微应用与微应用间的高效通信机制

### 2.2 Core Architecture Components

```
├── Main Framework (main-app)
│   ├── Micro-Frontend Orchestrator       # 微前端协调器
│   ├── Dynamic Router                    # 动态路由系统
│   ├── Global State Management           # 全局状态管理
│   ├── Shared Component Library          # 共享组件库
│   ├── Security & Authentication         # 安全与认证
│   ├── Resource Manager                  # 资源管理器
│   ├── Performance Monitor               # 性能监控
│   └── Application Lifecycle Manager     # 应用生命周期管理
├── Micro Applications
│   ├── Admin Portal (micro-app-admin)    # 管理门户微应用
│   ├── Data Analytics (micro-app-analytics) # 数据分析微应用
│   ├── Workflow Engine (micro-app-workflow)  # 工作流引擎微应用
│   └── User Center (micro-app-user)      # 用户中心微应用
└── Shared Services
    ├── Message Bus                       # 消息总线
    ├── API Gateway                       # API网关
    └── Utils & Helpers                   # 工具函数库
```

## 3. Main Framework Design

### 3.1 Main Framework Architecture

主框架(Main Framework)作为整个微前端系统的基座，负责协调和管理所有微应用，提供统一的入口、基础设施和运行环境。主框架采用模块化设计，各模块职责清晰，便于维护和扩展。

```typescript
// 主应用入口组件 (React 18)
function App() {
  return (
    <Provider store={store}>
      <BrowserRouter>
        <MicroFrontendOrchestrator>
          <AppLayout>
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/auth/*" element={<AuthRoutes />} />
              {/* 微应用路由，由DynamicMicroAppRouter统一管理 */}
              <Route path="/*" element={<DynamicMicroAppRouter />} />
            </Routes>
          </AppLayout>
        </MicroFrontendOrchestrator>
      </BrowserRouter>
    </Provider>
  );
}
```

### 3.2 Micro-Frontend Orchestrator

微前端协调器(Micro-Frontend Orchestrator)是主框架的核心组件，负责微应用的注册、配置、加载和生命周期管理，提供统一的微应用管理接口。

```typescript
/**
 * Micro Application Configuration Interface
 * 微应用配置接口
 */
export interface MicroApplicationConfig {
  /** Application name - 应用名称 (required) */
  name: string;
  /** Application entry URL - 应用入口地址 (required) */
  entry: string;
  /** Route matching rule - 路由匹配规则 */
  activeRule: string | ((location: Location) => boolean);
  /** Container selector - 容器选择器 */
  container?: string;
  /** Sandbox configuration - 沙箱配置 */
  sandbox?: boolean | Record<string, any>;
  /** Security policy configuration - 安全策略配置 */
  securityPolicy?: Record<string, any>;
  /** Loading priority - 加载优先级 */
  priority?: number;
  /** Properties passed to the application - 传递给应用的属性 */
  props?: Record<string, any>;
  /** Whether to preload - 是否预加载 */
  preload?: boolean;
  /** Whether to keep alive - 是否保活 */
  keepAlive?: boolean;
  /** Application version - 应用版本号 */
  version?: string;
  /** Application icon - 应用图标 */
  icon?: string;
  /** Application title - 应用标题 */
  title?: string;
  /** Required permissions - 所需权限列表 */
  requiredPermissions?: string[];
  /** Error fallback component - 错误回退组件 */
  fallbackComponent?: React.ReactNode;
  /** Loading component - 加载组件 */
  loadingComponent?: React.ReactNode;
}

/**
 * Micro-Frontend Orchestrator Class
 * 微前端协调器类 - 负责微应用的注册、配置和生命周期管理
 */
export class MicroFrontendOrchestrator {
  private static instance: MicroFrontendOrchestrator;
  private appConfigs: Map<string, MicroApplicationConfig>;
  private initializedApps: Set<string>;
  private mountingApps: Map<string, Promise<void>>;
  private activeApps: Set<string>;
  private resourceCache: Map<string, any>;

  private constructor() {
    this.appConfigs = new Map();
    this.initializedApps = new Set();
    this.mountingApps = new Map();
    this.activeApps = new Set();
    this.resourceCache = new Map();
    this.initialize();
  }

  /**
   * 初始化协调器
   */
  private initialize(): void {
    // 监听路由变化，优化应用加载
    window.addEventListener('popstate', this.handleRouteChange.bind(this));
    // 初始化错误监控
    this.setupErrorMonitoring();
  }

  /**
   * 获取单例实例 - Singleton Pattern
   */
  public static getInstance(): MicroFrontendOrchestrator {
    if (!MicroFrontendOrchestrator.instance) {
      MicroFrontendOrchestrator.instance = new MicroFrontendOrchestrator();
    }
    return MicroFrontendOrchestrator.instance;
  }

  /**
   * Register a single micro application
   * 注册单个微应用
   * @param config Micro application configuration
   */
  public registerApp(config: MicroApplicationConfig): boolean {
    // 验证配置有效性
    if (!config || !config.name || !config.entry) {
      console.error('MicroFrontendOrchestrator: Invalid application configuration');
      return false;
    }

    try {
      // 验证并规范化配置
      const normalizedConfig: MicroApplicationConfig = {
        ...config,
        name: config.name.trim(),
        entry: config.entry.trim(),
        sandbox: config.sandbox ?? true,
        priority: config.priority ?? 0,
        version: config.version || '1.0.0',
        preload: config.preload ?? false,
        keepAlive: config.keepAlive ?? false
      };

      // 存储配置
      this.appConfigs.set(normalizedConfig.name, normalizedConfig);

      // 触发应用注册事件
      globalEventBus.emit(AppEvents.MICRO_APP_REGISTERED, normalizedConfig);
      console.log(`MicroFrontendOrchestrator: Application registered: ${normalizedConfig.name}`);

      return true;
    } catch (error) {
      console.error(`MicroFrontendOrchestrator: Failed to register application ${config.name}:`, error);
      return false;
    }
  }

  /**
   * Register multiple micro applications
   * 批量注册微应用
   * @param apps Array of micro application configurations
   */
  public registerApps(apps: MicroApplicationConfig[]): {
    success: number;
    failed: number;
    registeredApps: string[];
  } {
    let success = 0;
    let failed = 0;
    const registeredApps: string[] = [];

    if (!Array.isArray(apps)) {
      console.error('MicroFrontendOrchestrator: Apps parameter must be an array');
      return { success: 0, failed: 0, registeredApps: [] };
    }

    apps.forEach(app => {
      if (this.registerApp(app)) {
        success++;
        registeredApps.push(app.name);
      } else {
        failed++;
      }
    });

    // 触发批量注册完成事件
    globalEventBus.emit(AppEvents.MICRO_APPS_REGISTERED, {
      total: apps.length,
      success,
      failed,
      registeredApps
    });

    return { success, failed, registeredApps };
  }

  /**
   * Get application configuration
   * 获取应用配置
   * @param name Application name
   */
  public getAppConfig(name: string): MicroApplicationConfig | undefined {
    return this.appConfigs.get(name);
  }

  /**
   * Get all application configurations
   * 获取所有应用配置
   */
  public getAllAppConfigs(): MicroApplicationConfig[] {
    return Array.from(this.appConfigs.values());
  }

  /**
   * Initialize micro application
   * 初始化微应用
   * @param name Application name
   * @param options Initialization options
   */
  public async initializeApp(name: string, options?: {
    force?: boolean;
    timeout?: number;
  }): Promise<boolean> {
    const { force = false, timeout = 30000 } = options || {};

    // 如果已经初始化且不强制重新初始化，直接返回
    if (this.initializedApps.has(name) && !force) {
      return true;
    }

    // 如果正在初始化，返回现有的Promise
    if (this.mountingApps.has(name)) {
      try {
        // 使用Promise.race添加超时处理
        await Promise.race([
          this.mountingApps.get(name)!,
          new Promise<void>((_, reject) => 
            setTimeout(() => reject(new Error('Initialization timeout')), timeout)
          )
        ]);
        return this.initializedApps.has(name);
      } catch (error) {
        console.error(`MicroFrontendOrchestrator: Application initialization timed out: ${name}`);
        return false;
      }
    }

    // 获取配置
    const config = this.getAppConfig(name);
    if (!config) {
      console.error(`MicroFrontendOrchestrator: Application config not found: ${name}`);
      return false;
    }

    // 创建初始化Promise
    const initPromise = new Promise<void>(async (resolve, reject) => {
      const startTime = performance.now();
      try {
        // 触发初始化开始事件
        globalEventBus.emit(AppEvents.MICRO_APP_LOAD_START, { 
          name, 
          timestamp: Date.now() 
        });

        // 加载应用资源
        await this.loadAppResources(config);

        // 标记为已初始化
        this.initializedApps.add(name);

        // 计算初始化时间并记录性能指标
        const loadTime = performance.now() - startTime;
        performanceMonitor.recordAppLoadTime(name, loadTime);

        // 触发初始化完成事件
        globalEventBus.emit(AppEvents.MICRO_APP_LOAD_COMPLETE, { 
          name, 
          loadTime,
          timestamp: Date.now() 
        });

        resolve();
      } catch (error) {
        console.error(`MicroFrontendOrchestrator: Application initialization failed: ${name}`, error);
        globalEventBus.emit(AppEvents.MICRO_APP_LOAD_ERROR, { 
          name, 
          error: (error as Error).message,
          timestamp: Date.now() 
        });
        reject(error);
      } finally {
        // 无论成功失败都移除mounting状态
        this.mountingApps.delete(name);
      }
    });

    // 存储mounting Promise
    this.mountingApps.set(name, initPromise);

    try {
      await initPromise;
      return true;
    } catch (error) {
      return false;
    }
  }

  /**
   * Load application resources
   * 加载应用资源
   * @param config Application configuration
   */
  private async loadAppResources(config: MicroApplicationConfig): Promise<void> {
    console.log(`MicroFrontendOrchestrator: Loading resources for: ${config.name}`);
    
    // 检查缓存中是否已有资源
    const cacheKey = `${config.name}_${config.version}`;
    if (this.resourceCache.has(cacheKey)) {
      console.log(`MicroFrontendOrchestrator: Using cached resources for: ${config.name}`);
      return;
    }
    
    // 如果配置了预加载，则使用无界框架的预加载API
    if (config.preload) {
      try {
        // 预加载应用
        await preloadApp(config.name, config.entry);
        // 缓存资源
        this.resourceCache.set(cacheKey, { timestamp: Date.now() });
      } catch (error) {
        console.warn(`MicroFrontendOrchestrator: Preload failed for: ${config.name}`, error);
        // 预加载失败不影响应用的正常加载，只记录警告
      }
    }
  }

  /**
   * Activate micro application
   * 激活微应用
   * @param name Application name
   */
  public activateApp(name: string): void {
    if (!this.activeApps.has(name)) {
      this.activeApps.add(name);
      globalEventBus.emit(AppEvents.MICRO_APP_ACTIVATED, { name });
      console.log(`MicroFrontendOrchestrator: Application activated: ${name}`);
    }
  }

  /**
   * Deactivate micro application
   * 停用微应用
   * @param name Application name
   */
  public deactivateApp(name: string): void {
    if (this.activeApps.has(name)) {
      this.activeApps.delete(name);
      globalEventBus.emit(AppEvents.MICRO_APP_DEACTIVATED, { name });
      console.log(`MicroFrontendOrchestrator: Application deactivated: ${name}`);
    }
  }

  /**
   * Unload micro application
   * 卸载微应用
   * @param name Application name
   */
  public unloadApp(name: string): boolean {
    // 停用应用
    this.deactivateApp(name);
    
    // 移除已初始化状态
    const wasInitialized = this.initializedApps.delete(name);
    
    // 清理缓存
    const config = this.getAppConfig(name);
    if (config) {
      const cacheKey = `${config.name}_${config.version}`;
      this.resourceCache.delete(cacheKey);
    }
    
    if (wasInitialized) {
      // 触发卸载事件
      globalEventBus.emit(AppEvents.MICRO_APP_UNMOUNT, { name, timestamp: Date.now() });
      console.log(`MicroFrontendOrchestrator: Application unloaded: ${name}`);
    }
    
    return wasInitialized;
  }

  /**
   * Clean up all micro applications
   * 清理所有微应用
   */
  public clear(): void {
    // 卸载所有已初始化的应用
    Array.from(this.initializedApps).forEach(name => this.unloadApp(name));
    
    // 清空配置和状态
    this.appConfigs.clear();
    this.mountingApps.clear();
    this.resourceCache.clear();
    
    console.log('MicroFrontendOrchestrator: All applications cleared');
  }

  /**
   * Check if application is initialized
   * 检查应用是否已初始化
   * @param name Application name
   */
  public isAppInitialized(name: string): boolean {
    return this.initializedApps.has(name);
  }

  /**
   * Check if application is active
   * 检查应用是否处于活动状态
   * @param name Application name
   */
  public isAppActive(name: string): boolean {
    return this.activeApps.has(name);
  }

  /**
   * Handle route change
   * 处理路由变化
   */
  private handleRouteChange(): void {
    // 这里可以添加路由变化时的优化逻辑
    // 例如预加载可能的下一个微应用
    console.log('MicroFrontendOrchestrator: Route changed, optimizing application loading');
  }

  /**
   * Set up error monitoring
   * 设置错误监控
   */
  private setupErrorMonitoring(): void {
    // 监听未捕获的错误
    window.addEventListener('error', (event) => {
      console.error('MicroFrontendOrchestrator: Uncaught error:', event.error);
      // 这里可以添加错误上报逻辑
    });
  }
}

// Export singleton instance
export const microFrontendOrchestrator = MicroFrontendOrchestrator.getInstance();
```

### 3.3 应用初始化流程

应用初始化器负责微应用的动态注册和初始化流程管理。

```typescript
/**
 * 应用初始化器类
 * 负责微应用的动态注册和初始化流程
 */
export class AppInitializer {
  private microAppManager: MicroAppManager;
  private isInitialized: boolean = false;
  private initPromise: Promise<void> | null = null;

  constructor() {
    this.microAppManager = MicroAppManager.getInstance();
  }

  /**
   * 初始化应用
   * @param options 初始化选项
   */
  public async initialize(options?: {
    dynamicLoad?: boolean;
    defaultApps?: MicroAppConfig[];
    preloadApps?: string[];
  }): Promise<void> {
    // 防止重复初始化
    if (this.isInitialized) {
      return;
    }

    // 如果正在初始化，返回现有的Promise
    if (this.initPromise) {
      return this.initPromise;
    }

    this.initPromise = this.doInitialize(options || {});
    return this.initPromise;
  }

  /**
   * 执行初始化逻辑
   */
  private async doInitialize(options: {
    dynamicLoad?: boolean;
    defaultApps?: MicroAppConfig[];
    preloadApps?: string[];
  }): Promise<void> {
    try {
      console.log('AppInitializer: 开始初始化应用...');

      // 注册默认应用
      if (options.defaultApps && options.defaultApps.length > 0) {
        const { success, failed } = this.microAppManager.registerApps(options.defaultApps);
        console.log(`AppInitializer: 注册默认应用 ${success} 个，失败 ${failed} 个`);
      }

      // 动态加载应用配置
      if (options.dynamicLoad !== false) {
        await this.loadDynamicApps();
      }

      // 预加载指定应用
      if (options.preloadApps && options.preloadApps.length > 0) {
        await this.preloadApps(options.preloadApps);
      }

      this.isInitialized = true;
      console.log('AppInitializer: 应用初始化完成');
    } catch (error) {
      console.error('AppInitializer: 应用初始化失败', error);
      throw error;
    } finally {
      this.initPromise = null;
    }
  }

  /**
   * 动态加载应用配置
   */
  private async loadDynamicApps(): Promise<void> {
    try {
      // 从API获取动态应用配置
      const response = await fetch('/api/micro-apps/config');
      if (!response.ok) {
        throw new Error(`获取应用配置失败: ${response.status}`);
      }

      const apps: MicroAppConfig[] = await response.json();
      if (apps && apps.length > 0) {
        const { success, failed } = this.microAppManager.registerApps(apps);
        console.log(`AppInitializer: 动态注册应用 ${success} 个，失败 ${failed} 个`);
      }
    } catch (error) {
      console.error('AppInitializer: 动态加载应用配置失败', error);
      // 动态加载失败不应阻止应用启动
    }
  }

  /**
   * 预加载应用
   * @param appNames 应用名称数组
   */
  private async preloadApps(appNames: string[]): Promise<void> {
    const promises = appNames.map(async (name) => {
      try {
        await this.microAppManager.initializeApp(name);
        console.log(`AppInitializer: 预加载应用成功: ${name}`);
      } catch (error) {
        console.warn(`AppInitializer: 预加载应用失败: ${name}`, error);
        // 预加载失败不影响整体流程
      }
    });

    await Promise.all(promises);
  }

  /**
   * 检查是否已初始化
   */
  public isReady(): boolean {
    return this.isInitialized;
  }
}

// 导出应用初始化器实例
export const appInitializer = new AppInitializer();
```

## 4. Micro Application Design

### 4.1 Micro Application Architecture

微应用(Micro Application)是独立的业务模块，具有自己的路由、状态管理和业务逻辑，遵循独立开发、独立部署、独立运行的原则。

#### 4.1.1 Micro Application Directory Structure

```
micro-app/
├── public/
├── src/
│   ├── assets/           # Static assets - 静态资源
│   ├── components/       # React components - 组件
│   ├── pages/            # Page components - 页面
│   ├── services/         # API services - API服务
│   ├── store/            # State management - 状态管理
│   ├── utils/            # Utility functions - 工具函数
│   ├── hooks/            # Custom hooks - 自定义Hooks
│   ├── types/            # TypeScript types - 类型定义
│   ├── App.tsx           # Root application component - 应用组件
│   ├── bootstrap.tsx     # Micro application bootstrap - 微应用启动入口
│   └── index.ts          # Module exports - 导出模块
├── package.json          # Dependencies and scripts - 依赖和脚本配置
├── tsconfig.json         # TypeScript configuration - TypeScript配置
├── webpack.config.js     # Webpack configuration - Webpack配置
└── README.md             # Project documentation - 项目文档
```

#### 4.1.2 Micro Application Bootstrap File

```typescript
// bootstrap.tsx - Micro application bootstrap file
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { configureStore } from '@reduxjs/toolkit';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import rootReducer from './store/reducers';
import { registerMicroAppLifecycle } from '@bone/micro-frontend-sdk';
import { microAppMessenger } from '@bone/micro-frontend';

// 创建Redux store with Redux Toolkit
const store = configureStore({
  reducer: rootReducer,
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: false,
    }),
});

// 渲染应用
function render(props: Record<string, any>) {
  const { container } = props || {};
  const rootElement = container ? container.querySelector('#root') : document.querySelector('#root');
  
  // 使用React 18的createRoot API
  const root = ReactDOM.createRoot(rootElement!);
  
  root.render(
    <React.StrictMode>
      <Provider store={store}>
        <BrowserRouter 
          basename={window.__POWERED_BY_WUJIE__ ? `/micro-app-name` : '/'}>
          <App {...props} />
        </BrowserRouter>
      </Provider>
    </React.StrictMode>
  );
}

// 独立运行时直接渲染 (开发模式支持)
if (!window.__POWERED_BY_WUJIE__) {
  console.log('Running as standalone application');
  render({});
}

// 导出微应用生命周期钩子 (符合微前端规范)
export async function bootstrap() {
  console.log('Micro Application: Bootstrapping...');
  // 初始化配置和资源
  registerMicroAppLifecycle('bootstrap');
  return Promise.resolve();
}

export async function mount(props: Record<string, any>) {
  console.log('Micro Application: Mounting...', props);
  
  // 注册消息监听器
  if (props?.globalEventBus) {
    // 监听全局事件
    props.globalEventBus.on('GLOBAL_THEME_CHANGED', handleThemeChange);
    props.globalEventBus.on('USER_LOGIN_STATE_CHANGED', handleLoginStateChange);
  }
  
  // 渲染应用
  render(props);
  
  // 注册生命周期事件
  registerMicroAppLifecycle('mount');
}

export async function unmount() {
  console.log('Micro Application: Unmounting...');
  
  // 清理资源和事件监听器
  microAppMessenger.off('*'); // 移除所有消息监听器
  
  // 卸载React应用
  ReactDOM.unmountComponentAtNode(document.querySelector('#root')!);
  
  // 注册生命周期事件
  registerMicroAppLifecycle('unmount');
}

// 提供额外的生命周期钩子
export async function update(props: Record<string, any>) {
  console.log('Micro Application: Updating...', props);
  // 更新应用状态和视图
  render(props);
  
  // 注册生命周期事件
  registerMicroAppLifecycle('update');
}

// 生命周期辅助函数
export function registerMicroAppLifecycle(stage: string) {
  console.log(`[LIFECYCLE] Micro Application ${stage} at ${new Date().toISOString()}`);
  // 可选：上报生命周期事件到监控系统
}

// 事件处理函数
function handleThemeChange(theme: string) {
  document.documentElement.setAttribute('data-theme', theme);
}

function handleLoginStateChange(userInfo: any) {
  // 处理登录状态变更
  store.dispatch({ type: 'USER_SET', payload: userInfo });
}
```

### 4.2 Micro Application Routing Management

微前端路由系统(Micro-Frontend Routing System)负责匹配和加载对应的微应用，支持智能预加载、错误处理和性能优化。

```typescript
/**
 * Micro Application Route Configuration Interface
 * 微应用路由配置接口
 */
export interface MicroAppRoute {
  /** Application name - 应用名称 */
  name: string;
  /** Route matching rule - 路由匹配规则 */
  activeRule: string | ((location: Location) => boolean);
  /** Application entry URL - 应用入口地址 */
  entry?: string;
  /** Container selector - 容器选择器 */
  container?: string;
  /** Sandbox configuration - 沙箱配置 */
  sandbox?: boolean | Record<string, any>;
  /** Security policy configuration - 安全策略配置 */
  securityPolicy?: Record<string, any>;
  /** Loading priority - 加载优先级 */
  priority?: number;
  /** Properties passed to the application - 传递给应用的属性 */
  props?: Record<string, any>;
  /** Loading component - 加载组件 */
  loader?: React.ReactNode;
  /** Error component - 错误组件 */
  error?: React.ReactNode;
  /** Route path - 路由路径 */
  path?: string;
  /** Exact match - 是否精确匹配 */
  exact?: boolean;
  /** Case sensitive - 是否区分大小写 */
  sensitive?: boolean;
  /** Strict matching - 是否严格匹配 */
  strict?: boolean;
  /** Preload - 是否预加载 */
  preload?: boolean;
  /** Keep alive - 是否保活 */
  alive?: boolean;
  /** Sync loading - 是否同步加载 */
  sync?: boolean;
  /** Singleton mode - 是否单例模式 */
  singleton?: boolean;
  /** Degradation config - 降级配置 */
  degrade?: boolean | (() => boolean);
  /** Lazy loading config - 懒加载配置 */
  lazyLoadConfig?: {
    /** Intersection Observer threshold */
    threshold?: number;
    /** Preload timeout */
    timeout?: number;
  };
  /** Performance metrics tracking - 性能指标跟踪 */
  trackPerformance?: boolean;
  /** Version control - 版本控制 */
  version?: string;

/**
 * Micro Frontend Router Component
 * 微前端路由组件 - 负责匹配和加载对应的微应用
 * 提供智能路由匹配、预加载优化和错误边界等功能
 */
export const MicroAppRouter: React.FC<{
  routes: MicroAppRoute[];
  defaultFallback?: React.ReactNode;
  loadingComponent?: React.ReactNode;
  errorComponent?: (error: Error) => React.ReactNode;
  useEnhancedContainer?: boolean;
  preloadDistance?: number;
  maxPreloadApps?: number;
}> = ({ 
  routes, 
  defaultFallback = <Empty description="暂无微应用配置" />,
  loadingComponent,
  errorComponent,
  useEnhancedContainer = true,
  preloadDistance = 3,
  maxPreloadApps = 3
}) => {
  const location = useLocation();
  const navigate = useNavigate();
  const params = useParams();
  const [matchedRoute, setMatchedRoute] = useState<MicroAppRoute | null>(null);
  const [preloadingApps, setPreloadingApps] = useState<Set<string>>(new Set());
  
  // 查找匹配的路由
  const currentMatchedRoute = useMemo(() => {
    return routes.find(route => {
      const match = matchPath(location.pathname, {
        path: route.path,
        exact: route.exact,
        strict: route.strict,
        sensitive: route.sensitive
      });
      return !!match;
    }) || null;
  }, [routes, location.pathname]);

  // 执行预加载的函数
  const doPreload = useCallback(async (appName: string) => {
    if (preloadingApps.has(appName)) {
      return;
    }

    try {
      setPreloadingApps(prev => new Set(prev).add(appName));
      
      // 获取配置并预加载
      const config = microAppManager.getAppConfig(appName);
      if (config && config.entry) {
        console.log(`预加载微应用: ${appName}`, config.entry);
        await preloadApp(appName, config.entry);
      }
    } catch (error) {
      console.error(`微应用 ${appName} 预加载失败:`, error);
    } finally {
      setPreloadingApps(prev => {
        const newSet = new Set(prev);
        newSet.delete(appName);
        return newSet;
      });
    }
  }, [preloadingApps]);

  // 智能预加载逻辑
  useEffect(() => {
    if (!currentMatchedRoute) return;
    
    // 基于优先级和配置的预加载策略
    const sortedAppsToPreload = routes
      .filter(route => {
        if (route.name === currentMatchedRoute.name) return false;
        if (route.preload === false) return false;
        if (preloadingApps.has(route.name)) return false;
        return true;
      })
      .sort((a, b) => (a.priority || 999) - (b.priority || 999))
      .map(route => route.name)
      .filter((appName, index, self) => self.indexOf(appName) === index)
      .slice(0, maxPreloadApps);
    
    // 执行预加载
    sortedAppsToPreload.forEach(appName => {
      doPreload(appName);
    });
  }, [currentMatchedRoute, routes, preloadingApps, maxPreloadApps, doPreload]);

  // 更新活动路由和状态
  useEffect(() => {
    setMatchedRoute(currentMatchedRoute);
    
    if (currentMatchedRoute) {
      // 通知路由变化
      globalEventBus.emit(AppEvents.MICRO_APP_ROUTE_CHANGED, {
        appName: currentMatchedRoute.name,
        path: location.pathname,
        timestamp: Date.now()
      });
    }
  }, [currentMatchedRoute, location.pathname]);

  // 处理微应用错误
  const handleAppError = useCallback((error: Error) => {
    console.error('微应用路由加载错误:', error);
    
    // 上报错误
    globalEventBus.emit(AppEvents.MICRO_APP_ROUTE_ERROR, {
      path: location.pathname,
      error: error.message,
      timestamp: Date.now()
    });
    
    if (errorComponent) {
      return errorComponent(error);
    }
    
    return (
      <Alert
        message="微应用加载失败"
        description={error.message}
        type="error"
        showIcon
        action={
          <Button type="primary" onClick={() => window.location.reload()}>
            刷新重试
          </Button>
        }
      />
    );
  }, [location.pathname, errorComponent]);

  // 渲染匹配的微应用
  if (matchedRoute) {
    const ContainerComponent = useEnhancedContainer ? 
      EnhancedMicroAppContainer : MicroAppContainer;
      
    return (
      <ErrorBoundary fallback={handleAppError}>
        <div className="micro-app-router-container" style={{ width: '100%', height: '100%' }}>
          <ContainerComponent
            appName={matchedRoute.name}
            customSandboxConfig={matchedRoute.sandbox}
            customSecurityPolicy={matchedRoute.securityPolicy}
            props={{
              ...matchedRoute.props,
              route: { location, navigate, params }
            }}
          />
        </div>
      </ErrorBoundary>
    );
  }
  
  // 渲染默认回退组件
  return <div>{defaultFallback}</div>;
};
```

### 4.3 Enhanced Micro Application Container

增强版微应用容器(Enhanced Micro Application Container)提供了额外的功能，如性能监控、资源管理、错误处理和安全隔离等高级特性，提升微应用的加载性能和运行稳定性。

```typescript
/**
 * Enhanced Micro Application Container Component
 * 增强版微应用容器组件
 * 提供性能监控、资源管理、错误处理等增强功能
 * 实现了完整的微应用生命周期管理和性能优化
 */
export const EnhancedMicroAppContainer: React.FC<{
  appName: string;
  customSandboxConfig?: boolean | Record<string, any>;
  customSecurityPolicy?: Record<string, any>;
  props?: any;
}> = ({ appName, customSandboxConfig, customSecurityPolicy, props }) => {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [performanceData, setPerformanceData] = useState<any>({});
  
  // 获取应用配置
  const getAppConfig = useCallback(async () => {
    try {
      // 尝试从管理器获取配置
      let config = microAppManager.getAppConfig(appName);
      
      // 如果没有配置，动态获取
      if (!config) {
        config = await fetch(`/api/micro-apps/${appName}/config`)
          .then(res => res.json())
          .catch(err => {
            console.error(`获取微应用配置失败: ${appName}`, err);
            return null;
          });
        
        // 注册获取到的配置
        if (config) {
          microAppManager.registerApp(config);
        }
      }
      
      return config;
    } catch (error) {
      console.error(`获取微应用配置出错: ${appName}`, error);
      return null;
    }
  }, [appName]);
  
  // 加载应用前的准备工作
  useEffect(() => {
    let isMounted = true;
    const loadStartTime = performance.now();
    
    const prepareAndLoad = async () => {
      try {
        setLoading(true);
        setError(null);
        
        // 初始化应用
        await microAppManager.initializeApp(appName);
        
        // 获取配置
        const config = await getAppConfig();
        if (!config && isMounted) {
          throw new Error(`未找到微应用配置: ${appName}`);
        }
        
        if (isMounted) {
          // 记录准备时间
          setPerformanceData(prev => ({
            ...prev,
            prepareTime: performance.now() - loadStartTime
          }));
          
          // 触发加载开始事件
          globalEventBus.emit(AppEvents.MICRO_APP_LOAD_START, { appName });
        }
      } catch (err) {
        if (isMounted) {
          const error = err instanceof Error ? err : new Error(String(err));
          setError(error);
          
          // 触发加载错误事件
          globalEventBus.emit(AppEvents.MICRO_APP_LOAD_ERROR, { 
            appName, 
            error: error.message 
          });
        }
      }
    };
    
    prepareAndLoad();
    
    return () => {
      isMounted = false;
    };
  }, [appName, getAppConfig]);
  
  // 处理加载完成
  const handleLoad = useCallback(() => {
    const loadTime = performance.now() - (performanceData.prepareTime || 0);
    
    setPerformanceData(prev => ({
      ...prev,
      loadTime,
      totalTime: loadTime + (prev.prepareTime || 0)
    }));
    
    setLoading(false);
    
    // 记录性能指标
    performanceMonitor.recordAppLoadTime(appName, loadTime);
    
    // 触发加载完成事件
    globalEventBus.emit(AppEvents.MICRO_APP_LOAD_COMPLETE, { 
      appName, 
      loadTime 
    });
  }, [appName, performanceData.prepareTime]);
  
  // 处理错误
  const handleError = useCallback((err: Error) => {
    console.error(`微应用 ${appName} 加载错误:`, err);
    setError(err);
    setLoading(false);
    
    // 记录错误
    errorHandler.handleMicroAppError(appName, err);
    
    // 触发错误事件
    globalEventBus.emit(AppEvents.MICRO_APP_ERROR, { 
      appName, 
      error: err.message 
    });
  }, [appName]);
  
  // 处理应用消息
  const handleMessage = useCallback((data: any) => {
    // 处理来自微应用的消息
    console.log(`收到来自微应用 ${appName} 的消息:`, data);
    
    // 转发消息给全局事件总线
    globalEventBus.emit(AppEvents.MICRO_APP_MESSAGE, { 
      appName, 
      data 
    });
  }, [appName]);
  
  // 处理应用卸载
  const handleUnmount = useCallback(() => {
    console.log(`微应用 ${appName} 卸载`);
    
    // 触发卸载事件
    globalEventBus.emit(AppEvents.MICRO_APP_UNMOUNT, { appName });
  }, [appName]);
  
  // 构建无界框架配置
  const getWujieConfig = useCallback(() => {
    return {
      name: appName,
      url: microAppManager.getAppConfig(appName)?.entry,
      sync: false,
      sandbox: buildWujieSandboxConfig(customSandboxConfig),
      prefix: [`/${appName}`],
      props: {
        ...props,
        globalEventBus,
        microAppMessenger
      },
      deadLoopCheck: true,
      lifeCycles: {
        beforeLoad: () => {
          console.log(`准备加载微应用: ${appName}`);
        },
        beforeMount: () => {
          console.log(`准备挂载微应用: ${appName}`);
        },
        afterMount: handleLoad,
        beforeUnmount: handleUnmount,
        afterUnmount: () => {
          console.log(`微应用 ${appName} 已卸载`);
        },
        error: handleError
      },
      customGetUrl: (url: string) => {
        // 可以在这里对URL进行自定义处理
        return url;
      },
      ...(customSecurityPolicy ? { securityPolicy: customSecurityPolicy } : {})
    };
  }, [appName, customSandboxConfig, customSecurityPolicy, props, handleLoad, handleUnmount, handleError]);
  
  // 渲染加载状态
  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
        <Spin size="large" tip={`加载微应用 ${appName}...`} />
      </div>
    );
  }
  
  // 渲染错误状态
  if (error) {
    return (
      <Alert
        message="微应用加载失败"
        description={error.message}
        type="error"
        showIcon
        action={
          <Button type="primary" onClick={() => window.location.reload()}>
            刷新重试
          </Button>
        }
      />
    );
  }
  
  // 渲染微应用
  return (
    <div className="enhanced-micro-app-container">
      <WujieReact
        {...getWujieConfig()}
        onMessage={handleMessage}
      />
    </div>
  );
};
```

## 5. Micro-Frontend Communication Mechanisms

### 5.1 Event Bus System

事件总线（Event Bus）是微前端架构中的核心通信机制，用于主应用和微应用之间的消息传递和状态同步。

```typescript
/**
 * Micro-Application Event Enumeration
 * 微应用事件枚举 - 定义所有跨应用通信的标准事件类型
 */
export enum AppEvents {
  // 路由相关事件 - Route related events
  MICRO_APP_ROUTE_CHANGED = 'micro_app_route_changed', // 路由变化事件
  MICRO_APP_ROUTE_ERROR = 'micro_app_route_error', // 路由错误事件
  
  // 生命周期相关事件 - Lifecycle related events
  MICRO_APP_LOAD_START = 'micro_app_load_start', // 加载开始事件
  MICRO_APP_LOAD_COMPLETE = 'micro_app_load_complete', // 加载完成事件
  MICRO_APP_LOAD_ERROR = 'micro_app_load_error', // 加载错误事件
  MICRO_APP_MOUNT = 'micro_app_mount', // 挂载完成事件
  MICRO_APP_UNMOUNT = 'micro_app_unmount', // 卸载完成事件
  MICRO_APP_ACTIVATED = 'micro_app_activated', // 应用激活事件（获得焦点）
  MICRO_APP_DEACTIVATED = 'micro_app_deactivated', // 应用失活事件（失去焦点）
  
  // 通信相关事件 - Communication related events
  MICRO_APP_MESSAGE = 'micro_app_message', // 点对点消息事件
  MICRO_APP_GLOBAL_MESSAGE = 'micro_app_global_message', // 全局广播消息事件
  
  // 应用管理事件 - Application management events
  MICRO_APP_REGISTERED = 'micro_app_registered', // 应用注册事件
  MICRO_APP_UNREGISTERED = 'micro_app_unregistered', // 应用注销事件
  ACTIVE_APP_CHANGED = 'active_app_changed', // 活动应用变更事件
  
  // 资源管理事件 - Resource management events
  MICRO_APP_RESOURCES_LOAD = 'micro_app_resources_load', // 资源加载事件
  MICRO_APP_RESOURCES_ERROR = 'micro_app_resources_error', // 资源加载错误事件
  
  // 性能监控事件 - Performance monitoring events
  MICRO_APP_PERFORMANCE_METRIC = 'micro_app_performance_metric', // 性能指标事件
}

/**
 * Micro-Application Event Bus Class
 * 微应用事件总线类 - 负责主应用和微应用之间的通信与消息传递
 */
export class MicroAppEventBus {
  private eventMap: Map<string, Set<(data: any) => void>>; // 存储事件监听器的映射
  private onceEventMap: Map<string, Set<(data: any) => void>>; // 存储一次性事件监听器
  private isMicroApp: boolean; // 是否在微应用环境中
  private wujieBus: any; // 无界框架的通信总线
  private isInitialized: boolean = false; // 初始化状态标志
  private eventHistory: Array<{ event: string; args: any[]; timestamp: number }>; // 事件历史记录

  constructor() {
    this.eventMap = new Map();
    this.onceEventMap = new Map();
    this.eventHistory = [];
    this.initialize();
  }

  /**
   * Initialize Event Bus
   * 初始化事件总线 - 设置环境检测和通信桥接
   */
  private initialize(): void {
    if (typeof window === 'undefined') {
      return;
    }
    
    this.isMicroApp = !!window.__POWERED_BY_WUJIE__;
    this.wujieBus = window.$wujie?.bus;
    this.isInitialized = true;
    
    // 初始化与无界框架的通信桥接
    this.initializeWujieBridge();
    
    // 注册窗口卸载事件监听，清理资源
    window.addEventListener('unload', this.dispose.bind(this));
  }

  /**
   * Initialize Wujie Framework Communication Bridge
   * 初始化无界框架的通信桥接 - 建立跨应用消息传递通道
   */
  private initializeWujieBridge(): void {
    if (!this.wujieBus) return;
    
    if (this.isMicroApp) {
      // 在微应用中监听主应用的事件
      this.wujieBus.$on('micro_app_message', (data: any) => {
        try {
          const { event, args } = data;
          this.emit(event, ...args);
        } catch (error) {
          console.error('处理无界框架消息时出错:', error);
        }
      });
      
      // 监听特定应用的消息
      this.wujieBus.$on(`micro_app_message_${window.__WUJIE_APPNAME__}`, (data: any) => {
        try {
          this.emit(AppEvents.MICRO_APP_MESSAGE, data);
        } catch (error) {
          console.error('处理特定应用消息时出错:', error);
        }
      });
    } else {
      // 在主应用中监听微应用的事件
      this.wujieBus.$on('micro_app_message_from_child', (data: any) => {
        try {
          const { event, args } = data;
          this.emit(event, ...args);
        } catch (error) {
          console.error('处理微应用消息时出错:', error);
        }
      });
    }
  }

  /**
   * Register Event Listener
   * 注册事件监听器 - 监听指定事件并在事件触发时执行回调函数
   * @param event 事件名称
   * @param listener 事件监听器回调函数
   * @returns 事件总线实例，支持链式调用
   */
  on(event: string, listener: (data: any) => void): this {
    if (typeof listener !== 'function') {
      console.warn('事件监听器必须是函数类型');
      return this;
    }
    
    if (!this.eventMap.has(event)) {
      this.eventMap.set(event, new Set());
    }
    this.eventMap.get(event)?.add(listener);
    return this;
  }

  /**
   * Register One-time Event Listener
   * 注册一次性事件监听器 - 事件触发后自动移除监听器
   * @param event 事件名称
   * @param listener 事件监听器回调函数
   * @returns 事件总线实例，支持链式调用
   */
  once(event: string, listener: (data: any) => void): this {
    if (typeof listener !== 'function') {
      console.warn('事件监听器必须是函数类型');
      return this;
    }
    
    const onceWrapper = (data: any) => {
      this.off(event, onceWrapper);
      listener(data);
    };
    
    onceWrapper._originalListener = listener;
    return this.on(event, onceWrapper);
  }

  /**
   * Remove Event Listener
   * 移除事件监听器 - 取消指定事件的监听
   * @param event 事件名称
   * @param listener 可选，指定要移除的监听器。不提供时移除所有该事件的监听器
   * @returns 事件总线实例，支持链式调用
   */
  off(event: string, listener?: (data: any) => void): this {
    // 移除特定监听器
    if (listener) {
      const eventListeners = this.eventMap.get(event);
      if (eventListeners) {
        for (const registeredListener of eventListeners) {
          if (registeredListener === listener || registeredListener._originalListener === listener) {
            eventListeners.delete(registeredListener);
            break;
          }
        }
        
        // 如果没有监听器了，移除该事件
        if (eventListeners.size === 0) {
          this.eventMap.delete(event);
        }
      }
    } else {
      // 移除该事件的所有监听器
      this.eventMap.delete(event);
    }
    
    return this;
  }

  /**
   * Emit Event
   * 触发事件 - 执行所有注册的监听器并通过无界框架发送跨应用事件
   * @param event 事件名称
   * @param args 传递给监听器的参数
   * @returns 是否成功触发事件
   */
  emit(event: string, ...args: any[]): boolean {
    // 记录事件到历史
    this.eventHistory.push({ event, args, timestamp: Date.now() });
    // 限制历史记录长度
    if (this.eventHistory.length > 1000) {
      this.eventHistory.shift();
    }
    
    try {
      // 本地触发事件
      const listeners = this.eventMap.get(event);
      if (listeners) {
        const listenersCopy = new Set(listeners); // 创建副本避免在触发过程中修改导致的问题
        listenersCopy.forEach(listener => {
          try {
            listener(...args);
          } catch (error) {
            console.error(`执行事件 ${event} 监听器时出错:`, error);
          }
        });
      }
      
      // 处理一次性事件
      const onceListeners = this.onceEventMap.get(event);
      if (onceListeners) {
        onceListeners.forEach(listener => {
          try {
            listener(...args);
          } catch (error) {
            console.error(`执行一次性事件 ${event} 监听器时出错:`, error);
          }
        });
        this.onceEventMap.delete(event);
      }
      
      // 通过无界框架进行跨应用通信
      if (this.wujieBus) {
        try {
          if (this.isMicroApp) {
            // 微应用向主应用发送消息
            this.wujieBus.$emit('micro_app_message_from_child', { event, args });
          } else {
            // 主应用向所有微应用广播消息
            this.wujieBus.$emit('micro_app_message', { event, args });
          }
        } catch (error) {
          console.error('通过无界框架发送消息失败:', error);
          // 无界框架通信失败不应影响本地事件触发
        }
      }
      
      return true;
    } catch (error) {
      console.error(`触发事件 ${event} 时出错:`, error);
      return false;
    }
  }

  /**
   * Get Event Listeners Count
   * 获取事件监听器数量 - 返回指定事件的监听器数量
   * @param event 事件名称
   * @returns 监听器数量
   */
  getListenerCount(event: string): number {
    const listeners = this.eventMap.get(event);
    return listeners ? listeners.size : 0;
  }

  /**
   * Get All Event Names
   * 获取所有事件名称 - 返回所有已注册的事件名称数组
   * @returns 事件名称数组
   */
  getEventNames(): string[] {
    return Array.from(this.eventMap.keys());
  }

  /**
   * Dispose Resources
   * 清理资源 - 移除所有监听器并释放引用
   */
  dispose(): void {
    // 移除所有事件监听器
    this.eventMap.clear();
    this.onceEventMap.clear();
    this.eventHistory = [];
    
    // 移除窗口卸载事件监听
    if (typeof window !== 'undefined') {
      window.removeEventListener('unload', this.dispose.bind(this));
    }
    
    // 重置状态
    this.isInitialized = false;
    this.wujieBus = null;
  }
}

// 导出全局事件总线实例
export const globalEventBus = new MicroAppEventBus();
```

### 5.2 Message Communication Tool

消息通信工具（Message Communication Tool）提供了更高级别的通信抽象，简化了微应用间的消息传递，支持点对点通信和全局广播。

```typescript
/**
 * Micro-Application Message Interface
 * 微应用消息接口 - 定义跨应用消息的标准格式
 */
export interface MicroAppMessage {
  type: string; // 消息类型
  payload?: any; // 消息内容
  from: string; // 发送者名称
  to?: string; // 接收者名称（可选）
  timestamp: number; // 消息时间戳
  messageId?: string; // 消息唯一标识符
}

/**
 * Micro-Application Message Communication Tool
 * 微应用消息通信工具类 - 提供高级消息通信API，简化跨应用通信
 */
export class MicroAppMessenger {
  private eventBus: MicroAppEventBus;
  private messageQueue: MicroAppMessage[] = []; // 消息队列，用于处理离线消息
  private isReady: boolean = false; // 通信就绪状态
  private appName: string; // 当前应用名称
  
  /**
   * 构造函数
   * @param eventBus 事件总线实例，默认为全局事件总线
   */
  constructor(eventBus: MicroAppEventBus = globalEventBus) {
    this.eventBus = eventBus;
    this.appName = this.getSenderName();
    this.initialize();
  }
  
  /**
   * Initialize Messenger
   * 初始化消息通信工具 - 设置应用环境和状态
   */
  private initialize(): void {
    // 监听应用初始化完成事件
    this.eventBus.on(AppEvents.MICRO_APP_MOUNT, () => {
      this.isReady = true;
      // 处理队列中的消息
      this.processMessageQueue();
    });
    
    // 初始检查状态
    this.isReady = this.isMicroApp() && !!window.__WUJIE_APPNAME__;
  }
  
  /**
   * Send Message to Specific Micro-Application
   * 发送消息到指定微应用 - 支持点对点通信
   * @param appName 目标微应用名称
   * @param type 消息类型
   * @param payload 消息内容
   */
  send(appName: string, type: string, payload?: any): void {
    const message: MicroAppMessage = {
      type,
      payload,
      from: this.appName,
      to: appName,
      timestamp: Date.now(),
      messageId: this.generateMessageId()
    };
    
    // 如果通信未就绪，将消息加入队列
    if (!this.isReady) {
      this.messageQueue.push(message);
      return;
    }
    
    try {
      if (this.isMicroApp() && appName === 'main') {
        // 微应用发送消息给主应用
        this.eventBus.emit(AppEvents.MICRO_APP_MESSAGE, message);
      } else if (appName === 'main') {
        // 主应用内部通信
        this.eventBus.emit(AppEvents.MICRO_APP_MESSAGE, message);
      } else {
        // 主应用发送消息给微应用
        if (window.$wujie) {
          // 检查目标应用是否已加载
          const targetApp = window.$wujie.getInstance(appName);
          if (targetApp && targetApp.mounted) {
            window.$wujie.bus.$emit(`micro_app_message_${appName}`, message);
          } else {
            console.warn(`目标微应用 ${appName} 尚未加载，消息已加入队列`);
            this.messageQueue.push(message);
          }
        }
      }
    } catch (error) {
      console.error(`发送消息到 ${appName} 失败:`, error);
      // 发送失败时加入队列，稍后重试
      this.messageQueue.push(message);
    }
  }
  
  /**
   * Send Broadcast Message to All Applications
   * 发送全局广播消息给所有应用 - 支持一对多通信
   * @param type 消息类型
   * @param payload 消息内容
   */
  broadcast(type: string, payload?: any): void {
    const message: MicroAppMessage = {
      type,
      payload,
      from: this.appName,
      timestamp: Date.now(),
      messageId: this.generateMessageId()
    };
    
    try {
      this.eventBus.emit(AppEvents.MICRO_APP_GLOBAL_MESSAGE, message);
    } catch (error) {
      console.error('发送全局广播消息失败:', error);
    }
  }
  
  /**
   * Subscribe to Messages of Specific Type
   * 订阅指定类型的消息 - 监听并处理符合条件的消息
   * @param type 消息类型
   * @param handler 消息处理器
   * @returns 取消订阅函数
   */
  on(type: string, handler: (message: MicroAppMessage) => void): () => void {
    const eventHandler = (message: MicroAppMessage) => {
      // 过滤匹配的消息类型和目标应用
      if (message.type === type && (!message.to || message.to === this.appName)) {
        try {
          handler(message);
        } catch (error) {
          console.error(`处理消息类型 ${type} 时出错:`, error);
        }
      }
    };
    
    // 监听点对点消息和全局消息
    this.eventBus.on(AppEvents.MICRO_APP_MESSAGE, eventHandler);
    this.eventBus.on(AppEvents.MICRO_APP_GLOBAL_MESSAGE, eventHandler);
    
    // 返回取消订阅函数
    return () => {
      this.eventBus.off(AppEvents.MICRO_APP_MESSAGE, eventHandler);
      this.eventBus.off(AppEvents.MICRO_APP_GLOBAL_MESSAGE, eventHandler);
    };
  }
  
  /**
   * Send Message with Response
   * 发送消息并等待响应 - 实现请求-响应模式
   * @param appName 目标微应用名称
   * @param type 消息类型
   * @param payload 消息内容
   * @param timeout 超时时间（毫秒），默认5000
   * @returns Promise，解析为响应消息
   */
  async sendWithResponse(appName: string, type: string, payload?: any, timeout: number = 5000): Promise<MicroAppMessage> {
    return new Promise((resolve, reject) => {
      const messageId = this.generateMessageId();
      const responseType = `${type}_response`;
      
      // 设置超时
      const timeoutId = setTimeout(() => {
        unsubscribe();
        reject(new Error(`等待 ${appName} 响应超时`));
      }, timeout);
      
      // 订阅响应消息
      const unsubscribe = this.on(responseType, (response: MicroAppMessage) => {
        if (response.messageId === messageId) {
          clearTimeout(timeoutId);
          unsubscribe();
          resolve(response);
        }
      });
      
      // 发送请求消息
      this.send(appName, type, {
        ...payload,
        messageId,
        responseExpected: true
      });
    });
  }
  
  /**
   * Generate Unique Message ID
   * 生成唯一消息ID - 用于消息追踪和去重
   * @returns 唯一消息ID
   */
  private generateMessageId(): string {
    return `${this.appName}_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }
  
  /**
   * Process Message Queue
   * 处理消息队列 - 发送队列中积压的消息
   */
  private processMessageQueue(): void {
    while (this.messageQueue.length > 0 && this.isReady) {
      const message = this.messageQueue.shift();
      if (message && message.to) {
        this.send(message.to, message.type, message.payload);
      }
    }
  }
  
  /**
   * Get Sender Name
   * 获取发送者名称 - 返回当前应用的唯一标识
   * @returns 应用名称
   */
  private getSenderName(): string {
    if (this.isMicroApp() && window.__WUJIE_APPNAME__) {
      return window.__WUJIE_APPNAME__;
    }
    return 'main';
  }
  
  /**
   * Check if in Micro-Application Environment
   * 检查当前环境是否为微应用
   * @returns 是否在微应用环境中
   */
  private isMicroApp(): boolean {
    return typeof window !== 'undefined' && !!window.__POWERED_BY_WUJIE__;
  }
  
  /**
   * Clear Message Queue
   * 清空消息队列
   */
  clearQueue(): void {
    this.messageQueue = [];
  }
}

// 导出全局消息通信工具实例
export const microAppMessenger = new MicroAppMessenger();
```

## 6. Sandbox and Security Policies

### 6.1 Sandbox Configuration

沙箱配置（Sandbox Configuration）确保微应用之间的安全隔离，防止全局变量污染和样式冲突，是微前端架构中安全机制的核心。

```typescript
/**
 * Build Wujie Sandbox Configuration
 * 构建无界框架沙箱配置 - 根据环境和自定义需求生成安全沙箱配置
 * @param customConfig 自定义沙箱配置，可为布尔值或配置对象
 * @returns 沙箱配置对象
 */
export function buildWujieSandboxConfig(customConfig?: boolean | Record<string, any>): Record<string, any> {
  // 默认沙箱配置 - Default sandbox configuration
  const defaultConfig = {
    // 开启沙箱 - Enable sandbox isolation
    enable: true,
    // 开启严格样式隔离 - Enable strict style isolation
    strictStyleIsolation: true,
    // 开启实验性样式隔离 - Enable experimental style isolation
    experimentalStyleIsolation: true,
    // 启用快照沙箱 - Enable snapshot sandbox for better performance
    snapshot: true,
    // 清理副作用 - Clean up side effects when unmounting
    cleanEffect: true,
    // 模拟的全局变量 - Mocked global variables
    mockWindow: {
      // 安全模式标记 - Security mode flag
      __SECURE_MODE__: true
    },
    // 排除的全局变量 - Global variables to be excluded from isolation
    excludeVariable: [
      // 必要的全局对象 - Essential global objects
      'document',
      'window',
      'navigator',
      'location',
      // 必要的事件对象 - Essential event objects
      'Event',
      'CustomEvent',
      'Error',
      // 网络相关API - Network related APIs
      'fetch',
      'XMLHttpRequest',
      // 存储API - Storage APIs
      'localStorage',
      'sessionStorage'
    ],
    // 白名单API - Whitelisted APIs for cross-app communication
    apiWhitelist: [
      // 允许的DOM操作 - Allowed DOM operations
      'querySelector',
      'querySelectorAll',
      'getElementById',
      // 允许的事件操作 - Allowed event operations
      'addEventListener',
      'removeEventListener',
      // 允许的控制台API - Allowed console APIs
      'console'
    ],
    // 沙箱更新策略 - Sandbox update strategy
    updateStrategy: {
      // 立即应用更新 - Apply updates immediately
      immediate: true,
      // 批处理更新 - Batch updates for better performance
      batchUpdate: true
    }
  };

  // 开发环境特殊处理 - Development environment special handling
  if (process.env.NODE_ENV === 'development') {
    defaultConfig.mockWindow = {
      ...defaultConfig.mockWindow,
      // 开发环境标记 - Development environment flag
      __DEV__: true,
      // 开发工具支持 - Development tools support
      __DEV_TOOLS__: true,
      // 性能监控开关 - Performance monitoring switch
      __PERFORMANCE_MONITORING__: true
    };
    
    // 开发环境下禁用部分安全限制 - Disable some security restrictions in development
    defaultConfig.strictStyleIsolation = false;
  }

  // 生产环境安全增强 - Production environment security enhancements
  if (process.env.NODE_ENV === 'production') {
    // 启用更严格的沙箱模式 - Enable stricter sandbox mode
    defaultConfig.snapshot = true;
    // 启用严格的样式隔离 - Enable strict style isolation
    defaultConfig.strictStyleIsolation = true;
    // 清理更强力 - More aggressive cleanup
    defaultConfig.cleanEffect = true;
    // 添加安全版本标记 - Add security version flag
    defaultConfig.mockWindow.__SECURITY_VERSION__ = '1.0.0';
  }

  // 如果customConfig为false，表示不使用沙箱 - If customConfig is false, disable sandbox
  if (customConfig === false) {
    return { enable: false };
  }

  // 如果customConfig为对象，则合并配置
  if (typeof customConfig === 'object' && customConfig !== null) {
    const custom = customConfig as Record<string, any>;
    
    // 合并排除的全局变量（数组深度合并）
    const mergedExcludeVariable = Array.isArray(custom.excludeVariable) 
      ? [...new Set([...defaultConfig.excludeVariable, ...custom.excludeVariable])]
      : defaultConfig.excludeVariable;
    
    // 合并白名单API（数组深度合并）
    const mergedApiWhitelist = Array.isArray(custom.apiWhitelist)
      ? [...new Set([...defaultConfig.apiWhitelist, ...custom.apiWhitelist])]
      : defaultConfig.apiWhitelist;
    
    // 合并更新策略（对象深度合并）
    const mergedUpdateStrategy = typeof custom.updateStrategy === 'object' && custom.updateStrategy !== null
      ? { ...defaultConfig.updateStrategy, ...custom.updateStrategy }
      : defaultConfig.updateStrategy;
    
    // 合并模拟窗口（对象深度合并）
    const mergedMockWindow = typeof custom.mockWindow === 'object' && custom.mockWindow !== null
      ? { ...defaultConfig.mockWindow, ...custom.mockWindow }
      : defaultConfig.mockWindow;
    
    return {
      ...defaultConfig,
      ...custom,
      excludeVariable: mergedExcludeVariable,
      apiWhitelist: mergedApiWhitelist,
      updateStrategy: mergedUpdateStrategy,
      mockWindow: mergedMockWindow
    };
  }

  // 默认返回标准配置
  return defaultConfig;
}

/**
 * Apply Security Policy
 * 应用安全策略 - 配置和应用微应用的安全策略，包括CSP和沙箱属性
 * @param policy 自定义安全策略配置
 * @returns 合并后的安全策略配置
 */
export function applySecurityPolicy(policy?: Record<string, any>): Record<string, any> {
  // 生成唯一nonce值用于内容安全策略
  const generateNonce = (): string => {
    return Buffer.from(crypto.randomBytes(16)).toString('base64');
  };
  
  // 环境特定安全级别
  const getSecurityLevel = (): 'low' | 'medium' | 'high' => {
    if (process.env.NODE_ENV === 'development') return 'low';
    if (process.env.NODE_ENV === 'test') return 'medium';
    return 'high'; // 生产环境使用最高安全级别
  };

  // 根据安全级别生成默认策略
  const generateDefaultPolicy = (securityLevel: 'low' | 'medium' | 'high') => {
    const basePolicy = {
      // 内容安全策略 - Content Security Policy
      contentSecurityPolicy: {
        // 允许的脚本源 - Allowed script sources
        scriptSrc: securityLevel === 'high' 
          ? ["'self'", `'nonce-${generateNonce()}'`]
          : ["'self'", "'unsafe-inline'", "'unsafe-eval'"],
          
        // 允许的样式源 - Allowed style sources
        styleSrc: securityLevel === 'high'
          ? ["'self'", `'nonce-${generateNonce()}'`]
          : ["'self'", "'unsafe-inline'"],
          
        // 允许的图片源 - Allowed image sources
        imgSrc: ["'self'", 'data:', 'blob:'],
        
        // 允许的连接源 - Allowed connection sources
        connectSrc: ["'self'"],
        
        // 允许的字体源 - Allowed font sources
        fontSrc: ["'self'", 'data:'],
        
        // 允许的媒体源 - Allowed media sources
        mediaSrc: ["'self'", 'blob:'],
        
        // 框架源 - Frame sources
        frameSrc: securityLevel === 'low' ? ["'self'", '*'] : ["'self'"]
      },
      
      // 沙箱属性 - Sandbox attributes
      sandbox: {
        // 允许同源 - Allow same origin
        allowSameOrigin: true,
        
        // 允许表单提交 - Allow forms
        allowForms: true,
        
        // 允许脚本执行 - Allow scripts
        allowScripts: true,
        
        // 允许弹窗 - Allow popups
        allowPopups: securityLevel !== 'high',
        
        // 允许下载 - Allow downloads
        allowDownloads: securityLevel !== 'high',
        
        // 禁止顶部导航 - Prevent top navigation
        allowTopNavigation: securityLevel === 'low',
        
        // 禁止打开新窗口 - Prevent opening new windows
        allowPopupsToEscapeSandbox: securityLevel === 'low'
      },
      
      // 安全头部配置 - Security headers configuration
      securityHeaders: {
        // X-Content-Type-Options
        'X-Content-Type-Options': 'nosniff',
        
        // X-Frame-Options
        'X-Frame-Options': securityLevel === 'high' ? 'DENY' : 'SAMEORIGIN',
        
        // X-XSS-Protection
        'X-XSS-Protection': '1; mode=block',
        
        // Referrer Policy
        'Referrer-Policy': 'strict-origin-when-cross-origin'
      }
    };
    
    // 生产环境额外安全增强
    if (securityLevel === 'high') {
      basePolicy.contentSecurityPolicy.objectSrc = ["'none'"];
      basePolicy.contentSecurityPolicy.baseUri = ["'self'"];
      basePolicy.securityHeaders['Strict-Transport-Security'] = 'max-age=31536000; includeSubDomains; preload';
    }
    
    return basePolicy;
  };

  const securityLevel = getSecurityLevel();
  const defaultPolicy = generateDefaultPolicy(securityLevel);

  // 合并自定义安全策略
  if (typeof policy === 'object' && policy !== null) {
    const custom = policy as Record<string, any>;
    
    // 深度合并内容安全策略（处理数组类型的源）
    const mergedContentSecurityPolicy: Record<string, string[]> = {};
    const allKeys = new Set([...Object.keys(defaultPolicy.contentSecurityPolicy), ...Object.keys(custom.contentSecurityPolicy || {})]);
    
    allKeys.forEach(key => {
      const defaultSources = Array.isArray(defaultPolicy.contentSecurityPolicy[key]) 
        ? defaultPolicy.contentSecurityPolicy[key] 
        : [];
      const customSources = Array.isArray(custom.contentSecurityPolicy?.[key]) 
        ? custom.contentSecurityPolicy[key] 
        : [];
      mergedContentSecurityPolicy[key] = [...new Set([...defaultSources, ...customSources])];
    });
    
    return {
      ...defaultPolicy,
      ...custom,
      // 深度合并内容安全策略
      contentSecurityPolicy: mergedContentSecurityPolicy,
      // 深度合并沙箱属性
      sandbox: { 
        ...defaultPolicy.sandbox, 
        ...custom.sandbox 
      },
      // 深度合并安全头部
      securityHeaders: { 
        ...defaultPolicy.securityHeaders, 
        ...custom.securityHeaders 
      }
    };
  }

  return defaultPolicy;
}
```

## 7. 性能优化

### 7.1 预加载策略

智能预加载机制可以显著提升微应用的加载速度和用户体验。

```typescript
/**
 * 微应用预加载管理器
 */
export class MicroAppPreloader {
  private preloadedApps: Set<string>;
  private preloadingApps: Set<string>;
  private microAppManager: MicroAppManager;

  constructor() {
    this.preloadedApps = new Set();
    this.preloadingApps = new Set();
    this.microAppManager = MicroAppManager.getInstance();
  }

  /**
   * 预加载单个微应用
   * @param appName 应用名称
   * @param options 预加载选项
   */
  async preloadApp(appName: string, options?: {
    force?: boolean;
    timeout?: number;
  }): Promise<boolean> {
    const { force = false, timeout = 30000 } = options || {};

    // 如果已经预加载过且不强制重新加载，则直接返回成功
    if (this.preloadedApps.has(appName) && !force) {
      return true;
    }

    // 如果正在加载中，则等待加载完成
    if (this.preloadingApps.has(appName)) {
      // 等待最多timeout毫秒
      const waitStartTime = Date.now();
      while (this.preloadingApps.has(appName) && Date.now() - waitStartTime < timeout) {
        await new Promise(resolve => setTimeout(resolve, 100));
      }
      return this.preloadedApps.has(appName);
    }

    try {
      this.preloadingApps.add(appName);

      // 获取应用配置
      const config = this.microAppManager.getAppConfig(appName);
      if (!config || !config.entry) {
        console.error(`预加载失败: 未找到应用配置或入口: ${appName}`);
        return false;
      }

      // 开始预加载计时
      const startTime = performance.now();

      // 使用无界框架的预加载API
      await preloadApp(appName, config.entry);

      // 计算加载时间
      const loadTime = performance.now() - startTime;
      console.log(`微应用 ${appName} 预加载完成，耗时: ${loadTime.toFixed(2)}ms`);

      // 标记为已预加载
      this.preloadedApps.add(appName);

      // 触发预加载完成事件
      globalEventBus.emit(AppEvents.MICRO_APP_PRELOADED, {
        appName,
        loadTime,
        timestamp: Date.now()
      });

      return true;
    } catch (error) {
      console.error(`微应用 ${appName} 预加载失败:`, error);
      
      // 触发预加载失败事件
      globalEventBus.emit(AppEvents.MICRO_APP_LOAD_ERROR, {
        appName,
        error: (error as Error).message,
        timestamp: Date.now()
      });

      return false;
    } finally {
      this.preloadingApps.delete(appName);
    }
  }

  /**
   * 批量预加载微应用
   * @param appNames 应用名称数组
   * @param options 预加载选项
   */
  async preloadApps(appNames: string[], options?: {
    force?: boolean;
    timeout?: number;
    concurrency?: number;
  }): Promise<{
    success: string[];
    failed: string[];
  }> {
    const { concurrency = 3 } = options || {};
    const success: string[] = [];
    const failed: string[] = [];

    // 创建并发控制的预加载任务
    const chunks: string[][] = [];
    for (let i = 0; i < appNames.length; i += concurrency) {
      chunks.push(appNames.slice(i, i + concurrency));
    }

    // 按批次执行预加载
    for (const chunk of chunks) {
      const results = await Promise.all(
        chunk.map(name => this.preloadApp(name, options))
      );

      // 收集结果
      results.forEach((result, index) => {
        if (result) {
          success.push(chunk[index]);
        } else {
          failed.push(chunk[index]);
        }
      });
    }

    return { success, failed };
  }

  /**
   * 智能预加载推荐的微应用
   * 基于用户行为和应用关联关系进行预加载
   */
  async smartPreload(options?: {
    maxApps?: number;
    ignoreActiveApp?: boolean;
  }): Promise<void> {
    const { maxApps = 3, ignoreActiveApp = true } = options || {};

    // 获取所有应用配置
    const allApps = this.microAppManager.getAllAppConfigs();
    if (allApps.length === 0) {
      return;
    }

    // 获取当前活动的应用
    let activeAppName: string | null = null;
    if (ignoreActiveApp) {
      // 这里可以通过全局状态获取当前活动的应用
      // 例如: activeAppName = getActiveAppName();
    }

    // 根据优先级和预加载配置排序
    const recommendedApps = allApps
      .filter(app => 
        (!ignoreActiveApp || app.name !== activeAppName) && 
        app.preload !== false
      )
      .sort((a, b) => (a.priority || 0) - (b.priority || 0))
      .slice(0, maxApps)
      .map(app => app.name);

    // 执行预加载
    await this.preloadApps(recommendedApps, {
      concurrency: 2 // 限制并发数
    });
  }

  /**
   * 检查应用是否已预加载
   * @param appName 应用名称
   */
  isPreloaded(appName: string): boolean {
    return this.preloadedApps.has(appName);
  }

  /**
   * 检查应用是否正在预加载
   * @param appName 应用名称
   */
  isPreloading(appName: string): boolean {
    return this.preloadingApps.has(appName);
  }

  /**
   * 清除预加载缓存
   * @param appName 应用名称，如果不指定则清除所有
   */
  clearCache(appName?: string): void {
    if (appName) {
      this.preloadedApps.delete(appName);
    } else {
      this.preloadedApps.clear();
    }
  }
}

// 导出预加载管理器实例
export const microAppPreloader = new MicroAppPreloader();
```

### 7.2 资源管理

资源管理机制可以有效控制和优化微应用的资源使用。

```typescript
/**
 * 响应缓存管理器
 */
export class ResponseCacheManager {
  private cache: Map<string, {
    data: any;
    timestamp: number;
    ttl: number;
  }>;
  private maxSize: number;
  private defaultTTL: number;

  constructor(options?: {
    maxSize?: number;
    defaultTTL?: number;
  }) {
    this.cache = new Map();
    this.maxSize = options?.maxSize || 100;
    this.defaultTTL = options?.defaultTTL || 300000; // 默认5分钟
    
    // 启动定期清理任务
    this.startCleanupTask();
  }

  /**
   * 缓存响应数据
   * @param key 缓存键
   * @param data 响应数据
   * @param ttl 过期时间(毫秒)
   */
  set(key: string, data: any, ttl?: number): void {
    // 如果缓存已满，删除最旧的缓存项
    if (this.cache.size >= this.maxSize) {
      const oldestKey = this.getOldestKey();
      if (oldestKey) {
        this.cache.delete(oldestKey);
      }
    }

    this.cache.set(key, {
      data,
      timestamp: Date.now(),
      ttl: ttl !== undefined ? ttl : this.defaultTTL
    });
  }

  /**
   * 获取缓存数据
   * @param key 缓存键
   */
  get(key: string): any | null {
    const item = this.cache.get(key);
    if (!item) {
      return null;
    }

    // 检查是否过期
    if (Date.now() - item.timestamp > item.ttl) {
      this.cache.delete(key);
      return null;
    }

    return item.data;
  }

  /**
   * 清除缓存
   * @param key 缓存键，如果不指定则清除所有
   */
  clear(key?: string): void {
    if (key) {
      this.cache.delete(key);
    } else {
      this.cache.clear();
    }
  }

  /**
   * 获取缓存大小
   */
  size(): number {
    return this.cache.size;
  }

  /**
   * 获取最旧的缓存键
   */
  private getOldestKey(): string | null {
    let oldestKey: string | null = null;
    let oldestTimestamp = Infinity;

    for (const [key, item] of this.cache.entries()) {
      if (item.timestamp < oldestTimestamp) {
        oldestKey = key;
        oldestTimestamp = item.timestamp;
      }
    }

    return oldestKey;
  }

  /**
   * 清理过期缓存
   */
  private cleanup(): void {
    const now = Date.now();
    for (const [key, item] of this.cache.entries()) {
      if (now - item.timestamp > item.ttl) {
        this.cache.delete(key);
      }
    }
  }

  /**
   * 启动定期清理任务
   */
  private startCleanupTask(): void {
    // 每30秒清理一次过期缓存
    setInterval(() => {
      this.cleanup();
    }, 30000);
  }
}

// 导出响应缓存管理器实例
export const responseCacheManager = new ResponseCacheManager();

/**
 * 判断是否为可重试的错误
 * @param error 错误对象
 */
export function isRetryableError(error: Error): boolean {
  // 网络错误可以重试
  if (error.name === 'NetworkError' || error.message.includes('网络错误')) {
    return true;
  }
  
  // 请求超时可以重试
  if (error.name === 'TimeoutError' || error.message.includes('timeout')) {
    return true;
  }
  
  // 服务器错误可以重试 (5xx)
  if ((error as any).status && (error as any).status >= 500 && (error as any).status < 600) {
    return true;
  }
  
  return false;
}

/**
 * 创建安全的fetch拦截器
 * @param options 拦截器选项
 */
export function createSecureFetchInterceptor(options?: {
  enableCache?: boolean;
  retryCount?: number;
  retryDelay?: number;
}) {
  const { 
    enableCache = true, 
    retryCount = 3, 
    retryDelay = 1000 
  } = options || {};

  return async (fetch: Function, url: string, config: RequestInit = {}) => {
    // 构建缓存键
    const cacheKey = `${url}_${JSON.stringify(config)}`;
    
    // 如果启用缓存且是GET请求，尝试从缓存获取
    if (enableCache && config.method?.toUpperCase() === 'GET') {
      const cachedData = responseCacheManager.get(cacheKey);
      if (cachedData) {
        console.log(`从缓存获取: ${url}`);
        return cachedData;
      }
    }

    // 重试逻辑
    let lastError: Error | null = null;
    
    for (let attempt = 0; attempt <= retryCount; attempt++) {
      try {
        // 执行fetch请求
        const response = await fetch(url, config);
        
        // 检查响应状态
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        // 解析响应数据
        const data = await response.json();
        
        // 如果启用缓存且是GET请求，缓存响应
        if (enableCache && config.method?.toUpperCase() === 'GET') {
          responseCacheManager.set(cacheKey, data);
        }
        
        return data;
      } catch (error) {
        lastError = error as Error;
        
        // 判断是否可以重试
        if (!isRetryableError(lastError) || attempt === retryCount) {
          throw lastError;
        }
        
        // 等待后重试，使用指数退避策略
        const delay = retryDelay * Math.pow(2, attempt);
        console.log(`请求失败，${delay}ms后重试 (${attempt + 1}/${retryCount}): ${url}`);
        await new Promise(resolve => setTimeout(resolve, delay));
      }
    }
    
    // 如果所有重试都失败，抛出最后一个错误
    throw lastError;
  };
}
```

## 8. 总结

本文档详细介绍了Bone企业级开发平台的React前端微应用架构设计方案，重点关注了主框架和微应用的核心设计。该架构具有以下优势：

- **模块化**：通过微前端架构实现业务模块的独立开发和部署
- **高性能**：采用预加载、缓存等机制提升用户体验
- **安全性**：通过沙箱技术确保微应用间的安全隔离
- **可扩展性**：支持新微应用的动态注册和加载
- **良好的协作**：多团队可以并行开发，互不干扰

通过这种架构设计，Bone平台能够有效支持大规模企业级应用的开发和维护，提高开发效率和系统可靠性。