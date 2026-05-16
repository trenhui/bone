> **⚠️ 历史草案（已废止）**  
> 前端工程与微前端的**唯一权威**为 [`bone-前端架构.md`](./bone-前端架构.md)；UI 规范见 [`frontend/frontend-ui-spec.md`](./frontend/frontend-ui-spec.md)。本文仅作归档参考，勿作为实现依据。

# 🎨 Bone 前端微前端架构设计方案

## 🎯 核心设计理念

### BONE 架构原则

```typescript
/**
 * BONE 前端架构核心原则
 * B - Business Domain Driven (业务领域驱动)
 * O - Optimized Developer Experience (优化开发体验)
 * N - Native Micro-Frontends (原生微前端)
 * E - Extensible & Enterprise Ready (可扩展企业级)
 */
```

### 架构设计原则

- **领域驱动设计**: 按业务域划分微应用，实现高内聚低耦合
- **开发体验优先**: 完善的工具链、热更新、类型安全
- **渐进式架构**: 支持从单体到微前端的平滑迁移
- **企业级标准**: 安全、性能、监控、可观测性
- **平台化思维**: 统一的开发规范、构建部署、运维体系

## 🏗️ 架构全景图

### 技术架构层次

```
┌───────────────────────────────────────────────────────────────────────────┐
│                   用户体验层 (User Experience Layer)                        │
├───────────────────────┬───────────────┬───────────────┬─────────────────┤
│   主应用 Shell        │   微应用A     │   微应用B     │   微应用C       │
├───────────────────────┴───────────────┴───────────────┴─────────────────┤
│             微前端框架层 (Micro-Frontend Framework Layer)                   │
├─────────┬───────────┬───────────┬───────────┬─────────────────┬─────────┤
│ 应用注册 │ 路由管理  │ 状态管理  │ 通信总线  │ 生命周期管理    │ 沙箱系统 │
├─────────┴───────────┴───────────┴───────────┴─────────────────┴─────────┤
│                 数据层 (Data Layer)                                       │
├─────────┬───────────┬───────────────────────────────┬────────────────────┤
│ API集成 │ 状态管理  │ 缓存管理                       │ 数据转换与验证     │
├─────────┴───────────┴───────────────────────────────┴────────────────────┤
│                 平台服务层 (Platform Services Layer)                      │
├─────────┬───────────┬───────────┬───────────┬─────────────────┬─────────┤
│ 设计系统 │ 组件库   │ 工具库    │ 类型系统  │ 国际化支持      │ 主题系统 │
├─────────┴───────────┴───────────┴───────────┴─────────────────┴─────────┤
│                 基础设施层 (Infrastructure Layer)                        │
├─────────┬───────────┬───────────┬───────────┬─────────────────┬─────────┤
│ 构建工具 │ 开发服务 │ CI/CD     │ 监控系统  │ 安全机制        │ 日志系统 │
└─────────┴───────────┴───────────┴───────────┴─────────────────┴─────────┘
```

### 核心技术栈选择

根据现代前端工程化最佳实践，Bone前端架构采用以下核心技术栈：

| 类别 | 技术 | 版本 | 选型理由 |
|------|------|------|----------|
| 构建工具 | Vite | 4.x+ | 极速开发体验，优化的构建性能，原生ESM支持 |
| 包管理 | PNPM | 8.x+ | 高效的依赖管理，硬链接机制节省磁盘空间，workspace支持 |
| 框架 | React | 18.x+ | 组件化思想，虚拟DOM，生态丰富，团队熟悉度高 |
| 路由 | React Router | 6.x+ | 声明式路由，嵌套路由支持，微前端友好 |
| 状态管理 | React Context + Zustand | - | 轻量级状态管理，避免过度设计，性能优异 |
| 样式方案 | Styled Components + Design Tokens | - | 组件级样式隔离，主题定制灵活，开发体验良好 |
| 类型系统 | TypeScript | 5.x+ | 静态类型检查，提升代码质量，增强开发体验 |
| 测试框架 | Vitest + React Testing Library | - | 与Vite集成良好，快速的测试执行，贴近用户行为的测试方法 |
| API通信 | Axios + React Query | - | 请求拦截、响应处理，缓存管理，数据同步 |
| 代码规范 | ESLint + Prettier + Husky | - | 统一代码风格，在提交前自动检查和格式化 |

## 📁 项目结构设计

### Monorepo 结构 (优化版)

Bone前端采用现代化的Monorepo架构，通过PNPM Workspace管理多包项目，实现代码共享、依赖复用和统一的构建流程。

```
bone-frontend/
├── apps/                              # 应用层 - 包含所有独立部署的应用
│   ├── platform-shell/                # 平台壳应用 - 微前端容器应用
│   ├── admin-portal/                  # 管理门户 - 后台管理界面
│   ├── data-analytics/                # 数据分析 - 数据可视化平台
│   ├── workflow-engine/               # 工作流引擎 - 流程设计与管理
│   ├── master-data/                   # 主数据管理 - 企业核心数据管理
│   ├── tools/                         # 工具库 - 开发辅助工具,比如代码生成
│   ├── identity-access/               # 身份访问管理 - 用户认证与授权
│   └── developer-tools/               # 开发者工具 - 平台开发辅助工具
├── packages/                          # 平台能力层 - 可复用的核心包
│   ├── core/                          # 核心框架 - 微前端基础能力
│   │   ├── micro-fe-runtime/          # 微前端运行时 - 应用加载与生命周期管理
│   │   ├── application-manager/       # 应用管理器 - 应用注册与调度
│   │   ├── state-management/          # 状态管理 - 全局状态与应用间状态共享
│   │   ├── event-bus/                 # 事件总线 - 应用间通信机制
│   │   └── sandbox/                   # 沙箱系统 - 应用隔离与安全
│   ├── ui/                            # UI 层 - 用户界面相关组件与系统
│   │   ├── design-system/             # 设计系统 - 设计令牌与设计规范
│   │   ├── components/                # 组件库 - 可复用UI组件
│   │   ├── theme/                     # 主题系统 - 主题定义与切换
│   │   ├── layouts/                   # 布局系统 - 页面布局组件
│   │   ├── icons/                     # 图标库 - SVG图标集合
│   │   └── hooks/                     # 自定义Hooks - UI相关逻辑复用
│   ├── data/                          # 数据层 - 数据处理与管理
│   │   ├── api/                       # API客户端 - HTTP请求封装
│   │   ├── query/                     # 数据查询 - 缓存与状态同步
│   │   ├── store/                     # 数据存储 - 本地状态管理
│   │   └── schemas/                   # 数据校验 - 数据结构验证
│   ├── infrastructure/                # 基础设施 - 支撑系统运行的底层能力
│   │   ├── storage/                   # 存储管理 - 本地存储封装
│   │   ├── logging/                   # 日志系统 - 日志记录与上报
│   │   ├── error-handling/            # 错误处理 - 统一错误捕获与处理
│   │   ├── services/                  # 公共服务 - 通用服务抽象
│   │   ├── monitoring/                # 监控系统 - 性能与异常监控
│   │   └── security/                  # 安全机制 - 认证授权与安全策略
│   ├── utilities/                     # 工具库 - 通用工具函数
│   │   ├── shared-utils/              # 共享工具 - 基础工具集
│   │   ├── date-utils/                # 日期工具 - 时间处理
│   │   ├── format-utils/              # 格式化工具 - 数据格式化
│   │   ├── validation/                # 验证工具 - 数据校验
│   │   └── i18n/                      # 国际化 - 多语言支持
│   └── types/                         # 类型定义 - 共享TypeScript类型
│       ├── core-types/                # 核心类型 - 框架核心类型
│       ├── api-types/                 # API 类型 - 接口数据类型
│       └── business-types/            # 业务类型 - 业务领域类型
├── tools/                             # 开发工具 - 开发流程与质量保障工具
│   ├── cli/                           # 命令行工具 - 项目脚手架与命令
│   ├── generators/                    # 代码生成器 - 模板代码生成
│   ├── eslint-config/                 # ESLint 配置 - 代码质量规范
│   ├── typescript-config/             # TypeScript 配置 - 类型检查配置
│   ├── vite-config/                   # Vite 配置 - 构建配置
│   └── test-utils/                    # 测试工具 - 测试辅助函数
├── scripts/                           # 构建部署脚本 - CI/CD与运维脚本
├── docs/                              # 项目文档 - 架构、API、开发指南
├── .github/                           # GitHub 工作流 - Actions配置
└── config/                            # 配置文件 - 环境配置与应用配置
```

### 模块职责划分

| 模块类型 | 主要职责 | 特点 |
|---------|---------|------|
| **apps** | 业务应用实现 | 独立部署，可单独开发测试 |
| **packages/core** | 微前端框架核心能力 | 提供微前端运行时、沙箱隔离等基础设施 |
| **packages/ui** | 用户界面组件与设计系统 | 提供统一的设计语言和组件库 |
| **packages/data** | 数据获取与管理 | 封装API调用、缓存策略和数据处理 |
| **packages/infrastructure** | 支撑系统运行的底层能力 | 提供日志、监控、安全等通用服务 |
| **packages/utilities** | 工具函数与辅助方法 | 提供跨模块复用的通用功能 |
| **packages/types** | 类型定义与接口规范 | 确保类型安全与接口一致性 |
| **tools** | 开发工具与构建配置 | 提升开发效率和代码质量 |

### UI层和基础设施层目录命名优化建议

基于业界最佳实践和现代前端工程命名规范，以下是对UI层和基础设施层目录名称的优化建议：

#### UI层目录命名优化

##### 优化前
```
ui/
├── design-system/       # 设计系统
├── component-library/   # 组件库
├── theme-provider/      # 主题系统
├── layout-system/       # 布局系统
└── icon-library/        # 图标库（新增）
```

##### 优化建议
```
ui/
├── design-system/       # 设计系统（保持不变，符合通用术语）
├── components/          # 组件库（简化名称，更符合现代前端规范）
├── theme/               # 主题系统（简化名称，移除"provider"，更通用）
├── layouts/             # 布局系统（使用复数形式，更标准）
└── icons/               # 图标库（简化名称，使用复数形式，更符合通用命名）
```

#### 基础设施层目录命名优化

##### 优化前
```
infrastructure/
├── storage/     # 存储管理
├── logger/              # 日志系统
├── error-handler/       # 错误处理
└── api/          # API客户端（新增）
```

##### 优化建议
```
infrastructure/
├── storage/             # 存储管理（简化名称，移除"manager"，更通用）
├── logging/             # 日志系统（使用动名词形式，更专业）
├── error-handling/      # 错误处理（使用动名词形式，更标准）
├── services/            # 公共服务（新增，统一管理各种基础设施服务）
├── monitoring/          # 监控系统（新增，性能与异常监控）
└── security/            # 安全机制（新增，认证授权与安全策略）
```

> **注意**：API客户端功能已从infrastructure移至新的数据层(data)中，以更好地分离关注点。

### 数据层目录命名优化

为了更好地分离数据处理和基础设施关注点，我们新增了独立的数据层(data)，并采用以下命名规范：

```
data/
├── api/                 # API客户端 - HTTP请求封装，包含REST、GraphQL等接口调用
├── query/               # 数据查询 - 基于React Query的数据获取、缓存与状态同步
├── store/               # 数据存储 - 本地状态管理，封装Zustand状态库
└── schemas/             # 数据校验 - 使用Zod等工具进行数据结构验证
```

#### 优化理由与最佳实践依据

1. **名称简化原则**：
   - 移除冗余词汇（如"provider"、"manager"、"library"），使名称更简洁
   - 采用业内通用的短名称，提高辨识度和一致性

2. **复数形式规范**：
   - 集合类目录（如组件、布局、图标）使用复数形式是前端工程的广泛实践
   - 提高了代码的可读性和直观性

3. **动名词形式**：
   - 对于功能性目录（如日志、错误处理），使用动名词形式（logging、error-handling）更符合英语语法习惯
   - 更准确表达该目录的功能定位

4. **命名一致性**：
   - 与业界流行框架和库的命名保持一致（如React、Angular、Vue生态系统）
   - 符合Bone前端工程命名规范

5. **关注点分离**：
   - 将API客户端从基础设施层移至数据层，更符合关注点分离原则
   - 清晰区分数据获取/处理与基础设施服务的职责边界

6. **分层架构优化**：
   - 新增独立的数据层，强化了前端架构的分层设计
   - 提供了更清晰的数据流管理和状态隔离

7. **扩展性考虑**：
   - 为未来的数据源扩展（如WebSocket、WebRTC等）预留了清晰的结构
   - 便于添加新的数据处理模块，如数据转换、数据聚合等功能

## 🔧 核心架构实现

### 微前端运行时 (Micro-FE Runtime)

```typescript
// packages/core/micro-fe-runtime/src/types.ts
export interface MicroAppConfig {
  id: string;                        // 应用唯一标识
  name: string;                      // 应用名称
  version: string;                   // 版本号
  entry: string | string[];          // 入口地址
  activeRule: RouteRule;             // 激活规则
  container?: string;                // 容器选择器
  props?: Record<string, any>;       // 初始化属性
  metadata?: AppMetadata;            // 应用元数据
}

export interface AppMetadata {
  description?: string;
  author?: string;
  dependencies?: string[];
  permissions?: string[];
  technology?: {
    framework: 'react' | 'vue' | 'angular' | 'svelte';
    version: string;
  };
}

// packages/core/micro-fe-runtime/src/application.ts
export class MicroApplication {
  private status: AppStatus = 'NOT_LOADED';
  private sandbox: Sandbox | null = null;
  private lifecycle: AppLifecycle;
  private errorHandler: ErrorHandler;
  private performanceMonitor: PerformanceMonitor;
  private securityEnforcer: SecurityPolicyEnforcer;
  private resourceCache: ResourceCache;

  constructor(private config: MicroAppConfig) {
    this.lifecycle = new AppLifecycle(this);
    this.errorHandler = new ErrorHandler(config.id);
    this.performanceMonitor = new PerformanceMonitor(config.id);
    this.securityEnforcer = new SecurityPolicyEnforcer(config.metadata?.permissions || []);
    this.resourceCache = new ResourceCache(config.id);
  }

  async load(): Promise<void> {
    if (this.status !== 'NOT_LOADED') return;
    
    // 安全策略检查
    if (!this.securityEnforcer.checkPermission('load')) {
      throw new SecurityError(`App ${this.config.id} doesn't have permission to load`);
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
      this.sandbox = await SandboxFactory.create(this.config);
      this.performanceMonitor.record('sandboxCreate', performance.now() - sandboxStartTime);
      
      // 4. 执行应用代码
      const evalStartTime = performance.now();
      await this.sandbox.eval(resources);
      this.performanceMonitor.record('codeEval', performance.now() - evalStartTime);
      
      // 5. 调用应用生命周期
      await this.lifecycle.bootstrap();
      
      this.status = 'NOT_MOUNTED';
      this.performanceMonitor.record('totalLoad', performance.now() - loadStartTime);
      
      // 记录应用加载成功
      this.performanceMonitor.reportSuccess('appLoad');
    } catch (error) {
      this.status = 'LOAD_ERROR';
      
      // 记录错误并上报
      this.errorHandler.handle(error, {
        phase: 'load',
        appInfo: this.config
      });
      
      throw error;
    }
  }

  async mount(container: HTMLElement, props?: any): Promise<void> {
    if (this.status !== 'NOT_MOUNTED') return;
    
    const mountStartTime = performance.now();
    this.status = 'MOUNTING';
    
    try {
      // 增强的属性传递，包含上下文信息
      const mountProps = {
        ...this.config.props,
        ...props,
        __boneContext: {
          appId: this.config.id,
          version: this.config.version,
          runtime: { version: BONE_RUNTIME_VERSION }
        }
      };
      
      await this.lifecycle.mount(container, mountProps);
      this.status = 'MOUNTED';
      
      this.performanceMonitor.record('mount', performance.now() - mountStartTime);
    } catch (error) {
      this.status = 'MOUNT_ERROR';
      
      // 记录错误并上报
      this.errorHandler.handle(error, {
        phase: 'mount',
        appInfo: this.config
      });
      
      throw error;
    }
  }

  async unmount(): Promise<void> {
    if (this.status !== 'MOUNTED') return;
    
    const unmountStartTime = performance.now();
    this.status = 'UNMOUNTING';
    
    try {
      await this.lifecycle.unmount();
      
      // 释放资源
      if (this.sandbox) {
        this.sandbox.destroy();
      }
      
      this.status = 'NOT_MOUNTED';
      this.performanceMonitor.record('unmount', performance.now() - unmountStartTime);
    } catch (error) {
      this.status = 'UNMOUNT_ERROR';
      
      // 记录错误并上报
      this.errorHandler.handle(error, {
        phase: 'unmount',
        appInfo: this.config
      });
      
      throw error;
    }
  }
}
```

### 应用管理器 (Application Manager)

```typescript
// packages/core/application-manager/src/registry.ts
export class ApplicationRegistry {
  private apps: Map<string, MicroApplication> = new Map();
  private loaders: Map<string, AppLoader> = new Map();

  register(config: MicroAppConfig): void {
    const app = new MicroApplication(config);
    this.apps.set(config.id, app);
    
    // 预加载资源
    if (config.metadata?.preload) {
      this.preloadApp(app);
    }
  }

  async getApp(appId: string): Promise<MicroApplication> {
    const app = this.apps.get(appId);
    if (!app) {
      throw new Error(`Application ${appId} not found`);
    }
    
    if (app.status === 'NOT_LOADED') {
      await app.load();
    }
    
    return app;
  }

  async activateApp(appId: string, container: HTMLElement, props?: any): Promise<void> {
    const app = await this.getApp(appId);
    
    // 停用当前活跃应用
    await this.deactivateActiveApps();
    
    // 激活目标应用
    await app.mount(container, props);
    
    // 更新路由状态
    this.updateRouteState(appId);
  }

  private async preloadApp(app: MicroApplication): Promise<void> {
    // 使用 requestIdleCallback 进行预加载
    if ('requestIdleCallback' in window) {
      requestIdleCallback(async () => {
        try {
          await app.load();
        } catch (error) {
          console.warn(`Preload failed for app ${app.config.name}:`, error);
        }
      });
    }
  }
  
  private async deactivateActiveApps(): Promise<void> {
    // 停用所有已挂载的应用
    for (const [id, app] of this.apps.entries()) {
      if (app.status === 'MOUNTED') {
        await app.unmount();
      }
    }
  }
  
  private updateRouteState(appId: string): void {
    // 更新路由状态，确保应用激活与路由匹配
    // 实际实现中可能需要与路由系统集成
    console.log(`Route state updated for app: ${appId}`);
  }
}
```

### 沙箱系统 (Sandbox System)

```typescript
// packages/core/micro-fe-runtime/src/sandbox/proxy-sandbox.ts
export class EnhancedProxySandbox implements Sandbox {
  private running = false;
  private globalContext: typeof window;
  private fakeWindow: Record<string, any>;
  private proxyWindow: Window & typeof globalThis;
  private sideEffects: Map<string, any> = new Map();
  private activeProperties: Set<string | symbol> = new Set();
  private securityPolicy: SandboxSecurityPolicy;
  private resourceLimits: ResourceLimits;
  
  constructor(
    private appId: string,
    options: SandboxOptions = {}
  ) {
    this.fakeWindow = Object.create(null);
    this.globalContext = window;
    this.securityPolicy = options.securityPolicy || new DefaultSecurityPolicy();
    this.resourceLimits = options.resourceLimits || {
      maxMemoryMB: 100,
      maxExecutionTimeMS: 30000
    };
    
    // 预热常用全局属性，提高性能
    this.preloadCommonGlobals();
    
    this.proxyWindow = this.createProxyWindow();
  }

  private preloadCommonGlobals(): void {
    // 预加载常用安全的全局对象，避免频繁查找原型链
    const safeGlobals = ['Math', 'JSON', 'Date', 'RegExp', 'Array', 'Object', 'String', 'Number', 'Boolean'];
    safeGlobals.forEach(key => {
      if (key in this.globalContext) {
        this.fakeWindow[key] = this.globalContext[key];
      }
    });
  }

  private createProxyWindow(): Window & typeof globalThis {
    const self = this;
    
    return new Proxy(this.fakeWindow, {
      get: (target, p) => {
        // 记录访问的属性
        self.activeProperties.add(p);
        
        // 1. 优先从 fakeWindow 获取
        if (p in target) {
          return target[p];
        }
        
        // 2. 安全检查
        const accessPolicy = self.securityPolicy.checkAccess('global', p);
        if (accessPolicy === 'block') {
          console.warn(`[Sandbox] Access to global.${String(p)} is blocked for app ${self.appId}`);
          return undefined;
        }
        
        // 3. 从全局 window 获取，并可能进行安全包装
        const globalValue = self.globalContext[p];
        
        // 对危险对象进行安全包装
        if (self.isDangerousObject(p, globalValue) && accessPolicy === 'wrap') {
          return self.wrapDangerousObject(p, globalValue);
        }
        
        return globalValue;
      },
      
      set: (target, p, value) => {
        // 安全检查
        if (!self.securityPolicy.checkWrite('global', p)) {
          console.warn(`[Sandbox] Write to global.${String(p)} is blocked for app ${self.appId}`);
          return false;
        }
        
        // 记录副作用
        const previousValue = target[p];
        if (previousValue !== value) {
          if (previousValue !== undefined) {
            self.sideEffects.set(String(p), previousValue);
          }
          
          // 设置值前进行安全检查
          if (self.isMaliciousValue(value)) {
            console.error(`[Sandbox] Potential malicious value detected for global.${String(p)} in app ${self.appId}`);
            return false;
          }
          
          target[p] = value;
        }
        
        return true;
      },
      
      has: (target, p) => {
        return p in target || p in self.globalContext;
      },
      
      deleteProperty: (target, p) => {
        // 安全检查
        if (!self.securityPolicy.checkDelete('global', p)) {
          console.warn(`[Sandbox] Delete global.${String(p)} is blocked for app ${self.appId}`);
          return false;
        }
        
        if (p in target) {
          // 记录删除的属性，用于恢复
          self.sideEffects.set(String(p), target[p]);
          delete target[p];
          return true;
        }
        
        return false;
      }
    });
  }

  async eval(code: string): Promise<any> {
    this.running = true;
    
    // 记录执行开始时间
    const startTime = performance.now();
    
    try {
      // 内存使用检查
      this.checkMemoryUsage();
      
      // 使用沙箱执行代码
      const result = await this.executeInSandbox(code);
      
      // 检查执行时间
      const executionTime = performance.now() - startTime;
      if (executionTime > this.resourceLimits.maxExecutionTimeMS) {
        console.warn(`[Sandbox] App ${this.appId} execution time exceeds limit: ${executionTime}ms`);
      }
      
      return result;
    } catch (error) {
      console.error(`[Sandbox] Error executing code in app ${this.appId}:`, error);
      throw error;
    } finally {
      this.running = false;
    }
  }
  
  private async executeInSandbox(code: string): Promise<any> {
    // 创建隔离的执行环境
    const sandboxFn = new Function('global', 'window', 'self', code);
    
    // 使用结构化克隆深拷贝全局对象引用，避免直接修改
    const clonedGlobals = this.createClonedGlobals();
    
    // 执行代码
    return await sandboxFn.call(this.proxyWindow, clonedGlobals, clonedGlobals, clonedGlobals);
  }
  
  private createClonedGlobals(): Record<string, any> {
    // 创建一个安全的全局对象副本供微应用使用
    const cloned = Object.create(null);
    
    // 复制必要的安全全局对象
    Object.keys(this.fakeWindow).forEach(key => {
      cloned[key] = this.fakeWindow[key];
    });
    
    // 添加上下文信息
    cloned.__boneSandboxContext = {
      appId: this.appId,
      timestamp: Date.now(),
      sandboxVersion: '1.0.0'
    };
    
    return cloned;
  }
  
  private checkMemoryUsage(): void {
    // 检查内存使用情况（在支持的浏览器中）
    if (performance && 'memory' in performance) {
      const memoryUsage = (performance as any).memory.usedJSHeapSize / 1024 / 1024; // MB
      if (memoryUsage > this.resourceLimits.maxMemoryMB) {
        throw new Error(`Sandbox memory limit exceeded: ${memoryUsage.toFixed(2)}MB > ${this.resourceLimits.maxMemoryMB}MB`);
      }
    }
  }

  private isDangerousObject(prop: string | symbol, value: any): boolean {
    // 识别危险的全局对象
    const dangerousProps = ['document', 'location', 'localStorage', 'sessionStorage', 'indexedDB'];
    return dangerousProps.includes(String(prop)) && typeof value === 'object';
  }
  
  private wrapDangerousObject(prop: string | symbol, original: any): any {
    // 为危险对象创建安全的包装器
    const propName = String(prop);
    
    if (propName === 'document') {
      // 返回一个受限制的document对象
      return new DocumentWrapper(original, this.appId);
    } else if (propName === 'location') {
      // 返回一个受限制的location对象
      return new LocationWrapper(original, this.appId);
    } else if (['localStorage', 'sessionStorage'].includes(propName)) {
      // 返回一个命名空间隔离的存储对象
      return new NamespacedStorage(original, this.appId);
    }
    
    return original;
  }
  
  private isMaliciousValue(value: any): boolean {
    // 简单的恶意代码检测
    if (typeof value === 'function') {
      const fnStr = value.toString();
      // 检测常见的恶意代码模式
      const maliciousPatterns = [
        /eval\(/,
        /new Function\(/,
        /document\.write\(/,
        /\<script/i
      ];
      
      return maliciousPatterns.some(pattern => pattern.test(fnStr));
    }
    
    return false;
  }
  
  // 清理沙箱，释放资源
  destroy(): void {
    // 清空fakeWindow
    this.fakeWindow = Object.create(null);
    
    // 清空记录
    this.sideEffects.clear();
    this.activeProperties.clear();
    
    // 重置状态
    this.running = false;
  }
  
  // 获取沙箱状态信息
  getStatus(): SandboxStatus {
    return {
      appId: this.appId,
      running: this.running,
      activePropertiesCount: this.activeProperties.size,
      sideEffectsCount: this.sideEffects.size
    };
  }
}
```

## 🎨 设计系统架构

Bone设计系统采用原子设计方法论，构建了从设计令牌到复杂组件的完整设计语言。系统包含设计令牌、基础组件、复合组件和布局组件四个核心层次，确保UI的一致性、可维护性和可扩展性。

### 设计令牌系统 (Design Tokens)

设计令牌是设计系统的基础设施，将设计决策转化为可在代码中使用的变量。我们的令牌系统支持多主题、多平台，并采用语义化命名规范。

```typescript
// packages/ui/design-system/src/tokens/index.ts
export const designTokens = {
  colors: {
    primary: {
      50: '#f0f9ff',
      100: '#e0f2fe',
      200: '#bae6fd',
      300: '#7dd3fc',
      400: '#38bdf8',
      500: '#0ea5e9',
      600: '#0284c7',
      700: '#0369a1',
      800: '#075985',
      900: '#0c4a6e'
    },
    gray: {
      50: '#f9fafb',
      100: '#f3f4f6',
      200: '#e5e7eb',
      300: '#d1d5db',
      400: '#9ca3af',
      500: '#6b7280',
      600: '#4b5563',
      700: '#374151',
      800: '#1f2937',
      900: '#111827'
    },
    semantic: {
      success: '#10b981',
      warning: '#f59e0b',
      error: '#ef4444',
      info: '#3b82f6'
    }
  },
  typography: {
    fonts: {
      primary: 'Inter, system-ui, sans-serif',
      mono: 'JetBrains Mono, monospace'
    },
    scales: {
      xs: { fontSize: '0.75rem', lineHeight: 1.5 },
      sm: { fontSize: '0.875rem', lineHeight: 1.571 },
      md: { fontSize: '1rem', lineHeight: 1.6 },
      lg: { fontSize: '1.125rem', lineHeight: 1.667 },
      xl: { fontSize: '1.25rem', lineHeight: 1.7 },
      '2xl': { fontSize: '1.5rem', lineHeight: 1.8 }
    }
  },
  spacing: {
    xs: '0.25rem',
    sm: '0.5rem',
    md: '1rem',
    lg: '1.5rem',
    xl: '2rem',
    '2xl': '3rem',
    '3xl': '4rem'
  },
  breakpoints: {
    sm: '640px',
    md: '768px',
    lg: '1024px',
    xl: '1280px',
    '2xl': '1536px'
  },
  zIndex: {
    base: 0,
    dropdown: 1000,
    sticky: 1020,
    fixed: 1030,
    modalBackdrop: 1040,
    modal: 1050,
    popover: 1060,
    tooltip: 1070
  }
} as const;

// CSS Variables 生成
export function generateCSSVariables(tokens = designTokens): string {
  let css = ':root {\\n';
  
  function generateVars(obj: any, prefix = '') {
    Object.entries(obj).forEach(([key, value]) => {
      // 处理数字键名（如颜色梯度）
      const safeKey = typeof key === 'string' && /^\d+$/.test(key) ? `_${key}` : key;
      const varName = prefix ? `--${prefix}-${safeKey}` : `--${safeKey}`;
      
      if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
        generateVars(value, safeKey);
      } else {
        css += `  ${varName}: ${value};\\n`;
      }
    });
  }
  
  generateVars(tokens);
  css += '}';
  return css;
}

// 主题管理
export const themes = {
  light: designTokens,
  dark: {
    colors: {
      primary: designTokens.colors.primary,
      gray: {
        50: '#111827',
        100: '#1f2937',
        200: '#374151',
        300: '#4b5563',
        400: '#6b7280',
        500: '#9ca3af',
        600: '#d1d5db',
        700: '#e5e7eb',
        800: '#f3f4f6',
        900: '#f9fafb'
      },
      semantic: designTokens.colors.semantic
    },
    // 其他令牌保持不变
    typography: designTokens.typography,
    spacing: designTokens.spacing,
    breakpoints: designTokens.breakpoints,
    zIndex: designTokens.zIndex
  },
  highContrast: {
    colors: {
      primary: {
        50: '#000000',
        100: '#000000',
        200: '#000000',
        300: '#000000',
        400: '#000000',
        500: '#0055ff',
        600: '#0055ff',
        700: '#0055ff',
        800: '#0055ff',
        900: '#0055ff'
      },
      gray: {
        50: '#000000',
        100: '#000000',
        200: '#000000',
        300: '#000000',
        400: '#000000',
        500: '#ffffff',
        600: '#ffffff',
        700: '#ffffff',
        800: '#ffffff',
        900: '#ffffff'
      },
      semantic: {
        success: '#00ff00',
        warning: '#ffff00',
        error: '#ff0000',
        info: '#0055ff'
      }
    },
    // 其他令牌保持不变
    typography: {
      ...designTokens.typography,
      scales: Object.fromEntries(
        Object.entries(designTokens.typography.scales).map(([key, value]) => [
          key,
          { ...value, fontWeight: 'bold' }
        ])
      )
    },
    spacing: designTokens.spacing,
    breakpoints: designTokens.breakpoints,
    zIndex: designTokens.zIndex
  }
};

// 设置主题的函数
export function applyTheme(themeName: 'light' | 'dark' | 'highContrast'): void {
  const theme = themes[themeName];
  if (!theme) return;
  
  // 生成并应用CSS变量
  const cssVars = generateCSSVariables(theme);
  
  // 创建或更新style标签
  let styleElement = document.getElementById('bone-theme-variables') as HTMLStyleElement;
  if (!styleElement) {
    styleElement = document.createElement('style');
    styleElement.id = 'bone-theme-variables';
    document.head.appendChild(styleElement);
  }
  
  styleElement.textContent = cssVars;
  
  // 更新body类名以便于主题特定的CSS选择器
  document.body.className = document.body.className.replace(/theme-\w+/g, '');
  document.body.classList.add(`theme-${themeName}`);
}
```

### 组件系统架构

```typescript
// packages/ui/components/src/components/button/button.tsx
import React from 'react';
import { styled } from '../../styled-system';
import { useButton } from './use-button';
import { Spinner } from '../components/spinner'; // 更精确的导入路径

// 定义沙箱类型
export interface Sandbox {
  mount: () => void;
  unmount: () => void;
  getGlobalState: () => Record<string, any>;
  execute: (code: string) => any;
}

// 版本常量
export const BONE_RUNTIME_VERSION = '1.0.0';

// 定义应用状态类型
export type AppStatus = 'NOT_LOADED' | 'LOADING' | 'NOT_MOUNTED' | 'MOUNTING' | 'MOUNTED' | 'UNMOUNTING' | 'LOAD_ERROR' | 'MOUNT_ERROR' | 'UNMOUNT_ERROR';
type RouteRule = string | RegExp | ((path: string) => boolean);

interface Sandbox {
  eval(code: string): Promise<any>;
  destroy(): void;
  getStatus(): SandboxStatus;
}

interface SandboxStatus {
  appId: string;
  running: boolean;
  activePropertiesCount: number;
  sideEffectsCount: number;
}

interface SandboxOptions {
  securityPolicy?: SandboxSecurityPolicy;
  resourceLimits?: ResourceLimits;
}

interface SandboxSecurityPolicy {
  checkAccess(type: string, prop: string | symbol): 'allow' | 'block' | 'wrap';
  checkWrite(type: string, prop: string | symbol): boolean;
  checkDelete(type: string, prop: string | symbol): boolean;
}

export class DefaultSecurityPolicy implements SandboxSecurityPolicy {
  checkAccess(type: string, prop: string | symbol): 'allow' | 'block' | 'wrap' {
    // 默认允许访问所有属性
    return 'allow';
  }
  
  checkWrite(type: string, prop: string | symbol): boolean {
    // 默认允许写入所有属性
    return true;
  }
  
  checkDelete(type: string, prop: string | symbol): boolean {
    // 默认允许删除所有属性
    return true;
  }
}

interface ResourceLimits {
  maxMemoryMB: number;
  maxExecutionTimeMS: number;
}

interface AppSecurityPolicy {
  check(app: MicroApplication, action: string, resource: any): Promise<{ allowed: boolean; reason?: string }>;
}

const BONE_RUNTIME_VERSION = '1.0.0';

const StyledButton = styled('button', {
  base: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    border: 'none',
    borderRadius: 'token(spacing.md)',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
    fontFamily: 'token(typography.fonts.primary)',
    fontWeight: 500,
    
    '&:focus-visible': {
      outline: '2px solid token(colors.primary.500)',
      outlineOffset: '2px'
    },
    
    '&:disabled': {
      opacity: 0.6,
      cursor: 'not-allowed'
    }
  },
  variants: {
    variant: {
      primary: {
        backgroundColor: 'token(colors.primary.600)',
        color: 'white',
        
        '&:hover:not(:disabled)': {
          backgroundColor: 'token(colors.primary.700)'
        }
      },
      secondary: {
        backgroundColor: 'token(colors.gray.100)',
        color: 'token(colors.gray.900)',
        
        '&:hover:not(:disabled)': {
          backgroundColor: 'token(colors.gray.200)'
        }
      }
    },
    size: {
      sm: {
        padding: 'token(spacing.xs) token(spacing.sm)',
        fontSize: 'token(typography.scales.sm.fontSize)'
      },
      md: {
        padding: 'token(spacing.sm) token(spacing.md)',
        fontSize: 'token(typography.scales.md.fontSize)'
      },
      lg: {
        padding: 'token(spacing.md) token(spacing.lg)',
        fontSize: 'token(typography.scales.lg.fontSize)'
      }
    }
  },
  defaultVariants: {
    variant: 'primary',
    size: 'md'
  }
});

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ children, loading, leftIcon, rightIcon, ...props }, ref) => {
    const { buttonProps, isDisabled } = useButton(props);
    
    return (
      <StyledButton
        ref={ref}
        {...buttonProps}
        disabled={isDisabled || loading}
        data-loading={loading}
      >
        {loading && <Spinner size="sm" />}
        {!loading && leftIcon && <span className="button-icon-left">{leftIcon}</span>}
        <span>{children}</span>
        {rightIcon && <span className="button-icon-right">{rightIcon}</span>}
      </StyledButton>
    );
  }
);
```

## 🚀 开发体验优化

### Vite 配置优化

```typescript
// tools/vite-config/src/micro-fe-preset.ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { visualizer } from 'rollup-plugin-visualizer';

export const microFePreset = (options: {
  appName: string;
  isMicroApp?: boolean;
  devPort?: number;
}) => defineConfig(({ mode }) => {
  const isDev = mode === 'development';
  const isMicroApp = options.isMicroApp ?? false;

  return {
    plugins: [
      react({
        jsxRuntime: 'automatic',
        babel: {
          plugins: [
            ['babel-plugin-styled-components', {
              displayName: isDev,
              fileName: isDev
            }]
          ]
        }
      }),
      !isDev && visualizer({
        filename: `dist/stats-${options.appName}.html`,
        open: false,
        gzipSize: true
      })
    ].filter(Boolean),
    
    server: {
      port: options.devPort,
      cors: true,
      headers: {
        'Access-Control-Allow-Origin': '*'
      }
    },
    
    build: {
      target: 'es2015',
      cssTarget: 'chrome80',
      rollupOptions: {
        external: isMicroApp ? [
          'react',
          'react-dom',
          'react-router-dom',
          '@bone/design-system'
        ] : [],
        output: {
          format: 'esm',
          entryFileNames: `assets/[name].${isDev ? 'js' : '[hash].js'}`,
          chunkFileNames: `assets/[name].${isDev ? 'js' : '[hash].js'}`,
          assetFileNames: `assets/[name].${isDev ? '[ext]' : '[hash].[ext]'}`,
          ...(isMicroApp && {
            globals: {
              react: 'React',
              'react-dom': 'ReactDOM',
              'react-router-dom': 'ReactRouterDOM'
            }
          })
        }
      }
    },
    
    optimizeDeps: {
      include: isMicroApp ? [] : [
        'react',
        'react-dom',
        'react-router-dom'
      ]
    }
  };
});
```

### 开发工具集成

```typescript
// tools/cli/src/commands/dev.ts
export class DevCommand {
  async run(options: DevOptions) {
    const { appName, port, open } = options;
    
    // 1. 检查依赖
    await this.checkDependencies();
    
    // 2. 启动开发服务器
    const server = await this.startDevServer(appName, port);
    
    // 3. 打开浏览器
    if (open) {
      await this.openBrowser(port);
    }
    
    // 4. 监听文件变化
    await this.watchFiles(appName);
    
    return server;
  }
  
  private async startDevServer(appName: string, port: number) {
    const config = await this.loadViteConfig(appName);
    
    return await createServer({
      ...config,
      server: { port }
    });
  }
  
  private async checkDependencies() {
    // 检查项目依赖是否安装
    console.log('Checking dependencies...');
    // 实际实现中应检查node_modules和package.json中的依赖
  }
  
  private async openBrowser(port: number) {
    const open = await import('open');
    await open(`http://localhost:${port}`);
  }
  
  private async watchFiles(appName: string) {
    // 监听文件变化，实现热更新逻辑
    console.log(`Watching files for ${appName}...`);
  }
  
  private async loadViteConfig(appName: string) {
    // 加载应用的Vite配置
    console.log(`Loading Vite config for ${appName}...`);
    return {};
  }
}

// tools/generators/src/micro-app-generator.ts
export class MicroAppGenerator {
  async generate(options: GenerateOptions) {
    const { name, framework, features } = options;
    
    // 1. 创建项目结构
    await this.createProjectStructure(name);
    
    // 2. 生成配置文件
    await this.generateConfigFiles(name, framework);
    
    // 3. 安装依赖
    await this.installDependencies(name, framework, features);
    
    // 4. 生成示例代码
    await this.generateExampleCode(name, framework);
    
    console.log(`✅ 微应用 ${name} 创建成功！`);
    console.log(`📁 目录: apps/${name}`);
    console.log(`🚀 启动: cd apps/${name} && pnpm dev`);
  }
  
  private async createProjectStructure(name: string) {
    // 创建微应用的基本目录结构
    console.log(`Creating project structure for ${name}...`);
  }
  
  private async generateConfigFiles(name: string, framework: string) {
    // 生成配置文件，如tsconfig.json, vite.config.ts等
    console.log(`Generating config files for ${name}...`);
  }
  
  private async installDependencies(name: string, framework: string, features: string[]) {
    // 安装项目依赖
    console.log(`Installing dependencies for ${name}...`);
  }
  
  private async generateExampleCode(name: string, framework: string) {
    // 生成示例代码，如组件、页面等
    console.log(`Generating example code for ${name}...`);
  }
}
```

## 📊 监控与可观测性

### 性能监控

```typescript
// packages/infrastructure/logging/src/performance-monitor.ts
export class PerformanceMonitor {
  private metrics: Map<string, number> = new Map();
  
  startMeasure(name: string) {
    performance.mark(`start-${name}`);
  }
  
  endMeasure(name: string) {
    performance.mark(`end-${name}`);
    performance.measure(name, `start-${name}`, `end-${name}`);
    
    const measure = performance.getEntriesByName(name)[0];
    this.metrics.set(name, measure.duration);
    
    // 上报性能数据
    this.reportMetric(name, measure.duration);
  }
  
  private reportMetric(name: string, duration: number) {
    const data = {
      name,
      duration,
      timestamp: Date.now(),
      userAgent: navigator.userAgent,
      url: window.location.href
    };
    
    // 使用 sendBeacon 上报，避免影响页面性能
    navigator.sendBeacon('/api/metrics', JSON.stringify(data));
  }
}

// 应用加载性能监控
export class AppLoadMonitor {
  private monitor = new PerformanceMonitor();
  
  async trackAppLoad(appId: string, loadPromise: Promise<any>) {
    this.monitor.startMeasure(`app-load-${appId}`);
    
    try {
      await loadPromise;
      this.monitor.endMeasure(`app-load-${appId}`);
    } catch (error) {
      this.monitor.endMeasure(`app-load-${appId}`);
      this.reportError(appId, error);
    }
  }
}
```

## 📡 消息通信工具

### 类型安全的事件总线

Bone架构实现了一个类型安全的事件总线系统，用于微应用间的高效通信。该系统基于TypeScript泛型，确保事件名称和数据类型的严格匹配，提供编译时类型检查，避免运行时错误。

```typescript
// packages/core/event-bus/src/index.ts

// 事件处理器类型
interface EventHandler<T> {
  (data: T): void;
}

// 事件映射类型
interface EventMap {
  [event: string]: any;
}

// 取消订阅函数类型
type UnsubscribeFunction = () => void;

// 类型安全的事件总线类
export class TypedEventBus<Events extends EventMap> {
  private eventHandlers: Map<keyof Events, Set<EventHandler<any>>> = new Map();

  // 注册事件监听
  on<K extends keyof Events>(event: K, handler: EventHandler<Events[K]>): UnsubscribeFunction {
    if (!this.eventHandlers.has(event)) {
      this.eventHandlers.set(event, new Set());
    }

    const handlers = this.eventHandlers.get(event)!;
    handlers.add(handler);

    // 返回取消订阅函数
    return () => {
      handlers.delete(handler);
      if (handlers.size === 0) {
        this.eventHandlers.delete(event);
      }
    };
  }

  // 触发事件
  emit<K extends keyof Events>(event: K, data: Events[K]): void {
    const handlers = this.eventHandlers.get(event);
    if (!handlers) {
      return;
    }

    // 复制处理函数集合，防止在触发过程中修改导致的问题
    const handlersCopy = new Set(handlers);
    handlersCopy.forEach(handler => {
      try {
        handler(data);
      } catch (error) {
        console.error(`Error in event handler for ${String(event)}:`, error);
      }
    });
  }

  // 只监听一次事件
  once<K extends keyof Events>(event: K, handler: EventHandler<Events[K]>): UnsubscribeFunction {
    const wrapper = (data: Events[K]) => {
      unsubscribe();
      handler(data);
    };

    const unsubscribe = this.on(event, wrapper);
    return unsubscribe;
  }

  // 移除特定事件的所有监听
  off<K extends keyof Events>(event: K): void {
    this.eventHandlers.delete(event);
  }

  // 移除所有事件监听
  clear(): void {
    this.eventHandlers.clear();
  }

  // 获取特定事件的监听器数量
  listenerCount<K extends keyof Events>(event: K): number {
    const handlers = this.eventHandlers.get(event);
    return handlers ? handlers.size : 0;
  }
}

// 应用事件接口
export interface AppEvents {
  'app:mounted': { appId: string };
  'app:unmounted': { appId: string };
  'app:activated': { appId: string };
  'app:deactivated': { appId: string };
  'app:error': { appId: string; error: Error; phase: string };
  'micro:app:message': MicroAppMessage;
  'micro:app:response': { requestId: string; response: MicroAppMessage };
  'user:login': { userId: string; token: string };
  'user:logout': void;
  'theme:changed': { theme: 'light' | 'dark' | 'highContrast' };
  // 消息类型事件
  [event: `message:${string}`]: MicroAppMessage;
}

// 全局事件总线单例
const globalEventBus = new TypedEventBus<AppEvents>();

export function getEventBus(): TypedEventBus<AppEvents> {
  return globalEventBus;
}

// 向后兼容函数，保持旧代码可用
export function getGlobalEventBus(): TypedEventBus<AppEvents> {
  console.warn('getGlobalEventBus() is deprecated. Use getEventBus() instead.');
  return getEventBus();
}
```

### 微应用消息通信工具

为了支持更复杂的微应用间通信场景，Bone架构实现了一个功能强大的消息通信工具，提供请求-响应模式、消息队列和重试机制等高级特性。

```typescript
// packages/core/event-bus/src/micro-app-messenger.ts

// 微应用消息接口
export interface MicroAppMessage {
  /** 消息类型 */
  type: string;
  /** 消息内容 */
  payload?: any;
  /** 发送者名称 */
  from: string;
  /** 接收者名称（可选） */
  to?: string;
  /** 消息时间戳 */
  timestamp: number;
  /** 消息唯一标识符 */
  messageId?: string;
  /** 是否需要响应 */
  responseExpected?: boolean;
  /** 错误信息 */
  error?: string;
}

// 响应处理器接口
interface ResponseHandler {
  resolve: (response: MicroAppMessage) => void;
  reject: (error: Error) => void;
  timeoutId: ReturnType<typeof setTimeout>;
}

// 消息队列项接口
interface MessageQueueItem {
  message: MicroAppMessage;
  retries: number;
  maxRetries: number;
  retryDelay: number;
  lastAttempt: number;
}

export class MicroAppMessenger {
  private eventBus: TypedEventBus<any>;
  private appName: string;
  private responseHandlers: Map<string, ResponseHandler> = new Map();
  private messageQueue: MessageQueueItem[] = [];
  private queueProcessing = false;
  private maxRetries = 3;
  private defaultTimeout = 5000;

  constructor(appName: string, eventBus?: TypedEventBus<any>) {
    this.appName = appName;
    this.eventBus = eventBus || getEventBus();
    this.initialize();
  }

  private initialize(): void {
    // 订阅消息事件
    this.eventBus.on('micro:app:message', this.handleIncomingMessage.bind(this));
    this.eventBus.on('micro:app:response', this.handleResponse.bind(this));

    // 订阅应用挂载事件，当目标应用激活时尝试发送队列中的消息
    this.eventBus.on('app:mounted', ({ appId }) => {
      this.processMessageQueue();
    });
  }

  // 发送消息到指定应用
  send(targetApp: string, type: string, payload?: any): void {
    const message: MicroAppMessage = {
      type,
      payload,
      from: this.appName,
      to: targetApp,
      timestamp: Date.now(),
      messageId: this.generateMessageId()
    };

    // 检查目标应用是否活跃
    if (this.isAppActive(targetApp)) {
      this.eventBus.emit('micro:app:message', message);
    } else {
      // 否则将消息加入队列
      this.enqueueMessage(message);
    }
  }

  // 发送消息并等待响应
  async sendWithResponse(targetApp: string, type: string, payload?: any, timeout: number = this.defaultTimeout): Promise<MicroAppMessage> {
    return new Promise((resolve, reject) => {
      const messageId = this.generateMessageId();

      // 设置超时
      const timeoutId = setTimeout(() => {
        this.responseHandlers.delete(messageId);
        reject(new Error(`等待 ${targetApp} 响应超时`));
      }, timeout);

      // 保存响应处理器
      this.responseHandlers.set(messageId, {
        resolve,
        reject,
        timeoutId
      });

      // 发送请求消息
      this.send(targetApp, type, {
        ...payload,
        messageId,
        responseExpected: true
      });
    });
  }

  // 回复消息
  reply(message: MicroAppMessage, payload?: any): void {
    if (!message.responseExpected) {
      console.warn('Cannot reply to a message that did not request a response');
      return;
    }

    const response: MicroAppMessage = {
      type: `${message.type}:response`,
      payload,
      from: this.appName,
      to: message.from,
      timestamp: Date.now(),
      messageId: this.generateMessageId()
    };

    this.eventBus.emit('micro:app:response', {
      requestId: message.messageId,
      response
    });
  }

  // 处理接收到的消息
  private handleIncomingMessage(message: MicroAppMessage): void {
    // 只处理发送给当前应用的消息
    if (message.to !== this.appName && message.to !== '*') {
      return;
    }

    // 触发特定类型的消息事件
    this.eventBus.emit(`message:${message.type}` as any, message);
  }
  
  // 订阅特定类型的消息
  on(type: string, handler: (message: MicroAppMessage) => void): () => void {
    return this.eventBus.on(`message:${type}` as any, handler);
  }

  // 处理接收到的响应
  private handleResponse(data: { requestId: string; response: MicroAppMessage }): void {
    const handler = this.responseHandlers.get(data.requestId);
    if (handler) {
      // 清除超时
      clearTimeout(handler.timeoutId);
      
      // 调用处理器
      handler.resolve(data.response);
      
      // 移除处理器
      this.responseHandlers.delete(data.requestId);
    }
  }

  // 检查应用是否活跃
  private isAppActive(appName: string): boolean {
    // 这里应该调用应用注册表检查应用状态
    // 简化实现，实际需要与应用注册表集成
    return true;
  }

  // 生成唯一消息ID
  private generateMessageId(): string {
    return `${this.appName}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  // 将消息加入队列
  private enqueueMessage(message: MicroAppMessage): void {
    this.messageQueue.push({
      message,
      retries: 0,
      maxRetries: this.maxRetries,
      retryDelay: 1000,
      lastAttempt: Date.now()
    });

    // 开始处理队列
    if (!this.queueProcessing) {
      this.processMessageQueue();
    }
  }

  // 处理消息队列
  private async processMessageQueue(): Promise<void> {
    this.queueProcessing = true;

    while (this.messageQueue.length > 0) {
      const queueItem = this.messageQueue[0];
      const now = Date.now();

      // 检查是否可以重试
      if (now - queueItem.lastAttempt >= queueItem.retryDelay) {
        if (this.isAppActive(queueItem.message.target)) {
          // 应用已活跃，发送消息
          try {
            this.eventBus.emit('micro:app:message', queueItem.message);
            // 消息发送成功，从队列中移除
            this.messageQueue.shift();
          } catch (error) {
            console.error('Error sending queued message:', error);
            // 增加重试计数
            queueItem.retries++;
            queueItem.lastAttempt = now;
            queueItem.retryDelay *= 2; // 指数退避

            // 达到最大重试次数，放弃并从队列中移除
            if (queueItem.retries >= queueItem.maxRetries) {
              console.warn(`Message to ${queueItem.message.target} failed after ${queueItem.maxRetries} retries`);
              this.messageQueue.shift();
            }
          }
        } else {
          // 应用仍未活跃，等待一段时间后重试
          await new Promise(resolve => setTimeout(resolve, 500));
        }
      } else {
        // 还未到重试时间，等待剩余时间
        await new Promise(resolve => setTimeout(resolve, queueItem.retryDelay - (now - queueItem.lastAttempt)));
      }
    }

    this.queueProcessing = false;
  }

  // 清理资源
  destroy(): void {
    this.eventBus.off('micro:app:message');
    this.eventBus.off('micro:app:response');
    
    // 清除所有超时
    this.responseHandlers.forEach(handler => {
      clearTimeout(handler.timeoutId);
      handler.reject(new Error('Messenger destroyed'));
    });
    this.responseHandlers.clear();
    
    // 清空消息队列
    this.messageQueue = [];
  }
  
  // 向后兼容函数 - 保持旧API可用
  dispose(): void {
    console.warn('dispose() is deprecated. Use destroy() instead.');
    this.destroy();
  }
  
  // 获取应用活跃状态 - 与应用注册表集成
  private isAppActive(appName: string): boolean {
    try {
      // 动态导入应用注册表以避免循环依赖
      const { getApplicationRegistry } = require('@bone/core/micro-fe-runtime');
      const registry = getApplicationRegistry();
      return registry.isAppActive(appName);
    } catch (error) {
      console.warn('Failed to check app status, assuming active:', error);
      return true;
    }
  }
  
  // 向后兼容函数
  getIsReady(): boolean {
    console.warn('getIsReady() is deprecated. App status is now checked dynamically.');
    return true;
  }
}
```

### 使用示例

以下是事件总线和消息通信工具的使用示例：

```typescript
// 工厂函数 - 简化消息通信工具的创建
export function createMicroAppMessenger(appName: string): MicroAppMessenger {
  return new MicroAppMessenger(appName, getEventBus());
}

// 在主应用中使用
import { getEventBus, MicroAppMessenger, createMicroAppMessenger } from '@bone/core/event-bus';

// 获取全局事件总线
const eventBus = getEventBus();

// 监听应用生命周期事件
eventBus.on('app:mounted', ({ appId }) => {
  console.log(`应用 ${appId} 已挂载`);
});

eventBus.on('app:error', ({ appId, error, phase }) => {
  console.error(`应用 ${appId} 在 ${phase} 阶段发生错误:`, error);
  // 上报错误
});

// 创建消息通信工具 - 推荐方式
const messenger = createMicroAppMessenger('platform-shell');

// 发送消息到微应用
messenger.send('admin-portal', 'theme:change', { theme: 'dark' });

// 发送消息并等待响应
async function getUserInfo() {
  try {
    const response = await messenger.sendWithResponse('identity-access', 'user:getInfo', {}, 3000);
    return response.payload;
  } catch (error) {
    console.error('获取用户信息失败:', error);
    throw error;
  }
}

// 在微应用中使用
import { createMicroAppMessenger } from '@bone/core/event-bus';

// 创建消息通信工具
const messenger = createMicroAppMessenger('admin-portal');

// 监听特定类型的消息
messenger.on('theme:change', (message) => {
  const { theme } = message.payload;
  console.log(`切换主题为: ${theme}`);
  // 应用主题变更
  
  // 回复确认
  messenger.reply(message, { success: true });
});

// 发送消息到其他微应用或主应用
messenger.send('*', 'data:updated', { entity: 'user', id: '123' });

// 组件卸载时清理
function cleanup() {
  messenger.destroy();
}
```

### 事件总线与微前端运行时集成

事件总线已与微前端运行时深度集成，自动在应用生命周期的关键时刻触发相应事件：

```typescript
// packages/core/micro-fe-runtime/src/application-registry.ts
import { getEventBus } from '@bone/core/event-bus';
import { MicroApplication, MicroAppConfig } from './types';

class ApplicationRegistry {
  private apps: Map<string, MicroApplication> = new Map();
  private eventBus = getEventBus();
  
  // ...其他方法
  
  async activateApp(appId: string): Promise<void> {
    try {
      const app = await this.getApp(appId);
      await app.mount(this.getContainer(appId));
      
      // 触发应用激活事件
      this.eventBus.emit('app:activated', { appId });
    } catch (error) {
      // 触发应用错误事件
      this.eventBus.emit('app:error', { 
        appId, 
        error: error as Error, 
        phase: 'activate' 
      });
      throw error;
    }
  }
  
  async deactivateApp(appId: string): Promise<void> {
    try {
      const app = this.apps.get(appId);
      if (app) {
        await app.unmount();
        // 触发应用停用事件
        this.eventBus.emit('app:deactivated', { appId });
      }
    } catch (error) {
      // 触发应用错误事件
      this.eventBus.emit('app:error', { 
        appId, 
        error: error as Error, 
        phase: 'deactivate' 
      });
      throw error;
    }
  }
}
```

## 🔒 安全增强

### 安全沙箱增强

```typescript
// packages/core/micro-fe-runtime/src/security/policy-enforcer.ts
export class SecurityPolicyEnforcer {
  private policies: AppSecurityPolicy[] = [];
  
  addPolicy(policy: AppSecurityPolicy) {
    this.policies.push(policy);
  }
  
  async enforce(app: MicroApplication, action: string, resource: any): Promise<boolean> {
    for (const policy of this.policies) {
      const result = await policy.check(app, action, resource);
      if (!result.allowed) {
        console.warn(`Security policy violation: ${result.reason}`);
        return false;
      }
    }
    return true;
  }
}

export class CSPPolicy implements AppSecurityPolicy {
  async check(app: MicroApplication, action: string, resource: any) {
    if (action === 'script-eval') {
      return {
        allowed: app.metadata?.permissions?.includes('unsafe-eval') ?? false,
        reason: 'CSP: eval() is not allowed'
      };
    }
    
    return { allowed: true };
  }
}
```

## 🚀 部署与运维

### Docker 多阶段构建

```dockerfile
# 构建阶段
FROM node:18-alpine AS builder
WORKDIR /app

# 安装依赖
COPY package.json pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile

# 复制源码并构建
COPY . .
RUN pnpm build

# 生产阶段
FROM nginx:alpine AS production
COPY --from=builder /app/apps/platform-shell/dist /usr/share/nginx/html
COPY --from=builder /app/apps/admin-portal/dist /usr/share/nginx/html/admin
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### GitHub Actions 工作流

```yaml
name: Production Deployment

on:
  push:
    tags: ['v*']

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: production
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Setup Node.js
      uses: actions/setup-node@v4
      with:
        node-version: '18'
        cache: 'pnpm'
        
    - name: Install dependencies
      run: pnpm install --frozen-lockfile
      
    - name: Run tests
      run: pnpm test
      
    - name: Build applications
      run: pnpm build
      
    - name: Run security audit
      run: pnpm audit
      
    - name: Deploy to production
      run: |
        echo "Deploying version ${GITHUB_REF#refs/tags/}"
        ./scripts/deploy.sh production
```

## 💡 架构优势总结

### 企业级特性

1. **完整的开发工具链**: 从代码生成到构建部署的全套工具
2. **强大的类型系统**: 端到端的TypeScript支持
3. **完善的监控体系**: 性能、错误、用户行为全面监控
4. **企业级安全**: 多层次安全防护和策略执行
5. **卓越的开发体验**: 热更新、代码提示、调试支持

### 技术先进性

- **现代构建工具**: Vite + SWC 极速构建
- **微前端最佳实践**: 基于行业标准的实现方案
- **设计系统**: Token驱动的现代化设计系统
- **Monorepo优化**: 高效的依赖管理和构建缓存
- **云原生友好**: Docker + Kubernetes 部署支持

### 业务价值

- **快速迭代**: 微应用独立开发部署，加速产品迭代
- **技术升级**: 渐进式技术栈升级，降低风险
- **团队协作**: 清晰的架构边界，提升团队协作效率
- **成本优化**: 资源共享，减少重复开发成本

该架构方案基于业界最佳实践，为企业级前端应用提供了完整的解决方案，平衡了技术先进性与工程实用性，支持大规模团队的协同开发和长期维护。