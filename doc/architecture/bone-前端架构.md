# Bone 前端架构（与仓库一致）

本文档为 `bone-frontend` 的**唯一架构说明**，替代此前多份互相重复或与实现不符的前端方案文档。

## 1. 设计目标

- **微前端**：主应用加载多个独立开发与部署的子应用。
- **统一体验**：布局、导航、认证由主应用协调；子应用专注业务界面。
- **可维护**：TypeScript、workspace 分包、清晰的目录约定。
- **可扩展**：新增子应用时遵循统一入口注册与端口约定。

## 2. 技术选型（与当前代码一致）

| 类别 | 技术 | 说明 |
|------|------|------|
| 框架 | React 18+ | 子应用与主应用均为 React |
| 语言 | TypeScript 5+ | 全量 TS |
| 构建 | Vite 4.x～5.x | 各应用独立 Vite 工程；根 `package.json` 当前为 **4.x**，升级 5+ 时须全仓对齐 |
| 微前端 | **Qiankun 2.x** + `vite-plugin-qiankun` | 子应用 UMD 导出；主应用运行时注册 |
| UI | Ant Design 5 + Pro Components | 管理类界面 |
| 路由 | React Router 6+ | 主应用与子应用各自路由 |
| 状态 | Redux Toolkit（按需） | 以各应用实际使用为准 |
| HTTP | Axios | 统一拦截器、错误处理 |
| Monorepo | npm workspaces | 根目录 `package.json` workspaces |

> **说明**：仓库实现为 Qiankun，请勿再按「无界 / wujie」方案改造主链路；旧文档中相关表述已废弃。

## 3. 微前端结构

1. **主应用（Shell）**：`apps/bone-shell` — 布局、登录态、子应用注册与生命周期、全局路由与子路由衔接。
2. **微应用**：位于 `apps/bone-*-app`，通过 Qiankun 接入，独立端口开发、独立构建产物。

典型子应用包括：IAM、元数据、主数据、集成、系统管理、扩展引擎、生成器等（以 `apps/` 下实际目录为准）。

## 4. Monorepo 目录（实际结构）

```
bone-frontend/
├── apps/
│   ├── bone-shell/              # 主应用（基座）
│   ├── bone-iam-app/
│   ├── bone-metadata-app/
│   ├── bone-masterdata-app/
│   ├── bone-integration-app/
│   ├── bone-system-app/
│   ├── bone-extension-app/
│   └── bone-generator-app/      # 以仓库为准；若有增减以 apps/ 为准
├── packages/
│   ├── shared-components/       # @bone/shared-components
│   ├── shared-utils/
│   ├── shared-services/
│   ├── shared-types/
│   ├── ui/
│   │   ├── design-system/       # BONE 设计令牌、applyTheme（语义源，见 UI 规范 §1.2）
│   │   ├── components/          # 实验性 headless 组件，非业务页默认栈
│   │   └── styled-system/
│   └── core/
│       └── event-bus/           # @bone/core/event-bus，跨应用消息
├── package.json                 # workspaces 根配置（npm）；根目录可有 pnpm-workspace.yaml 作可选
├── setup.sh / restart-all-apps.sh 等脚本
└── 各应用 README / START_GUIDE 等
```

> **微前端主链路**：`bone-shell` + **Qiankun** + `vite-plugin-qiankun`（各 `bone-*-app`）。已移除 `main` / `sub-app-*` 演示应用及 `micro-fe-runtime` 实验运行时。

### 4.1 单应用 `src` 约定（推荐）

与现有应用保持一致即可，推荐形态：

```
apps/<app-name>/src/
├── components/       # 应用级通用组件
├── pages/            # 页面（按业务域分子目录）
├── services/         # API 封装
├── store/            # 状态（若使用 Redux）
├── routes/ 或 App 内路由
├── types/
├── utils/
├── App.tsx
└── main.tsx          # 微应用需导出 qiankun 生命周期
```

- 目录与文件命名：**目录 `kebab-case`**；组件 **`PascalCase.tsx`**；工具与 hook 多用 **`camelCase`**。
- 类型：优先 `interface` 描述对象；避免滥用 `any`，必要时用 `unknown` 再收窄。

### 4.2 共享包

- 跨应用复用的 UI、类型、请求封装放在 `packages/`，通过 workspace 包名引用（如 `@bone/shared-types`）。
- 避免子应用之间直接源码依赖；共享逻辑上升为 `packages`。
- **UI 分层**（与 [`frontend/frontend-ui-spec.md`](./frontend/frontend-ui-spec.md) §1.1 一致）：业务组件用 **Ant Design**；色彩/排版语义以 `packages/ui/design-system` 为准。

### 5.1 主题与跨应用样式（强制约定）

1. **Shell 职责**（`apps/bone-shell`）  
   - 用户切换「浅色 / 深色 / 跟随系统」时调用 `design-system` 的 `applyTheme`，持久化 `localStorage`（键名 `bone-theme`）。  
   - 禁止仅设置 `document.body.className` 而不同步 Ant Design 主题（须使用 §1.2 映射或等价 `ConfigProvider`）。

2. **下发子应用**  
   - `registerMicroApps` 的 `props` 中传入 `themeMode: 'light' | 'dark' | 'system'`（及可选 `antdTheme` 对象）。  
   - 子应用在 Qiankun `mount(props)` 内用 `ConfigProvider` + [`frontend-ui-spec.md` §1.2](./frontend/frontend-ui-spec.md) 映射表渲染根节点。

3. **样式隔离**  
   - Shell 已启用 Qiankun `strictStyleIsolation` / `experimentalStyleIsolation`；子应用自定义样式须使用 CSS Modules 或 scoped 方案，避免污染全局。  
   - 跨应用一致的 `--primary` 等变量由 Shell `applyTheme` 写在 `document.documentElement`，子应用优先消费 CSS 变量而非硬编码色值。

4. **落地状态**  
   - 映射表与流程以 UI 规范为准；代码逐步对齐，新增/改版页面须符合本节，旧页可在迭代中迁移。

## 5. 开发与端口（常见约定）

主应用与子应用开发端口以各 `vite.config.ts` / 启动脚本为准；批量启动可参考 `bone-frontend/restart-all-apps.sh` 与 `SCRIPT_USAGE.md`。

新增子应用时：

1. 在 `apps/` 下复制现有子应用模板，修改 `package.json` 名称与端口。
2. 配置 `vite-plugin-qiankun`（子应用侧）与主应用注册表（入口 URL、activeRule）。
3. 在 Shell 菜单与路由中挂载路径。

## 6. 工程化与质量

- **Lint / Format**：根目录 ESLint、Prettier；提交前保持与仓库配置一致。
- **测试**：以各应用已配置的 Vitest / Testing Library 为准；关键业务逻辑优先单测。
- **环境变量**：按应用区分 `.env.development` 等，禁止将密钥写入仓库。

## 7. 相关文档

- [`BONE-总体架构设计方案.md`](./BONE-总体架构设计方案.md) — 平台总体架构、NFR、安全与数据一致性策略。
- [`frontend/frontend-ui-spec.md`](./frontend/frontend-ui-spec.md) — 设计令牌、**§1.1 实现策略**、Ant Design 映射、布局与无障碍。
- 根目录 [`AGENTS.md`](../../AGENTS.md) — 全栈模块索引与端口表。
- [`bone-frontend/START_GUIDE.md`](../../bone-frontend/START_GUIDE.md) — 本地启动步骤。
- [`bone-frontend/SCRIPT_USAGE.md`](../../bone-frontend/SCRIPT_USAGE.md) — 多应用脚本说明。

## 8. 工具链扩展（与仓库一致为准）

此前独立维护的「前端工程规范」长文与本文在 Monorepo、Qiankun、目录结构上重复，且部分条目（如全仓 pnpm、与 `apps/` 不一致的包树）与当前仓库不符，已废止；**以本文 §2～§6 为准**。下表仅作能力扩展参考：

| 能力 | 仓库现状 / 建议 |
|------|----------------|
| 包管理 | **npm workspaces** 为主；pnpm 仅为可选，勿将 pnpm-only 流程写入 CI 除非已全量迁移。 |
| 单元测试 | **Vitest** + Testing Library（以各应用 `package.json` 与配置为准）。 |
| 状态与数据 | **Redux Toolkit** 以各应用为准；Zustand、TanStack Query 可按业务点状引入，非强制全仓统一。 |
| E2E | Cypress、Playwright 等按需引入；未统一前不写死版本门禁。 |

Git 钩子、Commit 规范模板等团队外围流程以内部 playbook 为准；与目录、微前端、端口约定冲突时一律以本文为准。
