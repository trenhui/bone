## Why

智能理赔当前把 15 条一级菜单按**研发流程环节**平铺，且 6 个单案作业页都要先手输 18 位雪花 `claimId` 才能干活。前序交付虽已建「我的工作台」默认落点与案件 360 深链桥，但案件 360 的 Tab 仍只是**跳到独立作业页**：作业人员依然要「找菜单 → 进独立页 → 补案件上下文」，一次作业至少跨 3 屏，且上下文不可驻留。用户在真实 UI 上反馈「菜单还是这么多」。

业界证据指向相反方向：NN/g 的隐藏导航实验（179 人 / 6 个真实站点）显示可见导航使用率 48–50%、隐藏仅 27%，内容可发现性下降 >20%、任务时间至少慢 39%；SAP Fiori 用 Spaces/Pages/Sections 与 My Home 首页承载角色入口，Groups 自 S/4HANA 2021 废弃；Guidewire ClaimCenter 把 tasks、documents、decision points、history 绑到同一 claim file 的 screens/tabs；Carbon 左面板不设第三层，更深内容移到页面内 Tab。**结论：不该把功能塞进或折叠进一个菜单，而应把围绕同一业务对象的动作内聚到一个对象页，并在导航层保留常驻可见的分组标题。**

## What Changes

- **案件 360 从「深链桥」升级为「内嵌作业页」**：将 6 个使用 `CaseIdBar` + `useClaimIdParam` 的单案作业页拆成「无壳内容组件」，作为案件 360 的 Tab 直接渲染；`claimId` 为唯一数据源，Tab 状态写入 `?claimId=&tab=`，可分享、可回退、可进通知。切换 Tab 不再跳离案件、不再二次输入 ID。
- **旧路由保留兼容**：`/evidence`、`/adjust`、`/fund`、`/appeal`、`/suspend`、`/inspection` 等带 `?claimId=` 的既有深链继续可用；CaseIdBar 的「回案件 360」保持有效；内嵌形态下不出现双 PageHeader / 双 ID 输入源。
- **侧栏加常驻可见的四空间分组标题**：按「作业 / 查询 / 配置 / 治理」给 15 条菜单加 Section 标题，**默认展开、不折叠、不减条目**；菜单名、顺序、permission、icon、hash 与 IAM 下发数据一律不变。未知 path 显式 fail-closed 归入未分组，不改其可点性。
- **回归探针同批改**：单导航探针（数据层菜单↔路由双向全等 + 浏览器逐项点击）与工作台 UI 冒烟新增断言，覆盖分组标题常驻可见、15 条菜单项可见可点、Tab 内作业无需再输 ID、雪花 ID 无截断；新增断言须做变异验证以证明有判别力。
- **文档一致性**：清理《智能理赔-易用性与角色工作台设计提案》中「侧栏按四空间折叠分组」等与 v2.0 改判相矛盾的旧表述，统一到「My Home 首页 + 常驻分组、侧栏菜单数据不动」口径；在第二章补入 NN/g 实测数据、Fiori 配额与 Guidewire claim file 的依据强度。
- **并入前序交付复核**：按用户裁定，把 13 个已验证但未提交的相关文件（工作台、案件 360、深链工具、AI 卡与单测、两个 e2e 探针、设计提案 md/速览 html、门禁登记等）纳入本 change 的范围与验收；只做复核与必要增量，不重复实现、不覆盖并行会话在途改动。
- **明确不做**：不改 IAM 菜单数据 / `permission` / 0078 SIGNAL、不撤任何菜单条目、不新增角色配置表、不改后端契约、不做数据迁移、不做需真人参与的卡片分类与树测试（列为后续验证项）、不新增 PPT。
- 无 **BREAKING**：所有既有 URL 与菜单入口保持可达。

## Capabilities

### New Capabilities

- `claim/case360-embedded-workflow`: 以单一 `claimId` 把 6 类单案作业内聚为案件 360 的 Tab 视图，规定 URL 契约、上下文唯一来源、旧路由兼容与受保护动作的逐案终审约束。
- `claim/workbench-navigation-groups`: 侧栏四空间常驻可见分组，规定分组映射、默认展开、未知 path 回退，以及「菜单数据零改动」的可验证边界。

### Modified Capabilities

- 无。本次新增行为规格，不修改 `openspec/specs/` 下既有 capability 的 requirement。

## Impact

- **前端**：`bone-frontend/apps/bone-claim-app/src/`：
  `pages/Case360Page.tsx`、6 个单案作业页（`EvidenceUploadPage` / `AdjustmentAttributionPage` / `FundWorkbenchPage` / `AppealPage` / `SuspendPage` / `InspectionWorkbenchPage`）、`components/CaseIdBar.tsx`、`workbench/caseDeepLinks.ts`、`workbench/workbench.test.ts`、`pages/WorkbenchPage.tsx`、`routes.tsx`。
- **Shell**：`bone-frontend/apps/bone-shell/src/App.tsx` 的 `buildMenuFromNodes` / 菜单渲染（仅展示层分组）。
- **探针与脚本**：`scripts/e2e/ui/claim-single-nav-probe.mjs`、`scripts/e2e/synap-claim-workbench-ui-smoke.mjs`、`scripts/e2e/synap-claim-workbench-feedback-e2e.mjs`。
- **文档**：`doc/ai-product/synapClaim/05-plan/智能理赔-易用性与角色工作台设计提案.md` 与同名 `-速览.html`，以及 `docs.manifest.json` / `AI-INDEX.md` 的登记（若内容结构变化）。
- **硬约束**：IAM `GET /api/v1/iam/menus/current` 下发菜单；`0078_seed_claim_preexam_menu.sql` 的 SIGNAL 钉死 `/claim#/*` 的统一读码；G32/G35/G37/G40 门禁与单导航探针的「菜单叶子 ↔ `routes.tsx` 非 hidden 路由」双向全等断言 —— 因此**撤菜单必须与 IAM 迁移、探针同批改**，本 change 不触碰。
- **验证基线**：契约级联调 40/40、Playwright UI 冒烟 18/18、claim-app 单测 119/119、`tsc --noEmit` 与 eslint 干净、文档族门禁 `tools/check_all.py` 19/19；本 change 完成后须全部保持或提升，且新增断言经变异验证。
