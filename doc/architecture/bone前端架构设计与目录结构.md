# 🎨 Bone 前端架构设计与目录结构

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

- **微前端架构**: 将大型前端应用拆分为独立的微应用，实现业务模块解耦，支持多团队并行开发
- **组件化设计**: 采用原子设计方法论，构建可复用、可组合的组件体系
- **类型安全**: 全面使用TypeScript，确保代码质量、开发体验和长期可维护性
- **性能优先**: 实现智能预加载、资源缓存、懒加载等优化策略，提供流畅用户体验
- **可扩展性**: 支持新微应用的动态注册和加载，灵活适应业务增长和变化
- **安全隔离**: 采用多层级沙箱技术，确保微应用间的安全隔离和资源保护
- **标准化接口**: 统一的微应用接入规范，简化集成流程，提高开发效率

## 📋 **架构概述**

Bone前端采用现代混合式微前端架构，基于无界框架(wujie)实现，旨在构建一个高度可扩展、高性能、易维护的前端应用框架，支持多团队并行协作开发，实现业务模块的独立部署和运行。

### 核心特性

- **独立开发与部署**: 支持多个团队独立开发、测试和部署微应用，降低团队协作成本
- **技术栈无关性**: 微应用可使用React、Vue、Angular等不同技术栈，灵活选择最适合的技术方案
- **高性能**: 通过智能预加载、资源缓存、懒加载等机制优化用户体验和应用性能
- **安全隔离**: 采用多层级沙箱技术确保微应用间的安全隔离，防止样式和脚本冲突
- **统一管理**: 提供统一的应用注册、路由管理、生命周期控制和版本管理
- **无缝通信**: 实现主应用与微应用间的高效通信机制，支持复杂的数据交换和事件传递
- **渐进式采用**: 支持现有应用的渐进式迁移和新应用的逐步接入

## 📐 **架构组件与项目结构**

### 4.1 架构组件设计

架构组件描述了Bone前端系统的功能模块划分和相互关系，采用分层架构设计，确保系统的模块化和可扩展性：

```
├── 主框架 (main-app)
│   ├── 微前端协调器       # 微应用注册、加载和生命周期管理
│   ├── 动态路由系统       # 智能路由匹配和微应用加载
│   ├── 全局状态管理       # 跨应用数据共享和状态同步
│   ├── 共享组件库         # 公共UI组件和业务组件
│   ├── 安全与认证         # 统一认证授权和权限控制
│   ├── 性能监控           # 应用性能指标收集和分析
│   └── 主题与配置管理     # 全局主题和配置管理
├── 微应用
│   ├── 管理门户微应用      # 后台管理功能
│   ├── 数据分析微应用      # 数据可视化和报表功能
│   ├── 工作流引擎微应用    # 业务流程设计和执行
│   ├── 主数据管理微应用    # 核心业务数据管理
│   ├── 元数据管理微应用    # 系统元数据管理
│   └── 用户中心微应用      # 用户管理和个人设置
└── 共享服务
    ├── 消息总线           # 应用间通信机制
    ├── API网关           # 统一API请求管理
    ├── 工具函数库         # 通用工具和辅助函数
    └── 错误处理机制       # 全局错误处理和日志记录
```

### 4.2 Monorepo 项目结构

Bone平台前端采用现代化的Monorepo架构，使用Lerna和Yarn Workspaces进行管理，实现代码共享和依赖管理的最优化，提高开发效率和代码复用率。

```
bone-frontend/                 # 前端根目录
├── apps/                      # 应用目录
│   ├── main-app/              # 主应用 (基座应用)
│   ├── bone-admin/            # 管理门户微应用
│   ├── bone-analytics/        # 数据分析微应用
│   ├── bone-workflow/         # 工作流引擎微应用
│   ├── bone-masterdata/       # 主数据管理微应用
│   ├── bone-metadata/         # 元数据管理微应用
│   ├── bone-tool-codegen/     # 代码生成工具微应用
│   ├── bone-extension-studio/ # 扩展微应用
│   └── bone-iam/              # 用户中心微应用
├── packages/                  # 共享包目录
│   ├── ui-components/         # UI组件库
│   ├── micro-frontend-sdk/    # 微前端SDK
│   ├── shared-utils/          # 共享工具函数
│   ├── api-client/            # API客户端
│   ├── eslint-config/         # ESLint配置
│   ├── theme/                 # 主题包
│   └── hooks/                 # 共享Hooks
├── scripts/                   # 构建和部署脚本
├── docs/                      # 文档
├── .eslintrc.js               # 根目录ESLint配置
├── .prettierrc                # Prettier配置
├── lerna.json                 # Lerna配置
├── package.json               # 根目录package.json
├── tsconfig.json              # 根目录TypeScript配置
└── README.md                  # 项目说明文档
```

### 4.3 目录结构设计优势

- **集中管理**: 所有代码集中在一个仓库中，便于版本管理和代码共享
- **依赖优化**: 通过Yarn Workspaces实现依赖提升，减少重复安装
- **统一规范**: 统一的代码规范、构建流程和测试策略
- **跨应用协作**: 便于跨应用功能开发和问题排查
- **简化发布**: 支持多包版本协同发布和依赖管理
- **明确分离**: 清晰区分应用代码与共享代码，降低耦合度
- **模块化设计**: 符合微前端架构的模块化设计理念，便于维护和扩展

### 目录结构设计优势

- **集中管理**: 所有代码集中在一个仓库中，便于版本管理和代码共享
- **依赖优化**: 通过Yarn Workspaces实现依赖提升，减少重复安装
- **统一规范**: 统一的代码规范、构建流程和测试策略
- **跨应用协作**: 便于跨应用功能开发和问题排查
- **简化发布**: 支持多包版本协同发布和依赖管理

## 🔧 **主框架设计**

### 4.1 主框架架构

主框架作为整个微前端系统的基座，负责协调和管理所有微应用，提供统一的入口、基础设施和运行环境。

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

### 4.2 微前端协调器

微前端协调器是主框架的核心组件，负责微应用的注册、配置、加载和生命周期管理。

```typescript
/**
 * 微应用配置接口
 */
export interface MicroApplicationConfig {
  /** 应用名称 (必填) */
  name: string;
  /** 应用入口地址 (必填) */
  entry: string;
  /** 路由匹配规则 */
  activeRule: string | ((location: Location) => boolean);
  /** 容器选择器 */
  container?: string;
  /** 沙箱配置 */
  sandbox?: boolean | Record<string, any>;
  /** 加载优先级 */
  priority?: number;
  /** 传递给应用的属性 */
  props?: Record<string, any>;
  /** 是否预加载 */
  preload?: boolean;
  /** 是否保活 */
  keepAlive?: boolean;
  /** 应用版本号 */
  version?: string;
  // 其他配置项省略
}

/**
 * 微前端协调器类 - 采用单例模式
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
   * 获取单例实例
   */
  public static getInstance(): MicroFrontendOrchestrator {
    if (!MicroFrontendOrchestrator.instance) {
      MicroFrontendOrchestrator.instance = new MicroFrontendOrchestrator();
    }
    return MicroFrontendOrchestrator.instance;
  }

  /**
   * 注册单个微应用
   */
  public registerApp(config: MicroApplicationConfig): boolean {
    // 验证配置有效性
    if (!config || !config.name || !config.entry) {
      console.error('Invalid application configuration');
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
      return true;
    } catch (error) {
      console.error(`Failed to register application ${config.name}:`, error);
      return false;
    }
  }

  /**
   * 批量注册微应用
   */
  public registerApps(apps: MicroApplicationConfig[]): {
    success: number;
    failed: number;
    registeredApps: string[];
  } {
    let success = 0;
    let failed = 0;
    const registeredApps: string[] = [];

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
   * 初始化微应用
   */
  public async initializeApp(name: string, options?: {
    force?: boolean;
    timeout?: number;
  }): Promise<boolean> {
    const { force = false, timeout = 30000 } = options || {};

    // 缓存和状态检查逻辑
    if (this.initializedApps.has(name) && !force) return true;
    if (this.mountingApps.has(name)) {
      // 处理正在初始化的情况
      try {
        await Promise.race([
          this.mountingApps.get(name)!,
          new Promise<void>((_, reject) => 
            setTimeout(() => reject(new Error('Initialization timeout')), timeout)
          )
        ]);
        return this.initializedApps.has(name);
      } catch (error) {
        console.error(`Application initialization timed out: ${name}`);
        return false;
      }
    }

    // 获取配置
    const config = this.getAppConfig(name);
    if (!config) {
      console.error(`Application config not found: ${name}`);
      return false;
    }

    // 创建初始化Promise
    const initPromise = new Promise<void>(async (resolve, reject) => {
      try {
        // 触发初始化开始事件
        globalEventBus.emit(AppEvents.MICRO_APP_LOAD_START, { name });

        // 加载应用资源
        await this.loadAppResources(config);

        // 标记为已初始化
        this.initializedApps.add(name);

        // 触发初始化完成事件
        globalEventBus.emit(AppEvents.MICRO_APP_LOAD_COMPLETE, { name });
        resolve();
      } catch (error) {
        console.error(`Application initialization failed: ${name}`, error);
        globalEventBus.emit(AppEvents.MICRO_APP_LOAD_ERROR, { 
          name, 
          error: (error as Error).message 
        });
        reject(error);
      } finally {
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
   * 加载应用资源
   */
  private async loadAppResources(config: MicroApplicationConfig): Promise<void> {
    // 检查缓存
    const cacheKey = `${config.name}_${config.version}`;
    if (this.resourceCache.has(cacheKey)) {
      console.log(`Using cached resources for: ${config.name}`);
      return;
    }
    
    // 预加载逻辑
    if (config.preload) {
      try {
        await preloadApp(config.name, config.entry);
        this.resourceCache.set(cacheKey, { timestamp: Date.now() });
      } catch (error) {
        console.warn(`Preload failed for: ${config.name}`, error);
      }
    }
  }

  /**
   * 应用生命周期管理方法
   */
  public activateApp(name: string): void { /* 实现略 */ }
  public deactivateApp(name: string): void { /* 实现略 */ }
  public unloadApp(name: string): boolean { /* 实现略 */ }
  public clear(): void { /* 实现略 */ }
  
  /**
   * 应用状态查询方法
   */
  public isAppInitialized(name: string): boolean { /* 实现略 */ }
  public isAppActive(name: string): boolean { /* 实现略 */ }
  public getAppConfig(name: string): MicroApplicationConfig | undefined { /* 实现略 */ }
  public getAllAppConfigs(): MicroApplicationConfig[] { /* 实现略 */ }
}

// 导出单例实例
export const microFrontendOrchestrator = MicroFrontendOrchestrator.getInstance();
```

### 4.3 应用初始化器

应用初始化器负责微应用的动态注册和初始化流程管理，支持默认应用注册、动态配置加载、预加载优化和启动状态管理。

```typescript
/**
 * 应用初始化器类
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
   */
  public async initialize(options?: {
    dynamicLoad?: boolean;
    defaultApps?: MicroAppConfig[];
    preloadApps?: string[];
  }): Promise<void> {
    // 防止重复初始化
    if (this.isInitialized) return;
    if (this.initPromise) return this.initPromise;

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
      // 触发初始化开始事件
      globalEventBus.emit(AppEvents.INITIALIZATION_START);
      
      // 注册默认应用
      if (options.defaultApps && options.defaultApps.length > 0) {
        this.microAppManager.registerApps(options.defaultApps);
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
      // 触发初始化完成事件
      globalEventBus.emit(AppEvents.INITIALIZATION_COMPLETE);
    } catch (error) {
      console.error('应用初始化失败', error);
      // 触发初始化失败事件
      globalEventBus.emit(AppEvents.INITIALIZATION_ERROR, { error: (error as Error).message });
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
      // 应用配置加载超时控制
      const timeoutPromise = new Promise<never>((_, reject) => 
        setTimeout(() => reject(new Error('应用配置加载超时')), 10000)
      );
      
      const fetchPromise = fetch('/api/micro-apps/config');
      const response = await Promise.race([fetchPromise, timeoutPromise]);
      
      if (!response.ok) throw new Error(`获取应用配置失败: ${response.status}`);

      const apps: MicroAppConfig[] = await response.json();
      if (apps && apps.length > 0) {
        // 验证配置有效性
        const validApps = apps.filter(app => app.name && app.entry && app.activeRule);
        if (validApps.length > 0) {
          this.microAppManager.registerApps(validApps);
        }
        // 记录无效配置
        if (validApps.length < apps.length) {
          console.warn(`发现${apps.length - validApps.length}个无效的应用配置`);
        }
      }
    } catch (error) {
      console.error('动态加载应用配置失败', error);
      // 动态加载失败不应阻止应用启动
      // 可考虑使用降级策略或默认配置
    }
  }

  /**
   * 预加载应用
   */
  private async preloadApps(appNames: string[]): Promise<void> {
    // 限制并发预加载数量，避免资源竞争
    const concurrencyLimit = 3;
    const results: Array<{ name: string; success: boolean; error?: Error }> = [];
    
    // 分批执行预加载
    for (let i = 0; i < appNames.length; i += concurrencyLimit) {
      const batch = appNames.slice(i, i + concurrencyLimit);
      const batchPromises = batch.map(async (name) => {
        try {
          await this.microAppManager.initializeApp(name);
          results.push({ name, success: true });
        } catch (error) {
          console.warn(`预加载应用失败: ${name}`, error);
          results.push({ name, success: false, error: error as Error });
        }
      });
      await Promise.all(batchPromises);
    }
    
    // 记录预加载统计信息
    const successCount = results.filter(r => r.success).length;
    console.log(`应用预加载完成: 成功${successCount}/${appNames.length}`);
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

## 🎯 **主应用 (main-app) 详细结构**

主应用作为微前端系统的基座，提供统一的入口、导航、认证和公共功能，负责微应用的协调和管理。

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
├── vite.config.ts            # Vite配置
└── README.md                 # 项目说明
```

## 🧩 **微应用设计与结构**

### 6.1 微应用架构

微应用是独立的业务模块，具有自己的路由、状态管理和业务逻辑，遵循独立开发、独立部署、独立运行的原则，实现业务模块的解耦和自治。

#### 核心特性

- **独立性**: 拥有独立的代码库、构建流程和部署通道，可独立开发和测试
- **自包含**: 包含完整的业务逻辑、UI组件和数据处理，满足特定业务域需求
- **标准化**: 遵循统一的微前端接口规范和生命周期管理，便于与主框架无缝集成
- **可复用**: 提供可复用的业务能力，可以被多个场景调用和组合
- **松耦合**: 与其他微应用和主框架保持松耦合，降低依赖和影响范围
- **技术灵活性**: 可根据业务需求选择最适合的技术栈和实现方案

### 6.2 微应用 (micro-app) 标准结构

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

## 📦 **共享包详细结构**

共享包是微前端架构中的关键组成部分，提供跨应用复用的能力，确保系统的一致性和开发效率。

### 7.1 UI组件库 (ui-components)

UI组件库是微前端架构中确保视觉一致性和开发效率的核心共享资源。Bone平台采用分层组件设计策略，基于业界成熟UI库进行定制扩展。

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

### 7.2 推荐UI库与选型标准

#### 推荐成熟UI库

基于Bone平台的企业级应用需求和微前端架构特点，推荐以下成熟UI库：

1. **Ant Design**
   - **适用场景**：React技术栈的企业级中后台应用，作为Bone平台的首选UI库
   - **核心优势**：组件丰富（60+）、功能完善、文档详尽、社区活跃、TypeScript支持优秀
   - **集成方案**：
     - **按需加载**：使用babel-plugin-import或手动按需引入，减小打包体积
     - **主题定制**：通过ConfigProvider和less变量覆盖，实现统一的品牌风格
     - **组件封装**：在共享组件库中对核心组件进行二次封装，统一接口和行为
     - **国际化**：配置统一的localeProvider，支持多语言切换
   - **使用建议**：适合管理门户、数据分析、工作流引擎等复杂业务场景
   - **版本建议**：推荐使用Ant Design 5.x版本，支持CSS-in-JS和更现代的设计系统
   - **集成示例**：
     ```typescript
     // 在共享组件库中封装Ant Design组件
     import { Button as AntButton } from 'antd';
     import type { ButtonProps } from 'antd';
     import { LoadingOutlined } from '@ant-design/icons';
     
     interface EnhancedButtonProps extends ButtonProps {
       // 扩展的自定义属性
       bizType?: 'primary' | 'success' | 'warning' | 'error';
       loading?: boolean;
     }
     
     /**
      * 增强版Button组件，统一Bone平台的按钮行为和样式
      */
     export const Button: React.FC<EnhancedButtonProps> = ({
       children,
       loading,
       bizType,
       ...props
     }) => {
       // 根据业务类型映射对应的type
       const getButtonType = () => {
         const typeMap = {
           primary: 'primary',
           success: 'default',
           warning: 'default',
           error: 'default',
         };
         return typeMap[bizType || 'primary'];
       };
       
       // 根据业务类型映射对应的样式
       const getButtonStyle = () => {
         const styleMap = {
           success: { borderColor: '#52c41a', color: '#52c41a' },
           warning: { borderColor: '#faad14', color: '#faad14' },
           error: { borderColor: '#f5222d', color: '#f5222d' },
         };
         return bizType && bizType !== 'primary' ? styleMap[bizType] : {};
       };
       
       return (
         <AntButton
           type={getButtonType()}
           style={getButtonStyle()}
           loading={loading}
           icon={loading ? <LoadingOutlined /> : props.icon}
           {...props}
         >
           {children}
         </AntButton>
       );
     };
     
     // 主题配置示例
     import { ConfigProvider } from 'antd';
     import zhCN from 'antd/es/locale/zh_CN';
     
     const boneTheme = {
       token: {
         colorPrimary: '#1890ff',
         colorSuccess: '#52c41a',
         colorWarning: '#faad14',
         colorError: '#f5222d',
         colorInfo: '#1890ff',
         borderRadius: 4,
       },
       algorithm: [theme.defaultAlgorithm],
     };
     
     // 在主应用中配置全局主题
     const App = () => (
       <ConfigProvider theme={boneTheme} locale={zhCN}>
         {/* 应用内容 */}
       </ConfigProvider>
     );
     ```
   - **性能优化建议**：
     - 配置Tree Shaking，移除未使用的组件和样式
     - 使用虚拟滚动处理大数据量列表（Table、List等）
     - 合理使用React.memo和useMemo避免不必要的重渲染
     - 对于复杂表单，使用Form.Item的shouldUpdate优化渲染性能
     - 使用CSS Modules隔离组件样式，避免全局污染

2. **Element Plus**
   - **适用场景**：Vue技术栈的微应用
   - **核心优势**：轻量级、性能优秀、易于定制
   - **集成方案**：通过微前端沙箱隔离，与React主应用和平共存
   - **使用建议**：适合独立的功能模块和业务流程微应用

3. **Material UI**
   - **适用场景**：需要现代化设计风格的应用
   - **核心优势**：完全遵循Material Design、高度可定制、TypeScript支持优秀
   - **集成方案**：通过共享主题配置统一视觉风格
   - **使用建议**：适合面向用户的交互型界面

4. **TDesign**
   - **适用场景**：企业级应用、管理系统
   - **核心优势**：由腾讯开源、功能全面、设计规范完善
   - **集成方案**：支持多框架版本(React/Vue/Angular)，便于微前端集成
   - **使用建议**：适合需要统一设计语言的复杂系统

5. **PrimeReact**
   - **适用场景**：需要丰富数据展示组件的应用
   - **核心优势**：组件种类齐全、性能优秀、国际化支持完善
   - **集成方案**：通过组件封装适配微前端环境
   - **使用建议**：适合数据密集型应用和报表系统

#### UI库选型标准

选择UI库时，建议从以下维度进行评估：

1. **架构兼容性**：与微前端架构的集成难易程度
2. **性能表现**：包体积、渲染性能、按需加载支持
3. **维护状态**：社区活跃度、更新频率、问题解决速度
4. **功能完整性**：组件覆盖度、业务场景支持能力
5. **定制能力**：主题定制、样式覆盖、组件扩展能力
6. **开发体验**：TypeScript支持、文档质量、调试便利性
7. **国际化支持**：多语言支持、RTL布局支持
8. **无障碍性**：ARIA支持、键盘导航、屏幕阅读器兼容性

#### 最佳实践建议

- **统一基础库**：在React技术栈的微应用中统一使用Ant Design，确保视觉和交互一致性
- **组件再封装**：将Ant Design组件封装为业务组件，统一接口和行为，便于维护和升级
- **主题共享**：通过ConfigProvider和统一的主题配置，实现跨应用的视觉一致性
- **版本锁定**：使用确切版本号（如"antd": "^5.8.6"），避免依赖冲突
- **按需引入**：配置Tree Shaking，使用`import { Button } from 'antd'`代替全量引入
- **性能监控**：监控UI组件的渲染性能，特别关注复杂表单、大数据表格等场景
- **自定义组件库**：建立Bone专属的设计系统，基于Ant Design进行定制化开发
- **使用最新特性**：充分利用Ant Design 5.x的CSS-in-JS、动态主题、虚拟滚动等新特性

Bone平台推荐使用**Ant Design**作为主要UI库，通过精心的封装和定制，打造统一的用户体验，同时保持技术栈的灵活性和可扩展性。在ui-components共享包中，我们将建立一套完整的基于Ant Design的设计系统，包含按钮、表单、表格等常用组件的Bone风格封装，确保全平台的视觉一致性。

### 7.2 微前端SDK (micro-frontend-sdk)

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

## 🔄 **核心架构组件关系**

### 8.1 架构组件层次与交互

```
├── Main Framework (main-app)
│   ├── Micro-Frontend Orchestrator       # 微前端协调器
│   ├── Dynamic Router                    # 动态路由系统
│   ├── Global State Management           # 全局状态管理
│   ├── Shared Component Library          # 共享组件库
│   ├── Security & Authentication         # 安全与认证
│   ├── Resource Manager                  # 资源管理器
│   ├── Performance Monitor               # 性能监控
│   ├── Application Lifecycle Manager     # 应用生命周期管理
│   └── Theme & Config Manager            # 主题与配置管理
├── Micro Applications
│   ├── Admin Portal (bone-admin)         # 管理门户微应用
│   ├── Data Analytics (bone-analytics)   # 数据分析微应用
│   ├── Workflow Engine (bone-workflow)   # 工作流引擎微应用
│   ├── Master Data (bone-masterdata)     # 主数据管理微应用
│   ├── Metadata (bone-metadata)          # 元数据管理微应用
│   ├── Codegen Tool (bone-tool-codegen)  # 代码生成工具微应用
│   └── IAM (bone-iam)                    # 用户中心微应用
└── Shared Services
    ├── Message Bus                       # 应用间通信机制
    ├── API Gateway                       # 统一API请求管理
    ├── Utils & Helpers                   # 工具函数库
    ├── Error Handling                    # 统一错误处理
    └── UI Component Library              # 共享UI组件库
```

### 8.2 组件交互流程图

主应用与微应用、微应用与共享服务之间通过标准化的接口进行交互，形成完整的功能体系：

1. **路由与应用加载流程**：用户访问 -> 主应用路由匹配 -> 微前端协调器加载对应微应用 -> 微应用初始化并挂载
2. **数据共享机制**：全局状态 -> 微应用订阅/更新 -> 状态同步到其他微应用
3. **通信机制**：消息总线 -> 发布/订阅模式 -> 应用间事件传递
4. **生命周期管理**：路由变化 -> 微应用激活/停用 -> 资源加载/卸载
5. **UI一致性保证**：共享主题配置 -> UI组件库 -> 各应用统一视觉风格

### 8.3 微应用通信机制详解

微应用间的通信是微前端架构中的关键环节，Bone平台实现了多层次的通信机制：

#### 1. 基于发布/订阅的消息总线

```typescript
// 消息总线实现示例
class EventBus {
  private events: Map<string, Set<Function>> = new Map();

  // 订阅事件
  on(event: string, callback: Function): void {
    if (!this.events.has(event)) {
      this.events.set(event, new Set());
    }
    this.events.get(event)?.add(callback);
  }

  // 取消订阅
  off(event: string, callback: Function): void {
    if (this.events.has(event)) {
      this.events.get(event)?.delete(callback);
    }
  }

  // 触发事件
  emit(event: string, ...args: any[]): void {
    if (this.events.has(event)) {
      this.events.get(event)?.forEach(callback => {
        try {
          callback(...args);
        } catch (error) {
          console.error(`Error in event handler for ${event}:`, error);
        }
      });
    }
  }

  // 一次性订阅
  once(event: string, callback: Function): void {
    const onceCallback = (...args: any[]) => {
      callback(...args);
      this.off(event, onceCallback);
    };
    this.on(event, onceCallback);
  }
}

// 导出全局实例
export const globalEventBus = new EventBus();
```

#### 2. 基于Props的直接通信

主应用可以通过props向微应用传递数据和方法：

```typescript
// 主应用传递props给微应用
<MicroApp
  name="bone-admin"
  entry="//localhost:8001"
  activeRule="/admin"
  props={{
    userInfo: currentUser,
    token: authToken,
    onUserUpdate: handleUserUpdate,
    themeConfig: globalTheme
  }}
/>
```

#### 3. 全局状态管理

使用Redux或其他状态管理工具实现跨应用的数据共享：

```typescript
// 全局状态管理示例
import { configureStore } from '@reduxjs/toolkit';
import userReducer from './features/userSlice';
import appReducer from './features/appSlice';

export const store = configureStore({
  reducer: {
    user: userReducer,
    app: appReducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: false,
    }),
});

// 在微应用中访问全局状态
export const useGlobalState = () => {
  // 通过微前端SDK访问全局状态
  return microFrontendSdk.getGlobalState();
};
```

#### 4. 共享内存空间

通过代理对象实现安全的跨应用内存共享：

```typescript
// 共享内存空间实现
class SharedMemory {
  private data: Map<string, any> = new Map();
  private listeners: Map<string, Set<Function>> = new Map();

  // 设置共享数据
  set(key: string, value: any): void {
    this.data.set(key, value);
    // 通知监听器
    this.notify(key, value);
  }

  // 获取共享数据
  get(key: string): any {
    return this.data.get(key);
  }

  // 监听数据变化
  watch(key: string, callback: Function): void {
    if (!this.listeners.has(key)) {
      this.listeners.set(key, new Set());
    }
    this.listeners.get(key)?.add(callback);
  }

  // 取消监听
  unwatch(key: string, callback: Function): void {
    if (this.listeners.has(key)) {
      this.listeners.get(key)?.delete(callback);
    }
  }

  // 通知监听器
  private notify(key: string, value: any): void {
    if (this.listeners.has(key)) {
      this.listeners.get(key)?.forEach(callback => {
        try {
          callback(value);
        } catch (error) {
          console.error(`Error in shared memory watcher for ${key}:`, error);
        }
      });
    }
  }
}

export const sharedMemory = new SharedMemory();
```

## 🔧 **工程结构与构建配置**

Bone平台前端采用现代化的Monorepo架构，使用Lerna和Yarn Workspaces进行管理，实现代码共享和依赖管理的最优化，同时配置了完善的构建、测试和部署流程。

### 9.1 构建工具配置 (Vite)

```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';
import { createHtmlPlugin } from 'vite-plugin-html';
import { visualizer } from 'rollup-plugin-visualizer';
import viteCompression from 'vite-plugin-compression';
import viteImagemin from 'vite-plugin-imagemin';
import importToCDN from 'vite-plugin-cdn-import';

// 构建配置
export default defineConfig(({ mode, command }) => {
  const isProduction = mode === 'production';
  const isBuild = command === 'build';
  
  return {
    plugins: [
      react({ 
        jsxRuntime: 'automatic',
        // 启用Fast Refresh
        fastRefresh: true,
        // 优化开发体验
        development: {
          babelPlugins: [
            ['@babel/plugin-transform-react-jsx-source', { runtime: 'automatic' }]
          ]
        }
      }),
      createHtmlPlugin({
        inject: { 
          data: { title: 'Bone Platform' },
          // 注入环境变量
          injectData: {
            NODE_ENV: mode,
            BUILD_TIME: new Date().toISOString()
          }
        }
      }),
      // 生产环境资源压缩
      isProduction && viteCompression({
        algorithm: 'gzip',
        threshold: 10240,
        ext: '.gz',
        deleteOriginFile: false
      }),
      // 图片优化
      isProduction && viteImagemin({
        gifsicle: {
          optimizationLevel: 7,
          interlaced: false,
        },
        optipng: {
          optimizationLevel: 7,
        },
        mozjpeg: {
          quality: 80,
        },
        pngquant: {
          quality: [0.7, 0.8],
          speed: 4,
        },
        svgo: {
          plugins: [
            { name: 'removeViewBox' },
            { name: 'removeEmptyAttrs', active: false }
          ]
        }
      }),
      // 构建体积分析
      isBuild && visualizer({
        open: false,
        filename: 'build-stats.html',
        gzipSize: true,
        brotliSize: true
      }),
      // CDN优化 (生产环境)
      isProduction && importToCDN({
        modules: [
          {
            name: 'react',
            var: 'React',
            path: 'https://cdn.jsdelivr.net/npm/react@18.2.0/umd/react.production.min.js',
          },
          {
            name: 'react-dom',
            var: 'ReactDOM',
            path: 'https://cdn.jsdelivr.net/npm/react-dom@18.2.0/umd/react-dom.production.min.js',
          },
          {
            name: 'antd',
            var: 'antd',
            path: 'https://cdn.jsdelivr.net/npm/antd@5.11.0/dist/antd.min.js',
            css: {
              href: 'https://cdn.jsdelivr.net/npm/antd@5.11.0/dist/reset.css',
              async: true,
              defer: true
            }
          },
          // Ant Design 图标库CDN配置
          {
            name: '@ant-design/icons',
            var: 'AntDesignIcons',
            path: 'https://cdn.jsdelivr.net/npm/@ant-design/icons@5.0.1/dist/index.umd.min.js'
          }
        ]
      })
    ].filter(Boolean),
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src'),
        '@bone': resolve(__dirname, '../packages'),
        // UI库别名，便于统一管理和升级
        'antd': resolve(__dirname, '../node_modules/antd'),
        // Ant Design图标库别名，优化导入路径
        '@ant-design/icons': resolve(__dirname, '../node_modules/@ant-design/icons')
      },
      // 优化Ant Design依赖解析
      dedupe: ['antd', '@ant-design/icons']
    },
    // Ant Design主题定制与CSS优化
    css: {
      // 启用CSS模块化
      modules: {
        localsConvention: 'camelCaseOnly',
        generateScopedName: '[name]__[local]___[hash:base64:5]'
      },
      // 预处理器配置
      preprocessorOptions: {
        less: {
          javascriptEnabled: true,
          // Ant Design主题变量覆盖
          modifyVars: {
            '@primary-color': '#1677FF',
            '@border-radius-base': '4px',
            '@font-size-base': '14px',
            '@text-color': '#333333',
            '@text-color-secondary': '#666666'
          }
        }
      // 配置CSS压缩
      postcss: {
        plugins: [
          // 移除未使用的CSS，特别是Ant Design中未使用的组件样式
          isProduction && require('@fullhuman/postcss-purgecss')({
            content: ['./src/**/*.tsx', './src/**/*.ts', './index.html'],
            safelist: [
              // 保留Ant Design核心组件样式
              /^ant-/, 
              // 保留响应式相关类名
              /^ant-col-/, 
              // 保留动画相关类名
              /^fade-/, 
              /^slide-/, 
              // 保留模态框相关类名
              /^modal-/,
              // 保留加载状态相关类名
              /^spin-/,
              // 保留表单验证相关类名
              /^has-/
            ]
          })
        ].filter(Boolean)
      }
      // 优化解析速度
    extensions: ['.js', '.jsx', '.ts', '.tsx', '.json']
  },
  // 构建优化配置
  build: {
    // 输出目录
    outDir: 'dist',
    // 静态资源目录
    assetsDir: 'assets',
    // 代码分割策略
    rollupOptions: {
      output: {
        // 优化Ant Design等第三方库的代码分割
        manualChunks: {
          // 分离Ant Design核心库
          'antd-core': ['antd'],
          // 分离Ant Design图标库
          'antd-icons': ['@ant-design/icons'],
          // 分离React核心库
          'react-core': ['react', 'react-dom'],
          // 分离常用工具库
          'utils': ['lodash', 'dayjs'],
          // 分离路由库
          'router': ['react-router-dom'],
          // 分离状态管理库
          'state': ['zustand', 'jotai']
        },
        // 命名规范
        chunkFileNames: 'js/[name].[hash:8].js',
        entryFileNames: 'js/[name].[hash:8].js',
        assetFileNames: '[ext]/[name].[hash:8].[ext]'
      },
      // 优化Tree Shaking
      treeshake: {
        // 严格模式，更好地移除未使用的代码（特别是Ant Design组件）
        moduleSideEffects: 'no-external',
        // 确保副作用分析准确性
        correctVarValueBeforeDeclaration: true
      }
    },
    // 优化生产构建速度
    cache: true,
    // 启用源映射（开发环境）
    sourcemap: !isProduction,
    // 启用内联资源限制
    assetsInlineLimit: 4096,
    // 配置minify策略
    minify: isProduction ? 'terser' : 'esbuild',
    // Terser配置，更好地压缩Ant Design代码
    terserOptions: {
      compress: {
        // 移除console和debugger
        drop_console: isProduction,
        drop_debugger: isProduction,
        // 优化变量名
        reduce_vars: true
      },
      format: {
        // 移除注释
        comments: false,
        // 美化输出（开发环境）
        beautify: !isProduction
      }
    }
    },
    // 开发服务器配置
    server: {
      port: 3000,
      host: true,
      // 启用HTTPS
      https: false,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
          // 配置WebSocket支持
          ws: true
        }
      },
      fs: {
        // 允许从项目根目录读取文件
        allow: ['..']
      },
      // 配置热更新
      hmr: {
        overlay: true,
        clientPort: 3000
      }
    },
    build: {
      outDir: 'dist',
      minify: isProduction ? 'terser' : false,
      sourcemap: !isProduction,
      // 增加chunkSize限制
      chunkSizeWarningLimit: 1000,
      // 配置产物清理
      emptyOutDir: true,
      // 配置输出文件名格式
      assetsDir: 'static',
      // 配置产物命名
      rollupOptions: {
        output: {
          // 静态资源命名
          assetFileNames: 'static/[name]-[hash][extname]',
          chunkFileNames: 'static/js/[name]-[hash].js',
          entryFileNames: 'static/js/[name]-[hash].js',
          // 优化Ant Design等第三方库的代码分割
          manualChunks: {
            // 分离Ant Design核心库
            'antd-core': ['antd'],
            // 分离Ant Design图标库
            'antd-icons': ['@ant-design/icons'],
            // 分离React核心库
            'react-core': ['react', 'react-dom'],
            // 分离常用工具库
            'utils': ['lodash', 'dayjs'],
            // 分离路由库
            'router': ['react-router-dom'],
            // 分离状态管理库
            'state': ['zustand', 'jotai']
          }
        },
        // 优化Tree Shaking
        treeshake: {
          // 严格模式，更好地移除未使用的代码（特别是Ant Design组件）
          moduleSideEffects: 'no-external',
          // 确保副作用分析准确性
          correctVarValueBeforeDeclaration: true
        }
      }
    },
    // 环境变量配置
    define: {
      'process.env.NODE_ENV': JSON.stringify(mode),
      // Ant Design 全局配置
      'process.env.ANTD_THEME': JSON.stringify('bone')
          }
        });
```     compress: {
          drop_console: isProduction,
          drop_debugger: isProduction,
          // 其他压缩选项
          pure_funcs: ['console.log', 'console.info']
        },
        format: {
          // 美化输出
          comments: false
        }
      }
    },
    optimizeDeps: {
      // 预构建关键依赖
      include: ['react', 'react-dom', 'antd/es/locale/zh_CN', '@ant-design/icons', '@bone/ui-components'],
      // 优化依赖扫描
      exclude: ['@bone/micro-frontend-sdk'],
      // 强制预构建
      force: true
    },
    // 配置CSS
    css: {
      // 启用CSS模块化
      modules: {
        generateScopedName: isProduction ? '[hash:base64:8]' : '[name]__[local]__[hash:base64:5]'
      },
      // 预处理器配置
      preprocessorOptions: {
        less: {
          // Ant Design主题配置
          modifyVars: {
            'primary-color': '#1890ff',
            'border-radius-base': '4px'
          },
          javascriptEnabled: true
        }
      },
      // 启用CSS sourcemap
      devSourcemap: !isProduction
    }
  };
});
```

### 9.2 性能优化策略

#### 1. 构建时优化

- **代码分割**：使用动态import和手动分包策略，减小初始加载体积
- **树摇优化**：确保package.json中的sideEffects正确配置，移除未使用代码
- **资源压缩**：启用gzip/brotli压缩，优化传输大小
- **CDN加速**：将第三方库通过CDN加载，减轻服务器负担
- **图片优化**：自动压缩和转换图片格式，支持WebP等现代格式
- **字体优化**：使用font-display和子集化字体，提升加载体验

#### 2. 运行时优化

- **懒加载**：组件和路由的按需加载
- **预加载**：智能预加载可能访问的页面和资源
- **缓存策略**：合理设置资源缓存时间和策略
- **虚拟滚动**：处理大数据量列表渲染
- **防抖节流**：优化频繁触发的事件处理
- **Web Workers**：将耗时操作移至后台线程

#### 3. 微前端特定优化

- **应用预加载**：基于用户行为预测预加载微应用
- **资源共享**：公共依赖抽取和共享缓存
- **应用保活**：使用keep-alive机制减少重复加载
- **沙箱复用**：优化沙箱创建和销毁性能
- **通信优化**：减少跨应用通信频率和数据量

### 9.3 安全策略

#### 1. 前端安全最佳实践

- **XSS防护**：使用React/Vue等框架的自动转义，避免直接操作innerHTML
- **CSRF防护**：实现CSRF token验证机制
- **安全头配置**：设置适当的Content-Security-Policy等安全头
- **敏感信息处理**：不在前端存储敏感信息，避免在控制台输出敏感数据
- **依赖扫描**：定期扫描和更新有安全漏洞的依赖包

#### 2. 微前端安全隔离

- **JavaScript沙箱**：使用Proxy和iframe等机制实现代码隔离
- **样式隔离**：使用CSS Modules或Shadow DOM避免样式冲突
- **资源隔离**：限制微应用对全局资源的访问
- **通信安全**：对跨应用通信数据进行验证和加密
- **应用权限控制**：基于用户角色限制对微应用的访问

### 9.4 测试策略

#### 1. 测试分层

- **单元测试**：测试独立的函数和组件
- **集成测试**：测试模块间的交互
- **E2E测试**：模拟用户行为的端到端测试
- **性能测试**：评估应用性能指标

#### 2. 测试工具链

- **Jest**：单元测试和快照测试
- **React Testing Library**：组件测试
- **Cypress**：E2E测试
- **Lighthouse**：性能和可访问性测试
- **Storybook**：组件开发和文档化

```typescript
// Jest配置示例
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'jsdom',
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
    '^@bone/(.*)$': '<rootDir>/../packages/$1',
    '\.(css|less|scss)$': 'identity-obj-proxy'
  },
  transform: {
    '^.+\\.(ts|tsx)$': ['ts-jest', {
      tsconfig: 'tsconfig.test.json'
    }]
  },
  setupFilesAfterEnv: ['<rootDir>/src/setupTests.ts'],
  collectCoverageFrom: [
    'src/**/*.{ts,tsx}',
    '!src/**/*.d.ts',
    '!src/index.tsx',
    '!src/App.tsx'
  ],
  coverageThreshold: {
    global: {
      branches: 80,
      functions: 80,
      lines: 80,
      statements: 80
    }
  }
};
```

## 📝 **目录结构设计说明**

### 10.1 设计原则

1. **模块化组织**：按功能模块划分目录，保持高内聚低耦合，提高代码可维护性
2. **关注点分离**：将组件、服务、工具等不同关注点明确分离，遵循单一职责原则
3. **标准化命名**：采用统一的命名规范（kebab-case文件名，PascalCase组件名），提高代码可读性
4. **可扩展性**：预留扩展点，支持未来功能和微应用的新增，适应业务发展需求
5. **一致性**：主应用与微应用保持相似的目录结构，便于开发和维护，降低学习成本
6. **扁平化与嵌套平衡**：关键目录保持扁平化设计，避免过深的目录嵌套
7. **类型先行**：优先定义TypeScript接口和类型，确保类型安全

### 10.2 关键目录说明

- **apps/**：存放所有独立应用，包括主应用和微应用
- **packages/**：存放可复用的共享包，供所有应用使用
- **scripts/**：存放构建、部署和开发相关的脚本工具
- **docs/**：存放项目文档和API说明

### 10.3 微应用特殊要求

每个微应用必须包含以下关键文件：
- **bootstrap.tsx**：微应用的启动入口，负责与主框架集成，处理生命周期事件
- **index.ts**：模块导出，便于开发和调试，支持独立运行模式
- **vite.config.ts**：构建配置，必须支持微前端集成，包含正确的资源路径配置
- **src/types/micro-app.ts**：定义与主框架交互的接口类型
- **src/config/micro-app.ts**：微应用配置文件，包含应用信息和集成参数

## 🔧 **技术栈配置文件**

### 11.1 根目录关键配置

```
├── lerna.json                 # Lerna配置，管理Monorepo多包发布
├── package.json               # 根项目依赖配置和工作流脚本
├── tsconfig.json              # 全局TypeScript配置
├── tsconfig.base.json         # TypeScript基础配置，供子项目继承
├── .eslintrc.js               # 全局ESLint配置
├── .prettierrc                # Prettier代码格式化配置
├── .gitignore                 # Git忽略配置
├── .editorconfig              # 编辑器配置
└── commitlint.config.js       # Git提交规范配置

### 11.2 应用级配置

```
├── vite.config.ts            # Vite构建工具配置
├── tsconfig.json             # 应用级TypeScript配置
├── .env.*                    # 环境变量配置
└── jest.config.js            # 测试配置
```

## 🚀 **部署与CI/CD**

### 12.1 CI/CD流水线配置

```yaml
# .github/workflows/ci-cd.yml 示例
name: Frontend CI/CD

on:
  push:
    branches: [ main, develop, feature/*, hotfix/* ]
  pull_request:
    branches: [ main, develop ]

jobs:
  lint-and-test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
      with:
        fetch-depth: 0
    
    - name: Setup Node.js
      uses: actions/setup-node@v3
      with:
        node-version: '18'
        cache: 'yarn'
    
    - name: Install dependencies
      run: yarn install --frozen-lockfile
    
    - name: Check code formatting
      run: yarn format:check
    
    - name: Lint code
      run: yarn lint
    
    - name: Run tests
      run: yarn test
    
    - name: Test type checking
      run: yarn typecheck

  build:
    needs: lint-and-test
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Setup Node.js
      uses: actions/setup-node@v3
      with:
        node-version: '18'
        cache: 'yarn'
    
    - name: Install dependencies
      run: yarn install --frozen-lockfile
    
    - name: Build applications
      run: yarn build
    
    - name: Build packages
      run: yarn build:packages
    
    - name: Upload build artifacts
      uses: actions/upload-artifact@v3
      with:
        name: build-artifacts
        path: |
          apps/**/dist
          packages/**/dist
        retention-days: 7

  deploy-staging:
    needs: build
    if: github.event_name == 'push' && github.ref == 'refs/heads/develop'
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/download-artifact@v3
      with:
        name: build-artifacts
        path: ./dist
    
    - name: Deploy to staging
      run: ./scripts/deploy.sh staging
      env:
        STAGING_DEPLOY_KEY: ${{ secrets.STAGING_DEPLOY_KEY }}

  deploy-production:
    needs: build
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    # 生产环境部署需要人工审核
    concurrency: production
    
    steps:
    - uses: actions/download-artifact@v3
      with:
        name: build-artifacts
        path: ./dist
    
    - name: Deploy to production
      run: ./scripts/deploy.sh production
      env:
        PRODUCTION_DEPLOY_KEY: ${{ secrets.PRODUCTION_DEPLOY_KEY }}

  sonarqube-analysis:
    needs: build
    runs-on: ubuntu-latest
    if: github.event_name == 'pull_request'
    
    steps:
    - uses: actions/checkout@v3
    
    - name: SonarQube Scan
      uses: SonarSource/sonarqube-scan-action@master
      env:
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
```

## 📝 **总结**

Bone前端微架构设计方案基于业界最佳实践，采用混合式微前端架构模式，结合了无界框架(wujie)的强大能力，实现了高度可扩展、高性能、易维护的前端应用框架。该方案支持多团队并行开发，业务模块独立部署，同时通过精心设计的协调器和初始化器，确保了微应用间的无缝协作和高效通信。

### 架构优势

通过本架构设计与目录结构方案，Bone平台能够实现：
- **业务模块解耦**：微前端架构实现业务模块的解耦，提高开发效率和代码质量
- **技术栈无关性**：支持不同技术栈的微应用共存，灵活选择最适合的前端技术
- **性能优化**：通过智能预加载、资源缓存、懒加载等机制，提供流畅的用户体验
- **安全隔离**：采用多层级沙箱技术，确保微应用间的安全隔离，防止模块间的相互影响
- **可扩展性**：灵活的动态注册机制，轻松应对业务增长和变化
- **团队协作**：支持多团队并行开发，独立部署，提高开发效率
- **统一管理**：提供统一的应用管理、路由控制和状态同步机制
- **一致的用户体验**：通过共享组件库和主题系统，确保跨应用的视觉和交互一致性

### 实施建议

1. **渐进式迁移**：现有应用可通过渐进式方式迁移到微前端架构，先从非核心业务开始试点
2. **规范先行**：在实施前制定详细的微应用开发规范和接入标准，确保一致性
3. **性能监控**：建立完善的性能监控体系，实时跟踪应用性能指标，及时发现和解决性能瓶颈
4. **持续优化**：根据业务发展和用户反馈，持续优化架构和性能，保持技术先进性
5. **安全审计**：定期进行安全审计，确保微应用间的安全隔离，防范潜在安全风险
6. **技术选型**：为共享组件库选择合适的成熟UI库（推荐Ant Design），并进行统一封装
7. **团队培训**：对开发团队进行微前端架构和相关技术的培训，提升开发能力
8. **文档完善**：建立完善的开发文档和最佳实践指南，降低新成员学习成本

### 未来展望

随着Bone平台的发展，前端架构也将不断演进：

1. **智能化**：引入AI辅助开发和智能优化工具，提升开发效率和应用性能
2. **服务端渲染**：探索SSR和SSG技术在微前端架构中的应用，进一步提升首屏加载性能
3. **跨端能力**：扩展架构支持跨平台应用开发，实现一次开发多端运行
4. **可观测性**：强化应用的可观测性体系，实现全链路监控和问题快速定位
5. **自动化测试**：构建完善的自动化测试和CI/CD流程，保障代码质量和发布效率

该架构设计为Bone企业级开发平台的前端应用提供了坚实的技术基础，支持平台的持续演进和业务创新，适应不断变化的业务需求和技术发展趋势。通过采用业界成熟的UI库和最佳实践，Bone平台将能够构建出高性能、安全可靠、用户体验优秀的企业级应用。

## 📚 **扩展阅读**

- [无界框架官方文档](https://wujie-micro.github.io/doc/)
- [微前端架构实践指南](https://micro-frontends.org/)
- [Lerna + Yarn Workspaces 最佳实践](https://lerna.js.org/)
- [Vite 构建优化指南](https://vitejs.dev/guide/build.html)
- [TypeScript 高级类型系统](https://www.typescriptlang.org/docs/handbook/2/advanced-types.html)