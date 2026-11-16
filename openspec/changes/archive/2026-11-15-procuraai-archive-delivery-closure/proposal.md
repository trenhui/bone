# Proposal

## Why

`procuraai-ai-first-landing` 的业务交付（通用主数据 Golden Record 去重内核、r1/r2 验证报告、
OpenSpec 归档目录）已全部提交并推送 `origin/dev`，但该 Comet Classic Run 的
`complete-delivery` 收据无法干净闭合：SDK 以「`archive.preflight.baseCommit..HEAD` 只含本 change
已验证实现」为不变量，而并行会话在共享 `dev` 上的提交被 `fetch → rebase → push` 夹进该范围，
`allowed()` 对非本 change 提交无豁免。实证：并行提交 `285827275` 的父提交正是旧 Run 的
`baseCommit = 16c2fec85`，归档提交 `82e60e430` 又被 rebase 到 `285827275` 之上，
因此 `16c2fec85..HEAD` 必然夹带该提交的 160 个路径（`--no-renames` 口径）。
归档后 `propose-archive` / `archive-reopen` /
`repair-archive` / `repair-delivery` 均无法刷新基线（见归档目录内
`comet-sdk-blocking-bug.md`）。因此需要在干净基线上补一次可机器验证的交付闭环，
并把旧 Run 的偏差作为可审计事实保留，而不是伪造收据或改写已推送历史。

## What Changes

- 新增一个独立的 Classic full change（`procuraai-archive-delivery-closure`），以当前
  `origin/dev` HEAD 为基线，完整走 Open → Design → Build → Verify → Archive → Delivery。
- 将新增可审计的交付闭环证据：范围声明、技术设计、任务清单、实施计划、验证报告、
  `walkthrough.html`，并拟随本 change 一并归档（当前 phase 为 build，尚属计划范围，
  不是已完成事实）。
- 新增本 change 的 OpenSpec 归档目录；本 change **不创建任何 `specs/` 文件**（零 delta
  spec），因此归档时 `findSpecUpdates` 返回空、不会向 `openspec/specs/` 写入任何需求。
  `.openspec.yaml` 的 `skip_specs: true` 作用是让 `specs` 产物合法 `skipped`，
  而不是跳过主 spec 更新的直接原因。
- 以连续单父提交把本 change 的实现与归档产物提交并推送 `origin/dev`，
  使 `complete-delivery` 的整段范围只包含本 change 的已批准路径，Run 达到 `done`。

**非目标（明确不做）**

- 不新增或修改任何业务代码，不触碰 `bone-platform/bone-masterdata` 的既有实现。
- 不改 `procuraai-ai-first-landing` 的 Run 状态、归档目录或 `:72 full.archive.deliver`；
  旧 Run 保持事实状态，其偏差说明继续由 `comet-sdk-blocking-bug.md` 承载。
- 不重放旧 change 的 23 项 deferred 任务，不重建 AI 能力 / `bone-procurement-*` /
  `t_proc_*` DDL。
- 不改写 Git 历史、不 force push、不手工编辑 Runtime 权威 checkpoint、
  不伪造 PASS 收据或声明 `outcome-unknown`。
- 不创建或切换 Git 分支（`isolation: current`，始终在 `dev`）。

## Capabilities

### New Capabilities

无。本 change 不改变任何系统行为，属于工作流与文档闭环登记。

### Modified Capabilities

无。本 change 不创建任何 `specs/` delta 文件，归档时 `findSpecUpdates` 返回空 ⇒
不会向 `openspec/specs/` 写入需求；`.openspec.yaml` 的 `skip_specs: true` 只让
`specs` 产物合法 `skipped`，不是跳过主 spec 更新的直接原因。

## Impact

- **产物范围**：`openspec/changes/procuraai-archive-delivery-closure/`、
  `docs/superpowers/plans/`、`docs/superpowers/reports/`、
  以及归档后的 `openspec/changes/archive/2026-11-15-procuraai-archive-delivery-closure/`。
- **机器门禁**：Build 执行 `mvn -o -f bone-platform/bone-masterdata/pom.xml clean test`；
  Verify 执行 `bash doc/ai-product/procuraAI/scripts/check-procura-ci.sh`；二者均为真实执行。
- **Git**：`origin/dev` 上新增连续单父提交；`HEAD = origin/dev`；无并行提交夹带时
  `complete-delivery` 可通过。
- **风险**：共享 `dev` 上若在本 change 的 `archive.preflight` 与 `complete-delivery` 之间
  出现新的并行提交，范围会再次被污染。应对：在静默窗口内串行完成，
  若发生则如实记录，不绕过 Guard。

## 验收场景

1. 成功：新 Run `continuation: done`，`complete-delivery --commit <sha>` 通过，
   `baseCommit..HEAD` 每个提交的路径均属本 change 的已批准集合。
2. 成功：`openspec/specs/` 无新增 capability；旧归档目录与 `comet-sdk-blocking-bug.md` 未被修改。
3. 成功：本地 HEAD、`origin/dev`、`FETCH_HEAD` 三方一致，`ahead/behind = 0/0`。
4. 边界：Verify 报告结论必须 PASS 且含独立最终集成审查；FAIL 时不得执行
   `guard verify --apply`。
5. 边界：若交付范围被并行提交污染，保持 Run 未闭合并如实登记，不得手改基线或伪造收据。
