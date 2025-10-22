# 📁 Bone 前端架构整体目录结构

## 🏗️ Monorepo 结构概览

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

## 🎯 主应用 (main-app) 详细结构

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

## 🧩 微应用 (micro-app) 标准结构

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

## 📦 共享包详细结构

### UI组件库 (ui-components)

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

### 微前端SDK (micro-frontend-sdk)

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

## 🔄 核心架构组件关系

### 架构组件层次

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

## 📝 目录结构设计说明

### 设计原则

1. **模块化组织**：按功能模块划分目录，保持高内聚低耦合
2. **关注点分离**：将组件、服务、工具等不同关注点明确分离
3. **标准化命名**：采用统一的命名规范，提高代码可读性
4. **可扩展性**：预留扩展点，支持未来功能和微应用的新增
5. **一致性**：主应用与微应用保持相似的目录结构，便于开发和维护

### 关键目录说明

- **apps/**：存放所有独立应用，包括主应用和微应用
- **packages/**：存放可复用的共享包，供所有应用使用
- **scripts/**：存放构建、部署和开发相关的脚本工具
- **docs/**：存放项目文档和API说明

### 微应用特殊要求

每个微应用必须包含以下关键文件：
- **bootstrap.tsx**：微应用的启动入口，负责与主框架集成
- **index.ts**：模块导出，便于开发和调试
- **vite.config.ts**：构建配置，必须支持微前端集成

## 🔧 技术栈配置文件

### 根目录关键配置

```
├── lerna.json                 # Lerna配置，管理Monorepo
├── package.json               # 根项目依赖配置
├── tsconfig.json              # 全局TypeScript配置
├── .eslintrc.js               # 全局ESLint配置
└── .prettierrc                # Prettier代码格式化配置
```

### 应用级配置

```
├── vite.config.ts            # Vite构建工具配置
├── tsconfig.json             # 应用级TypeScript配置
├── .env.*                    # 环境变量配置
└── jest.config.js            # 测试配置
```