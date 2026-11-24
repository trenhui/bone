---
comet_change: claim-ai-inspection-delivery-closure
role: technical-design
canonical_spec: openspec
---

# Design

## Context

V1.0「AI 后督质检 100% 全量覆盖」的**业务实现已经完成并验证**：候选 SQL 终态白名单纳入 `CLOSED_REJECTED`、配置读取 fail-safe 回落 5%（含 DB 异常）、`InspectionConfigTest` / `InspectionSamplingTerminalScopeTest` 与 4 条变异双向钉子；全量测试 `Tests run: 1674, Failures: 0, Errors: 0, Skipped: 8`；独立审查 IMPORTANT 已修复并复核 PASS；guard build/verify 全 PASS；OpenSpec 已归档、主 spec 已同步、代码已推送 `origin` 双远程。

唯一未完成的是旧 Run `claim-ai-inspection-full-coverage` 的 `complete-delivery` 收据闭合。本次交付闭环设计的约束来自 SDK 的不变式与已归档状态：

- SDK 交付校验（`classic-sdk-application.js:313-348, 430-438`）对 `archive.preflight.baseCommit..HEAD` **逐提交** `diff-tree --no-renames --name-only` 取路径并集（`changed`），再用 `allowed = implementationFiles ∪ archiveFiles ∪ stateFiles` 过滤：
  ```js
  const allowed = (file) => implementationFiles.has(file) ||
      archiveFiles.has(file) || archiveManifest.stateFiles.includes(file);
  const offendingPaths = [...changed].filter((file) => !allowed(file));
  ```
- `implementationFiles` 来自 verify check 收据的 `deliveryInputs`（归档 `comet-state.yaml:1872-1879` 仅 7 个 Java/SQL 文件），`changed` 逐提交累积且注释明确「逐提交检查，避免先夹带未批准文件、再撤销的提交躲过最终 tree 对比」。
- 旧 Run 的 `baseCommit = 879a5352808e7bab457f3eca4f77a01aa99d2a3b`。归档提交 `d4d153af2` 由 pre-commit hook 的 `impact.py --write-map` 自动写入了 `scripts/e2e/impact-map.json`（新增 procurement `SourcingController./rfx/{rfxNo}/award` 端点映射），该文件既不在 verify 的 `deliveryInputs`，也不在归档/状态文件集合，故被判为未批准路径。
- 后续两次补交（`78919ac2e` 补齐被 `.gitignore` 排除的 5 个 `.comet` 归档证据、`b2d0342fd` 把 impact-map.json 恢复为基线内容）未能消除 `changed` 集合里的 impact-map.json；当前 HEAD `b2d0342fd` 的 impact-map.json 已与基线字节一致，是一份干净的新基线。
- 归档后无官方重设基线通道：`propose-archive` / `decide-archive` 要求 `!state.archived`，`reopen-verification` 要求未归档且活动目录存在，`repair-archive` 要求 `archive.execute=failed`，`repair-delivery` 只重判生成物输出声明且明确拒绝「基线已追踪文件」（`classic-sdk-delivery-repair.js:82-101`：`cannot omit a file tracked by the approved baseline`）。
- `collectClassicSdkImplementationInputs` 会排除 `openspec/`、`.comet`、`docs/superpowers` 下的路径，因此归档证据类文档不会成为 `implementationFiles`。
- 当前 `origin` 双远程分支 HEAD 与本地一致；仓库已有同类先例 `procuraai-archive-delivery-closure` 以新 Run 取得 `done` 终态并归档。

**源码取证锚点（供独立复核）**：以上 SDK 行为引用自本机全局安装 `@rpamis/comet` **0.5.2**，路径 `/opt/homebrew/lib/node_modules/@rpamis/comet/dist/domains/comet-classic/`（不在本 Git 工作树内，故不能在仓内 `find` 到；先例 `procuraai-archive-delivery-closure/design.md` 同样以行号引用）。关键片段固化如下：

| 文件 | 行 | 关键内容 |
| --- | --- | --- |
| `classic-sdk-application.js` | 340-348 | `diff-tree --no-commit-id --no-renames --name-only -r -z <commit>` 逐提交取路径并入 `changed`；注释原文「逐提交检查，避免先夹带未批准文件、再撤销的提交躲过最终 tree 对比」 |
| `classic-sdk-application.js` | 430-438 | `const allowed = (file) => implementationFiles.has(file) \|\| archiveFiles.has(file) \|\| archiveManifest.stateFiles.includes(file); const offendingPaths = [...changed].filter((file) => !allowed(file));` |
| `classic-sdk-application.js` | 249-346 | `assertClassicSdkDeliveryInputs`：`implementationFiles` 来自成功 `full.verify.check` action 的 `outcome.output.deliveryInputs` |
| `classic-sdk-delivery-repair.js` | 58, 82-101 | 注释「输出声明只能移除原基线未追踪的生成物，原实现与已归档文档仍须匹配」；`git cat-file -e <base>:<ref>` 成功则抛 `Classic delivery recovery cannot omit a file tracked by the approved baseline: <ref>` |
| `classic-sdk-revision-application.js` | 126-144 | `repair-archive` 要求 `archive.execute` 状态为 failed 且 `assertClassicArchiveUnchanged` |
| `classic-cli-help.js` | 280-283 | `reopen-verification` 要求「尚未执行归档、活动目录仍存在且没有 running/unknown Action」 |

复核方式：`grep -n '<关键字符串>' /opt/homebrew/lib/node_modules/@rpamis/comet/dist/domains/comet-classic/<文件>`；行号以 0.5.2 为准，版本升级后需重新核对。

## Goals / Non-Goals

**Goals:**

- 在新 Run 上取得一个由官方 Guard 全程验证的 `done` 终态。
- 交付范围 `baseCommit..HEAD` 内每个提交的路径都可被 `allowed()` 解释，尤其不含 impact-map.json。
- 不触碰旧 Run、旧归档目录与任何生产代码。

**Non-Goals:**

- 不为旧 Run 补写或回填任何 outcome；不把旧 Run 标成 completed。
- 不新增 delta spec、不合并任何需求到 `openspec/specs/`。
- 不引入新的检查脚本或改 `.comet/config.yaml`。
- 不伪造收据、不声明 not-executed、不绕过 Guard、不改写已推送历史。

## Decisions

### D1：新 change 而非修补旧 Run

- **选择**：新建 `claim-ai-inspection-delivery-closure`（full / `isolation: current`），以当前干净 HEAD `b2d0342fd` 为基线。
- **理由**：SDK 在归档后不提供基线刷新；手改权威 checkpoint 会改变审批绑定产物，等价于绕过 Guard；改写已推送历史会丢失并行会话代码。旧 Run 的所有恢复通道已逐条实证不适用（见 Context）。
- **备选**：`repair-delivery`（基线已追踪文件被明确拒绝）、`reopen-verification`（已归档被拒）、`repair-archive`（archive 已 succeeded 被拒）、force rebase（丢他人代码）、手改 checkpoint（伪造审批）。
- **实证先例**：`procuraai-archive-delivery-closure` 以同样方式取得 `done` 并归档。

### D2：零 delta spec，且 `skip_specs: true` 让跳过合法

- **选择**：`.openspec.yaml` 声明 `skip_specs: true`，本 change 不创建任何 `specs/` 文件。
- **理由**：本 change 无任何行为规格变更。OpenSpec 仅扫描 `changeDir/specs/` 下的 delta 文件生成 spec updates，目录为空 ⇒ `specUpdates` 为空 ⇒ 归档不写 `openspec/specs/`。`skip_specs: true` 的真实作用是让 `specs` 产物合法 `skipped`（否则零 delta 会被 `validate` 判红），**不是**跳过主 spec 更新的直接开关；直接开关是归档 CLI 的 `--skip-specs` 选项。此区分避免把因果归因写错。
- **备选**：写一份「交付闭环」spec —— 会把工作流约定误固化为系统需求，拒绝。

### D3：交付提交只含本 change 的可解释路径

- **选择**：归档提交精确暂存 `openspec/changes/claim-ai-inspection-delivery-closure/`（active 删除 + archive 新增）、`docs/superpowers/plans/`、`docs/superpowers/reports/` 下唯一一份绑定报告、`walkthrough.html`、`.comet/` 归档收据；不做 `git add -A`。
- **理由**：旧 Run 的失败教训是 hook 自动 `--write-map` 把无关派生文件夹带进提交。本 change 只保留一份绑定报告，不创建任何会被删除的 spec 文件，且 pre-commit 阶段以 `BONE_DATA_IMPACT=off` 关闭 impact-map 自动写回（impact-map.json 已与基线一致，无后端 Java 变更需要同步）。
- **备选**：先提交文档再归档 —— 会引入额外路径与中间提交，增加范围噪声，拒绝。

### D4：真实检查命令沿用已验证的两条

- **主判据**（AI 后督模块全量测试）：沿用旧 Run 已跑绿的全路径 Maven 命令 `mvn -pl bone-apps/synap-claim/synap-claim-app -am test`，本 change 实测 `Tests run: 1674, Failures: 0, Errors: 0, Skipped: 8` + `BUILD SUCCESS`（RC=0）。
- **辅助门禁**（SynapClaim 文档族一致性 `python3 doc/ai-product/synapClaim/tools/check_all.py`）：实测 18/21 PASS，3 项失败均为与本 change 无关的预存红项（`_check_manifest` 2 份历史未登记文档、`_check_ruleset_alignment` A9 过时声明、`_ai_native_readiness.py --self-test`）；这些红项在本 change 前已存在，判据为「无新增红项」，按 accepted deviation 登记，**不作为 done 的阻断**。
- **理由**：Maven 命令真实跑绿且与本仓环境匹配；不新增脚本，避免引入未受门禁守护的判据。文档门禁的预存红项若强行修绿，会扩大交付范围、引入与本 change 无关的文档改动，重蹈 impact-map.json 夹带的覆辙。
- **备选**：全仓 `scripts/check.sh` —— 存在与本次无关的预存红项且耗时过长，不作为阻断项，作为 accepted deviation 记录。

### D5：`review_mode: standard` + 独立只读最终集成审查

- **选择**：Build 提案采用 standard 审查深度，Verify 由独立只读审查做最终集成审查。
- **理由**：与旧 change r2 PASS 口径一致，防止「测试通过」被误当作「验收文字与代码一致」。

## Risks / Trade-offs

- **[并行提交再次落入交付范围]** → archive preflight 取当前 HEAD 为基线；从 preflight 到 `complete-delivery` 串行完成；若期间出现新并行提交，保持 Run 未闭合并如实登记，不绕过 Guard。
- **[hook 再次自动写回 impact-map.json]** → 归档与交付提交全部使用精简 hook 且 `BONE_DATA_IMPACT=off`（impact-map.json 已与基线一致，本 change 无 Controller/Java 变更）；提交后以 `git diff-tree` 逐提交核对范围不含 impact-map.json。
- **[Verify 取证后修改输入文件导致 Archive `check inputs changed`]** → 取证前完成全部文档，取证后不再改动任何输入文件；确需改动则 `reopen-verification` 重新取证。
- **[第二个归档目录造成认知重复]** → 在 proposal / design / walkthrough 中明确标注：旧 change 的业务归档是主交付，本 change 仅为交付闭环证据，且本 change 不写主 spec。
- **[别的 change 的在途未跟踪文件被卷入]** → 归档提交逐路径精确 `git add`，绝不 `git add -A`，并在 staged diff 中确认其未出现。

## Migration Plan

1. **Open**：完成 proposal / design / tasks 三产物，`comet state artifacts` 通过全闭包，`comet guard open --apply` 取得用户确认后进入 Design。
2. **Design**：补强本设计源码实证，用户确认正式设计后 `propose-design → decide-design → complete-design`；设计 accepted 后不再改动 design.md，避免证据失效循环。
3. **Build**：确认 build 配置（autonomous/tdd 或等价的文档型配置），`propose-build → decide-build → submit-plan → complete-build → guard build --apply` 跑真实测试命令。
4. **Verify**：写唯一绑定报告，`guard verify --report ... --apply` 跑 Maven 全量测试主判据 + 文档族辅助门禁（预存红项按 accepted deviation 登记）+ 独立审查 PASS。
5. **Archive / Delivery**：`propose-archive(push) → guard archive --apply`，精确暂存交付提交推送双远程，`complete-delivery --commit <sha>` 取得 `done` 终态。
6. **回滚**：任一阶段 Guard 失败则按 SDK recovery 正规恢复（mark-unknown / retry / repair-*），绝不伪造收据或改写历史；若范围再次被污染，保持 Run 未闭合如实登记。
