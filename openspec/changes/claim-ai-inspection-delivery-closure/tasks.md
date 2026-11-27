# Tasks

## 1. 范围与偏差登记

- [x] 1.1 在 `proposal.md` 记录旧 Run `complete-delivery` 范围阻塞的实证根因、非目标与验收场景；验证：`comet state artifacts claim-ai-inspection-delivery-closure --json` 报告 proposal 完整，且文中含 `baseCommit`、`allowed()`、`impact-map.json`、`b2d0342fd` 四项证据。 <!-- comet-task:749c0f4b-5db7-4743-b0a4-5bde13d2cac1 -->
- [x] 1.2 在 `design.md` 记录 D1–D5 技术取舍、风险缓解与回滚方案；验证：`comet state complete-design` 通过，文中含 `classic-sdk-application.js`、`cannot omit a file tracked by the approved baseline` 等源码实证。 <!-- comet-task:75671815-637f-4515-8be4-fc314118a168 -->
- [x] 1.3 在 `tasks.md` 建立可在归档前逐项验证的任务清单，并将归档/交付动作保留为 `Workflow follow-up` 普通条目；验证：`comet state tasks claim-ai-inspection-delivery-closure --json` 可解析全部复选框且 `needsIds: false`。 <!-- comet-task:bc95016c-c58e-404d-8cac-e7b7949b0208 -->

## 2. 机器验证与闭环证据

- [x] 2.1 真实执行 AI 后督模块全量测试，确认交付闭环登记未破坏 V1.0 实现；验证：全路径 Maven 命令输出 `Tests run: 1674, Failures: 0, Errors: 0, Skipped: 8` 与 `BUILD SUCCESS`。 <!-- comet-task:a8e6d67e-c564-41c7-915d-e644da877490 -->
- [x] 2.2 真实执行 SynapClaim 文档族门禁，确认交付闭环登记未引入新增红项；验证：`python3 doc/ai-product/synapClaim/tools/check_all.py` 实测 18/21 PASS、3 项失败均为与本 change 无关的预存红项（`_check_manifest` 2 份历史未登记文档、`_check_ruleset_alignment` A9 过时声明、`_ai_native_readiness.py --self-test`），已在 design D4 与 plan 中作为 accepted deviation 登记；判据为「无新增红项 + 3 项预存红项如实登记」，而非 `GATE_RC=0`（该口径与预存红项事实冲突，已按独立审查 I-1 修正）。 <!-- comet-task:9fd926f1-0d92-4133-a2bc-498583b939a2 -->
- [x] 2.3 完成独立只读最终集成审查（结论 PASS，无 CRITICAL / IMPORTANT），并定稿唯一一份 Verify 报告的目标路径与内容；验证：独立审查结论 PASS，目标报告为 `docs/superpowers/reports/` 下单层 `.md`（zh-CN）。报告首次写入与 `guard verify --apply` 属 `phase: verify` 的阶段动作，验收标准逐字保留在 `Workflow follow-up`，由 Runtime 绑定真实检查收据，Build 阶段不得提前声明其结果。 <!-- comet-task:742151db-7d8b-47dc-b11a-344dae47d09c -->
- [x] 2.4 在 Build 阶段以完整重建模式生成 `walkthrough.html`，同步本 change 范围、验证区与旧 Run 保留事实；Verify 阶段若仅补验证结果则用 `--verification-only`，取证后不再修改任何输入文件（避免 Archive `check inputs changed`）。 <!-- comet-task:663a2aea-d891-484b-99c8-63556b3a4e3a -->

## 3. 交付范围前置核对

- [x] 3.1 归档前核对分支 HEAD、`origin/tracked`、`FETCH_HEAD` 三方一致，且 ahead/behind = 0/0；验证：`git rev-parse HEAD origin/<branch>` 与 `git rev-list --left-right --count` 输出一致。 <!-- comet-task:4fcef352-ef71-4b63-a3b2-c2432eab4903 -->
- [x] 3.2 核对 `openspec/specs/` 无新增 capability，旧归档目录未被修改；验证：`git status --porcelain` 中本 change 外无已跟踪文件的修改或 staged 变更（其他 change 的未跟踪在途文件允许存在，但不得进入 staged 与归档提交）。 <!-- comet-task:f4d60117-db87-44a3-b893-d3bda6e6a18a -->
- [x] 3.3 核对 impact-map.json 与基线字节一致、且交付范围内无任何提交触及它；验证：`git diff --quiet <base> HEAD -- scripts/e2e/impact-map.json` 为真，且逐提交 `git diff-tree --name-only` 中不含 impact-map.json。 <!-- comet-task:44e12816-0452-4e82-8191-f9d0fc07570a -->

## Workflow follow-up

- 在 `phase: verify` 首次写入唯一绑定报告 `docs/superpowers/reports/2026-11-24-claim-ai-inspection-delivery-closure-verify.md`（单层 `.md`、zh-CN、结论 PASS），并执行 `comet guard claim-ai-inspection-delivery-closure verify --report <report> --apply --json -- <check-command>`；验收：`exitCode=0`、`ALL CHECKS PASSED — phase=archive` 且 `inputBefore == inputAfter`。
- `propose-archive` / `decide-archive(push)` / `guard archive --apply` 完成 OpenSpec 归档。
- 精确暂存本 change 的实现、归档目录、plan、Verify 报告、walkthrough 与 `.comet/` 收据（不使用 `git add -A`，且 `BONE_DATA_IMPACT=off` 防止 impact-map 自动写回），创建连续单父提交并推送 `origin` 双远程。
- `comet state complete-delivery claim-ai-inspection-delivery-closure --commit <sha>` 与 `comet state next --json` 确认 `continuation: done`。
- 若期间出现新的并行提交导致范围再次被污染，保持 Run 未闭合并如实登记，不绕过 Guard、不改写历史。
