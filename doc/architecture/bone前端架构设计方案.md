# 🎨 React Frontend Micro-Architecture Design / React前端微架构设计

## 🎯 **核心设计理念与架构原则**

### 1.1 前端架构设计哲学
```typescript
/**
 * BONE 前端架构核心原则
 * B - Business Component Based (基于业务组件)
 * O - Optimized Performance (优化性能)
 * N - Natively Decoupled (原生解耦)
 * E - Extensible & Evolvable (可扩展可演进)
 */
```

### 1.2 核心设计原则
- **微前端架构**: 将大型前端应用拆分为独立的微应用，实现业务模块解耦
- **组件化设计**: 采用原子设计方法论，构建可复用的组件体系
- **类型安全**: 全面使用TypeScript，确保代码质量和开发体验
- **性能优先**: 实现智能预加载、资源缓存和懒加载等优化策略
- **可扩展性**: 支持新微应用的动态注册和加载，适应业务增长

---

## 📋 **概述** / Overview

本文档详细描述了Bone企业级开发平台的React前端微应用架构设计方案。该方案基于业界最佳实践，旨在构建一个高度可扩展、高性能、易维护的前端应用框架，支持多团队并行协作开发，实现业务模块的独立部署和运行。通过微前端架构，我们将大型前端应用拆分为多个小型、松耦合的微应用，每个微应用可以由独立团队负责，使用适合其业务场景的技术栈。

## 📐 **架构设计** / Architecture Design

### 2.1 Micro-Frontend Architecture Overview / 微前端架构概述

Bone前端采用现代混合式微前端架构，基于无界框架(wujie)实现，结合了基座模式和去中心化模式的优点，具有以下核心特性：

- **Decentralized Development**: 支持多个团队独立开发、测试和部署微应用，降低团队间协作复杂度
- **Technology Agnostic**: 微应用可以使用不同的技术栈开发，兼容React、Vue、Angular等主流框架
- **High Performance**: 通过智能预加载、资源缓存、懒加载等机制显著提升用户体验
- **Secure Isolation**: 采用多层级沙箱技术确保微应用间的安全隔离，防止全局变量污染和样式冲突
- **Unified Management**: 提供统一的应用注册、路由管理、生命周期控制和权限管理
- **Seamless Communication**: 实现主应用与微应用、微应用与微应用间的高效通信机制

### 2.2 Core Architecture Components / 核心架构组件

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

## 🔧 **主框架设计** / Main Framework Design

### 3.1 Main Framework Architecture / 主框架架构概述

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

### 3.2 Micro-Frontend Orchestrator / 微前端协调器

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

### 3.3 Application Initialization Flow / 应用初始化流程

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

## 🧩 **微应用设计与工程结构** / Micro Application Design & Engineering Structure

### 4.1 Micro Application Architecture / 微应用架构

微应用(Micro Application)是独立的业务模块，具有自己的路由、状态管理和业务逻辑，遵循独立开发、独立部署、独立运行的原则。每个微应用都可以作为独立的Web应用运行，也可以作为整体系统的一部分被主框架加载和管理。

### 4.1.1 微应用特性

- **独立性**: 拥有独立的代码库、构建流程和部署通道
- **自包含**: 包含完整的业务逻辑、UI组件和数据处理
- **标准化**: 遵循统一的微前端接口规范，便于与主框架集成
- **可复用**: 提供可复用的业务能力，可以被多个场景调用

### 4.2 Complete Frontend Engineering Structure / 完整前端工程结构

### 4.2.1 Monorepo Structure Overview / 单仓库结构概览

Bone平台前端采用现代化的Monorepo架构，使用Lerna和Yarn Workspaces进行管理，实现代码共享和依赖管理的最优化。

```
bone-frontend/                 # 前端根目录
├── apps/                      # 应用目录
│   ├── main-app/              # 主应用 (基座应用)
│   ├── micro-app-admin/       # 管理门户微应用
│   ├── micro-app-analytics/   # 数据分析微应用
│   ├── micro-app-workflow/    # 工作流引擎微应用
│   └── micro-app-user/        # 用户中心微应用
├── packages/                  # 共享包目录
│   ├── ui-components/         # UI组件库
│   ├── micro-frontend-sdk/    # 微前端SDK
│   ├── shared-utils/          # 共享工具函数
│   ├── api-client/            # API客户端
│   └── eslint-config/         # ESLint配置
├── scripts/                   # 构建和部署脚本
├── docs/                      # 文档
├── .eslintrc.js               # 根目录ESLint配置
├── .prettierrc                # Prettier配置
├── lerna.json                 # Lerna配置
├── package.json               # 根目录package.json
├── tsconfig.json              # 根目录TypeScript配置
└── README.md                  # 项目说明文档
```

### 4.2.2 Main Application Structure / 主应用结构

```
main-app/
├── public/                    # 静态资源目录
│   ├── index.html            # HTML入口文件
│   ├── favicon.ico           # 网站图标
│   └── manifest.json         # PWA配置文件
├── src/
│   ├── assets/               # 资源文件目录
│   │   ├── images/           # 图片资源
│   │   ├── icons/            # 图标资源
│   │   ├── fonts/            # 字体资源
│   │   └── styles/           # 全局样式
│   ├── components/           # 公共组件
│   │   ├── layout/           # 布局组件
│   │   ├── common/           # 通用组件
│   │   └── business/         # 业务组件
│   ├── config/               # 配置文件
│   │   ├── appConfig.ts      # 应用配置
│   │   ├── microApps.ts      # 微应用配置
│   │   └── securityConfig.ts # 安全配置
│   ├── core/                 # 核心模块
│   │   ├── orchestrator/     # 微前端协调器
│   │   ├── router/           # 路由系统
│   │   ├── store/            # 状态管理
│   │   └── theme/            # 主题配置
│   ├── hooks/                # 自定义Hooks
│   ├── pages/                # 页面组件
│   │   ├── Home/             # 首页
│   │   ├── Login/            # 登录页
│   │   ├── Layout/           # 主布局
│   │   └── Error/            # 错误页面
│   ├── services/             # API服务
│   │   ├── authService.ts    # 认证服务
│   │   ├── userService.ts    # 用户服务
│   │   └── apiClient.ts      # API客户端
│   ├── types/                # TypeScript类型定义
│   ├── utils/                # 工具函数
│   │   ├── formatters.ts     # 格式化工具
│   │   ├── validators.ts     # 验证工具
│   │   └── security.ts       # 安全工具
│   ├── App.tsx               # 应用根组件
│   ├── main.tsx              # 应用入口文件
│   ├── routes.tsx            # 路由配置
│   └── setupTests.ts         # 测试配置
├── tests/                    # 测试文件
│   ├── unit/                 # 单元测试
│   └── integration/          # 集成测试
├── .env.development          # 开发环境变量
├── .env.production           # 生产环境变量
├── .env.staging              # 预发环境变量
├── babel.config.js           # Babel配置
├── jest.config.js            # Jest配置
├── package.json              # 依赖配置
├── tsconfig.json             # TypeScript配置
├── tsconfig.paths.json       # TypeScript路径别名
├── vite.config.ts            # Vite配置 (现代化构建工具)
└── README.md                 # 项目说明
```

### 4.2.3 Micro Application Structure / 微应用结构

```
micro-app/
├── public/                    # 静态资源
├── src/
│   ├── assets/               # 静态资源 - 图片、样式等
│   ├── components/           # React组件 - 业务组件
│   ├── config/               # 微应用配置
│   ├── hooks/                # 自定义Hooks
│   ├── pages/                # 页面组件
│   ├── router/               # 微应用路由配置
│   ├── services/             # API服务
│   ├── store/                # 状态管理
│   │   ├── slices/           # Redux Toolkit slices
│   │   ├── selectors/        # 选择器
│   │   └── index.ts          # store配置
│   ├── types/                # TypeScript类型定义
│   ├── utils/                # 工具函数
│   ├── App.tsx               # 根应用组件
│   ├── bootstrap.tsx         # 微应用启动入口 (必须)
│   └── index.ts              # 模块导出
├── .eslintrc.js              # ESLint配置
├── package.json              # 依赖和脚本配置
├── tsconfig.json             # TypeScript配置
├── vite.config.ts            # Vite配置
└── README.md                 # 项目文档
```

### 4.2.4 Shared Packages Structure / 共享包结构

#### UI组件库
```
ui-components/
├── src/
│   ├── components/           # 组件
│   │   ├── Button/           # Button组件
│   │   │   ├── Button.tsx
│   │   │   ├── Button.types.ts
│   │   │   ├── Button.stories.tsx
│   │   │   └── Button.test.tsx
│   │   ├── Form/             # Form组件
│   │   ├── Table/            # Table组件
│   │   └── index.ts          # 组件导出
│   ├── hooks/                # 组件Hooks
│   ├── theme/                # 主题配置
│   ├── types/                # 类型定义
│   └── utils/                # 工具函数
├── docs/                     # 组件文档
├── stories/                  # Storybook stories
├── package.json              # 包配置
├── tsconfig.json             # TypeScript配置
└── README.md                 # 文档
```

#### 微前端SDK
```
micro-frontend-sdk/
├── src/
│   ├── communication/        # 通信模块
│   ├── lifecycle/            # 生命周期管理
│   ├── sandbox/              # 沙箱工具
│   ├── utils/                # 工具函数
│   └── index.ts              # 导出
├── package.json
└── tsconfig.json
```

### 4.2.5 Build Tooling Configuration / 构建工具配置

#### Vite配置示例 (vite.config.ts)
```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';
import { createHtmlPlugin } from 'vite-plugin-html';
import styleImport from 'vite-plugin-style-import';
import microApp from '@micro-zoe/micro-app';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const isProduction = mode === 'production';
  
  return {
    plugins: [
      react({
        jsxRuntime: 'automatic'
      }),
      createHtmlPlugin({
        inject: {
          data: {
            title: 'Bone Platform'
          }
        }
      }),
      styleImport({
        libs: [
          {
            libraryName: 'antd',
            esModule: true,
            resolveStyle: (name: string) => `antd/es/${name}/style/index`
          }
        ]
      })
    ],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src'),
        '@bone': resolve(__dirname, '../packages')
      }
    },
    server: {
      port: 3000,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true
        }
      }
    },
    build: {
      outDir: 'dist',
      minify: 'terser',
      sourcemap: !isProduction,
      rollupOptions: {
        output: {
          manualChunks: {
            vendor: ['react', 'react-dom'],
            antd: ['antd'],
            redux: ['redux', '@reduxjs/toolkit', 'react-redux']
          }
        }
      }
    },
    css: {
      preprocessorOptions: {
        less: {
          javascriptEnabled: true
        }
      }
    }
  };
});
```

### 4.2.6 CI/CD Pipeline Configuration / CI/CD流水线配置

```yaml
# .github/workflows/ci-cd.yml example
name: Frontend CI/CD

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    - name: Setup Node.js
      uses: actions/setup-node@v3
      with:
        node-version: '16'
        cache: 'yarn'
    
    - name: Install dependencies
      run: yarn install --frozen-lockfile
    
    - name: Lint code
      run: yarn lint
    
    - name: Run tests
      run: yarn test
    
    - name: Build applications
      run: yarn build
    
    - name: Upload build artifacts
      uses: actions/upload-artifact@v3
      with:
        name: build-artifacts
        path: |
          apps/**/dist
          packages/**/dist

  deploy:
    needs: build
    if: github.event_name == 'push' && (github.ref == 'refs/heads/main' || github.ref == 'refs/heads/develop')
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/download-artifact@v3
      with:
        name: build-artifacts
        path: ./dist
    
    - name: Deploy to environment
      run: |
        if [ "${{ github.ref }}" = "refs/heads/main" ]; then
          # 部署到生产环境
          ./scripts/deploy.sh production
        else
          # 部署到测试环境
          ./scripts/deploy.sh staging
        fi
```

### 4.2.7 Code Quality Tools Configuration / 代码质量工具配置

#### ESLint配置 (.eslintrc.js)
```javascript
module.exports = {
  root: true,
  extends: [
    'airbnb',
    'airbnb-typescript',
    'airbnb/hooks',
    'plugin:@typescript-eslint/recommended',
    'plugin:react-hooks/recommended',
    'plugin:prettier/recommended'
  ],
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
    project: './tsconfig.json'
  },
  plugins: ['react', '@typescript-eslint', 'prettier'],
  rules: {
    'react/react-in-jsx-scope': 'off',
    'react/prop-types': 'off',
    '@typescript-eslint/explicit-function-return-type': 'off',
    'import/prefer-default-export': 'off',
    'prettier/prettier': ['error']
  },
  settings: {
    react: {
      version: 'detect'
    }
  }
};
```

#### Prettier配置 (.prettierrc)
```json
{
  "semi": true,
  "trailingComma": "all",
  "singleQuote": true,
  "printWidth": 80,
  "tabWidth": 2
}
```

### 4.2.8 Development Workflow / 开发工作流

1. **本地开发环境设置**
   - 安装依赖: `yarn install`
   - 启动主应用: `yarn workspace main-app dev`
   - 启动微应用: `yarn workspace micro-app-name dev`
   - 启动所有微应用: `yarn dev:all`

2. **代码提交规范**
   - 使用Husky进行Git钩子管理
   - Commit message格式遵循Conventional Commits
   - 提交前自动运行lint和测试

3. **测试策略**
   - 单元测试: Jest + React Testing Library
   - 集成测试: Cypress
   - 代码覆盖率要求: >80%

4. **文档生成**
   - 组件文档: Storybook
   - API文档: TypeDoc
   - 架构文档: Markdown + Docusaurus

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

## 💬 **微前端通信机制** / Micro-Frontend Communication

### 4.3.1 Event Bus System / 事件总线系统

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

### 4.3.2 Message Communication Tool / 消息通信工具

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

## 7. Performance Optimization

### 7.1 Preloading Strategy

智能预加载机制可以显著提升微应用的加载速度和用户体验，通过分析用户行为和应用关联关系，在合适的时机预加载可能会被访问的微应用。

```typescript
/**
 * Micro Application Preloader Manager
 * 微应用预加载管理器 - 负责智能预加载微应用资源，提升加载性能
 */
export class MicroAppPreloader {
  private preloadedApps: Set<string>;
  private preloadingApps: Set<string>;
  private microAppManager: MicroAppManager;
  private preloadMetrics: Map<string, {
    loadTime: number;
    success: boolean;
    timestamp: number;
    attempts: number;
  }>;

  constructor() {
    this.preloadedApps = new Set();
    this.preloadingApps = new Set();
    this.microAppManager = MicroAppManager.getInstance();
    this.preloadMetrics = new Map();
    this.initializeEventListeners();
  }

  /**
   * Initialize Event Listeners
   * 初始化事件监听器 - 监听路由变化和用户交互事件以触发预加载
   */
  private initializeEventListeners(): void {
    // 监听路由变化事件
    globalEventBus.on(AppEvents.ROUTE_CHANGED, (data: { path: string }) => {
      this.handleRouteChange(data.path);
    });
    
    // 监听用户点击事件以进行预测性预加载
    if (typeof window !== 'undefined') {
      document.addEventListener('click', this.handleUserInteraction.bind(this), { passive: true });
    }
  }

  /**
   * Handle Route Change
   * 处理路由变化 - 根据当前路由预加载相关微应用
   * @param path 当前路由路径
   */
  private handleRouteChange(path: string): void {
    // 延迟执行，避免影响当前页面渲染
    setTimeout(() => {
      this.smartPreload({
        strategy: 'route',
        currentPath: path
      });
    }, 300);
  }

  /**
   * Handle User Interaction
   * 处理用户交互 - 基于用户点击行为进行预测性预加载
   * @param event 点击事件
   */
  private handleUserInteraction(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    const appLink = target.closest('[data-app-link]') as HTMLElement | null;
    
    if (appLink) {
      const appName = appLink.getAttribute('data-app-name');
      if (appName && !this.isPreloaded(appName) && !this.isPreloading(appName)) {
        // 用户可能即将访问该应用，提前预加载
        this.preloadApp(appName, { priority: 'high' });
      }
    }
  }

  /**
   * Preload Single Micro Application
   * 预加载单个微应用 - 加载微应用资源到内存
   * @param appName 应用名称
   * @param options 预加载选项
   */
  async preloadApp(appName: string, options?: {
    force?: boolean;
    timeout?: number;
    priority?: 'low' | 'medium' | 'high';
  }): Promise<boolean> {
    const { 
      force = false, 
      timeout = 30000, 
      priority = 'medium' 
    } = options || {};

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

    // 检查系统资源状况，仅在资源充足时预加载
    if (!this.isSystemResourceAvailable(priority)) {
      console.warn(`系统资源不足，延迟预加载: ${appName}`);
      // 延迟预加载
      setTimeout(() => this.preloadApp(appName, options), 1000);
      return false;
    }

    try {
      this.preloadingApps.add(appName);
      const metrics = this.preloadMetrics.get(appName) || { 
        loadTime: 0, 
        success: false, 
        timestamp: 0, 
        attempts: 0 
      };
      metrics.attempts += 1;

      // 获取应用配置
      const config = this.microAppManager.getAppConfig(appName);
      if (!config || !config.entry) {
        console.error(`预加载失败: 未找到应用配置或入口: ${appName}`);
        return false;
      }

      // 开始预加载计时
      const startTime = performance.now();

      // 根据优先级设置不同的加载策略
      const loadOptions = priority === 'high' 
        ? { timeout: timeout / 2 } // 高优先级应用使用更短的超时时间
        : {};

      // 使用无界框架的预加载API
      await preloadApp(appName, config.entry, loadOptions);

      // 计算加载时间
      const loadTime = performance.now() - startTime;
      console.log(`微应用 ${appName} 预加载完成，耗时: ${loadTime.toFixed(2)}ms`);

      // 更新指标
      metrics.loadTime = loadTime;
      metrics.success = true;
      metrics.timestamp = Date.now();
      this.preloadMetrics.set(appName, metrics);

      // 标记为已预加载
      this.preloadedApps.add(appName);

      // 触发预加载完成事件
      globalEventBus.emit(AppEvents.MICRO_APP_PRELOADED, {
        appName,
        loadTime,
        timestamp: Date.now(),
        priority
      });

      // 预加载完成后处理消息队列
      microAppMessenger.processMessageQueue();

      return true;
    } catch (error) {
      console.error(`微应用 ${appName} 预加载失败:`, error);
      
      // 更新指标
      metrics.success = false;
      metrics.timestamp = Date.now();
      this.preloadMetrics.set(appName, metrics);
      
      // 触发预加载失败事件
      globalEventBus.emit(AppEvents.MICRO_APP_LOAD_ERROR, {
        appName,
        error: (error as Error).message,
        timestamp: Date.now(),
        attempts: metrics.attempts
      });

      return false;
    } finally {
      this.preloadingApps.delete(appName);
    }
  }

  /**
   * Batch Preload Micro Applications
   * 批量预加载微应用 - 控制并发数进行批量预加载
   * @param appNames 应用名称数组
   * @param options 预加载选项
   */
  async preloadApps(appNames: string[], options?: {
    force?: boolean;
    timeout?: number;
    concurrency?: number;
    priority?: 'low' | 'medium' | 'high';
  }): Promise<{
    success: string[];
    failed: string[];
  }> {
    const { 
      concurrency = 3,
      priority = 'medium'
    } = options || {};
    const success: string[] = [];
    const failed: string[] = [];

    // 根据网络状况调整并发数
    const adjustedConcurrency = this.adjustConcurrencyByNetwork(concurrency, priority);

    // 创建并发控制的预加载任务
    const chunks: string[][] = [];
    for (let i = 0; i < appNames.length; i += adjustedConcurrency) {
      chunks.push(appNames.slice(i, i + adjustedConcurrency));
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

      // 批次间隔，避免过度占用资源
      if (chunks.indexOf(chunk) < chunks.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 100));
      }
    }

    return { success, failed };
  }

  /**
   * Smart Preload Recommended Micro Applications
   * 智能预加载推荐的微应用 - 基于多种策略进行智能预加载
   * @param options 预加载选项
   */
  async smartPreload(options?: {
    maxApps?: number;
    ignoreActiveApp?: boolean;
    strategy?: 'user-behavior' | 'route' | 'relationship';
    currentPath?: string;
  }): Promise<void> {
    const { 
      maxApps = 3, 
      ignoreActiveApp = true,
      strategy = 'user-behavior',
      currentPath = ''
    } = options || {};

    // 获取所有应用配置
    const allApps = this.microAppManager.getAllAppConfigs();
    if (allApps.length === 0) {
      return;
    }

    // 获取当前活动的应用
    let activeAppName: string | null = null;
    if (ignoreActiveApp) {
      // 通过全局状态获取当前活动的应用
      activeAppName = this.getActiveAppName();
    }

    let recommendedApps: string[] = [];
    
    // 根据不同策略获取推荐应用
    switch (strategy) {
      case 'route':
        // 基于路由路径的预加载
        recommendedApps = this.getAppsByRoute(currentPath, activeAppName);
        break;
        
      case 'relationship':
        // 基于应用关联关系的预加载
        recommendedApps = this.getAppsByRelationship(activeAppName);
        break;
        
      case 'user-behavior':
      default:
        // 基于用户行为和优先级的预加载
        recommendedApps = this.getAppsByUserBehavior(activeAppName);
        break;
    }

    // 过滤已预加载和正在预加载的应用
    const appsToPreload = recommendedApps
      .filter(app => !this.isPreloaded(app) && !this.isPreloading(app))
      .slice(0, maxApps);

    // 如果有应用需要预加载，则执行预加载
    if (appsToPreload.length > 0) {
      await this.preloadApps(appsToPreload, {
        concurrency: 2,
        priority: 'low' // 智能预加载使用低优先级
      });
    }
  }

  /**
   * Get Apps By Route
   * 根据路由获取相关应用
   * @param path 当前路由路径
   * @param activeAppName 当前活动应用名称
   */
  private getAppsByRoute(path: string, activeAppName: string | null): string[] {
    // 根据路由路径匹配相关应用
    return this.microAppManager.getAllAppConfigs()
      .filter(app => 
        (!activeAppName || app.name !== activeAppName) && 
        app.preload !== false &&
        (app.routes || []).some(route => path.startsWith(route))
      )
      .sort((a, b) => (a.priority || 0) - (b.priority || 0))
      .map(app => app.name);
  }

  /**
   * Get Apps By Relationship
   * 根据应用关联关系获取推荐应用
   * @param activeAppName 当前活动应用名称
   */
  private getAppsByRelationship(activeAppName: string | null): string[] {
    if (!activeAppName) return [];
    
    // 获取当前应用配置
    const activeApp = this.microAppManager.getAppConfig(activeAppName);
    if (!activeApp || !activeApp.relatedApps) return [];
    
    // 返回相关联的应用
    return activeApp.relatedApps
      .map(appName => this.microAppManager.getAppConfig(appName))
      .filter(app => app && app.preload !== false)
      .sort((a, b) => (a!.priority || 0) - (b!.priority || 0))
      .map(app => app!.name);
  }

  /**
   * Get Apps By User Behavior
   * 根据用户行为获取推荐应用
   * @param activeAppName 当前活动应用名称
   */
  private getAppsByUserBehavior(activeAppName: string | null): string[] {
    // 这里可以集成用户行为分析逻辑
    // 例如基于历史访问频率、时间模式等
    
    // 简化实现：基于优先级和预加载配置
    return this.microAppManager.getAllAppConfigs()
      .filter(app => 
        (!activeAppName || app.name !== activeAppName) && 
        app.preload !== false
      )
      .sort((a, b) => {
        // 优先排序有预加载时间窗口的应用
        const now = new Date().getHours();
        const aInWindow = this.isInPreloadWindow(a.preloadWindow, now);
        const bInWindow = this.isInPreloadWindow(b.preloadWindow, now);
        
        if (aInWindow && !bInWindow) return -1;
        if (!aInWindow && bInWindow) return 1;
        
        // 其次基于优先级
        return (a.priority || 0) - (b.priority || 0);
      })
      .map(app => app.name);
  }

  /**
   * Check if in Preload Window
   * 检查是否在预加载时间窗口内
   * @param window 预加载时间窗口配置
   * @param currentHour 当前小时
   */
  private isInPreloadWindow(window?: [number, number], currentHour?: number): boolean {
    if (!window || !currentHour) return false;
    const [start, end] = window;
    return currentHour >= start && currentHour < end;
  }

  /**
   * Get Active App Name
   * 获取当前活动的应用名称
   */
  private getActiveAppName(): string | null {
    // 从全局状态或路由获取当前活动应用
    try {
      // 这里需要根据实际的路由实现调整
      // 例如: return router.getCurrentApp();
      return null;
    } catch (error) {
      return null;
    }
  }

  /**
   * Check System Resource Availability
   * 检查系统资源可用性
   * @param priority 预加载优先级
   */
  private isSystemResourceAvailable(priority: 'low' | 'medium' | 'high'): boolean {
    // 检查网络状态
    if (navigator.onLine === false) {
      return priority === 'high'; // 离线状态下只允许高优先级预加载
    }
    
    // 检查电池状态
    if ('getBattery' in navigator) {
      // 这里可以添加电池状态检查逻辑
    }
    
    // 低优先级预加载在网络状态不佳时可能被延迟
    if (priority === 'low' && navigator.connection && 
        (navigator.connection.saveData || 
         navigator.connection.effectiveType === '2g')) {
      return false;
    }
    
    return true;
  }

  /**
   * Adjust Concurrency By Network
   * 根据网络状况调整并发数
   * @param baseConcurrency 基础并发数
   * @param priority 预加载优先级
   */
  private adjustConcurrencyByNetwork(baseConcurrency: number, priority: 'low' | 'medium' | 'high'): number {
    if (!navigator.connection) return baseConcurrency;
    
    const { effectiveType } = navigator.connection;
    
    switch (effectiveType) {
      case '4g':
        return baseConcurrency; // 4G网络保持原并发数
      case '3g':
        return Math.max(1, baseConcurrency - 1); // 3G网络降低并发数
      case '2g':
        return 1; // 2G网络只允许串行加载
      default:
        return Math.max(1, baseConcurrency - 1);
    }
  }

  /**
   * Check if App is Preloaded
   * 检查应用是否已预加载
   * @param appName 应用名称
   */
  isPreloaded(appName: string): boolean {
    return this.preloadedApps.has(appName);
  }

  /**
   * Check if App is Preloading
   * 检查应用是否正在预加载
   * @param appName 应用名称
   */
  isPreloading(appName: string): boolean {
    return this.preloadingApps.has(appName);
  }

  /**
   * Clear Preload Cache
   * 清除预加载缓存
   * @param appName 应用名称，如果不指定则清除所有
   */
  clearCache(appName?: string): void {
    if (appName) {
      this.preloadedApps.delete(appName);
      this.preloadMetrics.delete(appName);
    } else {
      this.preloadedApps.clear();
      this.preloadMetrics.clear();
    }
  }

  /**
   * Get Preload Metrics
   * 获取预加载指标
   * @param appName 应用名称，如果不指定则返回所有
   */
  getPreloadMetrics(appName?: string): Map<string, any> | undefined {
    if (appName) {
      const metrics = this.preloadMetrics.get(appName);
      return metrics ? new Map([[appName, metrics]]) : undefined;
    }
    return this.preloadMetrics;
  }

  /**
   * Dispose Preloader
   * 清理预加载器资源
   */
  dispose(): void {
    this.preloadedApps.clear();
    this.preloadingApps.clear();
    this.preloadMetrics.clear();
    
    // 移除事件监听器
    if (typeof window !== 'undefined') {
      document.removeEventListener('click', this.handleUserInteraction.bind(this));
    }
  }
}

// 导出预加载管理器实例
export const microAppPreloader = new MicroAppPreloader();
```

### 7.2 Resource Management

资源管理机制可以有效控制和优化微应用的资源使用，包括响应缓存、网络请求优化、内存管理等方面，提升整体性能和用户体验。

```typescript
/**
 * Response Cache Manager
 * 响应缓存管理器 - 智能缓存API响应数据，减少重复请求，提升性能
 */
export class ResponseCacheManager {
  private cache: Map<string, {
    data: any;
    timestamp: number;
    ttl: number;
    size: number;
    hitCount: number;
    tags?: string[];
  }>;
  private maxSize: number;
  private defaultTTL: number;
  private totalSize: number;
  private memoryLimit: number;
  private cleanupInterval: NodeJS.Timeout | null;

  /**
   * Constructor
   * @param options 配置选项
   */
  constructor(options?: {
    maxSize?: number;        // 最大缓存条目数
    defaultTTL?: number;     // 默认过期时间（毫秒）
    memoryLimit?: number;    // 内存限制（字节），默认10MB
    cleanupInterval?: number; // 清理间隔（毫秒）
  }) {
    this.cache = new Map();
    this.maxSize = options?.maxSize || 100;
    this.defaultTTL = options?.defaultTTL || 300000; // 默认5分钟
    this.totalSize = 0;
    this.memoryLimit = options?.memoryLimit || 10 * 1024 * 1024; // 默认10MB
    
    // 启动定期清理任务
    this.startCleanupTask(options?.cleanupInterval || 30000);
    
    // 监听页面可见性变化，在页面隐藏时优化缓存
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', this.handleVisibilityChange.bind(this));
    }
  }

  /**
   * Set Cache
   * 缓存响应数据
   * @param key 缓存键
   * @param data 响应数据
   * @param options 缓存选项
   */
  set(key: string, data: any, options?: {
    ttl?: number;     // 过期时间（毫秒）
    tags?: string[];  // 缓存标签，用于批量清理
    priority?: 'low' | 'medium' | 'high'; // 优先级
  }): void {
    // 计算数据大小（近似值）
    const size = this.calculateSize(data);
    
    // 如果当前项大小超过内存限制的50%，不缓存
    if (size > this.memoryLimit * 0.5) {
      console.warn(`缓存项 ${key} 大小超过限制，不进行缓存`);
      return;
    }
    
    // 检查是否需要清理空间
    this.ensureCacheSpace(size, options?.priority || 'medium');

    // 如果已经存在该键，减去旧大小
    const oldItem = this.cache.get(key);
    if (oldItem) {
      this.totalSize -= oldItem.size;
    }

    // 存储新数据
    this.cache.set(key, {
      data,
      timestamp: Date.now(),
      ttl: options?.ttl !== undefined ? options.ttl : this.defaultTTL,
      size,
      hitCount: 0,
      tags: options?.tags
    });
    
    // 增加总大小
    this.totalSize += size;
    
    // 记录缓存统计
    this.recordCacheStats('set');
  }

  /**
   * Get Cache
   * 获取缓存数据
   * @param key 缓存键
   * @param options 获取选项
   */
  get(key: string, options?: {
    staleWhileRevalidate?: boolean; // 允许使用过期数据并在后台刷新
  }): any | null {
    const item = this.cache.get(key);
    if (!item) {
      this.recordCacheStats('miss');
      return null;
    }

    const now = Date.now();
    const isExpired = now - item.timestamp > item.ttl;

    // 检查是否过期
    if (isExpired) {
      if (options?.staleWhileRevalidate) {
        // 允许使用过期数据，并异步刷新缓存
        this.refreshInBackground(key).catch(err => {
          console.warn(`后台刷新缓存失败: ${key}`, err);
        });
        
        // 增加命中计数
        item.hitCount += 1;
        this.recordCacheStats('stale');
        return item.data;
      } else {
        // 删除过期缓存
        this.cache.delete(key);
        this.totalSize -= item.size;
        this.recordCacheStats('miss');
        return null;
      }
    }

    // 缓存有效，增加命中计数
    item.hitCount += 1;
    this.recordCacheStats('hit');
    return item.data;
  }

  /**
   * Clear Cache
   * 清除缓存
   * @param key 缓存键，如果不指定则清除所有
   */
  clear(key?: string): void {
    if (key) {
      const item = this.cache.get(key);
      if (item) {
        this.totalSize -= item.size;
        this.cache.delete(key);
        this.recordCacheStats('clear');
      }
    } else {
      this.cache.clear();
      this.totalSize = 0;
      this.recordCacheStats('clearAll');
    }
  }

  /**
   * Clear Cache by Tags
   * 根据标签清除缓存
   * @param tags 标签数组
   */
  clearByTags(tags: string[]): void {
    const tagsSet = new Set(tags);
    let clearedCount = 0;
    
    for (const [key, item] of this.cache.entries()) {
      if (item.tags && item.tags.some(tag => tagsSet.has(tag))) {
        this.totalSize -= item.size;
        this.cache.delete(key);
        clearedCount++;
      }
    }
    
    if (clearedCount > 0) {
      console.log(`根据标签清除了 ${clearedCount} 个缓存项`);
      this.recordCacheStats('clearByTags');
    }
  }

  /**
   * Get Cache Size
   * 获取缓存大小
   * @returns 缓存大小（条目数）
   */
  size(): number {
    return this.cache.size;
  }

  /**
   * Get Total Memory Usage
   * 获取总内存使用量
   * @returns 内存使用量（字节）
   */
  getMemoryUsage(): number {
    return this.totalSize;
  }

  /**
   * Calculate Size
   * 计算数据大小（近似值）
   * @param data 要计算大小的数据
   * @returns 估计的字节大小
   */
  private calculateSize(data: any): number {
    try {
      // 对于不同类型的数据使用不同的计算方法
      if (typeof data === 'string') {
        return data.length * 2; // UTF-16编码，每个字符2字节
      } else if (data === null || data === undefined) {
        return 0;
      } else if (typeof data === 'object') {
        // 对于对象，使用JSON序列化来估算大小
        const serialized = JSON.stringify(data);
        return serialized.length * 2;
      } else {
        // 基本类型的估算大小
        return 8; // 假设其他类型占用8字节
      }
    } catch (error) {
      // 如果计算失败，返回默认值
      return 1024; // 默认1KB
    }
  }

  /**
   * Ensure Cache Space
   * 确保缓存空间可用
   * @param requiredSize 需要的空间（字节）
   * @param priority 优先级
   */
  private ensureCacheSpace(requiredSize: number, priority: 'low' | 'medium' | 'high'): void {
    // 检查条目数量限制
    while (this.cache.size >= this.maxSize) {
      this.evictLowestPriorityItem();
    }
    
    // 检查内存限制
    while (this.totalSize + requiredSize > this.memoryLimit) {
      this.evictLowestPriorityItem();
    }
  }

  /**
   * Evict Lowest Priority Item
   * 驱逐最低优先级的缓存项
   */
  private evictLowestPriorityItem(): void {
    if (this.cache.size === 0) return;
    
    let evictKey: string | null = null;
    let lowestScore = Infinity;
    
    // 计算每个缓存项的分数，优先驱逐分数低的
    for (const [key, item] of this.cache.entries()) {
      // 基于访问频率、年龄和大小计算分数
      const age = Date.now() - item.timestamp;
      const ageScore = age / 1000; // 转换为秒
      const sizeScore = item.size / 1024; // 转换为KB
      const hitScore = item.hitCount > 0 ? 1 / item.hitCount : 1;
      
      // 综合分数，分数越低越优先被驱逐
      const score = ageScore * 0.4 + sizeScore * 0.4 + hitScore * 0.2;
      
      if (score < lowestScore) {
        lowestScore = score;
        evictKey = key;
      }
    }
    
    // 删除选中的缓存项
    if (evictKey) {
      const item = this.cache.get(evictKey)!;
      this.totalSize -= item.size;
      this.cache.delete(evictKey);
    }
  }

  /**
   * Refresh Cache in Background
   * 后台刷新缓存
   * @param key 缓存键
   */
  private async refreshInBackground(key: string): Promise<void> {
    // 这里需要实现具体的刷新逻辑
    // 通常需要存储原始请求信息才能实现自动刷新
    console.log(`后台刷新缓存: ${key}`);
  }

  /**
   * Cleanup Expired Cache
   * 清理过期缓存
   */
  private cleanup(): void {
    const now = Date.now();
    let removedCount = 0;
    let removedSize = 0;

    for (const [key, item] of this.cache.entries()) {
      if (now - item.timestamp > item.ttl) {
        removedSize += item.size;
        this.cache.delete(key);
        removedCount++;
      }
    }
    
    this.totalSize -= removedSize;
    
    if (removedCount > 0) {
      console.log(`清理了 ${removedCount} 个过期缓存项，释放了 ${(removedSize / 1024).toFixed(2)}KB`);
    }
  }

  /**
   * Start Cleanup Task
   * 启动定期清理任务
   * @param interval 清理间隔（毫秒）
   */
  private startCleanupTask(interval: number): void {
    // 停止之前的清理任务
    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
    }
    
    // 启动新的清理任务
    this.cleanupInterval = setInterval(() => {
      this.cleanup();
    }, interval);
    
    // 确保在页面卸载时清理定时器
    if (typeof window !== 'undefined') {
      window.addEventListener('beforeunload', () => {
        if (this.cleanupInterval) {
          clearInterval(this.cleanupInterval);
        }
      });
    }
  }

  /**
   * Handle Visibility Change
   * 处理页面可见性变化
   */
  private handleVisibilityChange(): void {
    if (document.hidden) {
      // 页面隐藏时，主动清理过期缓存和部分低优先级缓存
      this.cleanup();
      
      // 如果缓存项过多，清理一部分
      const excessCount = this.cache.size - Math.floor(this.maxSize * 0.7);
      if (excessCount > 0) {
        console.log(`页面隐藏，清理 ${excessCount} 个低优先级缓存项`);
        for (let i = 0; i < excessCount; i++) {
          this.evictLowestPriorityItem();
        }
      }
    }
  }

  /**
   * Record Cache Stats
   * 记录缓存统计信息
   * @param event 事件类型
   */
  private recordCacheStats(event: string): void {
    // 这里可以实现缓存统计记录，例如发送到监控系统
    // 为简化实现，暂时只记录日志
    if (process.env.NODE_ENV === 'development') {
      // console.log(`Cache event: ${event}`);
    }
  }

  /**
   * Get Cache Stats
   * 获取缓存统计信息
   */
  getStats(): {
    size: number;
    memoryUsage: number;
    memoryUsagePercent: number;
  } {
    return {
      size: this.cache.size,
      memoryUsage: this.totalSize,
      memoryUsagePercent: (this.totalSize / this.memoryLimit) * 100
    };
  }

  /**
   * Dispose Cache Manager
   * 清理缓存管理器资源
   */
  dispose(): void {
    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
      this.cleanupInterval = null;
    }
    
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', this.handleVisibilityChange.bind(this));
    }
    
    this.cache.clear();
    this.totalSize = 0;
  }
}

// 导出响应缓存管理器实例
export const responseCacheManager = new ResponseCacheManager();

/**
 * Check if Error is Retryable
 * 判断是否为可重试的错误
 * @param error 错误对象
 * @returns 是否可重试
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
  
  // 特定的错误消息也可以重试
  const retryableMessages = [
    'Connection reset',
    'Connection refused',
    'Failed to fetch',
    'Network request failed'
  ];
  
  return retryableMessages.some(message => 
    error.message.toLowerCase().includes(message.toLowerCase())
  );
}

/**
 * Create Enhanced Fetch Interceptor
 * 创建增强的fetch拦截器 - 包含缓存、重试、超时控制等功能
 * @param options 拦截器选项
 * @returns 增强的fetch函数
 */
export function createEnhancedFetchInterceptor(options?: {
  enableCache?: boolean;       // 是否启用缓存
  retryCount?: number;         // 重试次数
  retryDelay?: number;         // 重试延迟（毫秒）
  timeout?: number;            // 请求超时（毫秒）
  requestTransformer?: (url: string, config: RequestInit) => [string, RequestInit]; // 请求转换器
  responseTransformer?: (data: any) => any; // 响应转换器
  onRequestStart?: (url: string, config: RequestInit) => void; // 请求开始回调
  onRequestEnd?: (url: string, config: RequestInit, data: any, error?: Error) => void; // 请求结束回调
}) {
  const { 
    enableCache = true, 
    retryCount = 3, 
    retryDelay = 1000,
    timeout = 30000,
    requestTransformer,
    responseTransformer,
    onRequestStart,
    onRequestEnd
  } = options || {};

  /**
   * Timeout Wrapper
   * 超时包装器
   */
  const withTimeout = <T>(promise: Promise<T>, ms: number): Promise<T> => {
    return new Promise((resolve, reject) => {
      const timeoutId = setTimeout(() => {
        reject(new Error(`Request timeout after ${ms}ms`));
      }, ms);
      
      promise
        .then(resolve)
        .catch(reject)
        .finally(() => clearTimeout(timeoutId));
    });
  };

  /**
   * Request Cache Metadata
   * 请求缓存元数据 - 存储原始请求信息用于刷新缓存
   */
  const requestCacheMetadata = new Map<string, { url: string; config: RequestInit }>();

  return async (fetch: Function, url: string, config: RequestInit = {}): Promise<any> => {
    // 转换请求（如果配置了转换器）
    let transformedUrl = url;
    let transformedConfig = { ...config };
    
    if (requestTransformer) {
      [transformedUrl, transformedConfig] = requestTransformer(url, config);
    }
    
    // 构建缓存键
    const cacheKey = `${transformedUrl}_${JSON.stringify(transformedConfig)}`;
    
    // 触发请求开始回调
    if (onRequestStart) {
      try {
        onRequestStart(transformedUrl, transformedConfig);
      } catch (err) {
        console.error('Request start callback error:', err);
      }
    }
    
    const startTime = performance.now();
    let error: Error | undefined;
    
    try {
      // 如果启用缓存且是GET请求，尝试从缓存获取
      if (enableCache && transformedConfig.method?.toUpperCase() === 'GET') {
        const cachedData = responseCacheManager.get(cacheKey, {
          staleWhileRevalidate: true
        });
        
        if (cachedData) {
          console.log(`从缓存获取: ${transformedUrl}`);
          
          // 触发请求结束回调
          if (onRequestEnd) {
            try {
              onRequestEnd(transformedUrl, transformedConfig, cachedData);
            } catch (err) {
              console.error('Request end callback error:', err);
            }
          }
          
          // 转换响应（如果配置了转换器）
          return responseTransformer ? responseTransformer(cachedData) : cachedData;
        }
      }

      // 重试逻辑
      let lastError: Error | null = null;
      
      for (let attempt = 0; attempt <= retryCount; attempt++) {
        try {
          // 执行fetch请求（带超时控制）
          const response = await withTimeout(
            fetch(transformedUrl, transformedConfig),
            timeout
          );
          
          // 检查响应状态
          if (!response.ok) {
            const errorMessage = `HTTP error! status: ${response.status}`;
            const error = new Error(errorMessage);
            (error as any).status = response.status;
            throw error;
          }
          
          // 解析响应数据
          let data;
          const contentType = response.headers.get('content-type');
          
          if (contentType && contentType.includes('application/json')) {
            data = await response.json();
          } else {
            data = await response.text();
          }
          
          // 如果启用缓存且是GET请求，缓存响应
          if (enableCache && transformedConfig.method?.toUpperCase() === 'GET') {
            // 获取缓存控制头
            const cacheControl = response.headers.get('cache-control') || '';
            let ttl = 300000; // 默认5分钟
            
            // 解析Cache-Control头
            if (cacheControl.includes('max-age=')) {
              const maxAgeMatch = cacheControl.match(/max-age=(\d+)/);
              if (maxAgeMatch && maxAgeMatch[1]) {
                ttl = parseInt(maxAgeMatch[1], 10) * 1000; // 转换为毫秒
              }
            }
            
            // 存储请求元数据用于缓存刷新
            requestCacheMetadata.set(cacheKey, { url: transformedUrl, config: transformedConfig });
            
            // 缓存响应数据
            responseCacheManager.set(cacheKey, data, {
              ttl,
              tags: [transformedUrl.split('/')[2]], // 使用域名作为标签
              priority: 'medium'
            });
          }
          
          // 转换响应（如果配置了转换器）
          const finalData = responseTransformer ? responseTransformer(data) : data;
          
          // 触发请求结束回调
          if (onRequestEnd) {
            try {
              onRequestEnd(transformedUrl, transformedConfig, finalData);
            } catch (err) {
              console.error('Request end callback error:', err);
            }
          }
          
          return finalData;
        } catch (err) {
          lastError = err as Error;
          error = lastError;
          
          // 判断是否可以重试
          if (!isRetryableError(lastError) || attempt === retryCount) {
            throw lastError;
          }
          
          // 等待后重试，使用指数退避策略
          const delay = Math.min(
            retryDelay * Math.pow(2, attempt) + Math.random() * 100, // 添加随机抖动
            10000 // 最大延迟10秒
          );
          
          console.log(`请求失败，${delay.toFixed(0)}ms后重试 (${attempt + 1}/${retryCount}): ${transformedUrl}`);
          
          // 记录性能指标
          const attemptTime = performance.now() - startTime;
          globalEventBus.emit(AppEvents.REQUEST_RETRY, {
            url: transformedUrl,
            attempt: attempt + 1,
            maxRetries: retryCount,
            error: lastError.message,
            timeElapsed: attemptTime
          });
          
          await new Promise(resolve => setTimeout(resolve, delay));
        }
      }
      
      // 如果所有重试都失败，抛出最后一个错误
      throw lastError!;
    } catch (err) {
      error = err as Error;
      
      // 触发请求结束回调（带错误）
      if (onRequestEnd) {
        try {
          onRequestEnd(transformedUrl, transformedConfig, null, error);
        } catch (callbackErr) {
          console.error('Request end callback error:', callbackErr);
        }
      }
      
      // 记录请求失败事件
      globalEventBus.emit(AppEvents.REQUEST_ERROR, {
        url: transformedUrl,
        error: error.message,
        timeElapsed: performance.now() - startTime
      });
      
      throw error;
    }
  };
}

/**
 * Memory Usage Monitor
 * 内存使用监控器 - 监控并优化微应用的内存使用
 */
export class MemoryUsageMonitor {
  private appMemoryUsage: Map<string, {
    initial: number;
    current: number;
    peak: number;
    timestamps: Array<{ timestamp: number; usage: number }>;
  }>;
  private monitoringInterval: NodeJS.Timeout | null;
  private memoryWarningThreshold: number; // 内存警告阈值（MB）
  private memoryLimit: number; // 内存限制（MB）

  constructor(options?: {
    memoryWarningThreshold?: number; // 内存警告阈值（MB）
    memoryLimit?: number; // 内存限制（MB）
    monitoringInterval?: number; // 监控间隔（毫秒）
  }) {
    this.appMemoryUsage = new Map();
    this.memoryWarningThreshold = options?.memoryWarningThreshold || 500; // 默认500MB
    this.memoryLimit = options?.memoryLimit || 1000; // 默认1GB
    
    // 启动内存监控（仅在支持的环境中）
    if (typeof performance !== 'undefined' && 'memory' in performance) {
      this.startMonitoring(options?.monitoringInterval || 60000); // 默认每分钟监控一次
    }
  }

  /**
   * Start Monitoring
   * 开始内存监控
   * @param interval 监控间隔（毫秒）
   */
  private startMonitoring(interval: number): void {
    this.monitoringInterval = setInterval(() => {
      this.checkMemoryUsage();
    }, interval);
    
    // 确保在页面卸载时清理定时器
    if (typeof window !== 'undefined') {
      window.addEventListener('beforeunload', () => {
        if (this.monitoringInterval) {
          clearInterval(this.monitoringInterval);
        }
      });
    }
  }

  /**
   * Check Memory Usage
   * 检查内存使用情况
   */
  private checkMemoryUsage(): void {
    if (!('memory' in performance)) return;
    
    const memoryInfo = (performance as any).memory;
    const usedMemoryMB = memoryInfo.usedJSHeapSize / (1024 * 1024);
    
    console.log(`当前内存使用: ${usedMemoryMB.toFixed(2)}MB / ${(memoryInfo.jsHeapSizeLimit / (1024 * 1024)).toFixed(2)}MB`);
    
    // 检查是否超过警告阈值
    if (usedMemoryMB > this.memoryWarningThreshold) {
      globalEventBus.emit(AppEvents.MEMORY_WARNING, {
        usage: usedMemoryMB,
        threshold: this.memoryWarningThreshold
      });
    }
    
    // 检查是否超过限制
    if (usedMemoryMB > this.memoryLimit) {
      globalEventBus.emit(AppEvents.MEMORY_LIMIT_EXCEEDED, {
        usage: usedMemoryMB,
        limit: this.memoryLimit
      });
      
      // 触发内存优化
      this.triggerMemoryOptimization();
    }
  }

  /**
   * Trigger Memory Optimization
   * 触发内存优化
   */
  private triggerMemoryOptimization(): void {
    console.warn('内存使用超过限制，触发优化');
    
    // 清理缓存
    responseCacheManager.clear();
    
    // 触发全局内存优化事件
    globalEventBus.emit(AppEvents.TRIGGER_MEMORY_OPTIMIZATION);
  }

  /**
   * Track App Memory Usage
   * 跟踪应用内存使用
   * @param appName 应用名称
   */
  trackAppMemoryUsage(appName: string): void {
    if (!('memory' in performance)) return;
    
    const memoryInfo = (performance as any).memory;
    const usedMemoryMB = memoryInfo.usedJSHeapSize / (1024 * 1024);
    
    if (!this.appMemoryUsage.has(appName)) {
      this.appMemoryUsage.set(appName, {
        initial: usedMemoryMB,
        current: usedMemoryMB,
        peak: usedMemoryMB,
        timestamps: [{ timestamp: Date.now(), usage: usedMemoryMB }]
      });
    } else {
      const stats = this.appMemoryUsage.get(appName)!;
      stats.current = usedMemoryMB;
      stats.peak = Math.max(stats.peak, usedMemoryMB);
      stats.timestamps.push({ timestamp: Date.now(), usage: usedMemoryMB });
      
      // 只保留最近100个时间点的数据
      if (stats.timestamps.length > 100) {
        stats.timestamps.shift();
      }
    }
  }

  /**
   * Get App Memory Usage
   * 获取应用内存使用情况
   * @param appName 应用名称
   */
  getAppMemoryUsage(appName: string): {
    initial: number;
    current: number;
    peak: number;
    growth: number;
  } | undefined {
    const stats = this.appMemoryUsage.get(appName);
    if (!stats) return undefined;
    
    return {
      initial: stats.initial,
      current: stats.current,
      peak: stats.peak,
      growth: stats.current - stats.initial
    };
  }

  /**
   * Dispose Monitor
   * 清理监控器资源
   */
  dispose(): void {
    if (this.monitoringInterval) {
      clearInterval(this.monitoringInterval);
      this.monitoringInterval = null;
    }
    
    this.appMemoryUsage.clear();
  }
}

// 导出内存监控器实例
export const memoryMonitor = new MemoryMonitor();

事件总线支持发布-订阅模式，提供类型安全的事件定义和处理机制。

```typescript
// 事件总线接口定义
interface EventBus {
  // 订阅事件
  on<T extends EventType>(event: T, handler: EventHandler<T>): Subscription;
  // 发布事件
  emit<T extends EventType>(event: T, payload: EventPayload<T>): void;
  // 取消订阅
  off<T extends EventType>(event: T, handler?: EventHandler<T>): void;
  // 清除所有订阅
  clear(): void;
}
```

### 4.3.3 Cross-Application State Sharing / 跨应用状态共享

跨应用状态共享机制为微前端架构提供了一种在多个微应用间安全、高效地共享全局状态的解决方案，避免了重复数据获取和状态不一致的问题。

#### 设计原则

- **单向数据流**：遵循与Redux相似的单向数据流模式，确保状态变更可追踪
- **类型安全**：使用TypeScript泛型确保状态访问的类型安全
- **响应式更新**：当共享状态变更时，所有订阅的微应用自动获得更新
- **隔离性**：支持按模块隔离状态，避免命名冲突
- **性能优化**：实现状态的精细订阅和更新，避免不必要的重渲染

#### 实现方案

```typescript
/**
 * 共享状态项接口
 */
export interface SharedStateItem<T = any> {
  value: T;
  subscribers: Set<(value: T) => void>;
  lastUpdated: number;
}

/**
 * 跨应用状态管理器
 */
export class CrossAppStateManager {
  private static instance: CrossAppStateManager;
  private stateMap: Map<string, SharedStateItem> = new Map();
  private eventBus: MicroAppEventBus;
  private readonly prefix = '@shared_state/';
  private readonly maxHistorySize = 100;
  private stateHistory: Array<{key: string; value: any; timestamp: number}> = [];
  
  /**
   * 获取单例实例
   */
  public static getInstance(eventBus: MicroAppEventBus = globalEventBus): CrossAppStateManager {
    if (!CrossAppStateManager.instance) {
      CrossAppStateManager.instance = new CrossAppStateManager(eventBus);
    }
    return CrossAppStateManager.instance;
  }
  
  /**
   * 私有构造函数
   */
  private constructor(eventBus: MicroAppEventBus) {
    this.eventBus = eventBus;
    this.setupEventListeners();
  }
  
  /**
   * 设置事件监听器，处理跨应用状态同步
   */
  private setupEventListeners(): void {
    // 监听来自其他应用的状态更新事件
    this.eventBus.on(AppEvents.SHARED_STATE_UPDATE, (data: {key: string; value: any; sender: string}) => {
      // 防止循环更新：如果状态更新源自当前应用，则不处理
      if (data.sender !== this.getCurrentAppName()) {
        this.updateStateInternal(data.key, data.value, false);
      }
    });
    
    // 监听状态订阅请求
    this.eventBus.on(AppEvents.SHARED_STATE_SUBSCRIBE, (data: {key: string; sender: string}) => {
      // 当有新应用订阅状态时，立即发送当前状态给该应用
      const stateItem = this.stateMap.get(data.key);
      if (stateItem) {
        this.eventBus.emit(AppEvents.SHARED_STATE_SYNC, {
          key: data.key,
          value: stateItem.value,
          target: data.sender
        });
      }
    });
    
    // 监听状态同步消息
    this.eventBus.on(AppEvents.SHARED_STATE_SYNC, (data: {key: string; value: any; target?: string}) => {
      // 如果有指定目标且不是当前应用，则忽略
      if (data.target && data.target !== this.getCurrentAppName()) {
        return;
      }
      this.updateStateInternal(data.key, data.value, false);
    });
  }
  
  /**
   * 获取当前应用名称
   */
  private getCurrentAppName(): string {
    if (typeof window !== 'undefined' && window.__WUJIE_APPNAME__) {
      return window.__WUJIE_APPNAME__;
    }
    return 'main';
  }
  
  /**
   * 内部状态更新方法
   */
  private updateStateInternal<T>(key: string, value: T, broadcast: boolean = true): void {
    const now = Date.now();
    
    // 获取或创建状态项
    let stateItem = this.stateMap.get(key);
    if (!stateItem) {
      stateItem = {
        value,
        subscribers: new Set(),
        lastUpdated: now
      };
      this.stateMap.set(key, stateItem);
    } else {
      // 检查值是否实际发生变化
      if (JSON.stringify(stateItem.value) === JSON.stringify(value)) {
        return;
      }
      
      stateItem.value = value;
      stateItem.lastUpdated = now;
    }
    
    // 更新历史记录
    this.stateHistory.push({key, value, timestamp: now});
    if (this.stateHistory.length > this.maxHistorySize) {
      this.stateHistory.shift();
    }
    
    // 通知本地订阅者
    stateItem.subscribers.forEach(subscriber => {
      try {
        subscriber(value);
      } catch (error) {
        console.error(`执行状态订阅回调时出错 [${key}]:`, error);
      }
    });
    
    // 广播状态更新到其他应用
    if (broadcast) {
      this.eventBus.emit(AppEvents.SHARED_STATE_UPDATE, {
        key,
        value,
        sender: this.getCurrentAppName()
      });
    }
  }
  
  /**
   * 设置共享状态
   */
  public setState<T>(key: string, value: T): void {
    this.updateStateInternal(key, value);
  }
  
  /**
   * 获取共享状态
   */
  public getState<T>(key: string): T | undefined {
    const stateItem = this.stateMap.get(key);
    return stateItem ? stateItem.value as T : undefined;
  }
  
  /**
   * 订阅共享状态变化
   */
  public subscribe<T>(key: string, callback: (value: T) => void): () => void {
    // 确保状态项存在
    let stateItem = this.stateMap.get(key);
    if (!stateItem) {
      stateItem = {
        value: undefined,
        subscribers: new Set(),
        lastUpdated: Date.now()
      };
      this.stateMap.set(key, stateItem);
    }
    
    // 添加订阅者
    stateItem.subscribers.add(callback as (value: any) => void);
    
    // 发送订阅请求，获取最新状态
    this.eventBus.emit(AppEvents.SHARED_STATE_SUBSCRIBE, {
      key,
      sender: this.getCurrentAppName()
    });
    
    // 返回取消订阅函数
    return () => {
      stateItem?.subscribers.delete(callback as (value: any) => void);
      // 如果没有订阅者了，清理状态项
      if (stateItem && stateItem.subscribers.size === 0) {
        this.stateMap.delete(key);
      }
    };
  }
  
  /**
   * 删除共享状态
   */
  public removeState(key: string): void {
    const stateItem = this.stateMap.get(key);
    if (stateItem) {
      // 通知所有订阅者状态已被移除
      stateItem.subscribers.forEach(subscriber => {
        try {
          subscriber(undefined);
        } catch (error) {
          console.error(`执行状态移除回调时出错 [${key}]:`, error);
        }
      });
      
      this.stateMap.delete(key);
      
      // 广播状态移除事件
      this.eventBus.emit(AppEvents.SHARED_STATE_REMOVE, {
        key,
        sender: this.getCurrentAppName()
      });
    }
  }
  
  /**
   * 清除所有共享状态
   */
  public clearAllState(): void {
    // 清除所有本地状态
    this.stateMap.clear();
    this.stateHistory = [];
    
    // 广播清除事件
    this.eventBus.emit(AppEvents.SHARED_STATE_CLEAR_ALL, {
      sender: this.getCurrentAppName()
    });
  }
  
  /**
   * 获取所有已注册的状态键
   */
  public getAllStateKeys(): string[] {
    return Array.from(this.stateMap.keys());
  }
}

// 导出全局状态管理器实例
export const crossAppState = CrossAppStateManager.getInstance();

/**
 * 为React组件提供的自定义Hook，方便订阅共享状态
 */
export function useSharedState<T>(key: string, defaultValue?: T): [T, (value: T) => void] {
  const [value, setValue] = React.useState<T>(() => {
    const currentValue = crossAppState.getState<T>(key);
    return currentValue !== undefined ? currentValue : defaultValue as T;
  });
  
  React.useEffect(() => {
    // 订阅状态变化
    const unsubscribe = crossAppState.subscribe<T>(key, (newValue) => {
      setValue(newValue !== undefined ? newValue : defaultValue as T);
    });
    
    // 组件卸载时取消订阅
    return () => unsubscribe();
  }, [key, defaultValue]);
  
  // 创建更新状态的函数
  const updateState = React.useCallback((newValue: T) => {
    crossAppState.setState(key, newValue);
  }, [key]);
  
  return [value, updateState];
}
```

#### 使用示例

```typescript
// 在主应用中设置全局用户信息
import { crossAppState } from 'micro-frontend-sdk';

// 登录成功后设置用户信息
function handleLoginSuccess(userInfo) {
  crossAppState.setState('globalUser', userInfo);
}

// 在微应用中订阅用户信息
import { useSharedState } from 'micro-frontend-sdk';

function UserProfile() {
  // 使用自定义Hook订阅共享状态
  const [user, setUser] = useSharedState('globalUser', { name: 'Guest' });
  
  return (
    <div>
      <h2>用户信息</h2>
      <p>用户名: {user.name}</p>
      <p>角色: {user.role}</p>
      {/* 可以直接更新共享状态 */}
      <button onClick={() => setUser({...user, lastActive: new Date().toISOString()})}>
        更新活动时间
      </button>
    </div>
  );
}
```

#### 高级特性

1. **模块命名空间**：通过命名约定实现状态模块化，如`user.profile`、`settings.theme`
2. **状态持久化**：支持将关键共享状态保存到localStorage或sessionStorage
3. **状态验证**：可集成schema验证，确保设置的状态符合预期格式
4. **性能优化**：通过选择性订阅和批量更新机制减少不必要的重渲染
5. **调试工具**：提供状态变更日志和时间旅行调试能力

通过这种跨应用状态共享机制，微前端架构中的各应用可以高效协同工作，同时保持良好的隔离性和可维护性。

---

## ⚡ **性能优化策略** / Performance Optimization

性能优化是微前端架构中的关键考量因素，由于微应用的动态加载特性，需要采用一系列优化策略确保应用的快速响应和流畅体验。本节详细介绍Bone平台实现的性能优化方案。

### 5.1 Loading Optimization / 加载优化

加载优化聚焦于减少应用初始加载时间和微应用切换时间，采用智能预加载、按需加载和多层缓存策略。

#### 5.1.1 智能预加载机制

基于用户行为预测和访问模式分析，在适当时机预加载可能需要的微应用资源。

```typescript
/**
 * 智能预加载管理器
 */
export class PreloadManager {
  private static instance: PreloadManager;
  private appRegistry: Map<string, MicroAppConfig>;
  private preloadQueue: Array<{ appName: string; priority: number }> = [];
  private preloadedApps: Set<string> = new Set();
  private isPreloading: boolean = false;
  private readonly maxConcurrentPreloads = 3;
  private readonly preloadThreshold = 0.7; // 网络空闲度阈值
  
  /**
   * 获取单例实例
   */
  public static getInstance(appRegistry: Map<string, MicroAppConfig>): PreloadManager {
    if (!PreloadManager.instance) {
      PreloadManager.instance = new PreloadManager(appRegistry);
    }
    return PreloadManager.instance;
  }
  
  private constructor(appRegistry: Map<string, MicroAppConfig>) {
    this.appRegistry = appRegistry;
    this.setupNetworkMonitor();
  }
  
  /**
   * 设置网络监视器，根据网络状态调整预加载策略
   */
  private setupNetworkMonitor(): void {
    if ('connection' in navigator) {
      const connection = navigator.connection as any;
      const updatePreloadStrategy = () => {
        const effectiveType = connection.effectiveType; // 2g, 3g, 4g
        const saveData = connection.saveData; // 用户是否开启了省流量模式
        
        // 根据网络状况动态调整预加载策略
        if (effectiveType === '2g' || saveData) {
          this.maxConcurrentPreloads = 1;
          this.preloadThreshold = 0.9;
        } else if (effectiveType === '3g') {
          this.maxConcurrentPreloads = 2;
          this.preloadThreshold = 0.8;
        } else {
          this.maxConcurrentPreloads = 3;
          this.preloadThreshold = 0.7;
        }
      };
      
      connection.addEventListener('change', updatePreloadStrategy);
      updatePreloadStrategy();
    }
  }
  
  /**
   * 添加应用到预加载队列
   */
  public queueForPreload(appName: string, priority: number = 1): void {
    if (!this.appRegistry.has(appName) || this.preloadedApps.has(appName)) {
      return;
    }
    
    // 检查是否已经在队列中
    const existingIndex = this.preloadQueue.findIndex(item => item.appName === appName);
    if (existingIndex >= 0) {
      // 更新优先级
      if (this.preloadQueue[existingIndex].priority < priority) {
        this.preloadQueue[existingIndex].priority = priority;
        // 重新排序队列
        this.preloadQueue.sort((a, b) => b.priority - a.priority);
      }
      return;
    }
    
    this.preloadQueue.push({ appName, priority });
    // 按优先级排序
    this.preloadQueue.sort((a, b) => b.priority - a.priority);
    
    // 尝试开始预加载
    this.tryPreload();
  }
  
  /**
   * 尝试开始预加载
   */
  private async tryPreload(): Promise<void> {
    if (this.isPreloading || this.preloadQueue.length === 0) {
      return;
    }
    
    this.isPreloading = true;
    
    try {
      // 检查网络空闲状态
      if ('requestIdleCallback' in window) {
        await new Promise<void>(resolve => {
          (window as any).requestIdleCallback((deadline: any) => {
            // 只有当剩余时间足够且网络空闲度达到阈值时才进行预加载
            if (deadline.timeRemaining() > 50 && this.checkNetworkIdle()) {
              resolve();
            } else {
              // 推迟到下次空闲时间
              setTimeout(() => this.tryPreload(), 500);
              resolve();
            }
          }, { timeout: 2000 });
        });
      }
      
      // 执行预加载
      await this.executePreloads();
    } catch (error) {
      console.error('预加载过程中发生错误:', error);
    } finally {
      this.isPreloading = false;
      
      // 尝试预加载下一批
      if (this.preloadQueue.length > 0) {
        setTimeout(() => this.tryPreload(), 100);
      }
    }
  }
  
  /**
   * 执行预加载
   */
  private async executePreloads(): Promise<void> {
    const concurrentPreloads = Math.min(this.maxConcurrentPreloads, this.preloadQueue.length);
    const appsToPreload = this.preloadQueue.splice(0, concurrentPreloads);
    
    const preloadPromises = appsToPreload.map(async ({ appName }) => {
      try {
        const appConfig = this.appRegistry.get(appName);
        if (!appConfig) return;
        
        // 预加载微应用资源
        await this.preloadAppResources(appConfig);
        
        // 标记为已预加载
        this.preloadedApps.add(appName);
        console.log(`微应用 ${appName} 预加载完成`);
      } catch (error) {
        console.warn(`微应用 ${appName} 预加载失败:`, error);
        // 预加载失败，重新加入队列但降低优先级
        this.queueForPreload(appName, 0.5);
      }
    });
    
    await Promise.allSettled(preloadPromises);
  }
  
  /**
   * 预加载应用资源
   */
  private async preloadAppResources(appConfig: MicroAppConfig): Promise<void> {
    const { entry, cssList = [], jsList = [] } = appConfig;
    
    // 预加载CSS资源
    const cssPromises = cssList.map(href => {
      return new Promise<void>((resolve, reject) => {
        const link = document.createElement('link');
        link.rel = 'preload';
        link.as = 'style';
        link.href = href;
        link.onload = () => resolve();
        link.onerror = () => reject(new Error(`Failed to preload CSS: ${href}`));
        document.head.appendChild(link);
      });
    });
    
    // 预加载JavaScript资源
    const jsPromises = jsList.map(src => {
      return new Promise<void>((resolve, reject) => {
        const script = document.createElement('script');
        script.rel = 'preload';
        script.as = 'script';
        script.src = src;
        script.onload = () => resolve();
        script.onerror = () => reject(new Error(`Failed to preload JS: ${src}`));
        document.head.appendChild(script);
      });
    });
    
    // 预加载HTML入口
    if (entry) {
      try {
        const response = await fetch(entry, {
          method: 'GET',
          mode: 'cors',
          credentials: 'include',
          cache: 'force-cache'
        });
        if (!response.ok) throw new Error(`Failed to preload entry: ${entry}`);
        // 仅预加载，不解析
        await response.text();
      } catch (error) {
        console.warn(`预加载入口文件失败: ${entry}`, error);
      }
    }
    
    await Promise.allSettled([...cssPromises, ...jsPromises]);
  }
  
  /**
   * 检查网络空闲状态
   */
  private checkNetworkIdle(): boolean {
    // 简单实现：检查最近是否有活跃的网络请求
    // 实际项目中可以集成Performance API或自定义网络监控
    return true; // 简化实现，实际应返回真实网络空闲度
  }
  
  /**
   * 清除预加载状态
   */
  public clearPreloadState(appName?: string): void {
    if (appName) {
      this.preloadedApps.delete(appName);
      // 从队列中移除
      const index = this.preloadQueue.findIndex(item => item.appName === appName);
      if (index >= 0) {
        this.preloadQueue.splice(index, 1);
      }
    } else {
      this.preloadedApps.clear();
      this.preloadQueue = [];
    }
  }
}
```

#### 5.1.2 资源缓存策略

实现多层缓存机制，包括HTTP缓存、内存缓存和IndexedDB缓存，减少重复资源加载。

```typescript
/**
 * 资源缓存管理器
 */
export class ResourceCacheManager {
  private static instance: ResourceCacheManager;
  private memoryCache: Map<string, { data: any; timestamp: number; ttl: number }> = new Map();
  private dbName = 'bone-resource-cache';
  private dbVersion = 1;
  private db: IDBDatabase | null = null;
  private dbReadyPromise: Promise<void> | null = null;
  
  /**
   * 获取单例实例
   */
  public static getInstance(): ResourceCacheManager {
    if (!ResourceCacheManager.instance) {
      ResourceCacheManager.instance = new ResourceCacheManager();
    }
    return ResourceCacheManager.instance;
  }
  
  private constructor() {
    this.initDatabase();
  }
  
  /**
   * 初始化IndexedDB数据库
   */
  private initDatabase(): void {
    this.dbReadyPromise = new Promise((resolve, reject) => {
      if (!('indexedDB' in window)) {
        console.warn('当前浏览器不支持IndexedDB，无法使用持久化缓存');
        resolve();
        return;
      }
      
      const request = indexedDB.open(this.dbName, this.dbVersion);
      
      request.onupgradeneeded = (event) => {
        const db = (event.target as IDBOpenDBRequest).result;
        // 创建资源缓存存储
        if (!db.objectStoreNames.contains('resources')) {
          const store = db.createObjectStore('resources', { keyPath: 'url' });
          store.createIndex('timestamp', 'timestamp', { unique: false });
        }
        // 创建元数据存储
        if (!db.objectStoreNames.contains('metadata')) {
          db.createObjectStore('metadata', { keyPath: 'id' });
        }
      };
      
      request.onsuccess = (event) => {
        this.db = (event.target as IDBOpenDBRequest).result;
        // 设置缓存限制
        this.setCacheLimit(50 * 1024 * 1024); // 50MB
        resolve();
      };
      
      request.onerror = (event) => {
        console.error('打开IndexedDB失败:', (event.target as IDBOpenDBRequest).error);
        resolve(); // 失败时仍继续，使用内存缓存
      };
    });
  }
  
  /**
   * 缓存资源
   */
  public async cacheResource(url: string, data: any, options: { memory?: boolean; disk?: boolean; ttl?: number } = {}): Promise<void> {
    const { memory = true, disk = true, ttl = 3600000 } = options; // 默认TTL为1小时
    const timestamp = Date.now();
    
    // 内存缓存
    if (memory) {
      this.memoryCache.set(url, { data, timestamp, ttl });
      // 限制内存缓存大小
      this.limitMemoryCacheSize(100); // 最多缓存100个资源
    }
    
    // 磁盘缓存
    if (disk && this.db) {
      await this.dbReadyPromise;
      try {
        const transaction = this.db.transaction(['resources'], 'readwrite');
        const store = transaction.objectStore('resources');
        await store.put({ url, data, timestamp, ttl });
        await transactionComplete(transaction);
      } catch (error) {
        console.warn('缓存资源到IndexedDB失败:', error);
      }
    }
  }
  
  /**
   * 获取缓存的资源
   */
  public async getCachedResource(url: string): Promise<any | null> {
    const now = Date.now();
    
    // 先检查内存缓存
    const memoryItem = this.memoryCache.get(url);
    if (memoryItem) {
      // 检查是否过期
      if (now - memoryItem.timestamp < memoryItem.ttl) {
        return memoryItem.data;
      } else {
        // 过期则移除
        this.memoryCache.delete(url);
      }
    }
    
    // 再检查磁盘缓存
    if (this.db) {
      await this.dbReadyPromise;
      try {
        const transaction = this.db.transaction(['resources'], 'readonly');
        const store = transaction.objectStore('resources');
        const item = await storeGet(store, url);
        
        if (item) {
          // 检查是否过期
          if (now - item.timestamp < item.ttl) {
            // 加载到内存缓存
            this.memoryCache.set(url, { data: item.data, timestamp: item.timestamp, ttl: item.ttl });
            return item.data;
          } else {
            // 过期则删除
            const deleteTransaction = this.db.transaction(['resources'], 'readwrite');
            deleteTransaction.objectStore('resources').delete(url);
            await transactionComplete(deleteTransaction);
          }
        }
      } catch (error) {
        console.warn('从IndexedDB获取缓存失败:', error);
      }
    }
    
    return null;
  }
  
  /**
   * 清除指定资源缓存
   */
  public async clearResourceCache(url: string): Promise<void> {
    // 清除内存缓存
    this.memoryCache.delete(url);
    
    // 清除磁盘缓存
    if (this.db) {
      await this.dbReadyPromise;
      try {
        const transaction = this.db.transaction(['resources'], 'readwrite');
        transaction.objectStore('resources').delete(url);
        await transactionComplete(transaction);
      } catch (error) {
        console.warn('从IndexedDB删除缓存失败:', error);
      }
    }
  }
  
  /**
   * 清除所有缓存
   */
  public async clearAllCache(): Promise<void> {
    // 清除内存缓存
    this.memoryCache.clear();
    
    // 清除磁盘缓存
    if (this.db) {
      await this.dbReadyPromise;
      try {
        const transaction = this.db.transaction(['resources'], 'readwrite');
        transaction.objectStore('resources').clear();
        await transactionComplete(transaction);
      } catch (error) {
        console.warn('清除IndexedDB缓存失败:', error);
      }
    }
  }
  
  /**
   * 设置缓存限制
   */
  private async setCacheLimit(maxSizeInBytes: number): Promise<void> {
    if (!this.db) return;
    
    await this.dbReadyPromise;
    try {
      const transaction = this.db.transaction(['metadata'], 'readwrite');
      const store = transaction.objectStore('metadata');
      await store.put({ id: 'cacheLimit', maxSize: maxSizeInBytes });
      await transactionComplete(transaction);
      
      // 检查并清理超出限制的缓存
      this.evictOldCache();
    } catch (error) {
      console.warn('设置缓存限制失败:', error);
    }
  }
  
  /**
   * 清理旧缓存
   */
  private async evictOldCache(): Promise<void> {
    // 实现LRU缓存淘汰策略
    // ...
  }
  
  /**
   * 限制内存缓存大小
   */
  private limitMemoryCacheSize(maxItems: number): void {
    if (this.memoryCache.size <= maxItems) return;
    
    // 按时间戳排序并移除最旧的项目
    const sortedEntries = Array.from(this.memoryCache.entries())
      .sort(([,a], [,b]) => a.timestamp - b.timestamp);
    
    const itemsToRemove = sortedEntries.length - maxItems;
    for (let i = 0; i < itemsToRemove; i++) {
      this.memoryCache.delete(sortedEntries[i][0]);
    }
  }
}

// IndexedDB辅助函数
function transactionComplete(transaction: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error);
  });
}

function storeGet(store: IDBObjectStore, key: string): Promise<any> {
  return new Promise((resolve, reject) => {
    const request = store.get(key);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}
```

### 5.2 Rendering Optimization / 渲染优化

渲染优化专注于提高组件渲染性能，减少不必要的渲染和提升用户交互响应速度。

#### 5.2.1 组件懒加载和代码分割

使用React.lazy和Suspense实现组件的按需加载，减小初始包体积。

```typescript
/**
 * 高级懒加载组件，支持预加载和错误处理
 */
import React, { lazy, Suspense, ComponentType, useState, useEffect, useRef } from 'react';

interface LazyLoadComponentProps {
  fallback?: React.ReactNode;
  preloadOn?: 'visible' | 'hover' | 'immediate';
  placeholder?: React.ReactNode;
}

/**
 * 创建懒加载组件
 */
export function createLazyComponent<T extends ComponentType<any>>(
  importFn: () => Promise<{ default: T }>,
  options?: {
    displayName?: string;
    errorComponent?: ComponentType<{ error: Error; resetError: () => void }>;
  }
) {
  const LazyComponent = lazy(importFn);
  const { displayName = 'LazyComponent', errorComponent: ErrorComponent } = options || {};
  
  const EnhancedLazyComponent: React.FC<LazyLoadComponentProps & React.ComponentProps<T>> = (props) => {
    const { fallback = null, preloadOn = 'immediate', placeholder, ...componentProps } = props;
    const [error, setError] = useState<Error | null>(null);
    const importRef = useRef(importFn);
    const hasLoadedRef = useRef(false);
    const preloadRef = useRef<HTMLDivElement>(null);
    
    // 重置错误状态
    const resetError = () => {
      setError(null);
      // 重试加载
      importRef.current = importFn;
    };
    
    // 预加载逻辑
    useEffect(() => {
      let observer: IntersectionObserver | null = null;
      let hoverHandler: (() => void) | null = null;
      
      const preload = () => {
        if (!hasLoadedRef.current) {
          importRef.current();
          hasLoadedRef.current = true;
        }
      };
      
      switch (preloadOn) {
        case 'visible':
          // 当元素可见时预加载
          if (preloadRef.current && 'IntersectionObserver' in window) {
            observer = new IntersectionObserver(
              (entries) => {
                if (entries[0].isIntersecting) {
                  preload();
                  observer?.disconnect();
                }
              },
              { rootMargin: '200px' } // 提前200px预加载
            );
            observer.observe(preloadRef.current);
          }
          break;
          
        case 'hover':
          // 当鼠标悬停时预加载
          if (preloadRef.current) {
            hoverHandler = () => preload();
            preloadRef.current.addEventListener('mouseenter', hoverHandler);
          }
          break;
          
        case 'immediate':
        default:
          // 立即预加载
          preload();
          break;
      }
      
      return () => {
        if (observer) observer.disconnect();
        if (hoverHandler && preloadRef.current) {
          preloadRef.current.removeEventListener('mouseenter', hoverHandler);
        }
      };
    }, [preloadOn]);
    
    // 错误边界处理
    if (error) {
      if (ErrorComponent) {
        return <ErrorComponent error={error} resetError={resetError} />;
      }
      return (
        <div className="lazy-load-error">
          <p>组件加载失败</p>
          <button onClick={resetError}>重试</button>
        </div>
      );
    }
    
    return (
      <div ref={preloadRef}>
        {placeholder && !hasLoadedRef.current && placeholder}
        <Suspense fallback={fallback || <div className="lazy-load-loading">加载中...</div>}>
          <LazyComponent {...componentProps} />
        </Suspense>
      </div>
    );
  };
  
  EnhancedLazyComponent.displayName = `EnhancedLazy${displayName}`;
  return EnhancedLazyComponent;
}

// 使用示例
const HeavyChartComponent = createLazyComponent(
  () => import('./HeavyChartComponent'),
  {
    displayName: 'HeavyChartComponent',
    errorComponent: ({ error, resetError }) => (
      <div className="chart-error">
        <h3>图表加载失败</h3>
        <p>{error.message}</p>
        <button onClick={resetError}>重新加载</button>
      </div>
    )
  }
);
```

#### 5.2.2 性能优化Hooks

提供一系列自定义Hooks用于优化组件性能和资源使用。

```typescript
import { useMemo, useCallback, useRef, useEffect, useReducer } from 'react';

/**
 * 防抖Hook - 延迟执行函数，直到用户停止操作指定时间
 */
export function useDebounce<T extends (...args: any[]) => any>(
  callback: T,
  delay: number
): (...args: Parameters<T>) => void {
  const timeoutRef = useRef<NodeJS.Timeout>();
  const callbackRef = useRef(callback);
  
  // 更新回调引用
  useEffect(() => {
    callbackRef.current = callback;
  }, [callback]);
  
  const debouncedCallback = useCallback(
    (...args: Parameters<T>) => {
      // 清除之前的定时器
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
      
      // 设置新的定时器
      timeoutRef.current = setTimeout(() => {
        callbackRef.current(...args);
      }, delay);
    },
    [delay]
  );
  
  // 清理定时器
  useEffect(() => {
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, []);
  
  return debouncedCallback;
}

/**
 * 节流Hook - 限制函数在指定时间内最多执行一次
 */
export function useThrottle<T extends (...args: any[]) => any>(
  callback: T,
  limit: number
): (...args: Parameters<T>) => void {
  const inThrottle = useRef(false);
  const callbackRef = useRef(callback);
  
  // 更新回调引用
  useEffect(() => {
    callbackRef.current = callback;
  }, [callback]);
  
  const throttledCallback = useCallback(
    (...args: Parameters<T>) => {
      if (!inThrottle.current) {
        callbackRef.current(...args);
        inThrottle.current = true;
        
        setTimeout(() => {
          inThrottle.current = false;
        }, limit);
      }
    },
    [limit]
  );
  
  return throttledCallback;
}

/**
 * 优化的useState Hook - 避免不必要的更新
 */
export function useOptimizedState<T>(
  initialValue: T | (() => T),
  areEqual?: (prev: T, next: T) => boolean
): [T, (newValue: T | ((prevValue: T) => T)) => void] {
  const [state, setState] = useReducer(
    (prevState: T, newValue: T | ((prevValue: T) => T)): T => {
      const value = newValue instanceof Function ? newValue(prevState) : newValue;
      
      // 使用自定义比较函数或默认的深度比较
      if (areEqual) {
        return areEqual(prevState, value) ? prevState : value;
      }
      
      // 默认的深度比较（简化版）
      if (prevState === value) return prevState;
      
      // 对象/数组的简单深度比较
      if (typeof prevState === 'object' && prevState !== null &&
          typeof value === 'object' && value !== null) {
        // 对于常见的不可变数据操作，使用JSON字符串比较作为简单实现
        // 注意：这不是最高效的方法，生产环境可使用更专业的深度比较库
        return JSON.stringify(prevState) === JSON.stringify(value) ? prevState : value;
      }
      
      return value;
    },
    initialValue
  );
  
  return [state, setState];
}

/**
 * 虚拟滚动Hook - 优化大量数据列表的渲染性能
 */
export function useVirtualScroll<T>(
  items: T[],
  itemHeight: number,
  containerHeight: number,
  options: {
    overscan?: number; // 预渲染的额外项目数
    keyExtractor?: (item: T) => string | number;
  } = {}
) {
  const { overscan = 5, keyExtractor = (item, index) => index } = options;
  const containerRef = useRef<HTMLElement>(null);
  const [scrollTop, setScrollTop] = useState(0);
  
  const visibleCount = Math.ceil(containerHeight / itemHeight);
  const startIndex = Math.max(0, Math.floor(scrollTop / itemHeight) - overscan);
  const endIndex = Math.min(items.length, startIndex + visibleCount + overscan * 2);
  
  const visibleItems = items.slice(startIndex, endIndex);
  const offsetY = startIndex * itemHeight;
  const totalHeight = items.length * itemHeight;
  
  const handleScroll = useCallback(() => {
    if (containerRef.current) {
      setScrollTop(containerRef.current.scrollTop);
    }
  }, []);
  
  useEffect(() => {
    const container = containerRef.current;
    if (container) {
      container.addEventListener('scroll', handleScroll, { passive: true });
      return () => container.removeEventListener('scroll', handleScroll);
    }
  }, [handleScroll]);
  
  const getItemKey = useCallback(
    (item: T, index: number) => {
      return keyExtractor(item, startIndex + index);
    },
    [keyExtractor, startIndex]
  );
  
  return {
    containerRef,
    visibleItems,
    startIndex,
    endIndex,
    offsetY,
    totalHeight,
    getItemKey
  };
}
```

### 5.3 Resource Management / 资源管理

资源管理聚焦于有效利用和释放系统资源，防止内存泄漏，确保应用在长期运行中保持稳定。

#### 5.3.1 内存监控与优化

实现内存使用监控和自动优化机制，在检测到内存压力时执行清理操作。

```typescript
/**
 * 内存管理器
 */
export class MemoryManager {
  private static instance: MemoryManager;
  private memoryUsageHistory: Array<{ timestamp: number; usage: number }> = [];
  private readonly maxHistorySize = 100;
  private readonly memoryThreshold = 0.8; // 80%内存使用率阈值
  private readonly monitoringInterval = 5000; // 监控间隔（毫秒）
  private monitoringTimer: NodeJS.Timeout | null = null;
  private cleanupHandlers: Array<{ priority: number; handler: () => Promise<void> }> = [];
  private isPerformingCleanup = false;
  
  /**
   * 获取单例实例
   */
  public static getInstance(): MemoryManager {
    if (!MemoryManager.instance) {
      MemoryManager.instance = new MemoryManager();
    }
    return MemoryManager.instance;
  }
  
  private constructor() {
    this.startMonitoring();
  }
  
  /**
   * 开始内存监控
   */
  public startMonitoring(): void {
    if (this.monitoringTimer) return;
    
    this.monitoringTimer = setInterval(() => {
      this.checkMemoryUsage();
    }, this.monitoringInterval);
  }
  
  /**
   * 停止内存监控
   */
  public stopMonitoring(): void {
    if (this.monitoringTimer) {
      clearInterval(this.monitoringTimer);
      this.monitoringTimer = null;
    }
  }
  
  /**
   * 检查内存使用情况
   */
  private async checkMemoryUsage(): Promise<void> {
    if (!('performance' in window) || !('memory' in performance)) {
      // 浏览器不支持内存API，使用备选方案
      console.warn('当前浏览器不支持Performance Memory API');
      return;
    }
    
    const memoryInfo = (performance as any).memory;
    const usedMemory = memoryInfo.usedJSHeapSize;
    const totalMemory = memoryInfo.jsHeapSizeLimit;
    const usageRatio = usedMemory / totalMemory;
    
    // 记录内存使用历史
    this.memoryUsageHistory.push({ timestamp: Date.now(), usage: usageRatio });
    if (this.memoryUsageHistory.length > this.maxHistorySize) {
      this.memoryUsageHistory.shift();
    }
    
    console.log(`内存使用率: ${(usageRatio * 100).toFixed(2)}%`);
    
    // 当内存使用率超过阈值时执行清理
    if (usageRatio > this.memoryThreshold && !this.isPerformingCleanup) {
      await this.performMemoryCleanup();
    }
  }
  
  /**
   * 注册内存清理处理器
   */
  public registerCleanupHandler(
    priority: number, // 优先级，数字越小优先级越高
    handler: () => Promise<void>
  ): () => void {
    const cleanupHandler = { priority, handler };
    this.cleanupHandlers.push(cleanupHandler);
    
    // 按优先级排序
    this.cleanupHandlers.sort((a, b) => a.priority - b.priority);
    
    // 返回取消注册函数
    return () => {
      const index = this.cleanupHandlers.indexOf(cleanupHandler);
      if (index >= 0) {
        this.cleanupHandlers.splice(index, 1);
      }
    };
  }
  
  /**
   * 执行内存清理
   */
  private async performMemoryCleanup(): Promise<void> {
    if (this.isPerformingCleanup) return;
    
    this.isPerformingCleanup = true;
    console.log('开始执行内存清理...');
    
    try {
      // 执行所有清理处理器
      for (const { handler, priority } of this.cleanupHandlers) {
        try {
          console.log(`执行优先级 ${priority} 的清理处理器`);
          await handler();
        } catch (error) {
          console.error(`执行清理处理器时出错 (优先级 ${priority}):`, error);
        }
        
        // 检查是否需要继续清理
        if (await this.checkIfCleanupNeeded()) {
          continue;
        } else {
          console.log('内存使用率已恢复到安全水平，停止清理');
          break;
        }
      }
      
      // 强制垃圾回收（如果可用）
      this.forceGarbageCollection();
      
      console.log('内存清理完成');
    } catch (error) {
      console.error('内存清理过程中发生错误:', error);
    } finally {
      this.isPerformingCleanup = false;
    }
  }
  
  /**
   * 检查是否仍需要清理
   */
  private async checkIfCleanupNeeded(): Promise<boolean> {
    if (!('performance' in window) || !('memory' in performance)) {
      return false;
    }
    
    const memoryInfo = (performance as any).memory;
    const usedMemory = memoryInfo.usedJSHeapSize;
    const totalMemory = memoryInfo.jsHeapSizeLimit;
    const usageRatio = usedMemory / totalMemory;
    
    return usageRatio > this.memoryThreshold * 0.9; // 使用略低的阈值避免频繁清理
  }
  
  /**
   * 尝试强制垃圾回收
   */
  private forceGarbageCollection(): void {
    // 在某些浏览器中，可以通过特定方法触发垃圾回收
    // 注意：这通常只在开发环境或特定浏览器中可用
    if (window.gc && typeof window.gc === 'function') {
      try {
        window.gc();
      } catch (error) {
        console.warn('触发垃圾回收失败:', error);
      }
    }
  }
  
  /**
   * 获取内存使用报告
   */
  public getMemoryReport(): {
    currentUsage: number;
    peakUsage: number;
    averageUsage: number;
    history: Array<{ timestamp: number; usage: number }>;
  } {
    if (this.memoryUsageHistory.length === 0) {
      return { currentUsage: 0, peakUsage: 0, averageUsage: 0, history: [] };
    }
    
    const peakUsage = Math.max(...this.memoryUsageHistory.map(item => item.usage));
    const averageUsage = this.memoryUsageHistory.reduce((sum, item) => sum + item.usage, 0) / this.memoryUsageHistory.length;
    const currentUsage = this.memoryUsageHistory[this.memoryUsageHistory.length - 1].usage;
    
    return {
      currentUsage,
      peakUsage,
      averageUsage,
      history: [...this.memoryUsageHistory]
    };
  }
}

// 使用示例
const memoryManager = MemoryManager.getInstance();

// 注册清理处理器
const unregisterImageCacheCleanup = memoryManager.registerCleanupHandler(1, async () => {
  // 清理图片缓存
  console.log('清理图片缓存...');
  // 实现图片缓存清理逻辑
});

const unregisterEventBusCleanup = memoryManager.registerCleanupHandler(2, async () => {
  // 清理未使用的事件监听器
  console.log('清理事件监听器...');
  // 实现事件监听器清理逻辑
});
```

#### 5.3.2 微应用生命周期管理优化

优化微应用的挂载、卸载和资源清理过程，防止内存泄漏和资源占用。

```typescript
/**
 * 增强的微应用生命周期管理器
 */
export class EnhancedAppLifecycleManager {
  private static instance: EnhancedAppLifecycleManager;
  private appInstances: Map<string, {
    instance: any;
    mountedTime: number;
    resources: Set<any>;
    cleanupFunctions: Array<() => void>;
    lastAccessed: number;
    usageCount: number;
  }> = new Map();
  private readonly maxIdleTime = 30 * 60 * 1000; // 30分钟空闲时间后卸载
  private readonly memoryScanningInterval = 60 * 1000; // 每分钟检查一次
  private scanningTimer: NodeJS.Timeout | null = null;
  
  /**
   * 获取单例实例
   */
  public static getInstance(): EnhancedAppLifecycleManager {
    if (!EnhancedAppLifecycleManager.instance) {
      EnhancedAppLifecycleManager.instance = new EnhancedAppLifecycleManager();
    }
    return EnhancedAppLifecycleManager.instance;
  }
  
  private constructor() {
    this.startMemoryScanning();
    this.setupVisibilityChangeHandler();
  }
  
  /**
   * 开始内存扫描
   */
  private startMemoryScanning(): void {
    if (this.scanningTimer) return;
    
    this.scanningTimer = setInterval(() => {
      this.scanIdleApps();
    }, this.memoryScanningInterval);
  }
  
  /**
   * 设置页面可见性变化处理器
   */
  private setupVisibilityChangeHandler(): void {
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) {
        // 页面隐藏时，清理不常用的微应用
        this.cleanupUnusedAppsOnHidden();
      }
    });
  }
  
  /**
   * 注册微应用实例
   */
  public registerAppInstance(appName: string, instance: any): void {
    this.appInstances.set(appName, {
      instance,
      mountedTime: Date.now(),
      resources: new Set(),
      cleanupFunctions: [],
      lastAccessed: Date.now(),
      usageCount: 0
    });
  }
  
  /**
   * 记录微应用访问
   */
  public recordAppAccess(appName: string): void {
    const appInfo = this.appInstances.get(appName);
    if (appInfo) {
      appInfo.lastAccessed = Date.now();
      appInfo.usageCount++;
    }
  }
  
  /**
   * 注册微应用资源
   */
  public registerAppResource(appName: string, resource: any, cleanup?: () => void): void {
    const appInfo = this.appInstances.get(appName);
    if (appInfo) {
      appInfo.resources.add(resource);
      if (cleanup) {
        appInfo.cleanupFunctions.push(cleanup);
      }
    }
  }
  
  /**
   * 扫描并清理空闲应用
   */
  private scanIdleApps(): void {
    const now = Date.now();
    
    this.appInstances.forEach((appInfo, appName) => {
      const idleTime = now - appInfo.lastAccessed;
      
      // 如果应用空闲时间超过阈值且使用频率不高，则卸载
      if (idleTime > this.maxIdleTime && appInfo.usageCount < 5) {
        console.log(`卸载长时间未使用的微应用: ${appName} (空闲时间: ${idleTime / 1000}秒)`);
        this.unloadApp(appName);
      }
    });
  }
  
  /**
   * 页面隐藏时清理不常用的应用
   */
  private cleanupUnusedAppsOnHidden(): void {
    // 保留最近使用的2个应用，卸载其他应用
    const sortedApps = Array.from(this.appInstances.entries())
      .sort(([,a], [,b]) => b.lastAccessed - a.lastAccessed);
    
    const appsToUnload = sortedApps.slice(2);
    appsToUnload.forEach(([appName]) => {
      console.log(`页面隐藏时卸载不常用微应用: ${appName}`);
      this.unloadApp(appName);
    });
  }
  
  /**
   * 卸载微应用
   */
  public unloadApp(appName: string): void {
    const appInfo = this.appInstances.get(appName);
    if (!appInfo) return;
    
    try {
      // 执行所有清理函数
      appInfo.cleanupFunctions.forEach(cleanup => {
        try {
          cleanup();
        } catch (error) {
          console.error(`执行微应用 ${appName} 的清理函数时出错:`, error);
        }
      });
      
      // 释放资源引用
      appInfo.resources.clear();
      appInfo.cleanupFunctions = [];
      
      // 从实例映射中移除
      this.appInstances.delete(appName);
      
      console.log(`微应用 ${appName} 已成功卸载并清理资源`);
    } catch (error) {
      console.error(`卸载微应用 ${appName} 时出错:`, error);
    }
  }
  
  /**
   * 获取应用使用统计信息
   */
  public getAppStats(): Array<{
    appName: string;
    mountedTime: number;
    idleTime: number;
    usageCount: number;
    resourceCount: number;
  }> {
    const now = Date.now();
    
    return Array.from(this.appInstances.entries()).map(([appName, appInfo]) => ({
      appName,
      mountedTime: appInfo.mountedTime,
      idleTime: now - appInfo.lastAccessed,
      usageCount: appInfo.usageCount,
      resourceCount: appInfo.resources.size
    }));
  }
}

// 使用示例
const lifecycleManager = EnhancedAppLifecycleManager.getInstance();

// 在微应用挂载时注册
function mountMicroApp(appName, instance) {
  lifecycleManager.registerAppInstance(appName, instance);
  
  // 注册需要清理的资源
  const subscription = someObservable.subscribe(() => {});
  lifecycleManager.registerAppResource(appName, subscription, () => {
    subscription.unsubscribe();
  });
  
  // 注册事件监听器的清理
  const handleResize = () => {};
  window.addEventListener('resize', handleResize);
  lifecycleManager.registerAppResource(appName, handleResize, () => {
    window.removeEventListener('resize', handleResize);
  });
}

// 在微应用访问时记录
function accessMicroApp(appName) {
  lifecycleManager.recordAppAccess(appName);
}


---

## 🔒 **安全与隔离** / Security and Isolation

在微前端架构中，安全与隔离是确保系统稳定和数据安全的关键要素。Bone平台采用多层次的安全策略，实现了微应用间的有效隔离、统一的身份认证授权、严格的跨域控制和全面的数据保护机制。

### 6.1 Sandbox Implementation / 沙箱实现

沙箱实现是微前端架构安全的基础，Bone平台基于无界框架(wujie)实现了增强版的JavaScript沙箱，提供运行时隔离、样式隔离和网络隔离。

#### 6.1.1 增强的沙箱配置

```typescript
/**
 * 沙箱配置管理器
 */
export class SandboxConfigManager {
  private static instance: SandboxConfigManager;
  private defaultConfig: WujieConfig;
  private appSpecificConfigs: Map<string, WujieConfig> = new Map();
  
  /**
   * 获取单例实例
   */
  public static getInstance(): SandboxConfigManager {
    if (!SandboxConfigManager.instance) {
      SandboxConfigManager.instance = new SandboxConfigManager();
    }
    return SandboxConfigManager.instance;
  }
  
  private constructor() {
    // 初始化默认沙箱配置
    this.defaultConfig = this.createDefaultConfig();
  }
  
  /**
   * 创建默认沙箱配置
   */
  private createDefaultConfig(): WujieConfig {
    return {
      // 启用JavaScript沙箱
      jsSandbox: true,
      // 启用样式隔离
      cssSandbox: true,
      // 严格的全局变量隔离模式
      sandbox: 'strict',
      // 禁止微应用修改顶层document
      preventGlobalPollution: true,
      // 禁止微应用访问敏感全局对象
      prohibitedGlobals: [
        'localStorage',
        'sessionStorage',
        'IndexedDB',
        'document.cookie',
        'history.pushState',
        'history.replaceState'
      ],
      // 允许的全局事件
      allowedEvents: [
        'click',
        'dblclick',
        'mouseover',
        'mouseout',
        'keydown',
        'keyup',
        'input',
        'change',
        'focus',
        'blur'
      ],
      // 网络请求拦截配置
      fetchFilter: this.createFetchFilter(),
      // 脚本执行白名单
      scriptWhitelist: [],
      // 样式隔离策略
      cssIsolationStrategy: 'shadowdom',
      // 最大执行时间（防止无限循环）
      maxExecutionTime: 5000,
      // 资源加载超时时间
      resourceTimeout: 30000,
      // 允许的iframe特性
      allowedIframeFeatures: ['allow-scripts', 'allow-same-origin']
    };
  }
  
  /**
   * 创建fetch请求过滤器
   */
  private createFetchFilter(): (url: string, options: RequestInit) => boolean {
    return (url: string, options: RequestInit): boolean => {
      // 禁止访问敏感路径
      const sensitivePaths = ['/api/admin', '/api/auth', '/api/internal'];
      if (sensitivePaths.some(path => url.includes(path))) {
        console.warn(`微应用尝试访问被禁止的API路径: ${url}`);
        return false;
      }
      
      // 禁止不安全的请求方法
      const unsafeMethods = ['TRACE', 'TRACK', 'CONNECT'];
      if (options.method && unsafeMethods.includes(options.method)) {
        console.warn(`微应用尝试使用被禁止的HTTP方法: ${options.method}`);
        return false;
      }
      
      // 检查请求头安全
      const headers = options.headers as Record<string, string>;
      if (headers) {
        // 禁止修改安全相关的请求头
        const securityHeaders = ['Authorization', 'Cookie', 'X-Requested-With'];
        for (const header of securityHeaders) {
          if (headers[header]) {
            console.warn(`微应用尝试修改受保护的请求头: ${header}`);
            delete headers[header];
          }
        }
      }
      
      return true;
    };
  }
  
  /**
   * 获取应用的沙箱配置
   */
  public getAppConfig(appName: string): WujieConfig {
    const appConfig = this.appSpecificConfigs.get(appName);
    if (appConfig) {
      // 合并默认配置和应用特定配置
      return { ...this.defaultConfig, ...appConfig };
    }
    return { ...this.defaultConfig };
  }
  
  /**
   * 为特定应用设置沙箱配置
   */
  public setAppConfig(appName: string, config: Partial<WujieConfig>): void {
    this.appSpecificConfigs.set(appName, {
      ...this.getAppConfig(appName),
      ...config
    });
  }
  
  /**
   * 更新全局默认配置
   */
  public updateDefaultConfig(config: Partial<WujieConfig>): void {
    this.defaultConfig = { ...this.defaultConfig, ...config };
  }
  
  /**
   * 添加脚本白名单
   */
  public addScriptToWhitelist(scriptUrl: string, appName?: string): void {
    if (appName) {
      const config = this.getAppConfig(appName);
      if (!config.scriptWhitelist.includes(scriptUrl)) {
        config.scriptWhitelist.push(scriptUrl);
        this.setAppConfig(appName, config);
      }
    } else {
      if (!this.defaultConfig.scriptWhitelist.includes(scriptUrl)) {
        this.defaultConfig.scriptWhitelist.push(scriptUrl);
      }
    }
  }
  
  /**
   * 添加允许的全局变量
   */
  public allowGlobal(globalName: string, appName?: string): void {
    if (appName) {
      const config = this.getAppConfig(appName);
      config.prohibitedGlobals = config.prohibitedGlobals.filter(
        name => name !== globalName
      );
      this.setAppConfig(appName, config);
    } else {
      this.defaultConfig.prohibitedGlobals = this.defaultConfig.prohibitedGlobals.filter(
        name => name !== globalName
      );
    }
  }
}
```

#### 6.1.2 运行时安全监控器

```typescript
/**
 * 沙箱运行时安全监控器
 */
export class SandboxSecurityMonitor {
  private static instance: SandboxSecurityMonitor;
  private violationReports: Array<{
    timestamp: number;
    appName: string;
    violationType: string;
    details: any;
  }> = [];
  private readonly maxReportHistory = 1000;
  private alertThreshold = 5; // 同一应用在1分钟内的违规阈值
  private violationCounts = new Map<string, number>();
  private alertHandlers: Array<(report: any) => void> = [];
  
  /**
   * 获取单例实例
   */
  public static getInstance(): SandboxSecurityMonitor {
    if (!SandboxSecurityMonitor.instance) {
      SandboxSecurityMonitor.instance = new SandboxSecurityMonitor();
    }
    return SandboxSecurityMonitor.instance;
  }
  
  private constructor() {
    this.setupViolationCounterReset();
  }
  
  /**
   * 设置违规计数器重置
   */
  private setupViolationCounterReset(): void {
    // 每分钟重置一次违规计数
    setInterval(() => {
      this.violationCounts.clear();
    }, 60000);
  }
  
  /**
   * 记录安全违规
   */
  public recordViolation(appName: string, violationType: string, details: any): void {
    const report = {
      timestamp: Date.now(),
      appName,
      violationType,
      details
    };
    
    // 记录违规报告
    this.violationReports.push(report);
    
    // 限制历史记录大小
    if (this.violationReports.length > this.maxReportHistory) {
      this.violationReports.shift();
    }
    
    // 记录违规计数
    const key = `${appName}-${violationType}`;
    const count = (this.violationCounts.get(key) || 0) + 1;
    this.violationCounts.set(key, count);
    
    console.warn(`微应用安全违规:`, report);
    
    // 检查是否达到告警阈值
    if (count >= this.alertThreshold) {
      this.triggerAlert(report);
    }
  }
  
  /**
   * 触发安全告警
   */
  private triggerAlert(report: any): void {
    console.error(`安全告警: 微应用 ${report.appName} 触发了安全阈值`, report);
    
    // 调用所有注册的告警处理器
    this.alertHandlers.forEach(handler => {
      try {
        handler(report);
      } catch (error) {
        console.error('执行告警处理器失败:', error);
      }
    });
  }
  
  /**
   * 注册告警处理器
   */
  public registerAlertHandler(handler: (report: any) => void): () => void {
    this.alertHandlers.push(handler);
    
    // 返回取消注册函数
    return () => {
      const index = this.alertHandlers.indexOf(handler);
      if (index >= 0) {
        this.alertHandlers.splice(index, 1);
      }
    };
  }
  
  /**
   * 设置告警阈值
   */
  public setAlertThreshold(threshold: number): void {
    this.alertThreshold = threshold;
  }
  
  /**
   * 获取违规统计
   */
  public getViolationStats(appName?: string): any {
    if (appName) {
      return this.violationReports
        .filter(report => report.appName === appName)
        .reduce((stats, report) => {
          stats[report.violationType] = (stats[report.violationType] || 0) + 1;
          return stats;
        }, {} as Record<string, number>);
    }
    
    // 所有应用的统计
    return this.violationReports.reduce((stats, report) => {
      if (!stats[report.appName]) {
        stats[report.appName] = {};
      }
      stats[report.appName][report.violationType] = 
        (stats[report.appName][report.violationType] || 0) + 1;
      return stats;
    }, {} as Record<string, Record<string, number>>);
  }
  
  /**
   * 获取最近的违规报告
   */
  public getRecentViolations(limit: number = 20, appName?: string): Array<any> {
    let reports = [...this.violationReports];
    
    if (appName) {
      reports = reports.filter(report => report.appName === appName);
    }
    
    // 按时间倒序排序并返回最新的
    return reports
      .sort((a, b) => b.timestamp - a.timestamp)
      .slice(0, limit);
  }
}
```

#### 6.1.3 沙箱初始化与集成

```typescript
/**
 * 沙箱管理器
 */
export class SandboxManager {
  private static instance: SandboxManager;
  private sandboxes: Map<string, WujieInstance> = new Map();
  private configManager = SandboxConfigManager.getInstance();
  private securityMonitor = SandboxSecurityMonitor.getInstance();
  
  /**
   * 获取单例实例
   */
  public static getInstance(): SandboxManager {
    if (!SandboxManager.instance) {
      SandboxManager.instance = new SandboxManager();
    }
    return SandboxManager.instance;
  }
  
  private constructor() {
    this.setupGlobalSecurity();
  }
  
  /**
   * 设置全局安全策略
   */
  private setupGlobalSecurity(): void {
    // 拦截动态脚本创建
    this.interceptDynamicScriptCreation();
    
    // 拦截动态样式创建
    this.interceptDynamicStyleCreation();
  }
  
  /**
   * 拦截动态脚本创建
   */
  private interceptDynamicScriptCreation(): void {
    const originalCreateElement = document.createElement;
    
    document.createElement = function(this: Document, tagName: string, options?: ElementCreationOptions): Element {
      const element = originalCreateElement.call(this, tagName, options);
      
      if (tagName.toLowerCase() === 'script') {
        const script = element as HTMLScriptElement;
        const originalSrc = Object.getOwnPropertyDescriptor(script, 'src');
        
        // 拦截src属性设置
        Object.defineProperty(script, 'src', {
          get: function() {
            return originalSrc?.get?.call(this);
          },
          set: function(value: string) {
            // 检查脚本是否在白名单中
            const sandboxManager = SandboxManager.getInstance();
            const currentApp = sandboxManager.getCurrentApp();
            
            if (currentApp) {
              const config = sandboxManager.configManager.getAppConfig(currentApp);
              if (!config.scriptWhitelist.some(whitelisted => value.includes(whitelisted))) {
                sandboxManager.securityMonitor.recordViolation(
                  currentApp,
                  'unauthorized_script',
                  { url: value }
                );
                console.warn(`微应用 ${currentApp} 尝试加载未授权的脚本: ${value}`);
                return;
              }
            }
            
            if (originalSrc?.set) {
              originalSrc.set.call(this, value);
            } else {
              (this as any).setAttribute('src', value);
            }
          }
        });
      }
      
      return element;
    } as typeof document.createElement;
  }
  
  /**
   * 拦截动态样式创建
   */
  private interceptDynamicStyleCreation(): void {
    // 类似脚本拦截的实现
    // ...
  }
  
  /**
   * 创建微应用沙箱实例
   */
  public async createSandbox(appName: string, config: MicroAppConfig): Promise<WujieInstance> {
    // 获取应用特定的沙箱配置
    const sandboxConfig = this.configManager.getAppConfig(appName);
    
    // 创建无界实例
    const wujieInstance = window.wujie.createApp({
      name: appName,
      url: config.entry,
      ...sandboxConfig,
      props: {
        ...config.props,
        onSecurityViolation: (violation: any) => {
          this.securityMonitor.recordViolation(appName, violation.type, violation.details);
        }
      },
      // 生命周期钩子增强
      beforeLoad: () => {
        console.log(`[沙箱] 开始加载微应用: ${appName}`);
        return config.beforeLoad?.() ?? true;
      },
      beforeMount: () => {
        console.log(`[沙箱] 开始挂载微应用: ${appName}`);
        return config.beforeMount?.() ?? true;
      },
      afterMount: () => {
        console.log(`[沙箱] 微应用挂载完成: ${appName}`);
        config.afterMount?.();
      },
      beforeUnmount: () => {
        console.log(`[沙箱] 开始卸载微应用: ${appName}`);
        return config.beforeUnmount?.() ?? true;
      },
      afterUnmount: () => {
        console.log(`[沙箱] 微应用卸载完成: ${appName}`);
        config.afterUnmount?.();
        // 确保完全清理资源
        this.cleanupSandbox(appName);
      },
      // 错误处理增强
      onError: (error: any) => {
        console.error(`[沙箱] 微应用 ${appName} 发生错误:`, error);
        this.securityMonitor.recordViolation(appName, 'runtime_error', { error: error.message });
        config.onError?.(error);
      }
    });
    
    // 存储沙箱实例
    this.sandboxes.set(appName, wujieInstance);
    
    return wujieInstance;
  }
  
  /**
   * 挂载微应用
   */
  public async mountApp(appName: string, container: HTMLElement): Promise<void> {
    let wujieInstance = this.sandboxes.get(appName);
    
    if (!wujieInstance) {
      throw new Error(`未找到微应用 ${appName} 的沙箱实例`);
    }
    
    try {
      // 挂载微应用
      await wujieInstance.mount(container);
      
      // 设置当前活动的应用
      this.setCurrentApp(appName);
    } catch (error) {
      console.error(`挂载微应用 ${appName} 失败:`, error);
      throw error;
    }
  }
  
  /**
   * 卸载微应用
   */
  public async unmountApp(appName: string): Promise<void> {
    const wujieInstance = this.sandboxes.get(appName);
    
    if (wujieInstance) {
      try {
        await wujieInstance.unmount();
      } catch (error) {
        console.error(`卸载微应用 ${appName} 失败:`, error);
      }
    }
  }
  
  /**
   * 清理沙箱资源
   */
  private cleanupSandbox(appName: string): void {
    // 执行额外的清理操作
    // 清除事件监听器、定时器等
    this.clearAppIntervals(appName);
    this.clearAppEventListeners(appName);
  }
  
  /**
   * 清除应用的定时器
   */
  private clearAppIntervals(appName: string): void {
    // 实现定时器清理逻辑
    // ...
  }
  
  /**
   * 清除应用的事件监听器
   */
  private clearAppEventListeners(appName: string): void {
    // 实现事件监听器清理逻辑
    // ...
  }
  
  /**
   * 设置当前活动的应用
   */
  private setCurrentApp(appName: string): void {
    // 存储当前活动应用信息
    (window as any).__CURRENT_MICRO_APP__ = appName;
  }
  
  /**
   * 获取当前活动的应用
   */
  private getCurrentApp(): string | null {
    return (window as any).__CURRENT_MICRO_APP__ || null;
  }
}
```

### 6.2 Authentication and Authorization / 认证与授权

Bone平台实现了统一的身份认证和细粒度的权限管理机制，确保用户只能访问其被授权的功能和数据。

#### 6.2.1 统一认证服务

```typescript
/**
 * 统一认证服务
 */
export class AuthService {
  private static instance: AuthService;
  private tokenKey = 'bone_auth_token';
  private userInfoKey = 'bone_user_info';
  private refreshTokenKey = 'bone_refresh_token';
  private tokenExpiryKey = 'bone_token_expiry';
  private authEvents = new EventEmitter();
  private refreshTimer: NodeJS.Timeout | null = null;
  private readonly tokenRefreshThreshold = 5 * 60 * 1000; // 提前5分钟刷新
  
  /**
   * 获取单例实例
   */
  public static getInstance(): AuthService {
    if (!AuthService.instance) {
      AuthService.instance = new AuthService();
    }
    return AuthService.instance;
  }
  
  private constructor() {
    this.setupTokenRefresh();
  }
  
  /**
   * 设置令牌自动刷新
   */
  private setupTokenRefresh(): void {
    const expiryTime = this.getTokenExpiry();
    if (expiryTime) {
      const timeUntilRefresh = expiryTime - Date.now() - this.tokenRefreshThreshold;
      
      if (timeUntilRefresh > 0) {
        this.refreshTimer = setTimeout(() => {
          this.refreshToken();
        }, timeUntilRefresh);
      } else {
        // 令牌即将过期，立即刷新
        this.refreshToken();
      }
    }
  }
  
  /**
   * 用户登录
   */
  public async login(username: string, password: string): Promise<{ success: boolean; message?: string }> {
    try {
      // 执行登录请求
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Requested-With': 'XMLHttpRequest'
        },
        body: JSON.stringify({ username, password }),
        credentials: 'include' // 包含Cookie
      });
      
      if (!response.ok) {
        throw new Error(`登录失败: ${response.statusText}`);
      }
      
      const data = await response.json();
      
      if (data.success && data.token) {
        // 存储认证信息
        this.setAuthData(data);
        
        // 触发登录成功事件
        this.authEvents.emit('loginSuccess', data.userInfo);
        
        return { success: true };
      } else {
        return { success: false, message: data.message || '登录失败' };
      }
    } catch (error) {
      console.error('登录过程中发生错误:', error);
      return { success: false, message: error.message || '登录过程中发生错误' };
    }
  }
  
  /**
   * 用户登出
   */
  public async logout(): Promise<void> {
    try {
      // 通知服务器登出
      await fetch('/api/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.getToken()}`,
          'X-Requested-With': 'XMLHttpRequest'
        },
        credentials: 'include'
      });
    } catch (error) {
      console.warn('登出请求失败，但仍清除本地状态:', error);
    } finally {
      // 清除本地存储的认证信息
      this.clearAuthData();
      
      // 触发登出事件
      this.authEvents.emit('logout');
    }
  }
  
  /**
   * 刷新访问令牌
   */
  private async refreshToken(): Promise<void> {
    const refreshToken = this.getRefreshToken();
    
    if (!refreshToken) {
      console.warn('没有刷新令牌，无法刷新访问令牌');
      this.handleTokenExpired();
      return;
    }
    
    try {
      const response = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Requested-With': 'XMLHttpRequest'
        },
        body: JSON.stringify({ refreshToken }),
        credentials: 'include'
      });
      
      if (response.ok) {
        const data = await response.json();
        if (data.success && data.token) {
          // 更新认证信息
          this.setAuthData(data);
          
          // 触发令牌刷新事件
          this.authEvents.emit('tokenRefreshed');
          
          return;
        }
      }
      
      // 刷新失败，处理令牌过期
      this.handleTokenExpired();
    } catch (error) {
      console.error('刷新令牌失败:', error);
      this.handleTokenExpired();
    }
  }
  
  /**
   * 处理令牌过期
   */
  private handleTokenExpired(): void {
    // 清除认证信息
    this.clearAuthData();
    
    // 触发令牌过期事件
    this.authEvents.emit('tokenExpired');
  }
  
  /**
   * 存储认证数据
   */
  private setAuthData(data: { token: string; refreshToken: string; userInfo: any; expiresIn: number }): void {
    const expiryTime = Date.now() + data.expiresIn * 1000;
    
    // 使用localStorage存储，实际项目中可以考虑使用更安全的方式
    localStorage.setItem(this.tokenKey, data.token);
    localStorage.setItem(this.refreshTokenKey, data.refreshToken);
    localStorage.setItem(this.userInfoKey, JSON.stringify(data.userInfo));
    localStorage.setItem(this.tokenExpiryKey, expiryTime.toString());
    
    // 设置自动刷新
    this.setupTokenRefresh();
  }
  
  /**
   * 清除认证数据
   */
  private clearAuthData(): void {
    localStorage.removeItem(this.tokenKey);
    localStorage.removeItem(this.refreshTokenKey);
    localStorage.removeItem(this.userInfoKey);
    localStorage.removeItem(this.tokenExpiryKey);
    
    // 清除刷新定时器
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
  }
  
  /**
   * 获取访问令牌
   */
  public getToken(): string | null {
    return localStorage.getItem(this.tokenKey);
  }
  
  /**
   * 获取刷新令牌
   */
  private getRefreshToken(): string | null {
    return localStorage.getItem(this.refreshTokenKey);
  }
  
  /**
   * 获取令牌过期时间
   */
  private getTokenExpiry(): number | null {
    const expiryStr = localStorage.getItem(this.tokenExpiryKey);
    return expiryStr ? parseInt(expiryStr, 10) : null;
  }
  
  /**
   * 获取用户信息
   */
  public getUserInfo(): any | null {
    const userInfoStr = localStorage.getItem(this.userInfoKey);
    return userInfoStr ? JSON.parse(userInfoStr) : null;
  }
  
  /**
   * 检查用户是否已认证
   */
  public isAuthenticated(): boolean {
    const token = this.getToken();
    const expiry = this.getTokenExpiry();
    
    // 检查令牌是否存在且未过期
    return !!token && !!expiry && expiry > Date.now();
  }
  
  /**
   * 检查用户是否有权限
   */
  public hasPermission(permission: string): boolean {
    const userInfo = this.getUserInfo();
    
    if (!userInfo || !userInfo.permissions) {
      return false;
    }
    
    // 支持权限通配符，如 'user:*' 匹配所有用户相关权限
    if (permission.includes('*')) {
      const pattern = new RegExp(permission.replace(/\*/g, '.*'));
      return userInfo.permissions.some((p: string) => pattern.test(p));
    }
    
    return userInfo.permissions.includes(permission);
  }
  
  /**
   * 检查用户是否有角色
   */
  public hasRole(role: string): boolean {
    const userInfo = this.getUserInfo();
    return userInfo && userInfo.roles && userInfo.roles.includes(role);
  }
  
  /**
   * 添加认证事件监听器
   */
  public on(event: string, handler: (...args: any[]) => void): () => void {
    this.authEvents.on(event, handler);
    
    // 返回取消监听函数
    return () => {
      this.authEvents.off(event, handler);
    };
  }
  
  /**
   * 一次性认证事件监听
   */
  public once(event: string, handler: (...args: any[]) => void): void {
    this.authEvents.once(event, handler);
  }
  
  /**
   * 获取认证头
   */
  public getAuthHeaders(): Record<string, string> {
    const token = this.getToken();
    return token ? { Authorization: `Bearer ${token}` } : {};
  }
}
```

#### 6.2.2 权限守卫组件

```typescript
import React, { useEffect, useState } from 'react';
import { useNavigate, Navigate } from 'react-router-dom';
import { AuthService } from './AuthService';

interface PermissionGuardProps {
  requiredPermission?: string;
  requiredRole?: string;
  fallback?: React.ReactNode;
  redirectTo?: string;
  children: React.ReactNode;
}

/**
 * 权限守卫组件
 */
export const PermissionGuard: React.FC<PermissionGuardProps> = ({
  requiredPermission,
  requiredRole,
  fallback = <div>您没有权限访问此页面</div>,
  redirectTo,
  children
}) => {
  const authService = AuthService.getInstance();
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(true);
  const [hasAccess, setHasAccess] = useState(false);
  
  useEffect(() => {
    const checkAccess = async () => {
      try {
        // 检查是否已认证
        if (!authService.isAuthenticated()) {
          setHasAccess(false);
          return;
        }
        
        // 检查权限
        if (requiredPermission && !authService.hasPermission(requiredPermission)) {
          setHasAccess(false);
          return;
        }
        
        // 检查角色
        if (requiredRole && !authService.hasRole(requiredRole)) {
          setHasAccess(false);
          return;
        }
        
        // 拥有访问权限
        setHasAccess(true);
      } catch (error) {
        console.error('权限检查失败:', error);
        setHasAccess(false);
      } finally {
        setIsLoading(false);
      }
    };
    
    checkAccess();
    
    // 监听权限变化
    const unregisterListener = authService.on('permissionChanged', checkAccess);
    
    return () => {
      unregisterListener();
    };
  }, [requiredPermission, requiredRole, authService]);
  
  if (isLoading) {
    return <div>权限检查中...</div>;
  }
  
  if (!hasAccess) {
    if (redirectTo) {
      return <Navigate to={redirectTo} replace />;
    }
    return fallback;
  }
  
  return <>{children}</>;
};

/**
 * 权限检查Hook
 */
export const usePermission = (permission?: string, role?: string) => {
  const authService = AuthService.getInstance();
  const [hasPermission, setHasPermission] = useState(false);
  
  useEffect(() => {
    const checkPermission = () => {
      if (!authService.isAuthenticated()) {
        setHasPermission(false);
        return;
      }
      
      if (permission && !authService.hasPermission(permission)) {
        setHasPermission(false);
        return;
      }
      
      if (role && !authService.hasRole(role)) {
        setHasPermission(false);
        return;
      }
      
      setHasPermission(true);
    };
    
    checkPermission();
    
    // 监听认证状态变化
    const unregisterAuthListener = authService.on('loginSuccess', checkPermission);
    const unregisterLogoutListener = authService.on('logout', checkPermission);
    const unregisterTokenListener = authService.on('tokenRefreshed', checkPermission);
    
    return () => {
      unregisterAuthListener();
      unregisterLogoutListener();
      unregisterTokenListener();
    };
  }, [permission, role, authService]);
  
  return hasPermission;
};
```

### 6.3 Cross-Origin Security / 跨域安全

Bone平台实现了全面的跨域安全策略，包括CORS配置、CSRF防护和安全的跨域通信机制。

#### 6.3.1 跨域资源共享(CORS)配置

```typescript
/**
 * CORS配置管理器
 */
export class CorsConfigManager {
  private static instance: CorsConfigManager;
  private allowedOrigins: Set<string> = new Set();
  private allowedMethods: string[] = ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'];
  private allowedHeaders: string[] = [
    'Content-Type',
    'Authorization',
    'X-Requested-With',
    'Accept',
    'Origin',
    'Cache-Control',
    'Pragma'
  ];
  private exposeHeaders: string[] = [
    'Content-Length',
    'Access-Control-Allow-Origin',
    'Access-Control-Allow-Headers',
    'Content-Type'
  ];
  private allowCredentials = true;
  private maxAge = 86400; // 24小时
  
  /**
   * 获取单例实例
   */
  public static getInstance(): CorsConfigManager {
    if (!CorsConfigManager.instance) {
      CorsConfigManager.instance = new CorsConfigManager();
    }
    return CorsConfigManager.instance;
  }
  
  private constructor() {
    // 初始化默认允许的源
    this.addAllowedOrigin('https://app.bone.com');
    this.addAllowedOrigin('https://test.bone.com');
  }
  
  /**
   * 添加允许的源
   */
  public addAllowedOrigin(origin: string): void {
    this.allowedOrigins.add(origin);
  }
  
  /**
   * 移除允许的源
   */
  public removeAllowedOrigin(origin: string): void {
    this.allowedOrigins.delete(origin);
  }
  
  /**
   * 检查源是否被允许
   */
  public isOriginAllowed(origin: string): boolean {
    return this.allowedOrigins.has(origin);
  }
  
  /**
   * 获取CORS响应头
   */
  public getCorsHeaders(requestOrigin?: string): Record<string, string> {
    const headers: Record<string, string> = {
      'Access-Control-Allow-Methods': this.allowedMethods.join(','),
      'Access-Control-Allow-Headers': this.allowedHeaders.join(','),
      'Access-Control-Expose-Headers': this.exposeHeaders.join(','),
      'Access-Control-Allow-Credentials': this.allowCredentials.toString(),
      'Access-Control-Max-Age': this.maxAge.toString()
    };
    
    // 只有当请求源在允许列表中时才设置允许的源
    if (requestOrigin && this.isOriginAllowed(requestOrigin)) {
      headers['Access-Control-Allow-Origin'] = requestOrigin;
    }
    
    return headers;
  }
  
  /**
   * 设置允许的HTTP方法
   */
  public setAllowedMethods(methods: string[]): void {
    this.allowedMethods = methods;
  }
  
  /**
   * 添加允许的HTTP方法
   */
  public addAllowedMethod(method: string): void {
    if (!this.allowedMethods.includes(method)) {
      this.allowedMethods.push(method);
    }
  }
  
  /**
   * 设置允许的请求头
   */
  public setAllowedHeaders(headers: string[]): void {
    this.allowedHeaders = headers;
  }
  
  /**
   * 添加允许的请求头
   */
  public addAllowedHeader(header: string): void {
    if (!this.allowedHeaders.includes(header)) {
      this.allowedHeaders.push(header);
    }
  }
  
  /**
   * 设置是否允许凭证
   */
  public setAllowCredentials(allow: boolean): void {
    this.allowCredentials = allow;
  }
  
  /**
   * 设置预检请求结果缓存时间
   */
  public setMaxAge(seconds: number): void {
    this.maxAge = seconds;
  }
}
```

#### 6.3.2 CSRF防护机制

```typescript
/**
 * CSRF保护服务
 */
export class CsrfProtectionService {
  private static instance: CsrfProtectionService;
  private csrfTokenKey = 'bone_csrf_token';
  private tokenValidity = 3600000; // 1小时
  
  /**
   * 获取单例实例
   */
  public static getInstance(): CsrfProtectionService {
    if (!CsrfProtectionService.instance) {
      CsrfProtectionService.instance = new CsrfProtectionService();
    }
    return CsrfProtectionService.instance;
  }
  
  private constructor() {
    // 初始化CSRF令牌
    this.initCsrfToken();
  }
  
  /**
   * 初始化CSRF令牌
   */
  private async initCsrfToken(): Promise<void> {
    const existingToken = this.getCsrfToken();
    
    if (!existingToken || this.isTokenExpired(existingToken)) {
      await this.fetchNewCsrfToken();
    }
  }
  
  /**
   * 从服务器获取新的CSRF令牌
   */
  private async fetchNewCsrfToken(): Promise<void> {
    try {
      const response = await fetch('/api/csrf-token', {
        method: 'GET',
        credentials: 'include',
        headers: {
          'X-Requested-With': 'XMLHttpRequest'
        }
      });
      
      if (response.ok) {
        const data = await response.json();
        if (data.token) {
          this.storeCsrfToken(data.token);
        }
      }
    } catch (error) {
      console.error('获取CSRF令牌失败:', error);
    }
  }
  
  /**
   * 存储CSRF令牌
   */
  private storeCsrfToken(token: string): void {
    const tokenData = {
      value: token,
      timestamp: Date.now()
    };
    
    // 存储在sessionStorage中，因为CSRF令牌应该与会话绑定
    sessionStorage.setItem(this.csrfTokenKey, JSON.stringify(tokenData));
  }
  
  /**
   * 获取CSRF令牌
   */
  public getCsrfToken(): string | null {
    try {
      const tokenDataStr = sessionStorage.getItem(this.csrfTokenKey);
      if (!tokenDataStr) return null;
      
      const tokenData = JSON.parse(tokenDataStr);
      return tokenData.value;
    } catch (error) {
      console.error('获取CSRF令牌失败:', error);
      return null;
    }
  }
  
  /**
   * 检查令牌是否过期
   */
  private isTokenExpired(tokenData: any): boolean {
    if (!tokenData || !tokenData.timestamp) return true;
    
    return Date.now() - tokenData.timestamp > this.tokenValidity;
  }
  
  /**
   * 获取带有CSRF令牌的请求头
   */
  public getCsrfHeaders(): Record<string, string> {
    const token = this.getCsrfToken();
    if (!token) {
      // 如果没有令牌，尝试获取新令牌
      this.fetchNewCsrfToken();
    }
    
    return token ? {
      'X-CSRF-Token': token,
      'X-Requested-With': 'XMLHttpRequest'
    } : {
      'X-Requested-With': 'XMLHttpRequest'
    };
  }
  
  /**
   * 验证CSRF令牌（服务器端逻辑，这里仅作演示）
   */
  public async validateCsrfToken(token: string): Promise<boolean> {
    try {
      const response = await fetch('/api/validate-csrf', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...this.getCsrfHeaders()
        },
        body: JSON.stringify({ token }),
        credentials: 'include'
      });
      
      if (response.ok) {
        const data = await response.json();
        return data.valid === true;
      }
    } catch (error) {
      console.error('验证CSRF令牌失败:', error);
    }
    
    return false;
  }
  
  /**
   * 为fetch请求添加CSRF保护
   */
  public enhanceFetch(fetchFn?: typeof fetch): typeof fetch {
    const baseFetch = fetchFn || window.fetch;
    
    return (input: RequestInfo, init?: RequestInit): Promise<Response> => {
      // 只对非GET请求添加CSRF保护
      const method = (init?.method || 'GET').toUpperCase();
      if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
        init = {
          ...init,
          headers: {
            ...init?.headers,
            ...this.getCsrfHeaders()
          },
          credentials: init?.credentials || 'include'
        };
      }
      
      return baseFetch(input, init);
    };
  }
}

// 使用示例：增强全局fetch
window.fetch = CsrfProtectionService.getInstance().enhanceFetch();
```

### 6.4 数据安全与隐私保护

除了以上安全措施，Bone平台还实现了全面的数据安全与隐私保护机制，确保敏感数据在传输和存储过程中的安全性。

#### 6.4.1 数据加密工具

```typescript
/**
 * 数据加密服务
 */
export class EncryptionService {
  private static instance: EncryptionService;
  private cryptoKey: CryptoKey | null = null;
  private keyDerivationSalt: Uint8Array;
  private keyDerivationIterations = 100000;
  
  /**
   * 获取单例实例
   */
  public static getInstance(): EncryptionService {
    if (!EncryptionService.instance) {
      EncryptionService.instance = new EncryptionService();
    }
    return EncryptionService.instance;
  }
  
  private constructor() {
    // 初始化盐值（实际项目中应从服务器获取或使用环境变量）
    this.keyDerivationSalt = this.hexStringToUint8Array('a1b2c3d4e5f6');
    this.initializeCryptoKey();
  }
  
  /**
   * 初始化加密密钥
   */
  private async initializeCryptoKey(): Promise<void> {
    try {
      // 从密码派生密钥（实际项目中应使用更安全的方式）
      const password = await this.getEncryptionPassword();
      this.cryptoKey = await this.deriveKeyFromPassword(password);
    } catch (error) {
      console.error('初始化加密密钥失败:', error);
    }
  }
  
  /**
   * 获取加密密码（演示实现，实际应更安全）
   */
  private async getEncryptionPassword(): Promise<string> {
    // 实际项目中应从安全存储获取或与服务器协商
    return 'secure_master_password';
  }
  
  /**
   * 从密码派生加密密钥
   */
  private async deriveKeyFromPassword(password: string): Promise<CryptoKey> {
    const encoder = new TextEncoder();
    const passwordData = encoder.encode(password);
    
    // 使用PBKDF2派生密钥
    const importedKey = await crypto.subtle.importKey(
      'raw',
      passwordData,
      { name: 'PBKDF2' },
      false,
      ['deriveKey']
    );
    
    return await crypto.subtle.deriveKey(
      {
        name: 'PBKDF2',
        salt: this.keyDerivationSalt,
        iterations: this.keyDerivationIterations,
        hash: 'SHA-256'
      },
      importedKey,
      {
        name: 'AES-GCM',
        length: 256
      },
      true,
      ['encrypt', 'decrypt']
    );
  }
  
  /**
   * 加密数据
   */
  public async encrypt(data: any): Promise<string> {
    if (!this.cryptoKey) {
      throw new Error('加密密钥未初始化');
    }
    
    const encoder = new TextEncoder();
    const dataStr = typeof data === 'string' ? data : JSON.stringify(data);
    const dataBuffer = encoder.encode(dataStr);
    
    // 生成随机初始化向量
    const iv = crypto.getRandomValues(new Uint8Array(12));
    
    // 加密数据
    const encryptedData = await crypto.subtle.encrypt(
      {
        name: 'AES-GCM',
        iv
      },
      this.cryptoKey,
      dataBuffer
    );
    
    // 合并IV和加密数据并转换为base64
    const combined = new Uint8Array(iv.length + encryptedData.byteLength);
    combined.set(iv);
    combined.set(new Uint8Array(encryptedData), iv.length);
    
    return this.arrayBufferToBase64(combined.buffer);
  }
  
  /**
   * 解密数据
   */
  public async decrypt(encryptedData: string): Promise<any> {
    if (!this.cryptoKey) {
      throw new Error('加密密钥未初始化');
    }
    
    // 解码base64并分离IV和加密数据
    const combinedBuffer = this.base64ToArrayBuffer(encryptedData);
    const iv = new Uint8Array(combinedBuffer.slice(0, 12));
    const dataToDecrypt = combinedBuffer.slice(12);
    
    // 解密数据
    const decryptedData = await crypto.subtle.decrypt(
      {
        name: 'AES-GCM',
        iv
      },
      this.cryptoKey,
      dataToDecrypt
    );
    
    // 解码为文本
    const decoder = new TextDecoder();
    const decryptedText = decoder.decode(decryptedData);
    
    // 尝试解析为JSON
    try {
      return JSON.parse(decryptedText);
    } catch {
      return decryptedText;
    }
  }
  
  /**
   * 哈希数据
   */
  public async hash(data: string): Promise<string> {
    const encoder = new TextEncoder();
    const dataBuffer = encoder.encode(data);
    
    const hashBuffer = await crypto.subtle.digest('SHA-256', dataBuffer);
    
    return this.arrayBufferToHexString(hashBuffer);
  }
  
  /**
   * 验证数据完整性
   */
  public async verifyIntegrity(data: string, expectedHash: string): Promise<boolean> {
    const actualHash = await this.hash(data);
    return actualHash === expectedHash;
  }
  
  /**
   * 工具方法：ArrayBuffer转Base64
   */
  private arrayBufferToBase64(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    
    return btoa(binary);
  }
  
  /**
   * 工具方法：Base64转ArrayBuffer
   */
  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    const binaryString = atob(base64);
    const len = binaryString.length;
    const bytes = new Uint8Array(len);
    
    for (let i = 0; i < len; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    
    return bytes.buffer;
  }
  
  /**
   * 工具方法：ArrayBuffer转十六进制字符串
   */
  private arrayBufferToHexString(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    return Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  }
  
  /**
   * 工具方法：十六进制字符串转Uint8Array
   */
  private hexStringToUint8Array(hexString: string): Uint8Array {
    const bytes = new Uint8Array(hexString.length / 2);
    
    for (let i = 0; i < hexString.length; i += 2) {
      bytes[i / 2] = parseInt(hexString.substr(i, 2), 16);
    }
    
    return bytes;
  }
}
```

#### 6.4.2 安全存储服务

```typescript
/**
 * 安全存储服务
 */
export class SecureStorageService {
  private static instance: SecureStorageService;
  private encryptionService = EncryptionService.getInstance();
  private securePrefix = 'secure_';
  private isSecureStorageAvailable = false;
  
  /**
   * 获取单例实例
   */
  public static getInstance(): SecureStorageService {
    if (!SecureStorageService.getInstance) {
      SecureStorageService.getInstance = new SecureStorageService();
    }
    return SecureStorageService.getInstance;
  }
  
  private constructor() {
    this.checkSecureStorageAvailability();
  }
  
  /**
   * 检查安全存储是否可用
   */
  private checkSecureStorageAvailability(): void {
    // 检查浏览器是否支持必要的API
    this.isSecureStorageAvailable = (
      'localStorage' in window &&
      'crypto' in window &&
      'subtle' in crypto &&
      'TextEncoder' in window &&
      'TextDecoder' in window
    );
  }
  
  /**
   * 安全存储数据
   */
  public async setItem(key: string, value: any): Promise<void> {
    if (!this.isSecureStorageAvailable) {
      console.warn('安全存储不可用，使用普通存储');
      this.fallbackSetItem(key, value);
      return;
    }
    
    try {
      // 加密数据
      const encryptedValue = await this.encryptionService.encrypt(value);
      
      // 添加安全前缀并存储
      const secureKey = this.getSecureKey(key);
      localStorage.setItem(secureKey, encryptedValue);
    } catch (error) {
      console.error('安全存储数据失败:', error);
      // 失败时使用回退方案
      this.fallbackSetItem(key, value);
    }
  }
  
  /**
   * 获取安全存储的数据
   */
  public async getItem<T>(key: string): Promise<T | null> {
    if (!this.isSecureStorageAvailable) {
      console.warn('安全存储不可用，使用普通存储');
      return this.fallbackGetItem<T>(key);
    }
    
    try {
      // 获取加密数据
      const secureKey = this.getSecureKey(key);
      const encryptedValue = localStorage.getItem(secureKey);
      
      if (!encryptedValue) {
        return null;
      }
      
      // 解密数据
      return await this.encryptionService.decrypt(encryptedValue) as T;
    } catch (error) {
      console.error('获取安全存储数据失败:', error);
      // 失败时尝试使用回退方案
      return this.fallbackGetItem<T>(key);
    }
  }
  
  /**
   * 删除安全存储的数据
   */
  public removeItem(key: string): void {
    const secureKey = this.getSecureKey(key);
    localStorage.removeItem(secureKey);
    
    // 同时删除可能存在的回退数据
    this.fallbackRemoveItem(key);
  }
  
  /**
   * 清除所有安全存储的数据
   */
  public clear(): void {
    // 只清除带有安全前缀的数据
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key && key.startsWith(this.securePrefix)) {
        localStorage.removeItem(key);
      }
    }
    
    // 同时清除回退存储
    this.fallbackClear();
  }
  
  /**
   * 获取安全键名
   */
  private getSecureKey(key: string): string {
    return `${this.securePrefix}${key}`;
  }
  
  /**
   * 回退存储方法 - 使用sessionStorage
   */
  private fallbackSetItem(key: string, value: any): void {
    try {
      const valueStr = typeof value === 'string' ? value : JSON.stringify(value);
      sessionStorage.setItem(`fallback_${key}`, valueStr);
    } catch (error) {
      console.error('回退存储失败:', error);
    }
  }
  
  /**
   * 回退获取方法
   */
  private fallbackGetItem<T>(key: string): T | null> {
    try {
      const valueStr = sessionStorage.getItem(`fallback_${key}`);
      if (!valueStr) return null;
      
      try {
        return JSON.parse(valueStr) as T;
      } catch {
        return valueStr as unknown as T;
      }
    } catch (error) {
      console.error('回退获取失败:', error);
      return null;
    }
  }
  
  /**
   * 回退删除方法
   */
  private fallbackRemoveItem(key: string): void {
    sessionStorage.removeItem(`fallback_${key}`);
  }
  
  /**
   * 回退清除方法
   */
  private fallbackClear(): void {
    for (let i = 0; i < sessionStorage.length; i++) {
      const key = sessionStorage.key(i);
      if (key && key.startsWith('fallback_')) {
        sessionStorage.removeItem(key);
      }
    }
  }
}

---

## 📝 **总结** / Summary

This document provides a comprehensive overview of the React micro-frontend architecture design for the Bone enterprise-level development platform, with a focus on the core design of the main framework and micro-applications. The architecture offers the following key advantages:

- **Modularity / 模块化**: Achieves independent development and deployment of business modules through the micro-frontend architecture, enabling teams to work autonomously while maintaining system cohesion.

- **High Performance / 高性能**: Implements advanced optimization strategies including preloading mechanisms, intelligent caching, and resource management to significantly enhance user experience and application responsiveness.

- **Security / 安全性**: Ensures secure isolation between micro-applications through sandbox technology, preventing cross-application interference and protecting sensitive data with comprehensive security policies.

- **Scalability / 可扩展性**: Supports dynamic registration and loading of new micro-applications, allowing the platform to evolve without disrupting existing functionality and accommodating business growth seamlessly.

- **Effective Collaboration / 良好的协作**: Facilitates parallel development across multiple teams with well-defined boundaries and standardized communication protocols, reducing integration conflicts and accelerating delivery cycles.

- **Robust Communication / 强大的通信**: Provides a sophisticated event bus and messaging system that enables efficient and type-safe communication between micro-applications while maintaining loose coupling.

- **Optimized Resource Management / 优化的资源管理**: Implements intelligent resource allocation and cleanup mechanisms to ensure efficient memory usage, prevent memory leaks, and maintain application stability under heavy loads.

Through this architectural design, the Bone platform can effectively support the development and maintenance of large-scale enterprise applications, significantly improving development efficiency, system reliability, and overall user satisfaction. The modular approach also provides long-term benefits in terms of maintainability, allowing the platform to adapt to changing business requirements and technological advancements with minimal effort.