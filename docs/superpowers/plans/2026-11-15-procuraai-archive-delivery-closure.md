---
change: procuraai-archive-delivery-closure
design-doc: openspec/changes/procuraai-archive-delivery-closure/design.md
base-ref: 46e59ea7c70da7c2f9fb6563e718b162702ff8d4
archived-with: 2026-11-15-procuraai-archive-delivery-closure
---

# 实施计划：交付闭环（procuraai-archive-delivery-closure）

<!-- comet-task-authority: openspec/changes/procuraai-archive-delivery-closure/tasks.md -->

## 目标与任务权威

在干净基线 `46e59ea7c` 上，用官方 Guard 全程验证地把一个新 Classic Run 推进到 `done`，
不触碰 `procuraai-ai-first-landing` 的旧 Run、归档目录与任何业务代码。
任务正文与完成状态**只以** `openspec/changes/procuraai-archive-delivery-closure/tasks.md` 为准，
本计划不复制 checkbox。

## 执行配置

| 字段 | 值 | 依据 |
| --- | --- | --- |
| `build_mode` | `direct` | 无业务代码变更，由当前会话按计划直接实施 |
| `tdd_mode` | `direct` | 不新增行为，无需 RED/GREEN；仍做真实回归检查与缺陷排查 |
| `review_mode` | `standard` | design.md D5：风险任务复核 + Verify 唯一一次独立最终集成审查 |
| `direct_override` | `true` | full direct 需要显式选择，已随 Build 配置提案批准 |
| `subagent_dispatch` | `null` | 不委派实现代理 |

环境说明：Superpowers `writing-plans` / `test-driven-development` /
`requesting-code-review` / `verification-before-completion` 技能在本环境未安装，
按 Comet 规则如实说明，采用等价的实证方法，不虚构加载记录。

## 任务范围与依赖

| 任务 | 依赖 | 实施内容 | 验收 |
| --- | --- | --- | --- |
| 1.1 | — | proposal.md 记录范围阻塞实证根因、非目标、5 条验收场景 | `state artifacts` 报 `proposal: done` |
| 1.2 | 1.1 | design.md 记录 D1–D5、风险、回滚，补齐 Comet frontmatter | `complete-design` 通过 |
| 1.3 | 1.2 | tasks.md 建立 9 项可验证任务，归档/交付动作留作 `Workflow follow-up` | `state tasks` 解析成功且 `needsIds: false` |
| 2.1 | 1.3 | 真实跑 masterdata 单测 | 当前基线 `Tests run: 251, Failures: 0, Errors: 0, Skipped: 0`、`BUILD SUCCESS`（非旧 change 的 250） |
| 2.2 | 1.3 | 真实跑 procuraAI 文档族门禁 | `10/10`、`146/146`、`69/69`、`GATE_RC=0` |
| 2.3 | 2.1, 2.2 | 独立只读最终集成审查 + 定稿唯一 Verify 报告的目标路径与内容 | 独立审查 PASS；目标为 `docs/superpowers/reports/` 单层 `.md`；报告写入与 `guard verify --apply` 归 Verify 阶段，验收原句见 `Workflow follow-up` |
| 2.4 | 2.3 | Build 阶段完整重建 `walkthrough.html` | `comet walkthrough ... --file <workspace 外的 json>` 成功且页面存在；取证后零改动 |
| 3.1 | 2.4 | 三方 SHA 与 ahead/behind 核对 | 一致且 `0/0` |
| 3.2 | 3.1 | 主 spec 无新增 capability、旧归档未被改动 | `git ls-tree -r --name-only HEAD -- openspec/specs/procurement` 为空；`git status --porcelain` 中本 change 外无已跟踪文件的修改或 staged 变更（其他 change 的未跟踪在途文件允许存在，但不得进入 staged 与归档提交） |

<!-- comet-task-ref:cb352cac-cf75-4a57-bef0-94ba67f4b6f1 -->
<!-- comet-task-ref:b7291f3d-c1d0-4dae-adda-a70afe19435d -->
<!-- comet-task-ref:88cb7e9a-fd2d-494e-852e-1ec52f279399 -->
<!-- comet-task-ref:cf2ba90a-f9d4-456b-a2b0-532b193c0766 -->
<!-- comet-task-ref:a9ee0e31-8888-4541-a6c3-94ea5d675348 -->
<!-- comet-task-ref:d88a834c-492e-4efe-8e66-207503c22ad1 -->
<!-- comet-task-ref:24fa9356-9eb8-46ed-98be-f4ac5ec17bfe -->
<!-- comet-task-ref:0d307b82-058d-4868-8b7a-2a4b9156ae1c -->
<!-- comet-task-ref:e17d107b-02c9-4691-8064-854675601b59 -->

## 约束与高风险处理

- **绝不** `git add -A`；归档提交只精确暂存本 change 的 active 删除、archive 新增、
  plan、唯一一份 Verify 报告、walkthrough 与 `.comet/` 收据。
  另一个 change 的未跟踪在途文件
  `openspec/changes/synapclaim-architecture-convergence/comet-state.yaml` 允许留在工作区，
  但 staged diff 中不得出现；它不进入 `diff-tree` 交付范围。
- **独立审查结论优先**：首轮独立只读审查为 FAIL（1 IMPORTANT + 5 WARNING），
  修完全部问题并复验 PASS 之前，不得勾选任务或运行 `guard verify --apply`。
- **绝不** 改写历史、force push、手改权威 checkpoint、伪造 PASS 收据或
  声明 `outcome-unknown`。
- **失败路径**：Verify 报告 FAIL 时不执行 `guard verify --apply`；
  交付范围被并行提交污染时保持 Run 未闭合、如实登记，不绕过 Guard。
- **输入漂移**：Verify 取证后不再改任何输入文件；确需改动时
  `comet state reopen-verification` 重新取证，避免 Archive `check inputs changed`。
- **旧 Run 边界**：`procuraai-ai-first-landing:72` 保持 `archive / running` 事实状态，
  其偏差说明继续由归档目录内 `comet-sdk-blocking-bug.md` 承载。

## 验收方式

1. `comet state complete-build` 通过 → Build Guard 真实执行
   `mvn -o -f bone-platform/bone-masterdata/pom.xml clean test`。
2. `comet guard procuraai-archive-delivery-closure verify --report docs/superpowers/reports/<report>.md --apply --json -- bash doc/ai-product/procuraAI/scripts/check-procura-ci.sh`
   返回 `exitCode=0`、`ALL CHECKS PASSED — phase=archive` 且 `inputBefore == inputAfter`。
3. Archive 后 `complete-delivery --commit <sha>` 通过，`comet state next --json`
   返回 `continuation: done`。
4. 本地 HEAD、`origin/dev`、`FETCH_HEAD` 三方一致；`openspec/specs/` 无新增 capability。
