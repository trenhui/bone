---
name: frontend-react
description: React 18 + TypeScript + Vite + Qiankun 微前端技能包（Bone 前端）
---

> 架构真源：[`doc/architecture/bone-前端架构.md`](../../../doc/architecture/bone-前端架构.md)、
> UI 规范 [`doc/architecture/frontend/frontend-ui-spec.md`](../../../doc/architecture/frontend/frontend-ui-spec.md)。
> **不是** Umi/Ant Design Pro 工程——路由是 React Router 6，构建是 Vite，微前端是 Qiankun。

## 技术栈约束
- React 18 + TypeScript 5，**Vite 5.x** 构建（禁用 Umi / `@umijs/max`）。
- 微前端：**Qiankun 2.x** + `vite-plugin-qiankun`（子应用 UMD 导出，主应用运行时注册）。
- UI：Ant Design 5 + Pro Components；设计令牌与语义色以 **`@bone/ui`**（`packages/ui/design-system`）为准。
- 路由：React Router 6（主应用与子应用各自路由）；HTTP：Axios（经 `@bone/shared-services` 统一实例）。
- 函数组件 + Hooks；严格 TypeScript。

## 目录结构（workspace）
```
bone-frontend/
├── apps/
│   ├── bone-shell/              # 主应用（基座：布局、登录态、注册子应用、全局路由）
│   └── bone-*-app/              # 子应用：iam / metadata / masterdata / integration / system / extension / generator
└── packages/
    ├── ui/design-system/        # @bone/ui：designTokens、BoneAppProvider、createBoneMicroAppRenderer、主题
    ├── shared-components/ shared-utils/ shared-services/ shared-types/
    └── core/event-bus/          # @bone/core/event-bus：跨应用类型安全事件
```

单应用 `src/` 推荐形态（与现有应用保持一致即可）：
```
apps/<app-name>/src/
├── components/   # 应用级通用组件
├── pages/        # 页面（按业务域分子目录）
├── services/     # API 封装
├── store/        # 状态（若使用）
├── routes/       # 路由
├── types/  utils/
├── App.tsx
└── main.tsx      # 微应用需导出 qiankun 生命周期
```

## UI 与主题（强制约定）
- 业务组件用 Ant Design；色彩 / 排版语义以 `packages/ui/design-system` 为准（主色 `#1677FF`，字号 14px，字体 PingFang SC，CSS 变量 `--bone-color-*`）。
- 主题切换由 **Shell** 调用 design-system 的 `applyTheme` 并持久化 `localStorage`（键名 `bone-theme`），**禁止只设 `document.body.className`** 而不同步 Ant Design 主题。
- Shell 经 `registerMicroApps` 的 `props` 下发 `themeMode: 'light' | 'dark' | 'system'`；子应用在 `mount(props)` 内用 `ConfigProvider` 渲染根节点。
- 样式隔离：子应用自定义样式用 **CSS Modules / scoped**，避免污染全局；优先消费 Shell 写的 CSS 变量而非硬编码色值。
- 微应用入口用 `@bone/ui` 的 `createBoneMicroAppRenderer` / `BoneAppProvider`。

## 编码规范
- 严格 TS，禁止 `any`（必要时 `unknown` 再收窄）；对象优先 `interface`。
- 目录 `kebab-case`；组件 `PascalCase.tsx`；工具 / hook 多用 `camelCase`。
- API 层与 UI 层分离，类型集中放 `types.ts` / `types/`。
- 组件拆分，单文件 ≤ 500 行；导入顺序：第三方 → 项目内部 → 样式。

## API 错误处理标准
- 所有微应用经 `@bone/shared-services` 的统一 Axios 实例请求。
- 拦截器统一处理 `ApiResponse`：`success=true` 返回 data；`success=false` 按错误码提示。
- `401` → 跳 Shell 登录页；`403` → 无权限提示；`429` → 限流提示；`500` → 通用错误提示。
- 网络错误自动重试 1 次（**仅 GET**）。

## 状态管理
- 服务端状态：**TanStack Query（React Query）**，替代手写 loading/error。
- 客户端状态：轻量用 **Zustand**，复杂用 Redux Toolkit。
- 跨应用共享（用户信息、主题、租户）：由 Shell `props` 下发，子应用不独立请求。
- 现状：`bone-iam-app` 等已迁移 React Query / shared-services；`bone-shell` / `bone-metadata-app` / `bone-extension-app` 仍用 Redux，随页面迭代迁移——**新代码按上述目标态写**。

## 微前端通信规范
- 统一用 `@bone/core/event-bus` 的 `emit<T>()` / `on<T>()`。
- 标准事件：`theme-changed`、`user-updated`、`tenant-switched`、`token-expired`。
- 子应用 mount 时校验 token 有效性，过期时 emit `token-expired` 通知 Shell。
- **禁止**子应用直接改全局 Store，只能通过事件通知 Shell。

## 常用命令（在 `bone-frontend/`）
```bash
npm run dev                       # 启动 Shell（默认）
npm run build                     # 构建全部 workspace
npm run typecheck                 # 类型检查（各应用 tsc --noEmit）
npm run lint                      # ESLint
npm test                          # Vitest（vitest run）
```
- 单应用命令用 `--workspace=<app>`，如 `npm run build --workspace=bone-iam-app`。
- 多应用批量启动参考 `bone-frontend/restart-all-apps.sh` 与 `SCRIPT_USAGE.md`；端口以各 `vite.config.ts` 为准。
