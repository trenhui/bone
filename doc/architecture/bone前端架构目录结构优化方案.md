# 📁 Bone 前端架构目录结构优化方案

## 🏗️ 优化设计理念

### 核心设计原则
- **命名一致性**：与Java后端架构保持命名规范一致性
- **分层架构**：清晰的职责分离和依赖管理
- **模块化设计**：高内聚、低耦合的模块组织
- **可扩展性**：支持业务和技术的平滑演进
- **协作友好**：优化团队协作和开发体验

### 命名规范
- **目录名**：采用小写、短横线分隔（kebab-case）
- **应用名**：采用 micro-app- 前缀标识微应用
- **共享包**：采用 @bone/ 命名空间，清晰标识
- **配置文件**：集中管理，便于维护

## 📁 优化后的Monorepo结构

```
bone-frontend/                  # 前端根目录
├── apps/                       # 应用目录
│   ├── main-app/               # 主应用 (基座应用)
│   ├── micro-app-admin/        # 管理门户微应用
│   ├── micro-app-analytics/    # 数据分析微应用
│   ├── micro-app-workflow/     # 工作流引擎微应用
│   ├── micro-app-user/         # 用户中心微应用
│   └── micro-app-template/     # 微应用模板（新增）
├── packages/                   # 共享包目录
│   ├── @bone/ui-components/    # UI组件库（使用命名空间）
│   ├── @bone/micro-frontend-sdk/ # 微前端SDK
│   ├── @bone/shared-utils/     # 共享工具函数
│   ├── @bone/api-client/       # API客户端
│   ├── @bone/eslint-config/    # ESLint配置
│   ├── @bone/hooks/            # 共享Hooks（新增）
│   ├── @bone/theme/            # 主题系统（新增）
│   └── @bone/i18n/             # 国际化工具（新增）
├── scripts/                    # 构建和部署脚本
│   ├── build/                  # 构建相关脚本
│   ├── deploy/                 # 部署相关脚本
│   ├── dev/                    # 开发环境脚本
│   ├── test/                   # 测试相关脚本
│   └── utils/                  # 脚本工具函数
├── docs/                       # 文档
│   ├── architecture/           # 架构文档
│   ├── api/                    # API文档
│   ├── components/             # 组件文档
│   └── guides/                 # 开发指南
├── configs/                    # 全局配置（新增）
│   ├── webpack/                # Webpack配置
│   ├── vite/                   # Vite配置
│   ├── tsconfig/               # TypeScript配置
│   └── environment/            # 环境变量配置
├── .github/                    # GitHub配置
│   ├── workflows/              # CI/CD工作流
│   └── ISSUE_TEMPLATE/         # Issue模板
├── .husky/                     # Git hooks配置
├── .vscode/                    # VSCode配置
├── .eslintrc.js                # 根目录ESLint配置
├── .prettierrc                 # Prettier配置
├── lerna.json                  # Lerna配置
├── package.json                # 根目录package.json
├── tsconfig.json               # 根目录TypeScript配置
└── README.md                   # 项目说明文档
```

## 🎯 主应用 (main-app) 优化结构

```
main-app/
├── public/                     # 静态资源目录
│   ├── index.html             # HTML入口文件
│   ├── favicon.ico            # 网站图标
│   ├── manifest.json          # PWA配置文件
│   └── assets/                # 公共静态资源
├── src/
│   ├── bootstrap/             # 应用引导层（新增）
│   │   ├── index.tsx          # 应用启动入口
│   │   ├── theme-registry.ts  # 主题注册
│   │   └── i18n-registry.ts   # 国际化注册
│   ├── core/                  # 核心模块
│   │   ├── orchestrator/      # 微前端协调器
│   │   ├── router/            # 路由系统
│   │   ├── store/             # 状态管理
│   │   ├── theme/             # 主题配置
│   │   └── security/          # 安全模块
│   ├── layout/                # 布局组件（从components中提取）
│   │   ├── MainLayout/        # 主布局
│   │   ├── Header/            # 头部组件
│   │   ├── Sidebar/           # 侧边栏组件
│   │   └── Footer/            # 底部组件
│   ├── components/            # UI组件
│   │   ├── common/            # 通用组件
│   │   ├── business/          # 业务组件
│   │   └── micro-app/         # 微应用相关组件
│   ├── pages/                 # 页面组件
│   │   ├── Home/              # 首页
│   │   ├── Login/             # 登录页
│   │   ├── Error/             # 错误页面
│   │   └── Dashboard/         # 仪表盘
│   ├── services/              # 服务层
│   │   ├── api/               # API服务
│   │   │   ├── authService.ts # 认证服务
│   │   │   └── userService.ts # 用户服务
│   │   ├── micro-app/         # 微应用服务
│   │   └── event/             # 事件服务
│   ├── hooks/                 # 自定义Hooks
│   ├── utils/                 # 工具函数
│   ├── constants/             # 常量定义（新增）
│   ├── types/                 # 类型定义
│   ├── assets/                # 资源文件
│   ├── config/                # 配置文件
│   │   ├── app.config.ts      # 应用配置
│   │   ├── micro-apps.config.ts # 微应用配置
│   │   └── security.config.ts # 安全配置
│   ├── i18n/                  # 国际化资源（新增）
│   │   ├── en/                # 英文资源
│   │   ├── zh/                # 中文资源
│   │   └── index.ts           # 国际化配置
│   └── App.tsx                # 应用根组件
├── tests/                     # 测试目录
│   ├── unit/                  # 单元测试
│   ├── integration/           # 集成测试
│   └── e2e/                   # 端到端测试
├── .env.development           # 开发环境变量
├── .env.production            # 生产环境变量
├── .env.staging               # 预发环境变量
├── babel.config.js            # Babel配置
├── jest.config.js             # Jest配置
├── package.json               # 依赖配置
├── tsconfig.json              # TypeScript配置
├── vite.config.ts             # Vite配置
└── README.md                  # 项目说明
```

## 🧩 微应用 (micro-app) 优化结构

```
micro-app-example/
├── public/                     # 静态资源
├── src/
│   ├── bootstrap/             # 微应用引导层（新增）
│   │   ├── index.tsx          # 微应用启动入口
│   │   └── lifecycle.ts       # 生命周期管理
│   ├── components/            # React组件
│   │   ├── common/            # 通用组件
│   │   └── business/          # 业务组件
│   ├── pages/                 # 页面组件
│   ├── router/                # 路由配置
│   │   ├── routes.tsx         # 路由定义
│   │   └── index.tsx          # 路由配置
│   ├── store/                 # 状态管理
│   │   ├── slices/            # Redux Toolkit slices
│   │   ├── selectors/         # 选择器
│   │   └── index.ts           # store配置
│   ├── services/              # 服务层
│   │   ├── api/               # API服务
│   │   └── event/             # 事件服务
│   ├── hooks/                 # 自定义Hooks
│   ├── utils/                 # 工具函数
│   ├── constants/             # 常量定义（新增）
│   ├── types/                 # 类型定义
│   ├── assets/                # 静态资源
│   ├── config/                # 微应用配置
│   ├── i18n/                  # 国际化资源（新增）
│   │   ├── en/                # 英文资源
│   │   ├── zh/                # 中文资源
│   │   └── index.ts           # 国际化配置
│   ├── App.tsx                # 根应用组件
│   └── entry.tsx              # 微应用入口文件（替代bootstrap.tsx）
├── .eslintrc.js               # ESLint配置
├── package.json               # 依赖和脚本配置
├── tsconfig.json              # TypeScript配置
├── vite.config.ts             # Vite配置
└── README.md                  # 项目文档
```

## 📦 共享包优化结构

### UI组件库 (@bone/ui-components)

```
@bone/ui-components/
├── src/
│   ├── components/            # 组件
│   │   ├── Button/            # Button组件
│   │   │   ├── Button.tsx
│   │   │   ├── Button.types.ts
│   │   │   ├── Button.stories.tsx
│   │   │   ├── Button.test.tsx
│   │   │   └── Button.scss
│   │   ├── Form/              # Form组件
│   │   ├── Table/             # Table组件
│   │   └── index.ts           # 组件导出
│   ├── hooks/                 # 组件Hooks
│   ├── theme/                 # 主题配置
│   ├── types/                 # 类型定义
│   └── utils/                 # 工具函数
├── docs/                      # 组件文档
├── stories/                   # Storybook stories
├── package.json               # 包配置
├── tsconfig.json              # TypeScript配置
└── README.md                  # 文档
```

### 微前端SDK (@bone/micro-frontend-sdk)

```
@bone/micro-frontend-sdk/
├── src/
│   ├── communication/         # 通信模块
│   │   ├── event-bus.ts       # 事件总线
│   │   ├── message.ts         # 消息传递
│   │   └── channel.ts         # 通信通道
│   ├── lifecycle/             # 生命周期管理
│   │   ├── hooks.ts           # 生命周期钩子
│   │   └── manager.ts         # 生命周期管理器
│   ├── sandbox/               # 沙箱工具
│   │   ├── proxy.ts           # 代理沙箱
│   │   └── style.ts           # 样式隔离
│   ├── loader/                # 应用加载器（新增）
│   │   ├── preload.ts         # 预加载策略
│   │   └── chunk.ts           # 代码块加载
│   └── index.ts               # 导出
├── package.json
└── tsconfig.json
```

## 🔧 构建与部署优化

### 构建配置优化

```
configs/
├── vite/
│   ├── base.config.ts         # 基础配置
│   ├── main-app.config.ts     # 主应用特定配置
│   ├── micro-app.config.ts    # 微应用通用配置
│   ├── shared.config.ts       # 共享包配置
│   └── plugins/               # 自定义插件
│       ├── micro-app-plugin.ts # 微应用插件
│       └── asset-plugin.ts    # 资源处理插件
└── webpack/                   # Webpack配置（可选）
    └── legacy.config.ts       # 旧版打包配置
```

### 部署配置优化

```
scripts/deploy/
├── templates/                 # 部署模板
│   ├── nginx.template.conf    # Nginx配置模板
│   └── docker.template.yml    # Docker配置模板
├── main-app.sh                # 主应用部署脚本
├── micro-app.sh               # 微应用通用部署脚本
├── shared.sh                  # 共享包发布脚本
└── utils.sh                   # 部署工具函数
```

## 🌐 国际化与主题系统

### 国际化结构

```
@bone/i18n/
├── src/
│   ├── core/                  # 核心功能
│   │   ├── i18n.ts            # 国际化核心
│   │   ├── loader.ts          # 资源加载器
│   │   └── formatter.ts       # 格式化工具
│   ├── hooks/                 # React Hooks
│   │   ├── useTranslation.ts  # 翻译Hook
│   │   └── useLocale.ts       # 语言切换Hook
│   └── index.ts               # 导出
├── package.json
└── tsconfig.json
```

### 主题系统结构

```
@bone/theme/
├── src/
│   ├── tokens/                # 设计令牌
│   │   ├── colors.ts          # 颜色系统
│   │   ├── typography.ts      # 排版系统
│   │   ├── spacing.ts         # 间距系统
│   │   └── breakpoints.ts     # 断点系统
│   ├── themes/                # 主题定义
│   │   ├── light.ts           # 浅色主题
│   │   ├── dark.ts            # 深色主题
│   │   └── index.ts           # 主题导出
│   ├── hooks/                 # React Hooks
│   │   ├── useTheme.ts        # 主题Hook
│   │   └── useThemeMode.ts    # 主题模式切换Hook
│   └── index.ts               # 导出
├── package.json
└── tsconfig.json
```

## 🚀 优化亮点

### 1. 结构优化
- **命名空间化**：共享包使用 @bone/ 命名空间，更符合现代前端工程化实践
- **职责分离**：清晰区分布局组件、业务组件和通用组件
- **引导层设计**：新增 bootstrap 目录统一管理应用启动流程
- **集中化配置**：全局配置目录统一管理构建、部署和环境配置

### 2. 协作体验优化
- **微应用模板**：新增 micro-app-template 提供标准化微应用创建模板
- **配置文件规范**：统一配置文件命名格式（.config.ts）
- **Git hooks**：集成 .husky 目录管理代码提交规范
- **IDE配置**：提供 .vscode 目录优化开发环境配置

### 3. 性能与构建优化
- **模块化构建**：按应用类型分离构建配置
- **预加载策略**：微前端SDK中集成预加载机制
- **资源管理**：优化静态资源组织结构

### 4. 可维护性提升
- **国际化支持**：完善的国际化目录结构
- **主题系统**：独立的主题包，支持多主题切换
- **测试目录**：结构化的测试目录组织
- **文档体系**：完善的文档结构

## 📝 实施建议

1. **渐进式迁移**：先从主应用和核心共享包开始优化，再逐步迁移微应用
2. **模板先行**：优先创建 micro-app-template，作为新建微应用的标准模板
3. **自动化工具**：开发自动化工具辅助迁移和代码生成
4. **团队培训**：对开发团队进行新目录结构培训
5. **CI/CD更新**：同步更新CI/CD流水线，适配新的目录结构

通过以上优化，Bone前端架构将更加符合现代前端工程化最佳实践，提升开发效率和协作体验，同时保持与后端Java架构的设计理念一致性。