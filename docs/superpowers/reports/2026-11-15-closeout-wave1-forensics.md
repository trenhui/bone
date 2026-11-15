# W1 收口取证报告 · 两个卡住的 Run（2026-11-15）

> 关联计划：`docs/superpowers/plans/2026-11-15-comet-all-changes-closeout-plan.md` §Wave 1
> 本批边界：**只做只读取证与终态确认，未执行任何不可逆 Run 写入**。原因见 §3。

## 1. `synapclaim-risk-fraud-dimensions` 取证矩阵

| 取证动作 | 实测结果 |
|---|---|
| `comet state check <c> open --recover --details --json` | `recovery.status=outcome-unknown`、`blocked=true`；`record={ref:null,status:missing}`；`evidence.receiptRef=null` / `manifestRef=null`；`diagnostics` 的 `stateReadable / executionReconciled / storageSettled / healthy` 全 `true`、`issues=[]` |
| `comet runtime dispatch --application classic-full --project-root <root> --request <inspect.json> --json`（`operation=inspect`，只读） | `run.status=running` / `revision=6` / `phase=open`；action `:1 full.open` = `succeeded`（1 份收据）；**action `:4 full.open.revalidate` = `running` / `attempt=1` / `command=null` / `receipts=[]` / `reconciliations=[]` / `outcome=null` / `claim=comet-classic-open-revalidate`** |
| `.comet/runtime/classic-sdk-checks/` 收据目录 | 目标 hash 目录不存在，整个 `classic-sdk-checks` 为空；无 `.write.lock` 残留 |
| 归档目录 | 不存在（未归档） |
| 实现事实（对照） | `tasks.md` 15/15；实现提交 `320573ffa` 在 HEAD 祖先链；1207 tests / `check.sh` 59-59 / 双向变异验证记录齐全 |

## 2. `procuraai-ai-first-landing` 终态确认

- 远端已归档 `openspec/changes/archive/2026-11-15-procuraai-ai-first-landing/`，含输入收据、清单收据与 `comet-sdk-blocking-bug.md` 阻塞登记。
- 并行会话提交 `c395e4b60` 另建 `procuraai-archive-delivery-closure`，走官方 Guard 全程验证闭环（Verify 报告 PASS、独立只读最终集成复审 PASS），把旧 Run `full.archive.deliver` 未完成的偏差专门登记为遗留。
- **结论**：业务与文档交付 100% 完成，保持终态。不重跑归档、不用 `--recover --confirmed` 覆盖既有事实、不手工改 `comet-state.yaml`。

## 3. 为什么本批停在取证边界

项目记忆 `sdk-run-outcome-unknown-recovery-forensics` 给出的判据是：

- `diagnostics` 全 `true` 且 `issues=[]` 只能排除 `WORKFLOW_CHANGED` 定义哈希冲突，说明**恢复写入在技术上可行**；
- `command=null + receipts=[] + record.status=missing + 空 evidence 目录 + 无进程 + 无锁` 属于**静态核对证据**，不等于历史 reconciliation 里的正向失败日志（如 `comet check run` 返回 `exitCode 70` 且未生成 `logRef`）；
- **声明 `not-executed` 是不可逆 Run 写入兼事实判断，须由用户明确承担，不得由 Agent 推断代替。**

`--recover` 自身输出的 instructions 也是同一口径：「只有已核实原尝试完全未执行时，才可先 mark-unknown … 已经执行或仍不明时不得声明 not-executed」。

因此本批**不执行** `mark-unknown`、`retry(not-executed)`、`comet check run` 重跑，也不伪造收据或手改 Run 状态。实现已交付、Run 停在 `open / outcome-unknown` 的事实如实保留。

## 4. 需要你明确承担的决定（W1 剩余）

1. **承担事实判断**：明确确认 action `:4 full.open.revalidate` 原尝试「完全未执行」，授权 `mark-unknown` → `retry(not-executed)` → `comet check run <c> open --json -- <program>` 真实重跑，把 Run 推进到 Verify / Archive；`evidence.summary` 会如实写明证据来源，不编造成功收据。
2. **或接受终态**：承认「实现已交付、Run 未 clean close」，W1 关闭，直接进 W2（multi-insurer Pilot）。

两条都合规；区别只在于不可逆 Run 写入的事实判断由谁承担。

## 5. 本批 Git 交付

| 项 | 结果 |
|---|---|
| 提交 | `8d4c3e6c6` — 5 文件 / +485 行（前端契约修复、E2E 正负向钉子、收口总规划、Wave0 执行报告） |
| pre-commit | `./scripts/check.sh` 全量通过，末行 `✅ 本地自检通过！`，RC=0 |
| pre-push | Gitee / GitHub 两侧钩子均通过（0 Java 变更、造数资产合规、无受影响 e2e 用例） |
| 双远端 | Gitee = GitHub = `8d4c3e6c6`；`HEAD = origin/dev`，`ahead/behind = 0/0`，工作区干净 |

本批未产生新的业务代码改动；上面的取证结论与待决项即 W1 的交付内容。
