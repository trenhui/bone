# React前端模块设计方案

## 1. 项目概述

本文档基于Bone现有代码与功能分析，设计一套符合业界最佳实践的React前端模块方案。该方案旨在为Bone平台提供一套统一、高效、可扩展、安全且用户友好的React前端开发框架，支持快速构建企业级应用，并与现有架构无缝集成。

## 1.1 设计理念

- **以用户为中心**：优化用户体验，提供流畅、直观的交互界面
- **可扩展性优先**：模块化设计，支持功能快速迭代和灵活扩展
- **工程化驱动**：标准化开发流程，自动化构建测试，确保代码质量
- **安全可靠**：全面的安全防护措施，保障应用和数据安全
- **性能优先**：优化性能，提供极速响应的用户体验
- **可访问性**：确保所有用户都能便捷使用系统功能

## 2. 技术架构分析

### 2.1 现有架构评估

通过对Bone项目代码库的分析，发现：

- 项目目前采用混合前端技术栈，包括Vue 3和React
- 核心业务模块已有基于元数据的动态表单引擎（React实现）
- 项目采用了微前端架构，主应用使用React + Antd
- 已有的动态UI组件能够基于元数据自动生成表单和列表
- 微前端框架使用wujie-react实现

### 2.2 技术栈现状

- **主应用**：React 18 + TypeScript + Antd + Vite
- **状态管理**：Zustand + React Query
- **路由**：React Router 6 + 微前端路由
- **微前端**：wujie-react
- **UI组件库**：Ant Design 5.x
- **构建工具**：Vite 5.0

## 3. 新前端模块方案

### 3.1 设计原则

1. **一致性**：与现有架构保持一致，遵循已有的设计模式和规范
2. **可扩展性**：模块化设计，支持功能扩展和自定义
3. **高性能**：优化组件渲染，减少不必要的重渲染
4. **开发体验优先**：提供完整的开发工具链和最佳实践
5. **生产就绪**：完善的错误处理、日志记录和性能监控

### 3.2 技术选型

### 3.3 架构设计

#### 3.3.1 分层架构与六边形架构

#### 3.3.2 微信应用前端主框架设计

##### 3.3.2.1 主框架架构

##### 3.3.2.2 前端微应用模块

##### 3.3.2.3 通用组件库

##### 3.3.2.4 模块协作机制

### 3.4 目录结构

### 3.5 核心功能模块

### 3.6 微前端集成

### 3.7 安全与权限

### 3.8 性能优化

### 3.9 测试策略

### 3.10 部署方案

## 4. 实现细节

## 5. 开发规范

## 6. 兼容性与可访问性

## 7. 结论和建议

| 类别 | 技术/库 | 版本 | 选型理由 |
|------|---------|------|----------|
| 核心框架 | React | ^18.2.0 | 业界成熟方案，与现有主应用一致，支持并发渲染和自动批处理 |
| 类型系统 | TypeScript | ^5.2.0 | 提供类型安全，提升开发效率和代码质量，支持严格模式 |
| UI组件库 | Ant Design | ^5.12.0 | 丰富的组件生态，与现有架构一致，支持定制主题 |
| 构建工具 | Vite | ^5.0.0 | 极速的开发体验，优化的构建输出，支持现代浏览器特性 |
| 路由管理 | React Router | ^6.20.0 | 官方推荐的路由解决方案，支持数据加载和嵌套路由 |
| 状态管理 | Zustand + React Query | ^4.4.0 + ^5.8.0 | 轻量级状态管理 + 服务端状态同步，减少样板代码 |
| HTTP客户端 | Axios | ^1.5.0 | 成熟的HTTP请求库，支持拦截器、取消请求等高级功能 |
| 工具库 | Lodash-es, Dayjs | ^4.17.21, ^1.11.9 | 提供常用工具函数和日期处理，支持模块化导入 |
| 样式处理 | SCSS, CSS Modules | - | 支持嵌套、变量等高级特性，防止样式冲突 |
| 原子化CSS | Tailwind CSS | ^3.3.0 | 提供高性能的原子类，加速开发，支持JIT编译 |
| 代码质量 | ESLint, Prettier | ^8.52.0, ^3.0.0 | 确保代码质量和一致性，支持自动格式化 |
| 测试框架 | Vitest, React Testing Library | ^1.0.0, ^13.4.0 | 现代化的测试解决方案，支持组件测试和Hook测试 |
| 端到端测试 | Cypress | ^13.0.0 | 现代化的E2E测试框架，提供直观的测试体验 |
| 国际化 | i18next, react-i18next | ^23.0.0, ^13.0.0 | 成熟的国际化解决方案，支持多语言切换 |
| 状态持久化 | zustand-persist | ^0.4.3 | Zustand状态持久化，支持localStorage/sessionStorage |
| 表单管理 | React Hook Form | ^7.45.0 | 高性能的表单处理库，支持表单验证和自动保存 |
| 拖拽功能 | react-dnd | ^16.0.1 | 灵活的拖拽功能实现，支持复杂交互场景 |
| 动画库 | Framer Motion | ^10.12.0 | 高性能动画库，支持声明式动画和手势 |
| 错误监控 | Sentry | ^7.50.0 | 实时错误监控和性能监控，支持源码映射 |
| 可访问性 | @axe-core/react | ^4.8.0 | 可访问性检查工具，确保应用符合WCAG标准 |
| 图标库 | @ant-design/icons | ^5.0.0 | Ant Design官方图标库，与组件库风格一致 |

### 3.3 架构设计

#### 3.3.1 分层架构与六边形架构

#### 3.3.2 微信应用前端主框架设计

基于Bone平台的微信应用前端框架采用**混合式微前端架构**，结合微信小程序和H5微应用的优势，提供统一的开发体验和用户体验。

##### 3.3.2.1 主框架架构

**技术选型**：
- **基础框架**：React Native for Web + Taro 3.x
- **状态管理**：Zustand + React Query
- **UI组件库**：WeUI + 自定义组件库
- **微前端框架**：基于Taro的小程序微前端 + 无界(wujie)H5微前端
- **构建工具**：Webpack + Taro CLI
- **开发模式**：一体化开发，多端构建

**核心设计**：
```
微信应用主框架
├── 主容器层（Taro应用）
│   ├── 全局状态管理
│   ├── 路由管理
│   ├── 权限管理
│   └── 微前端加载器
├── 微应用集成层
│   ├── 小程序微应用容器
│   ├── H5微应用容器
│   ├── 应用注册中心
│   └── 通信总线
└── 基础服务层
    ├── API服务
    ├── 缓存服务
    ├── 认证服务
    └── 配置服务
```

##### 3.3.2.2 前端微应用模块

基于业务领域划分，微信应用包含以下核心微应用模块：

1. **用户中心微应用**
   - 负责用户信息管理、权限控制、登录认证
   - 技术栈：React + TypeScript
   - 主要页面：登录页、个人中心、权限设置

2. **工作台微应用**
   - 提供核心业务入口、待办事项、通知提醒
   - 技术栈：React + TypeScript
   - 主要页面：仪表盘、待办列表、通知中心

3. **业务实体微应用**
   - 基于元数据驱动的实体管理模块
   - 技术栈：React + TypeScript
   - 主要功能：动态表单、动态列表、实体操作

4. **流程管理微应用**
   - 处理工作流、审批流程、流程设计
   - 技术栈：React + TypeScript
   - 主要功能：流程发起、审批、查看、设计

5. **报表分析微应用**
   - 提供数据可视化、报表查询、数据分析
   - 技术栈：React + ECharts
   - 主要功能：图表展示、数据筛选、报表导出

6. **消息中心微应用**
   - 处理系统消息、推送通知、消息设置
   - 技术栈：React + WebSocket
   - 主要功能：消息列表、消息详情、消息设置

7. **系统设置微应用**
   - 提供应用配置、个性化设置、关于信息
   - 技术栈：React + TypeScript
   - 主要功能：通用设置、个性化配置、版本信息

##### 3.3.2.3 通用组件库

**核心组件分类**：

1. **基础组件**
   - 按钮、输入框、选择器、开关等基础UI元素
   - 适配微信小程序和H5环境
   - 支持主题定制和国际化

2. **布局组件**
   - 页面容器、导航栏、标签栏、抽屉等布局元素
   - 响应式设计，适配不同屏幕尺寸
   - 支持自定义样式和行为

3. **业务组件**
   - 表单组件：动态表单、表单验证、表单布局
   - 列表组件：动态表格、数据网格、虚拟列表
   - 卡片组件：信息卡片、统计卡片、操作卡片
   - 图表组件：柱状图、折线图、饼图、仪表盘

4. **交互组件**
   - 弹窗、对话框、确认框、提示框
   - 加载状态、骨架屏、空状态
   - 下拉刷新、上拉加载、无限滚动

5. **工具组件**
   - 二维码生成器、图片预览器
   - 文件上传器、音频播放器
   - 位置选择器、地图组件

**组件设计原则**：
- 原子化设计：基础组件 → 组合组件 → 业务组件
- 跨平台兼容：同时支持小程序和H5环境
- 可定制性：支持主题配置和样式覆盖
- 性能优化：组件懒加载、按需渲染、缓存机制

##### 3.3.2.4 模块协作机制

**1. 应用间通信**

- **全局事件总线**：
  ```typescript
  // 全局事件总线实现
  class GlobalEventBus {
    private events: Map<string, Set<Function>> = new Map();
    
    on(event: string, handler: Function): void {
      if (!this.events.has(event)) {
        this.events.set(event, new Set());
      }
      this.events.get(event)!.add(handler);
    }
    
    emit(event: string, ...args: any[]): void {
      const handlers = this.events.get(event);
      if (handlers) {
        handlers.forEach(handler => handler(...args));
      }
    }
    
    off(event: string, handler: Function): void {
      const handlers = this.events.get(event);
      if (handlers) {
        handlers.delete(handler);
      }
    }
  }
  
  export const globalEventBus = new GlobalEventBus();
  ```

- **共享状态管理**：
  ```typescript
  // 共享状态示例
  import { create } from 'zustand';
  
  interface SharedState {
    userInfo: UserInfo | null;
    permissions: string[];
    appConfig: AppConfig;
    updateUserInfo: (userInfo: UserInfo) => void;
    updatePermissions: (permissions: string[]) => void;
    updateAppConfig: (config: Partial<AppConfig>) => void;
  }
  
  export const useSharedStore = create<SharedState>((set) => ({
    userInfo: null,
    permissions: [],
    appConfig: {
      theme: 'light',
      language: 'zh-CN',
      notificationsEnabled: true
    },
    updateUserInfo: (userInfo) => set({ userInfo }),
    updatePermissions: (permissions) => set({ permissions }),
    updateAppConfig: (config) => set((state) => ({
      appConfig: { ...state.appConfig, ...config }
    }))
  }));
  ```

- **微前端通信协议**：
  ```typescript
  // 微前端通信接口定义
  interface MicroAppMessage {
    type: 'event' | 'data' | 'action';
    payload: any;
    timestamp: number;
    sender: string;
    receiver?: string;
  }
  
  // 通信桥接器
  class MicroAppBridge {
    // 发送消息到指定微应用
    sendMessage(targetApp: string, message: MicroAppMessage): void {
      // 实现消息发送逻辑
    }
    
    // 广播消息到所有微应用
    broadcastMessage(message: MicroAppMessage): void {
      // 实现广播逻辑
    }
    
    // 注册消息处理器
    registerHandler(messageType: string, handler: (message: MicroAppMessage) => void): void {
      // 实现消息处理逻辑
    }
  }
  ```

**2. 路由集成**

- **统一路由注册中心**：
  ```typescript
  // 路由注册中心
  interface RouteConfig {
    path: string;
    component: string; // 微应用名称 + 组件路径
    microApp: string;
    exact?: boolean;
    auth?: boolean;
    permissions?: string[];
  }
  
  class RouterRegistry {
    private routes: RouteConfig[] = [];
    
    registerRoute(config: RouteConfig): void {
      this.routes.push(config);
    }
    
    getRoute(path: string): RouteConfig | undefined {
      // 实现路由匹配逻辑
    }
    
    getAllRoutes(): RouteConfig[] {
      return this.routes;
    }
  }
  
  export const routerRegistry = new RouterRegistry();
  ```

- **动态路由加载**：支持在微应用加载时动态注册路由
- **路由守卫**：统一的权限验证和拦截机制

**3. 生命周期管理**

- **微应用生命周期钩子**：
  ```typescript
  interface MicroAppLifecycle {
    // 微应用加载前
    beforeLoad?: (appInfo: AppInfo) => void;
    // 微应用加载后
    afterLoad?: (appInfo: AppInfo) => void;
    // 微应用挂载前
    beforeMount?: (appInfo: AppInfo) => void;
    // 微应用挂载后
    afterMount?: (appInfo: AppInfo) => void;
    // 微应用卸载前
    beforeUnmount?: (appInfo: AppInfo) => void;
    // 微应用卸载后
    afterUnmount?: (appInfo: AppInfo) => void;
  }
  ```

- **应用预加载策略**：根据用户行为和使用频率预加载常用微应用
- **资源释放机制**：自动卸载不再使用的微应用资源

**4. 权限控制集成**

- **统一权限服务**：
  ```typescript
  class PermissionService {
    // 检查权限
    hasPermission(permission: string): boolean {
      const userPermissions = this.getUserPermissions();
      return userPermissions.includes(permission);
    }
    
    // 检查多个权限（满足任一即可）
    hasPermissions(permissions: string[]): boolean {
      const userPermissions = this.getUserPermissions();
      return permissions.some(perm => userPermissions.includes(perm));
    }
    
    // 检查多个权限（全部满足）
    hasAllPermissions(permissions: string[]): boolean {
      const userPermissions = this.getUserPermissions();
      return permissions.every(perm => userPermissions.includes(perm));
    }
    
    // 获取用户权限列表
    getUserPermissions(): string[] {
      const { permissions } = useSharedStore.getState();
      return permissions;
    }
    
    // 权限检查高阶组件
    static withPermission(requiredPermission: string) {
      return (Component: React.ComponentType<any>) => {
        return (props: any) => {
          const permissionService = new PermissionService();
          if (!permissionService.hasPermission(requiredPermission)) {
            return <PermissionDeniedView />;
          }
          return <Component {...props} />;
        };
      };
    }
  }
  ```

- **组件级权限控制**：通过高阶组件实现组件级权限控制
  ```typescript
  // 使用示例
  const AdminPanel = () => {
    return <div>管理员面板</div>;
  };
  
  export default PermissionService.withPermission('admin:access')(AdminPanel);
  ```

- **路由级权限控制**：在路由配置中定义权限要求
  ```typescript
  // 路由配置示例
  routerRegistry.registerRoute({
    path: '/admin',
    component: 'admin-module/AdminDashboard',
    microApp: 'admin',
    auth: true,
    permissions: ['admin:access']
  });
  ```

**5. 数据共享机制**

- **统一数据存储**：
  ```typescript
  class DataStore {
    private storage: Map<string, any> = new Map();
    private listeners: Map<string, Set<(value: any) => void>> = new Map();
    
    // 设置数据
    set(key: string, value: any): void {
      this.storage.set(key, value);
      this.notifyListeners(key, value);
    }
    
    // 获取数据
    get(key: string): any {
      return this.storage.get(key);
    }
    
    // 监听数据变化
    subscribe(key: string, listener: (value: any) => void): () => void {
      if (!this.listeners.has(key)) {
        this.listeners.set(key, new Set());
      }
      this.listeners.get(key)!.add(listener);
      
      // 返回取消订阅函数
      return () => {
        this.listeners.get(key)?.delete(listener);
      };
    }
    
    // 通知监听器
    private notifyListeners(key: string, value: any): void {
      const keyListeners = this.listeners.get(key);
      if (keyListeners) {
        keyListeners.forEach(listener => listener(value));
      }
      
      // 同时通知全局监听器
      const globalListeners = this.listeners.get('*');
      if (globalListeners) {
        globalListeners.forEach(listener => listener({ key, value }));
      }
    }
  }
  
  export const dataStore = new DataStore();
  ```

- **API请求封装**：
  ```typescript
  class ApiService {
    private axiosInstance: AxiosInstance;
    
    constructor() {
      this.axiosInstance = axios.create({
        baseURL: '/api',
        timeout: 10000,
        headers: {
          'Content-Type': 'application/json'
        }
      });
      
      // 请求拦截器
      this.axiosInstance.interceptors.request.use(
        config => {
          const token = getAuthToken();
          if (token) {
            config.headers.Authorization = `Bearer ${token}`;
          }
          return config;
        },
        error => {
          return Promise.reject(error);
        }
      );
      
      // 响应拦截器
      this.axiosInstance.interceptors.response.use(
        response => response.data,
        error => {
          ErrorHandler.handleApiError(error);
          return Promise.reject(error);
        }
      );
    }
    
    // GET请求
    async get<T>(url: string, params?: any): Promise<T> {
      return this.axiosInstance.get<T>(url, { params });
    }
    
    // POST请求
    async post<T>(url: string, data?: any): Promise<T> {
      return this.axiosInstance.post<T>(url, data);
    }
    
    // 其他HTTP方法...
  }
  
  export const apiService = new ApiService();
  ```

**6. 微前端应用注册与管理**

```typescript
interface MicroAppConfig {
  name: string;
  entry: string;
  container: string;
  activeRule: string;
  props?: any;
  permissions?: string[];
  preload?: boolean;
}

class MicroAppManager {
  private apps: Map<string, MicroAppConfig> = new Map();
  private activeApps: Set<string> = new Set();
  
  // 注册微应用
  registerApp(config: MicroAppConfig): void {
    this.apps.set(config.name, config);
    
    // 如果配置了预加载
    if (config.preload) {
      this.preloadApp(config.name);
    }
  }
  
  // 预加载微应用
  preloadApp(appName: string): void {
    const appConfig = this.apps.get(appName);
    if (appConfig) {
      // 预加载逻辑实现
      console.log(`Preloading app: ${appName}`);
    }
  }
  
  // 启动微应用
  async startApp(appName: string): Promise<void> {
    if (this.activeApps.has(appName)) {
      return;
    }
    
    const appConfig = this.apps.get(appName);
    if (!appConfig) {
      throw new Error(`App ${appName} not registered`);
    }
    
    // 检查权限
    const permissionService = new PermissionService();
    if (appConfig.permissions && !permissionService.hasPermissions(appConfig.permissions)) {
      throw new Error('Permission denied');
    }
    
    // 启动微应用逻辑
    console.log(`Starting app: ${appName}`);
    this.activeApps.add(appName);
  }
  
  // 停止微应用
  stopApp(appName: string): void {
    if (this.activeApps.has(appName)) {
      // 停止微应用逻辑
      console.log(`Stopping app: ${appName}`);
      this.activeApps.delete(appName);
    }
  }
  
  // 获取所有微应用
  getAllApps(): MicroAppConfig[] {
    return Array.from(this.apps.values());
  }
}

export const microAppManager = new MicroAppManager();
```

**7. 应用初始化流程**

```typescript
class AppInitializer {
  async initialize(): Promise<void> {
    try {
      // 1. 加载全局配置
      await this.loadGlobalConfig();
      
      // 2. 初始化认证
      await this.initializeAuth();
      
      // 3. 注册微应用
      this.registerMicroApps();
      
      // 4. 设置路由
      this.setupRouting();
      
      // 5. 初始化事件监听
      this.setupEventListeners();
      
      // 6. 加载主题配置
      this.loadTheme();
      
      // 7. 预加载常用微应用
      this.preloadCommonApps();
      
      console.log('App initialized successfully');
    } catch (error) {
      console.error('Failed to initialize app:', error);
      ErrorHandler.handleGlobalError(error);
    }
  }
  
  private async loadGlobalConfig(): Promise<void> {
    // 加载配置逻辑
  }
  
  private async initializeAuth(): Promise<void> {
    // 初始化认证逻辑
  }
  
  private registerMicroApps(): void {
    // 注册微应用
    microAppManager.registerApp({
      name: 'user-center',
      entry: '/micro-apps/user-center',
      container: '#micro-app-container',
      activeRule: '/user-center',
      preload: true
    });
    
    // 注册其他微应用...
  }
  
  private setupRouting(): void {
    // 设置路由逻辑
  }
  
  private setupEventListeners(): void {
    // 设置事件监听逻辑
  }
  
  private loadTheme(): void {
    // 加载主题逻辑
  }
  
  private preloadCommonApps(): void {
    // 预加载常用微应用
  }
}

// 应用入口
const appInitializer = new AppInitializer();
appInitializer.initialize().then(() => {
  // 渲染应用
});

- **全局状态同步**：使用Zustand实现跨微应用状态共享
- **数据缓存策略**：统一的缓存管理，减少重复请求
- **数据预加载**：基于用户行为预测和预加载数据

#### 3.3.3 架构设计原则

遵循标准的软件架构设计原则，包括依赖倒置、单一职责、开放封闭、接口隔离和里氏替换等核心原则，确保系统的高内聚低耦合。

#### 3.3.4 微前端集成

新模块将作为微应用集成到现有主应用中，采用与现有微前端架构一致的方式，基于wujie-react实现渐进式微前端架构：

##### 3.3.4.1 微前端架构模式

- **基于路由的微前端**：根据URL路由动态加载对应的微应用
- **基于组件的微前端**：在页面内按需加载微应用组件
- **混合模式**：结合上述两种模式，根据场景灵活选择

##### 3.3.4.2 微应用通信机制

采用多层次的通信策略：props传递用于初始化数据、事件总线实现消息广播、共享状态管理全局数据同步、postMessage确保跨应用安全通信。

##### 3.3.4.3 微应用生命周期管理

实现预加载策略优化性能，智能资源管理确保内存高效，以及完善的降级策略保障系统稳定性。

##### 3.3.4.4 微前端安全策略

通过wujie提供的JavaScript和CSS沙箱实现应用隔离，统一的权限验证机制和消息安全策略保障系统安全性。

### 3.4 目录结构

采用符合六边形架构和领域驱动设计理念的目录结构，清晰划分职责，提高代码可维护性和可测试性：

```
react-frontend-module/
├── 📁 public/                # 静态资源文件
│   ├── favicon.ico
│   └── robots.txt
├── 📁 src/                   # 源代码目录
│   ├── 📁 presentation/       # 表现层
│   │   ├── 📁 components/     # UI组件
│   │   │   ├── 📁 common/     # 通用基础组件
│   │   │   ├── 📁 layout/     # 布局组件
│   │   │   └── 📁 dynamic/    # 动态生成组件
│   │   ├── 📁 pages/          # 页面组件
│   │   │   ├── 📁 dashboard/  # 仪表盘页面
│   │   │   ├── 📁 entity/     # 实体管理页面
│   │   │   └── 📁 system/     # 系统设置页面
│   │   ├── 📁 router/         # 路由配置
│   │   └── 📁 guards/         # 路由守卫
│   │
│   ├── 📁 application/        # 应用层
│   │   ├── 📁 services/       # 应用服务（协调业务流程）
│   │   └── 📁 hooks/          # 应用级自定义hooks
│   │
│   ├── 📁 domain/             # 领域层
│   │   ├── 📁 entities/       # 领域实体定义
│   │   ├── 📁 services/       # 领域服务（核心业务逻辑）
│   │   ├── 📁 events/         # 领域事件
│   │   └── 📁 rules/          # 业务规则和验证
│   │
│   ├── 📁 infrastructure/     # 基础设施层
│   │   ├── 📁 api/            # API客户端
│   │   │   ├── 📁 clients/    # 各业务域API客户端
│   │   │   └── 📁 interceptors/ # 请求拦截器
│   │   ├── 📁 store/          # 状态管理
│   │   │   ├── 📁 slices/     # 各功能模块状态
│   │   │   └── 📁 middleware/ # 状态中间件
│   │   ├── 📁 utils/          # 工具函数
│   │   └── 📁 config/         # 配置文件
│   │
│   ├── 📁 ports/              # 端口层（接口定义）
│   │   ├── 📁 api/            # API接口定义
│   │   └── 📁 store/          # 状态接口定义
│   │
│   ├── 📁 shared/             # 共享资源
│   │   ├── 📁 types/          # TypeScript类型定义
│   │   ├── 📁 constants/      # 常量定义
│   │   ├── 📁 assets/         # 静态资源
│   │   ├── 📁 i18n/           # 国际化资源
│   │   └── 📁 styles/         # 全局样式
│   │
│   ├── 📁 micro-frontend/     # 微前端相关
│   │   ├── 📁 integration/    # 微应用集成配置
│   │   ├── 📁 communication/  # 微应用通信机制
│   │   └── 📁 lifecycle/      # 微应用生命周期管理
│   │
│   └── 📁 main.tsx            # 应用入口
├── 📁 tests/                 # 测试文件
│   ├── 📁 unit/               # 单元测试
│   ├── 📁 integration/        # 集成测试
│   └── 📁 e2e/                # 端到端测试
├── 📁 scripts/               # 构建和开发脚本
├── 📁 docs/                  # 文档
├── .eslintrc.js              # ESLint配置
├── .prettierrc.js            # Prettier配置
├── tsconfig.json             # TypeScript配置
├── tsconfig.node.json        # TypeScript配置（Node环境）
├── vite.config.ts            # Vite配置
├── package.json              # 项目依赖
└── README.md                 # 项目说明
```

#### 3.4.1 目录职责说明

- **presentation/**: 负责用户界面渲染和用户交互，包含所有UI组件和页面
- **application/**: 协调各领域服务，处理跨领域业务流程，是连接表现层和领域层的桥梁
- **domain/**: 包含核心业务逻辑、实体和业务规则，是系统的核心部分
- **infrastructure/**: 实现与外部系统的交互，包括API调用、状态管理等基础设施
- **ports/**: 定义领域层与外部世界交互的接口，实现依赖倒置
- **shared/**: 存放各模块共享的资源和工具
- **micro-frontend/**: 处理微前端相关的集成、通信和生命周期管理

### 3.5 核心功能模块

根据六边形架构设计，核心功能模块按照领域和职责进行组织，确保关注点分离和高内聚低耦合。

#### 3.5.1 元数据管理领域

**领域实体**：Metadata、EntityDefinition、FieldDefinition、RelationshipDefinition、IndexDefinition

**核心功能**：
- 实体元数据的CRUD操作
- 字段配置管理（类型、验证规则、显示属性等）
- 关系配置管理（一对一、一对多、多对多关系）
- 索引配置和优化
- 元数据版本管理和回滚
- 元数据导入导出

**领域服务**：提供元数据定义、验证、导入导出等核心业务功能

**应用服务**：协调跨领域操作，提供实体发布、版本回滚、版本比较等高级功能
```

#### 3.5.2 动态表单领域

**领域实体**：FormDefinition、FieldConfig、ValidationRule、FormLayout

**核心功能**：
- 基于元数据自动生成表单
- 支持20+种字段类型（文本、选择列表、日期、数字、货币、关联查找、文件上传、富文本等）
- 支持复杂验证规则和自定义验证器
- 支持表单布局配置（栅格、分组、分步骤等）
- 支持表单状态管理和数据转换
- 支持视图模式控制（编辑/查看/只读）

**实现策略**：
1. 采用插件化架构，每种字段类型实现为独立插件
2. 使用组合模式构建复杂表单结构
3. 实现表单状态管理与数据转换的分离

**表单引擎核心组件**：
```tsx
// DynamicFormEngine.tsx
interface DynamicFormEngineProps {
  metadata: EntityDefinition;
  initialValues?: Record<string, any>;
  onSubmit: (values: Record<string, any>) => Promise<void>;
  mode?: 'create' | 'edit' | 'view' | 'readonly';
  layoutConfig?: FormLayoutConfig;
  plugins?: FormPlugin[];
}

const DynamicFormEngine: React.FC<DynamicFormEngineProps> = ({ 
  metadata, 
  initialValues, 
  onSubmit,
  mode = 'edit',
  layoutConfig,
  plugins = []
}) => {
  // 字段渲染器注册表
  const fieldRenderers = useMemo(() => {
    return plugins.reduce((acc, plugin) => {
      if (plugin.fieldRenderers) {
        Object.entries(plugin.fieldRenderers).forEach(([type, renderer]) => {
          acc[type] = renderer;
        });
      }
      return acc;
    }, {} as Record<string, FieldRenderer>);
  }, [plugins]);

  // 验证规则处理器注册表
  const validationHandlers = useMemo(() => {
    return plugins.reduce((acc, plugin) => {
      if (plugin.validationHandlers) {
        Object.entries(plugin.validationHandlers).forEach(([rule, handler]) => {
          acc[rule] = handler;
        });
      }
      return acc;
    }, {} as Record<string, ValidationHandler>);
  }, [plugins]);

  // 表单值转换处理
  const transformValue = useCallback((value: any, field: FieldDefinition) => {
    // 执行值转换逻辑
    // ...
    return transformedValue;
  }, []);

  return (
    <FormContainer 
      metadata={metadata}
      initialValues={initialValues}
      onSubmit={onSubmit}
      mode={mode}
      layoutConfig={layoutConfig}
      fieldRenderers={fieldRenderers}
      validationHandlers={validationHandlers}
      transformValue={transformValue}
    />
  );
};
```

#### 3.5.3 动态表格领域

**领域实体**：TableDefinition、ColumnConfig、FilterConfig、SortConfig

**核心功能**：
- 基于元数据自动生成表格
- 支持复杂的列配置（数据类型、格式化、自定义渲染等）
- 支持高级排序和筛选（多字段排序、多条件筛选）
- 支持服务端分页和客户端分页
- 支持行操作（编辑、删除、查看详情等）
- 支持批量操作和自定义操作
- 支持表格状态管理（列显示、排序状态、筛选状态等）

**实现策略**：
1. 采用虚拟滚动优化大数据量渲染性能
2. 实现表格状态的本地持久化
3. 支持列宽调整、列显示隐藏控制

#### 3.5.4 实体数据管理领域

**领域实体**：EntityData、EntityQuery、EntityFilter、EntitySort

**核心功能**：
- 实体数据的CRUD操作
- 高级搜索和复杂条件筛选
- 实体数据的导入和导出（Excel、CSV、JSON等格式）
- 实体数据的批量处理
- 实体数据变更历史记录
- 实体数据版本控制

**领域服务**：提供实体数据的增删改查、查询、验证等核心功能
```

#### 3.5.5 数据可视化领域

**领域实体**：Dashboard、Widget、ChartConfig、DataSource

**核心功能**：
- 动态仪表盘配置和管理
- 支持多种图表类型（柱状图、折线图、饼图、散点图、地图等）
- 支持数据筛选和参数配置
- 支持仪表盘布局自定义
- 支持实时数据更新和自动刷新
- 支持仪表盘导出和共享

**实现策略**：
1. 采用适配器模式集成多种图表库
2. 实现图表数据的实时更新机制
3. 支持拖拽式仪表盘布局设计

#### 3.5.6 业务规则引擎领域

**领域实体**：BusinessRule、RuleCondition、RuleAction、RuleContext

**核心功能**：
- 业务规则定义和配置
- 规则条件表达式编辑
- 规则动作配置
- 规则执行和评估
- 规则版本管理

**领域服务**：提供规则创建、评估、执行和查询等核心功能
```

#### 3.5.7 微前端集成领域

**领域实体**：MicroApp、AppConfig、CommunicationChannel

**核心功能**：
- 微应用注册和配置管理
- 微应用生命周期管理
- 微应用间通信和数据共享
- 微应用安全隔离和权限控制
- 微应用性能监控和优化

**应用服务**：提供微应用注册、加载、卸载、预加载和消息通信等集成功能
```

#### 3.5.8 安全与权限领域

**领域实体**：User、Role、Permission、AccessControlList

**核心功能**：
- 基于角色的访问控制（RBAC）
- 细粒度权限管理（字段级、操作级权限）
- 权限验证和授权
- 操作审计日志
- 数据脱敏和安全显示

**安全实现策略**：
1. 实现前端权限守卫和路由拦截
2. 支持字段级权限控制和数据脱敏
3. 实现操作审计日志记录机制

### 3.6 组件设计

采用结合原子设计（Atomic Design）和领域驱动设计（DDD）的组件架构，确保组件的可复用性、可维护性和业务一致性。

#### 3.6.1 组件分类与层次结构

**1. 原子组件（Atoms）**
- **核心UI元素**：按钮、输入框、选择器、复选框等纯UI组件
- **通用工具组件**：图标、徽章、分隔线等
- **设计原则**：完全与业务逻辑解耦，仅负责UI渲染和基础交互

**2. 分子组件（Molecules）**
- **组合UI元素**：表单行、表格单元格、卡片等
- **功能小组件**：搜索框、分页控件等
- **设计原则**：由原子组件组合而成，具备基本功能完整性

**3. 组织组件（Organisms）**
- **业务功能组件**：动态表单、数据表格、详情面板等
- **领域特定组件**：实体管理卡片、元数据配置面板等
- **设计原则**：实现特定业务功能，包含业务逻辑和状态管理

**4. 模板组件（Templates）**
- **页面布局模板**：列表详情页、配置页、向导页等
- **流程模板组件**：创建流程、编辑流程等
- **设计原则**：定义页面结构和布局模式

**5. 页面组件（Pages）**
- **完整业务页面**：实体管理页、元数据配置页等
- **设计原则**：组合组织组件和模板组件，实现完整业务流程

#### 3.6.2 组件设计原则

**1. 关注点分离**
- 表现层与业务逻辑分离
- UI渲染与状态管理分离
- 业务逻辑与数据获取分离

**2. 组件生命周期管理**：实现标准化的生命周期钩子，包含挂载、卸载等关键节点的日志记录和资源管理。
```

**3. 错误边界与降级处理**：实现错误边界组件捕获渲染错误，提供友好的降级UI，并记录错误信息。
```

**4. 性能优化策略**
- 使用React.memo避免不必要的重渲染
- 使用useMemo缓存计算结果
- 使用useCallback缓存回调函数
- 虚拟滚动处理大数据渲染
- 组件懒加载和代码分割

**5. 可访问性设计**
- 符合WCAG 2.1 AA级标准
- 支持键盘导航和屏幕阅读器
- 提供适当的ARIA属性
- 确保颜色对比度符合标准

#### 3.6.3 组件通信模式

**1. 组件树内通信**
- **Props传递**：父组件向子组件传递数据和回调
- **回调函数**：子组件向父组件传递事件和数据
- **Context API**：同层级或跨层级组件通信

**2. 状态管理通信**
- **本地状态**：组件内部状态管理
- **全局状态**：使用Zustand管理跨组件状态
- **领域状态**：按业务领域划分状态管理

**3. 微前端组件通信**
- **事件总线**：使用全局事件总线实现微应用间通信
- **共享状态**：通过共享store实现状态同步
- **消息通道**：使用postMessage进行跨应用通信

#### 3.6.4 领域特定组件示例

**动态表单组件**
```typescript
// 增强的动态表单组件设计
interface EnhancedDynamicFormProps {
  entityName: string;
  initialValues?: Record<string, any>;
  onFinish: (values: Record<string, any>) => void;
  mode?: 'create' | 'edit' | 'view';
  layout?: 'vertical' | 'horizontal' | 'inline';
  columns?: number; // 支持多列布局
  groupedFields?: Array<{ // 支持字段分组
    title: string;
    fields: string[];
  }>;
  readOnlyFields?: string[]; // 部分字段只读
  hiddenFields?: string[]; // 隐藏特定字段
  customComponents?: Record<string, React.ComponentType>; // 自定义组件映射
  beforeSubmit?: (values: Record<string, any>) => Promise<Record<string, any>>; // 提交前处理
}
```

**动态列表组件**
```typescript
// 动态列表组件设计
interface DynamicListProps {
  entityName: string;
  columns?: Array<{ // 支持自定义列配置
    dataIndex: string;
    title: string;
    render?: (text: any, record: any, index: number) => React.ReactNode;
  }>;
  rowActions?: Array<{ // 支持自定义行操作
    key: string;
    text: string;
    icon?: React.ReactNode;
    onClick: (record: any) => void;
    permission?: string;
  }>;
  batchActions?: Array<{ // 支持批量操作
    key: string;
    text: string;
    icon?: React.ReactNode;
    onClick: (selectedRows: any[]) => void;
    permission?: string;
  }>;
  searchConfig?: { // 搜索配置
    enabled: boolean;
    fields?: string[];
    advancedSearch: boolean;
  };
  pagination?: { // 分页配置
    enabled: boolean;
    pageSize: number;
    pageSizeOptions?: number[];
  };
  rowSelection?: boolean; // 是否支持行选择
  onRowClick?: (record: any) => void; // 行点击事件
}
```

**可复用业务组件**
- **EntitySelector**：实体选择器，支持搜索、多选
- **MetadataViewer**：元数据查看器，展示实体结构
- **OperationButtonGroup**：操作按钮组，根据权限动态显示
- **StatusBadge**：状态标签，根据状态值显示不同样式
- **HistoryTimeline**：历史时间线，展示操作历史

#### 3.6.5 组件文档与测试

**1. 组件文档化**
- 使用Storybook构建组件库文档
- 组件属性API文档
- 使用示例和最佳实践
- 设计规范和使用指南

**2. 组件测试策略**
```typescript
// 组件单元测试示例
import { render, screen, fireEvent } from '@testing-library/react';
import Button from './Button';

组件测试采用Jest和React Testing Library，包含渲染测试、交互测试等标准测试用例。
```

**3. 组件版本管理**
- 语义化版本控制
- 向后兼容性保障
- 废弃策略和迁移指南

### 3.7 状态管理设计

采用基于Zustand的现代化状态管理方案，结合六边形架构和领域驱动设计思想，实现状态的分层管理和关注点分离。

#### 3.7.1 状态分层架构

**1. 领域状态层（Domain State）**
- 包含核心业务领域的状态
- 与业务逻辑紧密相关
- 按业务领域划分不同的状态单元
- 保持高内聚低耦合

**2. 应用状态层（Application State）**
- 协调跨领域的业务流程状态
- 管理应用级别的共享状态
- 处理领域间的状态同步

**3. UI状态层（UI State）**
- 管理纯UI相关的状态
- 包含表单状态、视图状态等
- 与特定UI组件绑定

**4. 会话状态层（Session State）**
- 管理用户会话相关状态
- 包含认证信息、用户偏好等
- 需要持久化的状态

#### 3.7.2 状态管理实现策略

**1. 领域状态实现**：基于Zustand管理业务实体状态，包含数据存储、加载状态、错误处理等功能，通过依赖注入与领域服务集成。

**2. 应用状态实现**：管理全局应用状态，包括当前活动模块、面包屑导航、通知系统等，使用subscribeWithSelector中间件优化性能。
```

**3. 会话状态实现**
```typescript
// infrastructure/auth/sessionState.ts
import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import { AuthService } from './authService';

interface SessionState {
  // 认证状态
  user: {
    id: string;
    username: string;
    email: string;
    role: string;
    permissions: string[];
  } | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  
  // 认证操作
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshToken: () => Promise<void>;
  clearError: () => void;
}

// 使用自定义存储实现安全的token存储
const secureStorage = {
  getItem: (name: string): string | null => {
    try {
      // 敏感信息可考虑使用更安全的存储方式
      return sessionStorage.getItem(name);
    } catch (error) {
      console.error('Error reading from storage:', error);
      return null;
    }
  },
  setItem: (name: string, value: string): void => {
    try {
      sessionStorage.setItem(name, value);
    } catch (error) {
      console.error('Error writing to storage:', error);
    }
  },
  removeItem: (name: string): void => {
    try {
      sessionStorage.removeItem(name);
    } catch (error) {
      console.error('Error removing from storage:', error);
    }
  },
};

export const useSessionState = create<SessionState>()(
  persist(
    (set, get) => {
      const authService = new AuthService();
      
      return {
        user: null,
        token: null,
        isAuthenticated: false,
        isLoading: false,
        error: null,
        
        login: async (username, password) => {
          set({ isLoading: true, error: null });
          try {
            const { user, token } = await authService.login(username, password);
            set({
              user,
              token,
              isAuthenticated: true,
              isLoading: false
            });
          } catch (error) {
            set({
              error: error instanceof Error ? error.message : '登录失败',
              isLoading: false
            });
          }
        },
        
        logout: async () => {
          try {
            await authService.logout();
          } catch (error) {
            console.error('Logout error:', error);
          } finally {
            set({
              user: null,
              token: null,
              isAuthenticated: false
            });
          }
        },
        
        refreshToken: async () => {
          const { token } = get();
          if (!token) return;
          
          try {
            const newToken = await authService.refreshToken(token);
            set({ token: newToken });
          } catch (error) {
            console.error('Token refresh error:', error);
            get().logout();
          }
        },
        
        clearError: () => set({ error: null })
      };
    },
    {
      name: 'session-storage',
      storage: createJSONStorage(() => secureStorage),
      partialize: (state) => ({
        user: state.user,
        token: state.token,
        isAuthenticated: state.isAuthenticated
      }),
      onRehydrateStorage: () => (state) => {
        // 恢复状态后的操作
        if (state?.token) {
          // 设置token自动刷新
          const refreshInterval = setInterval(() => {
            state?.refreshToken();
          }, 30 * 60 * 1000); // 30分钟刷新一次
          
          return () => clearInterval(refreshInterval);
        }
      }
    }
  )
);
```

#### 3.7.3 状态管理中间件

实现错误处理、性能监控和状态验证等中间件，增强状态管理的健壮性和可观测性。
```

#### 3.7.4 状态管理最佳实践

1. **状态分层和隔离**
   - 按领域和职责划分状态
   - 避免状态耦合和全局混乱

2. **状态不变性**
   - 始终创建新对象/数组而不是修改现有状态
   - 使用不可变数据结构辅助工具

3. **性能优化**
   - 使用选择器(selectors)获取状态，避免不必要的重渲染
   - 合理使用memoization缓存计算结果
   - 避免在状态中存储派生数据

4. **状态持久化**
   - 仅持久化必要的状态数据
   - 敏感信息使用安全的存储方式
   - 实现状态恢复和初始化策略

5. **异步操作处理**
   - 清晰的加载状态和错误处理
   - 使用异步中间件统一处理异步操作
   - 实现乐观更新策略提升用户体验

6. **测试友好**
   - 状态逻辑与UI分离，便于单元测试
   - 使用依赖注入，便于模拟和测试
   - 编写状态转换的单元测试

### 3.8 API集成设计

基于Bone后端的元数据驱动架构，采用增强的六边形架构实现API集成，确保前端与后端动态服务的无缝对接，提高系统的可扩展性和可维护性。

#### 3.8.1 API集成架构

**1. 端口层（Ports）**
- 定义与外部API交互的抽象接口
- 领域层通过这些接口与外部系统通信
- 实现依赖倒置，避免领域层依赖具体实现
- 支持元数据驱动的动态接口定义

**2. 适配器层（Adapters）**
- 实现端口层定义的接口
- 负责与具体HTTP客户端和后端API交互
- 处理请求格式化、响应解析和错误转换
- 适配后端SmartMeta引擎的动态API结构

**3. 客户端层（Client）**
- 基于Axios的HTTP客户端实现
- 提供请求拦截、响应拦截、超时处理等基础功能
- 与后端GlobalExceptionHandler保持一致的错误处理机制

**4. 服务层（Services）**
- 协调API调用和业务逻辑
- 实现请求组合、数据转换等功能
- 集成元数据驱动的数据处理能力

**5. 元数据适配层**
- 动态适配后端元数据模型
- 提供前端元数据缓存和管理
- 支持实体、字段、关系等元数据的前端表示
- 实现元数据驱动的表单和列表动态生成

#### 3.8.2 端口层设计

定义标准化的API接口，包括实体查询参数、响应格式和通用CRUD操作，实现与后端API的对接和依赖倒置原则。同时提供认证API端口，处理用户登录、注册、认证状态管理等功能，以及元数据API端口支持SmartMeta引擎。
  create?: string;
  read?: string;
  update?: string;
  delete?: string;
  custom?: Record<string, string>;
}

interface ValidationRule {
  type: string;
  message: string;
  params?: Record<string, any>;
}

interface OperationParam {
  name: string;
  type: string;
  required: boolean;
  defaultValue?: any;
}

interface SmartQuery {
  entity: string;
  fields?: string[];
  filters?: Filter[];
  sortBy?: SortField[];
  groupBy?: string[];
  aggregations?: Aggregation[];
  includes?: Include[];
}

interface Filter {
  field: string;
  operator: string;
  value: any;
  conjunction?: 'AND' | 'OR';
}

interface SortField {
  field: string;
  direction: 'asc' | 'desc';
}

interface Aggregation {
  field: string;
  type: 'sum' | 'avg' | 'min' | 'max' | 'count';
  alias?: string;
}

interface Include {
  entity: string;
  fields?: string[];
  filters?: Filter[];
}
```

#### 3.8.3 HTTP客户端实现

```typescript
// infrastructure/api/httpClient.ts
import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse, AxiosError } from 'axios';
import { useSessionState } from '../auth/sessionState';
import { ApiError, ErrorType, ErrorHandler } from './errors';
import { microAppConfig } from '../../config/microAppConfig';
import { GlobalEventBus } from '../../shared/GlobalEventBus';
import { AppEvents } from '../../shared/constants';

// 基础HTTP客户端配置
interface HttpClientConfig {
  baseURL: string;
  timeout?: number;
  headers?: Record<string, string>;
  retryOptions?: RetryOptions;
  cacheOptions?: CacheOptions;
}

interface RetryOptions {
  attempts: number;
  delay: number;
  maxDelay?: number;
  exponentialBackoff?: boolean;
  retryableStatusCodes?: number[];
}

interface CacheOptions {
  enabled: boolean;
  ttl: number;
  maxSize: number;
}

// API请求上下文
interface ApiContext {
  requestId: string;
  startTime: number;
  appName: string;
  correlationId?: string;
}

// 缓存项接口
interface CacheItem<T> {
  data: T;
  timestamp: number;
  expiry: number;
}

// 创建可配置的HTTP客户端
class HttpClient {
  private instance: AxiosInstance;
  private retryOptions: RetryOptions;
  private cacheOptions: CacheOptions;
  private memoryCache: Map<string, CacheItem<any>>;

  constructor(config: HttpClientConfig) {
    this.retryOptions = config.retryOptions || {
      attempts: 3,
      delay: 1000,
      maxDelay: 10000,
      exponentialBackoff: true,
      retryableStatusCodes: [429, 500, 502, 503, 504]
    };
    
    this.cacheOptions = config.cacheOptions || {
      enabled: false,
      ttl: 60000,
      maxSize: 100
    };
    
    this.memoryCache = new Map();
    
    // 创建axios实例
    this.instance = axios.create({
      baseURL: config.baseURL,
      timeout: config.timeout || 30000,
      headers: {
        'Content-Type': 'application/json',
        'X-App-Name': microAppConfig.appName || 'bone-frontend',
        'X-App-Version': microAppConfig.version || '1.0.0',
        ...config.headers,
      },
    });

    // 设置拦截器
    this.setupInterceptors();
  }

  // 设置拦截器
  private setupInterceptors() {
    // 请求拦截器
    this.instance.interceptors.request.use(
      this.handleRequest,
      this.handleRequestError
    );

    // 响应拦截器
    this.instance.interceptors.response.use(
      this.handleResponse,
      this.handleResponseError
    );
  }

  // 请求处理
  private handleRequest = (config: AxiosRequestConfig): AxiosRequestConfig => {
    // 创建API上下文
    const context: ApiContext = {
      requestId: this.generateRequestId(),
      startTime: Date.now(),
      appName: microAppConfig.appName || 'bone-frontend'
    };
    
    // 存储上下文到配置中
    (config as any).context = context;

    // 添加认证令牌
    const token = useSessionState.getState().token;
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // 添加请求ID和其他标准头
    if (!config.headers) config.headers = {};
    config.headers['X-Request-ID'] = context.requestId;
    
    // 从全局上下文中获取关联ID（用于分布式追踪）
    const correlationId = GlobalEventBus.getInstance().getCorrelationId();
    if (correlationId) {
      config.headers['X-Correlation-ID'] = correlationId;
      context.correlationId = correlationId;
    }

    // 添加版本信息
    if (microAppConfig.version) {
      config.headers['X-App-Version'] = microAppConfig.version;
    }

    // 记录请求日志
    if (import.meta.env.DEV) {
      console.debug(`[API Request] ${config.method?.toUpperCase()} ${config.url}`, {
        params: config.params,
        data: config.data,
        requestId: context.requestId
      });
    }

    // 发布请求开始事件
    GlobalEventBus.getInstance().emit(AppEvents.API_REQUEST_START, {
      url: config.url,
      method: config.method,
      requestId: context.requestId
    });

    return config;
  };

  // 请求错误处理
  private handleRequestError = (error: AxiosError): Promise<AxiosError> => {
    console.error('[API Request Error]', error.config, error.message);
    return Promise.reject(error);
  };

  // 响应处理
  private handleResponse = (response: AxiosResponse): any => {
    const context = (response.config as any).context as ApiContext;
    const duration = Date.now() - context.startTime;
    
    // 记录响应日志
    if (import.meta.env.DEV) {
      console.debug(`[API Response] ${response.config?.url} (${duration}ms)`, {
        status: response.status,
        requestId: context.requestId
      });
    }

    // 发布请求完成事件
    GlobalEventBus.getInstance().emit(AppEvents.API_REQUEST_COMPLETE, {
      url: response.config?.url,
      method: response.config?.method,
      status: response.status,
      duration,
      requestId: context.requestId
    });

    // 处理缓存（仅对GET请求）
    if (response.config.method === 'get' && this.cacheOptions.enabled && response.status === 200) {
      const cacheKey = this.generateCacheKey(response.config);
      this.setCache(cacheKey, response.data);
    }

    // 返回响应数据（与后端ApiResponse格式保持一致）
    return response.data;
  };

  // 响应错误处理
  private handleResponseError = async (error: AxiosError): Promise<never> => {
    const config = error.config as AxiosRequestConfig & { context?: ApiContext };
    const context = config?.context || {
      requestId: this.generateRequestId(),
      startTime: Date.now(),
      appName: microAppConfig.appName || 'bone-frontend'
    };
    
    const duration = Date.now() - context.startTime;

    // 记录错误日志
    console.error(
      `[API Response Error] ${config?.url} (${duration}ms)`,
      error.response?.status,
      error.response?.data || error.message,
      { requestId: context.requestId }
    );

    // 发布请求错误事件
    GlobalEventBus.getInstance().emit(AppEvents.API_REQUEST_ERROR, {
      url: config?.url,
      method: config?.method,
      status: error.response?.status,
      duration,
      error: error.message,
      requestId: context.requestId
    });

    // 处理特定错误类型
    if (error.response) {
      const { status, data } = error.response;
      
      // 处理401未授权错误
      if (status === 401) {
        // 清除认证状态并发布认证过期事件
        useSessionState.getState().logout();
        GlobalEventBus.getInstance().emit(AppEvents.AUTH_EXPIRED);
      }

      // 处理403权限错误
      if (status === 403) {
        GlobalEventBus.getInstance().emit(AppEvents.PERMISSION_DENIED, {
          url: config?.url,
          message: data?.message || '权限不足'
        });
      }

      // 使用统一错误处理工具转换错误
      throw ErrorHandler.handleApiError(error);
    }

    // 网络错误重试逻辑
    if ((error.code === 'ECONNABORTED' || !error.response) && config) {
      // 获取或初始化重试计数
      const retryCount = (config as any).retryCount || 0;
      
      // 检查是否应该重试
      if (retryCount < this.retryOptions.attempts) {
        (config as any).retryCount = retryCount + 1;
        
        // 计算延迟时间（支持指数退避）
        let delay = this.retryOptions.delay;
        if (this.retryOptions.exponentialBackoff) {
          delay = Math.min(
            this.retryOptions.delay * Math.pow(2, retryCount),
            this.retryOptions.maxDelay || 30000
          );
        }
        
        console.info(`[API Retry] ${config.url} (${retryCount + 1}/${this.retryOptions.attempts})`);
        
        // 发布重试事件
        GlobalEventBus.getInstance().emit(AppEvents.API_REQUEST_RETRY, {
          url: config.url,
          attempt: retryCount + 1,
          maxAttempts: this.retryOptions.attempts,
          delay
        });
        
        return new Promise((resolve) => {
          setTimeout(() => resolve(this.instance(config)), delay);
        });
      }
    }

    // 创建并抛出网络错误
    throw ApiError.createNetworkError(error.message);
  };

  // 生成请求ID
  private generateRequestId(): string {
    return `req_${Date.now()}_${Math.random().toString(36).substring(2, 11)}`;
  }
  
  // 生成缓存键
  private generateCacheKey(config: AxiosRequestConfig): string {
    const url = config.url || '';
    const params = config.params ? JSON.stringify(config.params) : '';
    return `${config.method || 'get'}:${url}:${params}`;
  }
  
  // 设置缓存
  private setCache<T>(key: string, data: T): void {
    if (!this.cacheOptions.enabled) return;
    
    // 检查缓存大小限制
    if (this.memoryCache.size >= this.cacheOptions.maxSize) {
      // 移除最早的缓存项
      const firstKey = this.memoryCache.keys().next().value;
      this.memoryCache.delete(firstKey);
    }
    
    const expiry = Date.now() + this.cacheOptions.ttl;
    this.memoryCache.set(key, {
      data,
      timestamp: Date.now(),
      expiry
    });
  }
  
  // 获取缓存
  private getCache<T>(key: string): T | null {
    if (!this.cacheOptions.enabled) return null;
    
    const item = this.memoryCache.get(key);
    if (!item) return null;
    
    // 检查是否过期
    if (Date.now() > item.expiry) {
      this.memoryCache.delete(key);
      return null;
    }
    
    return item.data;
  }
  
  // 清除缓存
  clearCache(): void {
    this.memoryCache.clear();
  }
  
  // 清除特定缓存项
  invalidateCache(pattern: string): void {
    if (!this.cacheOptions.enabled) return;
    
    for (const key of this.memoryCache.keys()) {
      if (key.includes(pattern)) {
        this.memoryCache.delete(key);
      }
    }
  }

  // 暴露请求方法
  get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    // 尝试从缓存获取
    if (this.cacheOptions.enabled && !config?.forceRefresh) {
      const cacheKey = this.generateCacheKey({
        ...config,
        url,
        method: 'get'
      });
      
      const cachedData = this.getCache<T>(cacheKey);
      if (cachedData) {
        // 发布缓存命中事件
        GlobalEventBus.getInstance().emit(AppEvents.API_CACHE_HIT, {
          url,
          cacheKey
        });
        return Promise.resolve(cachedData);
      }
    }
    
    return this.instance.get(url, config);
  }
  
  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    // 清除相关缓存
    if (this.cacheOptions.enabled) {
      this.invalidateCache(url.split('/')[0]);
    }
    return this.instance.post(url, data, config);
  }
  
  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    // 清除相关缓存
    if (this.cacheOptions.enabled) {
      this.invalidateCache(url.split('/')[0]);
    }
    return this.instance.put(url, data, config);
  }
  
  patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    // 清除相关缓存
    if (this.cacheOptions.enabled) {
      this.invalidateCache(url.split('/')[0]);
    }
    return this.instance.patch(url, data, config);
  }
  
  delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T> {
    // 清除相关缓存
    if (this.cacheOptions.enabled) {
      this.invalidateCache(url.split('/')[0]);
    }
    return this.instance.delete(url, config);
  }
  
  // 文件上传方法
  upload<T = any>(url: string, formData: FormData, config?: AxiosRequestConfig): Promise<T> {
    return this.instance.post(url, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
        ...config?.headers,
      },
      ...config,
    });
  }
  
  // 批量请求方法
  async batchRequests<T = any>(requests: Array<{ method: string; url: string; data?: any; config?: AxiosRequestConfig }>): Promise<T[]> {
    const promises = requests.map(req => {
      const method = req.method.toLowerCase() as keyof Pick<AxiosInstance, 'get' | 'post' | 'put' | 'patch' | 'delete'>;
      
      if (this.instance[method]) {
        if (['get', 'delete'].includes(method)) {
          return this.instance[method](req.url, req.config);
        } else {
          return this.instance[method](req.url, req.data, req.config);
        }
      }
      
      return Promise.reject(new Error(`Unsupported method: ${req.method}`));
    });

    return Promise.all(promises);
  }
  
  // 发送表单数据
  postForm<T = any>(url: string, formData: FormData, config?: AxiosRequestConfig): Promise<T> {
    return this.instance.post<T>(url, formData, {
      ...config,
      headers: {
        ...config?.headers,
        'Content-Type': 'multipart/form-data'
      }
    });
  }
  
  // 执行元数据驱动的查询
  executeMetadataQuery<T = any>(entityName: string, query: any, config?: AxiosRequestConfig): Promise<T> {
    return this.post(`/metadata/query/${entityName}`, query, {
      ...config,
      headers: {
        'X-Metadata-Version': microAppConfig.metadataVersion || '1.0',
        ...config?.headers
      }
    });
  }
}

// 创建默认客户端实例
export const httpClient = new HttpClient({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  retryOptions: {
    attempts: parseInt(import.meta.env.VITE_API_RETRY_ATTEMPTS || '3'),
    delay: parseInt(import.meta.env.VITE_API_RETRY_DELAY || '1000'),
    maxDelay: parseInt(import.meta.env.VITE_API_RETRY_MAX_DELAY || '10000'),
    exponentialBackoff: true
  },
  cacheOptions: {
    enabled: import.meta.env.VITE_API_CACHE_ENABLED === 'true',
    ttl: parseInt(import.meta.env.VITE_API_CACHE_TTL || '60000'),
    maxSize: parseInt(import.meta.env.VITE_API_CACHE_MAX_SIZE || '100')
  }
});

export default HttpClient;
```

#### 3.8.4 API适配器实现

```typescript
// infrastructure/api/adapters/entityApiAdapter.ts
import { EntityApiPort, EntityQueryParams, EntityListResponse } from '../../../ports/api/entityApiPort';
import { httpClient } from '../httpClient';
import { ApiError, ErrorHandler } from '../errors';
import { GlobalEventBus } from '../../../shared/GlobalEventBus';
import { AppEvents } from '../../../shared/constants';
import { microAppConfig } from '../../../config/microAppConfig';

// 实体API适配器 - 实现实体API端口接口
export class EntityApiAdapter<T> implements EntityApiPort<T> {
  private endpoint: string;
  private entityName: string;
  private cacheEnabled: boolean;

  constructor(entityName: string) {
    this.entityName = entityName;
    this.endpoint = `/entities/${entityName}`;
    this.cacheEnabled = import.meta.env.VITE_API_CACHE_ENABLED === 'true';
  }

  // 获取实体列表
  async getEntities(params?: EntityQueryParams): Promise<EntityListResponse<T>> {
    try {
      // 添加元数据版本信息到请求头
      const headers = {
        'X-Entity-Name': this.entityName,
        'X-Metadata-Version': microAppConfig.metadataVersion || '1.0'
      };
      
      // 发布API调用开始事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_START, {
        entity: this.entityName,
        operation: 'list',
        params
      });
      
      const startTime = Date.now();
      const response = await httpClient.get<EntityListResponse<T>>(this.endpoint, { 
        params,
        headers
      });
      
      // 发布API调用完成事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_COMPLETE, {
        entity: this.entityName,
        operation: 'list',
        duration: Date.now() - startTime,
        resultSize: response.data?.length || 0
      });
      
      return response;
    } catch (error) {
      // 发布API调用错误事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_ERROR, {
        entity: this.entityName,
        operation: 'list',
        error: error instanceof Error ? error.message : String(error)
      });
      
      // 使用统一错误处理器
      throw ErrorHandler.handleApiError(error as any);
    }
  }

  // 根据ID获取实体详情
  async getEntityById(id: string): Promise<T> {
    try {
      // 添加元数据版本信息到请求头
      const headers = {
        'X-Entity-Name': this.entityName,
        'X-Metadata-Version': microAppConfig.metadataVersion || '1.0'
      };
      
      // 发布API调用开始事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_START, {
        entity: this.entityName,
        operation: 'get',
        id
      });
      
      const startTime = Date.now();
      const response = await httpClient.get<T>(`${this.endpoint}/${id}`, { headers });
      
      // 发布API调用完成事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_COMPLETE, {
        entity: this.entityName,
        operation: 'get',
        id,
        duration: Date.now() - startTime
      });
      
      return response;
    } catch (error) {
      // 发布API调用错误事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_ERROR, {
        entity: this.entityName,
        operation: 'get',
        id,
        error: error instanceof Error ? error.message : String(error)
      });
      
      // 使用统一错误处理器
      throw ErrorHandler.handleApiError(error as any);
    }
  }

  // 创建实体
  async createEntity(data: Partial<T>): Promise<T> {
    try {
      // 添加元数据版本信息到请求头
      const headers = {
        'X-Entity-Name': this.entityName,
        'X-Metadata-Version': microAppConfig.metadataVersion || '1.0'
      };
      
      // 发布API调用开始事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_START, {
        entity: this.entityName,
        operation: 'create',
        data
      });
      
      const startTime = Date.now();
      const response = await httpClient.post<T>(this.endpoint, data, { headers });
      
      // 创建操作后清除相关缓存
      if (this.cacheEnabled) {
        httpClient.invalidateCache(this.entityName);
      }
      
      // 发布API调用完成事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_COMPLETE, {
        entity: this.entityName,
        operation: 'create',
        duration: Date.now() - startTime
      });
      
      // 发布实体创建事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_CREATED, {
        entity: this.entityName,
        data: response
      });
      
      return response;
    } catch (error) {
      // 发布API调用错误事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_ERROR, {
        entity: this.entityName,
        operation: 'create',
        error: error instanceof Error ? error.message : String(error)
      });
      
      // 使用统一错误处理器
      throw ErrorHandler.handleApiError(error as any);
    }
  }

  // 更新实体
  async updateEntity(id: string, data: Partial<T>): Promise<T> {
    try {
      // 添加元数据版本信息到请求头
      const headers = {
        'X-Entity-Name': this.entityName,
        'X-Metadata-Version': microAppConfig.metadataVersion || '1.0'
      };
      
      // 发布API调用开始事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_START, {
        entity: this.entityName,
        operation: 'update',
        id,
        data
      });
      
      const startTime = Date.now();
      const response = await httpClient.put<T>(`${this.endpoint}/${id}`, data, { headers });
      
      // 更新操作后清除相关缓存
      if (this.cacheEnabled) {
        httpClient.invalidateCache(this.entityName);
      }
      
      // 发布API调用完成事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_COMPLETE, {
        entity: this.entityName,
        operation: 'update',
        id,
        duration: Date.now() - startTime
      });
      
      // 发布实体更新事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_UPDATED, {
        entity: this.entityName,
        id,
        data: response
      });
      
      return response;
    } catch (error) {
      // 发布API调用错误事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_ERROR, {
        entity: this.entityName,
        operation: 'update',
        id,
        error: error instanceof Error ? error.message : String(error)
      });
      
      // 使用统一错误处理器
      throw ErrorHandler.handleApiError(error as any);
    }
  }

  // 删除实体
  async deleteEntity(id: string): Promise<void> {
    try {
      // 添加元数据版本信息到请求头
      const headers = {
        'X-Entity-Name': this.entityName,
        'X-Metadata-Version': microAppConfig.metadataVersion || '1.0'
      };
      
      // 发布API调用开始事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_START, {
        entity: this.entityName,
        operation: 'delete',
        id
      });
      
      const startTime = Date.now();
      await httpClient.delete(`${this.endpoint}/${id}`, { headers });
      
      // 删除操作后清除相关缓存
      if (this.cacheEnabled) {
        httpClient.invalidateCache(this.entityName);
      }
      
      // 发布API调用完成事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_COMPLETE, {
        entity: this.entityName,
        operation: 'delete',
        id,
        duration: Date.now() - startTime
      });
      
      // 发布实体删除事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_DELETED, {
        entity: this.entityName,
        id
      });
    } catch (error) {
      // 发布API调用错误事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_ERROR, {
        entity: this.entityName,
        operation: 'delete',
        id,
        error: error instanceof Error ? error.message : String(error)
      });
      
      // 使用统一错误处理器
      throw ErrorHandler.handleApiError(error as any);
    }
  }

  // 批量删除实体
  async deleteEntities(ids: string[]): Promise<void> {
    try {
      // 添加元数据版本信息到请求头
      const headers = {
        'X-Entity-Name': this.entityName,
        'X-Metadata-Version': microAppConfig.metadataVersion || '1.0'
      };
      
      // 发布API调用开始事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_START, {
        entity: this.entityName,
        operation: 'batchDelete',
        ids
      });
      
      const startTime = Date.now();
      await httpClient.delete(`${this.endpoint}/batch`, {
        data: { ids }, // 使用data参数传递批量删除的ID列表
        headers
      });
      
      // 批量删除操作后清除相关缓存
      if (this.cacheEnabled) {
        httpClient.invalidateCache(this.entityName);
      }
      
      // 发布API调用完成事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_COMPLETE, {
        entity: this.entityName,
        operation: 'batchDelete',
        idsCount: ids.length,
        duration: Date.now() - startTime
      });
      
      // 发布实体批量删除事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITIES_DELETED, {
        entity: this.entityName,
        ids
      });
    } catch (error) {
      // 发布API调用错误事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_ERROR, {
        entity: this.entityName,
        operation: 'batchDelete',
        idsCount: ids.length,
        error: error instanceof Error ? error.message : String(error)
      });
      
      // 使用统一错误处理器
      throw ErrorHandler.handleApiError(error as any);
    }
  }

  // 批量更新实体
  async updateEntities(updates: Array<{id: string; data: Partial<T>}>): Promise<T[]> {
    try {
      // 添加元数据版本信息到请求头
      const headers = {
        'X-Entity-Name': this.entityName,
        'X-Metadata-Version': microAppConfig.metadataVersion || '1.0'
      };
      
      // 发布API调用开始事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_START, {
        entity: this.entityName,
        operation: 'batchUpdate',
        updatesCount: updates.length
      });
      
      const startTime = Date.now();
      const response = await httpClient.put<Array<T>>(`${this.endpoint}/batch`, updates, { headers });
      
      // 批量更新操作后清除相关缓存
      if (this.cacheEnabled) {
        httpClient.invalidateCache(this.entityName);
      }
      
      // 发布API调用完成事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_COMPLETE, {
        entity: this.entityName,
        operation: 'batchUpdate',
        updatesCount: updates.length,
        duration: Date.now() - startTime
      });
      
      // 发布实体批量更新事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITIES_UPDATED, {
        entity: this.entityName,
        updates: response
      });
      
      return response;
    } catch (error) {
      // 发布API调用错误事件
      GlobalEventBus.getInstance().emit(AppEvents.ENTITY_API_CALL_ERROR, {
        entity: this.entityName,
        operation: 'batchUpdate',
        updatesCount: updates.length,
        error: error instanceof Error ? error.message : String(error)
      });
      
      // 使用统一错误处理器
      throw ErrorHandler.handleApiError(error as any);
    }
  }
}

// 创建特定实体的API适配器工厂函数
export function createEntityApiAdapter<T>(entityName: string): EntityApiPort<T> {
  return new EntityApiAdapter<T>(entityName);
}
```

#### 3.8.5 错误处理系统

```typescript
// infrastructure/api/errors.ts

// 错误通知选项接口
export interface ErrorNotificationOptions {
  title?: string;
  type?: 'success' | 'error' | 'warning' | 'info';
  duration?: number;
  showIcon?: boolean;
  placement?: 'topLeft' | 'topRight' | 'bottomLeft' | 'bottomRight';
  retryable?: boolean;
  onClick?: () => void;
  onClose?: () => void;
  onRetry?: () => void;
}

// 错误类型枚举
export enum ErrorType {
  API_ERROR = 'API_ERROR',
  NETWORK_ERROR = 'NETWORK_ERROR',
  VALIDATION_ERROR = 'VALIDATION_ERROR',
  AUTH_ERROR = 'AUTH_ERROR',
  PERMISSION_ERROR = 'PERMISSION_ERROR',
  NOT_FOUND_ERROR = 'NOT_FOUND_ERROR',
  SERVER_ERROR = 'SERVER_ERROR',
  CLIENT_ERROR = 'CLIENT_ERROR',
  METADATA_ERROR = 'METADATA_ERROR', // 元数据相关错误
  METADATA_VERSION_ERROR = 'METADATA_VERSION_ERROR', // 元数据版本不匹配
  BUSINESS_RULE_ERROR = 'BUSINESS_RULE_ERROR', // 业务规则验证错误
  DATA_INTEGRITY_ERROR = 'DATA_INTEGRITY_ERROR', // 数据完整性错误
}

// 自定义API错误类
export class ApiError extends Error {
  status: number;
  type: ErrorType;
  data?: any;
  details?: string[];

  constructor(
    message: string,
    status: number,
    type: ErrorType,
    data?: any,
    details?: string[]
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.type = type;
    this.data = data;
    this.details = details;
  }

  // 静态工厂方法 - 创建验证错误
  static createValidationError(message: string, details?: string[]): ApiError {
    return new ApiError(
      message,
      400,
      ErrorType.VALIDATION_ERROR,
      undefined,
      details
    );
  }

  // 静态工厂方法 - 创建认证错误
  static createAuthError(message: string = '认证失败'): ApiError {
    return new ApiError(
      message,
      401,
      ErrorType.AUTH_ERROR
    );
  }

  // 静态工厂方法 - 创建权限错误
  static createPermissionError(message: string = '权限不足'): ApiError {
    return new ApiError(
      message,
      403,
      ErrorType.PERMISSION_ERROR
    );
  }

  // 静态工厂方法 - 创建未找到错误
  static createNotFoundError(message: string = '资源不存在'): ApiError {
    return new ApiError(
      message,
      404,
      ErrorType.NOT_FOUND_ERROR
    );
  }

  // 静态工厂方法 - 创建服务器错误
  static createServerError(message: string = '服务器内部错误'): ApiError {
    return new ApiError(
      message,
      500,
      ErrorType.SERVER_ERROR
    );
  }

  // 静态工厂方法 - 创建网络错误
  static createNetworkError(message: string = '网络请求失败'): ApiError {
    return new ApiError(
      message,
      0,
      ErrorType.NETWORK_ERROR
    );
  }

  // 静态工厂方法 - 创建元数据错误
  static createMetadataError(message: string = '元数据加载或处理失败', data?: any): ApiError {
    return new ApiError(
      message,
      0,
      ErrorType.METADATA_ERROR,
      data
    );
  }

  // 静态工厂方法 - 创建元数据版本错误
  static createMetadataVersionError(message: string = '元数据版本不匹配', expectedVersion?: string, actualVersion?: string): ApiError {
    return new ApiError(
      message,
      409,
      ErrorType.METADATA_VERSION_ERROR,
      { expectedVersion, actualVersion }
    );
  }

  // 静态工厂方法 - 创建业务规则错误
  static createBusinessRuleError(message: string = '业务规则验证失败', details?: string[]): ApiError {
    return new ApiError(
      message,
      400,
      ErrorType.BUSINESS_RULE_ERROR,
      undefined,
      details
    );
  }

  // 静态工厂方法 - 创建数据完整性错误
  static createDataIntegrityError(message: string = '数据完整性约束违反'): ApiError {
    return new ApiError(
      message,
      409,
      ErrorType.DATA_INTEGRITY_ERROR
    );
  }

  // 格式化错误信息为用户友好的消息
  toUserFriendlyMessage(): string {
    switch (this.type) {
      case ErrorType.VALIDATION_ERROR:
        return this.details ? this.details.join('; ') : this.message;
      case ErrorType.AUTH_ERROR:
        return '请先登录再继续操作';
      case ErrorType.PERMISSION_ERROR:
        return '您没有权限执行此操作';
      case ErrorType.NOT_FOUND_ERROR:
        return '请求的资源不存在或已被删除';
      case ErrorType.NETWORK_ERROR:
        return '网络连接异常，请检查网络设置后重试';
      case ErrorType.SERVER_ERROR:
        return '服务器暂时不可用，请稍后重试';
      case ErrorType.METADATA_ERROR:
        return '系统配置加载失败，请刷新页面后重试';
      case ErrorType.METADATA_VERSION_ERROR:
        return '系统配置已更新，请刷新页面以获取最新配置';
      case ErrorType.BUSINESS_RULE_ERROR:
        return this.details ? this.details.join('; ') : this.message;
      case ErrorType.DATA_INTEGRITY_ERROR:
        return '数据一致性校验失败，请检查数据后重试';
      default:
        return this.message;
    }
  }
}

// 错误处理工具类
export class ErrorHandler {
  // 处理API错误
  static handleApiError(error: any): never {
    // 生成错误ID用于跟踪
    const errorId = `err_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    
    // 错误上下文信息
    const errorContext: any = {
      errorId,
      timestamp: new Date().toISOString(),
    };
    
    // 如果已经是ApiError实例，添加额外信息后抛出
    if (error instanceof ApiError) {
      // 添加错误上下文
      error.data = {
        ...error.data,
        errorContext,
        stack: error.stack
      };
      
      // 发布错误事件
      if (GlobalEventBus) {
        GlobalEventBus.getInstance().emit(AppEvents.API_ERROR_THROWN, {
          error,
          errorContext
        });
      }
      
      throw error;
    }

    // 根据错误类型创建适当的ApiError
    let apiError: ApiError;
    
    if (error.response) {
      // 服务器返回错误响应
      const { status, data } = error.response;
      
      // 从响应头提取元数据版本信息
      const metadataVersion = error.response.headers?.['x-metadata-version'];
      if (metadataVersion) {
        errorContext.metadataVersion = metadataVersion;
      }
      
      // 从响应数据中检查是否包含元数据特定错误
      if (data.errorType && data.errorType.includes('METADATA')) {
        if (data.errorType === ErrorType.METADATA_VERSION_ERROR) {
          apiError = ApiError.createMetadataVersionError(
            data.message || '元数据版本不匹配',
            data.expectedVersion,
            data.actualVersion
          );
        } else {
          apiError = ApiError.createMetadataError(
            data.message || '元数据处理失败',
            data
          );
        }
      } 
      // 业务规则错误检查
      else if (data.errorType === ErrorType.BUSINESS_RULE_ERROR) {
        apiError = ApiError.createBusinessRuleError(
          data.message || '业务规则验证失败',
          data.details || data.violations
        );
      }
      // 数据完整性错误
      else if (data.errorType === ErrorType.DATA_INTEGRITY_ERROR || status === 409) {
        apiError = ApiError.createDataIntegrityError(
          data.message || '数据完整性约束违反'
        );
      }
      // 标准HTTP错误
      else {
        switch (status) {
          case 400:
            apiError = ApiError.createValidationError(
              data.message || '请求参数错误',
              data.details || data.errors
            );
            break;
          case 401:
            apiError = ApiError.createAuthError(data.message);
            // 发布认证错误事件，用于登出或重定向到登录页
            if (GlobalEventBus) {
              GlobalEventBus.getInstance().emit(AppEvents.AUTH_ERROR,
                { error: apiError, errorContext }
              );
            }
            break;
          case 403:
            apiError = ApiError.createPermissionError(data.message);
            break;
          case 404:
            apiError = ApiError.createNotFoundError(data.message);
            break;
          case 500:
          case 502:
          case 503:
          case 504:
            apiError = ApiError.createServerError(data.message);
            // 服务器错误可以发送到监控服务
            this.reportServerError(data, errorContext);
            break;
          default:
            apiError = new ApiError(
              data.message || `请求失败 (${status})`,
              status,
              ErrorType.API_ERROR,
              data
            );
        }
      }
    } else if (error.request) {
      // 请求已发送但没有收到响应
      apiError = ApiError.createNetworkError();
      
      // 记录请求详情用于调试
      errorContext.request = {
        url: error.config?.url,
        method: error.config?.method,
        timeout: error.config?.timeout
      };
    } else {
      // 请求配置错误
      apiError = new ApiError(
        error.message || '请求配置错误',
        0,
        ErrorType.CLIENT_ERROR,
        error
      );
    }
    
    // 添加错误上下文和堆栈信息
    apiError.data = {
      ...apiError.data,
      errorContext,
      stack: error.stack
    };
    
    // 发布错误事件
    if (GlobalEventBus) {
      GlobalEventBus.getInstance().emit(AppEvents.API_ERROR_THROWN, {
        error: apiError,
        errorContext
      });
    }
    
    throw apiError;
  }
  
  // 报告服务器错误到监控服务
  private static reportServerError(errorData: any, errorContext: any): void {
    try {
      // 可以集成错误监控服务，如Sentry、New Relic等
      // errorMonitoringService.reportServerError({
      //   errorData,
      //   errorContext,
      //   environment: process.env.NODE_ENV
      // });
      
      // 开发环境下打印详细错误信息
      if (process.env.NODE_ENV === 'development') {
        console.error('[Server Error Report]', {
          errorData,
          errorContext
        });
      }
    } catch (reportError) {
      // 防止报告错误本身出错
      console.error('Failed to report server error:', reportError);
    }
  }

  // 显示错误通知
  static showErrorNotification(error: ApiError, notificationService?: any, options: ErrorNotificationOptions = {}): void {
    const message = error.toUserFriendlyMessage();
    
    // 自定义错误类型处理选项
    const errorTypeOptions = this.getErrorTypeOptions(error.type);
    
    // 合并默认选项、错误类型特定选项和用户提供的选项
    const mergedOptions = {
      ...this.getDefaultNotificationOptions(),
      ...errorTypeOptions,
      ...options
    };
    
    // 通知配置
    const notificationConfig = {
      message: mergedOptions.title,
      description: message,
      type: mergedOptions.type,
      duration: mergedOptions.duration,
      showIcon: mergedOptions.showIcon,
      placement: mergedOptions.placement,
      onClick: mergedOptions.onClick,
      onClose: () => {
        mergedOptions.onClose?.();
        // 发布错误通知关闭事件
        if (GlobalEventBus) {
          GlobalEventBus.getInstance().emit(AppEvents.ERROR_NOTIFICATION_CLOSED, {
            error,
            errorId: error.data?.errorContext?.errorId
          });
        }
      }
    };
    
    // 如果存在重试选项，添加重试按钮
    if (mergedOptions.retryable && mergedOptions.onRetry) {
      notificationConfig['btn'] = [
        {
          text: '重试',
          onPress: () => {
            mergedOptions.onRetry?.();
            // 发布错误通知重试事件
            if (GlobalEventBus) {
              GlobalEventBus.getInstance().emit(AppEvents.ERROR_NOTIFICATION_RETRIED, {
                error,
                errorId: error.data?.errorContext?.errorId
              });
            }
          }
        }
      ];
    }
    
    // 显示通知
    if (notificationService && typeof notificationService.error === 'function') {
      notificationService.error(notificationConfig);
    } else {
      console.error('[API Error]', {
        error,
        message,
        errorContext: error.data?.errorContext
      });
      
      // 在没有通知服务的情况下提供简单的重试选项
      if (mergedOptions.retryable && window.confirm(`${message}\n\n是否重试操作？`)) {
        mergedOptions.onRetry?.();
      }
    }
    
    // 记录错误统计信息
    this.logErrorMetrics(error);
  }
  
  // 获取默认通知选项
  private static getDefaultNotificationOptions(): ErrorNotificationOptions {
    return {
      title: '操作失败',
      type: 'error',
      duration: 5,
      showIcon: true,
      placement: 'topRight',
      retryable: false
    };
  }
  
  // 根据错误类型获取特定选项
  private static getErrorTypeOptions(errorType: ErrorType): Partial<ErrorNotificationOptions> {
    switch (errorType) {
      case ErrorType.NETWORK_ERROR:
        return {
          title: '网络连接错误',
          duration: 8,
          retryable: true
        };
      case ErrorType.AUTH_ERROR:
        return {
          title: '认证失败',
          duration: 10,
          onClick: () => {
            // 通常会重定向到登录页面
            if (GlobalEventBus) {
              GlobalEventBus.getInstance().emit(AppEvents.REDIRECT_TO_LOGIN, {});
            }
          }
        };
      case ErrorType.METADATA_ERROR:
      case ErrorType.METADATA_VERSION_ERROR:
        return {
          title: '系统配置错误',
          duration: 10,
          retryable: true,
          onClick: () => window.location.reload()
        };
      case ErrorType.SERVER_ERROR:
        return {
          title: '服务器错误',
          duration: 8
        };
      default:
        return {};
    }
  }
  
  // 记录错误统计信息
  private static logErrorMetrics(error: ApiError): void {
    try {
      // 可以集成到分析服务中
      // analyticsService.trackError({
      //   errorType: error.type,
      //   errorId: error.data?.errorContext?.errorId,
      //   entityName: error.data?.entityName,
      //   operation: error.data?.operation
      // });
      
      if (process.env.NODE_ENV === 'development') {
        console.log('[Error Metrics]', {
          errorType: error.type,
          errorId: error.data?.errorContext?.errorId,
          timestamp: new Date().toISOString()
        });
      }
    } catch (logError) {
      // 防止日志记录错误影响正常流程
      console.error('Failed to log error metrics:', logError);
    }
  }

  // 全局错误处理中间件（用于React组件错误边界）
  static handleGlobalError(error: Error, errorInfo?: React.ErrorInfo, componentStack?: string): void {
    // 生成错误ID
    const errorId = `global_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    
    // 错误上下文信息
    const errorContext = {
      errorId,
      timestamp: new Date().toISOString(),
      componentStack,
      errorInfo: errorInfo?.componentStack
    };
    
    // 构建全局错误对象
    const globalError = {
      name: error.name,
      message: error.message,
      stack: error.stack,
      context: errorContext
    };
    
    // 打印详细错误信息
    console.error('[Global Error]', globalError);
    
    // 发布全局错误事件
    if (GlobalEventBus) {
      GlobalEventBus.getInstance().emit(AppEvents.GLOBAL_ERROR_THROWN, {
        error,
        errorContext
      });
    }
    
    try {
      // 可以发送到错误监控服务
      // errorTrackingService.report({
      //   error,
      //   context: errorContext,
      //   environment: process.env.NODE_ENV,
      //   tags: ['global', 'unhandled']
      // });
    } catch (reportError) {
      console.error('Failed to report global error:', reportError);
    }
  }
  
  // 尝试恢复操作的实用方法
  static async attemptRecovery(action: () => Promise<any>, maxRetries: number = 3): Promise<any> {
    let lastError: any;
    
    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        // 发布重试尝试事件
        if (GlobalEventBus) {
          GlobalEventBus.getInstance().emit(AppEvents.RECOVERY_ATTEMPT, {
            attempt,
            maxRetries
          });
        }
        
        return await action();
      } catch (error) {
        lastError = error;
        
        // 仅对特定错误类型进行重试
        if (!this.isRetryableError(error)) {
          throw error;
        }
        
        // 指数退避策略
        const delay = Math.pow(2, attempt) * 100;
        await new Promise(resolve => setTimeout(resolve, delay));
      }
    }
    
    // 所有重试都失败
    throw lastError;
  }
  
  // 检查错误是否可重试
  private static isRetryableError(error: any): boolean {
    if (error instanceof ApiError) {
      // 网络错误和服务器错误通常是可重试的
      return [
        ErrorType.NETWORK_ERROR,
        ErrorType.SERVER_ERROR,
        ErrorType.METADATA_ERROR
      ].includes(error.type);
    }
    
    // 原始网络错误也可以重试
    return error.code === 'ECONNABORTED' || 
           error.message?.includes('network') ||
           error.message?.includes('timeout');
  }
}
```

#### 3.8.6 API集成最佳实践

1. **六边形架构应用**
   - 使用端口与适配器模式隔离领域层和外部系统
   - 通过依赖注入实现API适配器的替换和模拟

2. **错误处理策略**
   - 实现统一的错误类型和错误处理机制
   - 区分不同类型的错误，提供不同的用户反馈
   - 实现错误日志记录和监控

3. **请求优化**
   - 实现请求重试机制，提高系统稳定性
   - 使用批量请求减少网络开销
   - 实现请求缓存，减少重复请求

4. **安全性考虑**
   - 实现安全的认证和授权处理
   - 敏感信息加密传输
   - 防CSRF攻击保护

5. **可测试性设计**
   - API适配器易于模拟和测试
   - 实现单元测试和集成测试
   - 支持不同环境的API配置切换

6. **性能优化**
   - 使用请求压缩减少传输数据量
   - 实现响应数据转换和精简
   - 使用HTTP缓存机制减少不必要的请求

## 4. 开发规范和最佳实践

### 4.1 代码规范

#### 4.1.1 命名规范

- **文件命名**：
  - 组件文件：使用PascalCase，如 `UserProfile.tsx`
  - 工具函数文件：使用kebab-case，如 `date-utils.ts`
  - 样式文件：使用kebab-case，如 `user-profile.module.css` 或与组件同名
  - 类型定义文件：使用kebab-case并添加 `.types` 后缀，如 `user.types.ts`

- **代码元素命名**：
  - 组件命名：使用PascalCase，如 `UserProfile`
  - 函数和变量命名：使用camelCase，如 `fetchUserData`
  - 常量命名：使用UPPER_SNAKE_CASE，如 `MAX_RESULTS`
  - 接口命名：使用PascalCase并以 `I` 前缀，如 `IUserProfile`
  - 类型别名命名：使用PascalCase，如 `UserRole`
  - 枚举命名：使用PascalCase，枚举值使用UPPER_SNAKE_CASE

#### 4.1.2 TypeScript规范

- **严格模式配置**：
  ```json
  {
    "strict": true,
    "noImplicitAny": true,
    "strictNullChecks": true,
    "strictFunctionTypes": true,
    "strictBindCallApply": true,
    "strictPropertyInitialization": true,
    "noImplicitThis": true,
    "useUnknownInCatchVariables": true,
    "alwaysStrict": true
  }
  ```

- **类型使用原则**：
  - 禁止使用 `any` 类型（除非特殊情况并添加注释说明）
  - 使用 `unknown` 替代 `any` 作为未知类型
  - 为所有函数参数和返回值添加类型注解
  - 使用联合类型和交叉类型表示复杂数据结构
  - 使用类型断言 `as` 时需谨慎，确保类型安全

- **接口设计**：
  - 定义清晰的接口表示实体和数据结构
  - 接口应遵循单一职责原则
  - 使用 `readonly` 关键字标记不可变属性
  - 为可选属性添加 `?` 后缀

#### 4.1.3 组件设计规范

- **组件结构**：
  - 使用函数式组件配合Hooks开发
  - 组件应遵循单一职责原则
  - 复杂组件应拆分为更小的子组件
  - 使用TypeScript接口定义props和state类型

- **Hooks使用**：
  - 自定义Hook命名应以 `use` 开头，如 `useLocalStorage`
  - 避免在Hook内部使用不稳定的引用
  - 合理设置依赖项以避免无限循环
  - 遵循React Hook规则（只在顶层调用Hooks）

- **样式规范**：
  - 优先使用CSS Modules或Styled Components
  - 遵循BEM或类似的命名约定
  - 使用Tailwind CSS时遵循原子化设计原则
  - 避免使用全局样式污染

#### 4.1.4 注释规范

- **文件头部注释**：包含文件目的、作者和创建日期
- **组件注释**：使用JSDoc格式描述组件功能、参数和返回值
- **复杂逻辑注释**：为复杂算法和业务逻辑添加详细注释
- **类型定义注释**：为接口和类型定义添加描述

#### 4.1.5 版本控制规范

- **Git提交规范**：
  - 使用语义化提交信息：`<type>: <description>`
  - 常见类型：feat(新功能)、fix(修复bug)、docs(文档更新)、style(代码样式)、refactor(代码重构)、test(测试)、chore(构建/工具)
  - 提交信息应简洁明了，描述变更内容

### 4.2 性能优化

#### 4.2.1 组件优化策略

- **渲染优化**：
  - 使用 `React.memo` 包装纯函数组件
  - 使用 `useMemo` 缓存计算结果
  - 使用 `useCallback` 缓存函数引用
  - 使用 `React.lazy` 和 `Suspense` 实现代码分割和懒加载

- **列表优化**：
  - 对大型列表使用虚拟滚动（如 `react-window` 或 `react-virtualized`）
  - 为列表项提供稳定的 `key` 属性
  - 避免在列表渲染中执行昂贵的计算

- **状态管理优化**：
  - 避免不必要的状态提升
  - 合理使用Context，避免过度渲染
  - 使用Zustand的选择器功能优化状态读取
  - 状态更新应避免深层嵌套对象的修改

#### 4.2.2 网络性能优化

- **请求优化**：
  - 使用请求合并和防抖处理减少重复请求
  - 实现请求缓存策略，避免重复数据获取
  - 使用 `React Query` 管理服务器状态和缓存
  - 合理设置请求超时和重试策略

- **数据传输优化**：
  - 使用轻量级的数据格式（如Protocol Buffers）
  - 仅传输必要的数据字段
  - 使用HTTP/2或HTTP/3减少连接开销
  - 实现数据压缩传输

#### 4.2.3 资源优化

- **静态资源优化**：
  - 图片使用WebP格式并提供响应式尺寸
  - 实现图片懒加载
  - 合理使用CDN分发静态资源
  - 配置适当的缓存策略

- **构建优化**：
  - 启用代码分割和树摇（Tree Shaking）
  - 优化第三方依赖，使用按需加载
  - 配置构建缓存加速开发流程
  - 分析并优化打包后的代码体积

#### 4.2.4 性能监控

- **性能指标监控**：
  - 跟踪Core Web Vitals指标（LCP、FID、CLS）
  - 使用 `Performance` API 监控关键操作性能
  - 集成性能监控工具（如 Lighthouse、Sentry）
  - 设置性能预算并进行持续监控

- **性能分析**：
  - 使用React Developer Tools分析组件渲染
  - 使用Chrome DevTools进行性能分析
  - 识别并优化性能瓶颈
  - 定期进行性能审查

### 4.3 测试策略

#### 4.3.1 测试类型与范围

- **单元测试**：
  - 组件测试：渲染、props传递、事件处理
  - Hook测试：状态管理、副作用处理
  - 工具函数测试：输入输出验证、边界条件测试
  - 业务逻辑测试：核心算法和规则验证

- **集成测试**：
  - 组件集成测试：多个组件协同工作
  - API集成测试：与后端服务的交互
  - 页面级集成测试：完整用户流程验证

- **端到端测试**：
  - 关键业务流程测试
  - 用户交互模拟测试
  - 跨浏览器兼容性测试

#### 4.3.2 测试工具链

- **测试框架**：Jest
- **React测试库**：React Testing Library
- **端到端测试**：Cypress
- **测试覆盖率工具**：Istanbul/nyc
- **API模拟**：MSW (Mock Service Worker)

#### 4.3.3 测试最佳实践

- **测试用例设计**：
  - 每个测试只测试一个功能点
  - 使用描述性的测试名称
  - 测试应包含正常流程、边界条件和异常情况
  - 避免测试实现细节，关注行为和结果

- **测试代码组织**：
  - 测试文件与源代码文件保持相同的目录结构
  - 测试文件命名为 `<filename>.test.tsx` 或 `<filename>.spec.tsx`
  - 使用测试描述分组相关测试
  - 合理使用测试前置（beforeEach）和后置（afterEach）钩子

- **测试模拟**：
  - 模拟外部依赖（如API调用、浏览器API）
  - 使用MSW模拟网络请求
  - 模拟全局状态管理
  - 确保模拟行为与实际行为一致

#### 4.3.4 测试覆盖率目标

- **覆盖率标准**：
  - 核心业务组件：≥85%
  - 工具函数：≥90%
  - 整体项目：≥80%

- **覆盖率质量**：
  - 不仅关注行覆盖率，更要关注分支覆盖率和函数覆盖率
  - 避免为了提高覆盖率而编写无意义的测试
  - 确保测试能真正捕获潜在问题

#### 4.3.5 持续集成与测试

- **CI/CD配置**：
  - 自动化运行测试作为CI流程的一部分
  - 设置测试覆盖率阈值，低于阈值时构建失败
  - 集成代码质量检查工具（ESLint, Prettier）
  - 实现自动化端到端测试

- **测试环境管理**：
  - 为测试提供独立的测试环境
  - 使用测试数据工厂生成一致的测试数据
  - 确保测试环境与生产环境配置一致
  - 实现测试数据的自动清理

### 4.4 安全性最佳实践

#### 4.4.1 前端安全原则

- **数据验证**：
  - 对所有用户输入进行验证和清洗
  - 实现客户端和服务端双重验证
  - 避免使用 `eval()` 和 `innerHTML` 等危险函数
  - 对URL参数进行严格检查和转义

- **认证与授权**：
  - 实现安全的身份验证流程
  - 使用JWT进行无状态认证，设置合理的过期时间
  - 存储敏感信息时使用安全的存储方式
  - 实现细粒度的权限控制

#### 4.4.2 常见安全漏洞防范

- **XSS防护**：
  - 使用React的自动转义功能
  - 对动态内容进行适当的转义
  - 使用CSP（内容安全策略）限制脚本执行
  - 避免内联事件处理器和脚本

- **CSRF防护**：
  - 使用CSRF令牌验证请求
  - 实现SameSite Cookie属性
  - 验证请求来源（Origin/Referer头）
  - 避免使用GET请求进行敏感操作

- **其他安全措施**：
  - 实现安全的错误处理，避免信息泄露
  - 敏感数据传输使用HTTPS
  - 定期更新依赖库，修复已知漏洞
  - 实施安全日志记录和监控

### 4.5 可访问性（Accessibility）最佳实践

#### 4.5.1 可访问性原则

- **WCAG合规**：遵循WCAG 2.1 AA级标准
- **键盘导航**：确保所有功能可通过键盘访问
- **屏幕阅读器支持**：使用适当的ARIA属性和语义化HTML
- **颜色对比度**：确保文本与背景的对比度符合标准

#### 4.5.2 实现策略

- **语义化HTML**：
  - 使用正确的HTML元素（如 `<button>`、`<input>`、`<label>`）
  - 避免使用非语义化的 `<div>` 和 `<span>` 作为可交互元素
  - 实现适当的标题层级（h1-h6）

- **ARIA属性**：
  - 使用ARIA角色和属性增强可访问性
  - 为自定义组件添加适当的键盘交互
  - 确保动态内容更新时通知屏幕阅读器

- **测试与验证**：
  - 使用自动化工具（如axe-core）进行可访问性测试
  - 进行真实用户测试（如屏幕阅读器测试）
  - 定期进行可访问性审计

### 4.6 国际化与本地化

#### 4.6.1 国际化策略

- **使用i18next框架**：
  - 集中管理翻译资源
  - 支持多语言切换
  - 处理复数、日期、货币等本地化需求

- **代码实现**：
  - 所有用户可见文本使用翻译键
  - 避免硬编码字符串
  - 支持从右到左（RTL）的语言布局

#### 4.6.2 本地化考虑

- **区域设置处理**：
  - 根据用户区域设置格式化日期和时间
  - 支持不同区域的数字和货币格式
  - 考虑不同文化的排版和设计需求

- **资源管理**：
  - 实现动态加载翻译资源
  - 支持翻译资源的增量更新
  - 提供翻译管理工作流程

### 4.7 开发工作流最佳实践

#### 4.7.1 开发环境配置

- **统一开发环境**：
  - 使用Docker提供一致的开发环境
  - 配置开发环境脚本和工具
  - 提供详细的环境设置文档

- **开发工具链**：
  - 配置EditorConfig确保编辑器设置一致
  - 使用Prettier自动格式化代码
  - 配置ESLint规则和插件
  - 使用Husky实现Git hooks（提交前检查等）

#### 4.7.2 代码审查流程

- **审查标准**：
  - 代码质量和风格一致性
  - 功能正确性和完整性
  - 安全性和性能考量
  - 可维护性和可扩展性

- **审查流程**：
  - 使用Pull Request进行代码提交和审查
  - 至少需要一位团队成员审查
  - 自动化检查（CI）通过后才能合并
  - 记录和跟踪审查反馈

#### 4.7.3 文档与知识管理

- **文档类型**：
  - 架构设计文档
  - API文档
  - 组件文档（使用Storybook）
  - 开发指南和贡献指南

- **文档更新策略**：
  - 代码变更时同步更新文档
  - 使用自动化工具生成API文档
  - 定期审查和更新文档
  - 建立文档模板和标准

## 5. 部署和集成方案

### 5.1 微前端集成配置

#### 5.1.1 微应用配置与注册

```typescript
// src/micro-frontend/index.ts
import { MicroAppConfig } from './types';
import { registerMicroApp, startMicroApp, initMicroFrontend } from './registry';
import { useMicroApp, useMicroAppRoute, useMicroAppContext } from './hooks';
import { MicroAppRouter, MicroAppContainer } from './components';
import { globalEventBus } from './event-bus';

// 导出核心API
export {
  MicroAppConfig,
  registerMicroApp,
  startMicroApp,
  initMicroFrontend,
  useMicroApp,
  useMicroAppRoute,
  useMicroAppContext,
  MicroAppRouter,
  MicroAppContainer,
  globalEventBus
};
```

#### 5.1.2 微前端路由集成（基于无界框架）

```typescript
// src/micro-frontend/components/MicroAppRouter.tsx
import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { useLocation, useNavigate, useParams, matchPath } from 'react-router-dom';
import { Spin, Alert, Empty, Button } from 'antd';
import { useMicroAppStore } from '@/stores/microAppStore';
import { globalEventBus, AppEvents } from '@/micro-frontend/event-bus';
import { buildWujieSandboxConfig, applySecurityPolicy } from '@/micro-frontend/sandbox';

// 微前端沙箱配置和安全策略
// src/micro-frontend/sandbox.ts

/**
 * 沙箱类型枚举
 */
export enum SandboxType {
  SNAPSHOT = 'snapshot',
  PROXY = 'proxy',
  LEGACY = 'legacy'
}

/**
 * 样式隔离级别枚举
 */
export enum StyleIsolationLevel {
  NONE = 'none',
  STRICT = 'strict',
  EXPERIMENTAL = 'experimental',
  SCOPED = 'scoped'
}

/**
 * 沙箱配置接口
 */
export interface SandboxConfig {
  // JS沙箱开关
  jsSandbox: boolean;
  // 样式隔离级别
  styleIsolation: StyleIsolationLevel;
  // 沙箱类型
  sandboxType: SandboxType;
  // 是否启用快照沙箱
  snapshotSandbox?: boolean;
  // 是否启用代理沙箱
  proxySandbox?: boolean;
  // 是否允许微应用操作父文档
  allowDocumentManipulation?: boolean;
  // 是否允许微应用访问全局对象
  allowGlobalAccess?: boolean;
  // 允许访问的全局对象白名单
  globalObjectWhitelist?: string[];
  // 是否禁用外部链接
  disableExternalLinks?: boolean;
  // 是否拦截window.open
  interceptWindowOpen?: boolean;
  // 是否拦截fetch请求
  interceptFetch?: boolean;
  // 是否拦截XMLHttpRequest
  interceptXhr?: boolean;
}

/**
 * 安全策略接口
 */
export interface SecurityPolicy {
  // 内容安全策略
  contentSecurityPolicy?: string;
  // 是否启用CSP
  enableCSP?: boolean;
  // 是否禁用eval
  disableEval?: boolean;
  // 是否禁用Function构造函数
  disableFunctionConstructor?: boolean;
  // 允许的域名白名单（支持通配符，如 *.example.com）
  allowedDomains?: string[];
  // 是否限制本地存储访问
  restrictLocalStorage?: boolean;
  // 本地存储前缀
  localStoragePrefix?: string;
  // 是否限制Cookie访问
  restrictCookies?: boolean;
  // Cookie前缀
  cookiePrefix?: string;
  // 是否禁用iframe
  disableIframes?: boolean;
  
  // ========== 新增的安全策略选项 ==========
  // 允许的HTTP方法列表
  allowedMethods?: string[];
  // 是否启用CSRF保护
  enableCSRFProtection?: boolean;
  // fetch请求超时时间（毫秒）
  fetchTimeout?: number;
  // 是否启用自动重试
  autoRetry?: boolean;
  // 重试次数
  retryCount?: number;
  // 重试间隔（毫秒）
  retryInterval?: number;
  
  // ========== 缓存策略 ==========
  // 是否启用响应缓存
  enableCache?: boolean;
  // 缓存TTL（毫秒）
  cacheTTL?: number;
  // 最大缓存条目数
  maxCacheEntries?: number;
}

/**
 * 默认沙箱配置
 */
export const defaultSandboxConfig: SandboxConfig = {
  jsSandbox: true,
  styleIsolation: StyleIsolationLevel.STRICT,
  sandboxType: SandboxType.PROXY,
  snapshotSandbox: false,
  proxySandbox: true,
  allowDocumentManipulation: false,
  allowGlobalAccess: false,
  globalObjectWhitelist: ['console', 'Math', 'JSON', 'Date'],
  disableExternalLinks: true,
  interceptWindowOpen: true,
  interceptFetch: true,
  interceptXhr: true
};

/**
 * 默认安全策略
 */
export const defaultSecurityPolicy: SecurityPolicy = {
  contentSecurityPolicy: "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self';",
  enableCSP: true,
  disableEval: true,
  disableFunctionConstructor: true,
  allowedDomains: [],
  restrictLocalStorage: true,
  localStoragePrefix: 'micro_app_',
  restrictCookies: true,
  cookiePrefix: 'micro_app_',
  disableIframes: true
};

/**
 * 根据环境获取适合的沙箱配置
 * @param appName 微应用名称
 * @param env 环境
 * @returns 沙箱配置
 */
export function getOptimalSandboxConfig(appName: string, env: string = 'production'): SandboxConfig {
  const config = { ...defaultSandboxConfig };
  
  // 开发环境下放宽一些限制，便于调试
  if (env === 'development') {
    config.allowGlobalAccess = true;
    config.styleIsolation = StyleIsolationLevel.EXPERIMENTAL;
    config.interceptFetch = false;
    config.interceptXhr = false;
  }
  
  // 应用特定配置
  switch (appName) {
    case 'vue-dashboard':
      // Vue应用可能需要特殊处理
      config.styleIsolation = StyleIsolationLevel.EXPERIMENTAL;
      break;
    case 'angular-settings':
      // Angular应用可能需要更多全局访问权限
      config.globalObjectWhitelist = [...config.globalObjectWhitelist, 'Zone', '__zone_symbol__'];
      break;
    default:
      break;
  }
  
  return config;
}

/**
 * 构建无界框架的沙箱配置
 * @param appName 微应用名称
 * @param customConfig 自定义配置
 * @returns 无界框架兼容的沙箱配置
 */
export function buildWujieSandboxConfig(appName: string, customConfig?: Partial<SandboxConfig>) {
  const config = { ...getOptimalSandboxConfig(appName), ...customConfig };
  
  return {
    // 无界框架的jsSandbox配置
    jsSandbox: config.jsSandbox,
    // 无界框架的严格样式隔离
    strictStyleIsolation: config.styleIsolation === StyleIsolationLevel.STRICT,
    // 无界框架的实验性样式隔离
    experimentalStyleIsolation: config.styleIsolation === StyleIsolationLevel.EXPERIMENTAL,
    // 无界框架的快照沙箱
    snapshotSandbox: config.sandboxType === SandboxType.SNAPSHOT,
    // 无界框架的代理沙箱
    proxySandbox: config.sandboxType === SandboxType.PROXY,
  };
}

/**
 * 应用安全策略
 * @param appName 微应用名称
 * @param policy 安全策略
 */
export function applySecurityPolicy(appName: string, policy: SecurityPolicy = defaultSecurityPolicy): void {
  if (typeof document !== 'undefined') {
    // 应用CSP
    if (policy.enableCSP && policy.contentSecurityPolicy) {
      const meta = document.createElement('meta');
      meta.httpEquiv = 'Content-Security-Policy';
      meta.content = policy.contentSecurityPolicy;
      document.head.appendChild(meta);
    }
    
    // 其他安全策略可以在这里实现
    if (policy.disableEval) {
      // 可以通过覆盖eval函数实现
      // 注意：这需要在微应用加载前执行
    }
  }
}

/**
 * 响应缓存管理器类
 */
export class ResponseCacheManager {
  private cache: Map<string, { data: Response; timestamp: number }>;
  private ttl: number;
  private maxSize: number;
  private cleanupInterval: NodeJS.Timeout | null = null;
  
  constructor(ttl: number, maxSize: number) {
    this.cache = new Map();
    this.ttl = ttl;
    this.maxSize = maxSize;
    this.startCleanupInterval();
  }
  
  // 缓存清理定时器
  private startCleanupInterval() {
    if (typeof window !== 'undefined') {
      this.cleanupInterval = setInterval(() => {
        this.cleanupExpiredEntries();
      }, this.ttl / 2); // 每TTL的一半时间执行一次清理
    }
  }
  
  // 清理过期条目
  private cleanupExpiredEntries() {
    const now = Date.now();
    for (const [key, { timestamp }] of this.cache.entries()) {
      if (now - timestamp > this.ttl) {
        this.cache.delete(key);
      }
    }
  }
  
  // 添加缓存
  public set(key: string, response: Response) {
    // 检查缓存大小
    if (this.cache.size >= this.maxSize) {
      this.evictOldestEntry();
    }
    
    // 存储响应克隆
    this.cache.set(key, {
      data: response.clone(),
      timestamp: Date.now()
    });
  }
  
  // 获取缓存
  public get(key: string): Response | null {
    const cached = this.cache.get(key);
    if (!cached || Date.now() - cached.timestamp > this.ttl) {
      if (cached) {
        this.cache.delete(key);
      }
      return null;
    }
    return cached.data.clone();
  }
  
  // 删除最旧的条目
  private evictOldestEntry() {
    let oldestKey: string | null = null;
    let oldestTimestamp = Date.now();
    
    for (const [key, { timestamp }] of this.cache.entries()) {
      if (timestamp < oldestTimestamp) {
        oldestKey = key;
        oldestTimestamp = timestamp;
      }
    }
    
    if (oldestKey) {
      this.cache.delete(oldestKey);
    }
  }
  
  // 清理资源
  public dispose() {
    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
      this.cleanupInterval = null;
    }
    this.cache.clear();
  }
}

/**
 * 确定错误是否可重试的函数
 */
function isRetryableError(error: Error): boolean {
  // 网络错误、超时错误和特定状态码的服务器错误通常是可重试的
  return error.name === 'AbortError' || 
         error.message.includes('Network') || 
         error.message.includes('500') || 
         error.message.includes('502') || 
         error.message.includes('503');
}

/**
 * 创建安全的fetch拦截器
 * @param appName 微应用名称
 * @param policy 安全策略
 * @returns fetch拦截器函数
 */
export function createSecureFetchInterceptor(appName: string, policy: SecurityPolicy = defaultSecurityPolicy) {
  // 初始化缓存管理器
  let cacheManager: ResponseCacheManager | null = null;
  if (policy.enableCache) {
    cacheManager = new ResponseCacheManager(
      policy.cacheTTL || 30000, // 默认30秒缓存
      policy.maxCacheEntries || 100 // 默认最大缓存100条
    );
  }
  
  // 生成缓存键
  const getCacheKey = (url: string, options: RequestInit): string => {
    // 只缓存GET请求
    if (options.method && options.method.toUpperCase() !== 'GET') return '';
    
    // 序列化请求头，用于生成缓存键
    const headersStr = options.headers ? JSON.stringify(Object.fromEntries(new Headers(options.headers))) : '';
    return `${url}|${headersStr}`;
  };
  
  // 带超时控制的fetch函数
  const fetchWithTimeout = async (url: string, options: RequestInit, headers: Headers) => {
    const timeout = policy.fetchTimeout || 30000; // 默认30秒
    
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);
    
    try {
      const response = await fetch(url, {
        ...options,
        headers,
        signal: controller.signal
      });
      
      clearTimeout(timeoutId);
      
      // 检查响应状态
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}, url: ${url}`);
      }
      
      return response;
    } catch (error) {
      clearTimeout(timeoutId);
      
      if (error.name === 'AbortError') {
        throw new Error(`Request timeout for ${url} (${timeout}ms)`);
      }
      throw error;
    }
  };
  
  // 带指数退避的重试函数
  const fetchWithRetry = async (
    url: string, 
    options: RequestInit, 
    headers: Headers,
    attempt = 0
  ): Promise<Response> => {
    try {
      return await fetchWithTimeout(url, options, headers);
    } catch (error) {
      const maxRetries = policy.retryCount || 3;
      
      if (attempt < maxRetries && 
          policy.autoRetry && 
          isRetryableError(error as Error)) {
        // 指数退避算法，添加随机抖动避免雪崩效应
        const baseDelay = policy.retryInterval || 1000;
        const delay = baseDelay * Math.pow(2, attempt) * (0.5 + Math.random());
        
        console.log(`[安全拦截] 微应用 ${appName} 第${attempt + 1}次重试请求: ${url}，延迟${Math.round(delay)}ms`);
        await new Promise(resolve => setTimeout(resolve, delay));
        
        return fetchWithRetry(url, options, headers, attempt + 1);
      }
      
      throw error;
    }
  };
  
  // 返回拦截器函数
  return async (url: string, options: RequestInit = {}) => {
    // 参数验证
    if (!url || typeof url !== 'string') {
      throw new Error(`Invalid URL for micro-app ${appName}`);
    }
    
    // 生成缓存键并尝试获取缓存
    const cacheKey = getCacheKey(url, options);
    if (cacheKey && cacheManager) {
      const cachedResponse = cacheManager.get(cacheKey);
      if (cachedResponse) {
        console.log(`微应用 ${appName} 从缓存获取响应: ${url}`);
        return cachedResponse;
      }
    }
    
    // 检查是否是允许的域名
    try {
      const urlObj = new URL(url, window.location.origin);
      const domain = urlObj.hostname;
      
      // 检查域名白名单
      if (policy.allowedDomains && policy.allowedDomains.length > 0) {
        // 支持通配符域名检查
        const isAllowed = policy.allowedDomains.some(allowedDomain => {
          // 处理通配符域名，如 *.example.com
          if (allowedDomain.startsWith('*.')) {
            const baseDomain = allowedDomain.slice(2);
            return domain === baseDomain || domain.endsWith(`.${baseDomain}`);
          }
          return domain === allowedDomain;
        });
        
        if (!isAllowed) {
          console.warn(`微应用 ${appName} 尝试访问未授权域名: ${domain}`);
          throw new Error(`Domain ${domain} is not allowed for micro-app ${appName}`);
        }
      }
      
      // 检查是否允许的HTTP方法
      const method = (options.method || 'GET').toUpperCase();
      if (policy.allowedMethods && !policy.allowedMethods.includes(method)) {
        throw new Error(`HTTP method ${method} is not allowed`);
      }
      
      // 注入应用标识头和安全相关头
      const headers = new Headers(options.headers);
      headers.set('X-Micro-App-Name', appName);
      headers.set('X-Request-From', 'micro-app');
      headers.set('X-Request-Timestamp', Date.now().toString());
      
      // 添加防CSRF头（如果需要）
      if (policy.enableCSRFProtection && method !== 'GET') {
        // 获取CSRF令牌的更安全方式
        const csrfToken = typeof window !== 'undefined' 
          ? (window as any).__MICRO_APP_CSRF_TOKEN__ 
          : null;
        if (csrfToken) {
          headers.set('X-CSRF-Token', csrfToken);
        }
      }
      
      // 执行带重试的fetch
      const response = await fetchWithRetry(url, options, headers);
      
      // 缓存成功的GET响应
      if (cacheKey && response.ok && cacheManager) {
        cacheManager.set(cacheKey, response);
      }
      
      return response;
      
    } catch (error) {
      // 增强的错误处理
      const errorMessage = error instanceof Error ? error.message : 'Unknown fetch error';
      console.error(`[安全拦截] 微应用 ${appName} fetch 请求失败 (${url}):`, errorMessage);
      
      // 触发全局错误事件
      if (typeof window !== 'undefined' && window.dispatchEvent) {
        window.dispatchEvent(
          new CustomEvent('micro-app:fetch-error', {
            detail: { appName, url, error: errorMessage }
          })
        );
      }
      
      throw error;
    }
  };
}

/**
 * 微应用容器组件增强版 - 使用无界框架的高级配置
 * 增强性能监控、错误处理和资源管理
 */
export const EnhancedMicroAppContainer: React.FC<{
  appName: string;
  className?: string;
  customSandboxConfig?: Partial<SandboxConfig>;
  customSecurityPolicy?: Partial<SecurityPolicy>;
  onLoadStart?: () => void;
  onLoadComplete?: (time: number) => void;
  onMountComplete?: (time: number) => void;
  onError?: (error: Error) => void;
  fallback?: React.ReactNode;
}> = ({ 
  appName, 
  className,
  customSandboxConfig,
  customSecurityPolicy,
  onLoadStart,
  onLoadComplete,
  onMountComplete,
  onError,
  fallback
}) => {
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [performance, setPerformance] = useState({
    startTime: Date.now(),
    timings: {
      load: 0,
      mount: 0,
      render: 0
    },
    resources: {
      scripts: 0,
      stylesheets: 0,
      images: 0,
      xhrRequests: 0
    }
  });
  
  // 用于存储资源引用
  const resourceRefs = useRef<{[key: string]: any}>({});
  // 用于取消之前的定时器等
  const cleanupRef = useRef<Array<() => void>>([]);
  
  const appConfig = getMicroAppConfig(appName);
  const mergedPolicy = { ...defaultSecurityPolicy, ...customSecurityPolicy };
  
  // 构建沙箱配置
  const sandboxConfig = buildWujieSandboxConfig(appName, {
    ...appConfig?.sandbox,
    ...customSandboxConfig
  });
  
  // 创建安全fetch拦截器
  const secureFetch = createSecureFetchInterceptor(appName, mergedPolicy);
  
  // 资源清理函数
  const cleanupResources = () => {
    // 清理定时器和事件监听器
    cleanupRef.current.forEach(cleanup => cleanup());
    cleanupRef.current = [];
    
    // 清理其他引用
    Object.keys(resourceRefs.current).forEach(key => {
      resourceRefs.current[key] = null;
    });
    resourceRefs.current = {};
  };
  
  useEffect(() => {
    // 应用安全策略
    applySecurityPolicy(appName, mergedPolicy);
    
    // 记录开始时间
    const startTime = Date.now();
    setPerformance(prev => ({ ...prev, startTime }));
    
    // 设置超时处理
    const timeoutId = setTimeout(() => {
      if (status === 'loading') {
        const timeoutError = new Error(`微应用 ${appName} 加载超时`);
        handleError(timeoutError);
      }
    }, 30000); // 30秒超时
    
    cleanupRef.current.push(() => clearTimeout(timeoutId));
    
    // 监听页面可见性变化
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'hidden' && window.$wujie && appConfig) {
        // 页面隐藏时可以暂停某些操作
        console.log(`微应用 ${appName} 页面隐藏，暂停部分操作`);
      }
    };
    
    document.addEventListener('visibilitychange', handleVisibilityChange);
    cleanupRef.current.push(() => 
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    );
    
    // 监听内存使用
    if (typeof performance !== 'undefined' && performance.memory) {
      const memoryCheckInterval = setInterval(() => {
        const memoryUsage = performance.memory.usedJSHeapSize / 1024 / 1024; // MB
        if (memoryUsage > 500) { // 超过500MB时发出警告
          console.warn(`微应用 ${appName} 内存使用较高: ${memoryUsage.toFixed(2)}MB`);
          globalEventBus?.emit(AppEvents.MICRO_APP_PERFORMANCE, {
            type: 'memory',
            appName,
            usage: memoryUsage
          });
        }
      }, 60000); // 每分钟检查一次
      
      cleanupRef.current.push(() => clearInterval(memoryCheckInterval));
    }
    
    return () => {
      // 组件卸载时清理资源
      cleanupResources();
      
      // 记录性能报告
      const endTime = Date.now();
      const totalTime = endTime - performance.startTime;
      const performanceReport = {
        ...performance,
        totalTime,
        endTime,
        success: status === 'ready'
      };
      
      console.log(`微应用 ${appName} 性能报告:`, performanceReport);
      
      // 发送性能事件
      globalEventBus?.emit(AppEvents.MICRO_APP_PERFORMANCE, {
        type: 'lifecycle',
        appName,
        timings: performance.timings,
        totalTime,
        status
      });
    };
  }, [appName, mergedPolicy, status]);
  
  const handleLoad = () => {
    const loadTime = Date.now() - performance.startTime;
    setPerformance(prev => ({
      ...prev,
      timings: { ...prev.timings, load: loadTime }
    }));
    setStatus('ready');
    
    // 通知完成
    onLoadComplete?.(loadTime);
    
    // 发送事件
    globalEventBus?.emit(AppEvents.MICRO_APP_LOADED, {
      appName,
      loadTime,
      timestamp: Date.now()
    });
    
    console.log(`微应用 ${appName} 加载完成，耗时: ${loadTime}ms`);
  };
  
  const handleMount = () => {
    const mountTime = Date.now() - performance.startTime;
    setPerformance(prev => ({
      ...prev,
      timings: { ...prev.timings, mount: mountTime }
    }));
    
    // 通知完成
    onMountComplete?.(mountTime);
    
    // 发送事件
    globalEventBus?.emit(AppEvents.MICRO_APP_MOUNTED, {
      appName,
      mountTime,
      timestamp: Date.now()
    });
    
    console.log(`微应用 ${appName} 挂载完成，耗时: ${mountTime}ms`);
  };
  
  const handleError = (err: Error) => {
    const errorMessage = err.message;
    setError(errorMessage);
    setStatus('error');
    
    // 通知错误
    onError?.(err);
    
    // 发送错误事件
    globalEventBus?.emit(AppEvents.MICRO_APP_ERROR, {
      appName,
      error: errorMessage,
      timestamp: Date.now(),
      stack: err.stack
    });
    
    // 记录错误到性能数据
    setPerformance(prev => ({
      ...prev,
      error: errorMessage
    }));
    
    console.error(`微应用 ${appName} 加载错误:`, err);
  };
  
  const handleBeforeLoad = () => {
    console.log(`开始加载微应用: ${appName}`);
    onLoadStart?.();
    
    // 发送开始加载事件
    globalEventBus?.emit(AppEvents.MICRO_APP_LOAD_START, {
      appName,
      timestamp: Date.now()
    });
  };
  
  const handleUnmount = () => {
    console.log(`微应用 ${appName} 卸载`);
    
    // 发送卸载事件
    globalEventBus?.emit(AppEvents.MICRO_APP_UNMOUNTED, {
      appName,
      timestamp: Date.now()
    });
    
    // 清理资源
    cleanupResources();
  };
  
  // 错误处理和配置检查
  if (!appConfig) {
    const configError = `未找到微应用 ${appName} 的配置`;
    console.error(configError);
    return (
      <div className={`micro-app-error ${className}`}>
        {fallback || (
          <Alert
            message="微应用配置错误"
            description={configError}
            type="error"
            showIcon
            action={
              <Button 
                size="small" 
                onClick={() => {
                  // 重新尝试获取配置
                  window.location.reload();
                }}
              >
                重试
              </Button>
            }
          />
        )}
      </div>
    );
  }
  
  if (error) {
    return (
      <div className={`micro-app-error ${className}`}>
        {fallback || (
          <Alert
            message="微应用加载失败"
            description={error}
            type="error"
            showIcon
            action={
              <Button 
                size="small" 
                onClick={() => {
                  // 重置状态并重试
                  setError(null);
                  setStatus('loading');
                  setPerformance(prev => ({ 
                    ...prev, 
                    startTime: Date.now() 
                  }));
                  // 触发重新渲染
                  window.location.reload();
                }}
              >
                重试
              </Button>
            }
          />
        )}
      </div>
    );
  }
  
  return (
    <div className={`enhanced-micro-app-container ${className}`}>
      <WujieReact
        width="100%"
        height="100%"
        name={appConfig.name}
        url={appConfig.url}
        // 无界框架特性
        sync={appConfig.sync ?? true}
        alive={appConfig.alive ?? true}
        singleton={appConfig.singleton ?? false}
        // 沙箱配置
        {...sandboxConfig}
        // 安全增强
        fetch={secureFetch}
        // 生命周期钩子
        beforeLoad={handleBeforeLoad}
        onLoad={handleLoad}
        onMount={handleMount}
        onError={handleError}
        onUnmount={handleUnmount}
        // 传递给微应用的props
        props={{
          ...appConfig.props,
          // 自动注入应用信息
          appInfo: {
            name: appName,
            version: appConfig.version,
            basePath: appConfig.activeRule as string || `/${appName}`
          },
          // 注入通信工具
          eventBus: globalEventBus || eventBus,
          messenger: microAppMessenger,
          // 注入性能监控接口
          performanceAPI: {
            reportMetric: (type: string, data: any) => {
              globalEventBus?.emit(AppEvents.MICRO_APP_PERFORMANCE, {
                type,
                appName,
                data,
                timestamp: Date.now()
              });
            }
          }
        }}
      />
    </div>
  );
}

/**
 * 微前端路由组件增强版
 * 支持高级路由匹配、智能预加载和性能监控
 */
export const EnhancedMicroAppRouter: React.FC<{
  routes: MicroAppRoute[];
  fallback?: React.ReactNode;
  preloadCount?: number;
  debounceDelay?: number;
  onRouteMatch?: (route: MicroAppRoute | undefined) => void;
  onPreloadStart?: (apps: string[]) => void;
  onPreloadComplete?: (success: string[], errors: Map<string, Error>) => void;
}> = ({ 
  routes, 
  fallback, 
  preloadCount = 2,
  debounceDelay = 300,
  onRouteMatch,
  onPreloadStart,
  onPreloadComplete
}) => {
  const location = useLocation();
  const [preloadedApps, setPreloadedApps] = useState<Set<string>>(new Set());
  const [preloadErrors, setPreloadErrors] = useState<Map<string, Error>>(new Map());
  const [isPreloading, setIsPreloading] = useState(false);
  const [matchedRoute, setMatchedRoute] = useState<MicroAppRoute | undefined>();
  
  // 缓存路由匹配结果，提高性能
  const matchCacheRef = useRef<Map<string, MicroAppRoute | undefined>>(new Map());
  
  // 路由匹配优化器
  const matchRoute = useCallback(() => {
    const pathKey = location.pathname + location.search;
    
    // 检查缓存
    if (matchCacheRef.current.has(pathKey)) {
      const cachedMatch = matchCacheRef.current.get(pathKey);
      setMatchedRoute(cachedMatch);
      onRouteMatch?.(cachedMatch);
      return cachedMatch;
    }
    
    // 按优先级排序路由，优先匹配高优先级路由
    const sortedRoutes = [...routes].sort((a, b) => (b.priority || 0) - (a.priority || 0));
    
    for (const route of sortedRoutes) {
      let isMatch = false;
      
      try {
        if (typeof route.activeRule === 'function') {
          // 函数式匹配规则
          isMatch = route.activeRule(location);
        } else if (typeof route.activeRule === 'string') {
          // 字符串匹配规则 - 支持多种匹配模式
          const activeRule = route.activeRule;
          
          // 完整路径精确匹配
          if (activeRule === pathKey) {
            isMatch = true;
          }
          // 路径部分精确匹配
          else if (activeRule === location.pathname) {
            isMatch = true;
          }
          // 前缀匹配 (如: /app/*)
          else if (activeRule.endsWith('*')) {
            const prefix = activeRule.slice(0, -1);
            isMatch = location.pathname.startsWith(prefix);
          }
          // 简单路径前缀匹配
          else if (location.pathname.startsWith(activeRule)) {
            isMatch = true;
          }
        }
        
        if (isMatch) {
          matchCacheRef.current.set(pathKey, route);
          setMatchedRoute(route);
          onRouteMatch?.(route);
          
          // 发送路由变更事件
          globalEventBus?.emit(AppEvents.MICRO_APP_ROUTE_CHANGED, {
            route: route,
            path: location.pathname,
            timestamp: Date.now()
          });
          
          return route;
        }
      } catch (error) {
        console.error(`微应用路由匹配错误 (${route.name}):`, error);
        // 继续尝试其他路由
        continue;
      }
    }
    
    // 无匹配路由
    matchCacheRef.current.set(pathKey, undefined);
    setMatchedRoute(undefined);
    onRouteMatch?.(undefined);
    return undefined;
  }, [location, routes, onRouteMatch]);
  
  // 路由匹配逻辑
  useEffect(() => {
    const startTime = performance.now();
    const result = matchRoute();
    const matchTime = performance.now() - startTime;
    
    // 性能监控
    if (matchTime > 100) {
      console.warn(`微应用路由匹配耗时过长: ${matchTime.toFixed(2)}ms`, {
        path: location.pathname,
        matched: !!result
      });
    }
    
    // 限制缓存大小，避免内存泄漏
    if (matchCacheRef.current.size > 100) {
      // 清除最早的缓存项
      const firstKey = matchCacheRef.current.keys().next().value;
      matchCacheRef.current.delete(firstKey);
    }
  }, [location.pathname, location.search, matchRoute]);
  
  // 智能预加载机制优化
  useEffect(() => {
    // 预加载统计
    const preloadStats = {
      start: 0,
      end: 0,
      count: 0,
      success: 0,
      fail: 0
    };
    
    const preloadApps = async () => {
      // 避免重复预加载
      if (isPreloading) return;
      
      setIsPreloading(true);
      preloadStats.start = performance.now();
      
      try {
        // 1. 收集预加载目标
        // 按优先级排序：1. 当前匹配路由 2. 同域名相关路由 3. 其他路由按权重排序
        const sortedRoutes = [...routes].sort((a, b) => {
          // 优先排序当前匹配路由
          if (a.name === matchedRoute?.name) return -1;
          if (b.name === matchedRoute?.name) return 1;
          
          // 最后按优先级排序
          return (b.priority || 0) - (a.priority || 0);
        });
        
        // 2. 筛选未预加载且需要预加载的路由
        const preloadTargets = sortedRoutes
          .filter(route => route.entry && !preloadedApps.has(route.name))
          .slice(0, Math.max(1, preloadCount)); // 至少预加载一个
        
        preloadStats.count = preloadTargets.length;
        
        if (preloadTargets.length === 0) {
          setIsPreloading(false);
          return;
        }
        
        // 3. 通知预加载开始
        const preloadNames = preloadTargets.map(r => r.name);
        onPreloadStart?.(preloadNames);
        globalEventBus?.emit(AppEvents.MICRO_APP_PRELOAD_START, { apps: preloadNames });
        
        // 4. 执行预加载，支持并发控制
        const concurrencyLimit = 2; // 控制并发数
        const newPreloaded = new Set(preloadedApps);
        const newErrors = new Map(preloadErrors);
        
        // 并发执行器
        const executeWithConcurrency = async () => {
          const executing: Promise<void>[] = [];
          
          for (const route of preloadTargets) {
            // 等待直到执行中的任务数低于并发限制
            if (executing.length >= concurrencyLimit) {
              await Promise.race(executing);
            }
            
            const task = (async () => {
              try {
                // 检查环境和配置
                if (typeof window === 'undefined' || !window.$wujie) {
                  throw new Error('无界框架未初始化');
                }
                
                // 获取应用配置
                const appConfig = getMicroAppConfig?.(route.name);
                if (!appConfig || !appConfig.url) {
                  throw new Error('应用配置缺失或无效');
                }
                
                // 执行预加载
                const preloadStartTime = performance.now();
                await window.$wujie.preloadApp({
                  name: appConfig.name,
                  url: appConfig.url
                });
                const preloadTime = performance.now() - preloadStartTime;
                
                // 记录成功
                newPreloaded.add(route.name);
                newErrors.delete(route.name);
                preloadStats.success++;
                
                // 发送预加载成功事件
                globalEventBus?.emit(AppEvents.MICRO_APP_PRELOADED, {
                  name: route.name,
                  success: true,
                  time: preloadTime
                });
                
                console.log(`微应用 ${route.name} 预加载成功，耗时: ${preloadTime.toFixed(2)}ms`);
              } catch (error) {
                // 记录失败
                const err = error as Error;
                newErrors.set(route.name, err);
                preloadStats.fail++;
                
                // 发送预加载失败事件
                globalEventBus?.emit(AppEvents.MICRO_APP_PRELOADED, {
                  name: route.name,
                  success: false,
                  error: err.message
                });
                
                console.error(`微应用 ${route.name} 预加载失败:`, err);
              }
            })();
            
            executing.push(task);
            
            // 任务完成后从执行队列中移除
            task.finally(() => {
              const index = executing.indexOf(task);
              if (index > -1) executing.splice(index, 1);
            });
          }
          
          // 等待所有任务完成
          await Promise.allSettled(executing);
        };
        
        // 执行预加载
        await executeWithConcurrency();
        
        // 更新状态
        setPreloadedApps(newPreloaded);
        setPreloadErrors(newErrors);
        
        // 通知预加载完成
        const successApps = Array.from(newPreloaded).filter(name => 
          preloadNames.includes(name)
        );
        onPreloadComplete?.(successApps, newErrors);
        
        // 性能统计
        preloadStats.end = performance.now();
        const totalTime = preloadStats.end - preloadStats.start;
        
        // 记录性能指标
        globalEventBus?.emit(AppEvents.MICRO_APP_PERFORMANCE, {
          type: 'preload',
          count: preloadStats.count,
          success: preloadStats.success,
          fail: preloadStats.fail,
          duration: totalTime,
          averageTime: preloadStats.count > 0 ? totalTime / preloadStats.count : 0
        });
        
      } catch (error) {
        console.error('微应用预加载过程发生错误:', error);
      } finally {
        setIsPreloading(false);
      }
    };
    
    // 防抖处理，避免路由频繁切换时的重复预加载
    const timeoutId = setTimeout(() => {
      preloadApps();
    }, debounceDelay);
    
    return () => clearTimeout(timeoutId);
  }, [location.pathname, routes, matchedRoute, preloadCount, preloadedApps, preloadErrors, isPreloading, debounceDelay, onPreloadStart, onPreloadComplete]);
  
  // 监听页面可见性变化，优化预加载策略
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible' && !isPreloading) {
        // 页面变为可见时的优化策略可以在这里实现
      }
    };
    
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [isPreloading]);
  
  if (!matchedRoute) {
    return fallback || <div>微应用路由未匹配</div>;
  }
  
  return (
    <EnhancedMicroAppContainer
      appName={matchedRoute.name}
      customSandboxConfig={matchedRoute.sandbox}
      customSecurityPolicy={matchedRoute.securityPolicy}
    />
  );
}



/**
 * 路由转换配置选项
 */
export interface CreateRoutesOptions {
  defaultActiveRule?: (name: string) => string;
  mergeDuplicates?: boolean;
  validateRoutes?: boolean;
}

/**
 * 生成微应用路由配置
 * 将MicroAppConfig配置转换为适用于路由系统的MicroAppRoute配置
 * @param apps 微应用配置数组（使用8511行定义的MicroAppConfig接口）
 * @param options 路由转换配置选项
 * @returns 生成的路由配置数组
 * @throws 当传入无效的应用配置时会记录错误但不会抛出异常
 */
export function createMicroAppRoutes(
  apps: MicroAppConfig[],
  options: CreateRoutesOptions = {}
): MicroAppRoute[] {
  // 默认选项
  const defaultOptions: CreateRoutesOptions = {
    defaultActiveRule: (name: string) => `/${name}`,
    mergeDuplicates: true,
    validateRoutes: true,
    ...options
  };

  // 参数验证
  if (!Array.isArray(apps)) {
    console.error('createMicroAppRoutes: apps 参数必须是数组');
    return [];
  }
  
  // 去重逻辑，避免重复路由
  const routeMap = new Map<string, MicroAppRoute>();
  const processedApps = new Set<string>();
  const validationErrors: { appName: string; errors: string[] }[] = [];
  
  apps.forEach((app, index) => {
    if (!app || typeof app !== 'object') {
      console.warn(`createMicroAppRoutes: 索引 ${index} 的应用配置无效，跳过`);
      return;
    }
    
    // 验证应用名称
    if (!app.name || typeof app.name !== 'string' || !app.name.trim()) {
      validationErrors.push({
        appName: `[索引 ${index}]`,
        errors: ['应用名称是必需的且不能为空字符串']
      });
      return;
    }
    
    // 应用名称规范化
    const normalizedName = app.name.trim();
    
    // 处理重复应用
    if (processedApps.has(normalizedName)) {
      if (defaultOptions.mergeDuplicates) {
        console.warn(`createMicroAppRoutes: 应用名称 "${normalizedName}" 重复，使用最新配置`);
        // 继续处理，将使用最新配置覆盖
      } else {
        console.warn(`createMicroAppRoutes: 应用名称 "${normalizedName}" 重复，跳过后续配置`);
        return;
      }
    }
    
    // 构建路由配置
    const route: MicroAppRoute = {
      name: normalizedName,
      activeRule: app.activeRule || defaultOptions.defaultActiveRule(normalizedName),
      entry: app.entry,
      container: app.container,
      sandbox: app.sandbox,
      securityPolicy: app.securityPolicy,
      priority: typeof app.priority === 'number' ? app.priority : 0,
      props: app.props,
      loader: app.loader,
      error: app.error
    };
    
    // 验证路由配置
    if (defaultOptions.validateRoutes) {
      const routeValidation = validateRouteConfig(route);
      if (!routeValidation.isValid) {
        validationErrors.push({
          appName: normalizedName,
          errors: routeValidation.errors
        });
        // 即使验证失败，仍然添加路由，但会记录错误
      }
    }
    
    // 添加到路由映射
    routeMap.set(normalizedName, route);
    processedApps.add(normalizedName);
  });
  
  // 输出验证错误信息
  if (validationErrors.length > 0) {
    console.group('createMicroAppRoutes: 路由验证警告:');
    validationErrors.forEach(item => {
      console.warn(`应用 "${item.appName}": ${item.errors.join(', ')}`);
    });
    console.groupEnd();
  }
  
  // 转换为数组并按优先级排序
  return Array.from(routeMap.values())
    .sort((a, b) => (b.priority || 0) - (a.priority || 0));
}

/**
 * 验证路由配置
 * @param route 路由配置
 * @returns 验证结果
 */
function validateRouteConfig(route: MicroAppRoute): { isValid: boolean; errors: string[] } {
  const errors: string[] = [];
  
  // 验证activeRule类型
  if (route.activeRule !== undefined &&
      typeof route.activeRule !== 'string' &&
      typeof route.activeRule !== 'function') {
    errors.push('activeRule 必须是字符串或函数');
  }
  
  // 验证entry格式
  if (route.entry && typeof route.entry === 'string') {
    const trimmedEntry = route.entry.trim();
    if (!trimmedEntry.startsWith('http://') &&
        !trimmedEntry.startsWith('https://') &&
        !trimmedEntry.startsWith('/')) {
      errors.push('entry 必须是有效的URL或相对路径');
    }
  }
  
  // 验证sandbox类型
  if (route.sandbox !== undefined &&
      typeof route.sandbox !== 'boolean' &&
      typeof route.sandbox !== 'object') {
    errors.push('sandbox 必须是布尔值或对象');
  }
  
  // 验证priority类型
  if (route.priority !== undefined && typeof route.priority !== 'number') {
    errors.push('priority 必须是数字');
  }
  
  return { isValid: errors.length === 0, errors };
}

/**
 * 微前端路由钩子 - 用于动态路由场景
 * 提供类型安全、高效的路由管理功能
 */
/**
 * 路由变更类型枚举
 */
export enum RouteChangeType {
  /** 添加路由 */
  ADD = 'ADD',
  /** 更新路由 */
  UPDATE = 'UPDATE',
  /** 删除路由 */
  REMOVE = 'REMOVE'
}

export function useMicroAppRoutes(initialRoutes?: MicroAppRoute[]) {
  // 初始化时去重，确保路由名称唯一
  const initialUniqueRoutes = useMemo(() => {
    const routeMap = new Map<string, MicroAppRoute>();
    (initialRoutes || []).forEach(route => {
      if (route && route.name && typeof route.name === 'string' && route.name.trim()) {
        // 深拷贝路由配置，避免外部引用修改
        routeMap.set(route.name, { ...route });
      }
    });
    return Array.from(routeMap.values());
  }, [initialRoutes]);
  
  const [appRoutes, setAppRoutes] = useState<MicroAppRoute[]>(initialUniqueRoutes);
  
  // 路由变更事件分发
  const routeChangeEvent = useCallback((type: RouteChangeType, routeName: string, route?: MicroAppRoute, previousRoute?: MicroAppRoute) => {
    try {
      globalEventBus.emit(AppEvents.MICRO_APP_ROUTE_CHANGED, {
        type,
        routeName,
        route,
        previousRoute,
        timestamp: Date.now()
      });
    } catch (error) {
      console.error('useMicroAppRoutes: 发送路由变更事件失败:', error);
    }
  }, []);
  
  // 验证路由配置
  const validateRoute = useCallback((route: MicroAppRoute): { isValid: boolean; errors: string[] } => {
    const errors: string[] = [];
    
    if (!route || typeof route !== 'object') {
      errors.push('路由配置必须是对象');
      return { isValid: false, errors };
    }
    
    if (!route.name || typeof route.name !== 'string' || !route.name.trim()) {
      errors.push('路由名称是必需的且不能为空');
    }
    
    if (route.path && typeof route.path !== 'string') {
      errors.push('路由路径必须是字符串');
    }
    
    return { isValid: errors.length === 0, errors };
  }, []);
  
  // 动态添加路由
  const addRoute = useCallback((route: MicroAppRoute): boolean => {
    const validation = validateRoute(route);
    if (!validation.isValid) {
      console.error('addRoute: 路由验证失败:', validation.errors.join(', '));
      globalEventBus.emit(AppEvents.MICRO_APP_ROUTE_ERROR, {
        operation: 'add',
        routeName: route?.name,
        error: validation.errors.join(', ')
      });
      return false;
    }
    
    let added = false;
    
    setAppRoutes(prev => {
      // 检查路由是否已存在
      if (prev.some(r => r.name === route.name)) {
        console.warn(`addRoute: 路由 ${route.name} 已存在，使用updateRoute更新`);
        return prev;
      }
      
      const newRoutes = [...prev, { ...route }]; // 深拷贝，避免引用问题
      added = true;
      return newRoutes;
    });
    
    if (added) {
      routeChangeEvent(RouteChangeType.ADD, route.name, { ...route });
    }
    
    return added;
  }, [validateRoute, routeChangeEvent]);
  
  // 批量添加路由
  const addRoutes = useCallback((routes: MicroAppRoute[]): { successCount: number; failedRoutes: { route: MicroAppRoute; error: string }[] } => {
    if (!Array.isArray(routes)) {
      console.error('addRoutes: routes参数必须是数组');
      return { successCount: 0, failedRoutes: [] };
    }
    
    const validRoutes: MicroAppRoute[] = [];
    const failedRoutes: { route: MicroAppRoute; error: string }[] = [];
    
    // 预先验证所有路由
    routes.forEach(route => {
      const validation = validateRoute(route);
      if (validation.isValid) {
        validRoutes.push(route);
      } else {
        failedRoutes.push({
          route,
          error: validation.errors.join(', ')
        });
      }
    });
    
    let successCount = 0;
    
    if (validRoutes.length > 0) {
      setAppRoutes(prev => {
        const existingNames = new Set(prev.map(r => r.name));
        const filtered = validRoutes.filter(route => !existingNames.has(route.name));
        
        if (filtered.length === 0) {
          return prev;
        }
        
        successCount = filtered.length;
        const newRoutes = [...prev, ...filtered.map(r => ({ ...r }))]; // 深拷贝
        
        // 触发每个新增路由的事件
        filtered.forEach(route => {
          routeChangeEvent(RouteChangeType.ADD, route.name, { ...route });
        });
        
        return newRoutes;
      });
    }
    
    return { successCount, failedRoutes };
  }, [validateRoute, routeChangeEvent]);
  
  // 动态移除路由
  const removeRoute = useCallback((name: string): boolean => {
    if (!name || typeof name !== 'string') {
      console.error('removeRoute: 路由名称必须是非空字符串');
      return false;
    }
    
    let removedRoute: MicroAppRoute | undefined;
    let removed = false;
    
    setAppRoutes(prev => {
      const filtered = prev.filter(route => {
        const shouldRemove = route.name === name;
        if (shouldRemove) {
          removedRoute = { ...route };
        }
        return !shouldRemove;
      });
      
      removed = filtered.length !== prev.length;
      return filtered;
    });
    
    if (removed && removedRoute) {
      routeChangeEvent(RouteChangeType.REMOVE, name, undefined, removedRoute);
    } else if (!removed) {
      console.warn(`removeRoute: 未找到路由 ${name}`);
    }
    
    return removed;
  }, [routeChangeEvent]);
  
  // 批量移除路由
  const removeRoutes = useCallback((names: string[]): { successCount: number; notFound: string[] } => {
    if (!Array.isArray(names)) {
      console.error('removeRoutes: names参数必须是数组');
      return { successCount: 0, notFound: [] };
    }
    
    const nameSet = new Set(names.filter(n => n && typeof n === 'string'));
    const notFound: string[] = [];
    let successCount = 0;
    
    setAppRoutes(prev => {
      const nameArray = Array.from(nameSet);
      const nameMap = new Map(nameArray.map(name => [name, false]));
      
      const filtered = prev.filter(route => {
        if (nameMap.has(route.name)) {
          nameMap.set(route.name, true);
          successCount++;
          // 触发移除事件
          routeChangeEvent(RouteChangeType.REMOVE, route.name, undefined, { ...route });
          return false;
        }
        return true;
      });
      
      // 找出未找到的路由名称
      nameMap.forEach((found, name) => {
        if (!found) {
          notFound.push(name);
        }
      });
      
      return filtered;
    });
    
    return { successCount, notFound };
  }, [routeChangeEvent]);
  
  // 更新路由
  const updateRoute = useCallback((name: string, updates: Partial<MicroAppRoute>): boolean => {
    if (!name || typeof name !== 'string') {
      console.error('updateRoute: 路由名称必须是非空字符串');
      return false;
    }
    
    if (!updates || typeof updates !== 'object') {
      console.error('updateRoute: 更新内容必须是非空对象');
      return false;
    }
    
    let updated = false;
    let previousRoute: MicroAppRoute | undefined;
    
    setAppRoutes(prev => {
      const routeIndex = prev.findIndex(route => route.name === name);
      if (routeIndex === -1) {
        console.warn(`updateRoute: 未找到路由 ${name}`);
        return prev;
      }
      
      previousRoute = { ...prev[routeIndex] };
      const updatedRoute = { ...prev[routeIndex], ...updates };
      
      // 验证更新后的路由
      const validation = validateRoute(updatedRoute);
      if (!validation.isValid) {
        console.error('updateRoute: 更新后的路由验证失败:', validation.errors.join(', '));
        return prev;
      }
      
      const updatedRoutes = [...prev];
      updatedRoutes[routeIndex] = updatedRoute;
      updated = true;
      
      return updatedRoutes;
    });
    
    if (updated && previousRoute) {
      routeChangeEvent(RouteChangeType.UPDATE, name, { ...previousRoute, ...updates }, previousRoute);
    }
    
    return updated;
  }, [validateRoute, routeChangeEvent]);
  
  // 获取特定路由
  const getRoute = useCallback((name: string): MicroAppRoute | undefined => {
    if (!name || typeof name !== 'string') {
      return undefined;
    }
    
    const route = appRoutes.find(r => r.name === name);
    // 返回深拷贝，避免外部修改
    return route ? { ...route } : undefined;
  }, [appRoutes]);
  
  // 查找路由
  const findRoutes = useCallback((predicate: (route: MicroAppRoute) => boolean): MicroAppRoute[] => {
    if (typeof predicate !== 'function') {
      console.error('findRoutes: predicate必须是函数');
      return [];
    }
    
    return appRoutes.filter(predicate).map(route => ({ ...route })); // 返回深拷贝
  }, [appRoutes]);
  
  // 清空所有路由
  const clearRoutes = useCallback((): number => {
    const previousCount = appRoutes.length;
    
    if (previousCount > 0) {
      // 触发所有路由的移除事件
      appRoutes.forEach(route => {
        routeChangeEvent(RouteChangeType.REMOVE, route.name, undefined, { ...route });
      });
      
      setAppRoutes([]);
    }
    
    return previousCount;
  }, [appRoutes, routeChangeEvent]);
  
  // 检查路由是否存在
  const hasRoute = useCallback((name: string): boolean => {
    return !!name && appRoutes.some(route => route.name === name);
  }, [appRoutes]);
  
  return {
    routes: appRoutes,
    addRoute,
    addRoutes,
    removeRoute,
    removeRoutes,
    updateRoute,
    getRoute,
    findRoutes,
    clearRoutes,
    hasRoute
  };
}

// 微前端事件总线系统
// src/micro-frontend/event-bus.ts
/**
 * 微应用事件枚举
 * 包含所有微前端架构中使用的事件名称
 */
export enum AppEvents {
  // 路由相关事件
  MICRO_APP_ROUTE_CHANGED = 'micro_app_route_changed',
  MICRO_APP_ROUTE_ERROR = 'micro_app_route_error',
  ROUTE_CHANGED = 'route_changed',
  
  // 生命周期相关事件
  MICRO_APP_LOAD_START = 'micro_app_load_start',
  MICRO_APP_LOAD_COMPLETE = 'micro_app_load_complete',
  MICRO_APP_LOAD_ERROR = 'micro_app_load_error',
  MICRO_APP_MOUNT = 'micro_app_mount',
  MICRO_APP_UNMOUNT = 'micro_app_unmount',
  MICRO_APP_MOUNTED = 'micro_app_mounted',
  MICRO_APP_UNMOUNTED = 'micro_app_unmounted',
  MICRO_APP_LOADED = 'micro_app_loaded',
  MICRO_APP_BEFORE_UNMOUNT = 'micro_app_before_unmount',
  
  // 错误相关事件
  MICRO_APP_ERROR = 'micro_app_error',
  MICRO_APP_CONFIG_ERROR = 'micro_app_config_error',
  MICRO_APP_CRITICAL_ERROR = 'micro_app_critical_error',
  
  // 性能相关事件
  MICRO_APP_PERFORMANCE = 'micro_app_performance',
  MICRO_APP_LOAD_PROGRESS = 'micro_app_load_progress',
  
  // 预加载相关事件
  MICRO_APP_PRELOAD_START = 'micro_app_preload_start',
  MICRO_APP_PRELOADED = 'micro_app_preloaded',
  
  // 通信相关事件
  MICRO_APP_MESSAGE = 'micro_app_message',
  MICRO_APP_GLOBAL_MESSAGE = 'micro_app_global_message',
  SHOW_MESSAGE = 'show_message',
  
  // 状态相关事件
  MICRO_APP_STATUS_CHANGED = 'micro_app_status_changed',
  ACTIVE_APP_CHANGED = 'active_app_changed',
  MICRO_APP_FALLBACK = 'micro_app_fallback',
  
  // 数据相关事件
  DATA_REFRESH = 'data_refresh',
  
  // 用户相关事件
  USER_UPDATE = 'user_update',
  AUTH_EXPIRED = 'auth_expired',
  
  // UI相关事件
  THEME_CHANGE = 'theme_change',
  
  // 页面可见性事件
  PAGE_VISIBLE = 'page_visible',
  PAGE_HIDDEN = 'page_hidden',
}

/**
 * 微应用事件监听器类型
 */
export type EventListener = (...args: any[]) => void;

/**
 * 全局事件总线类
 * 负责主应用和微应用之间的通信
 */
export class MicroAppEventBus {
  private eventMap: Map<string, Set<EventListener>>;
  private onceEventMap: Map<string, Set<EventListener>>;
  private isMicroApp: boolean;
  private wujieBus: any;
  private isInitialized: boolean = false;
  private appInstanceId: string | null = null;
  private eventQueue: Array<{event: string, args: any[], timestamp: number}> = [];
  private readonly MAX_QUEUE_SIZE = 100;

  constructor(appId?: string) {
    this.eventMap = new Map();
    this.onceEventMap = new Map();
    this.appInstanceId = appId || null;
    this.initialize();
  }

  /**
   * 初始化事件总线
   */
  private initialize(): void {
    if (typeof window === 'undefined') {
      console.warn('MicroAppEventBus: 无法在非浏览器环境中初始化');
      return;
    }
    
    this.isMicroApp = !!window.__POWERED_BY_WUJIE__;
    this.wujieBus = window.$wujie?.bus;
    this.isInitialized = true;
    
    // 初始化与无界框架的通信桥接
    this.initializeWujieBridge();
    
    // 处理队列中的事件
    this.processEventQueue();
  }

  /**
   * 初始化无界框架的通信桥接
   */
  private initializeWujieBridge(): void {
    if (!this.wujieBus) return;
    
    if (this.isMicroApp) {
      // 在微应用中监听主应用的事件
      this.wujieBus.$on('micro_app_message', (data: any) => {
        try {
          const { event, args, source } = data;
          // 避免事件循环 (如果消息来自自身)
          if (source !== this.appInstanceId) {
            this.emit(event, ...args);
          }
        } catch (error) {
          console.error('MicroAppEventBus: 处理无界框架消息时出错:', error);
        }
      });
    } else {
      // 在主应用中监听微应用的事件
      this.wujieBus.$on('micro_app_message_from_child', (data: any) => {
        try {
          const { event, args } = data;
          this.emit(event, ...args);
        } catch (error) {
          console.error('MicroAppEventBus: 处理微应用消息时出错:', error);
        }
      });
    }
  }

  /**
   * 处理事件队列
   */
  private processEventQueue(): void {
    while (this.eventQueue.length > 0 && this.isInitialized) {
      const queuedEvent = this.eventQueue.shift();
      if (queuedEvent) {
        this.emitInternal(queuedEvent.event, queuedEvent.args);
      }
    }
  }

  /**
   * 监听事件
   * @param event 事件名称
   * @param listener 事件监听器
   * @returns this 链式调用
   */
  on(event: string, listener: EventListener): this {
    if (typeof listener !== 'function') {
      console.warn('MicroAppEventBus: 监听器必须是函数');
      return this;
    }
    
    if (!this.eventMap.has(event)) {
      this.eventMap.set(event, new Set());
    }
    this.eventMap.get(event)?.add(listener);
    return this;
  }

  /**
   * 监听一次性事件
   * @param event 事件名称
   * @param listener 事件监听器
   * @returns this 链式调用
   */
  once(event: string, listener: EventListener): this {
    if (typeof listener !== 'function') {
      console.warn('MicroAppEventBus: 监听器必须是函数');
      return this;
    }
    
    const onceWrapper = (...args: any[]) => {
      this.off(event, onceWrapper);
      listener(...args);
    };
    
    onceWrapper._originalListener = listener;
    return this.on(event, onceWrapper);
  }

  /**
   * 取消监听事件
   * @param event 事件名称
   * @param listener 事件监听器
   * @returns this 链式调用
   */
  off(event: string, listener?: EventListener): this {
    if (listener) {
      // 如果指定了监听器，则只移除该监听器
      const eventListeners = this.eventMap.get(event);
      if (eventListeners) {
        // 查找原始监听器或包装器
        let found = false;
        for (const registeredListener of eventListeners) {
          if (registeredListener === listener || registeredListener._originalListener === listener) {
            eventListeners.delete(registeredListener);
            found = true;
            break;
          }
        }
        
        // 如果事件没有监听器了，清理Map条目
        if (eventListeners.size === 0) {
          this.eventMap.delete(event);
        }
        
        if (!found) {
          console.warn(`MicroAppEventBus: 未找到事件 ${event} 的监听器`);
        }
      }
    } else {
      // 移除该事件的所有监听器
      this.eventMap.delete(event);
    }
    
    return this;
  }

  /**
   * 内部事件发射方法
   * @param event 事件名称
   * @param args 事件参数
   */
  private emitInternal(event: string, args: any[]): void {
    const listeners = this.eventMap.get(event);
    if (listeners) {
      // 创建监听器副本，避免在迭代过程中修改集合导致的问题
      const listenersCopy = new Set(listeners);
      listenersCopy.forEach(listener => {
        try {
          listener(...args);
        } catch (error) {
          console.error(`MicroAppEventBus: 执行事件 ${event} 监听器时出错:`, error);
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
          console.error(`MicroAppEventBus: 执行一次性事件 ${event} 监听器时出错:`, error);
        }
      });
      this.onceEventMap.delete(event);
    }
  }

  /**
   * 发射事件
   * @param event 事件名称
   * @param args 事件参数
   * @returns 是否成功发射
   */
  emit(event: string, ...args: any[]): boolean {
    // 如果尚未初始化，将事件加入队列
    if (!this.isInitialized) {
      if (this.eventQueue.length < this.MAX_QUEUE_SIZE) {
        this.eventQueue.push({ event, args, timestamp: Date.now() });
        return true;
      }
      console.warn('MicroAppEventBus: 事件队列已满，丢弃事件:', event);
      return false;
    }
    
    // 先在本地发射事件
    this.emitInternal(event, args);
    
    // 通过无界框架进行跨应用通信
    if (this.wujieBus) {
      try {
        if (this.isMicroApp) {
          // 微应用向主应用发送消息
          this.wujieBus.$emit('micro_app_message_from_child', {
            event,
            args,
            source: this.appInstanceId,
            timestamp: Date.now()
          });
        } else {
          // 主应用向所有微应用广播消息
          this.wujieBus.$emit('micro_app_message', {
            event,
            args,
            source: this.appInstanceId,
            timestamp: Date.now()
          });
        }
      } catch (error) {
        console.error('MicroAppEventBus: 通过无界框架发送消息失败:', error);
        return false;
      }
    }
    
    return true;
  }

  /**
   * 获取指定事件的监听器数量
   * @param event 事件名称
   * @returns 监听器数量
   */
  getListenerCount(event: string): number {
    const regularListeners = this.eventMap.get(event);
    const onceListeners = this.onceEventMap.get(event);
    
    return (
      (regularListeners ? regularListeners.size : 0) +
      (onceListeners ? onceListeners.size : 0)
    );
  }

  /**
   * 清理资源
   */
  dispose(): void {
    this.eventMap.clear();
    this.onceEventMap.clear();
    this.eventQueue = [];
    
    if (this.wujieBus && this.isMicroApp) {
      try {
        this.wujieBus.$off('micro_app_message');
      } catch (error) {
        console.error('MicroAppEventBus: 清理无界框架监听器失败:', error);
      }
    }
    
    this.isInitialized = false;
    this.wujieBus = null;
  }

  /**
   * 获取实例标识
   * @returns 实例ID
   */
  getInstanceId(): string | null {
    return this.appInstanceId;
  }
}

/**
 * 全局事件总线实例
 */
export const globalEventBus = new MicroAppEventBus();

/**
 * 为特定微应用创建事件总线实例
 * @param appId 应用ID
 * @returns 事件总线实例
 */
export function createAppEventBus(appId: string): MicroAppEventBus {
  return new MicroAppEventBus(appId);
}
    
    if (this.wujieBus && this.isMicroApp) {
      try {
        this.wujieBus.$off('micro_app_message');
      } catch (error) {
        console.error('MicroAppEventBus: 清理无界框架监听器失败:', error);
      }
    }
    
    this.isInitialized = false;
    this.wujieBus = null;
  }

  /**
   * 获取实例标识
   * @returns 实例ID
   */
  getInstanceId(): string | null {
    return this.appInstanceId;
  }
}

  /**
   * 触发事件
   * @param event 事件名称
   * @param args 事件参数
   * @returns this 链式调用
   */
  emit(event: string, ...args: any[]): this {
    // 触发普通事件
    const normalListeners = this.eventMap.get(event);
    if (normalListeners) {
      normalListeners.forEach(listener => {
        try {
          listener(...args);
        } catch (error) {
          console.error(`微前端事件 ${event} 处理失败:`, error);
        }
      });
    }

    // 触发一次性事件并移除
    const onceListeners = this.onceEventMap.get(event);
    if (onceListeners) {
      onceListeners.forEach(listener => {
        try {
          listener(...args);
        } catch (error) {
          console.error(`微前端一次性事件 ${event} 处理失败:`, error);
        }
      });
      this.onceEventMap.delete(event);
    }

    // 通过无界框架进行跨应用通信
    this.emitCrossApp(event, args);
    
    return this;
  }

  /**
   * 跨应用触发事件（通过无界框架）
   * @param event 事件名称
   * @param args 事件参数
   */
  private emitCrossApp(event: string, args: any[]): void {
    if (this.isMicroApp && this.wujieBus) {
      // 微应用向主应用发送消息
      this.wujieBus.$emit('micro_app_message', {
        event,
        args,
        from: this.getCurrentAppName(),
        timestamp: Date.now()
      });
    } else if (this.wujieBus) {
      // 主应用向所有微应用发送消息
      this.wujieBus.$emit('micro_app_message', {
        event,
        args,
        from: 'main',
        timestamp: Date.now()
      });
    }
  }

  /**
   * 向指定微应用发送消息
   * @param appName 目标微应用名称
   * @param event 事件名称
   * @param args 事件参数
   */
  emitToApp(appName: string, event: string, ...args: any[]): void {
    if (this.wujieBus && !this.isMicroApp) {
      // 主应用向特定微应用发送消息
      this.wujieBus.$emit(`micro_app_message_${appName}`, {
        event,
        args,
        from: 'main',
        timestamp: Date.now()
      });
    } else {
      console.warn('无法向指定微应用发送消息，当前环境不支持');
    }
  }

  /**
   * 清空所有事件监听器
   * @returns this 链式调用
   */
  clear(): this {
    this.eventMap.clear();
    this.onceEventMap.clear();
    return this;
  }

  /**
   * 获取指定事件的监听器数量
   * @param event 事件名称
   * @returns 监听器数量
   */
  listenerCount(event: string): number {
    const normalCount = this.eventMap.get(event)?.size || 0;
    const onceCount = this.onceEventMap.get(event)?.size || 0;
    return normalCount + onceCount;
  }

  /**
   * 获取当前应用名称
   * @returns 应用名称
   */
  private getCurrentAppName(): string {
    if (this.isMicroApp && typeof window !== 'undefined') {
      return window.__WUJIE_APPNAME__ || 'unknown';
    }
    return 'main';
  }
}

// 导出全局事件总线实例
export const globalEventBus = new MicroAppEventBus();

/**
 * 微应用消息类型
 */
export interface MicroAppMessage {
  type: string;
  payload?: any;
  from: string;
  to?: string;
  timestamp: number;
}

/**
 * 微应用消息处理器
 */
export type MessageHandler = (message: MicroAppMessage) => void;

/**
 * 微应用消息通信工具类
 */
export class MicroAppMessenger {
  private eventBus: MicroAppEventBus;
  
  constructor(eventBus: MicroAppEventBus = globalEventBus) {
    this.eventBus = eventBus;
  }
  
  /**
   * 发送消息到指定微应用
   * @param appName 目标微应用名称
   * @param type 消息类型
   * @param payload 消息内容
   */
  send(appName: string, type: string, payload?: any): void {
    const message: MicroAppMessage = {
      type,
      payload,
      from: this.getSenderName(),
      to: appName,
      timestamp: Date.now()
    };
    
    if (appName === 'main' && typeof window !== 'undefined' && window.__POWERED_BY_WUJIE__) {
      // 微应用发送消息给主应用
      this.eventBus.emitToApp(appName, AppEvents.MICRO_APP_MESSAGE, message);
    } else if (appName === 'main') {
      // 主应用内部通信
      this.eventBus.emit(AppEvents.MICRO_APP_MESSAGE, message);
    } else {
      // 主应用发送消息给微应用
      this.eventBus.emitToApp(appName, AppEvents.MICRO_APP_MESSAGE, message);
    }
  }
  
  /**
   * 发送全局消息给所有应用
   * @param type 消息类型
   * @param payload 消息内容
   */
  broadcast(type: string, payload?: any): void {
    const message: MicroAppMessage = {
      type,
      payload,
      from: this.getSenderName(),
      timestamp: Date.now()
    };
    
    this.eventBus.emit(AppEvents.MICRO_APP_GLOBAL_MESSAGE, message);
  }
  
  /**
   * 监听指定类型的消息
   * @param type 消息类型
   * @param handler 消息处理器
   */
  on(type: string, handler: MessageHandler): () => void {
    const eventHandler = (message: MicroAppMessage) => {
      if (message.type === type && (!message.to || message.to === this.getSenderName())) {
        handler(message);
      }
    };
    
    this.eventBus.on(AppEvents.MICRO_APP_MESSAGE, eventHandler);
    this.eventBus.on(AppEvents.MICRO_APP_GLOBAL_MESSAGE, eventHandler);
    
    // 返回取消监听函数
    return () => {
      this.eventBus.off(AppEvents.MICRO_APP_MESSAGE, eventHandler);
      this.eventBus.off(AppEvents.MICRO_APP_GLOBAL_MESSAGE, eventHandler);
    };
  }
  
  /**
   * 获取发送者名称
   * @returns 发送者名称
   */
  private getSenderName(): string {
    if (typeof window !== 'undefined') {
      if (window.__POWERED_BY_WUJIE__) {
        return window.__WUJIE_APPNAME__ || 'unknown_micro_app';
      }
    }
    return 'main';
  }
}

// 导出全局消息通信工具实例
export const microAppMessenger = new MicroAppMessenger();
import { getMicroAppConfig, microAppConfigs } from '@/micro-frontend/config';
import { EnhancedMicroAppContainer, MicroAppContainer } from './MicroAppContainer';
import { preloadApp } from 'wujie-react';
import { performance } from 'perf_hooks';

/**
 * 微应用路由配置接口
 * 用于定义微前端应用的路由信息
 */
export interface MicroAppRoute {
  /** 应用名称 */
  name: string;
  /** 路由匹配规则 */
  activeRule: string | ((location: any) => boolean);
  /** 应用入口地址 */
  entry?: string;
  /** 容器选择器 */
  container?: string;
  /** 沙箱配置 */
  sandbox?: boolean | Record<string, any>;
  /** 安全策略配置 */
  securityPolicy?: Record<string, any>;
  /** 优先级 */
  priority?: number;
  /** 传递给应用的属性 */
  props?: Record<string, any>;
  /** 加载组件 */
  loader?: React.ReactNode;
  /** 错误组件 */
  error?: React.ReactNode;
  /** 路由路径 */
  path?: string;
  /** 是否精确匹配 */
  exact?: boolean;
  /** 是否区分大小写 */
  sensitive?: boolean;
  /** 是否严格匹配 */
  strict?: boolean;
  /** 是否预加载 */
  preload?: boolean;
  /** 是否保活 */
  alive?: boolean;
  /** 是否同步加载 */
  sync?: boolean;
  /** 是否单例模式 */
  singleton?: boolean;
  /** 降级配置 */
  degrade?: boolean | (() => boolean);
  /** 插件配置 */
  plugins?: any[];
  /** 加载完成回调 */
  onLoad?: () => void;
  /** 错误处理回调 */
  onError?: (error: Error) => void;
  /** 应用消息回调 */
  onAppMessage?: (data: any) => void;
  /** 应用卸载回调 */
  onAppUnmount?: () => void;
  /** 降级组件 */
  fallback?: React.ReactNode;
}

interface MicroAppRouterProps {
  routes: MicroAppRoute[];
  defaultFallback?: React.ReactNode;
  loadingComponent?: React.ReactNode;
  errorComponent?: (error: Error) => React.ReactNode;
  useEnhancedContainer?: boolean;
  preloadDistance?: number;
  maxPreloadApps?: number;
}

// 错误边界组件
const ErrorBoundary: React.FC<{
  children: React.ReactNode;
  fallback: (error: Error) => React.ReactNode;
}> = ({ children, fallback }) => {
  const [error, setError] = useState<Error | null>(null);

  if (error) {
    return <>{fallback(error)}</>;
  }

  return React.createElement(React.Fragment, null, children);
};

// 延迟加载的微应用组件
const LazyLoadMicroApp: React.FC<MicroAppRoute & { routeProps: any }> = ({ 
  appName, 
  alive = true, 
  sync = true, 
  singleton = false,
  degrade = false,
  plugins = [],
  props = {}, 
  routeProps,
  onLoad,
  onError,
  onAppMessage,
  onAppUnmount
}) => {
  const [loading, setLoading] = useState(true);
  const { addPerformanceMetric } = useMicroAppStore();
  const ContainerComponent = useMicroAppStore.getState().useEnhancedContainer ? 
    EnhancedMicroAppContainer : MicroAppContainer;

  useEffect(() => {
    // 记录开始加载
    setLoading(true);
    
    // 构建传递给微应用的路由相关props
    const mergedProps = {
      ...props,
      route: routeProps
    };

    // 模拟立即加载完成，因为实际加载由MicroAppContainer处理
    setTimeout(() => {
      setLoading(false);
    }, 0);

  }, [appName, props, routeProps]);

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%', minHeight: '400px' }}>
        <Spin size="large" tip={`加载微应用 ${appName}...`} />
      </div>
    );
  }

  return (
    <ContainerComponent
      appName={appName}
      alive={alive}
      sync={sync}
      singleton={singleton}
      degrade={degrade}
      plugins={plugins}
      props={props}
      onLoad={onLoad}
      onError={onError}
      onAppMessage={onAppMessage}
      onAppUnmount={onAppUnmount}
    />
  );
};

export const MicroAppRouter: React.FC<MicroAppRouterProps> = ({
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
  const { getAppVersion, setActiveApp, updateAppStatus, addPerformanceMetric } = useMicroAppStore();
  const [matchedRoute, setMatchedRoute] = useState<MicroAppRoute | null>(null);
  const [preloadingApps, setPreloadingApps] = useState<Set<string>>(new Set());
  const [routeChangeStartTime, setRouteChangeStartTime] = useState(0);

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
      
      // 优先从缓存获取配置
      let config = microAppConfigs.get(appName);
      
      // 如果缓存中没有，动态获取配置
      if (!config) {
        config = await getMicroAppConfig(appName);
      }
      
      if (config && config.url) {
        console.log(`使用无界框架预加载微应用: ${appName}`, config.url);
        const startTime = performance.now();
        
        // 使用无界框架的预加载API
        await preloadApp(appName, config.url);
        
        const loadTime = performance.now() - startTime;
        console.log(`微应用 ${appName} 预加载完成，耗时: ${loadTime.toFixed(2)}ms`);
        
        // 记录预加载性能
        addPerformanceMetric({
          appName,
          metric: 'preloadTime',
          value: loadTime,
          success: true
        });
        
        // 预加载完成事件
        globalEventBus.emit(AppEvents.MICRO_APP_PRELOADED, {
          appName,
          loadTime,
          timestamp: Date.now()
        });
      }
    } catch (error) {
      console.error(`微应用 ${appName} 预加载失败:`, error);
      
      // 记录预加载错误
      addPerformanceMetric({
        appName,
        metric: 'preloadTime',
        value: 0,
        success: false,
        error: (error as Error).message
      });
    } finally {
      // 无论成功失败都移除预加载状态
      setPreloadingApps(prev => {
        const newSet = new Set(prev);
        newSet.delete(appName);
        return newSet;
      });
    }
  }, [preloadingApps, addPerformanceMetric]);

  // 智能预加载逻辑
  useEffect(() => {
    if (!currentMatchedRoute) return;
    
    // 基于优先级和配置的预加载策略
    const sortedAppsToPreload = routes
      .filter(route => {
        // 不预加载当前应用
        if (route.appName === currentMatchedRoute.appName) return false;
        
        // 只预加载启用了预加载的应用
        if (route.preload === false) return false;
        
        // 避免重复预加载
        if (preloadingApps.has(route.appName)) return false;
        
        return true;
      })
      // 按优先级排序（数值越小优先级越高）
      .sort((a, b) => (a.priority || 999) - (b.priority || 999))
      .map(route => route.appName)
      .filter((appName, index, self) => self.indexOf(appName) === index) // 去重
      .slice(0, maxPreloadApps); // 限制预加载数量
    
    // 执行预加载
    sortedAppsToPreload.forEach(appName => {
      doPreload(appName);
    });
  }, [currentMatchedRoute, routes, preloadingApps, maxPreloadApps, doPreload]);

  // 更新活动路由和状态
  useEffect(() => {
    setRouteChangeStartTime(performance.now());
    
    setMatchedRoute(currentMatchedRoute);
    
    if (currentMatchedRoute) {
      const { appName } = currentMatchedRoute;
      setActiveApp(appName);
      updateAppStatus(appName, 'loading');
      
      // 通知路由变化
      globalEventBus.emit(AppEvents.MICRO_APP_ROUTE_CHANGED, {
        appName,
        path: location.pathname,
        timestamp: Date.now()
      });
      
      // 页面可见性变化时处理
      const handleVisibilityChange = () => {
        if (document.hidden) {
          // 页面隐藏时可以做一些优化
          globalEventBus.emit(AppEvents.PAGE_HIDDEN, {
            appName,
            timestamp: Date.now()
          });
        } else {
          // 页面显示时恢复
          globalEventBus.emit(AppEvents.PAGE_VISIBLE, {
            appName,
            timestamp: Date.now()
          });
        }
      };
      
      document.addEventListener('visibilitychange', handleVisibilityChange);
      
      return () => {
        document.removeEventListener('visibilitychange', handleVisibilityChange);
      };
    } else {
      setActiveApp(null);
    }
  }, [currentMatchedRoute, location.pathname, setActiveApp, updateAppStatus]);

  // 处理微应用错误
  const handleAppError = useCallback((error: Error) => {
    console.error('微应用路由加载错误:', error);
    
    // 上报错误
    globalEventBus.emit(AppEvents.MICRO_APP_ROUTE_ERROR, {
      path: location.pathname,
      error: error.message,
      timestamp: Date.now()
    });
    
    // 如果有自定义错误组件，调用它
    if (errorComponent) {
      return errorComponent(error);
    }
    
    // 默认错误组件
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
        style={{ margin: '20px' }}
      />
    );
  }, [location.pathname, errorComponent]);

  // 渲染匹配的微应用
  if (matchedRoute) {
    const { appName } = matchedRoute;
    const version = getAppVersion(appName);
    
    // 构建传递给微应用的路由相关props
    const routeProps = {
      location,
      navigate,
      params,
      version
    };
    
    return (
      <ErrorBoundary
        fallback={handleAppError}
      >
        <div className="micro-app-router-container" style={{ width: '100%', height: '100%' }}>
          <LazyLoadMicroApp
            {...matchedRoute}
            routeProps={routeProps}
          />
        </div>
      </ErrorBoundary>
    );
  }
  
  // 渲染默认回退组件
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%', minHeight: '400px' }}>
      {defaultFallback}
    </div>
  );
};

// 导出一个路由配置工具函数
export const createMicroAppRoutes = (routes: MicroAppRoute[]): MicroAppRoute[] => {
  return routes.map(route => ({
    exact: true,
    strict: false,
    sensitive: false,
    preload: true,
    priority: 100,
    alive: true,
    sync: true,
    singleton: false,
    degrade: false,
    ...route
  }));
};

// 导出一个快速创建路由的Hook
export const useMicroAppRoutes = (routes: MicroAppRoute[]) => {
  const processedRoutes = useMemo(() => {
    return createMicroAppRoutes(routes);
  }, [routes]);
  
  return processedRoutes;
};
};
```

#### 5.1.3 高级微前端容器组件（基于无界框架）

```typescript
// src/micro-frontend/components/MicroAppContainer.tsx
import React, { useEffect, useState, useRef, useCallback } from 'react';
import { WujieReact, preloadApp } from 'wujie-react';
import { useMicroAppStore } from '@/stores/microAppStore';
import { useAuthStore } from '@/stores/authStore';
import { getMicroAppConfig, microAppConfigs } from '@/micro-frontend/config';
import { globalEventBus, AppEvents } from '@/micro-frontend/event-bus';
import { Spin, Alert, Button, Space, Tooltip } from 'antd';
import { ReloadOutlined, SettingOutlined, EyeOutlined } from '@ant-design/icons';
import { performance } from 'perf_hooks';
import { isSupportWebComponent } from '@/utils/browser';
import { getMicroAppPermissions } from '@/utils/permissions';

interface MicroAppContainerProps {
  appName: string;
  className?: string;
  fallback?: React.ReactNode;
  alive?: boolean;
  sync?: boolean;
  autoLoad?: boolean;
  singleton?: boolean;
  degrade?: boolean; // 降级标志
  plugins?: any[]; // 无界插件
  props?: Record<string, any>;
  onLoad?: () => void;
  onError?: (error: Error) => void;
  onAppMessage?: (data: any) => void;
  onAppUnmount?: () => void;
}

export const MicroAppContainer: React.FC<MicroAppContainerProps> = ({
  appName,
  className,
  fallback,
  alive = true,
  sync = true,
  autoLoad = true,
  singleton = false,
  degrade = false,
  plugins = [],
  props = {},
  onLoad,
  onError,
  onAppMessage,
  onAppUnmount
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<Error | null>(null);
  const [loading, setLoading] = useState(true);
  const [appConfig, setAppConfig] = useState<any>(null);
  const [loadStartTime, setLoadStartTime] = useState(0);
  const { user, token } = useAuthStore();
  const { updateAppStatus, setActiveApp, getAppVersion, addPerformanceMetric } = useMicroAppStore();
  const isMountedRef = useRef(true); // 避免组件卸载后更新状态
  
  // 预加载微应用资源
  const preloadAppResources = useCallback(async () => {
    try {
      if (appConfig && appConfig.url) {
        // 使用无界的预加载能力
        await preloadApp(appConfig.name, appConfig.url);
        console.log(`预加载微应用资源成功: ${appName}`);
      }
    } catch (err) {
      console.warn(`预加载微应用资源失败: ${appName}`, err);
      // 预加载失败不影响正常加载流程
    }
  }, [appConfig, appName]);

  // 加载微应用配置
  useEffect(() => {
    const loadConfig = async () => {
      try {
        // 尝试从动态配置中心获取配置
        const config = await getMicroAppConfig(appName);
        
        if (!config) {
          throw new Error(`未找到微应用配置: ${appName}`);
        }
        
        // 权限检查
        const permissions = await getMicroAppPermissions(appName);
        if (!permissions.canAccess) {
          throw new Error(`无权限访问微应用: ${appName}`);
        }
        
        // 合并权限信息到配置
        config.permissions = permissions;
        setAppConfig(config);
        
        // 预加载资源
        if (autoLoad) {
          preloadAppResources();
        }
      } catch (err) {
        console.error(`加载微应用配置失败:`, err);
        if (isMountedRef.current) {
          setError(err as Error);
          setLoading(false);
        }
        updateAppStatus(appName, 'error');
        
        // 上报配置加载错误
        globalEventBus.emit(AppEvents.MICRO_APP_CONFIG_ERROR, {
          appName,
          error: (err as Error).message,
          timestamp: Date.now()
        });
      }
    };
    
    loadConfig();
    
    // 清理函数
    return () => {
      isMountedRef.current = false;
    };
  }, [appName, updateAppStatus, autoLoad, preloadAppResources]);
  
  // 管理活动应用状态
  useEffect(() => {
    if (appConfig && !error && loading === false) {
      setActiveApp(appName);
      
      return () => {
        // 只有当组件卸载且不是错误状态时才设置activeApp为null
        if (!error && isMountedRef.current) {
          setActiveApp(null);
        }
        // 触发应用卸载事件
        if (onAppUnmount) {
          onAppUnmount();
        }
      };
    }
  }, [appName, appConfig, error, loading, setActiveApp, onAppUnmount]);
  
  // 错误处理
  const handleError = (err: Error) => {
    console.error(`微应用 ${appName} 加载失败:`, err);
    
    // 计算加载时间
    const loadTime = loadStartTime > 0 ? performance.now() - loadStartTime : 0;
    
    if (isMountedRef.current) {
      setError(err);
      setLoading(false);
    }
    updateAppStatus(appName, 'error');
    
    // 记录性能指标
    addPerformanceMetric({
      appName,
      metric: 'loadTime',
      value: loadTime,
      success: false,
      error: err.message
    });
    
    // 上报错误
    globalEventBus.emit(AppEvents.MICRO_APP_ERROR, {
      appName,
      error: err.message,
      timestamp: Date.now(),
      loadTime
    });
    
    // 调用用户自定义错误处理
    onError?.(err);
  };
  
  // 加载开始
  const handleLoadStart = () => {
    setLoadStartTime(performance.now());
    console.log(`微应用 ${appName} 开始加载`);
  };

  // 加载成功处理
  const handleLoad = () => {
    const loadTime = performance.now() - loadStartTime;
    console.log(`微应用 ${appName} 加载成功，耗时: ${loadTime.toFixed(2)}ms`);
    
    if (isMountedRef.current) {
      setLoading(false);
      setError(null);
    }
    updateAppStatus(appName, 'ready');
    
    // 记录性能指标
    addPerformanceMetric({
      appName,
      metric: 'loadTime',
      value: loadTime,
      success: true
    });
    
    // 上报加载成功
    globalEventBus.emit(AppEvents.MICRO_APP_LOADED, {
      appName,
      timestamp: Date.now(),
      loadTime
    });
    
    // 调用用户自定义加载完成处理
    onLoad?.();
  };
  
  // 重试加载
  const handleRetry = () => {
    if (isMountedRef.current) {
      setError(null);
      setLoading(true);
    }
    updateAppStatus(appName, 'loading');
    
    // 清理缓存并重新加载
    if (microAppConfigs.has(appName)) {
      const config = microAppConfigs.get(appName);
      if (config?.cacheKey) {
        // 清理缓存
        localStorage.removeItem(`micro_app_cache_${config.cacheKey}`);
      }
    }
    
    // 预加载最新资源
    preloadAppResources();
  };

  // 处理微应用消息
  const handleMessage = (event: any) => {
    console.log(`接收到微应用 ${appName} 消息:`, event);
    
    // 调用用户自定义消息处理
    onAppMessage?.(event);
    
    // 转发到全局事件总线
    globalEventBus.emit(AppEvents.MICRO_APP_MESSAGE, {
      appName,
      data: event,
      timestamp: Date.now()
    });
  };
  
  // 构建传递给微应用的props
  const buildMicroAppProps = () => {
    const version = getAppVersion(appName);
    
    return {
      // 全局状态
      globalState: {
        user,
        token,
        basePath: `/${appName}`,
        theme: window.__THEME__ || 'light',
        version,
        permissions: appConfig?.permissions || {},
        // 注入全局配置
        config: window.__APP_CONFIG__ || {}
      },
      // 工具方法
      utils: {
        navigate: (path: string) => {
          // 处理微应用内部路由跳转
          const fullPath = `/${appName}${path.startsWith('/') ? '' : '/'}${path}`;
          window.history.pushState(null, '', fullPath);
          
          // 通知其他微应用路由变化
          globalEventBus.emit(AppEvents.ROUTE_CHANGED, {
            path: fullPath,
            appName,
            timestamp: Date.now()
          });
        },
        showMessage: (type: 'success' | 'error' | 'warning' | 'info', content: string) => {
          // 集成消息提示
          globalEventBus.emit(AppEvents.SHOW_MESSAGE, { type, content });
        },
        // 提供事件总线
        eventBus: {
          on: globalEventBus.on.bind(globalEventBus),
          emit: globalEventBus.emit.bind(globalEventBus),
          sendToApp: globalEventBus.sendToApp.bind(globalEventBus)
        },
        // 动态加载资源
        loadResource: (url: string) => {
          return new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = url;
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
          });
        },
        // 上报性能数据
        reportPerformance: (data: any) => {
          addPerformanceMetric({
            ...data,
            appName
          });
        }
      },
      // 用户自定义props
      ...props
    };
  };

  // 构建无界框架配置
  const buildWujieConfig = () => {
    const config: any = {
      width: '100%',
      height: '100%',
      name: appConfig.name,
      url: appConfig.url,
      sync,
      alive,
      singleton,
      props: buildMicroAppProps(),
      onLoad,
      onError: handleError,
      onMessage: handleMessage,
      onBeforeLoad: handleLoadStart,
      onAfterLoad: handleLoad,
      onBeforeUnmount: () => {
        console.log(`微应用 ${appName} 即将卸载`);
        globalEventBus.emit(AppEvents.MICRO_APP_BEFORE_UNMOUNT, { appName });
      },
      onMounted: () => {
        console.log(`微应用 ${appName} 已挂载`);
        globalEventBus.emit(AppEvents.MICRO_APP_MOUNTED, { appName });
      }
    };

    // 降级配置
    if (degrade || !isSupportWebComponent()) {
      config.degrade = true;
      console.warn(`微应用 ${appName} 使用降级模式`);
    }

    // 沙箱配置
    config.sandBox = {
      // JS沙箱配置
      jsSandBox: true,
      // 严格样式隔离
      strictStyleIsolation: true,
      // 实验性样式隔离（适配新版Chrome）
      experimentalStyleIsolation: true,
      // 开启快照沙箱（适用于不支持proxy的环境）
      snapshotSandbox: !window.Proxy,
      // 开启代理沙箱
      proxySandbox: true,
      // 隔离全局事件
      isolatedGlobal: true,
      // 禁止访问父应用的全局变量
      disableSandbox: false
    };

    // 静态资源预加载配置
    if (appConfig.preloadAssets && appConfig.preloadAssets.length > 0) {
      config.preloadAssets = appConfig.preloadAssets;
    }

    // 自定义fetch配置
    config.fetch = {
      // 自定义请求处理
      handler: (url: string, options: RequestInit) => {
        // 记录请求
        console.log(`微应用 ${appName} 请求:`, url);
        
        // 注入认证信息
        const authHeaders = {
          'X-App-Name': appName,
          'X-App-Version': getAppVersion(appName),
          'X-Request-ID': Date.now().toString(36) + Math.random().toString(36).substr(2)
        };
        
        // 如果有token，注入Authorization头
        if (token) {
          authHeaders['Authorization'] = `Bearer ${token}`;
        }
        
        return window.fetch(url, {
          ...options,
          headers: {
            ...options?.headers,
            ...authHeaders
          }
        }).then(response => {
          // 处理401错误（token过期）
          if (response.status === 401) {
            globalEventBus.emit(AppEvents.AUTH_EXPIRED, { appName });
          }
          return response;
        });
      },
      // 自定义fetch过滤器
      filter: (url: string) => {
        // 可以过滤不需要处理的请求
        const excludePatterns = appConfig.fetchExcludePatterns || [];
        return !excludePatterns.some(pattern => url.includes(pattern));
      }
    };

    // 自定义生命周期钩子
    if (appConfig.lifeCycles) {
      Object.keys(appConfig.lifeCycles).forEach(hook => {
        config[hook] = appConfig.lifeCycles[hook];
      });
    }

    // 注入无界插件
    if (plugins.length > 0) {
      config.plugins = plugins;
    }

    return config;
  };
  
  // 渲染错误状态
  if (error) {
    return (
      <div className={`micro-app-container error ${className}`} ref={containerRef}>
        <Alert
          message="微应用加载失败"
          description={
            <Space direction="vertical" style={{ width: '100%' }}>
              <span>{error.message}</span>
              <Space>
                <Button 
                  type="primary" 
                  icon={<ReloadOutlined />} 
                  onClick={handleRetry}
                >
                  重试
                </Button>
                <Button 
                  onClick={() => {
                    // 降级处理，显示备用内容
                    globalEventBus.emit(AppEvents.MICRO_APP_FALLBACK, { appName });
                  }}
                >
                  显示备用内容
                </Button>
              </Space>
            </Space>
          }
          type="error"
          showIcon
        />
      </div>
    );
  }
  
  // 渲染加载状态
  if (loading && !fallback) {
    return (
      <div className={`micro-app-container loading ${className}`}>
        <Spin size="large" tip={`加载 ${appName}...`} />
      </div>
    );
  }
  
  // 渲染自定义fallback
  if (loading && fallback) {
    return (
      <div className={`micro-app-container ${className}`} ref={containerRef}>
        {fallback}
      </div>
    );
  }
  
  // 渲染Wujie微应用
  if (appConfig && !error) {
    const wujieConfig = buildWujieConfig();
    
    return (
      <div className={`micro-app-container ${className}`} ref={containerRef}>
        <WujieReact {...wujieConfig} />
        />
      </div>
    );
  }
  
  // 渲染开发调试信息（开发环境）
  if (process.env.NODE_ENV === 'development' && !loading && !error && !appConfig) {
    return (
      <div className={`micro-app-container ${className}`}>
        <Alert
          message="开发调试"
          description={`微应用 ${appName} 配置未加载`}
          type="info"
          showIcon
          action={
            <Tooltip title="刷新配置">
              <Button icon={<SettingOutlined />} onClick={handleRetry}>
                重新加载配置
              </Button>
            </Tooltip>
          }
        />
      </div>
    );
  }
  
  return null;
};

// 微应用容器的高阶组件，提供性能监控和错误恢复
const withMicroAppMonitoring = (WrappedComponent: React.ComponentType<MicroAppContainerProps>) => {
  return (props: MicroAppContainerProps) => {
    const { appName } = props;
    const [hasError, setHasError] = useState(false);
    const errorCountRef = useRef(0);
    const lastErrorTimeRef = useRef(0);
    
    // 错误边界处理
    const handleError = (error: Error) => {
      const now = Date.now();
      errorCountRef.current++;
      lastErrorTimeRef.current = now;
      setHasError(true);
      
      // 连续错误处理
      if (errorCountRef.current >= 3) {
        // 通知监控系统
        globalEventBus.emit(AppEvents.MICRO_APP_CRITICAL_ERROR, {
          appName,
          error: error.message,
          errorCount: errorCountRef.current,
          timestamp: now
        });
        
        // 重置错误计数
        setTimeout(() => {
          errorCountRef.current = 0;
        }, 60000); // 1分钟后重置
      }
      
      // 调用原始错误处理
      if (props.onError) {
        props.onError(error);
      }
    };
    
    // 自动重试
    useEffect(() => {
      if (hasError) {
        const retryTimer = setTimeout(() => {
          setHasError(false);
        }, 3000); // 3秒后自动重试
        
        return () => clearTimeout(retryTimer);
      }
    }, [hasError]);
    
    return (
      <WrappedComponent
        {...props}
        onError={handleError}
      />
    );
  };
};

// 导出增强版本的微应用容器
export const EnhancedMicroAppContainer = withMicroAppMonitoring(MicroAppContainer);


- **TypeScript配置接口**：
  ```typescript
  // 详细配置接口请参见8511行的MicroAppConfig定义
  ```

- **微应用配置实现（增强版）**：
  ```typescript
  // 微应用配置示例（在主应用中注册）
  import { MicroAppConfig } from './micro-frontend.types';
  import { getEnvConfig } from '../config/env';
  import { logger } from '../utils/logger';
  
  // 微应用性能监控装饰器
  const withPerformanceMonitoring = (config: MicroAppConfig): MicroAppConfig => {
    return {
      ...config,
      performanceTrack: true,
      beforeLoad: async () => {
        logger.info(`开始加载微应用: ${config.name}`);
        if (window.performance?.mark) {
          window.performance.mark(`${config.name}-load-start`);
        }
        // 调用原始的beforeLoad如果存在
        if (config.beforeLoad) {
          await config.beforeLoad();
        }
      },
      afterMount: () => {
        const loadTime = Date.now();
        logger.info(`微应用 ${config.name} 加载完成，耗时: ${loadTime}ms`);
        if (window.performance?.mark) {
          window.performance.mark(`${config.name}-load-end`);
          window.performance.measure(
            `${config.name}-load-time`,
            `${config.name}-load-start`,
            `${config.name}-load-end`
          );
        }
        // 调用原始的afterMount如果存在
        if (config.afterMount) {
          config.afterMount();
        }
      }
    };
  };
  
  // 获取微应用配置
  const getMicroAppConfigs = (): MicroAppConfig[] => {
    const envConfig = getEnvConfig();
    const baseUrl = envConfig.apiBaseUrl || window.location.origin;
    
    return [
      withPerformanceMonitoring({
        name: 'react-frontend-module',
        title: 'React前端模块',
        framework: 'react',
        description: '基于React的企业级前端模块',
        version: '1.0.0',
        url: import.meta.env.DEV 
          ? '//localhost:3005' 
          : `${baseUrl}/react-module`,
        entry: '/entry.html',
        activeRule: (location: Location) => {
          // 更灵活的激活规则，支持多路径匹配
          return location.pathname.startsWith('/react-module') || 
                 location.pathname.startsWith('/react-feature');
        },
        container: '#micro-app-container',
        sandbox: {
          strictStyleIsolation: true,
          experimentalStyleIsolation: true,
          // 允许访问主应用的特定全局变量
          looseSandbox: false
        },
        props: {
          basePath: '/react-module',
          theme: document.documentElement.getAttribute('data-theme') || 'light',
          // 动态获取认证信息
          getAuthInfo: () => {
            try {
              return window.bone?.getAuthState?.() || {};
            } catch (e) {
              logger.error('获取认证信息失败:', e);
              return {};
            }
          },
          // 配置信息
          config: {
            apiBase: `${baseUrl}/api`,
            enableAnalytics: true,
            maxUploadSize: 10 * 1024 * 1024, // 10MB
            featureFlags: {
              newDashboard: envConfig.features?.newDashboard || false,
              enhancedSearch: envConfig.features?.enhancedSearch || true
            }
          },
          // 全局事件总线引用
          eventBus: window.__bone_event_bus__
        },
        // 预加载配置
        prefetch: true,
        // 单例模式
        singular: true,
        // 加载超时设置
        timeout: 10000,
        // 降级配置
        fallback: {
          url: '/fallback/react-module',
          component: 'ReactModuleFallback'
        }
      }),
      // 其他微应用配置...
    ];
  };
  
  export const microAppConfigs = getMicroAppConfigs();
  
  // 动态注册微应用
  export const registerMicroApps = () => {
    try {
      // 这里可以引入 qiankun 或其他微前端框架进行注册
      logger.info('微应用配置初始化成功，共注册', microAppConfigs.length, '个微应用');
      return microAppConfigs;
    } catch (error) {
      logger.error('微应用配置初始化失败:', error);
      return [];
    }
  };
  ```

#### 5.1.2 微应用生命周期管理

- **微应用入口实现（增强版）**：
  ```typescript
  // src/main.tsx (微应用)
  import React from 'react';
  import ReactDOM from 'react-dom/client';
  import { createMemoryHistory } from 'history';
  import { BrowserRouter } from 'react-router-dom';
  import App from './App';
  import { setupApp } from './app-setup';
  import { logger } from './utils/logger';
  import { errorBoundary } from './utils/error-boundary';
  
  // 性能监控工具
  const performanceMonitor = {
    startTime: 0,
    start() {
      this.startTime = Date.now();
      if (window.performance?.mark) {
        window.performance.mark('micro-app-init-start');
      }
    },
    end() {
      const duration = Date.now() - this.startTime;
      logger.info(`微应用初始化耗时: ${duration}ms`);
      if (window.performance?.mark) {
        window.performance.mark('micro-app-init-end');
        window.performance.measure(
          'micro-app-init-time',
          'micro-app-init-start',
          'micro-app-init-end'
        );
      }
      return duration;
    }
  };
  
  let root: ReactDOM.Root | null = null;
  let memoryHistory: ReturnType<typeof createMemoryHistory> | null = null;
  let appUnmounted = false;
  
  // 清理资源函数
  const cleanupResources = () => {
    logger.info('清理微应用资源');
    
    // 清理事件监听器
    const cleanupFunctions = window.__micro_app_cleanup_functions || [];
    cleanupFunctions.forEach((fn: Function) => {
      try {
        fn();
      } catch (e) {
        logger.error('清理函数执行失败:', e);
      }
    });
    
    // 清理定时器
    const timers = window.__micro_app_timers || [];
    timers.forEach((timer: number) => {
      clearTimeout(timer);
      clearInterval(timer);
    });
    
    // 清理全局状态
    if (window.__POWERED_BY_QIANKUN__) {
      window.__micro_app_cleanup_functions = [];
      window.__micro_app_timers = [];
    }
  };
  
  // 安全的渲染函数
  const safeRender = errorBoundary((props: any = {}) => {
    const { container, basePath, theme, getAuthInfo, config } = props;
    
    // 获取认证信息
    const authInfo = typeof getAuthInfo === 'function' 
      ? getAuthInfo() 
      : { token: props.token, userInfo: props.userInfo };
    
    // 初始化内存路由
    memoryHistory = createMemoryHistory({
      initialEntries: [window.location.pathname.replace(basePath || '', '')],
      basename: basePath || '/'
    });
    
    // 设置全局配置
    setupApp({
      theme: theme || 'light',
      token: authInfo.token,
      userInfo: authInfo.userInfo,
      basePath,
      config: config || {}
    });
    
    // 获取挂载容器
    const rootElement = container 
      ? container.querySelector('#root') 
      : document.querySelector('#root');
    
    if (!rootElement) {
      throw new Error('未找到挂载容器');
    }
    
    if (!root) {
      root = ReactDOM.createRoot(rootElement);
    }
    
    // 渲染应用
    root.render(
      <React.StrictMode>
        <BrowserRouter history={memoryHistory}>
          <App />
        </BrowserRouter>
      </React.StrictMode>
    );
    
    logger.info('微应用渲染完成');
  });
  
  // 渲染应用
  const render = (props: any = {}) => {
    performanceMonitor.start();
    try {
      safeRender(props);
      return performanceMonitor.end();
    } catch (error) {
      logger.error('微应用渲染失败:', error);
      // 触发错误事件
      if (props.eventBus) {
        props.eventBus.emit('micro-app:error', { 
          appName: 'react-frontend-module', 
          error 
        });
      }
      // 显示降级UI
      const rootElement = (props.container || document).querySelector('#root');
      if (rootElement) {
        rootElement.innerHTML = `
          <div style="padding: 20px; text-align: center; background-color: #f5f5f5;">
            <h3>应用加载失败</h3>
            <p>${error instanceof Error ? error.message : '未知错误'}</p>
            <button onclick="window.location.reload()">重试</button>
          </div>
        `;
      }
      return -1;
    }
  };
  
  // 独立运行时
  if (!window.__POWERED_BY_QIANKUN__) {
    logger.info('微应用独立运行模式');
    render({
      basePath: '/',
      theme: 'light',
      config: {
        apiBase: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
      }
    });
  }
  
  // 微前端环境下导出生命周期钩子
  export const bootstrap = async () => {
    logger.info('React前端模块初始化');
    // 初始化全局清理函数数组
    if (window.__POWERED_BY_QIANKUN__) {
      window.__micro_app_cleanup_functions = [];
      window.__micro_app_timers = [];
    }
  };
  
  export const mount = async (props: any) => {
    logger.info('React前端模块挂载，接收参数:', Object.keys(props));
    appUnmounted = false;
    const loadTime = render(props);
    
    // 向主应用报告加载完成
    if (props.onMounted && typeof props.onMounted === 'function') {
      try {
        props.onMounted({ loadTime });
      } catch (e) {
        logger.error('报告挂载完成失败:', e);
      }
    }
  };
  
  export const unmount = async () => {
    logger.info('React前端模块卸载');
    appUnmounted = true;
    
    // 清理渲染根实例
    if (root) {
      root.unmount();
      root = null;
    }
    
    // 清理路由历史
    if (memoryHistory) {
      memoryHistory = null;
    }
    
    // 清理资源
    cleanupResources();
    
    logger.info('微应用卸载完成');
  };
  
  // 更新钩子（增强版）
  export const update = async (props: any) => {
    if (appUnmounted) {
      logger.warn('微应用已卸载，跳过更新');
      return;
    }
    
    logger.info('React前端模块更新，接收参数:', Object.keys(props));
    try {
      // 动态更新配置
      if (props.config) {
        setupApp({ config: props.config });
      }
      
      // 更新主题
      if (props.theme) {
        document.documentElement.setAttribute('data-theme', props.theme);
      }
      
      // 重新渲染
      render(props);
    } catch (error) {
      logger.error('微应用更新失败:', error);
    }
  };
  
  // 导出额外的工具方法供主应用调用
  export const utils = {
    // 获取微应用状态
    getState: () => ({
      mounted: !appUnmounted,
      version: '1.0.0'
    }),
    
    // 手动刷新
    refresh: () => {
      if (!appUnmounted) {
        logger.info('微应用手动刷新');
        // 这里可以实现不重新加载页面的刷新逻辑
      }
    }
  };
  ```

#### 5.1.3 微前端通信机制

- **增强的事件总线实现**：
  ```typescript
  // src/utils/event-bus.ts
  import { logger } from './logger';
  
  // 事件监听器类型
  type EventCallback = (...args: any[]) => void;
  
  // 事件总线配置
  interface EventBusOptions {
    debug?: boolean;
    maxListeners?: number;
    enableHistory?: boolean;
    historySize?: number;
  }
  
  // 事件历史记录项
  interface EventHistoryItem {
    event: string;
    args: any[];
    timestamp: number;
    source?: string;
  }
  
  class EventBus {
    private events: Map<string, Set<EventCallback>> = new Map();
    private eventHistory: EventHistoryItem[] = [];
    private options: Required<EventBusOptions>;
    private listenerCounts: Map<string, number> = new Map();
    
    constructor(options: EventBusOptions = {}) {
      this.options = {
        debug: options.debug || false,
        maxListeners: options.maxListeners || 100,
        enableHistory: options.enableHistory || false,
        historySize: options.historySize || 100
      };
      
      // 确保在微前端环境中可以跨应用访问
      if (typeof window !== 'undefined') {
        window.__bone_event_bus__ = this;
      }
    }
    
    // 订阅事件
    on(event: string, callback: EventCallback, once: boolean = false): () => void {
      if (typeof callback !== 'function') {
        throw new Error('Callback must be a function');
      }
      
      // 初始化事件集合
      if (!this.events.has(event)) {
        this.events.set(event, new Set());
        this.listenerCounts.set(event, 0);
      }
      
      // 检查监听器数量限制
      const count = this.listenerCounts.get(event) || 0;
      if (count >= this.options.maxListeners) {
        logger.warn(`Event "${event}" has reached max listeners limit (${this.options.maxListeners})`);
      }
      
      // 创建包装后的回调（支持一次性监听）
      const wrappedCallback: EventCallback = (...args) => {
        try {
          callback(...args);
          if (once) {
            this.off(event, wrappedCallback);
          }
        } catch (error) {
          logger.error(`Error in event handler for "${event}":`, error, { args });
        }
      };
      
      // 添加到事件集合
      this.events.get(event)!.add(wrappedCallback);
      this.listenerCounts.set(event, count + 1);
      
      if (this.options.debug) {
        logger.debug(`Registered listener for event "${event}"`, { listenerCount: count + 1 });
      }
      
      // 返回取消订阅函数
      return () => this.off(event, wrappedCallback);
    }
    
    // 一次性订阅事件
    once(event: string, callback: EventCallback): () => void {
      return this.on(event, callback, true);
    }
    
    // 发布事件
    emit(event: string, ...args: any[]): void {
      if (this.options.debug) {
        logger.debug(`Emitting event "${event}"`, { argsLength: args.length });
      }
      
      // 记录事件历史
      if (this.options.enableHistory) {
        this.recordEventHistory(event, args);
      }
      
      // 执行所有监听器
      if (this.events.has(event)) {
        const callbacks = this.events.get(event)!;
        // 转换为数组以防止回调过程中集合被修改
        [...callbacks].forEach(callback => {
          try {
            // 使用setTimeout确保异步执行，避免阻塞
            setTimeout(() => callback(...args), 0);
          } catch (error) {
            logger.error(`Error executing event handler for "${event}":`, error);
          }
        });
      }
    }
    
    // 取消订阅
    off(event?: string, callback?: EventCallback): void {
      // 如果没有指定事件，清理所有事件
      if (!event) {
        this.events.clear();
        this.listenerCounts.clear();
        if (this.options.debug) {
          logger.debug('Cleared all event listeners');
        }
        return;
      }
      
      // 如果没有指定回调，清理指定事件的所有监听器
      if (!callback && this.events.has(event)) {
        const count = this.listenerCounts.get(event) || 0;
        this.events.delete(event);
        this.listenerCounts.delete(event);
        if (this.options.debug) {
          logger.debug(`Cleared all ${count} listeners for event "${event}"`);
        }
        return;
      }
      
      // 移除特定回调
      if (callback && this.events.has(event)) {
        const callbacks = this.events.get(event)!;
        if (callbacks.delete(callback)) {
          const count = (this.listenerCounts.get(event) || 1) - 1;
          this.listenerCounts.set(event, count);
          if (count === 0) {
            this.events.delete(event);
            this.listenerCounts.delete(event);
          }
          if (this.options.debug) {
            logger.debug(`Removed listener for event "${event}"`, { remainingCount: count });
          }
        }
      }
    }
    
    // 获取事件监听器数量
    listenerCount(event?: string): number {
      if (!event) {
        return Array.from(this.listenerCounts.values()).reduce((sum, count) => sum + count, 0);
      }
      return this.listenerCounts.get(event) || 0;
    }
    
    // 获取所有注册的事件名称
    eventNames(): string[] {
      return Array.from(this.events.keys());
    }
    
    // 记录事件历史
    private recordEventHistory(event: string, args: any[]): void {
      const historyItem: EventHistoryItem = {
        event,
        args,
        timestamp: Date.now(),
        source: this.getEventSource()
      };
      
      this.eventHistory.push(historyItem);
      
      // 保持历史记录大小限制
      if (this.eventHistory.length > this.options.historySize) {
        this.eventHistory.shift();
      }
    }
    
    // 获取事件源（用于调试）
    private getEventSource(): string {
      try {
        // 通过错误栈获取调用位置
        const error = new Error();
        const stack = error.stack || '';
        const callerLine = stack.split('\n')[3] || '';
        return callerLine.trim();
      } catch (e) {
        return 'unknown';
      }
    }
    
    // 获取事件历史（用于调试和重放）
    getHistory(event?: string): EventHistoryItem[] {
      if (!event) {
        return [...this.eventHistory];
      }
      return this.eventHistory.filter(item => item.event === event);
    }
    
    // 清除事件历史
    clearHistory(): void {
      this.eventHistory = [];
      if (this.options.debug) {
        logger.debug('Cleared event history');
      }
    }
    
    // 重放历史事件
    replayHistory(delayMs: number = 100): Promise<void> {
      return new Promise(resolve => {
        if (this.eventHistory.length === 0) {
          resolve();
          return;
        }
        
        let index = 0;
        const replayNext = () => {
          if (index >= this.eventHistory.length) {
            resolve();
            return;
          }
          
          const item = this.eventHistory[index];
          this.emit(item.event, ...item.args);
          index++;
          
          setTimeout(replayNext, delayMs);
        };
        
        replayNext();
      });
    }
  }
  
  // 创建全局单例实例
  export const eventBus = new EventBus({
    debug: import.meta.env.DEV,
    enableHistory: import.meta.env.DEV
  });
  
  // 类型定义
  declare global {
    interface Window {
      __bone_event_bus__: EventBus;
    }
  }
  
  // 事件常量
  export const EVENT_TYPES = {
    AUTH: {
      LOGIN: 'auth:login',
      LOGOUT: 'auth:logout',
      TOKEN_REFRESH: 'auth:token-refresh'
    },
    APP: {
      MOUNTED: 'app:mounted',
      UNMOUNTED: 'app:unmounted',
      ERROR: 'app:error'
    },
    MICRO_APP: {
      MOUNTED: 'micro-app:mounted',
      UNMOUNTED: 'micro-app:unmounted',
      ERROR: 'micro-app:error',
      UPDATE: 'micro-app:update'
    },
    USER: {
      UPDATED: 'user:updated',
      PERMISSIONS_CHANGED: 'user:permissions-changed'
    },
    THEME: {
      CHANGED: 'theme:changed'
    }
  };
  ```

- **全局状态共享**：
  ```typescript
  // src/stores/global.ts (在主应用中)
  import { create } from 'zustand';
  import { createJSONStorage, persist } from 'zustand/middleware';
  
  interface GlobalState {
    userInfo: Record<string, unknown> | null;
    token: string | null;
    theme: 'light' | 'dark';
    setUserInfo: (userInfo: Record<string, unknown>) => void;
    setToken: (token: string) => void;
    setTheme: (theme: 'light' | 'dark') => void;
  }
  
  export const useGlobalStore = create<GlobalState>()(
    persist(
      (set) => ({
        userInfo: null,
        token: null,
        theme: 'light',
        setUserInfo: (userInfo) => set({ userInfo }),
        setToken: (token) => set({ token }),
        setTheme: (theme) => set({ theme })
      }),
      {
        name: 'global-storage',
        storage: createJSONStorage(() => sessionStorage)
      }
    )
  );
  ```

### 5.2 构建和部署流程

#### 5.2.1 构建配置

- **Vite构建配置**：
  ```typescript
  // vite.config.ts
  import { defineConfig, loadEnv } from 'vite';
  import react from '@vitejs/plugin-react';
  import { resolve } from 'path';
  import tsconfigPaths from 'vite-tsconfig-paths';
  import { visualizer } from 'rollup-plugin-visualizer';
  
  export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '');
    
    return {
      plugins: [
        react(),
        tsconfigPaths(),
        mode === 'production' && visualizer({
          filename: 'stats.html',
          gzipSize: true
        })
      ],
      resolve: {
        alias: {
          '@': resolve(__dirname, './src')
        }
      },
      build: {
        outDir: 'dist',
        minify: mode === 'production' ? 'terser' : false,
        sourcemap: mode !== 'production',
        rollupOptions: {
          output: {
            manualChunks: {
              vendor: ['react', 'react-dom', 'react-router-dom'],
              antd: ['antd'],
              utils: ['lodash', 'date-fns'],
              state: ['zustand']
            }
          }
        },
        chunkSizeWarningLimit: 1000
      },
      server: {
        port: 3005,
        headers: {
          'Access-Control-Allow-Origin': '*', // 支持跨域，微前端必须
        },
        proxy: {
          '/api': {
            target: env.VITE_API_BASE_URL || 'http://localhost:8080',
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, '')
          }
        }
      }
    };
  });
  ```

- **package.json脚本**：
  ```json
  {
    "scripts": {
      "dev": "vite",
      "dev:micro": "vite --mode micro",
      "build": "tsc && vite build",
      "build:micro": "tsc && vite build --mode micro",
      "type-check": "tsc --noEmit",
      "lint": "eslint . --ext ts,tsx --report-unused-disable-directives --max-warnings 0",
      "test": "jest",
      "test:coverage": "jest --coverage",
      "preview": "vite preview",
      "preview:micro": "vite preview --mode micro",
      "prepare": "husky install"
    }
  }
  ```

#### 5.2.2 CI/CD配置

- **增强的GitHub Actions工作流**：
  ```yaml
  # .github/workflows/ci-cd.yml
  name: React Frontend CI/CD Pipeline
  
  on:
    push:
      branches: [main, develop, feature/*, bugfix/*]
      tags: ['v*.*.*']
    pull_request:
      branches: [main, develop]
    # 定时触发代码质量扫描
    schedule:
      - cron: '0 0 * * 1' # 每周一执行
  
  # 环境变量配置
  env:
    NODE_VERSION: '18'
    NPM_CONFIG_CACHE: .npm
    
  # 缓存配置
  defaults:
    run:
      working-directory: .
  
  # 定义作业间共享的输出变量
  jobs:
    # 代码检出和准备
    prepare:
      runs-on: ubuntu-latest
      outputs:
        PR_NUMBER: ${{ steps.pr-number.outputs.number || '0' }}
        VERSION: ${{ steps.version.outputs.version || '0.0.0' }}
      steps:
        - name: Checkout code
          uses: actions/checkout@v4
          with:
            fetch-depth: 0 # 获取完整历史以便进行版本计算
        
        - name: Determine PR number
          id: pr-number
          if: github.event_name == 'pull_request'
          run: echo "number=${{ github.event.pull_request.number }}" >> $GITHUB_OUTPUT
        
        - name: Extract version
          id: version
          run: |
            if [[ "${{ github.ref_type }}" == "tag" && "${{ github.ref }}" =~ ^refs/tags/v(.+) ]]; then
              echo "version=${BASH_REMATCH[1]}" >> $GITHUB_OUTPUT
            elif [[ "${{ github.event_name }}" == "pull_request" ]]; then
              echo "version=pr-${{ github.event.pull_request.number }}" >> $GITHUB_OUTPUT
            else
              BRANCH_NAME=$(echo "${{ github.ref }}" | sed 's|refs/heads/||' | tr '/' '-')
              echo "version=${BRANCH_NAME}-${{ github.sha::7 }}" >> $GITHUB_OUTPUT
            fi
  
    # 依赖缓存和安装
    setup:
      runs-on: ubuntu-latest
      steps:
        - name: Checkout code
          uses: actions/checkout@v4
        
        - name: Setup Node.js
          uses: actions/setup-node@v3
          with:
            node-version: ${{ env.NODE_VERSION }}
            cache: 'npm'
        
        - name: Cache dependencies
          uses: actions/cache@v3
          with:
            path: .npm
            key: ${{ runner.os }}-node-${{ hashFiles('**/package-lock.json') }}
            restore-keys: |
              ${{ runner.os }}-node-
        
        - name: Install dependencies
          run: |
            npm ci
            echo "NPM installed successfully"
  
    # 代码质量检查
    code-quality:
      needs: [prepare, setup]
      runs-on: ubuntu-latest
      steps:
        - name: Checkout code
          uses: actions/checkout@v4
        
        - name: Setup Node.js
          uses: actions/setup-node@v3
          with:
            node-version: ${{ env.NODE_VERSION }}
            cache: 'npm'
        
        - name: Restore dependencies
          uses: actions/cache@v3
          with:
            path: .npm
            key: ${{ runner.os }}-node-${{ hashFiles('**/package-lock.json') }}
        
        - name: Install dependencies
          run: npm ci
        
        - name: TypeScript Check
          run: npm run type-check
        
        - name: Lint Check
          run: npm run lint
        
        - name: ESLint Report
          uses: github/codeql-action/upload-sarif@v2
          if: always()
          with:
            sarif_file: ./eslint-report.sarif
          continue-on-error: true
        
        - name: Code formatting check
          run: npx prettier --check .
        
        - name: Dependency vulnerability scan
          run: npm audit --audit-level=high
          continue-on-error: true
        
        # 代码覆盖率报告
        - name: Run tests with coverage
          run: npm run test:coverage
        
        - name: Upload coverage to Codecov
          uses: codecov/codecov-action@v3
          with:
            token: ${{ secrets.CODECOV_TOKEN }}
            directory: ./coverage
            flags: unittests
            name: codecov-${{ github.run_id }}
            fail_ci_if_error: true
        
        - name: Upload coverage artifact
          uses: actions/upload-artifact@v3
          with:
            name: coverage-report-${{ needs.prepare.outputs.VERSION }}
            path: coverage/
  
    # 构建作业
    build:
      needs: [prepare, code-quality]
      runs-on: ubuntu-latest
      steps:
        - name: Checkout code
          uses: actions/checkout@v4
        
        - name: Setup Node.js
          uses: actions/setup-node@v3
          with:
            node-version: ${{ env.NODE_VERSION }}
            cache: 'npm'
        
        - name: Restore dependencies
          uses: actions/cache@v3
          with:
            path: .npm
            key: ${{ runner.os }}-node-${{ hashFiles('**/package-lock.json') }}
        
        - name: Install dependencies
          run: npm ci
        
        # 构建独立版本
        - name: Build standalone version
          run: npm run build
        
        # 构建微前端版本
        - name: Build micro-frontend version
          run: npm run build:micro
        
        # 构建分析报告
        - name: Generate bundle analysis
          run: npx vite-bundle-visualizer --template sunburst --out fileName='bundle-report.html'
        
        # 版本信息注入
        - name: Inject version info
          run: |
            echo "VERSION=${{ needs.prepare.outputs.VERSION }}" > version.txt
            echo "BUILD_DATE=$(date -u '+%Y-%m-%d %H:%M:%S UTC')" >> version.txt
            cp version.txt dist/
        
        # 上传构建产物
        - name: Upload build artifacts
          uses: actions/upload-artifact@v3
          with:
            name: build-${{ needs.prepare.outputs.VERSION }}
            path: |
              dist/
              bundle-report.html
            retention-days: 14
  
    # 安全扫描作业
    security-scan:
      needs: build
      runs-on: ubuntu-latest
      if: github.event_name != 'pull_request'
      steps:
        - name: Download build artifacts
          uses: actions/download-artifact@v3
          with:
            name: build-${{ needs.prepare.outputs.VERSION }}
            path: dist/
        
        # SAST扫描
        - name: Run SAST scan
          uses: aquasecurity/trivy-action@master
          with:
            scan-type: 'fs'
            format: 'sarif'
            output: 'trivy-results.sarif'
            severity: 'CRITICAL,HIGH'
            ignore-unfixed: true
          continue-on-error: true
        
        - name: Upload SAST results
          uses: github/codeql-action/upload-sarif@v2
          if: always()
          with:
            sarif_file: 'trivy-results.sarif'
          continue-on-error: true
        
        # 敏感信息扫描
        - name: Scan for secrets
          uses: zricethezav/gitleaks-action@master
          continue-on-error: true
  
    # 部署到开发环境
    deploy-dev:
      if: github.ref == 'refs/heads/develop' || github.event_name == 'pull_request'
      needs: [prepare, build, security-scan]
      runs-on: ubuntu-latest
      environment: development
      steps:
        - name: Download build artifacts
          uses: actions/download-artifact@v3
          with:
            name: build-${{ needs.prepare.outputs.VERSION }}
            path: dist/
        
        # 部署前验证
        - name: Verify build integrity
          run: |
            if [ ! -f "dist/index.html" ]; then
              echo "构建产物不完整，缺少index.html"
              exit 1
            fi
            echo "构建产物验证通过"
        
        # 部署到开发环境
        - name: Deploy to development environment
          uses: easingthemes/ssh-deploy@v2
          env:
            SSH_PRIVATE_KEY: ${{ secrets.SSH_PRIVATE_KEY }}
            ARGS: '-rltgoDzvO --delete --exclude=".git" --exclude="node_modules"'
            SOURCE: 'dist/'
            REMOTE_HOST: ${{ secrets.DEV_HOST }}
            REMOTE_USER: ${{ secrets.DEV_USER }}
            TARGET: ${{ secrets.DEV_TARGET_DIR }}
            EXTRA_ARGS: '-avz --progress'
        
        # 部署后通知
        - name: Notify deployment
          uses: rtCamp/action-slack-notify@v2
          env:
            SLACK_WEBHOOK: ${{ secrets.SLACK_WEBHOOK }}
            SLACK_CHANNEL: dev-deployments
            SLACK_TITLE: "开发环境部署成功"
            SLACK_MESSAGE: "版本: ${{ needs.prepare.outputs.VERSION }}\n分支: ${{ github.ref_name }}\n提交: ${{ github.sha }}"
            SLACK_COLOR: '#28a745'
          continue-on-error: true
  
    # 部署到测试环境 (用于PR预览)
    deploy-staging:
      if: github.event_name == 'pull_request'
      needs: [prepare, build]
      runs-on: ubuntu-latest
      environment:
        name: staging
        url: ${{ steps.deploy-preview.outputs.preview_url }}
      steps:
        - name: Download build artifacts
          uses: actions/download-artifact@v3
          with:
            name: build-${{ needs.prepare.outputs.VERSION }}
            path: dist/
        
        - name: Deploy to staging environment
          id: deploy-preview
          run: |
            # 这里可以使用预览环境部署工具，如Netlify、Vercel等
            # 或者部署到专用的测试服务器
            echo "部署到测试环境: pr-${{ github.event.pull_request.number }}"
            # 模拟预览URL输出
            echo "preview_url=https://preview-${{ github.event.pull_request.number }}.bone-staging.com" >> $GITHUB_OUTPUT
        
        # 更新PR状态
        - name: Update PR with preview link
          uses: actions/github-script@v6
          with:
            github-token: ${{ secrets.GITHUB_TOKEN }}
            script: |
              const { data: comment } = await github.rest.issues.createComment({
                owner: context.repo.owner,
                repo: context.repo.repo,
                issue_number: ${{ github.event.pull_request.number }},
                body: `:rocket: 预览环境已部署！\n\n访问链接: ${{ steps.deploy-preview.outputs.preview_url }}`
              });
  
    # 部署到生产环境
    deploy-prod:
      if: startsWith(github.ref, 'refs/tags/v')
      needs: [prepare, build, security-scan]
      runs-on: ubuntu-latest
      environment:
        name: production
        url: ${{ secrets.PROD_BASE_URL }}
      # 要求手动批准部署
      concurrency: production
      steps:
        - name: Download build artifacts
          uses: actions/download-artifact@v3
          with:
            name: build-${{ needs.prepare.outputs.VERSION }}
            path: dist/
        
        # 构建验证
        - name: Validate production build
          run: |
            # 运行关键指标检查
            echo "验证生产构建..."
            # 可以添加构建大小检查、必要文件存在性检查等
        
        # 蓝绿部署
        - name: Deploy to blue environment
          id: blue-deploy
          run: |
            # 部署到非活动环境（蓝/绿）
            CURRENT_ENV=$(ssh ${{ secrets.PROD_USER }}@${{ secrets.PROD_HOST }} 'cat /var/www/current_env')
            TARGET_ENV=$([ "$CURRENT_ENV" = "green" ] && echo "blue" || echo "green")
            TARGET_DIR="${{ secrets.PROD_TARGET_DIR }}/$TARGET_ENV"
            
            echo "部署到目标环境: $TARGET_ENV"
            echo "target_env=$TARGET_ENV" >> $GITHUB_OUTPUT
            
            # 使用rsync部署到目标环境目录
            rsync -rltgoDzvO --delete -e "ssh -i ${{ secrets.SSH_PRIVATE_KEY_PATH }}" \
              --exclude=".git" --exclude="node_modules" \
              dist/ ${{ secrets.PROD_USER }}@${{ secrets.PROD_HOST }}:$TARGET_DIR
        
        # 健康检查
        - name: Health check on new deployment
          run: |
            # 对新部署的环境进行健康检查
            # 这里应该有实际的健康检查命令，例如curl检查API或网页响应
            echo "正在检查 ${{ steps.blue-deploy.outputs.target_env }} 环境健康状态..."
            # 模拟健康检查
            sleep 5
            echo "健康检查通过"
        
        # 切换流量
        - name: Switch traffic to new environment
          run: |
            # 更新符号链接或负载均衡器配置，将流量切换到新环境
            ssh ${{ secrets.PROD_USER }}@${{ secrets.PROD_HOST }} \
              "echo ${{ steps.blue-deploy.outputs.target_env }} > /var/www/current_env && \
               ln -sfT /var/www/${{ steps.blue-deploy.outputs.target_env }} /var/www/current"
            
            echo "流量已切换到 ${{ steps.blue-deploy.outputs.target_env }} 环境"
        
        # 部署后监控
        - name: Verify production deployment
          run: |
            # 再次验证生产环境的可用性
            echo "验证生产环境部署..."
            # 可以添加更复杂的验证逻辑
        
        # 部署后通知
        - name: Send deployment notification
          uses: rtCamp/action-slack-notify@v2
          env:
            SLACK_WEBHOOK: ${{ secrets.SLACK_WEBHOOK }}
            SLACK_CHANNEL: prod-deployments
            SLACK_TITLE: "生产环境部署成功"
            SLACK_MESSAGE: "版本: ${{ needs.prepare.outputs.VERSION }}\n部署时间: $(date)\n部署环境: ${{ steps.blue-deploy.outputs.target_env }}"
            SLACK_COLOR: '#008000'
          continue-on-error: true
        
        # 清理旧部署（可选）
        - name: Clean up old deployment
          run: |
            # 清理旧的非活动环境
            OLD_ENV=$([ "${{ steps.blue-deploy.outputs.target_env }}" = "blue" ] && echo "green" || echo "blue")
            ssh ${{ secrets.PROD_USER }}@${{ secrets.PROD_HOST }} \
              "if [ -d '/var/www/$OLD_ENV' ]; then rm -rf /var/www/$OLD_ENV; fi"
            echo "已清理旧环境: $OLD_ENV"
          continue-on-error: true
  ```

#### 5.2.3 环境配置管理

- **增强的环境变量配置管理**：
  ```typescript
  // src/config/env.ts
  
  /**
   * 环境配置接口定义
   */
  export interface EnvConfig {
    // 应用基础配置
    API_BASE_URL: string;
    CDN_URL: string;
    APP_NAME: string;
    APP_VERSION: string;
    BUILD_DATE: string;
    
    // 环境标识
    IS_PRODUCTION: boolean;
    IS_DEVELOPMENT: boolean;
    IS_TEST: boolean;
    IS_MICRO_APP: boolean;
    
    // 监控配置
    SENTRY_DSN?: string;
    PERFORMANCE_MONITORING_ENABLED: boolean;
    LOG_LEVEL: 'error' | 'warn' | 'info' | 'debug' | 'trace';
    
    // 特性开关
    FEATURE_FLAGS: Record<string, boolean> & {
      DARK_MODE: boolean;
      NEW_DASHBOARD: boolean;
      NEW_AUTH_FLOW: boolean;
      MICRO_FRONTEND: boolean;
      MOCK_API: boolean;
      CACHING_ENABLED: boolean;
    };
    
    // 微前端配置
    MICRO_FRONTEND_CONFIG: {
      ENABLED: boolean;
      CONTAINER_SELECTOR: string;
      REMOTE_TIMEOUT: number;
      RETRY_COUNT: number;
      POLLING_INTERVAL?: number;
    };
    
    // 缓存配置
    CACHE_CONFIG: {
      API_CACHE_TTL: number;
      MEMORY_CACHE_SIZE: number;
      ENABLE_LOCAL_STORAGE: boolean;
      ENABLE_SESSION_STORAGE: boolean;
    };
    
    // 安全配置
    SECURITY_CONFIG: {
      CSRF_PROTECTION: boolean;
      CORS_ENABLED: boolean;
      ALLOWED_ORIGINS: string[];
    };
  }
  
  /**
   * 从构建时注入的版本信息获取版本
   */
  const getAppVersion = (): string => {
    try {
      // 尝试从构建时生成的版本文件获取
      // 注意：这个文件需要在构建过程中生成
      const versionMeta = document.querySelector('meta[name="app-version"]');
      if (versionMeta) {
        return versionMeta.getAttribute('content') || '1.0.0';
      }
      
      // 回退到环境变量或默认值
      return import.meta.env.VITE_APP_VERSION || '1.0.0';
    } catch (error) {
      console.warn('Failed to get app version:', error);
      return '1.0.0';
    }
  };
  
  /**
   * 获取构建日期
   */
  const getBuildDate = (): string => {
    try {
      const dateMeta = document.querySelector('meta[name="build-date"]');
      if (dateMeta) {
        return dateMeta.getAttribute('content') || new Date().toISOString();
      }
      
      return import.meta.env.VITE_BUILD_DATE || new Date().toISOString();
    } catch {
      return new Date().toISOString();
    }
  };
  
  /**
   * 验证环境配置的完整性
   */
  const validateConfig = (config: Partial<EnvConfig>): asserts config is EnvConfig => {
    const requiredFields: (keyof EnvConfig)[] = [
      'API_BASE_URL',
      'APP_NAME', 
      'APP_VERSION',
      'IS_PRODUCTION',
      'IS_MICRO_APP'
    ];
    
    // 检查必需字段
    for (const field of requiredFields) {
      if (config[field] === undefined || config[field] === null) {
        throw new Error(`Missing required environment configuration: ${field}`);
      }
    }
    
    // 验证API URL格式
    if (typeof config.API_BASE_URL === 'string') {
      try {
        new URL(config.API_BASE_URL);
      } catch {
        throw new Error(`Invalid API URL format: ${config.API_BASE_URL}`);
      }
    }
    
    // 验证日志级别
    const validLogLevels = ['error', 'warn', 'info', 'debug', 'trace'];
    if (!validLogLevels.includes(config.LOG_LEVEL as string)) {
      console.warn(`Invalid log level: ${config.LOG_LEVEL}, defaulting to 'info'`);
      (config as EnvConfig).LOG_LEVEL = 'info';
    }
  };
  
  /**
   * 从环境变量加载特性开关
   */
  const loadFeatureFlags = (): EnvConfig['FEATURE_FLAGS'] => {
    // 基础特性开关配置
    const baseFlags: EnvConfig['FEATURE_FLAGS'] = {
      DARK_MODE: import.meta.env.VITE_FEATURE_DARK_MODE === 'true',
      NEW_DASHBOARD: import.meta.env.VITE_FEATURE_NEW_DASHBOARD === 'true',
      NEW_AUTH_FLOW: import.meta.env.VITE_FEATURE_NEW_AUTH_FLOW === 'true',
      MICRO_FRONTEND: import.meta.env.VITE_FEATURE_MICRO_FRONTEND === 'true',
      MOCK_API: import.meta.env.VITE_FEATURE_MOCK_API === 'true',
      CACHING_ENABLED: import.meta.env.VITE_FEATURE_CACHING_ENABLED !== 'false',
    };
    
    // 尝试从环境变量加载额外的特性开关
    // 格式: VITE_FEATURE_* 或 VITE_FLAG_*
    Object.keys(import.meta.env).forEach(key => {
      if (key.startsWith('VITE_FEATURE_') || key.startsWith('VITE_FLAG_')) {
        const flagName = key.replace(/^VITE_(FEATURE_|FLAG_)/, '').toLowerCase();
        const camelCaseName = flagName.replace(/_([a-z])/g, (g) => g[1].toUpperCase());
        (baseFlags as Record<string, boolean>)[camelCaseName] = import.meta.env[key] === 'true';
      }
    });
    
    return baseFlags;
  };
  
  /**
   * 获取默认配置
   */
  const getDefaultConfig = (): Partial<EnvConfig> => {
    const isProduction = import.meta.env.PROD;
    const isDevelopment = import.meta.env.DEV;
    const isTest = import.meta.env.MODE === 'test';
    const isMicroApp = import.meta.env.MODE === 'micro' || 
                      (typeof window !== 'undefined' && !!window.__POWERED_BY_QIANKUN__);
    
    return {
      API_BASE_URL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
      CDN_URL: import.meta.env.VITE_CDN_URL || '/static',
      APP_NAME: import.meta.env.VITE_APP_NAME || 'React Frontend Module',
      APP_VERSION: getAppVersion(),
      BUILD_DATE: getBuildDate(),
      IS_PRODUCTION: isProduction,
      IS_DEVELOPMENT: isDevelopment,
      IS_TEST: isTest,
      IS_MICRO_APP: isMicroApp,
      SENTRY_DSN: import.meta.env.VITE_SENTRY_DSN,
      PERFORMANCE_MONITORING_ENABLED: import.meta.env.VITE_PERFORMANCE_ENABLED !== 'false',
      LOG_LEVEL: (import.meta.env.VITE_LOG_LEVEL as EnvConfig['LOG_LEVEL']) || 
                (isDevelopment ? 'debug' : 'warn'),
      FEATURE_FLAGS: loadFeatureFlags(),
      
      MICRO_FRONTEND_CONFIG: {
        ENABLED: isMicroApp || (import.meta.env.VITE_MF_ENABLED === 'true'),
        CONTAINER_SELECTOR: import.meta.env.VITE_MF_CONTAINER_SELECTOR || '#root-micro',
        REMOTE_TIMEOUT: parseInt(import.meta.env.VITE_MF_TIMEOUT || '30000', 10),
        RETRY_COUNT: parseInt(import.meta.env.VITE_MF_RETRY_COUNT || '3', 10),
        POLLING_INTERVAL: parseInt(import.meta.env.VITE_MF_POLLING_INTERVAL || '0', 10),
      },
      
      CACHE_CONFIG: {
        API_CACHE_TTL: parseInt(import.meta.env.VITE_CACHE_TTL || (isProduction ? '300000' : '60000'), 10),
        MEMORY_CACHE_SIZE: parseInt(import.meta.env.VITE_CACHE_SIZE || '100', 10),
        ENABLE_LOCAL_STORAGE: import.meta.env.VITE_ENABLE_LOCAL_STORAGE !== 'false',
        ENABLE_SESSION_STORAGE: import.meta.env.VITE_ENABLE_SESSION_STORAGE !== 'false',
      },
      
      SECURITY_CONFIG: {
        CSRF_PROTECTION: import.meta.env.VITE_CSRF_PROTECTION !== 'false',
        CORS_ENABLED: import.meta.env.VITE_CORS_ENABLED !== 'false',
        ALLOWED_ORIGINS: (import.meta.env.VITE_ALLOWED_ORIGINS || 
                        (isDevelopment ? '*' : import.meta.env.VITE_API_BASE_URL || ''))
                        .split(',').map(origin => origin.trim()),
      },
    };
  };
  
  /**
   * 深度合并配置对象
   */
  const deepMerge = <T extends Record<string, any>>(target: T, source: Partial<T>): T => {
    const output = { ...target };
    
    if (!source || typeof source !== 'object') {
      return output;
    }
    
    Object.keys(source).forEach(key => {
      const sourceValue = source[key];
      const targetValue = target[key];
      
      // 如果源值和目标值都是对象，递归合并
      if (sourceValue && typeof sourceValue === 'object' && 
          targetValue && typeof targetValue === 'object' && 
          !Array.isArray(sourceValue) && !Array.isArray(targetValue)) {
        output[key] = deepMerge(targetValue, sourceValue);
      } 
      // 只覆盖非undefined的值
      else if (sourceValue !== undefined) {
        output[key] = sourceValue;
      }
    });
    
    return output;
  };
  
  /**
   * 从远程配置服务获取运行时配置
   */
  export const fetchRemoteConfig = async (): Promise<Partial<EnvConfig> | null> => {
    try {
      // 检查是否在浏览器环境
      if (typeof window === 'undefined') {
        return null;
      }
      
      const configUrl = import.meta.env.VITE_CONFIG_URL || 
                       `${window.location.origin}/api/config`;
      
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 3000); // 3秒超时
      
      const response = await fetch(configUrl, {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
          'Cache-Control': 'no-cache',
        },
        signal: controller.signal,
      });
      
      clearTimeout(timeoutId);
      
      if (!response.ok) {
        throw new Error(`Failed to fetch remote config: ${response.status}`);
      }
      
      const data = await response.json();
      return data;
    } catch (error) {
      console.warn('Failed to fetch remote config, using default config:', error);
      return null;
    }
  };
  
  /**
   * 从本地存储获取配置（用于持久化用户偏好设置）
   */
  const getStoredConfig = (): Partial<EnvConfig> | null => {
    try {
      if (typeof window === 'undefined' || !localStorage) {
        return null;
      }
      
      const stored = localStorage.getItem('app_config');
      if (!stored) {
        return null;
      }
      
      const parsed = JSON.parse(stored);
      return parsed;
    } catch (error) {
      console.warn('Failed to read stored config:', error);
      return null;
    }
  };
  
  /**
   * 存储配置到本地存储
   */
  export const storeConfig = (config: Partial<EnvConfig>): void => {
    try {
      if (typeof window === 'undefined' || !localStorage) {
        return;
      }
      
      // 只存储用户偏好相关的配置，避免覆盖环境相关配置
      const userConfig = {
        FEATURE_FLAGS: {
          DARK_MODE: config.FEATURE_FLAGS?.DARK_MODE
        },
        LOG_LEVEL: config.LOG_LEVEL
      };
      
      localStorage.setItem('app_config', JSON.stringify(userConfig));
    } catch (error) {
      console.warn('Failed to store config:', error);
    }
  };
  
  /**
   * 获取环境配置
   */
  export const getEnvConfig = (): EnvConfig => {
    // 获取默认配置
    const defaultConfig = getDefaultConfig();
    
    // 获取存储的用户配置
    const storedConfig = getStoredConfig();
    
    // 合并配置
    let mergedConfig = defaultConfig;
    if (storedConfig) {
      mergedConfig = deepMerge(mergedConfig, storedConfig);
    }
    
    // 验证配置完整性
    validateConfig(mergedConfig);
    
    // 在开发模式下打印配置（移除敏感信息）
    if (mergedConfig.IS_DEVELOPMENT) {
      const safeConfig = { ...mergedConfig };
      delete safeConfig.SENTRY_DSN;
      console.debug('Environment Configuration:', safeConfig);
    }
    
    return mergedConfig;
  };
  
  /**
   * 配置实例
   */
  export let env: EnvConfig = getEnvConfig();
  
  /**
   * 运行时更新配置
   * @param newConfig 新的配置部分
   * @param store 是否存储到本地存储（用于用户偏好）
   */
  export const updateConfig = (newConfig: Partial<EnvConfig>, store = false): void => {
    env = deepMerge(env, newConfig);
    validateConfig(env);
    
    if (store) {
      storeConfig(newConfig);
    }
    
    if (env.IS_DEVELOPMENT) {
      console.debug('Configuration updated at runtime:', newConfig);
    }
  };
  
  /**
   * 运行时重新加载配置
   * 从远程和本地存储重新获取最新配置
   */
  export const reloadConfig = async (): Promise<void> => {
    // 尝试从远程获取最新配置
    const remoteConfig = await fetchRemoteConfig();
    
    if (remoteConfig) {
      updateConfig(remoteConfig);
    }
    
    // 重新获取本地存储的配置
    const storedConfig = getStoredConfig();
    if (storedConfig) {
      updateConfig(storedConfig);
    }
  };
  
  /**
   * 检查特性是否启用
   */
  export const isFeatureEnabled = (featureName: string): boolean => {
    return env.FEATURE_FLAGS[featureName] || false;
  };
  
  /**
   * 切换特性开关
   * @param featureName 特性名称
   * @param enabled 是否启用
   * @param store 是否持久化
   */
  export const toggleFeature = (featureName: string, enabled?: boolean, store = true): void => {
    const currentValue = env.FEATURE_FLAGS[featureName] || false;
    const newValue = enabled !== undefined ? enabled : !currentValue;
    
    updateConfig({
      FEATURE_FLAGS: {
        [featureName]: newValue
      }
    }, store);
    
    if (env.IS_DEVELOPMENT) {
      console.debug(`Feature ${featureName} toggled to ${newValue}`);
    }
  };
  
  /**
   * 获取适用于API调用的完整URL
   */
  export const getApiUrl = (endpoint: string): string => {
    const baseUrl = env.API_BASE_URL.replace(/\/$/, '');
    const cleanEndpoint = endpoint.replace(/^\//, '');
    return `${baseUrl}/${cleanEndpoint}`;
  };
  
  /**
   * 获取CDN资源URL
   */
  export const getCdnUrl = (path: string): string => {
    const baseUrl = env.CDN_URL.replace(/\/$/, '');
    const cleanPath = path.replace(/^\//, '');
    return `${baseUrl}/${cleanPath}`;
  };
  
  /**
   * 初始化配置
   * 在应用启动时调用，可选地从远程加载配置
   */
  export const initConfig = async (options?: {
    loadRemote?: boolean;
    autoReload?: boolean;
    reloadInterval?: number;
  }): Promise<void> => {
    const { loadRemote = true, autoReload = false, reloadInterval = 600000 } = options || {};
    
    // 从远程加载配置
    if (loadRemote) {
      await reloadConfig();
    }
    
    // 设置自动重载（生产环境谨慎使用）
    if (autoReload && !env.IS_PRODUCTION && typeof window !== 'undefined') {
      window.setInterval(reloadConfig, reloadInterval);
    }
  };
  ```
  
  **环境变量配置文件示例**：
  ```env
  # .env.development
  VITE_API_BASE_URL=http://localhost:8080/api
  VITE_CDN_URL=http://localhost:3000/static
  VITE_APP_NAME=React Frontend Dev
  VITE_APP_VERSION=1.0.0-dev
  VITE_BUILD_DATE=2024-01-01T00:00:00Z
  
  # 监控配置
  VITE_SENTRY_DSN=https://your-sentry-dsn.example.com
  VITE_PERFORMANCE_ENABLED=true
  VITE_LOG_LEVEL=debug
  
  # 特性开关
  VITE_FEATURE_DARK_MODE=true
  VITE_FEATURE_NEW_DASHBOARD=true
  VITE_FEATURE_NEW_AUTH_FLOW=true
  VITE_FEATURE_MICRO_FRONTEND=true
  VITE_FEATURE_MOCK_API=true
  VITE_FEATURE_CACHING_ENABLED=true
  
  # 微前端配置
  VITE_MF_ENABLED=true
  VITE_MF_CONTAINER_SELECTOR=#root-micro
  VITE_MF_TIMEOUT=30000
  VITE_MF_RETRY_COUNT=3
  VITE_MF_POLLING_INTERVAL=300000
  
  # 缓存配置
  VITE_CACHE_TTL=60000
  VITE_CACHE_SIZE=100
  VITE_ENABLE_LOCAL_STORAGE=true
  VITE_ENABLE_SESSION_STORAGE=true
  
  # 安全配置
  VITE_CSRF_PROTECTION=true
  VITE_CORS_ENABLED=true
  VITE_ALLOWED_ORIGINS=*
  
  # 远程配置URL
  VITE_CONFIG_URL=http://localhost:8080/api/config
  ```
  
  **应用启动时的配置初始化示例**：
  ```typescript
  // src/main.tsx
  import { initConfig } from './config/env';
  import React from 'react';
  import ReactDOM from 'react-dom/client';
  import App from './App';
  import './index.css';
  
  // 初始化配置
  const initializeApp = async () => {
    try {
      await initConfig({
        loadRemote: true,
        autoReload: import.meta.env.DEV // 仅在开发环境启用自动重载
      });
      
      // 渲染应用
      ReactDOM.createRoot(document.getElementById('root')!).render(
        <React.StrictMode>
          <App />
        </React.StrictMode>
      );
    } catch (error) {
      console.error('Failed to initialize application:', error);
      // 即使配置加载失败，也尝试渲染应用（使用默认配置）
      ReactDOM.createRoot(document.getElementById('root')!).render(
        <React.StrictMode>
          <App />
        </React.StrictMode>
      );
    }
  };
  
  // 启动应用
  initializeApp();
  ```

#### 5.2.4 容器化部署

- **增强的Docker配置**：
  ```dockerfile
  # Dockerfile
  # 多阶段构建优化
  
  # 阶段1: 依赖安装和缓存优化
  FROM node:18-alpine AS deps
  WORKDIR /app
  
  # 先复制package.json和lock文件以优化缓存
  COPY package*.json ./
  
  # 设置npm配置以优化安装
  RUN npm config set fetch-retries 3 \
      && npm config set fetch-retry-factor 10 \
      && npm ci --omit=dev --frozen-lockfile --ignore-scripts
  
  # 阶段2: 构建阶段
  FROM node:18-alpine AS builder
  WORKDIR /app
  
  # 复制依赖
  COPY --from=deps /app/node_modules ./node_modules
  
  # 复制所有源代码
  COPY . .
  
  # 构建参数 - 可在构建时通过 --build-arg 传入
  ARG VITE_API_BASE_URL
  ARG VITE_APP_ENV=production
  ARG VITE_SENTRY_DSN
  ARG VITE_APP_VERSION=1.0.0
  ARG VITE_BUILD_DATE
  
  # 设置环境变量
  ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
  ENV VITE_APP_ENV=${VITE_APP_ENV}
  ENV VITE_SENTRY_DSN=${VITE_SENTRY_DSN}
  ENV VITE_APP_VERSION=${VITE_APP_VERSION}
  ENV VITE_BUILD_DATE=${VITE_BUILD_DATE:-$(date -u +"%Y-%m-%dT%H:%M:%SZ")}
  
  # 构建应用
  RUN npm run build:micro
  
  # 生成构建信息文件
  RUN echo "VERSION=${VITE_APP_VERSION}" > /app/dist/version.txt && \
      echo "BUILD_DATE=${VITE_BUILD_DATE}" >> /app/dist/version.txt && \
      echo "ENV=${VITE_APP_ENV}" >> /app/dist/version.txt
  
  # 阶段3: 运行时阶段 - 使用非root用户运行nginx
  FROM nginx:alpine AS runner
  
  # 设置时区
  RUN apk --no-cache add tzdata && \
      ln -fs /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
      echo "Asia/Shanghai" > /etc/timezone
  
  # 创建非root用户
  RUN addgroup -g 1001 -S appuser && \
      adduser -S appuser -u 1001
  
  # 配置Nginx
  COPY nginx.conf /etc/nginx/conf.d/default.conf
  
  # 添加自定义错误页面
  COPY error-pages /usr/share/nginx/html/error-pages
  
  # 复制构建产物
  COPY --from=builder --chown=appuser:appuser /app/dist /usr/share/nginx/html
  
  # 优化Nginx配置
  RUN rm /etc/nginx/conf.d/default.conf && \
      mkdir -p /var/cache/nginx/client_temp /var/cache/nginx/proxy_temp /var/cache/nginx/fastcgi_temp && \
      chown -R appuser:appuser /var/cache/nginx
  
  # 配置健康检查
  COPY health-check.sh /usr/local/bin/health-check.sh
  RUN chmod +x /usr/local/bin/health-check.sh
  
  # 切换到非root用户
  USER appuser
  
  # 暴露端口
  EXPOSE 8080
  
  # 健康检查
  HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 CMD ["/usr/local/bin/health-check.sh"]
  
  # 启动Nginx
  CMD ["nginx", "-g", "daemon off;"]
  ```
  
  **Nginx配置优化**：
  ```nginx
  # nginx.conf
  server {
      listen 8080;
      server_name localhost;
      
      # 安全头设置
      add_header X-Content-Type-Options nosniff;
      add_header X-Frame-Options DENY;
      add_header X-XSS-Protection "1; mode=block";
      add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
      add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'";
      
      # 根路径配置
      root /usr/share/nginx/html;
      index index.html;
      
      # 压缩配置
      gzip on;
      gzip_comp_level 6;
      gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript;
      gzip_vary on;
      
      # 静态文件缓存
      location ~* \.(jpg|jpeg|png|gif|ico|css|js|json|svg|woff|woff2|ttf|eot)$ {
          expires 7d;
          add_header Cache-Control "public, max-age=604800";
          access_log off;
      }
      
      # API代理配置
      location /api {
          proxy_pass $API_BASE_URL;
          proxy_http_version 1.1;
          proxy_set_header Upgrade $http_upgrade;
          proxy_set_header Connection 'upgrade';
          proxy_set_header Host $host;
          proxy_set_header X-Real-IP $remote_addr;
          proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
          proxy_set_header X-Forwarded-Proto $scheme;
          proxy_cache_bypass $http_upgrade;
          proxy_connect_timeout 10;
          proxy_send_timeout 10;
          proxy_read_timeout 60;
      }
      
      # 微前端配置
      location /micro-apps {
          proxy_pass $MICRO_APPS_URL;
          proxy_set_header Host $host;
          proxy_set_header X-Real-IP $remote_addr;
          proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
          proxy_set_header X-Forwarded-Proto $scheme;
      }
      
      # SPA路由配置 - 处理前端路由
      location / {
          try_files $uri $uri/ /index.html;
          add_header Cache-Control "no-store, no-cache, must-revalidate" always;
      }
      
      # 错误页面配置
      error_page 404 /error-pages/404.html;
      error_page 500 502 503 504 /error-pages/500.html;
      
      location /error-pages/ {
          root /usr/share/nginx/html;
          internal;
      }
      
      # 限流配置 (可选)
      limit_req_zone $binary_remote_addr zone=mylimit:10m rate=10r/s;
      location / {
          limit_req zone=mylimit burst=20 nodelay;
      }
  }
  ```
  
  **健康检查脚本**：
  ```bash
  #!/bin/sh
  # health-check.sh
  
  # 检查Nginx是否运行
  if [ "$(pgrep -x nginx)" = "" ]; then
      echo "Nginx is not running"
      exit 1
  fi
  
  # 检查首页是否可访问
  HTTP_STATUS=$(wget -qO- -T 5 http://localhost:8080/health-check 2>/dev/null | head -n 1)
  
  if [ "$HTTP_STATUS" = "OK" ]; then
      echo "Health check passed"
      exit 0
  else
      echo "Health check failed: $HTTP_STATUS"
      exit 1
  fi
  ```
  
  **健康检查HTML**：
  ```html
  <!-- public/health-check -->
  OK
  ```

- **增强的Docker Compose配置**：
  ```yaml
  # docker-compose.yml
  version: '3'
  
  services:
    react-frontend-module:
      build:
        context: .
        dockerfile: Dockerfile
        args:
          - VITE_API_BASE_URL=http://api-service:8080
          - VITE_SENTRY_DSN=${SENTRY_DSN}
      ports:
        - "3005:80"
      environment:
        - NGINX_HOST=0.0.0.0
        - NGINX_PORT=80
      networks:
        - app-network
      depends_on:
        - api-service
  
  networks:
    app-network:
      driver: bridge
  ```

#### 5.2.5 监控与告警

- **Sentry集成**：
  ```typescript
  // src/utils/sentry.ts
  import * as Sentry from '@sentry/react';
  import { BrowserTracing } from '@sentry/tracing';
  import { env } from '../config/env';
  
  export const initSentry = () => {
    if (env.SENTRY_DSN && env.IS_PRODUCTION) {
      Sentry.init({
        dsn: env.SENTRY_DSN,
        integrations: [new BrowserTracing()],
        environment: env.IS_PRODUCTION ? 'production' : 'development',
        release: `${env.APP_NAME}@${env.APP_VERSION}`,
        tracesSampleRate: 0.1,
        // 性能监控配置
        performance: {
          tracingOrigins: ['localhost', 'your-api-domain.com', /^//],
        },
      });
    }
  };
  
  export const captureError = (error: Error, context?: Record<string, unknown>) => {
    if (env.SENTRY_DSN) {
      Sentry.withScope(scope => {
        if (context) {
          Object.entries(context).forEach(([key, value]) => {
            scope.setContext(key, value as any);
          });
        }
        Sentry.captureException(error);
      });
    }
    console.error('捕获到错误:', error, context);
  };
  ```

## 5.3 前端可观测性

### 5.3.1 性能监控

- **自定义性能指标**：
  ```typescript
  // src/utils/performance.ts
  import { env } from '../config/env';

  export interface PerformanceMetric {
    name: string;
    value: number;
    unit: 'ms' | 'percent' | 'count';
    dimensions?: Record<string, string>;
  }

  export class PerformanceMonitor {
    private startTime: Record<string, number> = {};
    private metrics: PerformanceMetric[] = [];

    // 开始计时
    startTimer(metricName: string) {
      this.startTime[metricName] = performance.now();
    }

    // 结束计时并记录指标
    endTimer(metricName: string, dimensions?: Record<string, string>) {
      if (!this.startTime[metricName]) {
        console.warn(`Timer for ${metricName} was not started`);
        return;
      }

      const duration = performance.now() - this.startTime[metricName];
      this.recordMetric({
        name: metricName,
        value: duration,
        unit: 'ms',
        dimensions,
      });

      delete this.startTime[metricName];
    }

    // 记录自定义指标
    recordMetric(metric: PerformanceMetric) {
      this.metrics.push(metric);
      
      // 发送到监控服务
      if (env.IS_PRODUCTION) {
        this.sendMetricToService(metric);
      }
    }

    // 发送指标到监控服务
    private sendMetricToService(metric: PerformanceMetric) {
      fetch('/api/metrics', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          ...metric,
          timestamp: Date.now(),
          appVersion: env.APP_VERSION,
          environment: env.IS_PRODUCTION ? 'production' : 'development',
        }),
      }).catch(err => {
        console.error('Failed to send performance metric:', err);
      });
    }

    // 捕获Web Vitals指标
    captureWebVitals() {
      if ('web-vitals' in window) {
        import('web-vitals').then(({ onCLS, onFID, onFCP, onLCP, onTTFB }) => {
          onCLS(metric => this.recordMetric({
            name: 'CLS',
            value: metric.value,
            unit: 'count',
            dimensions: { id: metric.id, entries: JSON.stringify(metric.entries) }
          }));
          onFID(metric => this.recordMetric({
            name: 'FID',
            value: metric.value,
            unit: 'ms',
            dimensions: { id: metric.id, entries: JSON.stringify(metric.entries) }
          }));
          onFCP(metric => this.recordMetric({
            name: 'FCP',
            value: metric.value,
            unit: 'ms',
            dimensions: { id: metric.id, entries: JSON.stringify(metric.entries) }
          }));
          onLCP(metric => this.recordMetric({
            name: 'LCP',
            value: metric.value,
            unit: 'ms',
            dimensions: { id: metric.id, entries: JSON.stringify(metric.entries) }
          }));
          onTTFB(metric => this.recordMetric({
            name: 'TTFB',
            value: metric.value,
            unit: 'ms',
            dimensions: { id: metric.id, entries: JSON.stringify(metric.entries) }
          }));
        });
      }
    }
  }

  export const performanceMonitor = new PerformanceMonitor();
  ```

## 6. 迁移和兼容性策略

### 6.1 与现有系统集成

#### 6.1.1 API集成策略

- **增强的API适配层实现**：
  ```typescript
  // src/adapters/api-adapter.ts
  import axios from 'axios';
  import { DataTransformer } from '../utils/data-transformer';
  import { logger } from '../utils/logger';
  
  // 定义通用响应格式
  interface LegacyApiResponse<T> {
    success: boolean;
    data: T;
    message: string;
    errorCode?: string;
  }
  
  // 现代API响应格式
  interface ModernApiResponse<T> {
    ok: boolean;
    result: T;
    error?: {
      code: string;
      message: string;
    };
    meta?: {
      pagination?: {
        total: number;
        page: number;
        pageSize: number;
      };
      timestamp: number;
    };
  }
  
  export class ApiAdapter {
    private legacyClient = axios.create({
      baseURL: '/api/legacy',
      timeout: 10000
    });
    
    private modernClient = axios.create({
      baseURL: '/api',
      timeout: 10000
    });
    
    constructor() {
      // 配置拦截器统一处理错误
      this.setupInterceptors();
    }
    
    private setupInterceptors() {
      // 现代API错误拦截器
      this.modernClient.interceptors.response.use(
        response => response,
        error => {
          // 处理网络错误、超时等
          if (!error.response) {
            logger.warn('现代API网络错误，回退到旧API');
            return Promise.reject(new Error('NETWORK_ERROR'));
          }
          return Promise.reject(error);
        }
      );
      
      // 旧API错误拦截器
      this.legacyClient.interceptors.response.use(
        response => response,
        error => {
          logger.error('旧API请求失败:', error);
          return Promise.reject(error);
        }
      );
    }
    
    // 将旧格式响应转换为新格式
    private adaptResponse<T>(legacyResponse: LegacyApiResponse<T>): ModernApiResponse<T> {
      return {
        ok: legacyResponse.success,
        result: legacyResponse.data,
        error: legacyResponse.success ? undefined : {
          code: legacyResponse.errorCode || 'UNKNOWN_ERROR',
          message: legacyResponse.message
        },
        meta: {
          timestamp: Date.now()
        }
      };
    }
    
    // 获取用户数据示例
    async getUserData(userId: string): Promise<ModernApiResponse<any>> {
      try {
        // 尝试使用新API
        try {
          const response = await this.modernClient.get(`/users/${userId}`);
          return response.data;
        } catch (modernError: any) {
          // 如果新API调用失败且是网络错误或404，回退到旧API
          if (modernError.message === 'NETWORK_ERROR' || 
              (modernError.response && modernError.response.status === 404)) {
            logger.warn('新API调用失败，尝试使用旧API', { userId });
            const legacyResponse = await this.legacyClient.get<LegacyApiResponse<any>>(`/user/${userId}`);
            return this.adaptResponse(legacyResponse.data);
          }
          throw modernError;
        }
      } catch (error) {
        logger.error('获取用户数据失败:', { userId, error });
        throw error;
      }
    }
    
    // 批量获取数据，支持分页
    async getEntities(params: {
      page?: number;
      pageSize?: number;
      filter?: Record<string, any>;
    }): Promise<ModernApiResponse<any[]>> {
      const { page = 1, pageSize = 20, filter = {} } = params;
      
      try {
        // 尝试使用新API
        try {
          const response = await this.modernClient.get('/entities', {
            params: { page, pageSize, ...filter }
          });
          return response.data;
        } catch (modernError: any) {
          // 回退到旧API
          if (modernError.message === 'NETWORK_ERROR' || 
              (modernError.response && modernError.response.status === 404)) {
            const legacyParams = {
              pageNum: page,
              pageSize,
              ...DataTransformer.modernToLegacy(filter, DataTransformer.getEntityFilterMap())
            };
            
            const legacyResponse = await this.legacyClient.get<LegacyApiResponse<{ list: any[], total: number }>>('/entity/list', {
              params: legacyParams
            });
            
            // 转换响应格式
            const adaptedResponse = this.adaptResponse(legacyResponse.data);
            return {
              ...adaptedResponse,
              result: legacyResponse.data.data.list.map(item => 
                DataTransformer.legacyToModern(item, DataTransformer.getEntityTransformationMap(), 'entity')
              ),
              meta: {
                ...adaptedResponse.meta,
                pagination: {
                  total: legacyResponse.data.data.total,
                  page,
                  pageSize
                }
              }
            };
          }
          throw modernError;
        }
      } catch (error) {
        logger.error('获取实体数据失败:', { params, error });
        throw error;
      }
    }
    
    // 更多API适配方法...
  }
  
  export const apiAdapter = new ApiAdapter();
  ```

#### 6.1.2 认证与权限集成

- **增强的认证集成服务**：
  ```typescript
  // src/services/auth-integration.ts
  import { useGlobalStore } from '../stores/global';
  import { eventBus } from '../utils/event-bus';
  import { logger } from '../utils/logger';
  
  export class AuthIntegrationService {
    private globalStore = useGlobalStore.getState();
    private syncAttempts = 0;
    private maxSyncAttempts = 3;
    
    constructor() {
      // 监听主应用的登录事件
      eventBus.on('auth:login', this.handleMainAppLogin);
      // 监听主应用的登出事件
      eventBus.on('auth:logout', this.handleMainAppLogout);
      
      // 初始化时检查是否在微应用环境中
      if (window.__POWERED_BY_QIANKUN__) {
        this.syncWithMainApp();
      }
    }
    
    // 处理主应用登录事件
    private handleMainAppLogin = (userInfo: any, token: string) => {
      this.globalStore.setUserInfo(userInfo);
      this.globalStore.setToken(token);
      logger.info('同步主应用登录状态', { userId: userInfo?.id });
      
      // 重置同步尝试次数
      this.syncAttempts = 0;
    };
    
    // 处理主应用登出事件
    private handleMainAppLogout = () => {
      this.globalStore.setUserInfo(null);
      this.globalStore.setToken(null);
      logger.info('同步主应用登出状态');
    };
    
    // 与主应用同步状态（带重试机制）
    private syncWithMainApp() {
      // 通过全局API获取主应用的认证状态
      if (window.parent && window.parent.bone && window.parent.bone.getAuthState) {
        try {
          const authState = window.parent.bone.getAuthState();
          if (authState) {
            this.globalStore.setUserInfo(authState.userInfo);
            this.globalStore.setToken(authState.token);
            logger.info('与主应用同步认证状态成功');
            this.syncAttempts = 0;
          }
        } catch (error) {
          logger.error('与主应用同步认证状态失败:', error);
          this.handleSyncError();
        }
      } else if (this.syncAttempts < this.maxSyncAttempts) {
        // 主应用API可能尚未加载完成，延迟重试
        this.syncAttempts++;
        logger.warn(`主应用认证API未就绪，${this.syncAttempts}秒后重试`);
        setTimeout(() => this.syncWithMainApp(), 1000 * this.syncAttempts);
      }
    }
    
    // 处理同步错误
    private handleSyncError() {
      this.syncAttempts++;
      if (this.syncAttempts < this.maxSyncAttempts) {
        logger.warn(`同步失败，${this.syncAttempts}秒后重试`);
        setTimeout(() => this.syncWithMainApp(), 1000 * this.syncAttempts);
      } else {
        logger.error('达到最大同步尝试次数，同步失败');
      }
    }
    
    // 发送登录事件到主应用
    notifyMainAppLogin(userInfo: any, token: string) {
      if (window.__POWERED_BY_QIANKUN__ && window.parent && window.parent.bone && window.parent.bone.onLogin) {
        try {
          window.parent.bone.onLogin(userInfo, token);
          logger.info('通知主应用登录成功');
        } catch (error) {
          logger.error('通知主应用登录失败:', error);
        }
      }
      // 同时通过事件总线通知
      eventBus.emit('auth:login', userInfo, token);
    }
    
    // 发送登出事件到主应用
    notifyMainAppLogout() {
      if (window.__POWERED_BY_QIANKUN__ && window.parent && window.parent.bone && window.parent.bone.onLogout) {
        try {
          window.parent.bone.onLogout();
          logger.info('通知主应用登出成功');
        } catch (error) {
          logger.error('通知主应用登出失败:', error);
        }
      }
      // 同时通过事件总线通知
      eventBus.emit('auth:logout');
    }
    
    // 检查认证状态有效性
    isAuthenticated(): boolean {
      const token = this.globalStore.token;
      // 简单检查token是否存在和有效
      return !!token && token.length > 0;
    }
    
    // 获取用户权限
    getUserPermissions(): string[] {
      const userInfo = this.globalStore.userInfo;
      return userInfo?.permissions || [];
    }
    
    // 检查用户是否有特定权限
    hasPermission(permission: string): boolean {
      const permissions = this.getUserPermissions();
      return permissions.includes(permission);
    }
  }
  
  export const authIntegrationService = new AuthIntegrationService();
  ```

#### 6.1.3 数据格式兼容性

- **增强的数据转换工具**：
  ```typescript
  // src/utils/data-transformer.ts
  import { logger } from './logger';
  
  export class DataTransformer {
    // 高级数据转换配置
    private static transformationConfigs: Map<string, {
      map: Record<string, string>;
      converters?: Record<string, (value: any) => any>;
    }> = new Map();
    
    // 初始化转换配置
    static initialize() {
      // 用户数据转换配置
      this.transformationConfigs.set('user', {
        map: {
          id: 'user_id',
          name: 'username',
          email: 'email_address',
          role: 'user_role',
          createdAt: 'registration_date',
          lastLogin: 'last_login_time'
        },
        converters: {
          createdAt: (value) => new Date(value).toISOString(),
          lastLogin: (value) => new Date(value).toISOString()
        }
      });
      
      // 实体数据转换配置
      this.transformationConfigs.set('entity', {
        map: {
          id: 'entity_id',
          name: 'entity_name',
          type: 'entity_type',
          status: 'entity_status',
          createdAt: 'create_time',
          updatedAt: 'update_time',
          owner: 'creator_id',
          metadata: 'extra_data'
        },
        converters: {
          createdAt: (value) => new Date(value).toISOString(),
          updatedAt: (value) => new Date(value).toISOString(),
          status: (value) => this.mapStatus(value)
        }
      });
      
      // 实体筛选条件转换配置
      this.transformationConfigs.set('entityFilter', {
        map: {
          name: 'entity_name',
          type: 'entity_type',
          status: 'entity_status',
          startDate: 'begin_date',
          endDate: 'end_date',
          searchTerm: 'keyword'
        }
      });
    }
    
    // 状态映射
    private static mapStatus(legacyStatus: string): string {
      const statusMap: Record<string, string> = {
        'ACTIVE': 'active',
        'INACTIVE': 'inactive',
        'DELETED': 'deleted',
        'DRAFT': 'draft',
        'PUBLISHED': 'published'
      };
      
      return statusMap[legacyStatus] || legacyStatus.toLowerCase();
    }
    
    // 获取用户转换映射
    static getUserTransformationMap() {
      return this.transformationConfigs.get('user')?.map || {};
    }
    
    // 获取实体转换映射
    static getEntityTransformationMap() {
      return this.transformationConfigs.get('entity')?.map || {};
    }
    
    // 获取实体筛选条件转换映射
    static getEntityFilterMap() {
      return this.transformationConfigs.get('entityFilter')?.map || {};
    }
    
    // 将旧数据格式转换为新数据格式（支持深度转换和数据类型转换）
    static legacyToModern<T>(legacyData: any, transformationMap: Record<string, string>, type?: string): T {
      if (!legacyData) return {} as T;
      
      // 处理数组
      if (Array.isArray(legacyData)) {
        return legacyData.map(item => this.legacyToModern(item, transformationMap, type)) as unknown as T;
      }
      
      const modernData: any = {};
      const config = type ? this.transformationConfigs.get(type) : undefined;
      
      try {
        // 应用转换映射
        Object.entries(transformationMap).forEach(([modernKey, legacyKey]) => {
          if (legacyData.hasOwnProperty(legacyKey)) {
            let value = legacyData[legacyKey];
            
            // 应用类型转换器
            if (config?.converters && config.converters[modernKey]) {
              value = config.converters[modernKey](value);
            }
            
            modernData[modernKey] = value;
          }
        });
        
        // 保留未映射的字段（可选）
        Object.keys(legacyData).forEach(key => {
          const mappedKey = Object.entries(transformationMap).find(([_, v]) => v === key)?.[0];
          if (!mappedKey) {
            modernData[key] = legacyData[key];
          }
        });
      } catch (error) {
        logger.error('数据转换失败 (legacyToModern):', error, { legacyData, transformationMap, type });
      }
      
      return modernData as T;
    }
    
    // 将新数据格式转换为旧数据格式
    static modernToLegacy<T>(modernData: any, transformationMap: Record<string, string>, type?: string): T {
      if (!modernData) return {} as T;
      
      // 处理数组
      if (Array.isArray(modernData)) {
        return modernData.map(item => this.modernToLegacy(item, transformationMap, type)) as unknown as T;
      }
      
      const legacyData: any = {};
      
      try {
        // 应用反向转换映射
        Object.entries(transformationMap).forEach(([modernKey, legacyKey]) => {
          if (modernData.hasOwnProperty(modernKey)) {
            legacyData[legacyKey] = modernData[modernKey];
          }
        });
      } catch (error) {
        logger.error('数据转换失败 (modernToLegacy):', error, { modernData, transformationMap, type });
      }
      
      return legacyData as T;
    }
    
    // 深度合并对象
    static deepMerge(target: any, source: any): any {
      const output = { ...target };
      
      if (this.isObject(target) && this.isObject(source)) {
        Object.keys(source).forEach(key => {
          if (this.isObject(source[key])) {
            if (!(key in target)) {
              Object.assign(output, { [key]: source[key] });
            } else {
              output[key] = this.deepMerge(target[key], source[key]);
            }
          } else {
            Object.assign(output, { [key]: source[key] });
          }
        });
      }
      
      return output;
    }
    
    // 检查是否为对象
    private static isObject(item: any): boolean {
      return item && typeof item === 'object' && !Array.isArray(item);
    }
  }
  
  // 初始化数据转换器
  DataTransformer.initialize();
  
  // 使用示例
  // const modernUser = DataTransformer.legacyToModern(legacyUser, DataTransformer.getUserTransformationMap(), 'user');
  ```

### 6.2 渐进式迁移策略

#### 6.2.1 迁移路线图

1. **准备阶段**：
   - 环境搭建与配置
   - 核心架构实现
   - 共享组件库开发

2. **试点阶段**：
   - 选择低风险、低复杂度的模块作为试点
   - 实现完整的微前端集成
   - 收集反馈并调整方案

3. **渐进迁移阶段**：
   - 按业务模块逐步迁移
   - 每迁移一个模块进行充分测试
   - 保持新旧系统并行运行

4. **切换阶段**：
   - 用户引导与培训
   - 灰度发布策略
   - 全面切换与旧系统下线

#### 6.2.2 技术实现策略

- **路由控制迁移**：
  ```typescript
  // src/routes/migration-router.tsx
  import React, { useEffect, useState } from 'react';
  import { Routes, Route, useLocation, Navigate } from 'react-router-dom';
  import { FeatureFlagProvider, useFeatureFlag } from '@/providers/FeatureFlag';
  
  interface MigrationRouteProps {
    legacyPath: string;
    newComponent: React.ComponentType<any>;
    legacyComponent: React.ComponentType<any>;
    featureFlag: string;
  }
  
  const MigrationRoute: React.FC<MigrationRouteProps> = ({ 
    legacyPath, 
    newComponent: NewComponent, 
    legacyComponent: LegacyComponent, 
    featureFlag 
  }) => {
    const { pathname } = useLocation();
    const isNewVersionEnabled = useFeatureFlag(featureFlag);
    const [isMigrationEnabled, setIsMigrationEnabled] = useState(isNewVersionEnabled);
    
    // 监听特性标志变化
    useEffect(() => {
      setIsMigrationEnabled(isNewVersionEnabled);
    }, [isNewVersionEnabled]);
    
    // 可以基于用户ID、角色等进行灰度发布
    const getShouldUseNewVersion = () => {
      // 示例：只有特定用户使用新版本
      const userId = localStorage.getItem('userId');
      const betaUsers = ['user1', 'user2', 'user3'];
      
      // 特性标志优先
      if (isMigrationEnabled) return true;
      
      // 灰度用户
      if (userId && betaUsers.includes(userId)) return true;
      
      return false;
    };
    
    const shouldUseNewVersion = getShouldUseNewVersion();
    
    if (shouldUseNewVersion) {
      return <NewComponent />;
    }
    
    // 对于旧版本，可以重定向到旧系统或渲染旧组件
    if (window.__POWERED_BY_QIANKUN__) {
      // 在微前端环境中，可以渲染旧组件或重定向
      return <LegacyComponent />;
    } else {
      // 在独立运行时，可以重定向到旧系统URL
      return <Navigate to={`/legacy${pathname}`} replace />;
    }
  };
  
  // 使用示例
  // <MigrationRoute
  //   legacyPath="/user/profile"
  //   newComponent={UserProfile}
  //   legacyComponent={LegacyUserProfile}
  //   featureFlag="new_user_profile"
  // />
  ```

#### 6.2.3 数据同步机制

- **实时数据同步服务**：
  ```typescript
  // src/services/data-sync.ts
  import { eventBus } from '../utils/event-bus';
  
  export interface DataSyncOptions {
    syncInterval?: number; // 同步间隔（毫秒）
    syncOnEvent?: string[]; // 在哪些事件触发时同步
    onSyncStart?: () => void;
    onSyncComplete?: () => void;
    onSyncError?: (error: Error) => void;
  }
  
  export class DataSyncService {
    private syncInterval: NodeJS.Timeout | null = null;
    private options: DataSyncOptions;
    private isSyncing: boolean = false;
    
    constructor(private syncFunction: () => Promise<void>, options: DataSyncOptions = {}) {
      this.options = {
        syncInterval: 60000, // 默认1分钟同步一次
        syncOnEvent: [],
        ...options
      };
      
      this.setupEventListeners();
    }
    
    // 设置事件监听器
    private setupEventListeners() {
      if (this.options.syncOnEvent) {
        this.options.syncOnEvent.forEach(event => {
          eventBus.on(event, this.triggerSync);
        });
      }
    }
    
    // 触发同步
    private triggerSync = async () => {
      if (this.isSyncing) return;
      
      try {
        this.isSyncing = true;
        this.options.onSyncStart?.();
        await this.syncFunction();
        this.options.onSyncComplete?.();
      } catch (error) {
        console.error('数据同步失败:', error);
        this.options.onSyncError?.(error as Error);
      } finally {
        this.isSyncing = false;
      }
    };
    
    // 启动同步服务
    start() {
      // 立即执行一次同步
      this.triggerSync();
      
      // 设置定时同步
      if (this.options.syncInterval && !this.syncInterval) {
        this.syncInterval = setInterval(this.triggerSync, this.options.syncInterval);
      }
    }
    
    // 停止同步服务
    stop() {
      if (this.syncInterval) {
        clearInterval(this.syncInterval);
        this.syncInterval = null;
      }
    }
    
    // 手动触发同步
    async syncNow() {
      return this.triggerSync();
    }
  }
  
  // 使用示例
  // const userSyncService = new DataSyncService(
  //   async () => {
  //     // 同步用户数据的逻辑
  //     const legacyData = await legacyApi.getUserData();
  //     const modernData = DataTransformer.legacyToModern(legacyData, DataTransformer.getUserTransformationMap());
  //     await modernApi.updateUserData(modernData);
  //   },
  //   {
  //     syncInterval: 30000, // 30秒同步一次
  //     syncOnEvent: ['user:updated', 'auth:login'],
  //     onSyncComplete: () => console.log('用户数据同步完成')
  //   }
  // );
  // userSyncService.start();
  ```

#### 6.2.4 浏览器兼容性策略

- **浏览器支持配置**：
  ```typescript
  // src/config/browser-compatibility.ts
  export interface BrowserInfo {
    name: string;
    version: string;
    isSupported: boolean;
    compatibilityLevel: 'full' | 'partial' | 'none';
  }
  
  export const getBrowserInfo = (): BrowserInfo => {
    const userAgent = navigator.userAgent;
    let name = 'Unknown';
    let version = 'Unknown';
    
    // 简单的浏览器检测
    if (userAgent.indexOf('Chrome') !== -1 && userAgent.indexOf('Edg') === -1) {
      name = 'Chrome';
      version = userAgent.match(/Chrome\/(\d+)\./)?.[1] || 'Unknown';
    } else if (userAgent.indexOf('Firefox') !== -1) {
      name = 'Firefox';
      version = userAgent.match(/Firefox\/(\d+)\./)?.[1] || 'Unknown';
    } else if (userAgent.indexOf('Safari') !== -1 && userAgent.indexOf('Chrome') === -1) {
      name = 'Safari';
      version = userAgent.match(/Version\/(\d+)\./)?.[1] || 'Unknown';
    } else if (userAgent.indexOf('Edg') !== -1) {
      name = 'Edge';
      version = userAgent.match(/Edg\/(\d+)\./)?.[1] || 'Unknown';
    } else if (userAgent.indexOf('MSIE') !== -1 || userAgent.indexOf('Trident') !== -1) {
      name = 'Internet Explorer';
      version = userAgent.match(/MSIE (\d+)\./)?.[1] || '11';
    }
    
    // 判断支持程度
    let isSupported = true;
    let compatibilityLevel: 'full' | 'partial' | 'none' = 'full';
    
    if (name === 'Internet Explorer') {
      isSupported = false;
      compatibilityLevel = 'none';
    } else if (name === 'Safari' && parseInt(version) < 14) {
      isSupported = true;
      compatibilityLevel = 'partial';
    }
    
    return { name, version, isSupported, compatibilityLevel };
  };
  
  export const checkBrowserCompatibility = (): boolean => {
    const browser = getBrowserInfo();
    return browser.isSupported;
  };
  ```

## 4.8 AI辅助开发与测试

### 4.8.1 AI开发工具集成

- **智能编码助手配置**：
  ```typescript
  // .vscode/settings.json
  {
    "github.copilot.enable": true,
    "github.copilot.editor.enableAutoCompletions": true,
    "github.copilot.inlineSuggest.enable": true,
    "editor.tabCompletion": "on"
  }
  ```

- **代码生成与优化策略**：
  - 使用AI工具生成组件模板和基础功能代码
  - 借助AI进行代码重构和优化建议
  - 使用AI分析并修复潜在的代码问题
  - 建立AI提示工程规范，提高代码生成质量
  - 集成AI代码审查工具，自动检测代码异味和潜在问题

### 4.8.2 AI辅助测试

- **测试用例生成**：
  ```typescript
  // 示例：使用AI生成的测试用例
  describe('DynamicForm组件测试', () => {
    it('应正确渲染表单字段', () => {
      // AI生成的测试代码
      const mockMetadata = {
        fields: [
          { id: 'name', type: 'text', label: '姓名' },
          { id: 'age', type: 'number', label: '年龄' }
        ]
      };
      
      render(<DynamicForm metadata={mockMetadata} />);
      
      expect(screen.getByLabelText(/姓名/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/年龄/i)).toBeInTheDocument();
    });
  });
  ```

- **智能测试策略**：
  - 使用AI分析代码覆盖率，自动补充缺失的测试用例
  - 利用AI进行异常场景和边界条件测试生成
  - 实现基于AI的智能断言推荐
  - 建立测试用例质量评估标准
  - 集成AI驱动的视觉回归测试工具

## 4.9 现代CSS架构与设计系统

### 4.9.1 CSS架构模式

- **原子化CSS实现**：
  ```typescript
  // tailwind.config.js 配置示例
  module.exports = {
    content: ['./src/**/*.{js,jsx,ts,tsx}'],
    theme: {
      extend: {
        colors: {
          primary: '#1890ff',
          secondary: '#52c41a',
          neutral: {
            50: '#f5f5f5',
            100: '#e8e8e8',
            // 更多中性色...
            900: '#1f1f1f'
          }
        },
        fontFamily: {
          sans: ['Inter', 'sans-serif'],
          mono: ['JetBrains Mono', 'monospace']
        }
      }
    },
    plugins: []
  };
  ```

- **CSS-in-JS策略**：
  - 使用styled-components或emotion进行组件样式管理
  - 实现主题变量和样式系统
  - 组件级样式隔离和复用
  - 动态样式和响应式设计实现
  - CSS Modules与预处理器结合的混合策略

### 4.9.2 设计系统实现

- **设计令牌系统**：
  ```typescript
  // src/design-system/tokens.ts
  export const designTokens = {
    colors: {
      primary: '#1890ff',
      // 更多颜色...
    },
    spacing: {
      xs: '4px',
      sm: '8px',
      md: '16px',
      // 更多间距...
    },
    typography: {
      h1: {
        fontSize: '2.5rem',
        fontWeight: 'bold',
        lineHeight: 1.2
      },
      // 更多排版样式...
    },
    borderRadius: {
      sm: '4px',
      md: '8px',
      // 更多圆角...
    }
  };
  ```

- **设计系统组件库**：
  - 基于Storybook的组件文档和示例
  - 组件变体和状态管理
  - 设计系统版本控制和发布流程
  - 组件使用指南和最佳实践
  - 设计资产与代码的自动同步机制

## 4.10 React Server Components与边缘计算

### 4.10.1 React Server Components架构

- **核心概念**：
  - 服务器渲染组件，减少客户端JavaScript体积
  - 直接数据库访问，减少API层开发
  - 自动代码分割和优化加载

- **实现策略**：
  ```typescript
  // 服务端组件示例 (ProductList.server.tsx)
  import { db } from '@/lib/database';

  // 此组件仅在服务器上执行
  export default async function ProductList() {
    // 直接在服务器上查询数据
    const products = await db.products.findMany();
    
    return (
      <div>
        <h2>产品列表</h2>
        <ul>
          {products.map(product => (
            <li key={product.id}>{product.name} - ${product.price}</li>
          ))}
        </ul>
      </div>
    );
  }
  ```

### 4.10.2 边缘计算与CDN优化

- **边缘渲染实现**：
  - 使用Vercel Edge Functions或Cloudflare Workers
  - 实现动态内容的边缘缓存策略
  - 地理位置感知的内容分发

- **性能优化配置**：
  ```typescript
  // edge-config.ts
  export const edgeConfig = {
    cacheTTL: 3600, // 缓存时间(秒)
    staleWhileRevalidate: true,
    cacheKey: (request: Request) => {
      // 生成缓存键，可基于路径、用户角色等
      const url = new URL(request.url);
      return `${url.pathname}-${url.searchParams.get('category') || 'all'}`;
    },
    bypassCacheOn: ['POST', 'PUT', 'DELETE']
  };
  ```

## 4.11 GraphQL API集成

### 4.11.1 GraphQL客户端实现

- **Apollo Client配置**：
  ```typescript
  // src/lib/apollo-client.ts
  import { ApolloClient, InMemoryCache, createHttpLink } from '@apollo/client';
  import { setContext } from '@apollo/client/link/context';
  import { env } from '../config/env';

  const httpLink = createHttpLink({
    uri: `${env.API_BASE_URL}/graphql`,
  });

  const authLink = setContext((_, { headers }) => {
    const token = localStorage.getItem('token');
    return {
      headers: {
        ...headers,
        authorization: token ? `Bearer ${token}` : '',
      },
    };
  });

  export const apolloClient = new ApolloClient({
    link: authLink.concat(httpLink),
    cache: new InMemoryCache({
      typePolicies: {
        Query: {
          fields: {
            // 自定义缓存策略
            entities: {
              keyArgs: ['filter'],
              merge(existing = [], incoming) {
                return [...existing, ...incoming];
              },
            },
          },
        },
      },
    }),
  });
  ```

### 4.11.2 数据获取与缓存策略

- **组件中使用GraphQL**：
  ```typescript
  // src/components/EntityList.tsx
  import { useQuery, gql } from '@apollo/client';

  const GET_ENTITIES = gql`
    query GetEntities($filter: EntityFilter) {
      entities(filter: $filter) {
        id
        name
        type
        createdAt
        updatedAt
      }
    }
  `;

  interface EntityListProps {
    filter?: any;
  }

  export function EntityList({ filter }: EntityListProps) {
    const { data, loading, error } = useQuery(GET_ENTITIES, {
      variables: { filter },
      fetchPolicy: 'cache-and-network',
    });

    if (loading) return <div>加载中...</div>;
    if (error) return <div>错误: {error.message}</div>;

    return (
      <ul>
        {data.entities.map(entity => (
          <li key={entity.id}>{entity.name}</li>
        ))}
      </ul>
    );
  }
  ```


## 5. 部署和集成方案

### 5.1 微前端集成配置

#### 5.1.1 微应用配置与注册（基于无界框架）

- **TypeScript配置接口**：
  ```typescript
  // src/micro-frontend/types.ts
  export interface WujieSandboxConfig {
    // JS沙箱配置
    strict?: boolean; // 是否使用严格模式
    experimentalStyleIsolation?: boolean; // 是否使用实验性样式隔离
    default: boolean; // 是否默认开启沙箱
    quick?: boolean; // 是否使用快照沙箱
    proxy?: boolean; // 是否使用代理沙箱
    scopedCSS?: boolean; // 是否启用scopedCSS
    excludeOrigins?: string[]; // 排除的域名，不进行sandbox隔离
  }
  
  export interface WujiePlugin {
    name: string;
    options?: Record<string, any>;
    exec?: (code: string, url: string) => string; // 插件执行函数
  }
  
/**
   * 微应用配置统一接口（版本2.0）
   * 这是系统中唯一的MicroAppConfig接口定义，所有微应用配置相关功能均使用此接口
   * @see createMicroAppRoutes - 用于创建微应用路由配置
   * @see EnhancedMicroAppContainer - 用于渲染微应用的容器组件
   */
  export interface MicroAppConfig {
    // 基础信息
    name: string;         // 微应用唯一标识符
    title: string;        // 微应用标题
    framework: 'react' | 'vue' | 'angular' | string; // 微应用框架类型
    description: string;  // 微应用描述
    version: string;      // 微应用版本号
    url: string;          // 微应用基础URL
    entry?: string;       // 微应用入口文件路径
    
    // 无界框架特性配置
    alive?: boolean;      // 是否保持应用活跃（无界框架特性）
    sync?: boolean;       // 是否同步执行（无界框架特性）
    singleton?: boolean;  // 是否单例模式
    degrade?: boolean;    // 是否降级模式
    
    // 渲染与通信
    props?: Record<string, unknown>;                    // 传递给微应用的props
    activeRule?: string | ((location: Location) => boolean); // 激活规则
    container?: string;                                 // 容器选择器
    sandbox?: boolean | WujieSandboxConfig;             // 无界框架沙箱配置
    plugins?: WujiePlugin[];                            // 无界框架插件
    
    // 生命周期钩子
    beforeLoad?: () => Promise<void>;  // 加载前
    beforeMount?: () => Promise<void>; // 挂载前
    afterMount?: () => void;           // 挂载后
    beforeUnmount?: () => Promise<void>; // 卸载前
    afterUnmount?: () => void;         // 卸载后
    
    // 错误处理
    onError?: (error: Error) => void;  // 错误回调
    
    // 资源预加载
    preloadAssets?: boolean;           // 是否预加载资源
    preloadModules?: string[];         // 预加载的模块
    
    // 网络请求配置
    fetch?: (input: RequestInfo, init?: RequestInit) => Promise<Response>; // 自定义fetch
    requestInterceptors?: Array<(config: any) => any>;  // 请求拦截器
    responseInterceptors?: Array<(response: any) => any>; // 响应拦截器
    
    // 性能监控
    performanceTrack?: boolean;        // 是否开启性能监控
    timeout?: number;                  // 加载超时时间（毫秒）
  }
  
  export interface MicroAppState {
    name: string;
    status: 'loading' | 'ready' | 'error' | 'unmounted';
    lifecycle: 'beforeLoad' | 'beforeMount' | 'afterMount' | 'beforeUnmount' | 'afterUnmount';
    performance: {
      loadTime?: number;
      mountTime?: number;
      unmountTime?: number;
    };
    error?: Error;
  }
  ```

- **微应用配置实现**：
  ```typescript
  // src/micro-frontend/config.ts
  import { MicroAppConfig, WujieSandboxConfig } from './types';
  import { env } from '@/config/env';
  
  // 全局微应用配置缓存
  export const microAppConfigs = new Map<string, MicroAppConfig>();
  
  // 获取微应用基础URL
  const getMicroAppBaseUrl = (appName: string): string => {
    const baseUrls = {
      development: {
        'react-frontend-module': '//localhost:3005',
        'vue-dashboard-module': '//localhost:3006',
        'angular-settings-module': '//localhost:3007',
      },
      production: {
        'react-frontend-module': 'https://react-module.bone.com',
        'vue-dashboard-module': 'https://vue-module.bone.com',
        'angular-settings-module': 'https://angular-module.bone.com',
      },
    };
    
    return baseUrls[env.IS_PRODUCTION ? 'production' : 'development']?.[appName] || '';
  };
  
  // 默认沙箱配置
  const defaultSandboxConfig: WujieSandboxConfig = {
    strict: false, // 不使用严格模式
    experimentalStyleIsolation: true, // 使用实验性样式隔离
    default: true, // 默认开启沙箱
    quick: true, // 使用快照沙箱提高性能
    proxy: false, // 不使用代理沙箱
    scopedCSS: true, // 启用scopedCSS
    excludeOrigins: ['https://cdn.jsdelivr.net', 'https://unpkg.com'], // 第三方CDN不隔离
  };
  
  // 预定义的微应用配置列表
  const predefinedMicroApps: MicroAppConfig[] = [
    {
      name: 'react-frontend-module',
      title: 'React前端模块',
      framework: 'react',
      description: '基于React的企业级前端模块',
      version: '1.0.0',
      url: getMicroAppBaseUrl('react-frontend-module'),
      entry: '/entry.html',
      alive: true, // 保持应用活跃状态
      sync: true, // 同步执行
      singleton: true, // 单例模式
      degrade: false,
      sandbox: defaultSandboxConfig,
      preloadAssets: true,
      performanceTrack: true,
      timeout: 30000, // 30秒超时
      props: {
          basePath: '/react-module',
          theme: 'light',
          token: '', // 将在运行时注入
        },
        // 生命周期钩子示例
        beforeLoad: async () => {
          console.log('加载React前端模块前');
          // 可以在这里进行权限检查、预加载资源等操作
          return Promise.resolve();
        },
        afterMount: () => {
          console.log('React前端模块已挂载');
        },
        // 错误处理
        onError: (error: Error) => {
          console.error('React前端模块加载错误:', error);
          // 上报错误信息到监控系统
        },
        // 自定义fetch，用于注入认证信息
        fetch: (input, init = {}) => {
          const headers = {
            ...init.headers,
            'X-App-Name': 'main-app',
            'X-App-Version': env.APP_VERSION,
          };
          return window.fetch(input, { ...init, headers });
        }
      },
      {
        name: 'vue-dashboard-module',
        title: 'Vue仪表盘模块',
        framework: 'vue',
        description: '基于Vue的可视化仪表盘模块',
        version: '1.0.0',
        url: getMicroAppBaseUrl('vue-dashboard-module'),
        entry: '/entry.html',
        alive: true,
        sync: true,
        singleton: true,
        degrade: false,
        sandbox: defaultSandboxConfig,
        preloadAssets: true,
        performanceTrack: true,
        timeout: 30000,
        props: {
          basePath: '/dashboard',
          theme: 'light',
          token: '',
        }
      },
      {
        name: 'angular-settings-module',
        title: 'Angular设置模块',
        framework: 'angular',
        description: '基于Angular的系统设置模块',
        version: '1.0.0',
        url: getMicroAppBaseUrl('angular-settings-module'),
        entry: '/index.html',
        alive: false, // 不保持活跃，节省资源
        sync: false, // 异步执行
        singleton: false,
        degrade: false,
        sandbox: defaultSandboxConfig,
        preloadAssets: false,
        performanceTrack: true,
        timeout: 40000,
        props: {
          basePath: '/settings',
          theme: 'light',
          token: '',
        }
      },
    ];
    
    // 初始化所有预定义的微应用配置
    predefinedMicroApps.forEach(app => {
      microAppConfigs.set(app.name, app);
    });
    
    /**
     * 获取微应用配置
     * @param appName 微应用名称
     * @returns 微应用配置对象
     */
    export const getMicroAppConfig = (appName: string): MicroAppConfig | undefined => {
      return microAppConfigs.get(appName);
    };
    
    /**
     * 动态获取微应用配置（支持从服务器获取最新配置）
     * @param appName 微应用名称
     * @param forceRemote 是否强制从远程获取配置
     * @returns 微应用配置对象
     */
    export const fetchMicroAppConfig = async (
      appName: string,
      forceRemote = false
    ): Promise<MicroAppConfig | undefined> => {
      // 1. 如果缓存中存在且不强制远程，则直接返回缓存
      if (microAppConfigs.has(appName) && !forceRemote) {
        return getMicroAppConfig(appName);
      }
      
      try {
        // 2. 从远程服务器获取配置
        const response = await fetch(`${env.API_BASE_URL}/api/micro-apps/${appName}`, {
          headers: {
            'Content-Type': 'application/json',
          },
        });
        
        if (response.ok) {
          const remoteConfig = await response.json();
          // 3. 合并远程配置和本地配置
          const mergedConfig = {
            ...getMicroAppConfig(appName),
            ...remoteConfig,
            // 确保基础配置不被覆盖
            name: appName,
          };
          
          // 4. 更新配置缓存
          microAppConfigs.set(appName, mergedConfig);
          return mergedConfig;
        }
      } catch (error) {
        console.error(`获取微应用 ${appName} 远程配置失败:`, error);
        // 发生错误时返回本地缓存的配置
      }
      
      // 5. 最终返回本地配置或undefined
      return getMicroAppConfig(appName);
    };
    
    /**
     * 注册新的微应用配置
     * @param config 微应用配置
     */
    export const registerMicroApp = (config: MicroAppConfig): void => {
      if (!config.name) {
        throw new Error('微应用配置必须包含name属性');
      }
      
      // 合并默认配置
      const defaultConfig: Partial<MicroAppConfig> = {
        version: '1.0.0',
        alive: true,
        sync: true,
        singleton: false,
        degrade: false,
        sandbox: defaultSandboxConfig,
        preloadAssets: true,
        performanceTrack: true,
        timeout: 30000,
        props: {},
      };
      
      const mergedConfig = {
        ...defaultConfig,
        ...config,
        sandbox: typeof config.sandbox === 'boolean' 
          ? (config.sandbox ? defaultSandboxConfig : false)
          : { ...defaultSandboxConfig, ...config.sandbox },
      };
      
      // 更新配置缓存
      microAppConfigs.set(config.name, mergedConfig);
      
      console.log(`微应用 ${config.name} 已成功注册`);
    };
    
    /**
     * 注销微应用
     * @param appName 微应用名称
     */
    export const unregisterMicroApp = (appName: string): void => {
      microAppConfigs.delete(appName);
      console.log(`微应用 ${appName} 已注销`);
    };
    
    /**
     * 获取所有已注册的微应用配置
     * @returns 微应用配置数组
     */
    export const getAllMicroApps = (): MicroAppConfig[] => {
      return Array.from(microAppConfigs.values());
    };
    
    /**
     * 获取活动规则匹配的微应用
     * @param location 当前Location对象
     * @returns 匹配的微应用配置或undefined
     */
    export const getMatchedMicroApp = (location: Location): MicroAppConfig | undefined => {
      for (const app of microAppConfigs.values()) {
        if (!app.activeRule) continue;
        
        if (typeof app.activeRule === 'string') {
          // 字符串形式的activeRule，进行路径匹配
          if (location.pathname.startsWith(app.activeRule)) {
            return app;
          }
        } else if (typeof app.activeRule === 'function') {
          // 函数形式的activeRule，执行函数判断
          if (app.activeRule(location)) {
            return app;
          }
        }
      }
      
      return undefined;
    };
    
    /**
     * 初始化微应用配置系统
     */
    export const initializeMicroAppSystem = (): void => {
      console.log('微应用配置系统初始化完成，已注册微应用数量:', predefinedMicroApps.length);
      
      // 可以在这里添加更多初始化逻辑，如:
      // 1. 从服务器预加载配置
      // 2. 设置定时更新机制
      // 3. 注册全局错误监听
    };
    
    // 自动初始化微应用配置系统
    if (typeof window !== 'undefined') {
      // 确保DOM加载完成后初始化
      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initializeMicroAppSystem);
      } else {
        initializeMicroAppSystem();
      }
    }
        userInfo: null // 将在运行时注入
      },
    },
  ];
  ```

#### 5.1.2 微应用生命周期管理

- **微应用入口实现**：
  ```typescript
  // src/main.tsx (微应用)
  import React from 'react';
  import ReactDOM from 'react-dom/client';
  import { createMemoryHistory } from 'history';
  import App from './App';
  import { setupApp } from './app-setup';
  
  let root: ReactDOM.Root | null = null;
  let memoryHistory: ReturnType<typeof createMemoryHistory> | null = null;
  
  // 渲染应用
  const render = (props: any = {}) => {
    const { container, basePath, theme, token, userInfo } = props;
    
    // 初始化内存路由
    memoryHistory = createMemoryHistory({
      initialEntries: [window.location.pathname.replace(basePath || '', '')],
      basename: basePath
    });
    
    // 设置全局配置
    setupApp({
      theme,
      token,
      userInfo,
      basePath
    });
    
    // 获取挂载容器
    const rootElement = container 
      ? container.querySelector('#root') 
      : document.querySelector('#root');
    
    if (!root && rootElement) {
      root = ReactDOM.createRoot(rootElement);
    }
    
    root?.render(
      <React.StrictMode>
        <App history={memoryHistory} />
      </React.StrictMode>
    );
  };
  
  // 独立运行时
  if (!window.__POWERED_BY_QIANKUN__) {
    render();
  }
  
  // 微前端环境下导出生命周期钩子
  export const bootstrap = async () => {
    console.log('React前端模块初始化');
  };
  
  export const mount = async (props: any) => {
    console.log('React前端模块挂载，接收参数:', props);
    render(props);
  };
  
  export const unmount = async () => {
    console.log('React前端模块卸载');
    if (root) {
      root.unmount();
      root = null;
    }
    if (memoryHistory) {
      memoryHistory = null;
    }
  };
  
  // 可选：微前端更新钩子
  export const update = async (props: any) => {
    console.log('React前端模块更新，接收参数:', props);
    render(props);
  };
  ```

#### 5.1.3 微前端通信机制

- **事件总线实现**：
  ```typescript
  // src/utils/event-bus.ts
  class EventBus {
    private events: Map<string, Set<Function>> = new Map();
    
    on(event: string, callback: Function) {
      if (!this.events.has(event)) {
        this.events.set(event, new Set());
      }
      this.events.get(event)!.add(callback);
      
      // 返回取消订阅函数
      return () => this.off(event, callback);
    }
    
    emit(event: string, ...args: any[]) {
      if (this.events.has(event)) {
        this.events.get(event)!.forEach(callback => {
          try {
            callback(...args);
          } catch (error) {
            console.error(`Error in event handler for ${event}:`, error);
          }
        });
      }
    }
    
    off(event: string, callback?: Function) {
      if (!this.events.has(event)) return;
      
      if (callback) {
        this.events.get(event)!.delete(callback);
      } else {
        this.events.delete(event);
      }
    }
  }
  
  // 创建单例实例
  export const eventBus = new EventBus();
  ```

- **全局状态共享**：
  ```typescript
  // src/stores/global.ts (在主应用中)
  import { create } from 'zustand';
  import { createJSONStorage, persist } from 'zustand/middleware';
  
  interface GlobalState {
    userInfo: Record<string, unknown> | null;
    token: string | null;
    theme: 'light' | 'dark';
    setUserInfo: (userInfo: Record<string, unknown>) => void;
    setToken: (token: string) => void;
    setTheme: (theme: 'light' | 'dark') => void;
  }
  
  export const useGlobalStore = create<GlobalState>()(
    persist(
      (set) => ({
        userInfo: null,
        token: null,
        theme: 'light',
        setUserInfo: (userInfo) => set({ userInfo }),
        setToken: (token) => set({ token }),
        setTheme: (theme) => set({ theme })
      }),
      {
        name: 'global-storage',
        storage: createJSONStorage(() => sessionStorage)
      }
    )
  );
  ```

#### 5.1.4 Module Federation微前端方案

- **增强的Webpack Module Federation配置**：
  ```javascript
  // webpack.config.js (主应用)
  const { ModuleFederationPlugin } = require('webpack').container;
  const { DefinePlugin, container } = require('webpack');
  const HtmlWebpackPlugin = require('html-webpack-plugin');
  const { resolve } = require('path');
  const deps = require('./package.json').dependencies;
  
  // 环境配置
  const isProduction = process.env.NODE_ENV === 'production';
  const isDevelopment = !isProduction;
  const PORT = process.env.PORT || 3000;
  
  // 生成远程模块配置
  const getRemoteAppsConfig = () => {
    // 根据环境动态配置远程地址
    const baseUrl = isDevelopment 
      ? 'http://localhost' 
      : 'https://cdn.bone.com/apps';
    
    return {
      remote_app: `remote_app@${baseUrl}:3001/remoteEntry.js`,
      react_components: `react_components@${baseUrl}:3002/remoteEntry.js`
    };
  };
  
  // 共享依赖配置
  const sharedDependencies = {
    ...deps,
    react: {
      singleton: true,
      requiredVersion: deps.react,
      strictVersion: false,
      eager: true,
    },
    'react-dom': {
      singleton: true,
      requiredVersion: deps['react-dom'],
      strictVersion: false,
      eager: true,
    },
    'react-router-dom': {
      singleton: true,
      requiredVersion: deps['react-router-dom'],
      strictVersion: false,
    },
  };
  
  module.exports = {
    mode: isProduction ? 'production' : 'development',
    entry: {
      main: './src/index.tsx',
      // 分离核心依赖
      vendor: ['react', 'react-dom', 'react-router-dom'],
    },
    output: {
      publicPath: 'auto',
      path: resolve(__dirname, 'dist'),
      filename: isProduction ? '[name].[contenthash].js' : '[name].js',
      chunkFilename: isProduction ? '[name].[contenthash].chunk.js' : '[name].chunk.js',
      clean: true,
    },
    resolve: {
      extensions: ['.tsx', '.ts', '.jsx', '.js'],
      alias: {
        '@': resolve(__dirname, 'src'),
      },
    },
    // 性能优化配置
    optimization: {
      splitChunks: {
        chunks: 'all',
        cacheGroups: {
          vendor: {
            name: 'vendors',
            test: /[\\/]node_modules[\\/]/,
            priority: 10,
            chunks: 'all',
          },
        },
      },
      runtimeChunk: 'single',
    },
    plugins: [
      // Module Federation 配置
      new ModuleFederationPlugin({
        name: 'host_app',
        filename: 'remoteEntry.js',
        // 增强的模块暴露配置
        exposes: {
          './AppShell': './src/AppShell',
          './components/Layout': './src/components/Layout',
          './utils/auth': './src/utils/auth',
          './utils/logger': './src/utils/logger',
          // 新增核心共享模块
          './contexts/GlobalState': './src/contexts/GlobalState',
          './themes/default': './src/themes/default',
          './services/ApiClient': './src/services/ApiClient',
          './services/EventBus': './src/services/EventBus',
          './components/ErrorBoundary': './src/components/ErrorBoundary',
          './hooks/useMicroFrontend': './src/hooks/useMicroFrontend',
          './utils/dynamicFederation': './src/utils/dynamicFederation'
        },
        // 远程模块配置
        remotes: getRemoteAppsConfig(),
        // 增强的共享依赖配置
        shared: enhancedSharedDependencies,
        // 高级错误处理配置
        onError: (error) => {
          console.error('Module Federation 加载失败:', error);
          
          // 集成错误监控
          if (typeof window !== 'undefined' && window.Sentry) {
            window.Sentry.captureException(error, {
              tags: {
                module: 'module-federation',
                type: 'remote-loading-error'
              },
              extra: {
                timestamp: Date.now(),
                url: window.location.href
              }
            });
          }
          
          // 触发自定义错误事件
          const event = new CustomEvent('module-federation-error', {
            detail: { error, timestamp: Date.now() }
          });
          window.dispatchEvent(event);
        },
        // 延长请求超时时间
        requestTimeout: 10000,
        
        // 运行时初始化完成回调
        onRuntimeInitialized: () => {
          console.log('Module Federation 运行时初始化完成');
          // 触发初始化完成事件
          window.dispatchEvent(new CustomEvent('module-federation-ready'));
        },
        
        // 配置库类型，支持ESM
        library: {
          type: 'module',
          name: 'host_app'
        },
        
        // 配置远程模块类型
        remoteType: 'promise',
        
        // 配置共享作用域名称
        shareScope: 'default'
      }),
      // 全局变量定义
      new DefinePlugin({
        'process.env.APP_VERSION': JSON.stringify(require('./package.json').version),
        'process.env.API_BASE_URL': JSON.stringify(process.env.API_BASE_URL || 'http://localhost:8080/api'),
      }),
      // HTML 模板配置
      new HtmlWebpackPlugin({
        template: './public/index.html',
        favicon: './public/favicon.ico',
        inject: true,
      }),
    ],
    // 开发服务器配置
    devServer: {
      port: PORT,
      historyApiFallback: true,
      hot: true,
      headers: {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
      },
    },
    // 边缘计算优化配置
  experiments: {
    backCompat: false,
    futureDefaults: true,
    // 启用模块联邦ESM支持
    outputModule: true,
    // 启用持久化缓存
    persistentCache: {
      type: 'filesystem',
      cacheLocation: '.webpack_cache',
      // 缓存版本控制
      version: '1.0',
      // 缓存键生成策略
      buildDependencies: {
        config: [__filename],
        tsconfig: ['./tsconfig.json']
      }
    },
    // 启用顶级await
    topLevelAwait: true,
    // 启用React Fast Refresh
    hot: true
  },
  };
  ```

- **增强的远程应用配置**：
  ```javascript
  // webpack.config.js (远程应用)
  const { ModuleFederationPlugin } = require('webpack').container;
  const { DefinePlugin, container } = require('webpack');
  const HtmlWebpackPlugin = require('html-webpack-plugin');
  const { resolve } = require('path');
  const deps = require('./package.json').dependencies;
  
  // 环境配置
  const isProduction = process.env.NODE_ENV === 'production';
  const isDevelopment = !isProduction;
  const PORT = process.env.PORT || 3001;
  
  // 增强的共享依赖配置
  const enhancedSharedDependencies = {
    ...deps,
    // 核心React库 - 强制singleton模式
    react: {
      singleton: true,
      requiredVersion: deps.react,
      strictVersion: false,
      version: deps.react,
      // 允许版本覆盖
      allowVersionOverride: true,
      // 开发环境下eager加载
      eager: isDevelopment,
      // 提供警告但不阻止加载
      warning: true
    },
    'react-dom': {
      singleton: true,
      requiredVersion: deps['react-dom'],
      strictVersion: false,
      version: deps['react-dom'],
      allowVersionOverride: true,
      eager: isDevelopment
    },
    // 路由库
    'react-router-dom': {
      singleton: true,
      requiredVersion: deps['react-router-dom'] || '^6.0.0',
      strictVersion: false,
      version: deps['react-router-dom'] || '6.0.0'
    },
    // UI组件库
    '@mui/material': {
      singleton: true,
      requiredVersion: deps['@mui/material'],
      strictVersion: false,
      version: deps['@mui/material']
    },
    '@emotion/react': {
      singleton: true,
      requiredVersion: deps['@emotion/react'],
      strictVersion: false,
      version: deps['@emotion/react']
    },
    '@emotion/styled': {
      singleton: true,
      requiredVersion: deps['@emotion/styled'],
      strictVersion: false,
      version: deps['@emotion/styled']
    },
    // 状态管理
    'zustand': {
      singleton: true,
      requiredVersion: deps['zustand'],
      strictVersion: false,
      version: deps['zustand']
    },
    'rxjs': {
      singleton: true,
      requiredVersion: deps['rxjs'],
      strictVersion: false,
      version: deps['rxjs']
    },
    // 工具库 - 允许非singleton模式
    'lodash-es': {
      singleton: false,
      requiredVersion: deps['lodash-es'],
      version: deps['lodash-es'],
      // 自动共享子路径
      includeSecondaries: true,
      // 可选共享
      optional: true
    },
    // 网络请求库
    'axios': {
      singleton: true,
      requiredVersion: deps['axios'],
      strictVersion: false,
      version: deps['axios']
    },
    '@tanstack/react-query': {
      singleton: true,
      requiredVersion: deps['@tanstack/react-query'],
      strictVersion: false,
      version: deps['@tanstack/react-query']
    },
    // 类型定义 - 仅开发环境共享
    '@types/react': {
      requiredVersion: false,
      version: deps['@types/react'],
      provide: isDevelopment,
      eager: false
    },
    '@types/react-dom': {
      requiredVersion: false,
      version: deps['@types/react-dom'],
      provide: isDevelopment,
      eager: false
    }
  };

  // 动态远程应用配置函数
  const getRemoteAppsConfig = () => {
    // 基础配置
    const baseConfig = {
      // 主业务模块 - 立即加载
      business_app: getRemoteModuleConfig('business_app', {
        developmentUrl: 'http://localhost:3001/remoteEntry.js',
        productionUrl: '/business-app/remoteEntry.js',
        priority: 'high',
        // 提供降级模块
        fallbackModule: {
          default: () => <div>业务模块加载中...</div>
        }
      }),
      
      // 用户模块 - 立即加载
      user_app: getRemoteModuleConfig('user_app', {
        developmentUrl: 'http://localhost:3002/remoteEntry.js',
        productionUrl: '/user-app/remoteEntry.js',
        priority: 'high'
      }),
      
      // 分析模块 - 延迟加载
      analytics_app: getRemoteModuleConfig('analytics_app', {
        developmentUrl: 'http://localhost:3003/remoteEntry.js',
        productionUrl: '/analytics-app/remoteEntry.js',
        priority: 'low',
        // 非关键模块，允许延迟加载
        lazy: true
      }),
      
      // 报表模块 - 条件加载
      reports_app: getRemoteModuleConfig('reports_app', {
        developmentUrl: 'http://localhost:3004/remoteEntry.js',
        productionUrl: '/reports-app/remoteEntry.js',
        priority: 'medium',
        // 按需加载条件
        loadCondition: 'user.hasReportsAccess'
      })
    };
    
    // 从环境变量或配置中心合并动态配置
    const dynamicConfig = getDynamicRemoteAppsConfig();
    
    return { ...baseConfig, ...dynamicConfig };
  };
  
  /**
   * 获取单个远程模块的配置
   */
  const getRemoteModuleConfig = (name, options = {}) => {
    const {
      developmentUrl,
      productionUrl,
      priority = 'medium',
      lazy = false,
      fallbackModule,
      loadCondition
    } = options;
    
    // 根据环境选择URL
    const baseUrl = isProduction ? productionUrl : developmentUrl;
    
    // 支持从window配置或环境变量覆盖URL
    const overrideUrl = `window.__REMOTE_MODULES__?.${name} || process.env.${name.toUpperCase()}_URL`;
    
    // 生成动态远程配置
    return {
      external: `promise new Promise(resolve => {
        // 获取最终的远程模块URL
        const remoteUrl = ${overrideUrl} || '${baseUrl}';
        
        console.log('Loading remote module: ${name} from', remoteUrl);
        
        // 记录开始加载时间，用于性能监控
        const startTime = performance.now();
        
        // 对于低优先级模块，使用延迟加载策略
        const loadModule = () => {
          // 创建script标签
          const script = document.createElement('script');
          script.src = remoteUrl;
          script.type = 'text/javascript';
          script.async = true;
          script.crossOrigin = 'anonymous';
          
          // 加载成功处理
          script.onload = () => {
            const loadTime = performance.now() - startTime;
            console.log('Remote module ${name} loaded in', loadTime.toFixed(2), 'ms');
            
            // 上报性能指标
            reportRemoteModuleLoadMetric('${name}', 'success', loadTime);
            
            // 构建模块代理
            const proxy = {
              get: (request) => {
                try {
                  return window.${name}.get(request);
                } catch (e) {
                  console.error('Failed to get module ${name}/${request}:', e);
                  throw e;
                }
              },
              init: (arg) => {
                try {
                  return window.${name}.init(arg);
                } catch (e) {
                  console.error('Failed to initialize remote module ${name}:', e);
                  throw e;
                }
              }
            };
            resolve(proxy);
          };
          
          // 加载失败处理
          script.onerror = (error) => {
            const loadTime = performance.now() - startTime;
            console.error('Failed to load remote module ${name}:', error);
            
            // 上报错误指标
            reportRemoteModuleLoadMetric('${name}', 'error', loadTime, error.message);
            
            // 降级策略
            ${fallbackModule ? `resolve({
              get: () => Promise.resolve(${JSON.stringify(fallbackModule)}),
              init: () => Promise.resolve()
            });` : `reject(new Error('Failed to load remote module ${name}'));`}
          };
          
          // 超时处理
          const timeoutId = setTimeout(() => {
            script.remove();
            const loadTime = performance.now() - startTime;
            const error = new Error('Remote module ${name} load timeout');
            
            // 上报超时指标
            reportRemoteModuleLoadMetric('${name}', 'timeout', loadTime);
            
            ${fallbackModule ? `resolve({
              get: () => Promise.resolve(${JSON.stringify(fallbackModule)}),
              init: () => Promise.resolve()
            });` : `reject(error);`}
          }, 10000);
          
          script.onload = () => {
            clearTimeout(timeoutId);
            // 原有onload逻辑...
          };
          
          document.head.appendChild(script);
        };
        
        // 加载策略
        ${priority === 'high' ? `loadModule();` : `
        // 对于非高优先级模块，使用空闲时间加载或延迟加载
        if ('requestIdleCallback' in window && ${priority === 'low'}) {
          requestIdleCallback(loadModule, { timeout: 3000 });
        } else if (${lazy}) {
          // 延迟加载
          setTimeout(loadModule, 1000);
        } else {
          loadModule();
        }`}
        
        // 条件加载检查
        ${loadCondition ? `
        // 检查加载条件
        if (window.__USER_PERMISSIONS__ && !window.__USER_PERMISSIONS__.${loadCondition}) {
          console.log('Skipping remote module ${name} due to permission constraints');
          resolve({
            get: () => Promise.resolve(() => () => null),
            init: () => Promise.resolve()
          });
          return;
        }` : ''}
      })`
    };
  };
  
  /**
   * 从配置中心获取动态远程应用配置
   */
  const getDynamicRemoteAppsConfig = () => {
    // 实际项目中，这里可以从API获取配置
    // 这里返回一个空对象作为示例
    return {};
  };
  
  /**
   * 上报远程模块加载指标
   */
  const reportRemoteModuleLoadMetric = (moduleName, status, loadTime, errorMessage = '') => {
    // 实际项目中，这里可以上报到监控系统
    console.log(`Remote module metric: ${moduleName} - ${status} - ${loadTime.toFixed(2)}ms`);
  };
  
  module.exports = {
    mode: isProduction ? 'production' : 'development',
    entry: './src/bootstrap.tsx', // 使用单独的 bootstrap 文件延迟加载
    output: {
      publicPath: 'auto',
      path: resolve(__dirname, 'dist'),
      filename: isProduction ? '[name].[contenthash].js' : '[name].js',
      chunkFilename: isProduction ? '[name].[contenthash].chunk.js' : '[name].chunk.js',
      clean: true,
    },
    resolve: {
      extensions: ['.tsx', '.ts', '.jsx', '.js'],
      alias: {
        '@': resolve(__dirname, 'src'),
      },
    },
    // 性能优化配置
    optimization: {
      runtimeChunk: 'single',
      splitChunks: {
        chunks: 'all',
        minSize: 30000,
        maxSize: 250000,
        cacheGroups: {
          defaultVendors: {
            test: /[\\/]node_modules[\\/]/,
            priority: -10,
            reuseExistingChunk: true,
          },
          default: {
            minChunks: 2,
            priority: -20,
            reuseExistingChunk: true,
          },
        },
      },
    },
    plugins: [
      // Module Federation 配置
      new ModuleFederationPlugin({
        name: 'remote_app',
        filename: 'remoteEntry.js',
        // 定义要暴露的模块
        exposes: {
          './UserProfile': './src/components/UserProfile',
          './Dashboard': './src/components/Dashboard',
          './hooks': './src/hooks',
          './api': './src/api',
        },
        // 共享依赖
        shared: sharedDependencies,
        // 配置自动版本协商
        version: () => {
          // 可以从 package.json 获取版本
          return require('./package.json').version;
        },
        // 配置降级加载策略
        onError: (err) => {
          console.error('Remote app module federation error:', err);
        },
      }),
      // 全局变量定义
      new DefinePlugin({
        'process.env.APP_VERSION': JSON.stringify(require('./package.json').version),
        'process.env.IS_REMOTE': true,
      }),
      // HTML 配置（用于独立开发测试）
      new HtmlWebpackPlugin({
        template: './public/index.html',
        inject: true,
      }),
    ],
    // 开发服务器配置
    devServer: {
      port: PORT,
      historyApiFallback: true,
      hot: true,
      headers: {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
        'Access-Control-Allow-Headers': 'Origin, Content-Type, Accept, Authorization',
      },
    },
    // 边缘计算兼容配置
    experiments: {
      backCompat: false,
      topLevelAwait: true,
    },
  };
  ```

- **远程应用引导文件**：
  ```javascript
  // src/bootstrap.tsx
  // 延迟加载入口文件，避免在 Module Federation 初始化前加载
  import('./index');
  ```

- **边缘计算与微前端集成配置**：
  ```javascript
  // edge.config.js
  module.exports = {
    // 缓存策略配置
    cache: {
      // 静态资源缓存时间
      staticAssetsTTL: 60 * 60 * 24, // 24小时
      // 微应用远程入口缓存时间
      remoteEntriesTTL: 60 * 5, // 5分钟
      // 启用 Stale While Revalidate 策略
      staleWhileRevalidate: true,
    },
    // 边缘路由配置
    routing: {
      // 根据地理位置路由到最近的边缘节点
      geoRouting: true,
      // 路由规则
      rules: [
        {
          path: '/micro-apps/*',
          origin: 'https://cdn.bone.com/apps',
          cache: {
            ttl: 60 * 5,
          },
        },
      ],
    },
    // 错误处理
    errorPages: {
      500: '/edge-errors/500.html',
      404: '/edge-errors/404.html',
    },
    // 安全配置
    security: {
      cors: {
        origin: '*',
        methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
      },
      // 启用边缘级别的基本防护
      ddosProtection: true,
    },
  };
  ```

- **Service Worker 实现**：
  ```javascript
  // public/service-worker.js
  const CACHE_NAME = 'micro-app-cache-v1';
  const STATIC_ASSETS = [
    '/',
    '/index.html',
    '/manifest.json',
    '/favicon.ico',
  ];
  
  // 预缓存静态资源
  self.addEventListener('install', (event) => {
    event.waitUntil(
      caches.open(CACHE_NAME)
        .then((cache) => cache.addAll(STATIC_ASSETS))
        .then(() => self.skipWaiting())
    );
  });
  
  // 激活并清理旧缓存
  self.addEventListener('activate', (event) => {
    const currentCaches = [CACHE_NAME];
    event.waitUntil(
      caches.keys().then((cacheNames) => {
        return cacheNames.filter((cacheName) => !currentCaches.includes(cacheName));
      }).then((cachesToDelete) => {
        return Promise.all(cachesToDelete.map((cacheToDelete) => {
          return caches.delete(cacheToDelete);
        }));
      }).then(() => self.clients.claim())
    );
  });
  
  // 实现 Stale While Revalidate 策略
  self.addEventListener('fetch', (event) => {
    // 跳过非 GET 请求
    if (event.request.method !== 'GET') return;
    
    // 跳过 Chrome 扩展和 DevTools 请求
    if (event.request.url.startsWith('chrome-extension://')) return;
    
    // 处理远程入口文件（缓存时间较短）
    if (event.request.url.includes('/remoteEntry.js')) {
      event.respondWith(
        caches.match(event.request).then((cachedResponse) => {
          const fetchPromise = fetch(event.request)
            .then((networkResponse) => {
              const responseToCache = networkResponse.clone();
              caches.open(CACHE_NAME).then((cache) => {
                cache.put(event.request, responseToCache);
              });
              return networkResponse;
            })
            .catch(() => {
              // 返回缓存响应或失败页面
              return cachedResponse || caches.match('/edge-errors/offline.html');
            });
          
          // 优先返回缓存，但同时更新缓存
          return cachedResponse || fetchPromise;
        })
      );
      return;
    }
    
    // 处理其他资源
    event.respondWith(
      caches.match(event.request).then((cachedResponse) => {
        const fetchPromise = fetch(event.request)
          .then((networkResponse) => {
            // 只缓存成功的响应
            if (networkResponse && networkResponse.status === 200 && networkResponse.type === 'basic') {
              const responseToCache = networkResponse.clone();
              caches.open(CACHE_NAME).then((cache) => {
                cache.put(event.request, responseToCache);
              });
            }
            return networkResponse;
          })
          .catch(() => {
            return cachedResponse;
          });
        
        // 返回缓存或网络响应
        return cachedResponse || fetchPromise;
      })
    );
  });
  
  // 后台同步功能（用于离线操作）
  self.addEventListener('sync', (event) => {
    if (event.tag === 'sync-micro-app-data') {
      event.waitUntil(syncDataWithServer());
    }
  });
  
  // 推送通知处理
  self.addEventListener('push', (event) => {
    if (!event.data) return;
    
    try {
      const data = event.data.json();
      const options = {
        body: data.body,
        icon: '/favicon.ico',
        badge: '/badge.png',
        data: {
          url: data.url,
        },
      };
      
      event.waitUntil(
        self.registration.showNotification(data.title, options)
      );
    } catch (e) {
      console.error('Push notification error:', e);
    }
  });
  ```

- **增强的动态远程加载**：
  ```typescript
// src/utils/dynamicFederation.ts
import { logger } from './logger';
import { performance } from 'perf_hooks';

// 远程模块缓存
const remoteModuleCache = new Map<string, {
  module: any;
  timestamp: number;
  version: string;
}>();
// 加载状态跟踪
const loadingModules = new Map<string, Promise<any>>();
// 错误统计
const errorStats = new Map<string, {
  count: number;
  lastError: Error | null;
  lastAttempt: number;
}>();
// 模块健康状态
const moduleHealthStatus = new Map<string, {
  healthy: boolean;
  lastCheck: number;
  responseTime: number;
}>();

// 配置项
const CONFIG = {
  // 默认缓存时间（毫秒）
  DEFAULT_CACHE_TTL: 30 * 60 * 1000, // 30分钟
  // 最大重试次数
  DEFAULT_RETRY_COUNT: 3,
  // 默认超时时间（毫秒）
  DEFAULT_TIMEOUT: 5000,
  // 健康检查间隔（毫秒）
  HEALTH_CHECK_INTERVAL: 60 * 1000, // 1分钟
  // 错误阈值（超过此值触发健康检查）
  ERROR_THRESHOLD: 3,
};

/**
 * 加载远程模块
 * @param scope 远程作用域名称
 * @param module 模块路径
 * @param options 加载选项
 */
export const loadRemoteModule = async (
  scope: string,
  module: string,
  options: {
    retry?: number;
    timeout?: number;
    skipCache?: boolean;
    version?: string;
    onProgress?: (progress: number, status: string) => void;
    fallbackModule?: any;
  } = {}
) => {
  const {
    retry = CONFIG.DEFAULT_RETRY_COUNT,
    timeout = CONFIG.DEFAULT_TIMEOUT,
    skipCache = false,
    version = 'latest',
    onProgress,
    fallbackModule
  } = options;
  
  const cacheKey = `${scope}@${module}@${version}`;
  const baseCacheKey = `${scope}@${module}`;
  
  // 检查健康状态
  const healthStatus = moduleHealthStatus.get(baseCacheKey);
  if (healthStatus && !healthStatus.healthy) {
    const lastCheck = healthStatus.lastCheck;
    const now = Date.now();
    
    // 如果距离上次检查超过5分钟，尝试重新连接
    if (now - lastCheck > 5 * 60 * 1000) {
      logger.warn(`尝试恢复不可用模块: ${baseCacheKey}`);
      moduleHealthStatus.delete(baseCacheKey);
    } else if (fallbackModule) {
      logger.warn(`使用降级模块代替不可用模块: ${baseCacheKey}`);
      return fallbackModule;
    }
  }
  
  // 检查缓存
  if (!skipCache && remoteModuleCache.has(cacheKey)) {
    const cachedModule = remoteModuleCache.get(cacheKey)!;
    const now = Date.now();
    
    // 检查缓存是否过期
    if (now - cachedModule.timestamp < CONFIG.DEFAULT_CACHE_TTL) {
      logger.info(`使用缓存的远程模块: ${cacheKey}`);
      onProgress?.(100, 'completed_from_cache');
      return cachedModule.module;
    } else {
      logger.info(`远程模块缓存已过期: ${cacheKey}`);
      remoteModuleCache.delete(cacheKey);
    }
  }
  
  // 检查是否正在加载
  if (loadingModules.has(cacheKey)) {
    logger.info(`模块正在加载中: ${cacheKey}`);
    return loadingModules.get(cacheKey);
  }
  
  // 创建加载Promise
  const loadPromise = new Promise<any>(async (resolve, reject) => {
    let attempts = 0;
    let lastError: Error | null = null;
    
    // 重试函数
    const attemptLoad = async () => {
      attempts++;
      const startTime = performance.now();
      const loadId = `${cacheKey}_${attempts}`;
      
      logger.info(`尝试加载远程模块: ${cacheKey} (${attempts}/${retry})`);
      onProgress?.(10, `loading_attempt_${attempts}`);
      
      try {
        // 设置超时
        const timeoutId = setTimeout(() => {
          const error = new Error(`加载超时: ${cacheKey}`);
          error.name = 'LoadTimeoutError';
          throw error;
        }, timeout);
        
        // 动态注入远程脚本（如果未加载）
        if (!window[scope]) {
          onProgress?.(30, 'injecting_remote_script');
          await injectRemoteScript(scope);
        }
        
        onProgress?.(50, 'initializing_share_scope');
        
        // 初始化共享作用域
        await __webpack_init_sharing__('default');
        const container = window[scope];
        await container.init(__webpack_share_scopes__.default);
        
        onProgress?.(70, 'fetching_module_factory');
        
        // 获取模块工厂函数
        const factory = await container.get(module);
        
        // 清除超时
        clearTimeout(timeoutId);
        
        onProgress?.(90, 'creating_module_instance');
        
        // 创建模块实例
        const Module = factory();
        
        const endTime = performance.now();
        const loadTime = endTime - startTime;
        
        // 更新健康状态
        moduleHealthStatus.set(baseCacheKey, {
          healthy: true,
          lastCheck: Date.now(),
          responseTime: loadTime
        });
        
        // 缓存模块
        remoteModuleCache.set(cacheKey, {
          module: Module,
          timestamp: Date.now(),
          version
        });
        
        // 记录性能指标
        reportPerformanceMetrics({
          scope,
          module,
          version,
          loadTime,
          success: true
        });
        
        logger.info(`成功加载远程模块: ${cacheKey} (耗时: ${loadTime.toFixed(2)}ms)`);
        onProgress?.(100, 'completed');
        resolve(Module);
      } catch (error) {
        const endTime = performance.now();
        const loadTime = endTime - startTime;
        lastError = error as Error;
        
        // 记录错误统计
        const errorStat = errorStats.get(baseCacheKey) || { count: 0, lastError: null, lastAttempt: 0 };
        errorStats.set(baseCacheKey, {
          count: errorStat.count + 1,
          lastError,
          lastAttempt: Date.now()
        });
        
        // 如果错误次数超过阈值，标记为不健康
        if (errorStat.count + 1 >= CONFIG.ERROR_THRESHOLD) {
          moduleHealthStatus.set(baseCacheKey, {
            healthy: false,
            lastCheck: Date.now(),
            responseTime: 0
          });
          
          // 触发健康检查
          triggerHealthCheck(scope);
        }
        
        // 记录性能指标
        reportPerformanceMetrics({
          scope,
          module,
          version,
          loadTime,
          success: false,
          error: lastError.message
        });
        
        logger.error(`加载远程模块失败: ${cacheKey} (${lastError.name}: ${lastError.message})`);
        
        // 重试逻辑
        if (attempts < retry) {
          // 指数退避策略，添加随机抖动
          const baseDelay = Math.pow(2, attempts - 1) * 1000;
          const jitter = Math.random() * 500;
          const delay = baseDelay + jitter;
          
          logger.info(`将在 ${delay.toFixed(0)}ms 后重试加载: ${cacheKey}`);
          setTimeout(attemptLoad, delay);
        } else {
          const finalError = new Error(`加载远程模块失败 (${retry}次尝试): ${lastError?.message}`);
          finalError.name = 'LoadFailedError';
          finalError.cause = lastError;
          
          // 如果提供了降级模块，使用它
          if (fallbackModule) {
            logger.warn(`加载失败，使用降级模块: ${cacheKey}`);
            resolve(fallbackModule);
          } else {
            reject(finalError);
          }
        }
      }
    };
    
    // 开始加载
    attemptLoad();
  });
  
  // 记录加载Promise
  loadingModules.set(cacheKey, loadPromise);
  
  // 加载完成后清理
  loadPromise.finally(() => {
    loadingModules.delete(cacheKey);
  });
  
  return loadPromise;
};

/**
 * 动态注入远程脚本
 */
async function injectRemoteScript(scope: string): Promise<void> {
  // 从配置中心获取远程应用配置
  const remoteConfig = await fetchRemoteConfig(scope);
  
  if (!remoteConfig || !remoteConfig.entry) {
    throw new Error(`未找到远程应用配置: ${scope}`);
  }
  
  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = remoteConfig.entry;
    script.type = 'text/javascript';
    script.async = true;
    script.crossOrigin = 'anonymous';
    
    script.onload = () => {
      logger.info(`成功加载远程脚本: ${remoteConfig.entry}`);
      resolve();
    };
    
    script.onerror = (error) => {
      logger.error(`加载远程脚本失败: ${remoteConfig.entry}`, error);
      reject(new Error(`加载远程脚本失败: ${remoteConfig.entry}`));
    };
    
    // 支持脚本加载超时
    const timeoutId = setTimeout(() => {
      script.remove();
      reject(new Error(`加载远程脚本超时: ${remoteConfig.entry}`));
    }, 10000);
    
    script.onload = () => {
      clearTimeout(timeoutId);
      logger.info(`成功加载远程脚本: ${remoteConfig.entry}`);
      resolve();
    };
    
    document.head.appendChild(script);
  });
}

/**
 * 从配置中心获取远程应用配置
 */
async function fetchRemoteConfig(scope: string): Promise<any> {
  try {
    // 首先尝试从本地缓存获取
    const cachedConfig = localStorage.getItem(`remote_config_${scope}`);
    if (cachedConfig) {
      const { config, timestamp } = JSON.parse(cachedConfig);
      // 5分钟内的缓存有效
      if (Date.now() - timestamp < 5 * 60 * 1000) {
        return config;
      }
    }
    
    // 从配置中心获取
    const response = await fetch(`/api/config/remote-apps/${scope}`, {
      headers: {
        'Content-Type': 'application/json'
      },
      cache: 'no-store'
    });
    
    if (!response.ok) {
      throw new Error(`获取远程配置失败: ${response.status}`);
    }
    
    const config = await response.json();
    
    // 缓存配置
    localStorage.setItem(`remote_config_${scope}`, JSON.stringify({
      config,
      timestamp: Date.now()
    }));
    
    return config;
  } catch (error) {
    logger.error(`获取远程配置失败: ${scope}`, error);
    
    // 使用默认配置
    return getDefaultRemoteConfig(scope);
  }
}

/**
 * 获取默认远程配置
 */
function getDefaultRemoteConfig(scope: string): any {
  const defaultConfigs: Record<string, any> = {
    // 这里可以定义默认配置
    remote_app: {
      entry: process.env.NODE_ENV === 'development' 
        ? `//localhost:3001/remoteEntry.js`
        : `/remote-app/remoteEntry.js`
    }
  };
  
  return defaultConfigs[scope];
}

/**
 * 动态导入远程组件
 */
export const dynamicImportRemote = (
  scope: string,
  module: string,
  options?: Parameters<typeof loadRemoteModule>[2]
) => {
  return {
    // React.lazy 兼容的导入函数
    load: () => loadRemoteModule(scope, module, options).then((module) => ({
      default: module.default || module
    }))
  };
};

/**
 * 清理远程模块缓存
 */
export const clearRemoteCache = (scope?: string, module?: string) => {
  if (scope && module) {
    // 清理特定模块的所有版本缓存
    const pattern = `${scope}@${module}@`;
    Array.from(remoteModuleCache.keys()).forEach(key => {
      if (key.startsWith(pattern)) {
        remoteModuleCache.delete(key);
      }
    });
    logger.info(`已清理模块 ${scope}@${module} 的缓存`);
  } else if (scope) {
    // 清理特定作用域的缓存
    const pattern = `${scope}@`;
    Array.from(remoteModuleCache.keys()).forEach(key => {
      if (key.startsWith(pattern)) {
        remoteModuleCache.delete(key);
      }
    });
    logger.info(`已清理作用域 ${scope} 的远程模块缓存`);
  } else {
    // 清理所有缓存
    remoteModuleCache.clear();
    logger.info('已清理所有远程模块缓存');
  }
};

/**
 * 获取远程模块加载状态
 */
export const getRemoteModuleStatus = (scope: string, module: string) => {
  const cacheKey = `${scope}@${module}`;
  const versions = Array.from(remoteModuleCache.keys())
    .filter(key => key.startsWith(cacheKey))
    .map(key => {
      const [, , version] = key.split('@');
      return version;
    });
  
  return {
    isCached: versions.length > 0,
    isLoading: loadingModules.has(cacheKey),
    cachedVersions: versions,
    healthStatus: moduleHealthStatus.get(cacheKey),
    errorStats: errorStats.get(cacheKey)
  };
};

/**
 * 预加载远程模块
 */
export const preloadRemoteModules = async (
  modules: Array<{ scope: string; module: string; version?: string }>,
  priority = 'low'
): Promise<void> => {
  // 根据优先级决定预加载策略
  if (priority === 'high') {
    // 高优先级：立即并行加载
    await Promise.allSettled(
      modules.map(({ scope, module, version }) => 
        loadRemoteModule(scope, module, { version, skipCache: false })
      )
    );
  } else {
    // 低优先级：使用空闲时间加载
    if ('requestIdleCallback' in window) {
      await new Promise(resolve => {
        (window as any).requestIdleCallback(async () => {
          await Promise.allSettled(
            modules.map(({ scope, module, version }) => 
              loadRemoteModule(scope, module, { version, skipCache: false })
            )
          );
          resolve(true);
        }, { timeout: 3000 });
      });
    } else {
      // 降级到setTimeout
      await new Promise(resolve => {
        setTimeout(async () => {
          await Promise.allSettled(
            modules.map(({ scope, module, version }) => 
              loadRemoteModule(scope, module, { version, skipCache: false })
            )
          );
          resolve(true);
        }, 1000);
      });
    }
  }
};

/**
 * 触发健康检查
 */
export const triggerHealthCheck = async (scope: string): Promise<boolean> => {
  logger.info(`开始健康检查: ${scope}`);
  
  try {
    const healthCheckUrl = getHealthCheckUrl(scope);
    const response = await fetch(healthCheckUrl, {
      method: 'GET',
      timeout: 3000,
      headers: {
        'Content-Type': 'application/json'
      }
    });
    
    const isHealthy = response.ok;
    
    // 更新所有该作用域模块的健康状态
    Array.from(moduleHealthStatus.keys()).forEach(key => {
      if (key.startsWith(`${scope}@`)) {
        moduleHealthStatus.set(key, {
          healthy: isHealthy,
          lastCheck: Date.now(),
          responseTime: response.headers.get('x-response-time') ? 
            parseInt(response.headers.get('x-response-time') || '0') : 0
        });
      }
    });
    
    logger.info(`健康检查结果: ${scope} - ${isHealthy ? '健康' : '不健康'}`);
    
    // 发送健康状态通知
    if (isHealthy) {
      notifyHealthStatusChange(scope, 'recovered');
    } else {
      notifyHealthStatusChange(scope, 'degraded');
    }
    
    return isHealthy;
  } catch (error) {
    logger.error(`健康检查失败: ${scope}`, error);
    return false;
  }
};

/**
 * 获取健康检查URL
 */
function getHealthCheckUrl(scope: string): string {
  // 从环境变量或配置中获取健康检查URL
  const baseUrls = {
    development: 'http://localhost:3001',
    production: '/remote-app'
  };
  
  const env = process.env.NODE_ENV || 'development';
  const baseUrl = baseUrls[env as keyof typeof baseUrls];
  
  return `${baseUrl}/health`;
}

/**
 * 通知健康状态变更
 */
function notifyHealthStatusChange(scope: string, status: 'degraded' | 'recovered' | 'critical'): void {
  // 发送到监控服务
  logger.info(`微前端模块状态变更: ${scope} - ${status}`);
  
  // 可以集成到Sentry或其他监控服务
  if (typeof window !== 'undefined' && (window as any).Sentry) {
    (window as any).Sentry.captureMessage(`微前端模块状态变更: ${scope}`, {
      level: status === 'critical' ? 'error' : 'warning',
      tags: {
        module: scope,
        status
      }
    });
  }
  
  // 触发自定义事件
  const event = new CustomEvent('micro-frontend-health-change', {
    detail: { scope, status, timestamp: Date.now() }
  });
  window.dispatchEvent(event);
}

/**
 * 报告性能指标
 */
function reportPerformanceMetrics(data: {
  scope: string;
  module: string;
  version?: string;
  loadTime: number;
  success: boolean;
  error?: string;
}): void {
  const metricsData = {
    ...data,
    timestamp: Date.now(),
    userAgent: navigator.userAgent,
    url: window.location.href
  };
  
  // 发送到性能监控服务
  logger.info('微前端性能指标:', metricsData);
  
  // 使用Beacon API发送性能数据（不阻塞主线程）
  if (navigator.sendBeacon && data.loadTime > 1000) {
    navigator.sendBeacon('/api/metrics/micro-frontend', JSON.stringify(metricsData));
  }
}

/**
 * 初始化微前端管理器
 */
export const initializeMicroFrontendManager = () => {
  // 启动定期健康检查
  setInterval(() => {
    // 对所有已知的远程作用域进行健康检查
    const scopes = new Set<string>();
    
    Array.from(moduleHealthStatus.keys()).forEach(key => {
      const [scope] = key.split('@');
      scopes.add(scope);
    });
    
    scopes.forEach(scope => {
      triggerHealthCheck(scope);
    });
  }, CONFIG.HEALTH_CHECK_INTERVAL);
  
  // 监听网络状态变化
  window.addEventListener('online', () => {
    logger.info('网络已恢复，尝试重新连接所有微前端模块');
    // 清除错误统计，允许重试
    errorStats.clear();
  });
  
  logger.info('微前端管理器已初始化');
};

// 使用示例
export const RemoteComponentWrapper: React.FC<{
  scope: string;
  module: string;
  version?: string;
  fallback?: React.ReactNode;
  onError?: (error: Error) => void;
  onLoad?: () => void;
}> = ({ scope, module, version, fallback, onError, onLoad }) => {
  const [Component, setComponent] = React.useState<any>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<Error | null>(null);
  
  React.useEffect(() => {
    const loadComponent = async () => {
      try {
        setLoading(true);
        setError(null);
        
        const remoteComponent = await loadRemoteModule(
          scope,
          module,
          { 
            version,
            onProgress: (progress, status) => {
              logger.info(`加载进度: ${scope}@${module} - ${progress}%`, status);
            }
          }
        );
        
        setComponent(remoteComponent.default || remoteComponent);
        onLoad?.();
      } catch (err) {
        const error = err as Error;
        setError(error);
        onError?.(error);
        logger.error(`远程组件加载失败: ${scope}@${module}`, error);
      } finally {
        setLoading(false);
      }
    };
    
    loadComponent();
  }, [scope, module, version, onError, onLoad]);
  
  if (loading) {
    return fallback || <div className="remote-loading">加载中...</div>;
  }
  
  if (error || !Component) {
    return fallback || (
      <div className="remote-error">
        <h3>组件加载失败</h3>
        <p>{error?.message || '未知错误'}</p>
        <button onClick={() => window.location.reload()}>重试</button>
      </div>
    );
  }
  
  return <Component />;
};

// A/B测试支持
export const getModuleVersionForABTest = (scope: string, module: string): string => {
  // 从用户配置或特性标志服务获取用户分组
  const userGroup = getUserGroupForABTest();
  
  // 根据用户分组返回不同版本
  const versionMap: Record<string, string> = {
    'group-a': '1.0.0',
    'group-b': '1.1.0-beta',
    'group-c': 'latest'
  };
  
  return versionMap[userGroup] || 'latest';
};

/**
 * 获取用户A/B测试分组
 */
function getUserGroupForABTest(): string {
  // 从localStorage获取或生成用户分组
  let userGroup = localStorage.getItem('ab_test_group');
  
  if (!userGroup) {
    // 简单的用户分组逻辑，实际项目中可以使用更复杂的算法
    const groups = ['group-a', 'group-b', 'group-c'];
    const randomIndex = Math.floor(Math.random() * groups.length);
    userGroup = groups[randomIndex];
    localStorage.setItem('ab_test_group', userGroup);
  }
  
  return userGroup;
};
  
  // React 组件示例
  import React, { Suspense } from 'react';
  
  export const RemoteComponentWrapper = ({ scope, module, fallback = null }) => {
    const LazyRemoteComponent = React.lazy(
      () => loadRemoteModule(scope, module)
        .then(module => ({ default: module.default || module }))
        .catch(error => {
          logger.error(`远程组件加载失败: ${scope}@${module}`, error);
          // 返回错误组件
          return {
            default: () => (
              <div className="remote-component-error">
                <h3>组件加载失败</h3>
                <p>{error.message}</p>
              </div>
            )
          };
        })
    );
    
    return (
      <Suspense fallback={fallback || <div>加载中...</div>}>
        <LazyRemoteComponent />
      </Suspense>
    );
  };
  ```

### 5.2 构建和部署流程

#### 5.2.1 构建配置

- **Vite构建配置**：
  ```typescript
  // vite.config.ts
  import { defineConfig, loadEnv } from 'vite';
  import react from '@vitejs/plugin-react';
  import { resolve } from 'path';
  import tsconfigPaths from 'vite-tsconfig-paths';
  import { visualizer } from 'rollup-plugin-visualizer';
  
  export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '');
    
    return {
      plugins: [
        react(),
        tsconfigPaths(),
        mode === 'production' && visualizer({
          filename: 'stats.html',
          gzipSize: true
        })
      ],
      resolve: {
        alias: {
          '@': resolve(__dirname, './src')
        }
      },
      build: {
        outDir: 'dist',
        minify: mode === 'production' ? 'terser' : false,
        sourcemap: mode !== 'production',
        rollupOptions: {
          output: {
            manualChunks: {
              vendor: ['react', 'react-dom', 'react-router-dom'],
              antd: ['antd'],
              utils: ['lodash', 'date-fns'],
              state: ['zustand']
            }
          }
        },
        chunkSizeWarningLimit: 1000
      },
      server: {
        port: 3005,
        headers: {
          'Access-Control-Allow-Origin': '*', // 支持跨域，微前端必须
        },
        proxy: {
          '/api': {
            target: env.VITE_API_BASE_URL || 'http://localhost:8080',
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, '')
          }
        }
      }
    };
  });
  ```

- **package.json脚本**：
  ```json
  {
    "scripts": {
      "dev": "vite",
      "dev:micro": "vite --mode micro",
      "build": "tsc && vite build",
      "build:micro": "tsc && vite build --mode micro",
      "type-check": "tsc --noEmit",
      "lint": "eslint . --ext ts,tsx --report-unused-disable-directives --max-warnings 0",
      "test": "jest",
      "test:coverage": "jest --coverage",
      "preview": "vite preview",
      "preview:micro": "vite preview --mode micro",
      "prepare": "husky install"
    }
  }
  ```

#### 5.2.2 CI/CD配置

- **GitHub Actions工作流**：
  ```yaml
  # .github/workflows/ci-cd.yml
  name: CI/CD Pipeline
  
  on:
    push:
      branches: [main, develop]
      tags: ['v*.*.*']
    pull_request:
      branches: [main, develop]
  
  jobs:
    test:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v3
        - name: Setup Node.js
          uses: actions/setup-node@v3
          with:
            node-version: '18'
            cache: 'npm'
        - name: Install dependencies
          run: npm ci
        - name: TypeScript Check
          run: npm run type-check
        - name: Lint Check
          run: npm run lint
        - name: Run Tests
          run: npm run test
        - name: Upload coverage
          uses: actions/upload-artifact@v3
          with:
            name: coverage
            path: coverage/
  
    build:
      needs: test
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v3
        - name: Setup Node.js
          uses: actions/setup-node@v3
          with:
            node-version: '18'
            cache: 'npm'
        - name: Install dependencies
          run: npm ci
        - name: Build standalone version
          run: npm run build
        - name: Build micro-frontend version
          run: npm run build:micro
        - name: Upload build artifacts
          uses: actions/upload-artifact@v3
          with:
            name: build
            path: dist/
  
    deploy-dev:
      if: github.ref == 'refs/heads/develop'
      needs: build
      runs-on: ubuntu-latest
      steps:
        - name: Download build artifacts
          uses: actions/download-artifact@v3
          with:
            name: build
            path: dist/
        - name: Deploy to development environment
          uses: easingthemes/ssh-deploy@v2
          env:
            SSH_PRIVATE_KEY: ${{ secrets.SSH_PRIVATE_KEY }}
            ARGS: '-rltgoDzvO --delete'
            SOURCE: 'dist/'
            REMOTE_HOST: ${{ secrets.DEV_HOST }}
            REMOTE_USER: ${{ secrets.DEV_USER }}
            TARGET: ${{ secrets.DEV_TARGET_DIR }}
  
    deploy-prod:
      if: startsWith(github.ref, 'refs/tags/v')
      needs: build
      runs-on: ubuntu-latest
      steps:
        - name: Download build artifacts
          uses: actions/download-artifact@v3
          with:
            name: build
            path: dist/
        - name: Deploy to production
          uses: easingthemes/ssh-deploy@v2
          env:
            SSH_PRIVATE_KEY: ${{ secrets.SSH_PRIVATE_KEY }}
            ARGS: '-rltgoDzvO --delete'
            SOURCE: 'dist/'
            REMOTE_HOST: ${{ secrets.PROD_HOST }}
            REMOTE_USER: ${{ secrets.PROD_USER }}
            TARGET: ${{ secrets.PROD_TARGET_DIR }}
  ```

#### 5.2.3 环境配置管理

- **环境变量配置**：
  ```typescript
  // src/config/env.ts
  export interface EnvConfig {
    API_BASE_URL: string;
    APP_NAME: string;
    APP_VERSION: string;
    IS_PRODUCTION: boolean;
    IS_MICRO_APP: boolean;
    SENTRY_DSN?: string;
    FEATURE_FLAGS: Record<string, boolean>;
  }
  
  export const getEnvConfig = (): EnvConfig => {
    const isProduction = import.meta.env.PROD;
    const isMicroApp = import.meta.env.MODE === 'micro' || !!window.__POWERED_BY_QIANKUN__;
    
    return {
      API_BASE_URL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
      APP_NAME: import.meta.env.VITE_APP_NAME || 'React Frontend Module',
      APP_VERSION: import.meta.env.VITE_APP_VERSION || '1.0.0',
      IS_PRODUCTION: isProduction,
      IS_MICRO_APP: isMicroApp,
      SENTRY_DSN: import.meta.env.VITE_SENTRY_DSN,
      FEATURE_FLAGS: {
        DARK_MODE: import.meta.env.VITE_FEATURE_DARK_MODE === 'true',
        NEW_DASHBOARD: import.meta.env.VITE_FEATURE_NEW_DASHBOARD === 'true'
      }
    };
  };
  
  export const env = getEnvConfig();
  ```

#### 5.2.4 容器化部署

- **Docker配置**：
  ```dockerfile
  # Dockerfile
  FROM node:18-alpine as builder
  
  WORKDIR /app
  
  COPY package*.json ./
  RUN npm ci
  
  COPY . .
  
  ARG VITE_API_BASE_URL
  ARG VITE_SENTRY_DSN
  
  ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
  ENV VITE_SENTRY_DSN=${VITE_SENTRY_DSN}
  
  RUN npm run build:micro
  
  FROM nginx:alpine
  
  COPY --from=builder /app/dist /usr/share/nginx/html
  COPY nginx.conf /etc/nginx/conf.d/default.conf
  
  EXPOSE 80
  
  CMD ["nginx", "-g", "daemon off;"]
  ```

- **增强的Docker Compose配置**：
  ```yaml
  # docker-compose.yml
  version: '3.8'
  
  # 环境变量默认值定义
  x-common-env:
    &common-env
    # API相关配置
    API_BASE_URL: ${API_BASE_URL:-http://api-service:8080}
    
    # 微前端相关配置
    MICRO_APPS_URL: ${MICRO_APPS_URL:-http://micro-apps.example.com}
    
    # 性能监控配置
    SENTRY_DSN: ${SENTRY_DSN:-}
    
    # 应用环境配置
    NODE_ENV: production
    NGINX_HOST: 0.0.0.0
    NGINX_PORT: 80
    
    # 日志级别配置
    LOG_LEVEL: ${LOG_LEVEL:-info}
    
    # 特性开关配置
    ENABLE_FEATURE_X: ${ENABLE_FEATURE_X:-false}
    ENABLE_FEATURE_Y: ${ENABLE_FEATURE_Y:-true}
    
    # 限流配置
    RATE_LIMIT_ENABLED: ${RATE_LIMIT_ENABLED:-true}
    RATE_LIMIT_RPS: ${RATE_LIMIT_RPS:-100}
  
  # 服务健康检查模板
  x-healthcheck:
    &healthcheck
    interval: 30s
    timeout: 10s
    start_period: 10s
    retries: 3
    test: ['CMD', '/usr/local/bin/health-check.sh']
  
  # 资源限制模板
  x-resources:
    &resources
    limits:
      cpus: '1.0'
      memory: 512M
    reservations:
      cpus: '0.25'
      memory: 128M
  
  services:
    # 主前端服务
    react-frontend-module:
      # 构建配置
      build:
        context: .
        dockerfile: Dockerfile
        args:
          - VITE_API_BASE_URL=${API_BASE_URL:-http://api-service:8080}
          - VITE_APP_ENV=${APP_ENV:-production}
          - VITE_SENTRY_DSN=${SENTRY_DSN:-}
          - VITE_APP_VERSION=${APP_VERSION:-1.0.0}
          - VITE_BUILD_DATE=${BUILD_DATE:-}
        # 构建缓存优化
        cache_from:
          - ${DOCKER_REGISTRY}/react-frontend-module:latest
          - ${DOCKER_REGISTRY}/react-frontend-module:${APP_VERSION:-latest}
      
      # 容器配置
      container_name: react-frontend-module
      restart: unless-stopped
      
      # 环境变量
      environment:
        <<: *common-env
      
      # 端口映射
      ports:
        - "3005:80"
      
      # 健康检查
      healthcheck:
        <<: *healthcheck
      
      # 资源限制
      deploy:
        <<: *resources
        # 滚动更新配置
        update_config:
          parallelism: 2
          delay: 10s
          failure_action: rollback
          order: start-first
        # 回滚配置
        rollback_config:
          parallelism: 1
          delay: 5s
      
      # 网络配置
      networks:
        - app-network
        - api-network
      
      # 卷配置
      volumes:
        # 日志卷
        - ./logs/nginx:/var/log/nginx:rw
        # 可选：配置文件卷（开发环境使用）
        # - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro
      
      # 标签配置
      labels:
        - "traefik.enable=true"
        - "traefik.http.routers.react-frontend-module.rule=Host(`${DOMAIN:-localhost}`)"
        - "traefik.http.routers.react-frontend-module.entrypoints=web"
        - "traefik.http.services.react-frontend-module.loadbalancer.server.port=80"
        - "com.docker.compose.project=react-frontend"
        - "com.docker.compose.service=react-frontend-module"
        - "version=${APP_VERSION:-unknown}"
    
    # 可选：前端CDN服务
    frontend-cdn:
      build:
        context: ./cdn
        dockerfile: Dockerfile.cdn
      container_name: frontend-cdn
      ports:
        - "8081:80"
      volumes:
        - ./dist:/usr/share/nginx/html:ro
      networks:
        - app-network
      labels:
        - "traefik.enable=true"
        - "traefik.http.routers.cdn.rule=Host(`${CDN_DOMAIN:-cdn.localhost}`)"
        - "traefik.http.routers.cdn.entrypoints=web"
        - "traefik.http.services.cdn.loadbalancer.server.port=80"
    
    # 可选：监控服务
    prometheus:
      image: prom/prometheus:latest
      container_name: prometheus
      ports:
        - "9090:9090"
      volumes:
        - ./prometheus.yml:/etc/prometheus/prometheus.yml:ro
        - prometheus-data:/prometheus
      networks:
        - monitoring-network
      restart: unless-stopped
    
    # 可选：日志收集服务
    loki:
      image: grafana/loki:latest
      container_name: loki
      ports:
        - "3100:3100"
      networks:
        - monitoring-network
      volumes:
        - loki-data:/loki
      restart: unless-stopped
    
    # 可选：Traefik反向代理服务
    traefik:
      image: traefik:v2.9
      container_name: traefik
      ports:
        - "80:80"
        - "8080:8080"  # Traefik管理界面
      command:
        - '--providers.docker=true'
        - '--providers.docker.exposedbydefault=false'
        - '--entrypoints.web.address=:80'
        - '--api=true'
        - '--api.insecure=true'  # 仅在开发环境使用
      volumes:
        - /var/run/docker.sock:/var/run/docker.sock:ro
      networks:
        - app-network
        - api-network
      restart: unless-stopped
      labels:
        - "traefik.enable=true"
        - "traefik.http.routers.traefik.rule=Host(`${TRAEFIK_DOMAIN:-traefik.localhost}`)"
        - "traefik.http.routers.traefik.entrypoints=web"
        - "traefik.http.routers.traefik.service=api@internal"
  
  # 网络配置
  networks:
    app-network:
      driver: bridge
      ipam:
        driver: default
        config:
          - subnet: 172.28.0.0/16
            gateway: 172.28.0.1
    api-network:
      external:
        name: ${API_NETWORK_NAME:-api-network}
    monitoring-network:
      driver: bridge
  
  # 卷配置
  volumes:
    prometheus-data:
      driver: local
      driver_opts:
        type: none
        o: bind
        device: ./data/prometheus
    loki-data:
      driver: local
      driver_opts:
        type: none
        o: bind
        device: ./data/loki
  ```

### 5.3 前端可观测性

#### 5.3.1 性能监控与分析

- **核心性能指标监控**：
  ```typescript
  // src/utils/performance-monitor.ts
  export class PerformanceMonitor {
    private performanceEntries: PerformanceEntryList = [];
    
    constructor() {
      // 监听性能条目
      if ('performance' in window) {
        this.setupPerformanceObserver();
      }
    }
    
    private setupPerformanceObserver() {
      const observer = new PerformanceObserver((list) => {
        this.performanceEntries = list.getEntries();
        this.analyzePerformance();
      });
      
      // 观察关键性能指标
      observer.observe({
        type: 'navigation',
        buffered: true
      });
      
      observer.observe({
        type: 'paint',
        buffered: true
      });
      
      observer.observe({
        type: 'largest-contentful-paint',
        buffered: true
      });
      
      observer.observe({
        type: 'first-input',
        buffered: true
      });
      
      observer.observe({
        type: 'layout-shift',
        buffered: true
      });
    }
    
    private analyzePerformance() {
      // 计算并报告核心Web指标
      const lcp = this.getLCP();
      const fid = this.getFID();
      const cls = this.getCLS();
      
      // 报告性能数据
      this.reportPerformanceData({
        lcp,
        fid,
        cls,
        timestamp: Date.now()
      });
    }
    
    private getLCP(): number | null {
      // 获取最大内容绘制时间
      const lcpEntry = this.performanceEntries.find(
        entry => entry.entryType === 'largest-contentful-paint'
      );
      return lcpEntry ? lcpEntry.startTime : null;
    }
    
    private getFID(): number | null {
      // 获取首次输入延迟
      const fidEntry = this.performanceEntries.find(
        entry => entry.entryType === 'first-input'
      ) as PerformanceEventTiming;
      return fidEntry ? fidEntry.processingStart - fidEntry.startTime : null;
    }
    
    private getCLS(): number {
      // 获取累积布局偏移
      const clsEntries = this.performanceEntries.filter(
        entry => entry.entryType === 'layout-shift'
      ) as LayoutShift[];
      
      return clsEntries.reduce((total, entry) => {
        return total + (entry.hadRecentInput ? 0 : entry.value);
      }, 0);
    }
    
    private reportPerformanceData(data: Record<string, any>) {
      // 发送到监控服务
      console.log('性能指标报告:', data);
      // 可以集成到Sentry或其他监控服务
      // if (env.SENTRY_DSN) {
      //   Sentry.captureMessage('Performance metrics', {
      //     level: 'info',
      //     tags: { performance: true },
      //     extra: data
      //   });
      // }
    }
  }
  
  // 初始化性能监控
  export const performanceMonitor = new PerformanceMonitor();
  ```

- **用户体验监控**：
  - 交互延迟监控
  - 页面加载时间分析
  - 资源加载性能追踪
  - 自定义用户体验指标收集

#### 5.3.2 错误监控与日志

- **Sentry集成**：
  ```typescript
  // src/utils/sentry.ts
  import * as Sentry from '@sentry/react';
  import { BrowserTracing } from '@sentry/tracing';
  import { env } from '../config/env';
  
  export const initSentry = () => {
    if (env.SENTRY_DSN && env.IS_PRODUCTION) {
      Sentry.init({
        dsn: env.SENTRY_DSN,
        integrations: [new BrowserTracing()],
        environment: env.IS_PRODUCTION ? 'production' : 'development',
        release: `${env.APP_NAME}@${env.APP_VERSION}`,
        tracesSampleRate: 0.1,
        // 增强的错误上下文
        beforeSend(event) {
          // 添加用户上下文（脱敏处理）
          if (event.user) {
            delete event.user.password;
            delete event.user.token;
          }
          return event;
        }
      });
    }
  };
  
  export const captureError = (error: Error, context?: Record<string, unknown>) => {
    if (env.SENTRY_DSN) {
      Sentry.withScope(scope => {
        if (context) {
          Object.entries(context).forEach(([key, value]) => {
            scope.setContext(key, value as any);
          });
        }
        Sentry.captureException(error);
      });
    }
    console.error('捕获到错误:', error, context);
  };
  ```

- **日志系统实现**：
  ```typescript
  // src/utils/logger.ts
  import { env } from '../config/env';
  
  interface LogConfig {
    level: 'debug' | 'info' | 'warn' | 'error';
    captureConsole: boolean;
    logToRemote: boolean;
  }
  
  export class Logger {
    private config: LogConfig;
    
    constructor(config?: Partial<LogConfig>) {
      this.config = {
        level: 'debug',
        captureConsole: true,
        logToRemote: env.IS_PRODUCTION,
        ...config
      };
      
      if (this.config.captureConsole) {
        this.captureConsole();
      }
    }
    
    debug(message: string, ...args: any[]) {
      if (this.shouldLog('debug')) {
        console.debug(`[DEBUG] ${message}`, ...args);
        this.logToRemote('debug', message, args);
      }
    }
    
    info(message: string, ...args: any[]) {
      if (this.shouldLog('info')) {
        console.info(`[INFO] ${message}`, ...args);
        this.logToRemote('info', message, args);
      }
    }
    
    warn(message: string, ...args: any[]) {
      if (this.shouldLog('warn')) {
        console.warn(`[WARN] ${message}`, ...args);
        this.logToRemote('warn', message, args);
      }
    }
    
    error(message: string, error?: Error, ...args: any[]) {
      if (this.shouldLog('error')) {
        console.error(`[ERROR] ${message}`, error, ...args);
        this.logToRemote('error', message, { error, ...args });
      }
    }
    
    private shouldLog(level: 'debug' | 'info' | 'warn' | 'error'): boolean {
      const levels = ['debug', 'info', 'warn', 'error'];
      return levels.indexOf(level) >= levels.indexOf(this.config.level);
    }
    
    private logToRemote(level: string, message: string, data?: any) {
      if (!this.config.logToRemote) return;
      
      // 发送到远程日志服务
      // 示例使用fetch API
      fetch('/api/logs', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          level,
          message,
          timestamp: new Date().toISOString(),
          environment: env.IS_PRODUCTION ? 'production' : 'development',
          version: env.APP_VERSION,
          data: this.sanitizeData(data)
        })
      }).catch(err => {
        console.error('Failed to send log to remote:', err);
      });
    }
    
    private sanitizeData(data: any): any {
      // 移除敏感信息
      if (typeof data === 'object' && data !== null) {
        const sanitized = { ...data };
        const sensitiveKeys = ['password', 'token', 'secret', 'key'];
        
        sensitiveKeys.forEach(key => {
          if (sanitized[key]) {
            sanitized[key] = '***';
          }
        });
        
        return sanitized;
      }
      return data;
    }
    
    private captureConsole() {
      // 捕获控制台日志
      const originalConsole = console;
      
      console.log = (...args) => {
        this.info(...args);
        originalConsole.log(...args);
      };
      
      console.warn = (...args) => {
        this.warn(...args);
        originalConsole.warn(...args);
      };
      
      console.error = (...args) => {
        this.error(...args);
        originalConsole.error(...args);
      };
    }
  }
  
  // 创建全局logger实例
  export const logger = new Logger();
  ```

#### 5.3.3 用户行为分析

- **用户行为跟踪**：
  ```typescript
  // src/utils/user-tracking.ts
  import { env } from '../config/env';
  
  export interface TrackEvent {
    eventName: string;
    category: string;
    action: string;
    label?: string;
    value?: number;
    context?: Record<string, any>;
    timestamp: number;
  }
  
  export class UserTracker {
    private sessionId: string;
    private userId: string | null;
    
    constructor() {
      this.sessionId = this.generateSessionId();
      this.userId = localStorage.getItem('userId');
      
      // 监听页面卸载事件，发送未发送的事件
      window.addEventListener('beforeunload', () => {
        this.flushEvents();
      });
    }
    
    trackPageView(pageName: string, path?: string) {
      this.trackEvent({
        eventName: 'page_view',
        category: 'page',
        action: 'view',
        label: pageName,
        context: {
          path: path || window.location.pathname,
          referrer: document.referrer
        }
      });
    }
    
    trackEvent(event: Omit<TrackEvent, 'timestamp'>) {
      const fullEvent: TrackEvent = {
        ...event,
        timestamp: Date.now()
      };
      
      // 可以实时发送或批量发送
      this.sendEvent(fullEvent);
    }
    
    private sendEvent(event: TrackEvent) {
      if (!env.IS_PRODUCTION) {
        console.log('User Event:', event);
        return;
      }
      
      // 发送到分析服务
      fetch('/api/analytics/events', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          ...event,
          sessionId: this.sessionId,
          userId: this.userId,
          userAgent: navigator.userAgent
        })
      }).catch(err => {
        console.error('Failed to send event:', err);
      });
    }
    
    private flushEvents() {
      // 发送队列中的事件
      // 可以使用Navigator.sendBeacon API确保在页面卸载时发送
    }
    
    private generateSessionId(): string {
      return `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }
    
    setUserId(userId: string | null) {
      this.userId = userId;
      if (userId) {
        localStorage.setItem('userId', userId);
      } else {
        localStorage.removeItem('userId');
      }
    }
  }
  
  // 创建全局跟踪器实例
  export const userTracker = new UserTracker();
  ```

- **异常行为检测**：
  - 重复操作检测
  - 性能异常检测
  - 错误模式分析
  - 用户体验问题识别

## 6. 迁移和兼容性策略

### 6.1 与现有系统集成

#### 6.1.1 API集成策略

- **API适配层实现**：
  ```typescript
  // src/adapters/api-adapter.ts
  import axios from 'axios';
  
  // 定义通用响应格式
  interface LegacyApiResponse<T> {
    success: boolean;
    data: T;
    message: string;
    errorCode?: string;
  }
  
  // 现代API响应格式
  interface ModernApiResponse<T> {
    ok: boolean;
    result: T;
    error?: {
      code: string;
      message: string;
    };
  }
  
  export class ApiAdapter {
    private legacyClient = axios.create({
      baseURL: '/api/legacy',
      timeout: 10000
    });
    
    private modernClient = axios.create({
      baseURL: '/api',
      timeout: 10000
    });
    
    // 将旧格式响应转换为新格式
    private adaptResponse<T>(legacyResponse: LegacyApiResponse<T>): ModernApiResponse<T> {
      return {
        ok: legacyResponse.success,
        result: legacyResponse.data,
        error: legacyResponse.success ? undefined : {
          code: legacyResponse.errorCode || 'UNKNOWN_ERROR',
          message: legacyResponse.message
        }
      };
    }
    
    // 获取用户数据示例
    async getUserData(userId: string): Promise<ModernApiResponse<any>> {
      try {
        // 尝试使用新API
        try {
          const response = await this.modernClient.get(`/users/${userId}`);
          return response.data;
        } catch (modernError) {
          // 如果新API调用失败，回退到旧API
          console.warn('新API调用失败，尝试使用旧API', modernError);
          const legacyResponse = await this.legacyClient.get<LegacyApiResponse<any>>(`/user/${userId}`);
          return this.adaptResponse(legacyResponse.data);
        }
      } catch (error) {
        console.error('获取用户数据失败:', error);
        throw error;
      }
    }
    
    // 更多API适配方法...
  }
  
  export const apiAdapter = new ApiAdapter();
  ```

#### 6.1.2 认证与权限集成

- **认证集成服务**：
  ```typescript
  // src/services/auth-integration.ts
  import { useGlobalStore } from '../stores/global';
  import { eventBus } from '../utils/event-bus';
  
  export class AuthIntegrationService {
    private globalStore = useGlobalStore.getState();
    
    constructor() {
      // 监听主应用的登录事件
      eventBus.on('auth:login', this.handleMainAppLogin);
      // 监听主应用的登出事件
      eventBus.on('auth:logout', this.handleMainAppLogout);
      
      // 初始化时检查是否在微应用环境中
      if (window.__POWERED_BY_QIANKUN__) {
        this.syncWithMainApp();
      }
    }
    
    // 处理主应用登录事件
    private handleMainAppLogin = (userInfo: any, token: string) => {
      this.globalStore.setUserInfo(userInfo);
      this.globalStore.setToken(token);
      console.log('同步主应用登录状态:', userInfo);
    };
    
    // 处理主应用登出事件
    private handleMainAppLogout = () => {
      this.globalStore.setUserInfo(null);
      this.globalStore.setToken(null);
      console.log('同步主应用登出状态');
    };
    
    // 与主应用同步状态
    private syncWithMainApp() {
      // 通过全局API获取主应用的认证状态
      if (window.parent && window.parent.bone && window.parent.bone.getAuthState) {
        const authState = window.parent.bone.getAuthState();
        if (authState) {
          this.globalStore.setUserInfo(authState.userInfo);
          this.globalStore.setToken(authState.token);
        }
      }
    }
    
    // 发送登录事件到主应用
    notifyMainAppLogin(userInfo: any, token: string) {
      if (window.__POWERED_BY_QIANKUN__ && window.parent && window.parent.bone && window.parent.bone.onLogin) {
        window.parent.bone.onLogin(userInfo, token);
      }
      // 同时通过事件总线通知
      eventBus.emit('auth:login', userInfo, token);
    }
    
    // 发送登出事件到主应用
    notifyMainAppLogout() {
      if (window.__POWERED_BY_QIANKUN__ && window.parent && window.parent.bone && window.parent.bone.onLogout) {
        window.parent.bone.onLogout();
      }
      // 同时通过事件总线通知
      eventBus.emit('auth:logout');
    }
  }
  
  export const authIntegrationService = new AuthIntegrationService();
  ```

#### 6.1.3 数据格式兼容性

- **数据转换工具**：
  ```typescript
  // src/utils/data-transformer.ts
  export class DataTransformer {
    // 将旧数据格式转换为新数据格式
    static legacyToModern<T>(legacyData: any, transformationMap: Record<string, string>): T {
      const modernData: any = {};
      
      // 应用转换映射
      Object.entries(transformationMap).forEach(([modernKey, legacyKey]) => {
        if (legacyData.hasOwnProperty(legacyKey)) {
          modernData[modernKey] = legacyData[legacyKey];
        }
      });
      
      return modernData as T;
    }
    
    // 将新数据格式转换为旧数据格式
    static modernToLegacy<T>(modernData: any, transformationMap: Record<string, string>): T {
      const legacyData: any = {};
      
      // 应用反向转换映射
      Object.entries(transformationMap).forEach(([modernKey, legacyKey]) => {
        if (modernData.hasOwnProperty(modernKey)) {
          legacyData[legacyKey] = modernData[modernKey];
        }
      });
      
      return legacyData as T;
    }
    
    // 示例：用户数据转换映射
    static getUserTransformationMap() {
      return {
        id: 'user_id',
        name: 'username',
        email: 'email_address',
        role: 'user_role',
        createdAt: 'registration_date',
        lastLogin: 'last_login_time'
      };
    }
  }
  
  // 使用示例
  // const modernUser = DataTransformer.legacyToModern(legacyUser, DataTransformer.getUserTransformationMap());
  ```

### 6.2 渐进式迁移策略

#### 6.2.1 迁移路线图

1. **准备阶段**：
   - 环境搭建与配置
   - 核心架构实现
   - 共享组件库开发

2. **试点阶段**：
   - 选择低风险、低复杂度的模块作为试点
   - 实现完整的微前端集成
   - 收集反馈并调整方案

3. **渐进迁移阶段**：
   - 按业务模块逐步迁移
   - 每迁移一个模块进行充分测试
   - 保持新旧系统并行运行

4. **切换阶段**：
   - 用户引导与培训
   - 灰度发布策略
   - 全面切换与旧系统下线

#### 6.2.2 技术实现策略

- **路由控制迁移**：
  ```typescript
  // src/routes/migration-router.tsx
  import React, { useEffect, useState } from 'react';
  import { Routes, Route, useLocation, Navigate } from 'react-router-dom';
  import { FeatureFlagProvider, useFeatureFlag } from '@/providers/FeatureFlag';
  
  interface MigrationRouteProps {
    legacyPath: string;
    newComponent: React.ComponentType<any>;
    legacyComponent: React.ComponentType<any>;
    featureFlag: string;
  }
  
  const MigrationRoute: React.FC<MigrationRouteProps> = ({ 
    legacyPath, 
    newComponent: NewComponent, 
    legacyComponent: LegacyComponent, 
    featureFlag 
  }) => {
    const { pathname } = useLocation();
    const isNewVersionEnabled = useFeatureFlag(featureFlag);
    const [isMigrationEnabled, setIsMigrationEnabled] = useState(isNewVersionEnabled);
    
    // 监听特性标志变化
    useEffect(() => {
      setIsMigrationEnabled(isNewVersionEnabled);
    }, [isNewVersionEnabled]);
    
    // 可以基于用户ID、角色等进行灰度发布
    const getShouldUseNewVersion = () => {
      // 示例：只有特定用户使用新版本
      const userId = localStorage.getItem('userId');
      const betaUsers = ['user1', 'user2', 'user3'];
      
      // 特性标志优先
      if (isMigrationEnabled) return true;
      
      // 灰度用户
      if (userId && betaUsers.includes(userId)) return true;
      
      return false;
    };
    
    const shouldUseNewVersion = getShouldUseNewVersion();
    
    if (shouldUseNewVersion) {
      return <NewComponent />;
    }
    
    // 对于旧版本，可以重定向到旧系统或渲染旧组件
    if (window.__POWERED_BY_QIANKUN__) {
      // 在微前端环境中，可以渲染旧组件或重定向
      return <LegacyComponent />;
    } else {
      // 在独立运行时，可以重定向到旧系统URL
      return <Navigate to={`/legacy${pathname}`} replace />;
    }
  };
  
  // 使用示例
  // <MigrationRoute
  //   legacyPath="/user/profile"
  //   newComponent={UserProfile}
  //   legacyComponent={LegacyUserProfile}
  //   featureFlag="new_user_profile"
  // />
  ```

#### 6.2.3 数据同步机制

- **实时数据同步服务**：
  ```typescript
  // src/services/data-sync.ts
  import { eventBus } from '../utils/event-bus';
  
  export interface DataSyncOptions {
    syncInterval?: number; // 同步间隔（毫秒）
    syncOnEvent?: string[]; // 在哪些事件触发时同步
    onSyncStart?: () => void;
    onSyncComplete?: () => void;
    onSyncError?: (error: Error) => void;
  }
  
  export class DataSyncService {
    private syncInterval: NodeJS.Timeout | null = null;
    private options: DataSyncOptions;
    private isSyncing: boolean = false;
    
    constructor(private syncFunction: () => Promise<void>, options: DataSyncOptions = {}) {
      this.options = {
        syncInterval: 60000, // 默认1分钟同步一次
        syncOnEvent: [],
        ...options
      };
      
      this.setupEventListeners();
    }
    
    // 设置事件监听器
    private setupEventListeners() {
      if (this.options.syncOnEvent) {
        this.options.syncOnEvent.forEach(event => {
          eventBus.on(event, this.triggerSync);
        });
      }
    }
    
    // 触发同步
    private triggerSync = async () => {
      if (this.isSyncing) return;
      
      try {
        this.isSyncing = true;
        this.options.onSyncStart?.();
        await this.syncFunction();
        this.options.onSyncComplete?.();
      } catch (error) {
        console.error('数据同步失败:', error);
        this.options.onSyncError?.(error as Error);
      } finally {
        this.isSyncing = false;
      }
    };
    
    // 启动同步服务
    start() {
      // 立即执行一次同步
      this.triggerSync();
      
      // 设置定时同步
      if (this.options.syncInterval && !this.syncInterval) {
        this.syncInterval = setInterval(this.triggerSync, this.options.syncInterval);
      }
    }
    
    // 停止同步服务
    stop() {
      if (this.syncInterval) {
        clearInterval(this.syncInterval);
        this.syncInterval = null;
      }
    }
    
    // 手动触发同步
    async syncNow() {
      return this.triggerSync();
    }
  }
  
  // 使用示例
  // const userSyncService = new DataSyncService(
  //   async () => {
  //     // 同步用户数据的逻辑
  //     const legacyData = await legacyApi.getUserData();
  //     const modernData = DataTransformer.legacyToModern(legacyData, DataTransformer.getUserTransformationMap());
  //     await modernApi.updateUserData(modernData);
  //   },
  //   {
  //     syncInterval: 30000, // 30秒同步一次
  //     syncOnEvent: ['user:updated', 'auth:login'],
  //     onSyncComplete: () => console.log('用户数据同步完成')
  //   }
  // );
  // userSyncService.start();
  ```

#### 6.2.4 浏览器兼容性策略

- **浏览器支持配置**：
  ```typescript
  // src/config/browser-compatibility.ts
  export interface BrowserInfo {
    name: string;
    version: string;
    isSupported: boolean;
    compatibilityLevel: 'full' | 'partial' | 'none';
  }
  
  export const getBrowserInfo = (): BrowserInfo => {
    const userAgent = navigator.userAgent;
    let name = 'Unknown';
    let version = 'Unknown';
    
    // 简单的浏览器检测
    if (userAgent.indexOf('Chrome') !== -1 && userAgent.indexOf('Edg') === -1) {
      name = 'Chrome';
      version = userAgent.match(/Chrome\/(\d+)\./)?.[1] || 'Unknown';
    } else if (userAgent.indexOf('Firefox') !== -1) {
      name = 'Firefox';
      version = userAgent.match(/Firefox\/(\d+)\./)?.[1] || 'Unknown';
    } else if (userAgent.indexOf('Safari') !== -1 && userAgent.indexOf('Chrome') === -1) {
      name = 'Safari';
      version = userAgent.match(/Version\/(\d+)\./)?.[1] || 'Unknown';
    } else if (userAgent.indexOf('Edg') !== -1) {
      name = 'Edge';
      version = userAgent.match(/Edg\/(\d+)\./)?.[1] || 'Unknown';
    } else if (userAgent.indexOf('MSIE') !== -1 || userAgent.indexOf('Trident') !== -1) {
      name = 'Internet Explorer';
      version = userAgent.match(/MSIE (\d+)\./)?.[1] || '11';
    }
    
    // 判断支持程度
    let isSupported = true;
    let compatibilityLevel: 'full' | 'partial' | 'none' = 'full';
    
    if (name === 'Internet Explorer') {
      isSupported = false;
      compatibilityLevel = 'none';
    } else if (name === 'Safari' && parseInt(version) < 14) {
      isSupported = true;
      compatibilityLevel = 'partial';
    }
    
    return { name, version, isSupported, compatibilityLevel };
  };
  
  export const checkBrowserCompatibility = (): boolean => {
    const browser = getBrowserInfo();
    return browser.isSupported;
  };
  ```

## 7. 结论和建议

### 7.1 方案总结

本方案基于Bone现有代码与功能分析，设计了一套符合业界最佳实践的React前端模块方案。该方案融合了六边形架构和领域驱动设计思想，采用微前端技术实现模块化开发和集成，具有以下特点：

- **先进的架构设计**：采用六边形架构与领域驱动设计相结合的方式，实现了关注点分离和依赖倒置
- **完整的状态管理**：基于Zustand的分层状态管理，提供了清晰的数据流向和状态隔离
- **强大的组件体系**：基于原子设计和领域驱动设计的组件架构，确保了组件的可复用性和可维护性
- **灵活的API集成**：基于端口与适配器模式的API设计，支持多数据源和灵活扩展
- **完善的微前端支持**：提供了完整的微前端集成方案，支持与现有系统无缝集成
- **全面的开发规范**：包含代码规范、性能优化、测试策略、安全性等方面的最佳实践

### 7.2 实施建议

1. **分阶段实施**：
   - 第一阶段：基础设施建设（2-3周）
     - 开发环境搭建
     - 核心架构实现
     - 共享组件库开发
   - 第二阶段：试点模块开发（3-4周）
     - 选择1-2个低复杂度模块作为试点
     - 实现微前端集成
     - 收集反馈并优化
   - 第三阶段：全面迁移（根据模块数量确定，约8-12周）
     - 按业务优先级逐步迁移其他模块
     - 持续测试和优化
     - 用户培训和支持

2. **团队协作策略**：
   - 成立架构小组，负责整体架构设计和指导
   - 采用Feature Team模式，按业务领域组织开发团队
   - 建立代码审查机制，确保代码质量和一致性
   - 定期举行技术分享会议，提升团队技术水平

3. **质量保障措施**：
   - 建立完善的测试体系，包括单元测试、集成测试和端到端测试
   - 实施持续集成和持续部署，确保代码快速交付和质量
   - 引入性能监控和用户体验监控，及时发现和解决问题
   - 建立代码质量门禁，确保不符合规范的代码无法合并

4. **用户体验保障**：
   - 进行用户体验设计和测试
   - 实现灰度发布，逐步推广新版本
   - 收集用户反馈，持续优化产品
   - 提供详细的用户文档和帮助信息

### 7.3 风险评估与应对

| 风险类别 | 风险描述 | 影响程度 | 应对策略 |
|---------|---------|---------|----------|
| **技术风险** | 新架构与现有系统集成困难 | 高 | 建立完善的适配层，逐步迁移，保持兼容性 |
| **进度风险** | 开发周期延长，无法按时交付 | 中 | 分阶段实施，设置里程碑，优先级排序 |
| **质量风险** | 代码质量不达标，存在缺陷 | 高 | 严格的代码审查，自动化测试，质量门禁 |
| **兼容性风险** | 浏览器兼容性问题，功能异常 | 中 | 建立兼容性测试矩阵，提供降级方案 |
| **性能风险** | 应用性能下降，用户体验差 | 中 | 性能监控，性能优化，定期性能审查 |
| **团队风险** | 团队对新技术不熟悉，学习成本高 | 中 | 提前培训，知识分享，专家指导 |
| **业务风险** | 新系统无法满足业务需求 | 高 | 充分的需求分析，原型验证，用户反馈 |

### 7.4 未来展望

本方案不仅解决了当前的技术挑战，也为未来的发展奠定了基础。建议团队持续关注以下方向：

1. **技术创新**：
   - 探索WebAssembly在性能关键场景的应用
   - 研究机器学习技术在前端的应用
   - 关注前沿前端框架和工具的发展

2. **架构演进**：
   - 向更细粒度的微前端架构演进
   - 探索Server Components等新技术
   - 实现更智能的模块发现和加载机制

3. **用户体验提升**：
   - 引入用户体验设计系统
   - 实现更智能的用户界面
   - 提升应用的可访问性和国际化支持

4. **DevOps优化**：
   - 实现更智能的CI/CD流程
   - 自动化性能测试和监控
   - 建立更完善的混沌工程实践

通过本方案的实施，我们相信可以构建一个高性能、可维护、可扩展的现代前端应用，为用户提供卓越的体验，同时也为开发团队提供高效、愉悦的开发环境。随着技术的不断发展，本方案也将持续演进，保持其先进性和实用性。