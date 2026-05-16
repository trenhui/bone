> **⚠️ 历史草案（已废止）**  
> 前端工程与微前端的**唯一权威**为 [`bone-前端架构.md`](./bone-前端架构.md)；UI 规范见 [`frontend/frontend-ui-spec.md`](./frontend/frontend-ui-spec.md)。本文仅作归档参考，勿作为实现依据。

# 🎨 Bone 前端微前端架构设计方案

## 🎯 核心设计理念

### BONE 架构原则

```typescript
/**
 * BONE 前端架构核心原则
 * B - Business Component Based (业务组件化)
 * O - Optimized Performance (性能优化)  
 * N - Natively Decoupled (原生解耦)
 * E - Extensible & Evolvable (可扩展演进)
 */
```

### 设计原则

- **微前端架构**: 业务模块解耦，支持多团队并行开发
- **组件化设计**: 原子设计方法论，构建可复用组件体系
- **类型安全**: 全面TypeScript，确保代码质量和可维护性
- **性能优先**: 智能预加载、资源缓存、懒加载优化
- **安全隔离**: 多层级沙箱技术，确保应用间安全

## 📐 架构概览

### 混合式微前端架构

基于无界框架(wujie)实现，具备以下核心特性：

- **独立开发部署**: 微应用独立开发、测试和部署
- **技术栈无关**: 支持React、Vue、Angular等不同技术栈
- **高性能**: 智能预加载、资源缓存优化
- **安全隔离**: 沙箱技术防止样式和脚本冲突
- **统一管理**: 统一应用注册、路由管理和生命周期控制

## 🏗️ 项目结构设计

### Monorepo 结构

```
bone-frontend/
├── apps/                          # 应用目录
│   ├── main-app/                  # 主应用 (基座)
│   ├── bone-admin/                # 管理门户微应用
│   ├── bone-analytics/            # 数据分析微应用
│   ├── bone-workflow/             # 工作流引擎微应用
│   ├── bone-masterdata/           # 主数据管理微应用
│   └── bone-iam/                  # 用户中心微应用
├── packages/                      # 共享包
│   ├── ui-components/             # UI组件库
│   ├── micro-frontend-sdk/        # 微前端SDK
│   ├── shared-utils/              # 共享工具函数
│   ├── api-client/                # API客户端
│   └── theme/                     # 主题包
├── scripts/                       # 构建部署脚本
└── docs/                          # 项目文档
```

### 主应用结构 (main-app)

```
main-app/
├── src/
│   ├── core/                      # 核心模块
│   │   ├── orchestrator/          # 微前端协调器
│   │   ├── router/                # 路由系统
│   │   └── store/                 # 状态管理
│   ├── components/                # 公共组件
│   ├── pages/                     # 页面组件
│   ├── services/                  # API服务
│   ├── types/                     # 类型定义
│   └── utils/                     # 工具函数
├── public/                        # 静态资源
└── config/                        # 配置文件
```

## 🔧 核心架构实现

### 微前端协调器

```typescript
export interface MicroApplicationConfig {
  name: string;                    // 应用名称
  entry: string;                   // 应用入口地址
  activeRule: string | ((location: Location) => boolean);
  container?: string;              // 容器选择器
  sandbox?: boolean | Record<string, any>;
  priority?: number;               // 加载优先级
  props?: Record<string, any>;     // 传递给应用的属性
  preload?: boolean;               // 是否预加载
  keepAlive?: boolean;             // 是否保活
  version?: string;                // 应用版本号
}

export class MicroFrontendOrchestrator {
  private static instance: MicroFrontendOrchestrator;
  private appConfigs: Map<string, MicroApplicationConfig>;
  private initializedApps: Set<string>;

  public static getInstance(): MicroFrontendOrchestrator {
    if (!MicroFrontendOrchestrator.instance) {
      MicroFrontendOrchestrator.instance = new MicroFrontendOrchestrator();
    }
    return MicroFrontendOrchestrator.instance;
  }

  /**
   * 注册微应用
   */
  public registerApp(config: MicroApplicationConfig): boolean {
    if (!config?.name || !config.entry) {
      console.error('Invalid application configuration');
      return false;
    }

    try {
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

      this.appConfigs.set(normalizedConfig.name, normalizedConfig);
      globalEventBus.emit(AppEvents.MICRO_APP_REGISTERED, normalizedConfig);
      return true;
    } catch (error) {
      console.error(`Failed to register application ${config.name}:`, error);
      return false;
    }
  }

  /**
   * 初始化微应用
   */
  public async initializeApp(name: string, options?: {
    force?: boolean;
    timeout?: number;
  }): Promise<boolean> {
    const { force = false, timeout = 30000 } = options || {};

    if (this.initializedApps.has(name) && !force) return true;
    
    const config = this.getAppConfig(name);
    if (!config) {
      console.error(`Application config not found: ${name}`);
      return false;
    }

    try {
      globalEventBus.emit(AppEvents.MICRO_APP_LOAD_START, { name });
      await this.loadAppResources(config);
      this.initializedApps.add(name);
      globalEventBus.emit(AppEvents.MICRO_APP_LOAD_COMPLETE, { name });
      return true;
    } catch (error) {
      console.error(`Application initialization failed: ${name}`, error);
      globalEventBus.emit(AppEvents.MICRO_APP_LOAD_ERROR, { 
        name, 
        error: (error as Error).message 
      });
      return false;
    }
  }

  // 其他生命周期管理方法...
  public activateApp(name: string): void { /* 实现略 */ }
  public deactivateApp(name: string): void { /* 实现略 */ }
  public getAppConfig(name: string): MicroApplicationConfig | undefined { /* 实现略 */ }
}

export const microFrontendOrchestrator = MicroFrontendOrchestrator.getInstance();
```

### 应用初始化器

```typescript
export class AppInitializer {
  private microAppManager: MicroAppManager;
  private isInitialized: boolean = false;

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
    if (this.isInitialized) return;

    try {
      globalEventBus.emit(AppEvents.INITIALIZATION_START);
      
      // 注册默认应用
      if (options?.defaultApps?.length) {
        this.microAppManager.registerApps(options.defaultApps);
      }

      // 动态加载应用配置
      if (options?.dynamicLoad !== false) {
        await this.loadDynamicApps();
      }

      // 预加载应用
      if (options?.preloadApps?.length) {
        await this.preloadApps(options.preloadApps);
      }

      this.isInitialized = true;
      globalEventBus.emit(AppEvents.INITIALIZATION_COMPLETE);
    } catch (error) {
      console.error('应用初始化失败', error);
      globalEventBus.emit(AppEvents.INITIALIZATION_ERROR, { 
        error: (error as Error).message 
      });
      throw error;
    }
  }

  private async loadDynamicApps(): Promise<void> {
    try {
      const response = await fetch('/api/micro-apps/config');
      if (!response.ok) throw new Error(`获取应用配置失败: ${response.status}`);

      const apps: MicroAppConfig[] = await response.json();
      if (apps?.length) {
        const validApps = apps.filter(app => app.name && app.entry && app.activeRule);
        if (validApps.length) {
          this.microAppManager.registerApps(validApps);
        }
      }
    } catch (error) {
      console.error('动态加载应用配置失败', error);
      // 动态加载失败不应阻止应用启动
    }
  }

  public isReady(): boolean {
    return this.isInitialized;
  }
}

export const appInitializer = new AppInitializer();
```

## 🧩 微应用标准结构

### 微应用模板

```
micro-app/
├── src/
│   ├── bootstrap.tsx           # 微应用启动入口 (必须)
│   ├── App.tsx                 # 根应用组件
│   ├── components/             # React组件
│   ├── pages/                  # 页面组件
│   ├── router/                 # 路由配置
│   ├── store/                  # 状态管理
│   ├── services/               # API服务
│   ├── types/                  # 类型定义
│   └── utils/                  # 工具函数
├── public/                     # 静态资源
└── config/                     # 微应用配置
```

### 微应用启动入口

```typescript
// bootstrap.tsx
import React from 'react';
import ReactDOM from 'react-dom';
import App from './App';

let app: any = null;

export async function bootstrap() {
  console.log('微应用启动');
}

export async function mount(props: any) {
  console.log('微应用挂载', props);
  const container = props.container 
    ? props.container.querySelector('#root') 
    : document.getElementById('root');
  
  app = ReactDOM.render(<App {...props} />, container);
}

export async function unmount(props: any) {
  console.log('微应用卸载', props);
  if (app) {
    ReactDOM.unmountComponentAtNode(
      props.container 
        ? props.container.querySelector('#root') 
        : document.getElementById('root')
    );
  }
}

// 独立运行支持
if (!window.__POWERED_BY_QIANKUN__) {
  bootstrap().then(() => {
    mount({});
  });
}
```

## 📦 共享包设计

### UI组件库 (基于Ant Design)

```
ui-components/
├── src/
│   ├── components/
│   │   ├── Button/              # 增强Button组件
│   │   ├── Form/                # 表单组件
│   │   ├── Table/               # 表格组件
│   │   └── index.ts             # 组件导出
│   ├── hooks/                   # 组件Hooks
│   ├── theme/                   # 主题配置
│   └── types/                   # 类型定义
└── stories/                     # Storybook文档
```

### 组件封装示例

```typescript
import { Button as AntButton } from 'antd';
import type { ButtonProps } from 'antd';
import { LoadingOutlined } from '@ant-design/icons';

interface EnhancedButtonProps extends ButtonProps {
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
  const getButtonType = () => {
    const typeMap = {
      primary: 'primary',
      success: 'default',
      warning: 'default',
      error: 'default',
    };
    return typeMap[bizType || 'primary'];
  };
  
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
```

## ⚡ 性能优化策略

### 构建优化配置 (Vite)

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
      dedupe: ['antd', '@ant-design/icons'],
      // 优化解析速度
      extensions: ['.js', '.jsx', '.ts', '.tsx', '.json']
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
      },
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
              /^has-/]
          })
        ].filter(Boolean)
      }
    },
    // 构建优化配置
    build: {
      // 输出目录
      outDir: 'dist',
      // 静态资源目录
      assetsDir: 'static',
      // 增加chunkSize限制
      chunkSizeWarningLimit: 1000,
      // 配置产物清理
      emptyOutDir: true,
      // 代码分割策略
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
      },
      // 优化生产构建速度
      cache: true,
      // 启用源映射（开发环境）
      sourcemap: !isProduction,
      // 启用内联资源限制
      assetsInlineLimit: 4096,
      // 配置minify策略
      minify: isProduction ? 'terser' : false,
      // Terser配置，更好地压缩Ant Design代码
      terserOptions: {
        compress: {
          // 移除console和debugger
          drop_console: isProduction,
          drop_debugger: isProduction,
          // 优化变量名
          reduce_vars: true,
          // 移除指定的函数调用
          pure_funcs: ['console.log', 'console.info']
        },
        format: {
          // 移除注释
          comments: false
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
    // 依赖预构建优化
    optimizeDeps: {
      // 预构建关键依赖
      include: ['react', 'react-dom', 'antd/es/locale/zh_CN', '@ant-design/icons', '@bone/ui-components'],
      // 优化依赖扫描
      exclude: ['@bone/micro-frontend-sdk'],
      // 强制预构建
      force: true
    },
    // 环境变量配置
    define: {
      'process.env.NODE_ENV': JSON.stringify(mode),
      // Ant Design 全局配置
      'process.env.ANTD_THEME': JSON.stringify('bone')
    }
  };
});
```

### 运行时优化

- **懒加载**: 组件和路由按需加载
- **预加载**: 基于用户行为预测预加载资源
- **缓存策略**: 合理设置资源缓存
- **虚拟滚动**: 大数据量列表性能优化
- **应用保活**: keep-alive机制减少重复加载

## 🔒 安全策略

### 安全最佳实践

- **XSS防护**: 使用React自动转义，避免innerHTML操作
- **CSRF防护**: Token验证机制
- **安全头配置**: 设置Content-Security-Policy
- **依赖扫描**: 定期更新有漏洞的依赖包

### 微前端安全隔离

- **JavaScript沙箱**: Proxy和iframe机制实现代码隔离
- **样式隔离**: CSS Modules避免样式冲突
- **资源隔离**: 限制微应用对全局资源的访问
- **通信安全**: 跨应用通信数据验证和加密

## 🚀 部署与CI/CD

### 简化CI/CD配置

```yaml
name: Frontend CI/CD

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  quality-check:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - uses: actions/setup-node@v3
      with:
        node-version: '18'
        cache: 'yarn'
    - run: yarn install --frozen-lockfile
    - run: yarn lint
    - run: yarn test
    - run: yarn typecheck

  build-deploy:
    needs: quality-check
    if: github.event_name == 'push'
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - uses: actions/setup-node@v3
      with:
        node-version: '18'
        cache: 'yarn'
    - run: yarn install --frozen-lockfile
    - run: yarn build
    - run: ./scripts/deploy.sh ${{ github.ref == 'refs/heads/main' && 'production' || 'staging' }}
```

## 💡 架构优势总结

### 核心价值

1. **业务解耦**: 微前端架构实现业务模块独立开发和部署
2. **技术灵活性**: 支持多技术栈，适应不同业务需求
3. **性能卓越**: 智能预加载和资源优化提供流畅体验
4. **安全可靠**: 沙箱隔离和统一安全策略保障系统安全
5. **可扩展性**: 灵活的应用注册机制支持业务快速扩展

### 实施建议

- **渐进迁移**: 从非核心业务开始试点微前端化
- **规范先行**: 制定统一的开发规范和接入标准
- **性能监控**: 建立完善的性能监控体系
- **团队培训**: 提升团队微前端架构开发能力

该架构方案为Bone平台提供了坚实的技术基础，支持企业级应用的持续演进和业务创新，确保系统的高性能、安全性和可维护性。