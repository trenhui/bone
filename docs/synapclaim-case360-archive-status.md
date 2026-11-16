# synapclaim-case360-object-cohesion 归档与运行态收口说明

> 本文档是 Comet Classic `full` 工作流归档阶段的**状态补登记**，不改变 `docs/superpowers/reports/verification-report.md` §8 的验证结论。
> 登记时间：2026-11-13。

## 1. 交付事实（已完成）

| 项目 | 结论 | 依据 |
| --- | --- | --- |
| 任务清单 | 22/22 全部勾选，无剩余任务 | `openspec/changes/archive/2026-11-13-synapclaim-case360-object-cohesion/tasks.md` |
| 验证 | `verifyResult=pass`、`verifyFailures=0`、V1–V10 全量实跑通过 | `docs/superpowers/reports/verification-report.md` |
| 设计与计划 | `design.md`、实施计划、独立审查报告齐全 | 归档目录与 `docs/superpowers/` |
| 物理归档 | 目录已搬迁，原 active 目录不存在 | `openspec/changes/archive/2026-11-13-synapclaim-case360-object-cohesion/` |
| 入库 | 归档目录 7 个产物全部被 Git 跟踪 | `git ls-files`，提交 `4b6ca8c5d` |
| 交付方式 | 用户确认 `local`（本地提交，不 push / 不建 PR），已执行 | Run `full.archive.confirm` 决定记录 |

归档提交链：`7fe29e96a`（批准基线）→ `4b6ca8c5d`（主实现 + 归档目录 + 两份主 spec）→ `3ed0e7f33`（验证脚本 / 计划 / 字典生成器）→ `eeedfc282`（验证与独立审查报告），均在 `dev` 上。

## 2. 归档动作结果由 `outcome-unknown` 收口

Comet 的 `full.archive.execute`（Action `:35`）把 change 目录搬到归档目录后退出，未写执行收据，Run 一度停在 `outcome-unknown` / `archived=false`，`comet state delivery --verify` 因而返回 RC=70。

`comet archive <change> --recover --json` 生成了 `recoveryApproval`（proposalHash `795b323634403b447bfa504b92a3442205813f68a3ae876911a8e115cfa904ad`），列出两份待复核的新增主规格：

- `openspec/specs/claim/case360-embedded-workflow/spec.md`（2083 字符）：`claimId` 唯一真源、六类作业视图内聚、深链与前进后退恢复、既有作业 URL 不 404、非法 ID 与未知 `tab` 安全回退、受保护动作逐案人工终审、18 位雪花 ID 字符串不截断。
- `openspec/specs/claim/workbench-navigation-groups/spec.md`（1225 字符）：作业 / 查询 / 配置 / 治理四组常驻可见且默认展开、不改菜单名 / 顺序 / 图标 / 权限码 / hash 与 IAM 数据、未登记 hash 落「未分组」且保持可点、不引入第二套侧栏。

两份 `before=null`（新增），逐字比对磁盘文件完全一致，且随 `4b6ca8c5d` 提交入库、无未提交改动。按 CLI 约束，规格同步缺原始收据时必须与用户复核 delta 后确认，Agent 不得自行确认；用户确认「完整实现原契约」后执行：

```text
comet archive synapclaim-case360-object-cohesion \
  --recover --confirmed \
  --proposal-hash 795b323634403b447bfa504b92a3442205813f68a3ae876911a8e115cfa904ad --json
```

结果：

- `archived: true`，Run 推进到 revision 68；
- `diagnostics.healthy: true`、`executionReconciled: true`、`issues` 清空；
- 归档动作不再是 `outcome-unknown`。

按 `comet archive --help`，`--recover` 只补记已搬迁归档的结果，**不会重跑 OpenSpec Archive**。全程未改写 Git 历史、未重放归档、未声明 `not-executed`、未伪造执行收据。

## 3. 剩余缺口：`full.archive.deliver` 未结束

`comet state complete-delivery --commit <HEAD>` 与 SDK `comet state delivery --verify` 均返回 RC=70：

> Classic 交付提交 `5f2356100…` 的父提交 `669354a3877c…` 不等于 `7fe29e96a1d3…`；交付范围必须从已批准基线 `7fe29e96a1d3…` 连续开始。

原因是共享工作树上 `dev` 已被并行会话继续推进：批准基线 `7fe29e96a` 到当前 HEAD `1d0938ddf` 之间夹入了 merge 提交 `d52572325`（双父），其第二父链带入 `b04b2a8d6`、`5f2356100` 等属于其他 change 的提交。Runtime 要求 `baseCommit..HEAD` 全为连续单父提交、且只含本次已验证实现与归档产物。在不改写 Git 历史、不夹带其他 change 的前提下，当前分支无法满足该判据；`repair-delivery` 只处理生成物输出声明，不适用于提交链。

因此 Run 停在 `full.archive.deliver`（revision 68），`delivery.verification.status = needsVerification`。这是 Comet SDK 对隔离交付链的假设与共享树并行开发相冲突造成的**状态缺口，不是实现或验证的缺口**：任务、验证、归档目录与提交均已落地。

## 4. 处置口径

- 以归档目录与提交为最终事实；不回滚目录凑快照、不伪造交付收据、不手改 phase。
- 该守卫是 per-run 的，不影响后续新 change；指向已归档 change 的失效 selection 已清理。
- 若要在 Runtime 中彻底结束本 Run，需在一个从 `7fe29e96a` 起、只含本 change 提交且不含 merge 的连续单父 HEAD 上重新执行 `complete-delivery`。这属于改写 / 重建分支结构，需用户另行决定，本轮未执行。
