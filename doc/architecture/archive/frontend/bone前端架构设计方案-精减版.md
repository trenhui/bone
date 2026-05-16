> **⚠️ 历史草案（已废止）**  
> 前端工程与微前端的**唯一权威**为 [`bone-前端架构.md`](./bone-前端架构.md)；UI 规范见 [`frontend/frontend-ui-spec.md`](./frontend/frontend-ui-spec.md)。本文仅作归档参考，勿作为实现依据。

# 🎨 Bone 前端微架构设计方案

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

## 📋 **架构概述**

Bone前端采用现代混合式微前端架构，基于无界框架(wujie)实现，旨在构建一个高度可扩展、高性能、易维护的前端应用框架，支持多团队并行协作开发，实现业务模块的独立部署和运行。

### 核心特性

- **独立开发**: 支持多个团队独立开发、测试和部署微应用
- **技术无关**: 微应用可使用React、Vue、Angular等不同技术栈
- **高性能**: 通过智能预加载、资源缓存、懒加载等机制优化用户体验
- **安全隔离**: 采用多层级沙箱技术确保微应用间的安全隔离
- **统一管理**: 提供统一的应用注册、路由管理和生命周期控制
- **无缝通信**: 实现主应用与微应用间的高效通信机制

## 📐 **架构组件**

```
├── 主框架 (main-app)
│   ├── 微前端协调器       # 微应用注册、加载和生命周期管理
│   ├── 动态路由系统       # 智能路由匹配和微应用加载
│   ├── 全局状态管理       # 跨应用数据共享和状态同步
│   ├── 共享组件库         # 公共UI组件和业务组件
│   ├── 安全与认证         # 统一认证授权和权限控制
│   └── 性能监控           # 应用性能指标收集和分析
├── 微应用
│   ├── 管理门户微应用      # 后台管理功能
│   ├── 数据分析微应用      # 数据可视化和报表功能
│   ├── 工作流引擎微应用    # 业务流程设计和执行
│   └── 用户中心微应用      # 用户管理和个人设置
└── 共享服务
    ├── 消息总线           # 应用间通信机制
    ├── API网关           # 统一API请求管理
    └── 工具函数库         # 通用工具和辅助函数
```

## 🔧 **主框架设计**

### 3.1 主框架架构

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

### 3.2 微前端协调器

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

### 3.3 应用初始化器

应用初始化器负责微应用的动态注册和初始化流程管理，支持默认应用注册、动态配置加载和预加载优化。

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
    } catch (error) {
      console.error('应用初始化失败', error);
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
      const response = await fetch('/api/micro-apps/config');
      if (!response.ok) throw new Error(`获取应用配置失败: ${response.status}`);

      const apps: MicroAppConfig[] = await response.json();
      if (apps && apps.length > 0) {
        this.microAppManager.registerApps(apps);
      }
    } catch (error) {
      console.error('动态加载应用配置失败', error);
      // 动态加载失败不应阻止应用启动
    }
  }

  /**
   * 预加载应用
   */
  private async preloadApps(appNames: string[]): Promise<void> {
    const promises = appNames.map(async (name) => {
      try {
        await this.microAppManager.initializeApp(name);
      } catch (error) {
        console.warn(`预加载应用失败: ${name}`, error);
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

## 🧩 **微应用设计**

### 4.1 微应用架构

微应用是独立的业务模块，具有自己的路由、状态管理和业务逻辑，遵循独立开发、独立部署、独立运行的原则。

#### 核心特性

- **独立性**: 拥有独立的代码库、构建流程和部署通道
- **自包含**: 包含完整的业务逻辑、UI组件和数据处理
- **标准化**: 遵循统一的微前端接口规范，便于与主框架集成
- **可复用**: 提供可复用的业务能力，可以被多个场景调用

## 🔧 **工程结构与构建配置**

Bone平台前端采用现代化的Monorepo架构，使用Lerna和Yarn Workspaces进行管理，实现代码共享和依赖管理的最优化。

### 4.1 构建工具配置 (Vite)

```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';
import { createHtmlPlugin } from 'vite-plugin-html';

// 构建配置
export default defineConfig(({ mode }) => {
  const isProduction = mode === 'production';
  
  return {
    plugins: [
      react({ jsxRuntime: 'automatic' }),
      createHtmlPlugin({
        inject: { data: { title: 'Bone Platform' } }
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
        '/api': { target: 'http://localhost:8080', changeOrigin: true }
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
    }
  };
});
```

## 🚀 **部署与CI/CD**

### 5.1 CI/CD流水线配置

```yaml
# .github/workflows/ci-cd.yml 示例
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
          ./scripts/deploy.sh production
        else
          ./scripts/deploy.sh staging
        fi
```

## 📝 **总结**

Bone前端微架构设计方案基于业界最佳实践，采用混合式微前端架构模式，结合了无界框架的强大能力，实现了高度可扩展、高性能、易维护的前端应用框架。该方案支持多团队并行开发，业务模块独立部署，同时通过精心设计的协调器和初始化器，确保了微应用间的无缝协作和高效通信。

通过本方案，Bone平台能够实现：
- 业务模块解耦，提高开发效率和代码质量
- 技术栈无关性，灵活选择最适合的前端技术
- 性能优化，提供流畅的用户体验
- 安全隔离，防止模块间的相互影响
- 可扩展性，轻松应对业务增长和变化

该架构设计为Bone企业级开发平台的前端应用提供了坚实的技术基础，支持平台的持续演进和业务创新。