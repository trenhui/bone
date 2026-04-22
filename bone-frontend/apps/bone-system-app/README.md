# BONE 系统管理应用

基于 React + TypeScript + Ant Design + Vite 构建的企业级系统管理前端应用。

## 功能特性

- **系统配置管理**：管理系统全局配置、功能开关，支持配置导入导出和历史查看
- **监控告警**：实时监控系统指标，配置告警规则，查看告警历史
- **日志管理**：查看、搜索、分析系统日志，支持日志导出
- **系统部署**：支持系统部署、升级、重启，查看部署历史，Kubernetes 集成

## 技术栈

- **React 18**：前端框架
- **TypeScript**：类型安全
- **Ant Design 5**：UI 组件库
- **Vite**：构建工具
- **React Router 6**：路由管理
- **Axios**：HTTP 请求

## 快速开始

### 安装依赖

```bash
npm install
# 或
yarn install
# 或
pnpm install
```

### 启动开发服务

```bash
npm run dev
```

应用将在 `http://localhost:3002` 启动。

### 构建生产版本

```bash
npm run build
```

构建产物将输出到 `dist` 目录。

### 预览生产构建

```bash
npm run preview
```

### 类型检查

```bash
npm run type-check
```

### 代码检查

```bash
npm run lint
```

## 项目结构

```
bone-system-app/
├── src/
│   ├── pages/           # 页面组件
│   │   ├── SystemConfig.tsx       # 系统配置页面
│   │   ├── MonitorAlert.tsx       # 监控告警页面
│   │   ├── LogManagement.tsx      # 日志管理页面
│   │   └── SystemDeployment.tsx   # 系统部署页面
│   ├── services/        # API 服务
│   │   └── api.ts
│   ├── types/           # TypeScript 类型定义
│   │   └── index.ts
│   ├── App.tsx          # 主应用组件
│   ├── main.tsx         # 应用入口
│   ├── App.css          # 应用样式
│   └── index.css        # 全局样式
├── index.html           # HTML 模板
├── package.json         # 项目配置
├── tsconfig.json        # TypeScript 配置
├── vite.config.ts       # Vite 配置
└── README.md
```

## 配置说明

### API 地址配置

复制 `.env.example` 为 `.env`，并修改 `VITE_API_BASE_URL` 为实际的后端 API 地址：

```env
VITE_API_BASE_URL=http://your-backend-api:8080
```

### 代理配置

开发环境代理在 `vite.config.ts` 中配置，默认将 `/api` 请求代理到 `http://localhost:8080`。

## API 接口

应用使用以下主要 API 接口：

- 系统配置：`/api/system/config`
- 监控告警：`/api/system/metrics`, `/api/system/alerts`
- 日志管理：`/api/system/logs`
- 系统管理：`/api/system/info`, `/api/system/restart`, `/api/system/deploy`

## 开发规范

- 使用 TypeScript 进行类型安全开发
- 遵循 Ant Design 的组件使用规范
- 保持代码风格一致，使用 ESLint 进行代码检查
- 页面组件按功能划分，避免组件过大

## 相关文档

- [React 官方文档](https://react.dev/)
- [Ant Design 组件库](https://ant.design/)
- [Vite 官方文档](https://vitejs.dev/)
- [TypeScript 官方文档](https://www.typescriptlang.org/)

## License

MIT
