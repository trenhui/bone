---
change: synapclaim-case360-object-cohesion
design-doc: openspec/changes/synapclaim-case360-object-cohesion/design.md
base-ref: 669354a3877c07b876f2cea314ebb3d5d7447cbc
archived-with: 2026-11-13-synapclaim-case360-object-cohesion
---

# 实施计划：智能理赔案件 360 对象内聚与侧栏常驻分组

<!-- comet-task-authority: openspec/changes/synapclaim-case360-object-cohesion/tasks.md -->

## 0. 执行配置与约束

- 执行方式 `autonomous`，TDD `tdd`（严格 RED → GREEN），审查 `standard`（风险任务 + 最终集成独立审查）。
- 工作区 `current` / 分支 `dev`；产物语言 zh-CN。
- 行为需求以两份 delta spec 为唯一口径；技术取舍以唯一 Design Doc 为准；任务完成状态只认 tasks.md。
- 硬约束：不改 IAM 菜单数据 / `permission` / 0078 SIGNAL、不撤菜单条目、不改后端契约、不做迁移；侧栏分组用常驻可见标题而非折叠 SubMenu（单导航探针逐个点击 `.ant-menu-item`）。
- 脏工作树：188 个未提交改动中仅 13 个前序交付并入本 change，其余保留不处理；一律按精确路径暂存，不覆盖、不回滚他人在途改动。

## 1. TDD 策略（每个任务都保留真实 RED → GREEN 证据）

| 任务类型 | RED 形态 | GREEN 判据 |
|---|---|---|
| 纯函数 / 映射表 / URL 契约 | 先写单测断言期望值，运行确认失败 | 实现后同一测试转绿 |
| 内容组件抽取 | 先写组件测试断言「无 PageHeader / 无 ID 输入 / 接受 claimId」 | 组件落地后测试转绿，旧路由壳复用同一组件 |
| 探针 / UI 冒烟断言 | 先加断言并运行，确认在现状下判红 | 实现后转绿，并做互补变异验证 |
| 文档一致性 | 先用 grep 断言残留旧表述计数 > 0（失败） | 清理后计数为 0 且门禁 19/19 |

RED 与 GREEN 的实际命令输出按任务记录，不回退代码伪造 RED，不用未运行的结论充当通过。

## 2. 任务分解与依赖

### 阶段 1：基线复核与在途改动保护

- 依赖：无前置；先行执行，保护并行会话在途改动

- 1.1 复核用户裁定并入本 change 的 13 个前序交付文件，确认未被并行会话改写或代提交；验证：`git status --porcelain -- <精确路径>` 逐项列出，并记录契约联调 40/40、UI 冒烟 18/18、单测 119/119、文档门禁 19/19 的重跑结果 <!-- comet-task-ref:50844ede-b8f6-4be7-a395-fb41b6cfaf88 -->
- 1.2 按精确路径暂存本 change 相关文件，不覆盖、不回滚并行会话在途改动；验证：`git status --porcelain` 中 13 个文件状态符合预期，且无关文件状态未被改动 <!-- comet-task-ref:26e1f658-0e10-46bf-8551-b9f2124f6557 -->

### 阶段 2：六个单案作业页抽无壳内容组件

- 依赖：依赖 1；6 个页面彼此独立，可串行逐个 RED→GREEN

- 2.1 抽出材料与 OCR 内容组件（接受 `claimId`），旧路由页面壳复用同一组件；验证：旧 URL 带 `claimId` 直达正常渲染、新增组件单测通过、页面无双标题双 ID 输入 <!-- comet-task-ref:1aa09e43-313d-46ca-996c-ab8f10ce096f -->
- 2.2 抽出理算归因内容组件并复用；验证：旧 URL 直达 + 内容组件单测通过 <!-- comet-task-ref:7c5bf70c-f81a-4ff0-b4cf-5764f6e90a0e -->
- 2.3 抽出资金与结算内容组件并复用，负向断言无批量执行入口；验证：旧 URL 直达 + 单测 + 受保护动作负向断言通过 <!-- comet-task-ref:df8bc8b6-0908-4bf5-bcae-bff070288286 -->
- 2.4 抽出申诉复议内容组件并复用；验证：旧 URL 直达 + 内容组件单测通过 <!-- comet-task-ref:4be3202c-8dfb-42d9-9741-5a89dc94b26e -->
- 2.5 抽出挂起 / 恢复内容组件并复用，保留 FRAUD 逐案终审约束；验证：旧 URL 直达 + 单测 + 负向断言通过 <!-- comet-task-ref:5a35d285-860c-4317-9ae2-239ee28bf9a2 -->
- 2.6 抽出后督质检内容组件并复用，保留 DIFF 逐案确认；验证：旧 URL 直达 + 单测 + 负向断言通过 <!-- comet-task-ref:30cd640c-472a-4d46-b577-ff6c75b9a608 -->

### 阶段 3：案件 360 内嵌与 URL 契约

- 依赖：依赖 2 全部完成；URL 契约单测先行

- 3.1 把案件 360 的六个作业 Tab 改为直接渲染内容组件，`overview` 保留概览与 AI 结论卡，`trace` 保留既有深链桥；验证：真实浏览器依次切换六视图，页面不跳离、不出现案件 ID 输入框，且各视图数据属于同一 `claimId` <!-- comet-task-ref:bf9dab79-904d-4e80-831e-41a9f1119385 -->
- 3.2 补齐 URL 契约单测（深链分享、前进后退、`tab` 未登记回退、非法 `claimId` 安全空态、18 位雪花 ID 不截断）；验证：`workbench.test.ts` 新增用例全绿，且包含精确值断言 <!-- comet-task-ref:7e181b89-85a4-4edb-b4ec-cf6ae2b7910a -->
- 3.3 保持旧作业路由与「回案件 360」动作可用，内嵌态无重复页面标题 / ID 控件；验证：UI 冒烟断言旧链接不 404、返回后落到同一 `claimId` 对应 Tab <!-- comet-task-ref:0c37e33f-9652-4f22-922d-2d46e6e3ccce -->

### 阶段 4：侧栏四空间常驻分组

- 依赖：依赖 1；与 2/3 无文件冲突，可并行开发但串行验证

- 4.1 建立按 hash 的分组映射表（15 条可见菜单 → 作业 / 查询 / 配置 / 治理），未知 hash 显式落「未分组」；验证：映射表单测覆盖全部 15 条与未知值，计数断言使用精确值 <!-- comet-task-ref:ccf2869e-ccd8-40f7-9dd4-b896e0160e62 -->
- 4.2 在 shell 菜单渲染中输出常驻可见的分组标题，子项默认展开且仍为可直接点击的菜单项，不使用折叠 SubMenu；验证：真实浏览器可见四个分组标题与 15 条菜单项，无额外展开动作即可点击任一菜单项 <!-- comet-task-ref:a0f8f984-6048-450b-8ee3-03dde2f304d2 -->
- 4.3 核对菜单名、顺序、图标、permission、hash 与 IAM 下发数据一字未改；验证：单导航探针数据层「无幽灵菜单 / 无功能不可达 / 名称逐字一致 / 顺序等于路由声明顺序」四项全绿 <!-- comet-task-ref:5a83316b-89f1-4269-9ac0-7161f2934c66 -->

### 阶段 5：回归探针与变异验证

- 依赖：依赖 3、4；探针与冒烟断言必须与实现同批落地

- 5.1 单导航探针新增分组标题常驻、15 条菜单项可见可点、菜单顺序不变、无第三层导航断言；验证：`scripts/e2e/ui/claim-single-nav-probe.mjs` 真实浏览器运行 exit 0 <!-- comet-task-ref:7c483aea-1293-4053-adca-dca55c5c1ec9 -->
- 5.2 工作台 UI 冒烟新增「Tab 内作业无需再输 ID / 深链分享与回退 / 长 ID 无截断 / 非 AI_ASSISTED 无裁决动作」断言；验证：`scripts/e2e/synap-claim-workbench-ui-smoke.mjs` 全绿、零 pageerror、零 5xx <!-- comet-task-ref:23459aaa-3b3d-45cb-952a-013086f9e3b1 -->
- 5.3 对新增断言做互补变异验证（折叠分组、Tab 内混入 ID 输入、ID 截断三种缺陷写法），并恢复；验证：每种缺陷写法下对应断言确实转红，恢复后全绿，记录对照结果 <!-- comet-task-ref:af6f384b-4635-491e-a1e5-87304e8a443e -->

### 阶段 6：文档一致性与对标补录

- 依赖：依赖 1；可在 5 之前起草，但验收数字须等 7 的结果

- 6.1 清理设计提案中「侧栏按四空间折叠分组」等与 v2.0 改判口径矛盾的旧表述，全文统一为「My Home 首页 + 常驻分组、菜单数据不动」；验证：`grep -c` 残留为 0，且第十一、十二章口径一致 <!-- comet-task-ref:6f0a1aac-82ad-45fb-bc05-4dc290c11265 -->
- 6.2 在设计提案第二章补入 NN/g 隐藏导航实测数据、SAP Fiori Spaces/Pages/Sections 配额与 Guidewire claim file 对象内聚的依据强度列；验证：文档族 `tools/check_all.py` 19/19 全绿，manifest 与 AI-INDEX 登记一致 <!-- comet-task-ref:d5874c7e-5ddd-4ae5-addb-227b8bdaad43 -->
- 6.3 同步速览 HTML 的版本徽标、验收数字与本 change 成果；验证：HTML 与 Markdown 的数字口径逐项一致，预览可正常打开 <!-- comet-task-ref:cb897a88-4d16-48b1-bc27-c7cffd5b20f1 -->

### 阶段 7：集成验证与交付准备

- 依赖：依赖 2–6 全部完成；最后统一执行

- 7.1 逐项实际运行 `tsc --noEmit`、改动文件 eslint（零警告）、claim-app 全量单测、契约级联调 40/40、UI 冒烟、单导航探针、文档族门禁；验证：保存每条命令的真实退出码与关键输出，不使用未运行的结果充当通过 <!-- comet-task-ref:477904ac-8609-4f7d-8389-5c0e12a4e270 -->
- 7.2 按精确路径暂存本 change 的全部交付文件，并在提交前核对并行会话是否代提交 / 改写；验证：`git status --porcelain` 与 `git log --oneline -2 --stat` 核对无误，无关在途文件保持原样 <!-- comet-task-ref:aec7f521-631c-4d0b-a17d-a2c61f6487d8 -->

## 3. 高风险任务的失败处理与回退

- [6 页抽组件改变既有行为] 先跑该页既有单测作为基线；RED 之后每次只改一页，GREEN 立刻跑单测 + 单导航探针逐项点击（hash / 顶栏标题 / 页面标题）。失败即停在该页，不继续下一页。
- [常驻分组破坏探针可见性] 先写「四标题 + 15 项全部可见可点」断言并确认判红；实现后用真实浏览器验证；若 `.ant-menu-item` 结构变化，回退到仅注入标题的展示层方案，不折叠子项。
- [并行会话代提交 / 改写] 每完成一批立即按精确路径 `git add` 保护；提交前 `git log --oneline -2` 与 `git show --stat` 核对内容是否已入库，避免误判丢失而重做。
- [受保护动作被误做成批量] 资金 / FRAUD / 后督 DIFF 三个内容组件加负向单测与 UI 冒烟负向断言；发现批量入口立即回退该页实现。
- [整体回滚] 恢复案件 360 深链桥渲染并移除分组映射即可；旧路由与菜单数据自始至终未改，AI 回流 append-only 数据不随前端回滚删除。

## 4. 验收清单（全部实际运行并保存退出码）

1. `npx tsc --noEmit`（claim-app 与 shell）；2. 改动文件 `npx eslint --max-warnings 0`；3. claim-app 全量单测；4. 契约级联调 40/40；5. Playwright UI 冒烟（含新增断言）；6. 单导航探针真实浏览器；7. 文档族门禁 19/19；8. 三种缺陷写法的互补变异验证且恢复后全绿；9. `comet state artifacts` 闭包与 `openspec validate --strict`。

独立审查（standard）：在阶段 2（组件抽取）、阶段 5（探针断言）与最终集成各做一次风险任务复核，CRITICAL / IMPORTANT 问题必须解决后才能 `complete-build`。
