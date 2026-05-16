> **⚠️ 历史草案（已废止）**  
> 前端工程与微前端的**唯一权威**为 [`bone-前端架构.md`](./bone-前端架构.md)；UI 规范见 [`frontend/frontend-ui-spec.md`](./frontend/frontend-ui-spec.md)。本文仅作归档参考，勿作为实现依据。

# 📚 Bone 前端工程规范指南

## 🏗️ 设计理念与原则

### BONE 设计哲学
- **B**: Business-oriented（业务导向）- 架构设计以业务价值为核心
- **O**: Open & Extensible（开放可扩展）- 采用插件化设计，支持灵活扩展
- **N**: Normalized & Standardized（规范化标准化）- 统一规范，提高协作效率
- **E**: Efficient & Performant（高效高性能）- 注重性能优化，提升用户体验

### 核心工程化原则
- **一致性**：前后端架构保持命名和结构一致性
- **模块化**：高内聚、低耦合的模块组织
- **可维护性**：清晰的代码组织和完善的文档
- **可扩展性**：支持业务和技术的平滑演进
- **标准化**：遵循业界最佳实践和标准

## 📁 目录结构规范

### Monorepo 标准结构

```
bone-frontend/                  # 前端根目录
├── applications/               # 应用目录
├── packages/                   # 共享包目录
├── scripts/                    # 构建和部署脚本
├── docs/                       # 文档
├── configs/                    # 全局配置
├── templates/                  # 模板目录
├── playground/                 # 实验区
├── .github/                    # GitHub配置
├── .husky/                     # Git hooks配置
├── .vscode/                    # VSCode配置
└── README.md                   # 项目说明文档
```

### 命名规范

#### 1. 应用命名规范

| 类别 | 命名格式 | 示例 | 说明 |
|------|---------|------|------|
| 平台应用 | bone-platform-app | bone-platform-app | 主应用（基座应用） |
| 业务微应用 | bone-{domain}-app | bone-admin-app | 与后端模块对应 |
| 工具应用 | bone-{tool}-app | bone-codegen-app | 开发工具类应用 |
| 模板应用 | bone-{template}-app | bone-microapp-template | 应用模板 |

#### 2. 共享包命名规范

| 类别 | 命名格式 | 示例 | 说明 |
|------|---------|------|------|
| 核心库 | @bone/core | @bone/core | 核心基础功能 |
| UI组件 | @bone/components | @bone/components | UI组件库 |
| 微前端 | @bone/micro-frontend | @bone/micro-frontend | 微前端SDK |
| API客户端 | @bone/api | @bone/api | API通信库 |
| 主题系统 | @bone/theme | @bone/theme | 主题与样式系统 |
| 国际化 | @bone/i18n | @bone/i18n | 国际化工具 |
| 图标库 | @bone/icons | @bone/icons | SVG图标库 |
| 动画库 | @bone/animations | @bone/animations | 动画效果库 |
| 开发配置 | @bone/dev-config | @bone/dev-config | 开发工具配置 |

#### 3. 内部模块命名规范

- **目录名**：使用 `kebab-case`（小写、短横线分隔）
- **文件名**：
  - 组件文件：`PascalCase.tsx` 或 `PascalCase/index.tsx`
  - 功能模块：`camelCase.ts`
  - 配置文件：`kebab-case.config.ts`
  - 类型定义：`kebab-case.types.ts`
- **类型命名**：`PascalCase`
- **接口命名**：`IPascalCase`（以I开头）
- **常量命名**：`UPPER_SNAKE_CASE`

## 📝 代码组织规范

### 1. 应用内部结构

```
bone-{domain}-app/src/
├── bootstrap/             # 应用引导层
├── core/                  # 核心模块
│   ├── router/            # 路由系统
│   ├── store/             # 状态管理
│   ├── security/          # 安全模块
│   └── hooks/             # 核心Hooks
├── features/              # 业务功能模块（按业务域组织）
│   ├── {feature-name}/
│   │   ├── components/    # 功能特定组件
│   │   ├── hooks/         # 功能特定Hooks
│   │   ├── services/      # 功能特定服务
│   │   ├── store/         # 功能特定状态
│   │   └── index.ts       # 功能导出
├── layouts/               # 布局组件
├── components/            # 公共组件
├── services/              # 共享服务
├── utils/                 # 工具函数
├── constants/             # 常量定义
├── types/                 # 类型定义
├── assets/                # 静态资源
├── config/                # 应用配置
├── i18n/                  # 国际化资源
└── entry.tsx              # 应用入口
```

### 2. 业务功能模块（Feature）结构

```
features/{feature-name}/
├── components/         # 功能相关组件
│   ├── ComponentA.tsx
│   └── ComponentB.tsx
├── hooks/              # 功能相关Hooks
│   ├── useFeature.ts
│   └── useFeatureData.ts
├── services/           # 功能相关服务
│   └── feature-service.ts
├── store/              # 功能状态管理
│   ├── slice.ts        # Redux slice
│   └── selectors.ts    # 选择器
├── types/              # 功能类型定义
│   └── index.ts
├── utils/              # 功能工具函数
│   └── helpers.ts
├── constants/          # 功能常量
│   └── index.ts
├── validation/         # 验证规则（新增）
│   └── schemas.ts
└── index.ts            # 功能导出
```

### 3. 组件设计规范

```
components/{ComponentName}/
├── index.tsx           # 组件主文件
├── ComponentName.tsx   # 组件实现（可选，当需要分离关注点时）
├── ComponentName.types.ts  # 类型定义
├── ComponentName.stories.tsx  # Storybook文档
├── ComponentName.test.tsx  # 单元测试
├── ComponentName.scss  # 样式文件
└── utils.ts            # 组件工具函数
```

## 🔧 技术规范

### 1. TypeScript 规范

#### 文件结构

```
types/
├── api/                # API相关类型
│   └── response.ts     # 响应类型
├── components/         # 组件类型
│   └── common.ts       # 通用组件类型
├── domain/             # 领域模型类型
│   ├── user.ts         # 用户模型
│   └── permission.ts   # 权限模型
├── store/              # 状态类型
│   └── index.ts        # Redux状态类型
└── index.ts            # 类型导出
```

#### 类型设计原则
- 使用 `interface` 定义对象结构，`type` 定义联合类型或映射类型
- 为所有API响应创建类型定义
- 使用泛型提高类型复用性
- 避免使用 `any`，使用 `unknown` 替代需要类型断言的情况

### 2. 状态管理规范

#### Redux 结构

```
store/
├── slices/             # Redux Toolkit slices
│   ├── auth-slice.ts
│   ├── user-slice.ts
│   └── ui-slice.ts
├── selectors/          # 选择器
│   ├── auth-selectors.ts
│   └── user-selectors.ts
├── middleware/         # 中间件
│   ├── api-middleware.ts
│   └── error-middleware.ts
└── index.ts            # Store配置
```

#### 状态设计原则
- 采用 **规范模式** (Normalized State) 存储复杂数据
- 按功能域组织 slices
- 使用选择器封装状态访问逻辑
- 实现异步操作使用 `createAsyncThunk`

### 3. API 服务规范

```
services/api/
├── client/             # API客户端配置
│   └── api-client.ts   # 基础客户端
├── endpoints/          # API端点定义
│   └── endpoints.ts    # 端点URL常量
├── services/           # 业务服务
│   ├── auth-service.ts
│   ├── user-service.ts
│   └── product-service.ts
└── types/              # API类型定义
```

#### API设计原则
- 采用RESTful API设计风格
- 服务层负责错误处理和重试逻辑
- 使用Axios拦截器统一处理认证和响应转换
- 实现API请求缓存机制

## 🎨 UI/UX 规范

### 1. 组件库使用规范

#### 组件分类体系

```
@bone/components/
├── primitives/         # 基础原子组件
├── composites/         # 复合组件
├── layouts/            # 布局组件
├── data-display/       # 数据展示组件
├── feedback/           # 反馈组件
└── forms/              # 表单组件
```

#### 组件使用原则
- 优先使用共享组件库，避免重复开发
- 遵循组件设计规范，保持一致性
- 扩展组件时使用组合而非继承
- 组件API设计保持简洁直观

### 2. 主题系统规范

```
@bone/theme/
├── tokens/             # 设计令牌
│   ├── colors/         # 颜色系统
│   ├── typography/     # 排版系统
│   ├── spacing/        # 间距系统
│   └── breakpoints/    # 断点系统
├── themes/             # 主题定义
│   ├── light.ts        # 浅色主题
│   ├── dark.ts         # 深色主题
│   └── high-contrast.ts # 高对比度主题
└── utilities/          # 主题工具
```

#### 主题使用原则
- 严格使用设计令牌，禁止硬编码样式值
- 遵循设计系统的颜色、排版和间距规范
- 支持浅色/深色主题切换
- 考虑可访问性（WCAG标准）

## 🌐 国际化规范

```
i18n/
├── locales/            # 语言包
│   ├── en/             # 英文
│   │   ├── common.json
│   │   ├── auth.json
│   │   └── components.json
│   └── zh/             # 中文
│       ├── common.json
│       ├── auth.json
│       └── components.json
├── hooks/              # 国际化Hooks
│   ├── useTranslation.ts
│   └── useLocale.ts
└── utils/              # 国际化工具
```

#### 国际化原则
- 所有用户可见文本必须国际化
- 按功能模块组织翻译键
- 支持运行时语言切换
- 考虑复数形式和格式化需求

## 🔄 开发工作流程规范

### 1. 分支管理策略

| 分支类型 | 命名格式 | 用途 |
|---------|---------|------|
| 主分支 | main | 生产环境代码 |
| 开发分支 | develop | 开发集成分支 |
| 特性分支 | feature/feature-name | 新功能开发 |
| 修复分支 | fix/issue-description | Bug修复 |
| 发布分支 | release/vX.Y.Z | 版本发布准备 |
| 热修复分支 | hotfix/issue-description | 生产环境紧急修复 |

### 2. 提交规范

#### Conventional Commits 格式

```
<类型>[可选作用域]: <描述>

[可选正文]

[可选脚注]
```

**类型说明：**
- **feat**: 新功能
- **fix**: Bug修复
- **docs**: 文档更新
- **style**: 代码风格更改（不影响功能）
- **refactor**: 代码重构（既不修复bug也不添加功能）
- **perf**: 性能优化
- **test**: 测试相关更改
- **build**: 构建系统或外部依赖项更改
- **ci**: CI配置文件和脚本更改

### 3. 代码审查规范

#### 审查清单
- 代码符合项目风格和规范
- 类型定义完整准确
- 测试覆盖关键功能和边界情况
- 没有未使用的代码或依赖
- 性能考虑和内存管理
- 错误处理和异常情况

## 📦 构建与部署规范

### 1. 构建配置

```
configs/
├── vite/
│   ├── base.config.ts     # 基础配置
│   ├── platform.config.ts # 平台应用配置
│   ├── microapp.config.ts # 微应用配置
│   └── plugins/           # 自定义插件
├── webpack/               # 可选的Webpack配置
└── tsconfig/              # TypeScript配置
```

#### 构建优化原则
- 使用代码分割减小初始加载体积
- 实现按需加载和预加载策略
- 配置适当的缓存策略
- 优化静态资源（图片压缩、字体优化等）

### 2. 部署策略

```
scripts/deploy/
├── templates/            # 部署模板
│   ├── docker/           # Docker配置
│   ├── nginx/            # Nginx配置
│   └── kubernetes/       # Kubernetes配置
├── platform.sh           # 平台应用部署脚本
├── microapp.sh           # 微应用部署脚本
└── shared.sh             # 共享包发布脚本
```

#### 部署原则
- 自动化部署流程
- 支持环境变量配置不同环境
- 实现蓝绿部署或金丝雀发布
- 部署前运行自动化测试

## 🧪 测试规范

### 1. 测试类型与覆盖

```
tests/
├── unit/                 # 单元测试
├── integration/          # 集成测试
├── e2e/                  # 端到端测试
└── performance/          # 性能测试
```

#### 测试原则
- 单元测试覆盖核心逻辑（目标80%+）
- 集成测试验证模块间交互
- E2E测试覆盖关键用户流程
- 性能测试确保应用响应迅速

### 2. 测试编写规范

#### 单元测试结构

```typescript
// 导入待测试的模块
import { functionToTest } from '../path/to/module';

describe('ModuleName', () => {
  describe('functionToTest', () => {
    it('should return expected result when given valid input', () => {
      // 准备测试数据
      const input = { /* test data */ };
      
      // 执行测试
      const result = functionToTest(input);
      
      // 验证结果
      expect(result).toEqual(expectedResult);
    });
    
    it('should handle edge cases correctly', () => {
      // 测试边界情况
    });
    
    it('should throw error when given invalid input', () => {
      // 测试错误处理
    });
  });
});
```

## 🔍 质量保证规范

### 1. 静态代码分析

- **ESLint**: 代码质量和风格检查
- **Prettier**: 代码格式化
- **TypeScript**: 类型检查
- **SonarQube**: 代码质量深度分析

### 2. 性能监控

```
src/core/monitoring/
├── performance.ts        # 性能监控
├── error-tracking.ts     # 错误追踪
└── usage-analytics.ts    # 使用分析
```

### 3. 可访问性规范

- 遵循WCAG 2.1 AA级标准
- 使用ARIA属性确保无障碍访问
- 提供键盘导航支持
- 确保颜色对比度符合标准
- 为非文本内容提供替代文本

## 📋 实施指南

### 1. 渐进式迁移计划

1. **准备阶段**
   - 建立规范文档
   - 配置开发工具和插件
   - 创建模板和脚手架

2. **核心库迁移**
   - 重构共享包结构
   - 更新命名规范
   - 迁移核心功能模块

3. **应用迁移**
   - 迁移主应用
   - 逐一迁移微应用
   - 更新构建和部署脚本

4. **验证与优化**
   - 全面测试
   - 性能优化
   - 文档完善

### 2. 开发工具链配置

#### VSCode 配置

```json
{
  "editor.formatOnSave": true,
  "editor.codeActionsOnSave": {
    "source.fixAll.eslint": true
  },
  "typescript.tsdk": "node_modules/typescript/lib",
  "files.exclude": {
    "**/.git": true,
    "**/node_modules": true,
    "**/dist": true
  }
}
```

#### Git Hooks 配置

```bash
#!/bin/sh
. "$(dirname "$0")/_/husky.sh"

# 运行 ESLint 和 Prettier
yarn lint-staged

# 运行类型检查
yarn typecheck

# 运行测试
yarn test
```

## 🔄 与后端架构的协同

### 前后端模块映射

| 后端模块 | 前端应用 | 前端共享包 | 集成方式 |
|---------|---------|----------|--------|
| bone-core | - | @bone/core | 直接依赖 |
| bone-admin | bone-admin-app | - | API调用 |
| bone-iam | bone-user-app | @bone/core/security | SSO集成 |
| bone-workflow | bone-workflow-app | @bone/core/workflow | SDK集成 |
| bone-file | - | @bone/core/file-upload | 直接依赖 |
| bone-notification | - | @bone/components/notification | 事件总线 |
| bone-gateway | - | @bone/api | API网关调用 |

### API 命名约定

- **后端**: `com.bone.{domain}.api.{resource}`
- **前端**: `@bone/api/services/{domain}-service.ts`
- **端点**: `/api/v1/{domain}/{resource}`

## 📝 总结

本规范文档提供了Bone前端工程的全面指导，包括目录结构、命名规范、代码组织、技术实现、UI/UX设计、开发工作流、构建部署、测试和质量保证等方面。通过遵循这些规范，可以提高代码质量、团队协作效率和系统可维护性，确保Bone前端架构的稳健发展。

规范的成功实施需要团队成员的共同遵守和持续改进，定期回顾和优化这些规范，以适应业务和技术的发展需求。