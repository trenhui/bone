# 验证报告：ai-native-platform-plan-review（reverify 版）

| 项 | 值 |
|---|---|
| change | `ai-native-platform-plan-review` |
| 流程 | Classic / tweak / SDK Run |
| 配置 | `build_mode: direct`、`tdd_mode: direct`、`review_mode: off`、`verify_mode: light`、产物语言 `zh-CN` |
| 工作区 | `.worktrees/ai-native-platform-plan-review`，分支 `comet/ai-native-platform-plan-review`，base `11f7e17af` |
| 范围 | 复核并优化《Bone AI 原生工程基础设施改造方案》；纯文档层，无 spec 级行为变更（`skip_specs: true`） |
| 任务完成 | **8 / 8**（`tasks.md` 全勾选，apply `state: all_done`） |
| 首轮 Verify | 2026-11-10 20:5x，检查命令真实 `exit=0 / tier=full`；独立审查 Approve（0 Critical） |
| **reverify 原因** | 首轮 Verify 后做了「归档前收口」（方案文档版本自洽、11.5 收口记录等），`decide-archive --choice local` 返回 `Classic Archive check inputs changed`，Runtime 要求 `--choice reverify`。属正确的输入快照防护，已按提示重跑 |

## 一、实际改动

| 文件 | 改动 |
|---|---|
| `doc/architecture/Bone-AI原生工程基础设施改造方案.md` | v2（300 行）→ **v3（380 行）**：新增第十一章「业界实践对照复核与优化」（11.1 判定表 / 11.2 五项落点 / 11.3 验收命令清单 / 11.4 边界声明 / **11.5 归档前收口记录**）；T2/T4/T6/T7 各 1 行 v3 指针；H1 / 状态行 / 入库事实行由 v2 bump 至 v3；附录 v3 记录。**1–10 章编号与其余正文未改** |
| `doc/architecture/README.md` | 登记说明 v2 → v3（1 行） |
| change 产物 | `proposal.md` / `design.md` / `tasks.md` / `.openspec.yaml`（`skip_specs: true`）/ `comet-state.yaml` |

未改动（本 change 范围内）：任何工程代码、`scripts/check-*.py`、`ci.yml`、`AGENTS.md`、`doc/agents/06`、`.gitignore`、`.claude/settings.json`、PR 模板、`gate-state.json`、`Bone-DDD-最终实践方案.md`。

## 二、检查项与真实结果（reverify 实跑，非 mock）

| # | 检查 | 命令 | 实测 |
|---|---|---|---|
| 1 | 文档符号漂移 | `python3 scripts/check-doc-code-symbols.py` | **RC=0**（仓库 Java 类 3692 个，无未登记漂移） |
| 2 | DDD 文档防漂移 | `python3 scripts/check-ddd-doc-drift.py` | **RC=0**（白名单 125 个符号） |
| 3 | 门禁状态检查 | `python3 scripts/check-ddd-gate-state.py` | **RC=0**（10 项全绿，Known-missing 2 条，提示 0 条） |
| 4 | 规格校验 | `comet classic openspec -- validate ai-native-platform-plan-review --json` | **RC=0 / `valid: true`**（`skip_specs` 生效，零 delta 被接受） |
| 5 | 主树 `generate --check` | `python3 scripts/check-ddd-gate-state.py generate --check` | **主树 RC=0**（5 张表，无漂移）；**worktree RC=1**（独立基线未同步真源追平，见第四节） |
| 6 | 主树另外三项门禁 | `check-ddd-gate-state.py` / `check-doc-code-symbols.py` / `check-ddd-doc-drift.py` | 均 **RC=0** |

首轮 Build 完整验收命令经 Runtime 收据绑定（`contentHash=00962889407d…`，`inputBefore==inputAfter`，`changedDuringExecution=[]`）；首轮 Verify check 由 `comet check run … verify --json -- <program>` 真实执行，`exit=0 / tier=full`。

**恢复过程（如实记录）**：Build / Verify / Archive 的 `--apply` 多次遇到「Action 已领取、回执未提交」。按恢复协议分别用 `record-outcome` + `record-evidence`（收据 completed）、`mark-unknown` + `retry` + `check run`（收据 started/missing）处理；全程**未重跑已完成检查、未标记未执行、未伪造成功收据**。

## 三、独立审查结论

- 配置 `review_mode: off`，按配置本可跳过强制 reviewer；实际两轮均派出全新只读子 Agent。
- **首轮**：Approve，0 Critical，2 条非阻断 Suggestion（版本标识自洽、11.2② 边界措辞）。
- **reverify 轮**：Approve，0 Critical，3 条非阻断 Suggestion：
  1. 「8 份历史验证报告」计数偏差 → 已改为 **7 份**（另 1 个未跟踪文件是 `doc/architecture/reports/README.md`）；
  2. 方案文档 v3 收口尚未进 index → 已 `git add`，核验 `git show :<path>` 的 H1=v3 且含 11.5；
  3. 11.3 的 RC=1 与 11.5④ 转绿需显式说明 → 已补「两树结果差异说明」。
- 复核同时确认：`check.sh:809/839` 确有 `[52/59]` / `[57/59]`、`check-domain-model-layout.py` 确有 C5/C6 六条判定、`application-constructs-baseline.json` 的 `violations` 为 1 条、DDD 文档 17/18 行与追平后的 JSON 逐字一致、`.gitignore` 例外精确（`.workbuddy/reports/` 与 `target/**/reports/` 仍被忽略）、`gate-state.json` 无第二判据消费方。

## 四、可接受偏差与处置（如实登记）

| # | 项 | 处置 |
|---|---|---|
| 1 | `generate --check` 在 worktree RC=1 | **已定位为 `gate-state.json` 真源落后**（JSON 写「存量 96 条 / 四条判定 C1–C4」，实现与文档已是「1 条 / 六条 C1–C6」+ pre-commit 接线）。**机械 `generate` 会用旧真源倒灌、抹掉 C5/C6 与接线**，故未采用；改为在主工作树从文档提取精确列值追平 JSON（3 处文本），主树 `generate --check` RC=0。worktree 独立基线未同步该修复，故本表第 5 行仍记 RC=1，两者是基线差异而非矛盾 |
| 2 | 版本标识自洽（首轮 Suggestion，High） | **已收口**：H1 / 状态行 / 入库事实行 bump 至 v3；1–10 章编号不变 |
| 3 | 11.2② 边界措辞（首轮 Suggestion，Low） | **已收口**：明确「仅设计意图、不新增勾选项、不改 PR 模板、落地需 L3」，与 11.2③ 区分 |
| 4 | `.gitignore:242 reports/` 误伤 | **已修（主树，不属本 change）**：加两条精确例外，两个人工文档目录不再被忽略，运行时产物规则不变 |
| 5 | 例外生效后 7 份历史验证报告 + 1 个 README 仍为未跟踪 | **未批量入库**：属他人历史产物，是否补登需单独裁定，见方案 11.5③ |

无 CRITICAL / IMPORTANT 未解决项。第 4/5 项发生在主工作树，**不属本 change 提交范围**。

## 五、结论

**Verify 通过（light，reverify）**：8/8 任务完成，检查 1/2/3/4/6 全绿，检查 5 的双树差异已查明并登记；两轮独立审查均 Approve，首轮 2 条 + 本轮 3 条建议全部收口。
验证通过**不代表**归档或交付授权；归档与交付方式由用户在 Archive 阶段选择（本次选 `local`：仅本地提交、不 push）。

> **交付落地说明（2026-11-10 收口）**：Comet Runtime 归档的 `tweak.archive.preflight` 因输入快照敏感反复要求 reverify（Runtime 自身写 `comet-state.yaml` 会再次改变输入），按用户已选 `local` 授权改为本地提交。首次在独立 worktree 提交时，pre-commit 的 `test_ai_native_readiness_evidence_gates.py` 因该工作区缺少运行时产物 `outputs/claims-e2e-result.json`（不入 git、worktree 隔离）而判红（`RUNTIME_MODEL_COVERAGE=RUNTIME_ONLY`，BLOCKED 6≠7），与本 change 的文档改动无关；主树 dev 有该真实产物、7 个 BLOCKED，故最终成果在主树 dev 本地提交，内容一致。

> **主树提交补充**：首次在主树提交时，`[57/59] check-domain-model-layout.py` 因 `rglob` 扫到 `.worktrees/ai-native-platform-plan-review/` 副本（脚本不尊重 `.gitignore`）而 C4 判红，与文档改动无关。处置：成果同步主树后移除该 worktree、`.gitignore` 加 `.worktrees/` 防御规则，单独复验该判据 RC=0；熔断计数按项目规程在红点修复后清除。详见方案 11.5⑤。
