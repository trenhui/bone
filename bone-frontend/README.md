# BONE 前端项目

基于微前端架构的企业级全栈开发平台前端系统。

## 📋 项目概述

本项目采用 Qiankun 微前端框架，将整个系统拆分为一个主应用和多个微应用，实现了独立开发、独立部署的架构。

## 🏗️ 架构说明

### 主应用
- **bone-shell**: 主应用，负责整体布局、导航和微应用管理（端口 3000）

### 微应用
- **bone-iam-app**: IAM 身份管理系统（端口 3003）
- **bone-metadata-app**: 元数据管理系统（端口 3004）
- **bone-masterdata-app**: 主数据管理系统（端口 3005）
- **bone-integration-app**: 集成管理系统（端口 3006）
- **bone-system-app**: 系统管理系统（端口 3007）
- **bone-extension-app**: 扩展引擎管理控制台（端口 3008）

## 🚀 快速开始

### 前置要求
- Node.js >= 18
- npm >= 9

### 安装依赖

由于项目采用模块化架构，请依次在各个应用目录下安装依赖：

#### 1. 安装主应用依赖
```bash
cd apps/bone-shell
npm install
```

#### 2. 安装各个微应用依赖
```bash
# IAM 应用
cd ../bone-iam-app
npm install

# 元数据应用
cd ../bone-metadata-app
npm install

# 主数据应用
cd ../bone-masterdata-app
npm install

# 集成应用
cd ../bone-integration-app
npm install

# 系统应用
cd ../bone-system-app
npm install

# 扩展应用
cd ../bone-extension-app
npm install
```

### 启动应用

打开 7 个独立的终端窗口，分别启动各个应用：

#### 终端 1 - 启动主应用
```bash
cd apps/bone-shell
npm run dev
```

#### 终端 2 - 启动 IAM 应用
```bash
cd apps/bone-iam-app
npm run dev
```

#### 终端 3 - 启动元数据应用
```bash
cd apps/bone-metadata-app
npm run dev
```

#### 终端 4 - 启动主数据应用
```bash
cd apps/bone-masterdata-app
npm run dev
```

#### 终端 5 - 启动集成应用
```bash
cd apps/bone-integration-app
npm run dev
```

#### 终端 6 - 启动系统应用
```bash
cd apps/bone-system-app
npm run dev
```

#### 终端 7 - 启动扩展应用
```bash
cd apps/bone-extension-app
npm run dev
```

### 访问应用

所有应用启动后，在浏览器中访问：

**http://localhost:3000**

## 📁 项目结构

```
bone-frontend/
├── apps/                          # 应用目录
│   ├── bone-shell/               # 主应用
│   │   ├── src/
│   │   ├── index.html
│   │   ├── package.json
│   │   └── vite.config.ts
│   ├── bone-iam-app/             # IAM 管理应用
│   ├── bone-metadata-app/        # 元数据管理应用
│   ├── bone-masterdata-app/      # 主数据管理应用
│   ├── bone-integration-app/     # 集成管理应用
│   ├── bone-system-app/          # 系统管理应用
│   └── bone-extension-app/       # 扩展管理应用
├── packages/                      # 共享包目录
│   ├── shared-components/        # 共享组件
│   ├── shared-utils/             # 共享工具
│   ├── shared-services/          # 共享服务
│   └── shared-types/             # 共享类型
├── setup.sh                      # 设置脚本
├── start-all.sh                  # 启动指南脚本
├── START_GUIDE.md               # 详细启动指南
└── README.md
```

## 🛠️ 技术栈

- **框架**: React 18+
- **语言**: TypeScript
- **UI 组件库**: Ant Design 5.12+
- **路由**: React Router 6.20+
- **构建工具**: Vite 5.0+
- **微前端框架**: Qiankun 2.10+
- **状态管理**: Redux Toolkit 2.0+
- **HTTP 客户端**: Axios 1.6+

## 🔧 开发说明

### 代码规范
- 使用 TypeScript 进行开发
- 遵循 ESLint 规范
- 使用 Prettier 格式化代码

### 添加新的微应用

1. 在 `apps/` 目录下创建新应用
2. 参考现有应用配置 `package.json` 和 `vite.config.ts`
3. 在主应用 `App.tsx` 中注册新应用
4. 配置正确的端口和路由

## 📞 帮助

详细的启动指南请查看 [START_GUIDE.md](./START_GUIDE.md)。

如果遇到问题，请检查：
1. 所有应用是否都已正常启动
2. 端口配置是否正确
3. 网络连接是否正常

## 📄 相关文档

- [前端整体设计方案](./doc/architecture/bone-frontend-overall-design.md)
- [技术架构文档](./doc/arch/technical-architecture.md)
