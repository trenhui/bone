---
comet_change: procuraai-archive-delivery-closure
role: technical-design
canonical_spec: openspec
archived-with: 2026-11-15-procuraai-archive-delivery-closure
status: final
---

# Design

## Context

动机与范围见 `proposal.md`。这里只记录实现该交付闭环的技术取舍。

现场事实（2026-11-15 实证）：

- `procuraai-ai-first-landing` 的归档目录、r1/r2 验证报告、当时 250 例单测证据均已推送；
  其 Run 停在 `full.archive.deliver :72`，`archived: true`、`verifyResult: pass`。
  （并行会话 `dde05289b` 之后新增了断言，本 change 实测基线为 **251 例**，见 D4。）
- SDK 交付校验（`classic-sdk-application.js:313-348, 430-438`）对
  `archive.preflight.baseCommit..HEAD` **逐提交** `diff-tree` 取路径并集，
  再用 `allowed = implementationFiles ∪ archiveFiles ∪ stateFiles` 过滤。
  `285827275` 的父提交恰是旧 Run 的 `baseCommit = 16c2fec85`，因此并行提交被 rebase
  后必然落入该范围。旧归档提交 `82e60e430` 自身另有两组被 SDK 拒绝的路径：
  1 份非绑定的历史 r1 报告，以及 3 条 `specs/procurement/*/spec.md` active 路径
  （git 视角为 `R100` 重命名迁入 `deferred-specs/`，SDK 用 `--no-renames`
  故表现为删除）。这些路径不在 `allowed()` 集合内；该提交其余路径是否可解释
  未逐条判定，本设计不作穷举断言。
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

### D2：零 delta spec，且 `skip_specs: true` 让跳过合法

- **选择**：`.openspec.yaml` 声明 `skip_specs: true`，本 change 不创建任何 `specs/` 文件。
- **理由**：本 change 无任何行为规格变更。OpenSpec 仅扫描 `changeDir/specs/` 下的
  delta 文件来生成 spec updates，目录为空 ⇒ `specUpdates` 为空 ⇒ 归档不写
  `openspec/specs/`。`skip_specs: true` 的真实作用是让 `specs` 产物合法 `skipped`
  （否则零 delta 会被 `validate` 判红），**不是**跳过主 spec 更新的直接开关；
  直接开关是归档 CLI 的 `--skip-specs` 选项。此区分避免把因果归因写错。
- **备选**：写一份「交付闭环」spec —— 会把工作流约定误固化为系统需求，拒绝。

### D3：交付提交只含本 change 的可解释路径

- **选择**：归档提交精确暂存 `openspec/changes/procuraai-archive-delivery-closure/`
  （active 删除 + archive 新增）、`docs/superpowers/plans/` 与唯一一份绑定的
  Verify 报告、`walkthrough.html`、`.comet/` 归档收据；不做 `git add -A`。
- **理由**：旧 Run 的失败教训是「非绑定的 r1 历史报告」与「3 条被迁出的
  `specs/procurement/*` active 路径」不在允许集合内。本 change 只保留一份绑定报告，
  且不创建任何会被删除的 spec 文件。另一个 change 的未跟踪
  `synapclaim-architecture-convergence/comet-state.yaml` 属在途资产，
  绝不进入 staged 与归档提交（见 Risks）。
- **备选**：先提交文档再归档 —— 会引入额外路径与中间提交，增加范围噪声。

### D4：真实检查命令沿用已验证的两条

- **Build**：`/Users/renhui.trh/.m2/wrapper/dists/apache-maven-3.6.3-bin/1iopthnavndlasol9gbrbg6bf2/apache-maven-3.6.3/bin/mvn -o -f bone-platform/bone-masterdata/pom.xml clean test`
  （本 change 基线实测 **251 例**；旧 change 的 250 例是并行会话补断言前的数字）。
- **Verify**：`bash doc/ai-product/procuraAI/scripts/check-procura-ci.sh`
  （主门禁 10/10、注入自检 146/146 与 69/69）。
- **理由**：二者在旧 change 上真实跑绿且与本仓环境匹配；不新增脚本，
  避免引入未受门禁守护的判据。
- **备选**：全仓 `scripts/check.sh` —— 存在与本次无关的预存红项，且约需 14 分钟，
  不作为本 change 的阻断项，作为 accepted deviation 记录。

### D5：`review_mode: standard` + 独立只读最终集成审查

- **选择**：Build 提案采用 standard，Verify 由独立只读子代理做最终集成审查。
- **理由**：与旧 change 的 r2 PASS 口径一致，防止「测试通过」被误当作
  「验收文字与代码一致」。

## Risks / Trade-offs

- **[并行提交再次落入交付范围]** → archive preflight 取当前 HEAD 为基线；
  从 preflight 到 `complete-delivery` 串行完成；若期间出现新并行提交，
  保持 Run 未闭合并如实登记，不绕过 Guard。
- **[Verify 取证后修改 `walkthrough.html` 导致 Archive `check inputs changed`]** →
  取证前完成全部文档，取证后不再改动任何输入文件；确需改动则
  `reopen-verification` 重新取证。
- **[第二个归档目录造成认知重复]** → 在 proposal / design / walkthrough 中
  明确本 change 只做闭环登记，业务真源仍在
  `openspec/changes/archive/2026-11-15-procuraai-ai-first-landing/`。
- **[`.comet/` 收据被 `.gitignore` 漏掉]** → 归档后用 `git add -f` 精确补交，
  并在 `delivery --verify` 前逐文件核对 `git ls-tree HEAD`。
- **[别的 change 的在途未跟踪文件被卷入]** → 当前存在
  `openspec/changes/synapclaim-architecture-convergence/comet-state.yaml`（未跟踪）。
  它属于另一 change，不属本 change 交付范围；归档提交逐路径精确 `git add`，
  绝不 `git add -A`，并在 staged diff 中确认其未出现。未跟踪文件不进入
  `diff-tree` 范围，SDK 不会把它算作交付路径。
- **[独立集成审查发现验收文字与产物不自洽]** → 首轮 r1 审查 FAIL
  （1 IMPORTANT：1.1 判据要求 `proposal.md` 含 `285827275` 却缺失；5 WARNING：`skip_specs`
  归因、R100 表述、未跟踪在途文件、命令完整化、计划性措辞）。此外复核时额外发现
  250/251 例数字漂移，属独立问题、不计入上述 5 WARNING，也已一并修正。全部逐条修正后
  复验；FAIL 期间不得勾选任务或运行 `guard verify --apply`。
- **[两个 Run 同时处于 archive/running]** → 旧 Run 已无 pending 外部动作，
  本 change 使用独立 Run ID 与独立归档目录，selection 已指向新 change。

## Migration Plan

1. 按本设计创建 proposal / design / tasks，完成 Open Guard。
2. Design 阶段确认正式设计并完成 handoff；Build 阶段提交 plan、勾选任务、跑检查。
3. Verify 阶段真实执行两条检查命令、写 PASS 报告、做独立审查、`guard verify --apply`。
4. Archive 阶段 `propose-archive` / `decide-archive(push)` / `guard archive --apply`。
5. 精确暂存并创建唯一归档提交，push `origin/dev`，执行
   `complete-delivery --commit <sha>`，核对 `state next` 的 `done`。
6. **回滚**：本 change 不改业务代码；如需撤销，只需 revert 本 change 的归档提交
   并保留旧归档目录。绝不 force push。

## Open Questions

无。所有会改变范围或任务拆分的未知项已在 proposal 与 decision-point 中确认。
