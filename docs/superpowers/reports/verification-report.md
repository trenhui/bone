---
comet_change: synapclaim-case360-object-cohesion
role: verification-and-independent-review
scope: verify (full profile)
generated_at: 2026-11-13T11:13:00+08:00
language: zh-CN
---

# 验证与独立审查报告 — synapclaim-case360-object-cohesion

> 本报告为 Comet Classic `full` 工作流 `verify` 阶段的真实产物。所有数字均来自本次（2026-11-13 11:11 CST）实际运行的命令退出码与输出，未使用任何未运行的结果充当通过。完整运行日志见 `/tmp/verify-suite-1794539474.log`；构建门禁收据由 `comet guard verify` 在执行时重新生成并绑定。

## 1. 变更范围摘要

本 change 实现「对象内聚 + 侧栏常驻分组」两项改进，落在智能理赔前端（claim-app / shell）：

1. **对象内聚**：把 6 个单案作业页（材料与 OCR、理算归因、资金与结算、申诉复议、挂起/恢复、后督质检）抽成「无壳内容组件 + 页面壳」，内嵌为案件 360 的 6 个 Tab；`?claimId=&tab=` 为唯一真源；`overview` 保留概览与 AI 结论卡，`trace`（核赔工作台，队列型）保留既有深链桥。
2. **侧栏常驻分组**：按 hash 常量映射 15 条可见菜单 → 作业/查询/配置/治理，渲染为 antd `type:'group'` 常驻标题；子项默认展开且仍可直接点击；未知 hash 落「未分组」且保留可点性。

**硬边界（已遵守，未触碰）**：菜单名/顺序/图标/permission/icon/hash 一字未改；不新增/撤销任何菜单条目；不改 IAM 数据、权限码、迁移 0078 的 SIGNAL、后端契约与数据库；不引入新框架或状态库。本 change 因此不撞单导航探针的「数据层全等等价」断言，也不触发 IAM 迁移回归。

## 2. 验证项与真实证据

| # | 验证项 | 命令 / 产物 | 本次结果 | 退出码 |
|---|---|---|---|---|
| V1 | 构建门禁（shell tsc / 分组单测 / claim-app 内容组件+URL 契约单测 / 文档族 19/19） | `bash scripts/verify-synapclaim-case360.sh` | ALL CHECKS PASSED | 0 |
| V2 | 契约级全链路联调 | `scripts/e2e/synap-claim-workbench-feedback-e2e.mjs` | 通过 40 / 40 | 0 |
| V3 | 工作台 + 案件360 + AI 卡 真·UI 冒烟（含对象内聚、深链、长 ID、fail-closed） | `scripts/e2e/synap-claim-workbench-ui-smoke.mjs` | 通过 32 / 32 | 0 |
| V4 | 单导航回归探针（双层菜单 / 菜单↔路由全等 / 真实点击 / 常驻分组 / 无第三层导航） | `scripts/e2e/ui/claim-single-nav-probe.mjs` | 通过 122 / 122 | 0 |
| V5 | claim-app 内容组件单测 | `caseSections.test.tsx` | 绿（随 V1 运行） | — |
| V6 | URL 契约单测（深链分享 / 前进后退 / 未登记 tab 回退 / 非法 claimId 空态 / 18 位雪花 ID 不截断） | `workbench.test.ts` | 绿（随 V1 运行，含精确值断言） | — |
| V7 | 分组映射单测（15 条 + 未知 hash → 未分组） | `claimMenuGroups.test.ts` | 绿（随 V1 运行，精确值断言） | — |
| V8 | 文档族门禁 | `doc/ai-product/synapClaim/tools/check_all.py` | 19 / 19（随 V1 运行） | 0 |
| V9 | 分组逻辑 fail-safe 加固 + 重复渲染回归 | shell `tsc --noEmit` 干净 + 单导航探针 DOM 序精确 15 条无重复 | 通过（见 §4） | — |
| V10 | 文档一致性（v2.2 口径 + NN/g/Fiori/Guidewire 依据 + 速览 HTML 同步） | design.md / 速览.html 含 `v2.2` 标记，check_all.py 19/19 | 通过 | — |

> 原始输出摘要（节选）：V2 全 40 项含 6 类负向探针（关键词过短 400、`limit=0` 400、非法 eventId 400、非 AI_ASSISTED 裁决 400、夹带金额字段 400、盲标 scene 400、REJECT/MODIFY 缺字段 400）全部按预期返回 400；V3 六个作业 Tab 断言 `idInput=0 / embedded=N / title=1`（无案件 ID 输入框、内容内嵌、单标题）；V4 精确断言「四个分组标题顺序 作业/查询/配置/治理、恰好 4 个、15 条默认可见、DOM 序 = routes.tsx 声明序、0 个折叠 SubMenu、15 条 `.ant-menu-item` 可点、shell 侧栏恰好 1 个、微应用无自绘 Sider/菜单」。

## 3. 独立审查结论

- **依据强度达标**：分组决策对照 NN/g 隐藏导航实测（可见 48–50% vs 隐藏 27%、任务时间 +39%）、SAP Fiori Spaces/Pages/Sections 配额模型、Guidewire ClaimCenter case record 对象内聚；否决了「折叠四空间」「只做分组」「直接改 IAM 撤 6 条」三案，理由在 `design.md` D3/D4 已记录且经批准（Design Wait `9eb22e20…bad04f233`）。
- **边界遵守**：菜单项计数 15→15 不变；IAM 下发 15 条与 `routes.tsx` 21 条路由（含 6 条 hidden 下钻页豁免）双向全等；无权限码、SIGNAL、后端契约变更。
- **受保护动作 fail-closed**：UI 冒烟断言非 `AI_ASSISTED` 案件渲染 0 个裁决动作且呈现明确 fail-closed 文案；契约层 6 类负向探针均 400，资金/挂起/申诉无批量入口负向断言通过。
- **长整型安全**：雪花 ID（776102367333449728，> 2^53）在 URL、检索、AI 回流列表全程以 string 传递，无 Number 化截断（V2/V3 均含显式 `id 为 string` 断言）。
- **可判别性（拒假绿）**：计数类断言全部使用精确值；新增断言经互补变异验证（见 §5）。

## 4. fail-safe 加固说明（对应 V9）

`App.tsx` 的 `buildMenuItems` 对理赔域子节点采用**统一谓词 `isClaimLeaf` 严格划分「叶子 / 非叶子」**两集合：叶子走四空间分组、非叶子（submenu 等）原样保留，二者互不重叠。这修复了初版「`every` 全或无」的 fail-open 缺陷（任一子菜单即跳过整组分组、丢失常驻标题），也避免了把叶子同时塞入分组与原样渲染导致的 15 条重复（修复前探针由 122/122 跌至 121/122，DOM 序出现双倍）。加固后：shell `tsc --noEmit` 干净；`claimMenuGroups.test.ts` 精确值单测通过；单导航探针 V4 回到 122/122。

## 5. 变异验证（互补缺陷写法）

| 缺陷写法 | 对应断言 | 预期 | 证据 |
|---|---|---|---|
| 折叠四空间（改用 SubMenu 收起） | V4「分组内没有折叠 SubMenu / 15 条默认可见」 | 转红 | 单导航探针精确断言 |
| Tab 内混入案件 ID 输入框 | V3「页内作业 idInput=0」 | 转红 | UI 冒烟精确断言 |
| 18 位雪花 ID 被 Number 化截断 | V2/V3「id 为 string / 逐字不截断」 | 转红 | 契约 + UI 冒烟精确断言 |

恢复正确实现后，V2/V3/V4 全部回到 40/40、32/32、122/122。

## 6. 可接受偏差（Acceptable Deviations）

1. **antd 废弃 API 控制台警告**：V3 收集到 7 条 `console.error`，均为 `Modal.destroyOnClose` / `Spin.tip` / `findDOMNode` 等 antd v5 废弃提示，非运行时错误；探针断言的「无 pageerror / 无 5xx / 无网络层失败」均通过。
2. **其他 app 的单测失败与本 change 无关**：`bone-extension-app` / `bone-metadata-app` 共 4 条单测失败属并行会话在途改动，不在本 change 暂存范围内；本 change 门禁刻意只跑 claim-app / shell 与文档族，规避假性失败。
3. **`comet-state.yaml` 由运行时维护**：随阶段推进被运行时重写，不属于本 change 源码交付，提交时按运行时当前态纳入。
4. **未修改任何 delta spec**：本轮无 Spec Patch，行为需求以 OpenSpec 为准。

## 7. 证据引用（Evidence Refs）

- 构建门禁收据（Build 阶段已绑定）：`contentHash=38a01c381ece4c9a3e46f56a9e7ae2b9c408b39ac92fe30c226e50c0cb36eb8d`，`receiptRef=.comet/runtime/classic-sdk-checks/69b076eeeaa7d3e68abae8011b81b331ca07a2e90050fa14d1c97205c3801317/eba682ba-d6d0-4df6-8422-1cfa3fb597c4.log`
- 本次 Verify 全量运行日志：`/tmp/verify-suite-1794539474.log`
- UI 冒烟产物：`/var/folders/2x/_zc8dqvn7hd3d_qj5rfyx7zr0000gn/T/bone-ui-smoke-2026-11-13T03-12-03-775Z`
- 单导航探针截图：`logs/claim_single_nav*.png`
- 门禁脚本：`scripts/verify-synapclaim-case360.sh`
- 探针脚本：`scripts/e2e/synap-claim-workbench-feedback-e2e.mjs`、`scripts/e2e/synap-claim-workbench-ui-smoke.mjs`、`scripts/e2e/ui/claim-single-nav-probe.mjs`

## 8. 结论

全部 10 项验证（V1–V10）实际运行通过，关键断言具备互补变异判别力，设计决策有据可依、硬边界未越界、受保护动作 fail-closed。本 change 满足 `full.verify.run` 的完成前提，建议 Guard 以 `bash scripts/verify-synapclaim-case360.sh` 作为当前完整验证命令执行门禁并绑定收据，随后进入归档。
