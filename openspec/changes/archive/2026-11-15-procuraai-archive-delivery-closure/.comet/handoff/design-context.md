# Comet Design Handoff

- Change: procuraai-archive-delivery-closure
- Phase: design
- Mode: compact
- Context hash: 6562caf3169d0367c7e486ba683d898b8ed8369ba7bc68d23a90585f4b1b88cd

Generated-by: comet-handoff.sh
Task hash policy: task-content-v1. Read tasks.md for live completion; excerpts are design-time context.

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/procuraai-archive-delivery-closure/proposal.md

- Source: openspec/changes/procuraai-archive-delivery-closure/proposal.md
- Lines: 1-67
- SHA256: c9aeb998d6e9bb8b38407d2301ba3bc5f589e84f0604248262421ea196187570

```md
# Proposal

## Why

`procuraai-ai-first-landing` 的业务交付（通用主数据 Golden Record 去重内核、r1/r2 验证报告、
OpenSpec 归档目录）已全部提交并推送 `origin/dev`，但该 Comet Classic Run 的
`complete-delivery` 收据无法干净闭合：SDK 以「`archive.preflight.baseCommit..HEAD` 只含本 change
已验证实现」为不变量，而并行会话在共享 `dev` 上的提交被 `fetch → rebase → push` 夹进该范围，
`allowed()` 对非本 change 提交无豁免。归档后 `propose-archive` / `archive-reopen` /
`repair-archive` / `repair-delivery` 均无法刷新基线（见归档目录内
`comet-sdk-blocking-bug.md`）。因此需要在干净基线上补一次可机器验证的交付闭环，
并把旧 Run 的偏差作为可审计事实保留，而不是伪造收据或改写已推送历史。

## What Changes

- 新增一个独立的 Classic full change（`procuraai-archive-delivery-closure`），以当前
  `origin/dev` HEAD 为基线，完整走 Open → Design → Build → Verify → Archive → Delivery。
- 新增可审计的交付闭环证据：范围声明、技术设计、任务清单、实施计划、验证报告、
  `walkthrough.html`；全部随本 change 归档。
- 新增本 change 的 OpenSpec 归档目录；归档时 `deltaCount = 0`（`skip_specs: true`），
  不向 `openspec/specs/` 写入任何需求。
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

无。`.openspec.yaml` 已设置 `skip_specs: true`，归档不产生规格增量。

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

```

## openspec/changes/procuraai-archive-delivery-closure/design.md

- Source: openspec/changes/procuraai-archive-delivery-closure/design.md
- Lines: 1-118
- SHA256: 6a53ccf56f2d654fa1f3f5909b8ca79a4d947e4b9060f98028903dac6272530a

[TRUNCATED]

```md
---
comet_change: procuraai-archive-delivery-closure
role: technical-design
canonical_spec: openspec
---

# Design

## Context

动机与范围见 `proposal.md`。这里只记录实现该交付闭环的技术取舍。

现场事实（2026-11-15 实证）：

- `procuraai-ai-first-landing` 的归档目录、r1/r2 验证报告、250 例单测证据均已推送；
  其 Run 停在 `full.archive.deliver :72`，`archived: true`、`verifyResult: pass`。
- SDK 交付校验（`classic-sdk-application.js:313-348, 430-438`）对
  `archive.preflight.baseCommit..HEAD` **逐提交** `diff-tree` 取路径并集，
  再用 `allowed = implementationFiles ∪ archiveFiles ∪ stateFiles` 过滤。
  `285827275` 的父提交恰是旧 Run 的 `baseCommit = 16c2fec85`，因此并行提交被 rebase
  后必然落入该范围；旧归档提交自身还含 1 份历史 r1 报告与 3 份已删除 delta spec，
  二者也不在允许集合内。
- 归档后无官方重设基线通道：`propose-archive` / `decide-archive` 要求
  `!state.archived`，`archive-reopen` 在 `archived=true` 时拒绝，
  `repair-archive` 回退到 `phase: open`，`repair-delivery` 只重判生成物输出声明，
  且会因 `.comet/config.yaml` 变更拒绝重新界定输入归属。
- `collectClassicSdkImplementationInputs` 会排除 `openspec/`、`.comet`、
  `docs/superpowers` 下的路径，因此别的 change 的在途目录不会成为本 change 的
  `implementationFiles`。
- 当前 `origin/dev = 46e59ea7c`，其后无并行提交；新 Run 的 `baseRef` 已绑定该 SHA。

## Goals / Non-Goals

**Goals:**

- 在新 Run 上取得一个由官方 Guard 全程验证的 `done` 终态。
- 交付范围 `baseCommit..HEAD` 内每个提交的路径都可被 `allowed()` 解释。
- 不触碰旧 Run、旧归档目录与已有业务代码。

**Non-Goals:**

- 不为旧 Run 补写或回填任何 outcome；不把旧 Run 标成 completed。
- 不新增 delta spec、不合并任何需求到 `openspec/specs/`。
- 不引入新的检查脚本或改 `.comet/config.yaml`（该配置变更正是 `repair-delivery`
  在旧 Run 上失败的原因之一）。

## Decisions

### D1：新 change 而非修补旧 Run

- **选择**：新建 `procuraai-archive-delivery-closure`（full / `isolation: current`）。
- **理由**：SDK 在归档后不提供基线刷新；手改权威 checkpoint 会改变审批绑定产物，
  等价于绕过 Guard；改写已推送历史会丢失并行会话的 160 个文件。
- **备选**：`repair-delivery`（已实证因配置变更被拒）、`archive-reopen`（已实证被
  `archived=true` 拒绝）、force rebase（会丢他人代码）、手改 checkpoint（伪造审批）。

### D2：`skip_specs: true` 且 `deltaCount = 0`

- **选择**：`.openspec.yaml` 声明 `skip_specs: true`，不创建 `specs/`。
- **理由**：本 change 无任何行为规格变更；按 OpenSpec 官方语义合法跳过，
  归档时不会向主 spec 写入未实现的 `SHALL/MUST`。
- **备选**：写一份「交付闭环」spec —— 会把工作流约定误固化为系统需求，拒绝。

### D3：交付提交只含本 change 的可解释路径

- **选择**：归档提交精确暂存 `openspec/changes/procuraai-archive-delivery-closure/`
  （active 删除 + archive 新增）、`docs/superpowers/plans/` 与唯一一份绑定的
  Verify 报告、`walkthrough.html`、`.comet/` 归档收据；不做 `git add -A`。
- **理由**：旧 Run 的失败教训是「r1 历史报告」和「3 份已删除 delta spec」
  不在允许集合内。本 change 只保留一份绑定报告，且不创建任何会被删除的 spec 文件。
- **备选**：先提交文档再归档 —— 会引入额外路径与中间提交，增加范围噪声。

### D4：真实检查命令沿用已验证的两条

- **Build**：`mvn -o -f bone-platform/bone-masterdata/pom.xml clean test`（250 例）。
- **Verify**：`bash doc/ai-product/procuraAI/scripts/check-procura-ci.sh`
  （主门禁 10/10、注入自检 146/146 与 69/69）。
- **理由**：二者在旧 change 上真实跑绿且与本仓环境匹配；不新增脚本，
  避免引入未受门禁守护的判据。
- **备选**：全仓 `scripts/check.sh` —— 存在与本次无关的预存红项，且约需 14 分钟，

```

Full source: openspec/changes/procuraai-archive-delivery-closure/design.md

## openspec/changes/procuraai-archive-delivery-closure/tasks.md

- Source: openspec/changes/procuraai-archive-delivery-closure/tasks.md
- Lines: 1-26
- SHA256: 8951978a00748338972bbcf9e484b4df51ff52c0d9d185d7eeffaf69af267830

```md
# Tasks

## 1. 范围与偏差登记

- [x] 1.1 在 `proposal.md` 记录旧 Run `complete-delivery` 范围阻塞的实证根因、非目标与验收场景；验证：`comet state artifacts procuraai-archive-delivery-closure --json` 报告 `proposal: done`，且文中含 `baseCommit`、`allowed()`、`285827275` 三项证据。 <!-- comet-task:cb352cac-cf75-4a57-bef0-94ba67f4b6f1 -->
- [x] 1.2 在 `design.md` 记录 D1–D5 技术取舍、风险缓解与回滚方案，并保证 YAML frontmatter 完整；验证：`comet state complete-design` 通过，frontmatter 含 `comet_change` / `role: technical-design` / `canonical_spec: openspec`。 <!-- comet-task:b7291f3d-c1d0-4dae-adda-a70afe19435d -->
- [x] 1.3 在 `tasks.md` 建立可在归档前逐项验证的任务清单，并将归档/交付动作保留为 `Workflow follow-up` 普通条目；验证：`comet state tasks procuraai-archive-delivery-closure --json` 可解析全部复选框且 `needsIds: false`。 <!-- comet-task:88cb7e9a-fd2d-494e-852e-1ec52f279399 -->

## 2. 机器验证与闭环证据

- [ ] 2.1 真实执行 masterdata 模块单测，确认既有 Golden Record 内核未被本 change 破坏；验证：`mvn -o -f bone-platform/bone-masterdata/pom.xml clean test` 输出 `Tests run: 250, Failures: 0, Errors: 0` 与 `BUILD SUCCESS`。 <!-- comet-task:cf2ba90a-f9d4-456b-a2b0-532b193c0766 -->
- [ ] 2.2 真实执行 procuraAI 文档族门禁，确认交付闭环登记未破坏文档族一致性；验证：`bash doc/ai-product/procuraAI/scripts/check-procura-ci.sh` 输出 `10/10`、`146/146`、`69/69` 且 `GATE_RC=0`。 <!-- comet-task:a9ee0e31-8888-4541-a6c3-94ea5d675348 -->
- [ ] 2.3 写唯一一份绑定的验证报告并完成独立只读最终集成审查（结论 PASS，无 CRITICAL / IMPORTANT）；验证：报告位于 `docs/superpowers/reports/` 单层 `.md`，`comet guard ... verify --apply` 返回 `exitCode=0` 且 `inputBefore == inputAfter`。 <!-- comet-task:d88a834c-492e-4efe-8e66-207503c22ad1 -->
- [ ] 2.4 同步 `walkthrough.html` 到真实状态（本 change 范围、验证区、旧 Run 保留事实）；验证：`comet walkthrough classic` 重建成功，且取证后不再修改任何输入文件。 <!-- comet-task:24fa9356-9eb8-46ed-98be-f4ac5ec17bfe -->

## 3. 交付范围前置核对

- [ ] 3.1 归档前核对 `origin/dev`、本地 HEAD 与 `FETCH_HEAD` 三方一致，且 `ahead/behind = 0/0`；验证：`git rev-parse HEAD origin/dev` 与 `git rev-list --left-right --count` 输出一致。 <!-- comet-task:0d307b82-058d-4868-8b7a-2a4b9156ae1c -->
- [ ] 3.2 核对 `openspec/specs/` 无新增 capability，旧归档目录与 `comet-sdk-blocking-bug.md` 未被修改；验证：`git ls-tree -r --name-only HEAD -- openspec/specs/procurement` 为空，`git status --porcelain` 对本 change 外路径无改动。 <!-- comet-task:e17d107b-02c9-4691-8064-854675601b59 -->

## Workflow follow-up

- `propose-archive` / `decide-archive(push)` / `guard archive --apply` 完成 OpenSpec 归档。
- 精确暂存本 change 的实现、归档目录、plan、Verify 报告、walkthrough 与 `.comet/` 收据（不使用 `git add -A`），创建连续单父提交并推送 `origin/dev`。
- `comet state complete-delivery procuraai-archive-delivery-closure --commit <sha>` 与 `comet state next --json` 确认 `continuation: done`。
- 若期间出现新的并行提交导致范围再次被污染，保持 Run 未闭合并如实登记，不绕过 Guard、不改写历史。

```

## openspec/changes/procuraai-archive-delivery-closure/.openspec.yaml

- Source: openspec/changes/procuraai-archive-delivery-closure/.openspec.yaml
- Lines: 1-5
- SHA256: 54b731dee85ec8b6633678212e35762f6d059809d83ba1ccb7391c067830b377

```md
schema: spec-driven
created: 2026-11-15
# 本 change 只做 Comet 交付闭环与偏差登记，不涉及任何行为规格变更；
# 按 OpenSpec 官方 skip_specs 语义跳过 specs 产物，归档时不写入 openspec/specs/。
skip_specs: true

```
