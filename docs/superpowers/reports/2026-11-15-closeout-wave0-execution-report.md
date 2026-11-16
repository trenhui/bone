# 收口执行报告 · Wave 0 与联调闭环（2026-11-15）

> 关联计划：`docs/superpowers/plans/2026-11-15-comet-all-changes-closeout-plan.md`
> 本轮性质：**执行落地**（非规划）。所有数字均为本轮现场命令输出，不沿用历史报告结论。

## 1. 执行范围与诚实边界

**已执行**：W0 基线对齐（本地侧）、开工闸门实测、前后端联调测试、联调暴露缺陷的前后端代码修复与全量验证。

**未执行**：W1~W6 的流程收口，以及计划里的 **62 项实现任务**（W2 的 15 项 + W3 四批次的 47 项）。原因见 §6，不是遗漏。

## 2. 基线取证

| 项 | 事实 |
|---|---|
| 本轮开始 | 本地 `285827275`，Gitee `46e59ea7c`，GitHub `285827275`（behind 6 / ahead 0） |
| 执行中变化 | 并行会话又推送 `c395e4b60`（归档 `procuraai-archive-delivery-closure`），Gitee tip 变更 |
| 已执行 | `git fetch origin` + `git merge --ff-only origin/dev`（落点先经 `git branch --show-current` 确认为 `dev`） |
| 结果 | `HEAD = origin/dev = c395e4b60`，`ahead/behind = 0/0`，工作区仅本轮精确暂存的文件 |
| 未执行 | 补齐 GitHub `dev`（外部写远端，需授权）；补后需再核双远端 SHA 一致 |

并行会话的 `procuraai-archive-delivery-closure` Verify 报告把全仓门禁的 6 类历史红项登记为 accepted deviation；本轮用一手实测复核，结论见 §3。

## 3. 开工闸门（你已裁定：门禁转绿才进 Build）

| 时点 | 命令 | 时长 | 末行 | RC |
|---|---|---|---|---|
| 本轮改动前 | `./scripts/check.sh` | 3m17s | `✅ 本地自检通过！` | **0** |
| 本轮改动后（含 staged 变更） | `./scripts/check.sh` | 3m19s | `✅ 本地自检通过！` | **0** |

判据按项目铁律取 `exit 0 + 末行`，中间 ❌ 是负向探针，真实红点只看步骤级。**结论：闸门满足**，此前登记的 6 类 accepted deviation 在当前基线不复现。

## 4. 联调测试（前后端，经网关 :8888 与前端同口径）

| 套件 | 命令 | 结果 |
|---|---|---|
| 环境快检 | `./scripts/e2e/run.sh preflight --all` | 3/3 端点可达 + admin 登录探活通过 |
| 配置面 API（修复前） | `./scripts/e2e/run.sh claim-config-e2e.mjs` | **78/84，6 红**（缺陷暴露） |
| 配置面 API（修复后） | 同上 | **86/86 全绿** |
| 理赔全功能全链 | `./scripts/e2e/run.sh node-api` → `claim-full-chain-e2e.mjs` | **64/64 全绿** |
| 工作台 + AI 回流 | 同上 → `synap-claim-workbench-feedback-e2e.mjs` | **41/41 全绿** |
| 前端类型与构建 | `npm run typecheck && npm run build`（bone-claim-app） | **RC=0**，3368 modules，产物 `index-CGoFvzUn.js` |
| 前端单元测试 | `npx vitest run --root . apps/bone-claim-app` | **8 文件 / 131 用例全绿**，RC=0 |

全链覆盖含：小额直付、大额双人复核 SoD、拒赔申诉维持/推翻、补件循环、决策图与轨迹回放、后督抽检、DB 第二证据源落库对账、AI 回流幂等与 ID string 契约。

## 5. 联调发现的真实缺陷与修复

**根因**：后端 `LiabilityConfigApplicationService#assertCatalogMandatoryConfirmed` 是 fail-closed 守卫，在 `create` 与 `update` 上校验**合并后的生效策略**为 `MANDATORY` 时必须带 `confirmCatalogMandatory=true`（单测 `LiabilityRuleShapeGuardTest` 已有正向钉子）。但：

- 前端 `LiabilityConfigPage` 只有「目录准入策略」下拉，无确认控件，提交载荷不带该字段；
- 批量导入模板无该列，行级校验也不检查；
- E2E 配置面用例同样不带。

**后果**：配置岗在页面选择「必须查到结论」保存必然 400，且页面不解释原因；批量导入同配置静默失败。目录未接入时整批案件会 `CATALOG_UNRESOLVED` 转人工补件，属于高爆炸半径配置，必须显式确认。

**修复（4 文件，392 行新增中含 309 行规划文档）**：

| 文件 | 改动 |
|---|---|
| `bone-frontend/apps/bone-claim-app/src/services/claimConfigApi.ts` | `CreateLiabilityReq` / `UpdateLiabilityReq` 补 `confirmCatalogMandatory?: boolean`，注释写明「合并后生效值」语义 |
| `bone-frontend/apps/bone-claim-app/src/pages/LiabilityConfigPage.tsx` | 目录卡片新增 warning `Alert` + `Checkbox` 二次确认；`Form.useWatch` 取生效策略并以 `editTarget` 原值兜底（**编辑本就是 MANDATORY 的责任也强制重新确认**，堵住「只改赔付比例」的绕过路径）；提交载荷带确认位；批量导入新增列、请求透传与行级 `MANDATORY 未确认` 校验 |
| `scripts/e2e/claim-config-e2e.mjs` | 建责任补 `confirmCatalogMandatory: true`（正向钉子）；新增 2 条负向断言：不带确认位必 4xx，且错误消息点明 `confirmCatalogMandatory` 与 `CATALOG_UNRESOLVED` 后果 |
| `docs/superpowers/plans/2026-11-15-comet-all-changes-closeout-plan.md` | 上一轮交付的总规划（已暂存） |

正/负向成对是刻意设计：只跑正向时，把后端守卫删掉也照样全绿；现在删守卫负向必红，判别力可证。

## 6. 未完成项与阻断（不粉饰）

| 未完成 | 状态 / 原因 |
|---|---|
| W2 `multi-insurer-biz-identity-catalog-mapping` 15 项 | 0/15。unmanaged OpenSpec（无 `comet-state.yaml`），纳管方式待裁定；切片 C 的 `t_claim_coverage_override` 属 L3 DDL，需架构师审批 |
| W3 四批次 47 项 | 各 0/N。四个分支 `comet-state.yaml` 均 `plan: null`（未 `submit-plan`）；批 2/3/4 `build_mode/tdd_mode=null`、未 `propose-build`；依赖链 批2/3→批1、批4→批1+批3 |
| W1 两个 Run 收口 | `synapclaim-risk-fraud-dimensions` 仍 `outcome-unknown / record=missing / 无收据`；`procuraai-ai-first-landing` 仅 SDK Run `deliver` 未标 completed（业务交付已完成，远端有阻塞登记） |
| W4 归档 / W5 backlog / W6 台账 | 未开始 |
| GitHub `dev` | 仍停在 `285827275`，与 Gitee 不一致；外部写操作待授权 |
| `comet status` 的 `procuraai-ai-first-landing phase=invalid` | 快进后残留的**未跟踪**运行时锁目录 `.comet-state.lock/`（归档目录已含全部正式产物）；展示层噪声，未擅自删除 |

**流程层面的硬约束**：`.comet/current-change.json` 不存在，当前有多个活跃 change 候选；按 Comet 所有权规则，多个候选时须由用户选定当前需求后才能进入某个 change 的 Build 写入，Agent 不得自行归因。本轮修复属于「联调测试直接发现的契约缺陷」，不挂到任何 change 的任务清单。

## 7. 下一步（待裁定后即可继续）

1. 选定本轮进入 Build 的 change（W2 Pilot / 批 1 / 其他），建立单一 selection；
2. 四批次补 `submit-plan` 与 `propose-build`（批 2/3/4 配置是否沿用批 1 的 `executing-plans / tdd / thorough`）；
3. 授权补齐 GitHub `dev`，使双远端一致；
4. L3 DDL 与 CI 脚本改动走架构师审批；
5. 之后按计划的 WIP=1 串行归档窗口收口。

## 8. 本轮交付物与证据

- 代码：`claimConfigApi.ts`、`LiabilityConfigPage.tsx`、`scripts/e2e/claim-config-e2e.mjs`（已 `git add --` 精确暂存，**未提交**）；
- 报告：本文件；
- 证据：`check.sh` 两次全绿输出、86/86 + 64/64 + 41/41 联调输出、`typecheck`/`build`/`vitest` RC=0 输出、`git status --short` 与 `git diff --cached --stat`。
