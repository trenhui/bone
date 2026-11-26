---
change: claim-ai-inspection-delivery-closure
design-doc: openspec/changes/claim-ai-inspection-delivery-closure/design.md
base-ref: b2d0342fdaf4c33405d411dba61802a4f7f0a614
---

# 实施计划：交付闭环（claim-ai-inspection-delivery-closure）

<!-- comet-task-authority: openspec/changes/claim-ai-inspection-delivery-closure/tasks.md -->

## 目标与任务权威

在干净基线 `b2d0342fdaf4c33405d411dba61802a4f7f0a614`（其 `scripts/e2e/impact-map.json` 已与基线一致）上，用官方 Guard 全程验证地把一个新 Classic Run 推进到 `done`，不触碰旧 Run `claim-ai-inspection-full-coverage`、旧归档目录、任何生产代码与 `openspec/specs/`。任务正文与完成状态**只以** `openspec/changes/claim-ai-inspection-delivery-closure/tasks.md` 为准，本计划不复制 checkbox。

## 执行配置

| 字段 | 值 | 依据 |
| --- | --- | --- |
| `build_mode` | `direct` | 无业务代码变更，由当前会话按计划直接实施 |
| `tdd_mode` | `direct` | 不新增行为，无需 RED/GREEN；仍做真实回归检查与缺陷排查 |
| `review_mode` | `standard` | design.md D5：风险任务复核 + Verify 唯一一次独立最终集成审查 |
| `direct_override` | `true` | full direct 已显式选择并随 Build 配置提案批准 |
| `subagent_dispatch` | `null` | 不委派实现代理 |

环境说明：Superpowers `writing-plans` / `test-driven-development` /
`requesting-code-review` / `verification-before-completion` 技能在本环境未注册，
按 Comet 规则如实说明，采用等价的实证方法，不虚构加载记录。

## 任务范围与依赖

| 任务 | 依赖 | 实施内容 | 验收 |
| --- | --- | --- | --- |
| 1.1 | — | proposal.md 记录范围阻塞实证根因、非目标、5 条验收场景 | `state artifacts` 报 proposal 完整，含 `baseCommit`/`allowed()`/`impact-map.json`/`b2d0342fd` |
| 1.2 | 1.1 | design.md 记录 D1–D5、风险、回滚，补齐 Comet frontmatter | `complete-design` 通过，含源码实证 |
| 1.3 | 1.2 | tasks.md 建立 10 项可验证任务，归档/交付动作留作 `Workflow follow-up` | `state tasks` 解析成功且 `needsIds: false` |
| 2.1 | 1.3 | 真实跑 synap-claim 全量测试 | `Tests run: 1674, Failures: 0, Errors: 0, Skipped: 8`、`BUILD SUCCESS` |
| 2.2 | 1.3 | 真实跑 SynapClaim 文档族门禁并核对预存红项 | 记录 `check_all.py` 结果；3 项与本 change 无关的预存红项作为 accepted deviation 如实登记，不修无关文档 |
| 2.3 | 2.1, 2.2 | 独立只读最终集成审查 + 定稿唯一 Verify 报告的目标路径与内容 | 独立审查 PASS；目标为 `docs/superpowers/reports/` 单层 `.md`；报告写入与 `guard verify --apply` 归 Verify 阶段，验收原句见 `Workflow follow-up` |
| 2.4 | 2.3 | Build 阶段完整重建 `walkthrough.html` | `comet walkthrough ...` 成功且页面存在；取证后零改动 |
| 3.1 | 2.4 | 分支 HEAD / origin tracked / FETCH_HEAD 三方 SHA 与 ahead/behind 核对 | 一致且 `0/0` |
| 3.2 | 3.1 | 主 spec 无新增 capability、旧归档未被改动 | `git status --porcelain` 中本 change 外无已跟踪文件的修改或 staged 变更（其他 change 的未跟踪在途文件允许存在，但不得进入 staged 与归档提交） |
| 3.3 | 3.2 | impact-map.json 与基线一致且交付范围内无提交触及它 | `git diff --quiet <base> HEAD -- scripts/e2e/impact-map.json` 为真，逐提交 `diff-tree` 中不含它 |

<!-- comet-task-ref:749c0f4b-5db7-4743-b0a4-5bde13d2cac1 -->
<!-- comet-task-ref:75671815-637f-4515-8be4-fc314118a168 -->
<!-- comet-task-ref:bc95016c-c58e-404d-8cac-e7b7949b0208 -->
<!-- comet-task-ref:a8e6d67e-c564-41c7-915d-e644da877490 -->
<!-- comet-task-ref:9fd926f1-0d92-4133-a2bc-498583b939a2 -->
<!-- comet-task-ref:742151db-7d8b-47dc-b11a-344dae47d09c -->
<!-- comet-task-ref:663a2aea-d891-484b-99c8-63556b3a4e3a -->
<!-- comet-task-ref:4fcef352-ef71-4b63-a3b2-c2432eab4903 -->
<!-- comet-task-ref:f4d60117-db87-44a3-b893-d3bda6e6a18a -->
<!-- comet-task-ref:44e12816-0452-4e82-8191-f9d0fc07570a -->

## 约束与高风险处理

- **绝不** `git add -A`；归档提交只精确暂存本 change 的 active 删除、archive 新增、
  plan、唯一一份 Verify 报告、walkthrough 与 `.comet/` 收据。
  其他 change 的未跟踪在途文件允许留在工作区，但 staged diff 中不得出现。
- **归档提交阶段强制 `BONE_DATA_IMPACT=off`**：pre-commit hook 的 `impact.py --write-map`
  会把并行会话的端点映射误写回 `scripts/e2e/impact-map.json`（旧 Run 的教训）；
  本 change 无 Controller/Java 变更需要同步，关闭该步骤并重开精简 hook，
  提交后逐提交核对范围不含 impact-map.json。
- **独立审查结论优先**：Verify 前独立只读审查必须 PASS（无 CRITICAL/IMPORTANT），
  否则不得勾选任务或运行 `guard verify --apply`。
- **绝不** 改写历史、force push、手改权威 checkpoint、伪造 PASS 收据或
  声明 not-executed 于真实已执行的工作；runtime 恢复仅用于确证未执行/回报中断的 action。
- **失败路径**：Verify 报告 FAIL 时不执行 `guard verify --apply`；
  交付范围被并行提交污染时保持 Run 未闭合、如实登记，不绕过 Guard。
- **输入漂移**：Verify 取证后不再改任何输入文件；确需改动时 `reopen-verification` 重新取证。

## 真实检查命令

- Build / Verify 主判据：
  `mvn -pl bone-apps/synap-claim/synap-claim-app -am test`
  （JDK21 + Maven 3.8.6；基线 `Tests run: 1674, Failures: 0, Errors: 0, Skipped: 8`）
- 文档族一致性（accepted deviation 记录，不作为阻断）：
  `python3 doc/ai-product/synapClaim/tools/check_all.py`
  （预存 3 项与本 change 无关的红项：`_check_manifest` 的 2 份历史未登记文档、
  `_check_ruleset_alignment` 的 A9 过时声明、`_ai_native_readiness.py --self-test`）

## DoD

1. 全部 10 项任务完成，`comet state tasks` 显示 `completed=10`、`next=null`。
2. `guard build --apply` 与 `guard verify --apply` 全 PASS。
3. 独立只读最终集成审查 PASS。
4. 归档目录落地，精确暂存交付提交并推送 `origin` 双远程。
5. `comet state complete-delivery --commit <sha>` 成功，`state next` 显示 `continuation: done`。
