# Tasks

## 1. 范围与偏差登记

- [x] 1.1 在 `proposal.md` 记录旧 Run `complete-delivery` 范围阻塞的实证根因、非目标与验收场景；验证：`comet state artifacts procuraai-archive-delivery-closure --json` 报告 `proposal: done`，且文中含 `baseCommit`、`allowed()`、`285827275` 三项证据。 <!-- comet-task:cb352cac-cf75-4a57-bef0-94ba67f4b6f1 -->
- [x] 1.2 在 `design.md` 记录 D1–D5 技术取舍、风险缓解与回滚方案，并保证 YAML frontmatter 完整；验证：`comet state complete-design` 通过，frontmatter 含 `comet_change` / `role: technical-design` / `canonical_spec: openspec`。 <!-- comet-task:b7291f3d-c1d0-4dae-adda-a70afe19435d -->
- [x] 1.3 在 `tasks.md` 建立可在归档前逐项验证的任务清单，并将归档/交付动作保留为 `Workflow follow-up` 普通条目；验证：`comet state tasks procuraai-archive-delivery-closure --json` 可解析全部复选框且 `needsIds: false`。 <!-- comet-task:88cb7e9a-fd2d-494e-852e-1ec52f279399 -->

## 2. 机器验证与闭环证据

- [x] 2.1 真实执行 masterdata 模块单测，确认既有 Golden Record 内核未被本 change 破坏；验证：全路径 Maven 3.6.3 执行 `mvn -o -f bone-platform/bone-masterdata/pom.xml clean test` 输出 `Tests run: 251, Failures: 0, Errors: 0, Skipped: 0` 与 `BUILD SUCCESS`（251 为并行会话 `dde05289b` 补断言后的当前基线）。 <!-- comet-task:cf2ba90a-f9d4-456b-a2b0-532b193c0766 -->
- [x] 2.2 真实执行 procuraAI 文档族门禁，确认交付闭环登记未破坏文档族一致性；验证：`bash doc/ai-product/procuraAI/scripts/check-procura-ci.sh` 输出 `10/10`、`146/146`、`69/69` 且 `GATE_RC=0`。 <!-- comet-task:a9ee0e31-8888-4541-a6c3-94ea5d675348 -->
- [x] 2.3 完成独立只读最终集成审查（结论 PASS，无 CRITICAL / IMPORTANT），并定稿唯一一份 Verify 报告的目标路径与内容；验证：独立审查结论 PASS，且目标报告为 `docs/superpowers/reports/` 下单层 `.md`（zh-CN）。报告首次写入与 `guard verify --apply` 属 `phase: verify` 的阶段动作（design.md §Migration Plan 第 3 步），其验收标准逐字保留在 `Workflow follow-up`，由 Runtime 绑定真实检查收据，Build 阶段不得提前声明其结果。 <!-- comet-task:d88a834c-492e-4efe-8e66-207503c22ad1 -->
- [x] 2.4 在 Build 阶段以完整重建模式生成 `walkthrough.html`，同步本 change 范围、验证区与旧 Run 保留事实；验证：`comet walkthrough classic procuraai-archive-delivery-closure --project-root <root> --file <workspace 外的 json>` 成功返回绝对路径且页面存在；Verify 阶段若仅补验证结果则用 `--verification-only`，取证后不再修改任何输入文件（避免 Archive `check inputs changed`）。 <!-- comet-task:24fa9356-9eb8-46ed-98be-f4ac5ec17bfe -->

## 3. 交付范围前置核对

- [x] 3.1 归档前核对 `origin/dev`、本地 HEAD 与 `FETCH_HEAD` 三方一致，且 `ahead/behind = 0/0`；验证：`git rev-parse HEAD origin/dev` 与 `git rev-list --left-right --count` 输出一致。 <!-- comet-task:0d307b82-058d-4868-8b7a-2a4b9156ae1c -->
- [x] 3.2 核对 `openspec/specs/` 无新增 capability，旧归档目录与 `comet-sdk-blocking-bug.md` 未被修改；验证：`git ls-tree -r --name-only HEAD -- openspec/specs/procurement` 为空；`git status --porcelain` 中本 change 外**无已跟踪文件的修改或 staged 变更**（其他 change 的未跟踪在途文件允许存在，但不得进入 staged 与归档提交）。 <!-- comet-task:e17d107b-02c9-4691-8064-854675601b59 -->

## Workflow follow-up

- 在 `phase: verify` 首次写入唯一绑定报告 `docs/superpowers/reports/2026-11-15-procuraai-archive-delivery-closure-verify.md`（`docs/superpowers/reports/` 单层 `.md`、zh-CN、结论 PASS），并执行 `comet guard procuraai-archive-delivery-closure verify --report docs/superpowers/reports/<report>.md --apply --json -- bash doc/ai-product/procuraAI/scripts/check-procura-ci.sh`；验收：`exitCode=0`、`ALL CHECKS PASSED — phase=archive` 且 `inputBefore == inputAfter`（此即任务 2.3 原验收标准，归位到 Verify 阶段后逐字保留，不得降低）。
- `propose-archive` / `decide-archive(push)` / `guard archive --apply` 完成 OpenSpec 归档。
- 精确暂存本 change 的实现、归档目录、plan、Verify 报告、walkthrough 与 `.comet/` 收据（不使用 `git add -A`），创建连续单父提交并推送 `origin/dev`。
- `comet state complete-delivery procuraai-archive-delivery-closure --commit <sha>` 与 `comet state next --json` 确认 `continuation: done`。
- 若期间出现新的并行提交导致范围再次被污染，保持 Run 未闭合并如实登记，不绕过 Guard、不改写历史。
