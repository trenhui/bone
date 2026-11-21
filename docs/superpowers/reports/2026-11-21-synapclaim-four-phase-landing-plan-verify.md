# 验证报告 — synapclaim-four-phase-landing-plan

> 变更：`synapclaim-four-phase-landing-plan`（四阶段落地现状与解锁规划 · 纯文档治理 change）
> 工作树：`/Users/renhui.trh/wps/bone/.worktrees/synapclaim-four-phase-landing-plan`（分支 `comet/synapclaim-four-phase-landing-plan`，baseRef `a0f6db537`）
> 执行配置：`direct / tdd off / review off / directOverride=true`（沿用 Build 已批准配置）
> 唯一计划：`docs/superpowers/plans/2026-11-20-synapclaim-four-phase-landing-plan.md`
> 验证命令：`bash openspec/changes/synapclaim-four-phase-landing-plan/verify.sh`
> 报告生成：`2026-11-21`

## 0. 结论摘要

本 change 的**范围内验证通过**：12/12 任务已勾选，4 项 OpenSpec 产物均为 done，scoped `verify.sh` **5/5、exit 0**，零生产代码 / DDL / 门禁 / 活库改动。

全量 `./scripts/check.sh` 首轮实跑 **CHECK_EXIT=1**（红点见 §6.1）。经定位，两个 `[42/59]` readiness 失败并非判据回归或本 change 引入，而是新 worktree 缺少被 `.gitignore` 忽略的本地 E2E 产物 `outputs/claims-e2e-result.json` 所致——同一提交、同一用例在主工作树 dev（产物在位）为 OK。补齐主工作树真实跑出的产物后复跑，**CHECK_EXIT=0**、末行 `✅ 本地自检通过！`，任务 4.2 字面验收成立。过程 FAIL 与定位证据保留在 §6.1，未篡改真源、测试、授权书或生产代码。

本 change 范围内无 CRITICAL / 无 IMPORTANT；有 1 项已登记偏差（§6.2 节点计数口径）。是否进入 Archive 仍须单独授权。

## 1. 规格一致性核对（Completeness / Correctness / Coherence）

OpenSpec 适配器返回 `schemaName=spec-driven`、`actionContext.mode=repo-local`，proposal / specs / design / tasks 四项状态均为 `done`；`instructions apply` 返回 “All tasks are complete”。

| 维度 | 结论 |
|---|---|
| Completeness | tasks 12/12 勾选；1 个 delta spec、4 个 ADDED Requirements、8 个 Scenario 全有对应产物 |
| Correctness | 四态登记、两处偏离、四类外部前置、三条红线均能在 spec / design / 计划 / 对齐说明中逐项定位 |
| Coherence | 五项设计决策与 proposal、spec、tasks、计划一致；无第二真源、无资金语义改写 |

### 1.1 需求 → 证据映射（规划型 change，按文档契约核对）

| Requirement | 证据锚点 |
|---|---|
| 四阶段落地现状如实登记 | `spec.md:7-16`；`design.md:27-30,56-63`；对齐说明 §逐节点四态登记（阶段一至四表） |
| 代码可落地差距收口不改资金语义 | `spec.md:18-27`；`design.md:32-35,65-71`；计划 Task 2.1/2.2（含 `@Deprecated` 回退、金额对照、`LIMITED_CONDITION_DIMENSION_UNSUPPORTED`、白名单不变、不新增 DDL） |
| 外部前置解锁受控且 AI 不代签 | `spec.md:29-42`；`design.md:37-44`；计划 Task 3.1–3.4；对齐说明 §解锁路线（责任方 / 门禁 / AI 边界三列齐全） |
| 证据视图不翻绿门禁且单一真源 | `spec.md:44-49`；`design.md:46-49`；计划 Global Constraints（冲突取保守） |

八个 Scenario 均有可核对落点；其中 R2/R4 属于**后续实施契约**，本 change 交付的是规划与约束，不声称生产代码已切换或资金闸已打开。

## 2. 逐任务证据（对应 tasks.md 12 项 / comet-task-ref）

| 任务 | 证据 |
|---|---|
| 1.1 四态矩阵 | 对齐说明 §逐节点四态登记，阶段一至四逐节点表，附证据锚点与验证口径 |
| 1.2 两处偏离 | 对齐说明第 26/59 行：`t_claim_policy` 与 `E2_GRAPH_LOCAL` 均单列标注；design 决策 5 一致 |
| 2.1 引擎切换规划 | 计划 Task 2.1：先补金额对照测试 → Shadow → 保留 `@Deprecated` 回退与 fail-closed |
| 2.2 免赔四维规划 | 计划 Task 2.2：区域/医保/药品/诊疗显式拒绝 + 稳定原因码，医院/病种白名单不变，无 DDL |
| 3.1 盲标 | 计划 Task 3.1：≥300 条真实脱敏人工盲标、`BASELINE_WIRED`/`FROZEN_EVALSET_ACTIVE`、算法/评测责任方、SIMULATION 边界 |
| 3.2 目录准入 | 计划 Task 3.2：授权目录 + `CATALOG_POLICY` NONE→MANDATORY、`CATALOG_ADMISSION_ACTIVE`、产品+架构、全量停摆风险 |
| 3.3 Go-Live 13 册 | 计划 Task 3.3：补正文标 `current`、`GOLIVE_NOT_DRAFT`、合规/法务 L4、AI 禁代签 |
| 3.4 资金闸双签 | 计划 Task 3.4：`available=false`→`true` 的 L2 后果、指定 Review 人 + 产品签核人、AI 不单方变更 |
| 4.1 三条红线 | `spec.md` 三个 Scenario（合成数据不冒充真人盲标 / 合规不代签 / 资金闸双签）+ design 决策 3/4；单一真源在 Global Constraints |
| 4.2 零改动边界 | `git diff --name-only` 相对 baseRef 为空，工作树仅文档产物 + 补齐的 gitignored E2E 产物；scoped verify [5/5]、全量 check 补齐产物后 exit 0，见 §6.1 |
| 5.1 落地计划 | 计划文件路径在 artifactRefs 可解析；五项任务组按依赖排序，每项含前置、门禁和验收 |
| 5.2 对齐说明 | 对齐说明含四阶段总表、逐节点四态、四个判读点、四类解锁路线；口径为绿/琥珀/红 |

## 3. 实际执行的检查

| 检查 | 结果 |
|---|---|
| `bash openspec/changes/synapclaim-four-phase-landing-plan/verify.sh` | **VERIFY_EXIT=0**，5/5，末行 `✅ 四阶段落地规划 verify 通过` |
| `comet classic openspec -- status --change ... --json` | 4/4 artifacts `done`，`repo-local` |
| `comet classic openspec -- instructions apply --change ... --json` | “All tasks are complete” |
| `comet state tasks ... --json` | 12/12 completed，taskRevision `c56e7fd8...` |
| `./scripts/check.sh`（首轮，worktree 无 E2E 产物） | CHECK_EXIT=1，`[42/59]` 2 failures，详见 §6.1 |
| `./scripts/check.sh`（补齐真实 E2E 产物后复跑） | **CHECK_EXIT=0**，末行 `✅ 本地自检通过！`，59/59 |
| scoped check 输入哈希 | `13767dce...d95d06`（before == after，changedDuringExecution=[]） |

判据只看 exit code、末行 `✅ 本地自检通过！` 与步骤级结果；`scripts/check.sh` 中大量负向探针的 ❌（含种子过期/空清单探针）不作为步骤失败。`[42/59]` 首轮 2 failures 经定位为 worktree 缺 gitignored E2E 产物，补齐后消失。

## 4. Comet Build check 与恢复证据

Build check 由 Runtime action `synapclaim-four-phase-landing-plan:14` 真实执行，命令：

```text
bash openspec/changes/synapclaim-four-phase-landing-plan/verify.sh
```

- `exitCode=0`，scope `build`，tier `full`，checkEpoch `2`
- inputHash `716861b3493663f283ae82f5838cba2f123b73732abb702ea4fdf80fa3e6b286`
- receiptRef `.comet/runtime/classic-sdk-checks/3862b729.../ebb6ce19-....log`
- contentHash `2fa1f92d2aafe23a7b63132ee2e9b995491ea9b7d74fbfb68a30a8bf335b9bcc`（已用 `shasum -a 256` 复核）
- manifestHash `7c42eff4d52066cad6076b1559cd969deb435ff9e11ddf7cdc9b83623fc32cc0`

恢复检查发现 action 停在 running 但记录 `status=completed`、receipt/manifest 均存在且日志末尾为 `✅ 四阶段落地规划 verify 通过`；确认原执行器已停止后，通过 Runtime `record-outcome`（revision 28→29）与 `record-evidence`（revision 29→30）回填原结果，**未重跑、未声明 not-executed、未伪造收据**。

## 5. 独立审查与验证方法

- `review_mode=off` 为已批准 Build 配置；full direct 下不强制独立 reviewer，因此本轮无独立 reviewer 结论，按 SDK 方法登记跳过原因。
- `verification-before-completion` 方法已遵循：先跑 scoped verify、OpenSpec status/instructions、tasks 状态与全量 check，再写本报告；不以“应该通过”代替输出。
- 本 change 无生产代码 diff，故无 Java/前端单测、E2E 或变异验证需要重跑；既有工程证据门禁属于上一 change 的已验证资产。

## 6. 已登记偏差（诚实边界）

### 6.1 全量 `check.sh` 首轮 FAIL 的定位与消解（环境缺产物，非本 change 缺陷）

首轮实跑 `CHECK_EXIT=1`，步骤级红点（客观留痕）：

1. `[42/59] 门禁脚本自身单元测试`：`Ran 688 tests`，`FAILED (failures=2)`
   - `test_current_reality_does_not_claim_l3`：`AssertionError: 4 != 5`
   - `test_self_test_probes_all_flip`：`AssertionError: False is not true`
2. 种子过期/空清单：经核对均为种子校验器的**负向探针**（前后紧邻 `OK: 种子与活库一致`、`OK: 种子齐备`），非步骤失败。

定位证据链：

- 事实源是 `doc/ai-product/synapClaim/tools/_ai_native_readiness.py`，`RUNTIME_MODEL_COVERAGE` 判据读取 `outputs/claims-e2e-result.json` 的 `runtime` 段；无该段时判 `RUNTIME_ONLY`，导致 BLOCKED 计数 4 而期望 5、`self_test` 负向探针起点漂移。
- 该文件及两个测试、授权书 `ADOPTION-AUTHORIZATION.json` 均来自提交 `d14b8a47c`（2026-11-20，**早于**本 change 的 baseRef `a0f6db537`），本 change 未触碰。
- 产物 `outputs/` 被 `.gitignore:295` 忽略、从未入库、无 git 历史；新 worktree 天然没有。
- **对照实验**：同一提交、同一用例，在主工作树 dev（产物在位，16357 字节，`probeVerdict=REACHABLE`、`nodesScanned=148`）为 `OK`；纯内存注入 runtime 段后 BLOCKED 精确回到 5、`RUNTIME_ONLY` 归零。证明是环境缺产物，不是判据回归。

处置：从主工作树复制真实 E2E 产物到本 worktree 的 `outputs/`（真实跑出、非伪造；不入库、不改任何真源/测试/授权书/生产代码），复跑全量 `check.sh` 得 **CHECK_EXIT=0**、59/59、末行 `✅ 本地自检通过！`。任务 4.2 字面验收由此成立；首轮 FAIL 作为过程事实保留，不改写历史输出。

### 6.2 节点计数口径

任务 1.1 / 计划 Task 1.1 写“15 节点”，对齐说明标题也写“逐节点四态登记（15 节点）”，但逐节点表实际为 17 行（阶段一 6 + 阶段二 4 + 阶段三 3 + 阶段四 4，含 Go-Live 合规 13 册）；scoped verify [4/5] 使用 `>=15` 下限，因此未拦截。此为计数表述偏差，不增加能力宣称，也不改变四态结论；登记为 SUGGESTION，后续修订任务口径时一并统一，不在本轮改动已批准任务正文。

## 7. 未执行 / 未放宽项

- 未改生产代码、DDL、迁移、门禁脚本、活库数据；未新增错误码。
- 未把 `PARTIAL_DEFAULT_OFF` / `STUB_SAFETY_LOCK` / `DESIGN_ONLY` 表述为已实现或已上线。
- 未把视觉反诈 `available=false` 改为 `true`；未代签合规 13 册；未伪造盲标或冻结评测集。
- 4 项 readiness 外部前置仍按 BLOCKED 口径：`BASELINE_WIRED` / `FROZEN_EVALSET_ACTIVE` / `CATALOG_ADMISSION_ACTIVE` / `GOLIVE_NOT_DRAFT`；本 change 不提供翻绿输入。
- `comet-state.yaml`、`.comet/check-policy.json` 属运行态，不纳入提交。

## 8. 最终判定

本 change 范围内：12/12 任务、4/4 OpenSpec 产物、8/8 Scenario 有证据落点，scoped verify 5/5 exit 0，全量 `check.sh` 补齐真实 E2E 产物后 59/59 exit 0，零生产改动边界成立。**Verify 通过**；全量门禁首轮 FAIL 已定位为 worktree 环境缺 gitignored 产物并消解（过程留痕见 §6.1），节点计数口径偏差如实登记（§6.2）。是否归档、提交或推送由后续交付决定单独授权。
