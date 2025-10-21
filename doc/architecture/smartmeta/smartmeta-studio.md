
## 🏆 **SmartMeta Studio 前端架构方案**

### **🎯 架构策略：渐进式微前端**

```
smartmeta-studio/
├── 🏠 **主应用 (React + Antd)** - 核心框架
├── 📦 **核心模块** - 内置 React 组件
├── 🎯 **扩展模块** - 微应用(React/Vue)
└── 🛠️ **共享基础设施**
```

## 🚀 **1. 整体技术架构**

### **1.1 分层架构设计**
```typescript
// 📁 architecture.md
# SmartMeta Studio 前端架构

## 架构原则
1. **渐进式** - 从单体开始，按需微服务化
2. **技术栈自由** - 核心React，扩展支持多框架
3. **开发体验优先** - 统一的工具链和规范
4. **生产就绪** - 完善的监控、错误处理、性能优化

## 技术栈矩阵
| 层级 | 技术方案 | 说明 |
|------|----------|------|
| **主应用** | React 18 + TS + Antd + Vite | 统一技术栈，保证稳定性 |
| **状态管理** | Zustand + React Query | 轻量级 + 服务端状态 |
| **路由** | React Router 6 + 微前端路由 | 混合路由方案 |
| **构建工具** | Vite + Module Federation | 现代化构建体系 |
| **微前端** | wujie + 自定义加载器 | 渐进式接入 |
| **工具链** | ESLint + Prettier + Husky | 代码质量保障 |
```

### **1.2 包管理和依赖配置**
```json
// 📁 package.json
{
  "name": "smartmeta-studio",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview",
    "dev:micro": "concurrently \"npm run dev\" \"npm run dev:apps\"",
    "dev:apps": "cd apps && npm run dev:all",
    "build:all": "npm run build && npm run build:apps",
    "lint": "eslint src --ext .ts,.tsx --fix",
    "type-check": "tsc --noEmit",
    "test": "vitest",
    "test:ui": "vitest --ui",
    "analyze": "vite-bundle-analyzer"
  },
  "dependencies": {
    // React 核心
    "react": "^18.2.0",
    "react-dom": "^18.2.0",
    
    // UI 框架
    "antd": "^5.12.0",
    "@ant-design/icons": "^5.2.0",
    "@ant-design/pro-components": "^2.6.0",
    
    // 路由
    "react-router-dom": "^6.20.0",
    
    // 状态管理
    "zustand": "^4.4.0",
    "@tanstack/react-query": "^5.8.0",
    
    // 微前端
    "wujie-react": "^1.0.18",
    
    // 工具库
    "axios": "^1.5.0",
    "dayjs": "^1.11.9",
    "lodash-es": "^4.17.21",
    "immer": "^10.0.0",
    
    // 可视化
    "reactflow": "^11.8.0",
    "echarts": "^5.4.0",
    "echarts-for-react": "^3.0.2",
    
    // 工具函数
    "classnames": "^2.3.2",
    "nanoid": "^5.0.0"
  },
  "devDependencies": {
    // 构建工具
    "vite": "^5.0.0",
    "@vitejs/plugin-react": "^4.1.0",
    "typescript": "^5.2.0",
    
    // 代码质量
    "eslint": "^8.52.0",
    "@typescript-eslint/eslint-plugin": "^6.9.0",
    "prettier": "^3.0.0",
    
    // Git Hooks
    "husky": "^8.0.3",
    "lint-staged": "^15.0.0",
    
    // 测试
    "vitest": "^1.0.0",
    "@testing-library/react": "^13.4.0",
    
    // 分析工具
    "vite-bundle-analyzer": "^1.0.0"
  }
}
```

## 🏗️ **2. 项目结构设计**

### **2.1 完整目录结构**
```
smartmeta-studio/
├── 📁 public/                  # 静态资源
│   ├── favicon.ico
│   ├── manifest.json
│   └── micro-apps/            # 微应用资源
├── 📁 src/
│   ├── 📁 app/                # 应用入口
│   │   ├── main.tsx
│   │   ├── App.tsx
│   │   └── App.css
│   ├── 📁 layouts/            # 布局组件
│   │   ├── MainLayout.tsx
│   │   ├── MicroAppLayout.tsx
│   │   └── AuthLayout.tsx
│   ├── 📁 components/         # 通用组件
│   │   ├── 📁 ui/             # 基础UI组件
│   │   ├── 📁 business/       # 业务组件
│   │   ├── 📁 micro/          # 微应用相关组件
│   │   └── index.ts
│   ├── 📁 modules/            # 业务模块
│   │   ├── 📁 metadata/       # 元数据管理
│   │   ├── 📁 workflow/       # 工作流设计
│   │   ├── 📁 security/       # 权限管理
│   │   ├── 📁 monitoring/     # 系统监控
│   │   └── 📁 system/         # 系统设置
│   ├── 📁 hooks/              # 自定义Hooks
│   │   ├── useAuth.ts
│   │   ├── useMicroApp.ts
│   │   ├── usePermission.ts
│   │   └── index.ts
│   ├── 📁 stores/             # 状态管理
│   │   ├── authStore.ts
│   │   ├── appStore.ts
│   │   ├── microAppStore.ts
│   │   └── index.ts
│   ├── 📁 services/           # API服务
│   │   ├── api/
│   │   ├── types/
│   │   ├── interceptors.ts
│   │   └── index.ts
│   ├── 📁 utils/              # 工具函数
│   │   ├── request.ts
│   │   ├── auth.ts
│   │   ├── microApp.ts
│   │   └── index.ts
│   ├── 📁 types/              # 类型定义
│   │   ├── global.d.ts
│   │   ├── api.ts
│   │   ├── microApp.ts
│   │   └── index.ts
│   ├── 📁 styles/             # 样式文件
│   │   ├── globals.scss
│   │   ├── variables.scss
│   │   └── mixins.scss
│   └── 📁 config/             # 配置文件
│       ├── env.ts
│       ├── microApps.ts
│       └── theme.ts
├── 📁 apps/                   # 微应用目录
│   ├── 📁 metadata-designer/  # React微应用
│   ├── 📁 workflow-designer/  # Vue微应用
│   └── package.json
├── 📁 scripts/                # 构建脚本
├── 📁 docs/                   # 文档
├── vite.config.ts
├── tsconfig.json
└── package.json
```

### **2.2 TypeScript 配置**
```json
// 📁 tsconfig.json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "exactOptionalPropertyTypes": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"],
      "@components/*": ["src/components/*"],
      "@modules/*": ["src/modules/*"],
      "@hooks/*": ["src/hooks/*"],
      "@stores/*": ["src/stores/*"],
      "@services/*": ["src/services/*"],
      "@utils/*": ["src/utils/*"],
      "@types/*": ["src/types/*"],
      "@styles/*": ["src/styles/*"],
      "@config/*": ["src/config/*"]
    }
  },
  "include": ["src", "**/*.d.ts"],
  "exclude": ["node_modules", "dist", "apps"]
}
```

## ⚡ **3. 核心模块实现**

### **3.1 应用入口和路由**
```tsx
// 📁 src/app/main.tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { ErrorBoundary } from '@components/ui/ErrorBoundary';
import { themeConfig } from '@config/theme';
import App from './App';
import './styles/globals.scss';

// 🎯 React Query 客户端
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 5 * 60 * 1000, // 5分钟
    },
  },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <ConfigProvider theme={themeConfig}>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </ConfigProvider>
        <ReactQueryDevtools initialIsOpen={false} />
      </QueryClientProvider>
    </ErrorBoundary>
  </React.StrictMode>
);
```

### **3.2 主应用组件**
```tsx
// 📁 src/app/App.tsx
import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from '@stores/authStore';
import { MainLayout } from '@layouts/MainLayout';
import { AuthLayout } from '@layouts/AuthLayout';
import { LoadingScreen } from '@components/ui/LoadingScreen';

// 🎯 懒加载页面组件
const Login = React.lazy(() => import('@modules/auth/pages/Login'));
const MetadataDesigner = React.lazy(() => import('@modules/metadata/pages/MetadataDesigner'));
const WorkflowDesigner = React.lazy(() => import('@modules/workflow/pages/WorkflowDesigner'));

const App: React.FC = () => {
  const { isAuthenticated, isLoading } = useAuthStore();

  if (isLoading) {
    return <LoadingScreen />;
  }

  return (
    <div className="smartmeta-app">
      <Routes>
        {/* 🎯 认证路由 */}
        {!isAuthenticated ? (
          <Route path="/*" element={<AuthLayout />}>
            <Route path="login" element={<Login />} />
            <Route path="*" element={<Navigate to="/login" replace />} />
          </Route>
        ) : (
          /* 🎯 主应用路由 */
          <Route path="/*" element={<MainLayout />}>
            <Route index element={<Navigate to="/designer" replace />} />
            <Route path="designer/*" element={<MetadataDesigner />} />
            <Route path="workflow/*" element={<WorkflowDesigner />} />
            
            {/* 🎯 微应用路由 */}
            <Route path="security/*" element={
              <MicroAppContainer appName="security-manager" />
            } />
            <Route path="monitoring/*" element={
              <MicroAppContainer appName="monitoring-dashboard" />
            } />
            
            <Route path="*" element={<div>页面不存在</div>} />
          </Route>
        )}
      </Routes>
    </div>
  );
};

export default App;
```

### **3.3 主布局组件**
```tsx
// 📁 src/layouts/MainLayout.tsx
import React, { useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Layout, Menu, theme, FloatButton } from 'antd';
import {
  ApiOutlined,
  WorkflowOutlined,
  SafetyCertificateOutlined,
  DashboardOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import { useMicroAppStore } from '@stores/microAppStore';
import { AppHeader } from '@components/layout/AppHeader';
import { AppSider } from '@components/layout/AppSider';

const { Header, Sider, Content } = Layout;

export const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const { microApps } = useMicroAppStore();
  const location = useLocation();
  
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  // 🎯 导航菜单配置
  const menuItems = [
    {
      key: '/designer',
      icon: <ApiOutlined />,
      label: '元数据设计器',
      type: 'internal' as const,
    },
    {
      key: '/workflow',
      icon: <WorkflowOutlined />,
      label: '工作流设计器', 
      type: 'internal' as const,
    },
    ...microApps.map(app => ({
      key: `/${app.name}`,
      icon: getAppIcon(app.name),
      label: app.title,
      type: 'micro' as const,
      framework: app.framework,
    })),
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* 🎯 侧边栏 */}
      <AppSider
        collapsed={collapsed}
        menuItems={menuItems}
        onCollapse={setCollapsed}
      />
      
      <Layout>
        {/* 🎯 顶部导航 */}
        <AppHeader 
          collapsed={collapsed}
          onToggle={() => setCollapsed(!collapsed)}
        />
        
        {/* 🎯 主内容区域 */}
        <Content style={{ 
          margin: '16px', 
          background: colorBgContainer,
          borderRadius: borderRadiusLG,
          overflow: 'auto'
        }}>
          <React.Suspense fallback={<LoadingScreen />}>
            <Outlet />
          </React.Suspense>
          
          {/* 🎯 全局浮动按钮 */}
          <FloatButton.BackTop />
        </Content>
      </Layout>
    </Layout>
  );
};

const getAppIcon = (appName: string) => {
  const icons: Record<string, React.ReactNode> = {
    'security-manager': <SafetyCertificateOutlined />,
    'monitoring-dashboard': <DashboardOutlined />,
  };
  return icons[appName] || <ApiOutlined />;
};
```

## 🔧 **4. 状态管理设计**

### **4.1 认证状态管理**
```typescript
// 📁 src/stores/authStore.ts
import { create } from 'zustand';
import { devtools, persist } from 'zustand/middleware';

interface User {
  id: string;
  name: string;
  email: string;
  role: string;
  permissions: string[];
  avatar?: string;
}

interface AuthState {
  // State
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  
  // Actions
  login: (credentials: LoginCredentials) => Promise<void>;
  logout: () => void;
  refreshToken: () => Promise<void>;
  updateUser: (user: Partial<User>) => void;
  hasPermission: (permission: string) => boolean;
}

export const useAuthStore = create<AuthState>()(
  devtools(
    persist(
      (set, get) => ({
        // Initial state
        user: null,
        token: null,
        isAuthenticated: false,
        isLoading: false,
        
        // Actions
        login: async (credentials) => {
          set({ isLoading: true });
          try {
            const response = await authService.login(credentials);
            set({
              user: response.user,
              token: response.token,
              isAuthenticated: true,
              isLoading: false,
            });
            
            // 🎯 设置全局认证头
            setAuthHeader(response.token);
          } catch (error) {
            set({ isLoading: false });
            throw error;
          }
        },
        
        logout: () => {
          set({
            user: null,
            token: null,
            isAuthenticated: false,
          });
          clearAuthHeader();
          // 🎯 清理微应用状态
          window.$wujie?.bus.$emit('user-logout');
        },
        
        refreshToken: async () => {
          try {
            const response = await authService.refreshToken();
            set({
              token: response.token,
            });
            setAuthHeader(response.token);
          } catch (error) {
            get().logout();
            throw error;
          }
        },
        
        updateUser: (userUpdates) => {
          set((state) => ({
            user: state.user ? { ...state.user, ...userUpdates } : null,
          }));
        },
        
        hasPermission: (permission) => {
          const { user } = get();
          return user?.permissions.includes(permission) || false;
        },
      }),
      {
        name: 'auth-storage',
        partialize: (state) => ({
          user: state.user,
          token: state.token,
          isAuthenticated: state.isAuthenticated,
        }),
      }
    )
  )
);
```

### **4.2 微应用状态管理**
```typescript
// 📁 src/stores/microAppStore.ts
import { create } from 'zustand';
import { devtools } from 'zustand/middleware';

interface MicroApp {
  name: string;
  title: string;
  url: string;
  framework: 'react' | 'vue' | 'angular';
  status: 'loading' | 'ready' | 'error';
  version?: string;
  description?: string;
}

interface MicroAppState {
  // State
  microApps: MicroApp[];
  activeApp: string | null;
  appStatus: Record<string, 'loading' | 'ready' | 'error'>;
  
  // Actions
  registerApp: (app: Omit<MicroApp, 'status'>) => void;
  unregisterApp: (appName: string) => void;
  setActiveApp: (appName: string | null) => void;
  updateAppStatus: (appName: string, status: MicroApp['status']) => void;
  preloadApps: (appNames?: string[]) => Promise<void>;
  getAppConfig: (appName: string) => MicroApp | undefined;
}

export const useMicroAppStore = create<MicroAppState>()(
  devtools(
    (set, get) => ({
      // Initial state
      microApps: [],
      activeApp: null,
      appStatus: {},
      
      // Actions
      registerApp: (app) => {
        set((state) => ({
          microApps: [...state.microApps.filter(a => a.name !== app.name), {
            ...app,
            status: 'loading',
          }],
          appStatus: {
            ...state.appStatus,
            [app.name]: 'loading',
          },
        }));
      },
      
      unregisterApp: (appName) => {
        set((state) => ({
          microApps: state.microApps.filter(app => app.name !== appName),
          appStatus: Object.fromEntries(
            Object.entries(state.appStatus).filter(([name]) => name !== appName)
          ),
        }));
      },
      
      setActiveApp: (appName) => {
        set({ activeApp: appName });
        
        // 🎯 通知所有微应用活跃应用变更
        if (window.$wujie) {
          window.$wujie.bus.$emit('active-app-changed', appName);
        }
      },
      
      updateAppStatus: (appName, status) => {
        set((state) => ({
          microApps: state.microApps.map(app =>
            app.name === appName ? { ...app, status } : app
          ),
          appStatus: {
            ...state.appStatus,
            [appName]: status,
          },
        }));
      },
      
      preloadApps: async (appNames) => {
        const appsToPreload = appNames 
          ? get().microApps.filter(app => appNames.includes(app.name))
          : get().microApps;
          
        await Promise.all(
          appsToPreload.map(async (app) => {
            try {
              // 🎯 预加载微应用资源
              await preloadMicroApp(app);
              get().updateAppStatus(app.name, 'ready');
            } catch (error) {
              console.error(`预加载微应用 ${app.name} 失败:`, error);
              get().updateAppStatus(app.name, 'error');
            }
          })
        );
      },
      
      getAppConfig: (appName) => {
        return get().microApps.find(app => app.name === appName);
      },
    })
  )
);
```

## 🎯 **5. 微应用集成方案**

### **5.1 微应用容器组件**
```tsx
// 📁 src/utils/microAppSecurity.ts
import { isDev } from '@config/env';

/**
 * 沙箱类型枚举
 * 与react-frontend-module-design.md中定义保持一致
 */
export enum SandboxType {
  SNAPSHOT = 'snapshot',
  PROXY = 'proxy',
  LEGACY = 'legacy'
}

/**
 * 样式隔离级别枚举
 * 与react-frontend-module-design.md中定义保持一致
 */
export enum StyleIsolationLevel {
  NONE = 'none',
  STRICT = 'strict',
  EXPERIMENTAL = 'experimental',
  SCOPED = 'scoped'
}

/**
 * 沙箱配置接口
 * 与react-frontend-module-design.md中定义保持一致
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
 * 与react-frontend-module-design.md中定义保持一致
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
  if (env === 'development' || isDev) {
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
    snapshotSandbox: config.sandboxType === SandboxType.SNAPSHOT || config.snapshotSandbox,
    // 无界框架的代理沙箱
    proxySandbox: config.sandboxType === SandboxType.PROXY || config.proxySandbox,
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
    if (policy.disableEval && typeof window !== 'undefined') {
      // 可以通过覆盖eval函数实现
      // 注意：这需要在微应用加载前执行
      const originalEval = window.eval;
      Object.defineProperty(window, 'eval', {
        value: function() {
          throw new Error('eval is disabled in micro-app environment');
        },
        writable: false,
        configurable: false
      });
    }
  }
}

/**
 * 创建安全的fetch拦截器
 * @param appName 微应用名称
 * @param policy 安全策略
 * @returns fetch拦截器函数
 */
export function createSecureFetchInterceptor(appName: string, policy: SecurityPolicy = defaultSecurityPolicy) {
  return async (url: string, options: RequestInit = {}) => {
    // 检查是否是允许的域名
    const urlObj = new URL(url, window.location.origin);
    const domain = urlObj.hostname;
    
    // 检查域名白名单
    if (policy.allowedDomains && policy.allowedDomains.length > 0) {
      if (!policy.allowedDomains.includes(domain)) {
        console.warn(`微应用 ${appName} 尝试访问未授权域名: ${domain}`);
        throw new Error(`Domain ${domain} is not allowed`);
      }
    }
    
    // 注入应用标识头
    const headers = new Headers(options.headers);
    headers.set('X-Micro-App-Name', appName);
    headers.set('X-Request-From', 'micro-app');
    
    // 发送请求
    try {
      const response = await fetch(url, {
        ...options,
        headers
      });
      
      return response;
    } catch (error) {
      console.error(`微应用 ${appName} fetch 请求失败:`, error);
      throw error;
    }
  };
}

// 📁 src/components/micro/MicroAppContainer.tsx
import React, { useEffect, useState } from 'react';
import { Card, Alert, Button, Spin } from 'antd';
import { WujieReact } from 'wujie-react';
import { useMicroAppStore } from '@stores/microAppStore';
import { useAuthStore } from '@stores/authStore';
import { getMicroAppConfig } from '@config/microApps';
import { buildWujieSandboxConfig, applySecurityPolicy, createSecureFetchInterceptor } from '@utils/microAppSecurity';

interface MicroAppContainerProps {
  appName: string;
  className?: string;
}

export const MicroAppContainer: React.FC<MicroAppContainerProps> = ({
  appName,
  className,
}) => {
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [performance, setPerformance] = useState({
    startTime: Date.now(),
    loadTime: 0,
    mountTime: 0
  });
  
  const { user, token } = useAuthStore();
  const { updateAppStatus, setActiveApp } = useMicroAppStore();
  const appConfig = getMicroAppConfig(appName);
  
  // 构建沙箱配置
  const sandboxConfig = buildWujieSandboxConfig(appName, appConfig?.sandbox);
  
  // 创建安全fetch拦截器
  const secureFetchInterceptor = createSecureFetchInterceptor(appName);

  useEffect(() => {
    // 应用安全策略
    if (appConfig) {
      applySecurityPolicy(appName);
    }
    
    setActiveApp(appConfig ? appName : null);
    setPerformance(prev => ({ ...prev, startTime: Date.now() }));
    
    return () => {
      setActiveApp(null);
      // 记录性能指标
      console.log(`微应用 ${appName} 性能指标:`, performance);
    };
  }, [appName, appConfig, setActiveApp]);

  const handleLoad = () => {
    const currentTime = Date.now();
    const loadTime = currentTime - performance.startTime;
    
    setPerformance(prev => ({
      ...prev,
      loadTime
    }));
    
    setStatus('ready');
    updateAppStatus(appName, 'ready');
    console.log(`微应用 ${appName} 加载完成，耗时: ${loadTime}ms`);
  };

  const handleMount = () => {
    const mountTime = Date.now() - performance.startTime;
    
    setPerformance(prev => ({
      ...prev,
      mountTime
    }));
    
    console.log(`微应用 ${appName} 挂载完成，总耗时: ${mountTime}ms`);
  };

  const handleError = (err: Error) => {
    setError(err.message);
    setStatus('error');
    updateAppStatus(appName, 'error');
    console.error(`微应用 ${appName} 加载错误:`, err);
  };
  
  // 合并错误处理
  if (error || !appConfig) {
    return (
      <div className={`micro-app-error ${className}`}>
        <Alert
          message={error ? '微应用加载失败' : '微应用配置错误'}
          description={error || `未找到微应用 ${appName} 的配置`}
          type="error"
          showIcon
          action={
            <Button size="small" onClick={() => window.location.reload()}>
              重试
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className={`micro-app-container ${className}`}>
      {status === 'loading' && (
        <div className="micro-app-loading">
          <Spin size="large" tip={`加载 ${appConfig.title}...`} />
        </div>
      )}
      
      <WujieReact
        width="100%"
        height="100%"
        name={appConfig.name}
        url={appConfig.url}
        sync={true}
        alive={true}
        // 使用构建的沙箱配置
        {...sandboxConfig}
        // 自定义fetch
        fetch={secureFetchInterceptor}
        // 生命周期钩子
        beforeLoad={() => console.log(`开始加载微应用: ${appName}`)}
        onLoad={handleLoad}
        onMount={handleMount}
        onError={handleError}
        onUnmount={() => console.log(`微应用 ${appName} 卸载`)}        
        props={{
          // 🎯 传递全局状态
          globalState: {
            user,
            token,
            basePath: `/${appName}`,
            theme: 'light', // 可从主题store获取
          },
          // 🎯 传递工具方法
          utils: {
            navigate: (path: string) => {
              // 微应用内部路由跳转
            },
            showMessage: (type: string, content: string) => {
              // 消息提示
            },
          },
        }}
      />
    </div>
  );
};
```

### **5.2 微应用配置管理**
```typescript
// 📁 src/config/microApps.ts
import { MicroApp } from '@stores/microAppStore';

// 🎯 环境相关的微应用配置
const getMicroAppBaseUrl = (appName: string): string => {
  const baseUrls = {
    development: {
      'security-manager': '//localhost:3003',
      'monitoring-dashboard': '//localhost:3004',
    },
    production: {
      'security-manager': 'https://security.smartmeta.com',
      'monitoring-dashboard': 'https://monitoring.smartmeta.com',
    },
  };
  
  const env = import.meta.env.MODE;
  return baseUrls[env as keyof typeof baseUrls]?.[appName] || '';
};

// 🎯 微应用注册配置
export const microAppConfigs: Omit<MicroApp, 'status'>[] = [
  {
    name: 'security-manager',
    title: '权限管理器',
    framework: 'react',
    description: '管理用户权限和角色',
    version: '1.0.0',
    get url() {
      return getMicroAppBaseUrl('security-manager');
    },
  },
  {
    name: 'monitoring-dashboard',
    title: '系统监控',
    framework: 'vue', 
    description: '系统运行状态监控和告警',
    version: '1.0.0',
    get url() {
      return getMicroAppBaseUrl('monitoring-dashboard');
    },
  },
];

// 🎯 获取微应用配置
export const getMicroAppConfig = (appName: string) => {
  return microAppConfigs.find(config => config.name === appName);
};

// 🎯 初始化注册微应用
export const initializeMicroApps = (registerApp: (app: Omit<MicroApp, 'status'>) => void) => {
  microAppConfigs.forEach(registerApp);
};
```

## 🔄 **6. 跨框架通信方案**

### **6.1 全局事件总线**
```typescript
// 📁 src/utils/eventBus.ts
type EventCallback = (data: any) => void;

class GlobalEventBus {
  private events: Map<string, EventCallback[]> = new Map();
  private wujieBus: any = null;

  constructor() {
    this.initializeWujieBus();
  }

  private initializeWujieBus() {
    if (typeof window !== 'undefined' && window.$wujie) {
      this.wujieBus = window.$wujie.bus;
    }
  }

  // 🎯 监听事件（主应用和微应用）
  on(event: string, callback: EventCallback) {
    // 本地事件监听
    if (!this.events.has(event)) {
      this.events.set(event, []);
    }
    this.events.get(event)!.push(callback);

    // wujie 事件监听
    if (this.wujieBus) {
      this.wujieBus.$on(event, callback);
    }

    return () => this.off(event, callback);
  }

  // 🎯 触发事件
  emit(event: string, data?: any) {
    // 触发本地事件
    const callbacks = this.events.get(event);
    if (callbacks) {
      callbacks.forEach(callback => callback(data));
    }

    // 通过 wujie 广播到所有微应用
    if (this.wujieBus) {
      this.wujieBus.$emit(event, data);
    }
  }

  // 🎯 移除监听
  off(event: string, callback?: EventCallback) {
    // 移除本地监听
    if (callback) {
      const callbacks = this.events.get(event);
      if (callbacks) {
        const index = callbacks.indexOf(callback);
        if (index > -1) {
          callbacks.splice(index, 1);
        }
      }
    } else {
      this.events.delete(event);
    }

    // 移除 wujie 监听
    if (this.wujieBus) {
      if (callback) {
        this.wujieBus.$off(event, callback);
      } else {
        this.wujieBus.$off(event);
      }
    }
  }

  // 🎯 向特定微应用发送消息
  sendToApp(appName: string, event: string, data: any) {
    if (this.wujieBus && this.wujieBus.props[appName]) {
      this.wujieBus.props[appName].bus.$emit(event, data);
    }
  }
}

// 🎯 全局单例
export const globalEventBus = new GlobalEventBus();

// 🎯 常用事件类型
export const AppEvents = {
  // 用户相关
  USER_LOGIN: 'user-login',
  USER_LOGOUT: 'user-logout',
  USER_UPDATE: 'user-update',
  
  // 主题相关
  THEME_CHANGE: 'theme-change',
  
  // 应用状态
  APP_LOADED: 'app-loaded',
  APP_ERROR: 'app-error',
  
  // 数据同步
  DATA_REFRESH: 'data-refresh',
  DATA_UPDATE: 'data-update',
  
  // 业务事件
  WORKFLOW_SAVED: 'workflow-saved',
  METADATA_CHANGED: 'metadata-changed',
  PERMISSION_UPDATED: 'permission-updated',
} as const;
```

### **6.2 通信 Hook**
```typescript
// 📁 src/hooks/useEventBus.ts
import { useEffect, useRef } from 'react';
import { globalEventBus, AppEvents } from '@utils/eventBus';

export const useEventBus = () => {
  const unsubscribeCallbacks = useRef<(() => void)[]>([]);

  useEffect(() => {
    return () => {
      // 🎯 组件卸载时清理所有监听
      unsubscribeCallbacks.current.forEach(unsubscribe => unsubscribe());
      unsubscribeCallbacks.current = [];
    };
  }, []);

  const on = (event: string, callback: (data: any) => void) => {
    const unsubscribe = globalEventBus.on(event, callback);
    unsubscribeCallbacks.current.push(unsubscribe);
    return unsubscribe;
  };

  const emit = (event: string, data?: any) => {
    globalEventBus.emit(event, data);
  };

  const sendToApp = (appName: string, event: string, data: any) => {
    globalEventBus.sendToApp(appName, event, data);
  };

  return {
    on,
    emit,
    sendToApp,
    AppEvents,
  };
};

// 🎯 特定事件的 Hook
export const useAppEvents = () => {
  const { on, emit } = useEventBus();

  const onUserUpdate = (callback: (user: any) => void) => {
    return on(AppEvents.USER_UPDATE, callback);
  };

  const onThemeChange = (callback: (theme: string) => void) => {
    return on(AppEvents.THEME_CHANGE, callback);
  };

  const emitDataRefresh = (dataType: string) => {
    emit(AppEvents.DATA_REFRESH, { type: dataType, timestamp: Date.now() });
  };

  return {
    onUserUpdate,
    onThemeChange,
    emitDataRefresh,
  };
};
```

## 🎨 **7. 样式和主题系统**

### **7.1 主题配置**
```typescript
// 📁 src/config/theme.ts
import { ThemeConfig } from 'antd';

export const themeConfig: ThemeConfig = {
  token: {
    // 🎯 品牌色
    colorPrimary: '#1890ff',
    colorSuccess: '#52c41a',
    colorWarning: '#faad14',
    colorError: '#ff4d4f',
    colorInfo: '#1890ff',
    
    // 🎯 字体
    fontSize: 14,
    fontSizeLG: 16,
    fontSizeSM: 12,
    
    // 🎯 圆角
    borderRadius: 6,
    borderRadiusLG: 8,
    borderRadiusSM: 4,
    
    // 🎯 间距
    controlHeight: 32,
    controlHeightLG: 40,
    controlHeightSM: 24,
  },
  components: {
    // 🎯 Layout 组件定制
    Layout: {
      headerBg: '#001529',
      siderBg: '#001529',
      bodyBg: '#f0f2f5',
    },
    
    // 🎯 Card 组件定制
    Card: {
      borderRadiusLG: 8,
      boxShadowTertiary: '0 1px 2px -2px rgba(0, 0, 0, 0.16), 0 3px 6px 0 rgba(0, 0, 0, 0.12), 0 5px 12px 4px rgba(0, 0, 0, 0.09)',
    },
    
    // 🎯 Table 组件定制
    Table: {
      borderRadius: 6,
      headerBg: '#fafafa',
      headerColor: '#000000d9',
    },
    
    // 🎯 Menu 组件定制
    Menu: {
      itemBorderRadius: 6,
      subMenuItemBorderRadius: 6,
      itemHoverBg: 'rgba(24, 144, 255, 0.1)',
      itemSelectedBg: 'rgba(24, 144, 255, 0.15)',
    },
  },
};

// 🎯 暗黑主题
export const darkThemeConfig: ThemeConfig = {
  ...themeConfig,
  token: {
    ...themeConfig.token,
    colorBgBase: '#000',
    colorTextBase: '#fff',
  },
  components: {
    Layout: {
      headerBg: '#1f1f1f',
      siderBg: '#1f1f1f',
      bodyBg: '#141414',
    },
  },
};
```

### **7.2 全局样式**
```scss
// 📁 src/styles/globals.scss
// 🎯 CSS 变量定义
:root {
  // 颜色系统
  --smartmeta-primary: #1890ff;
  --smartmeta-success: #52c41a;
  --smartmeta-warning: #faad14;
  --smartmeta-error: #ff4d4f;
  
  // 中性色
  --smartmeta-gray-1: #ffffff;
  --smartmeta-gray-2: #fafafa;
  --smartmeta-gray-3: #f5f5f5;
  --smartmeta-gray-4: #f0f0f0;
  --smartmeta-gray-5: #d9d9d9;
  --smartmeta-gray-6: #bfbfbf;
  --smartmeta-gray-7: #8c8c8c;
  --smartmeta-gray-8: #595959;
  --smartmeta-gray-9: #434343;
  --smartmeta-gray-10: #262626;
  --smartmeta-gray-11: #1f1f1f;
  --smartmeta-gray-12: #141414;
  --smartmeta-gray-13: #000000;
  
  // 间距
  --smartmeta-spacing-xs: 4px;
  --smartmeta-spacing-sm: 8px;
  --smartmeta-spacing-md: 16px;
  --smartmeta-spacing-lg: 24px;
  --smartmeta-spacing-xl: 32px;
  --smartmeta-spacing-xxl: 48px;
  
  // 阴影
  --smartmeta-shadow-1: 0 1px 2px -2px rgba(0, 0, 0, 0.16), 0 3px 6px 0 rgba(0, 0, 0, 0.12), 0 5px 12px 4px rgba(0, 0, 0, 0.09);
  --smartmeta-shadow-2: 0 3px 6px -4px rgba(0, 0, 0, 0.12), 0 6px 16px 0 rgba(0, 0, 0, 0.08), 0 9px 28px 8px rgba(0, 0, 0, 0.05);
  
  // 边框圆角
  --smartmeta-border-radius: 6px;
  --smartmeta-border-radius-lg: 8px;
}

// 🎯 全局重置和基础样式
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html, body {
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  font-size: 14px;
  line-height: 1.5715;
  color: rgba(0, 0, 0, 0.85);
  background-color: #f0f2f5;
}

#root {
  height: 100%;
}

// 🎯 SmartMeta Studio 应用样式
.smartmeta-app {
  height: 100%;
  
  // 布局样式
  .smartmeta-layout {
    min-height: 100vh;
    
    .ant-layout-sider {
      box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
    }
  }
  
  // 设计器样式
  .smartmeta-designer {
    background: var(--smartmeta-gray-3);
    padding: var(--smartmeta-spacing-md);
    
    .designer-canvas {
      background: white;
      border-radius: var(--smartmeta-border-radius-lg);
      box-shadow: var(--smartmeta-shadow-1);
    }
  }
  
  // 微应用容器样式
  .micro-app-container {
    position: relative;
    height: 100%;
    
    .micro-app-loading {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      z-index: 10;
    }
  }
  
  // 卡片样式
  .smartmeta-card {
    transition: all 0.3s ease;
    
    &:hover {
      box-shadow: var(--smartmeta-shadow-2);
      transform: translateY(-2px);
    }
    
    &.highlight {
      border: 1px solid var(--smartmeta-primary);
    }
  }
  
  // 工具类
  .text-ellipsis {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  
  .flex-center {
    display: flex;
    align-items: center;
    justify-content: center;
  }
  
  .full-height {
    height: 100%;
  }
}
```

## ⚡ **8. 构建和部署配置**

### **8.1 Vite 配置**
```typescript
// 📁 vite.config.ts
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { createHtmlPlugin } from 'vite-plugin-html';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  
  return {
    plugins: [
      react(),
      createHtmlPlugin({
        inject: {
          data: {
            title: 'SmartMeta Studio - 智能元数据引擎',
            description: '企业级元数据驱动开发平台',
          },
        },
      }),
    ],
    
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
        '@components': path.resolve(__dirname, './src/components'),
        '@modules': path.resolve(__dirname, './src/modules'),
        '@hooks': path.resolve(__dirname, './src/hooks'),
        '@stores': path.resolve(__dirname, './src/stores'),
        '@services': path.resolve(__dirname, './src/services'),
        '@utils': path.resolve(__dirname, './src/utils'),
        '@types': path.resolve(__dirname, './src/types'),
        '@styles': path.resolve(__dirname, './src/styles'),
        '@config': path.resolve(__dirname, './src/config'),
      },
    },
    
    server: {
      port: 3000,
      open: true,
      cors: true,
      proxy: {
        '/api': {
          target: env.VITE_API_BASE_URL || 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
        '/micro': {
          target: env.VITE_MICRO_APP_BASE_URL || 'http://localhost:3001',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/micro/, ''),
        },
      },
    },
    
    build: {
      outDir: 'dist',
      sourcemap: mode !== 'production',
      minify: 'esbuild',
      rollupOptions: {
        output: {
          manualChunks: {
            'react-vendor': ['react', 'react-dom'],
            'antd-vendor': ['antd', '@ant-design/icons', '@ant-design/pro-components'],
            'utils-vendor': ['lodash-es', 'dayjs', 'axios'],
            'visualization-vendor': ['reactflow', 'echarts', 'echarts-for-react'],
          },
          chunkFileNames: 'assets/js/[name]-[hash].js',
          entryFileNames: 'assets/js/[name]-[hash].js',
          assetFileNames: 'assets/[ext]/[name]-[hash].[ext]',
        },
      },
      chunkSizeWarningLimit: 1000,
    },
    
    optimizeDeps: {
      include: ['react', 'react-dom', 'antd', 'lodash-es'],
    },
    
    // 🎯 环境变量
    define: {
      __APP_VERSION__: JSON.stringify(process.env.npm_package_version),
    },
  };
});
```

### **8.2 环境配置**
```typescript
// 📁 src/config/env.ts
interface AppConfig {
  apiBaseUrl: string;
  microAppBaseUrl: string;
  enableMock: boolean;
  enableDevTools: boolean;
  appVersion: string;
}

// 🎯 环境配置
const envConfigs = {
  development: {
    apiBaseUrl: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
    microAppBaseUrl: import.meta.env.VITE_MICRO_APP_BASE_URL || 'http://localhost:3001',
    enableMock: true,
    enableDevTools: true,
    appVersion: import.meta.env.VITE_APP_VERSION || '1.0.0',
  },
  production: {
    apiBaseUrl: import.meta.env.VITE_API_BASE_URL || 'https://api.smartmeta.bone.com',
    microAppBaseUrl: import.meta.env.VITE_MICRO_APP_BASE_URL || 'https://micro.smartmeta.bone.com',
    enableMock: false,
    enableDevTools: false,
    appVersion: import.meta.env.VITE_APP_VERSION || '1.0.0',
  },
};

export const currentEnv = import.meta.env.MODE || 'development';
export const config: AppConfig = envConfigs[currentEnv as keyof typeof envConfigs];
```

## 🚀 **9. 开发工具和规范**

### **9.1 ESLint 配置**
```json
// 📁 .eslintrc.js
module.exports = {
  root: true,
  env: {
    browser: true,
    es2020: true,
  },
  extends: [
    'eslint:recommended',
    '@typescript-eslint/recommended',
    '@typescript-eslint/recommended-requiring-type-checking',
  ],
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
    project: './tsconfig.json',
  },
  plugins: ['@typescript-eslint', 'react', 'react-hooks'],
  rules: {
    // React
    'react-hooks/rules-of-hooks': 'error',
    'react-hooks/exhaustive-deps': 'warn',
    
    // TypeScript
    '@typescript-eslint/no-unused-vars': 'error',
    '@typescript-eslint/explicit-function-return-type': 'off',
    '@typescript-eslint/explicit-module-boundary-types': 'off',
    '@typescript-eslint/no-explicit-any': 'warn',
    '@typescript-eslint/no-floating-promises': 'error',
    
    // 代码风格
    'prefer-const': 'error',
    'no-var': 'error',
    'object-shorthand': 'error',
    'prefer-template': 'error',
  },
};
```

### **9.2 Git Hooks 配置**
```javascript
// 📁 .husky/pre-commit
#!/usr/bin/env sh
. "$(dirname -- "$0")/_/husky.sh"

npm run lint
npm run type-check
npm run test:related -- --passWithNoTests
```

```json
// 📁 .lintstagedrc.json
{
  "*.{ts,tsx}": [
    "eslint --fix",
    "prettier --write"
  ],
  "*.{js,jsx}": [
    "eslint --fix", 
    "prettier --write"
  ],
  "*.{json,md,html,css,scss}": [
    "prettier --write"
  ]
}
```

---

## 🏆 **架构总结和演进路线**

### **🎯 第一阶段：单体应用 (1-3个月)**
- ✅ 核心功能：元数据设计器、工作流设计器
- ✅ 技术栈：React + Antd + Zustand
- ✅ 目标：快速上线，验证产品价值

### **🎯 第二阶段：微前端准备 (4-6个月)**
- ✅ 引入 wujie 微前端框架
- ✅ 开发权限管理微应用 (React)
- ✅ 建立跨框架通信规范

### **🎯 第三阶段：全面微服务化 (7-12个月)**
- ✅ 开发系统监控微应用 (Vue)
- ✅ 建立独立部署流水线
- ✅ 完善微应用治理体系

### **🎯 长期演进**
- 🔄 更多技术栈支持 (Angular、Svelte)
- 🔄 微应用市场机制
- 🔄 低代码平台集成

这个架构方案既保证了前期的开发效率，又为长期的技术演进留足了空间，是 SmartMeta Studio 的理想选择。