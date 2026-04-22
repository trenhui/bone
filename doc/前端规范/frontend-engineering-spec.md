# BONE 前端工程规范

## 1. 概述

本规范基于 BONE 产品需求文档、前端架构设计方案和优化方案，定义了 BONE 平台前端的工程结构、开发流程和技术规范，旨在确保前端开发的一致性、可维护性和可扩展性，同时符合业界最佳实践。

## 2. 技术栈

### 2.1 核心技术

| 技术 | 版本 | 用途 |
|------|------|------|
| React | 18+ | 前端框架 |
| TypeScript | 5.2+ | 类型系统 |
| Vite | 5.0+ | 构建工具 |
| Ant Design | 5.12+ | UI 组件库 |
| Redux Toolkit | 2.0+ | 状态管理 |
| React Router | 6.20+ | 路由管理 |
| Axios | 1.6+ | HTTP 客户端 |
| Qiankun | 2.10+ | 微前端框架 |
| Zustand | 4.4+ | 轻量级状态管理 |
| React Query | 5.0+ | 数据查询与缓存 |

### 2.2 开发工具

| 工具 | 版本 | 用途 |
|------|------|------|
| ESLint | 8+ | 代码质量检查 |
| Prettier | 3.1+ | 代码格式化 |
| Vitest | 2.0+ | 前端测试 |
| React Testing Library | 14.0+ | 组件测试 |
| Cypress | 13.0+ | E2E 测试 |
| pnpm | 8+ | 包管理 |
| Husky | 8.0+ | Git 钩子 |
| Commitizen | 4.3+ | 提交规范 |

## 3. 工程结构

### 3.1 整体结构

```
bone-frontend/
├── apps/              # 微前端应用
│   ├── bone-shell/    # 主应用（Shell）
│   ├── bone-iam-app/  # IAM 微应用
│   ├── bone-masterdata-app/ # 主数据微应用
│   ├── bone-integration-app/ # 集成微应用
│   ├── bone-system-app/ # 系统管理微应用
│   ├── bone-extension-app/ # 扩展引擎微应用
│   └── bone-metadata-app/ # 元数据微应用
├── packages/          # 共享包
│   ├── core/          # 核心功能
│   │   ├── event-bus/ # 事件总线
│   │   ├── micro-fe-runtime/ # 微前端运行时
│   │   └── performance-monitor/ # 性能监控
│   ├── ui/           # UI 相关
│   │   ├── components/ # 基础组件
│   │   ├── design-system/ # 设计系统
│   │   ├── styled-system/ # 样式系统
│   │   ├── theme/ # 主题系统
│   │   └── icons/ # 图标库
│   ├── data/          # 数据层
│   │   ├── api/       # API 客户端
│   │   ├── query/     # 数据查询
│   │   └── schemas/   # 数据校验
│   ├── infrastructure/ # 基础设施
│   │   ├── logging/   # 日志系统
│   │   ├── error-handling/ # 错误处理
│   │   ├── security/  # 安全机制
│   │   └── monitoring/ # 监控系统
│   ├── shared-components/ # 共享组件
│   ├── shared-services/ # 共享服务
│   ├── shared-types/ # 共享类型
│   ├── shared-utils/ # 共享工具
│   └── types/        # 类型定义
├── tools/             # 工具脚本
│   ├── cli/           # 命令行工具
│   ├── generators/    # 代码生成器
│   ├── eslint-config/ # ESLint 配置
│   └── vite-config/   # Vite 配置
├── scripts/           # 构建部署脚本
├── docs/              # 项目文档
├── package.json       # 根包配置
├── pnpm-workspace.yaml # 工作区配置
└── tsconfig.json      # TypeScript 配置
```

### 3.2 微应用结构

每个微应用遵循以下结构：

```
bone-xxx-app/
├── src/
│   ├── components/    # 组件
│   ├── pages/         # 页面
│   ├── services/      # API 服务
│   ├── store/         # 状态管理
│   ├── utils/         # 工具函数
│   ├── assets/        # 静态资源
│   ├── hooks/         # 自定义 Hooks
│   ├── types/         # 类型定义
│   ├── bootstrap.tsx  # 应用入口（微前端生命周期）
│   ├── App.tsx        # 根组件
│   ├── main.tsx       # 主入口
│   └── index.css      # 全局样式
├── public/            # 公共静态资源
├── tests/             # 测试文件
├── index.html         # HTML 模板
├── package.json       # 包配置
├── tsconfig.json      # TypeScript 配置
├── tsconfig.node.json # Node 环境 TypeScript 配置
└── vite.config.ts     # Vite 配置
```

### 3.3 共享包结构

```
packages/
├── core/
│   ├── micro-fe-runtime/
│   │   ├── src/
│   │   │   ├── sandbox/    # 沙箱系统
│   │   │   ├── security/   # 安全策略
│   │   │   ├── shared/     # 共享工具
│   │   │   ├── app-lifecycle.ts # 应用生命周期
│   │   │   ├── application-registry.ts # 应用注册
│   │   │   └── micro-application.ts # 微应用类
│   ├── event-bus/
│   │   ├── src/
│   │   │   ├── index.ts    # 事件总线
│   │   │   └── micro-app-messenger.ts # 微应用通信
├── ui/
│   ├── components/
│   │   ├── src/
│   │   │   ├── button/     # 按钮组件
│   │   │   ├── spinner/    # 加载组件
│   │   │   └── index.ts    # 导出
│   ├── design-system/
│   │   ├── src/
│   │   │   ├── tokens/     # 设计令牌
│   │   │   └── theme/      # 主题定义
├── shared-components/
│   └── src/
│       ├── components/ # 通用组件
│       ├── hooks/      # 自定义 hooks
│       └── index.ts    # 导出文件
├── shared-services/
│   └── src/
│       └── index.ts    # API 服务
├── shared-types/
│   └── src/
│       └── index.ts    # 类型定义
└── shared-utils/
    └── src/
        └── index.ts    # 工具函数
```

## 4. 开发规范

### 4.1 代码风格

- 使用 TypeScript 严格模式 (`strict: true`)
- 遵循 ESLint 规则，配置 extends: `@typescript-eslint/recommended`, `react-hooks/recommended`
- 使用 Prettier 自动格式化，配置统一
- 缩进：2 空格
- 行尾：LF
- 引号：单引号
- 分号：必要时使用
- 最大行宽：120 字符

### 4.2 命名规范

#### 4.2.1 文件命名

- 组件文件：PascalCase，如 `UserManagement.tsx`
- 工具文件：camelCase，如 `apiService.ts`
- 类型文件：PascalCase，如 `UserType.ts`
- 样式文件：kebab-case，如 `user-management.css`
- 目录命名：kebab-case，如 `user-management`

#### 4.2.2 变量命名

- 常量：UPPER_SNAKE_CASE，如 `MAX_RETRY_COUNT`
- 变量：camelCase，如 `userName`
- 函数：camelCase，如 `getUserInfo`
- 类：PascalCase，如 `UserService`
- 接口：PascalCase，如 `UserInterface`
- 类型别名：PascalCase，如 `UserType`
- 枚举：PascalCase，如 `UserRole`
- 事件：kebab-case，如 `user-login-success`

### 4.3 代码组织

- **组件**：单一职责，拆分合理，遵循原子设计原则
- **页面**：按功能模块组织，使用路由懒加载
- **服务**：集中管理 API 调用，使用 Axios 拦截器
- **类型**：统一管理 TypeScript 类型，使用接口和类型别名
- **工具**：通用功能抽离，避免重复代码
- **状态**：合理使用全局和局部状态，避免过度状态管理

### 4.4 注释规范

- 函数注释：使用 JSDoc 格式，包含参数、返回值和说明
- 复杂逻辑：添加说明性注释，解释设计思路
- 组件 props：使用 TypeScript 类型注释，添加必要的 JSDoc
- 公共 API：详细文档注释，包含使用示例
- 代码变更：关键变更添加注释，说明变更原因

## 5. 微前端规范

### 5.1 应用注册

- 主应用统一注册微应用，配置应用信息和激活规则
- 微应用遵循 Qiankun 生命周期：bootstrap、mount、unmount、update
- 支持动态加载和卸载，实现按需加载
- 应用配置包含：id、name、entry、activeRule、container 等

### 5.2 通信机制

- 使用增强的事件总线进行应用间通信，支持命名空间和事件节流
- 避免直接调用其他应用的方法，通过事件机制解耦
- 统一通信协议和数据格式，使用 TypeScript 接口定义
- 支持请求-响应模式的通信，实现跨应用数据交互
- 通信内容加密，保护敏感信息

### 5.3 沙箱隔离

- 使用增强的代理沙箱，提供更安全的运行环境
- 支持资源限制和安全策略，防止恶意代码执行
- 实现全局变量隔离，避免应用间冲突
- 提供命名空间隔离的存储方案，保护数据安全

### 5.4 路由管理

- 主应用管理顶层路由，微应用管理内部路由
- 支持路由参数传递和共享，实现跨应用导航
- 使用智能路由匹配器，优化路由匹配性能
- 支持路由预加载，提升用户体验

### 5.5 生命周期管理

- 增强的微应用生命周期管理器，支持生命周期钩子
- 实现资源清理机制，避免内存泄漏
- 提供生命周期事件监控，便于问题排查
- 支持并行执行生命周期钩子，提高效率

## 6. 状态管理

### 6.1 全局状态

- 使用 Redux Toolkit 管理全局状态，按功能模块划分 slice
- 遵循 Immutable 原则，使用 Immer 简化状态更新
- 统一处理异步操作，使用 createAsyncThunk
- 合理设计状态结构，避免过度嵌套

### 6.2 局部状态

- 使用 React Context API 管理局部状态
- 合理使用 useState 和 useReducer，避免过度使用全局状态
- 对于简单状态，优先使用 useState；对于复杂状态，使用 useReducer
- 考虑使用 Zustand 等轻量级状态管理库，简化状态管理

### 6.3 跨应用状态

- 使用跨应用状态管理器，实现微应用间状态共享
- 支持状态同步和历史记录，便于状态追踪和回滚
- 提供状态订阅机制，实现状态变更通知
- 状态更新支持批量操作，减少通信开销

### 6.4 异步状态

- 使用 React Query 处理异步数据，提供缓存和自动刷新
- 统一错误处理和加载状态，提升用户体验
- 合理设计缓存策略，减少不必要的网络请求
- 支持乐观更新，提升交互响应速度

## 7. API 调用

### 7.1 服务层

- 统一 API 服务封装，使用 Axios 创建实例
- 集中管理请求配置，包括基础 URL、超时时间等
- 统一错误处理，实现全局错误拦截
- 支持请求取消，避免重复请求

### 7.2 请求拦截

- 添加请求头（如认证信息、租户信息）
- 请求参数处理和格式化，支持统一的参数转换
- 实现超时和重试机制，提高请求可靠性
- 添加请求日志，便于问题排查

### 7.3 响应处理

- 统一响应格式，处理不同的响应状态
- 错误处理和提示，实现全局错误提示
- 数据转换和映射，将 API 数据转换为应用所需格式
- 支持响应缓存，减少重复请求

### 7.4 API 设计

- 遵循 RESTful API 设计规范
- 合理使用 HTTP 方法和状态码
- 支持分页、排序、过滤等常见功能
- 提供详细的 API 文档

## 8. 构建与部署

### 8.1 构建流程

- 使用 Vite 构建，配置优化的构建选项
- 支持开发、测试、生产环境，使用环境变量区分
- 实现代码分割和懒加载，减少初始加载体积
- 配置 chunk 分割策略，优化缓存利用

### 8.2 部署策略

- 微应用独立部署，支持独立升级
- 主应用统一集成，管理微应用版本
- 支持 CI/CD 流程，实现自动化部署
- 实现灰度发布机制，降低发布风险

### 8.3 性能优化

- 代码压缩和混淆，减少代码体积
- 资源优化和缓存，使用 HTTP 缓存策略
- 按需加载和预加载，提升加载性能
- 静态资源 CDN 加速，减少加载时间

### 8.4 构建配置

```typescript
// 优化的 Vite 配置示例
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd());
  
  return {
    base: mode === 'production' ? `/${APP_NAME}/` : '/',
    plugins: [
      react({ jsxRuntime: 'automatic' }),
      AutoImport({ imports: ['react', 'react-router-dom'] }),
      chunkSplitPlugin({
        strategy: 'default',
        customSplitting: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'state-vendor': ['redux', '@reduxjs/toolkit'],
          'ui-lib': ['antd']
        }
      })
    ],
    build: {
      outDir: 'dist',
      assetsDir: 'assets',
      sourcemap: mode !== 'production',
      minify: 'terser',
      rollupOptions: {
        output: {
          assetFileNames: 'assets/[name].[hash:8].[ext]',
          chunkFileNames: 'chunks/[name].[hash:8].js',
          entryFileNames: 'entry/[name].[hash:8].js'
        }
      }
    }
  };
});
```

## 9. 测试策略

### 9.1 测试类型

- **单元测试**：测试组件和工具函数，确保功能正确性
- **集成测试**：测试页面和功能模块，确保模块间协作正常
- **E2E 测试**：测试关键业务流程，确保端到端功能正常
- **性能测试**：测试应用性能，确保性能符合要求

### 9.2 测试工具

- **Vitest**：前端单元测试，与 Vite 集成良好
- **React Testing Library**：组件测试，模拟用户行为
- **Cypress**：E2E 测试，测试完整业务流程
- **Lighthouse**：性能测试，评估应用性能

### 9.3 测试覆盖率

- 核心组件：≥ 80%
- 工具函数：≥ 90%
- 业务逻辑：≥ 70%
- 关键路径：100%

### 9.4 测试最佳实践

- 测试文件与被测试文件放在同一目录，命名为 `*.test.tsx` 或 `*.spec.tsx`
- 使用 Jest 或 Vitest 的测试套件组织测试用例
- 测试用例应该独立，避免依赖外部状态
- 测试应该模拟真实场景，覆盖正常和异常情况

## 10. 代码质量

### 10.1 静态检查

- **ESLint**：代码质量检查，配置严格的规则
- **TypeScript**：类型检查，使用严格模式
- **Prettier**：代码格式化，确保代码风格一致
- **Stylelint**：CSS 代码质量检查

### 10.2 代码审查

- 建立代码审查流程，确保代码质量
- 审查重点：代码质量、性能、安全性、可维护性
- 使用自动化审查工具，如 SonarQube
- 制定审查标准，明确审查要点

### 10.3 质量门禁

- 代码覆盖率达到阈值
- 静态检查无错误
- 构建成功
- 测试通过
- 性能指标符合要求

## 11. 安全性

### 11.1 前端安全

- **XSS 防护**：使用 React 的自动转义，避免直接插入 HTML
- **CSRF 防护**：使用 CSRF Token，验证请求来源
- **敏感信息保护**：避免在前端存储敏感信息
- **内容安全策略**：配置 CSP，限制资源加载

### 11.2 认证授权

- **JWT 认证**：使用 JSON Web Token 进行身份验证
- **权限控制**：实现基于角色的权限控制
- **路由守卫**：保护需要认证的路由
- **会话管理**：合理管理用户会话，实现自动登录和登出

### 11.3 数据安全

- **数据加密传输**：使用 HTTPS 加密传输数据
- **敏感数据脱敏**：前端展示敏感数据时进行脱敏处理
- **输入验证**：对用户输入进行验证，防止注入攻击
- **数据验证**：使用 Zod 等工具进行数据结构验证

## 12. 性能优化

### 12.1 加载优化

- **代码分割**：使用动态 import() 实现代码分割
- **懒加载**：对非关键资源进行懒加载
- **预加载**：使用智能预加载系统，预测用户行为
- **缓存策略**：合理使用浏览器缓存，减少重复请求
- **资源提示**：使用 preload、prefetch 等资源提示

### 12.2 渲染优化

- **虚拟滚动**：对长列表使用虚拟滚动
- **防抖节流**：对频繁触发的事件使用防抖节流
- **避免不必要的重渲染**：使用 React.memo、useMemo、useCallback
- **优化组件渲染**：合理设计组件结构，减少渲染层级
- **骨架屏**：使用骨架屏提升用户体验

### 12.3 网络优化

- **API 缓存**：使用 React Query 缓存 API 响应
- **批量请求**：合并多个请求，减少网络开销
- **压缩传输**：使用 gzip 压缩传输数据
- **CDN 加速**：使用 CDN 分发静态资源
- **HTTP/2**：使用 HTTP/2 提升传输效率

### 12.4 资源监控

- **性能监控**：使用 Performance API 监控应用性能
- **内存监控**：监控内存使用情况，避免内存泄漏
- **网络监控**：监控网络状态和请求性能
- **错误监控**：监控应用错误，及时发现问题

## 13. 开发流程

### 13.1 开发环境

- **本地开发环境搭建**：使用 pnpm install 安装依赖
- **依赖管理**：使用 pnpm workspace 管理多包依赖
- **开发服务器配置**：配置 Vite 开发服务器，支持热更新
- **环境变量**：使用 .env 文件管理环境变量

#### 环境变量规范

- 文件命名：
  - `.env`：通用环境变量
  - `.env.development`：开发环境
  - `.env.production`：生产环境
  - `.env.test`：测试环境
  - `.env.local`：本地个人自定义变量（**必须**加入 `.gitignore`）
  - `.env.example`：环境变量模板（**必须**提交到 Git，不含敏感值）
- 命名规则：Vite 要求必须以 `VITE_` 前缀开头才能被客户端代码读取
- 类型安全：在 `src/vite-env.d.ts` 为 `import.meta.env` 添加 TypeScript 类型定义
- 敏感信息：**禁止**将 API Key、密码等敏感信息提交到 Git

### 13.2 代码提交

- **Git 工作流**：使用 Git Flow 或 GitHub Flow
- **提交规范**：使用 Commitizen 规范提交信息，遵循 [Conventional Commits](https://www.conventionalcommits.org/)

#### 提交信息格式

```
<type>(<scope>): <description>

<body>

<footer>
```

**type** 可选值：
- `feat`: 新功能
- `fix`: 修复 bug
- `docs`: 文档更新
- `style`: 代码格式调整（不影响功能）
- `refactor`: 重构（不新增功能不修复 bug）
- `perf`: 性能优化
- `test`: 测试相关
- `chore`: 构建/工具相关

- **分支管理**：主分支、开发分支、特性分支、修复分支
- **Git 钩子**：使用 Husky 配置 Git 钩子，在提交前进行检查

### 13.3 发布流程

- **测试环境部署**：部署到测试环境进行验证
- **预发布环境验证**：在预发布环境进行最终验证
- **生产环境发布**：使用 CI/CD 流程自动化发布
- **版本管理**：使用语义化版本管理

### 13.4 依赖管理

- **lock 文件**：`pnpm-lock.yaml` **必须**提交到 Git
- **依赖分组**：`dependencies`（运行时需要）vs `devDependencies`（开发时需要）清晰分离
- **定期更新**：定期更新依赖，使用 `pnpm audit` 检查安全漏洞
- **避免幽灵依赖**：pnpm 默认已经禁止幽灵依赖，保持依赖清晰

## 14. 文档规范

### 14.1 项目文档

- **README.md**：项目说明，包含快速开始指南
- **CHANGELOG.md**：版本变更记录，记录每次发布的变更内容
- **CONTRIBUTING.md**：贡献指南，说明如何参与项目
- **ARCHITECTURE.md**：架构文档，说明系统架构设计

### 14.2 API 文档

- **接口文档**：使用 OpenAPI 规范文档 API
- **组件文档**：使用 Storybook 文档组件
- **工具函数文档**：使用 JSDoc 文档工具函数
- **API 测试**：使用 Postman 或 Insomnia 测试 API

### 14.3 架构文档

- **技术架构**：说明系统技术栈和架构设计
- **数据流图**：说明系统数据流
- **组件关系图**：说明组件间关系
- **微前端架构**：说明微前端架构设计

## 15. 团队协作

### 15.1 开发规范

- **代码风格统一**：使用 ESLint 和 Prettier 确保代码风格一致
- **命名规范一致**：遵循统一的命名规范
- **文档完整**：确保代码和功能有完整的文档
- **代码复用**：鼓励代码复用，避免重复代码

### 15.2 代码审查

- **审查流程**：建立明确的代码审查流程
- **审查标准**：制定代码审查标准，明确审查要点
- **反馈机制**：建立有效的反馈机制，及时解决问题
- **审查工具**：使用 GitHub PR 或 GitLab MR 进行代码审查

### 15.3 知识共享

- **技术分享**：定期进行技术分享，交流经验
- **文档更新**：及时更新文档，确保文档与代码同步
- **经验总结**：总结开发经验，形成最佳实践
- **培训计划**：制定培训计划，提升团队技能

## 16. 最佳实践

### 16.1 组件开发

- **组件设计原则**：单一职责、可复用、可测试
- **组件复用策略**：抽象通用组件，建立组件库
- **组件测试**：为组件编写单元测试
- **组件文档**：为组件提供详细的文档和示例

### 16.2 状态管理

- **状态设计原则**：最小化状态、状态集中管理、状态不可变
- **状态分层**：全局状态、局部状态、组件状态
- **状态持久化**：合理使用 localStorage 或 sessionStorage 持久化状态
- **状态调试**：使用 Redux DevTools 等工具调试状态

### 16.3 性能优化

- **性能瓶颈识别**：使用 Chrome DevTools 识别性能瓶颈
- **优化策略**：针对不同的性能问题采取相应的优化策略
- **性能监控**：建立性能监控系统，持续监控应用性能
- **性能预算**：制定性能预算，确保应用性能符合要求

### 16.4 错误处理

- **全局错误处理**：实现全局错误处理机制
- **错误边界**：使用 React 错误边界捕获组件错误
- **错误日志**：记录错误日志，便于问题排查
- **错误提示**：为用户提供友好的错误提示

### 16.5 微前端最佳实践

- **应用隔离**：确保微应用间完全隔离，避免相互影响
- **通信规范**：建立统一的通信规范，确保通信可靠性
- **版本管理**：合理管理微应用版本，避免版本冲突
- **性能优化**：针对微前端特点进行性能优化

## 17. 常见问题

### 17.1 微前端问题

- **应用加载失败**：检查应用入口、网络连接、权限配置
- **样式冲突**：使用 CSS Modules 或 styled-components，避免全局样式
- **通信异常**：检查事件总线配置、事件名称、数据格式
- **路由冲突**：确保路由配置正确，避免路由重叠

### 17.2 性能问题

- **页面加载缓慢**：优化资源加载、使用代码分割、预加载
- **渲染性能差**：优化组件渲染、使用虚拟滚动、避免不必要的重渲染
- **内存泄漏**：检查事件监听器、定时器、闭包等可能导致内存泄漏的代码
- **网络请求慢**：优化 API 设计、使用缓存、批量请求

### 17.3 构建问题

- **构建失败**：检查依赖、配置文件、代码语法
- **依赖冲突**：使用 pnpm 管理依赖，避免依赖冲突
- **部署错误**：检查部署配置、环境变量、权限设置
- **资源路径错误**：确保资源路径配置正确，特别是在微前端环境中

## 18. 附录

### 18.1 配置示例

#### 18.1.1 Vite 配置

```typescript
// vite.config.ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

export default defineConfig({
  plugins: [
    react({
      jsxRuntime: 'automatic'
    })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@components': resolve(__dirname, 'src/components'),
      '@pages': resolve(__dirname, 'src/pages'),
      '@services': resolve(__dirname, 'src/services'),
      '@store': resolve(__dirname, 'src/store'),
      '@utils': resolve(__dirname, 'src/utils')
    }
  },
  server: {
    port: 3000,
    open: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    minify: 'terser',
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom', 'react-router-dom'],
          antd: ['antd']
        }
      }
    }
  }
});
```

#### 18.1.2 ESLint 配置

```json
// .eslintrc.json
{
  "extends": [
    "eslint:recommended",
    "plugin:@typescript-eslint/recommended",
    "plugin:react/recommended",
    "plugin:react-hooks/recommended",
    "prettier"
  ],
  "parser": "@typescript-eslint/parser",
  "parserOptions": {
    "ecmaVersion": 2020,
    "sourceType": "module",
    "ecmaFeatures": {
      "jsx": true
    }
  },
  "rules": {
    "react/prop-types": "off",
    "@typescript-eslint/explicit-module-boundary-types": "off"
  },
  "settings": {
    "react": {
      "version": "detect"
    }
  }
}
```

#### 18.1.3 TypeScript 配置

```json
// tsconfig.json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
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
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"],
      "@components/*": ["./src/components/*"],
      "@pages/*": ["./src/pages/*"],
      "@services/*": ["./src/services/*"],
      "@store/*": ["./src/store/*"],
      "@utils/*": ["./src/utils/*"]
    }
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

### 18.2 工具脚本

#### 18.2.1 开发脚本

```bash
# 启动主应用
pnpm dev

# 启动特定微应用
cd apps/bone-iam-app && pnpm dev

# 启动所有应用
bash restart-all-apps.sh
```

#### 18.2.2 构建脚本

```bash
# 构建所有应用
pnpm build

# 构建特定微应用
cd apps/bone-iam-app && pnpm build
```

#### 18.2.3 测试脚本

```bash
# 运行所有测试
pnpm test

# 运行特定测试
pnpm test apps/bone-iam-app

# 运行测试并生成覆盖率报告
pnpm test --coverage
```

### 18.3 参考资源

- **React 官方文档**：https://reactjs.org/docs/getting-started.html
- **TypeScript 官方文档**：https://www.typescriptlang.org/docs/
- **Vite 官方文档**：https://vitejs.dev/guide/
- **Ant Design 官方文档**：https://ant.design/docs/react/introduce
- **Qiankun 官方文档**：https://qiankun.umijs.org/zh/guide
- **Redux Toolkit 官方文档**：https://redux-toolkit.js.org/
- **React Query 官方文档**：https://tanstack.com/query/latest
- **ESLint 官方文档**：https://eslint.org/docs/latest/
- **Prettier 官方文档**：https://prettier.io/docs/en/

### 18.4 版本历史

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| 1.0 | 2026-04-22 | 初始版本 |
| 1.1 | 2026-04-23 | 整合架构设计和优化方案，增强微前端规范，完善性能优化策略 |
