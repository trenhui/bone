# 📦 Bone 前端工程模块名优化方案

## 🏗️ 优化设计理念

### 核心设计原则
- **命名一致性**：与Java后端架构保持命名规范一致性，实现前后端协同开发
- **模块清晰度**：模块命名直观表达功能定位和职责
- **可扩展性**：命名规范支持业务和技术的演进
- **业界最佳实践**：遵循现代前端工程化标准和流行框架的命名约定
- **协作效率**：优化团队协作体验，降低学习成本

### 模块命名规范
- **微应用命名**：`bone-{业务领域}-app`，与后端模块名保持一致
- **共享包命名**：`@bone/{功能域}`，采用命名空间统一管理
- **内部模块命名**：采用`kebab-case`，语义化且直观
- **文件命名**：采用`camelCase`，与现代前端开发工具链最佳实践一致

## 📁 模块名优化方案

### 1. Monorepo 顶层模块优化

```
bone-frontend/                  # 前端根目录
├── applications/               # 应用目录（重命名，更直观）
├── packages/                   # 共享包目录
├── scripts/                    # 构建和部署脚本
├── docs/                       # 文档
├── configs/                    # 全局配置
├── templates/                  # 模板目录（新增）
└── playground/                 # 实验区（新增）
```

### 2. 微应用命名优化

#### 优化前
```
apps/
├── main-app/               # 主应用
├── micro-app-admin/        # 管理门户微应用
├── micro-app-analytics/    # 数据分析微应用
├── micro-app-workflow/     # 工作流引擎微应用
└── micro-app-user/         # 用户中心微应用
```

#### 优化后
```
applications/
├── bone-platform-app/      # 主应用（平台应用）
├── bone-admin-app/         # 管理门户微应用（与后端 bone-admin 对应）
├── bone-analytics-app/     # 数据分析微应用
├── bone-workflow-app/      # 工作流引擎微应用（与后端 bone-workflow 对应）
├── bone-user-app/          # 用户中心微应用（与后端 bone-iam 对应）
└── bone-microapp-template/ # 微应用模板
```

### 3. 共享包命名优化

#### 优化前
```
packages/
├── @bone/ui-components/    # UI组件库
├── @bone/micro-frontend-sdk/ # 微前端SDK
├── @bone/shared-utils/     # 共享工具函数
├── @bone/api-client/       # API客户端
├── @bone/eslint-config/    # ESLint配置
├── @bone/hooks/            # 共享Hooks
├── @bone/theme/            # 主题系统
└── @bone/i18n/             # 国际化工具
```

#### 优化后
```
packages/
├── @bone/components/       # UI组件库（简化命名）
├── @bone/micro-frontend/   # 微前端SDK（简化命名）
├── @bone/core              # 核心库（新增，整合基础功能）
│   ├── utils/              # 工具函数
│   ├── hooks/              # 通用Hooks
│   └── constants/          # 常量定义
├── @bone/api               # API客户端（简化命名）
├── @bone/dev-config        # 开发配置集合（合并配置）
│   ├── eslint              # ESLint配置
│   ├── typescript          # TypeScript配置
│   └── jest                # Jest配置
├── @bone/theme             # 主题系统
├── @bone/i18n              # 国际化工具
├── @bone/icons             # 图标库（新增）
└── @bone/animations        # 动画库（新增）
```

### 4. 主应用内部模块命名优化

#### 优化前
```
main-app/src/
├── bootstrap/             # 应用引导层
├── core/                  # 核心模块
├── layout/                # 布局组件
├── components/            # UI组件
├── pages/                 # 页面组件
├── services/              # 服务层
├── hooks/                 # 自定义Hooks
├── utils/                 # 工具函数
├── constants/             # 常量定义
├── types/                 # 类型定义
├── assets/                # 资源文件
├── config/                # 配置文件
└── i18n/                  # 国际化资源
```

#### 优化后
```
bone-platform-app/src/
├── bootstrap/             # 应用引导层
├── core/                  # 核心模块
│   ├── orchestrator/      # 微前端协调器
│   ├── router/            # 路由系统
│   ├── store/             # 状态管理
│   ├── security/          # 安全模块
│   ├── navigation/        # 导航系统（新增）
│   └── resource-manager/  # 资源管理器（新增）
├── layouts/               # 布局组件（复数形式）
│   ├── DefaultLayout/     # 默认布局
│   └── AuthLayout/        # 认证布局
├── features/              # 业务功能模块（新增）
│   ├── dashboard/         # 仪表盘功能
│   ├── user-profile/      # 用户资料功能
│   └── notifications/     # 通知功能
├── components/            # UI组件
│   ├── common/            # 通用组件
│   ├── business/          # 业务组件
│   └── platform/          # 平台特定组件（新增）
├── services/              # 服务层
│   ├── api/               # API服务
│   │   ├── auth-service.ts # 认证服务（kebab-case）
│   │   └── user-service.ts # 用户服务（kebab-case）
│   ├── micro-app/         # 微应用服务
│   └── event/             # 事件服务
├── hooks/                 # 自定义Hooks
├── utils/                 # 工具函数
├── constants/             # 常量定义
├── types/                 # 类型定义
├── assets/                # 资源文件
├── config/                # 配置文件
└── i18n/                  # 国际化资源
```

### 5. 微应用内部模块命名优化

#### 优化后
```
bone-admin-app/src/
├── bootstrap/             # 微应用引导层
├── core/                  # 微应用核心（新增）
│   ├── router/            # 路由配置
│   ├── store/             # 状态管理
│   └── initializers/      # 初始化器（新增）
├── features/              # 业务功能模块（新增）
│   ├── users/             # 用户管理功能
│   ├── roles/             # 角色管理功能
│   └── permissions/       # 权限管理功能
├── components/            # React组件
│   ├── common/            # 通用组件
│   └── admin/             # 管理特定组件
├── services/              # 服务层
│   ├── api/               # API服务
│   └── event/             # 事件服务
├── hooks/                 # 自定义Hooks
├── utils/                 # 工具函数
├── constants/             # 常量定义
├── types/                 # 类型定义
├── assets/                # 静态资源
├── config/                # 微应用配置
├── i18n/                  # 国际化资源
└── entry.tsx              # 微应用入口文件
```

### 6. 共享包内部模块命名优化

#### UI组件库优化
```
@bone/components/
├── src/
│   ├── primitives/         # 基础原子组件（新增）
│   │   ├── Button/
│   │   ├── Input/
│   │   └── Card/
│   ├── composites/         # 复合组件（新增）
│   │   ├── Form/
│   │   ├── Table/
│   │   └── Modal/
│   ├── layouts/            # 布局组件（新增）
│   │   ├── Grid/
│   │   ├── Flex/
│   │   └── Container/
│   ├── data-display/       # 数据展示组件（新增）
│   │   ├── List/
│   │   ├── Chart/
│   │   └── Timeline/
│   ├── feedback/           # 反馈组件（新增）
│   │   ├── Alert/
│   │   ├── Toast/
│   │   └── Tooltip/
│   ├── hooks/              # 组件Hooks
│   ├── theme/              # 主题配置
│   └── utils/              # 工具函数
└── stories/                # Storybook stories
```

#### 微前端SDK优化
```
@bone/micro-frontend/
├── src/
│   ├── core/               # 核心功能（新增）
│   │   ├── loader/         # 应用加载器
│   │   ├── sandbox/        # 沙箱隔离
│   │   └── lifecycle/      # 生命周期管理
│   ├── communication/      # 通信模块
│   │   ├── event-bus/      # 事件总线
│   │   ├── message-passing/ # 消息传递
│   │   └── shared-state/   # 共享状态管理
│   ├── routing/            # 路由集成（新增）
│   ├── utils/              # 工具函数
│   └── index.ts            # 导出
└── package.json
```

## 🎯 关键优化亮点

### 1. 前后端模块命名一致性
- 微应用命名采用 `bone-{业务领域}-app` 格式，与后端模块名直接对应
- 简化了微应用的前缀命名，从 `micro-app-{domain}` 改为 `bone-{domain}-app`
- 保持了领域驱动设计的一致性，提高了跨团队协作效率

### 2. 功能模块化与分层
- 新增 `features` 目录组织业务功能模块，符合现代前端架构最佳实践
- 将UI组件按功能类型分类（primitives、composites、layouts等）
- 微前端SDK内部按职责分层（core、communication、routing等）

### 3. 命名简化与规范化
- 共享包命名简化（如 `@bone/ui-components` 简化为 `@bone/components`）
- API服务文件采用 kebab-case 命名，更符合RESTful风格
- 配置文件集中管理，提高可维护性

### 4. 扩展性优化
- 新增专门的图标库、动画库等可复用资源
- 微应用模板采用统一命名格式，便于生成新微应用
- 核心库整合基础功能，减少重复代码

## 📋 实施建议

1. **渐进式迁移策略**：
   - 第一阶段：更新共享包命名和目录结构
   - 第二阶段：更新主应用内部模块命名
   - 第三阶段：更新各微应用命名和结构

2. **自动化迁移工具**：
   - 开发脚本自动生成重命名规则
   - 使用代码分析工具确保引用路径正确更新

3. **CI/CD更新**：
   - 更新构建脚本和部署流程
   - 确保依赖管理配置正确更新

4. **文档与规范更新**：
   - 更新开发规范文档
   - 提供模块命名参考指南
   - 为开发团队提供培训

## 🔄 与后端架构的协同

### 前后端模块对应关系

| 后端模块 | 前端微应用 | 前端共享包 |
|---------|-----------|----------|
| bone-core | - | @bone/core |
| bone-admin | bone-admin-app | - |
| bone-iam | bone-user-app | @bone/core/security |
| bone-workflow | bone-workflow-app | @bone/core/workflow |
| bone-file | - | @bone/core/file-upload |
| bone-notification | - | @bone/components/notification |

### 命名空间一致性
- 后端：`com.bone.{domain}`
- 前端：`@bone/{domain}`
- 应用：`bone-{domain}-app`

通过这些命名优化，Bone前端架构将更加清晰、一致，并与后端架构保持良好的协同关系，提高团队协作效率和代码可维护性。