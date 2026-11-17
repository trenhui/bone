# 运行期证据报告 — synapclaim-risk-fraud-dimensions

> 变更：`synapclaim-risk-fraud-dimensions`（两条库内数据驱动风控维度：跨案重复索赔 / 近 90 天高频报案）
> 工作树：`.worktrees/_dev-merge-tmp`（base `320573ffa`，Build 收口 `3c0d9a6ef`）
> 执行配置：`executing-plans / tdd / thorough / subagent_dispatch=null`（沿用批 1，用户裁定）
> 唯一口径：`docs/superpowers/plans/2026-11-17-synapclaim-risk-fraud-dimensions.md`
> 验证命令：`bash openspec/changes/synapclaim-risk-fraud-dimensions/verify.sh`

## 0. 结论摘要

15/15 任务全部落地并由 Comet CLI 勾选；实现提交 `320573ffa`（10 files / +503 −4）经 `git show --stat` 复核，未卷入并行会话的在途文件。本轮独立重跑证据如下：

- 定向富化单测：**TARGETED_RC=0，6/6**（`QlExpressRuleEnginePortAdapterEnrichmentTest`）。
- 当前 dev 全模块重跑：**1490 run, 0 failures, 0 errors, 7 skipped**（7 skipped 为预存；本批提交时历史基线为 1207/0/0/6）。
- 全量门禁重跑：**CHECK_RC=0**，末行 `✅ 本地自检通过！`，**[59/59]** 全绿（日志 `/tmp/risk-fraud-final-check.log`）。
- Comet Build check 收据：**COMET_BUILD_CHECK_RC=0**，`exitCode=0`、`cwd=.worktrees/_dev-merge-tmp`、`scope=build`；首次 hash `b124c37d…`，Verify 重试后按官方流程重跑的最新 hash `f6620d4e…`，两次均 `exitCode=0`。
- 变异验证 **M1 / M2** 已实跑并按预期转红、恢复后复绿；规则非死代码，断言可证伪。
- 边界：零 DDL、零新增配置、零外部数据源；黑名单比对 / 诊疗-费用合理性 / 社会关系网络如实登记 gap，**不实装、不伪造**，不翻转 L3 前置判据。

## 1. 逐项证据（对应 tasks.md 15 项 / comet-task-ref）

| 任务 | 证据 |
|---|---|
| 1.1 仓储查询 | `ClaimCaseRepository.findByInsuredIdHash(tenantId, insuredIdHash)` 复用 `findByCriteria`，显式带租户条件；编译与 ArchUnit 均通过。 |
| 2.1 上下文富化 | `QlExpressRuleEnginePortAdapter.enrichPriorClaimFacts` 注入 `priorClaimCount` / `recentClaims90d`（double），排除本案；仓储 null/异常沿 best-effort 降级。 |
| 3.1 规则资产 | `R-FRAUD-22`：`priorClaimCount >= 1` / `PRIOR_CLAIM_DUPLICATE` / `risk=true` / priority 27；`R-ANOM-03`：`recentClaims90d >= 3` / `FREQUENCY_ANOMALY` / `risk=false` / priority 54。 |
| 3.2 版本 bump | 本批提交 `320573ffa` 将 `claim-ruleset-v3` → `claim-ruleset-v4`，保证 `t_claim_rule_hit.rule_version` 回放可区分。当前 dev 已由后续批次推进至 `claim-ruleset-v5`（见 §7）。 |
| 4.1 既有测试补桩 | 既有富化用例补 `tenantId=1L` 桩，版本断言随本批提交更新至 v4；当前树随后续批次更新至 v5。 |
| 4.2 跨案重复命中 | `crossCaseDuplicateTriggersFraudRule`：1 条 100 天前 prior claim → 命中 `R-FRAUD-22`、`riskHit=true`，`R-ANOM-03` 不命中。 |
| 4.3 高频审计命中 | `frequencyAnomalyTriggersAuditRule`：3 条近 90 天 prior claims → 同时命中两条，`R-ANOM-03` 仅留痕（`risk=false`）。 |
| 4.4 零误报 | `noPriorClaimsNoFalsePositive`：空历史 → 两条均不命中。 |
| 4.5 排除自身 | `excludesCurrentClaimFromHistory`：历史集合仅本案 → 两条均不命中，杜绝新报案 100% 误报。 |
| 4.6 变异验证 | 见 §4；M1/M2 实跑红点落在预期用例，恢复后 6/6 绿。 |
| 5.1 模块单测 | 本批 1207/0/0/6；本轮当前 dev 重跑 1490/0/0/7，均绿。 |
| 5.2 格式 | `mvn -o -q -pl bone-apps/synap-claim/synap-claim-app spotless:apply` BUILD SUCCESS；当前 `check.sh` 的 spotless:check 仍绿。 |
| 5.3 全量门禁 | 本轮 **CHECK_RC=0**，[59/59]，末行 `✅ 本地自检通过！`。 |
| 6.1 文档回写 | `AI技术方案-版本历史.md` v2.5 行及 Rulebook §九 / 九之二登记两条规则、原因码、口径和 gap。 |
| 6.2 提交隔离 | `320573ffa` 仅含 10 个本 change 路径；并行 `bone-ai-document` 在途文件未被卷入。 |

## 2. 测试与门禁汇总

| 范围 | 结果 |
|---|---|
| `QlExpressRuleEnginePortAdapterEnrichmentTest` | **TARGETED_RC=0，tests=6 / failures=0 / errors=0 / skipped=0** |
| `mvn -o -pl bone-apps/synap-claim/synap-claim-app -am test` | **1490 run, 0 failures, 0 errors, 7 skipped** |
| ArchUnit `ArchitectureTest` | 28/28 绿 |
| `./scripts/check.sh` | **CHECK_RC=0**，[59/59]，末行 `✅ 本地自检通过！` |
| Comet Build check | **COMET_BUILD_CHECK_RC=0**，receipt `exitCode=0`、hash `b124c37d…` |

本轮测试与门禁均为重新执行，未复用旧日志；中间出现的负向探针 ❌ 不计为步骤级失败，判据只看 exit code 与末行总结果。

## 3. Comet Build check 回执

Runtime Run 历史中有两份成功的 `full.build.check` 输出（首次与 Verify 重试后的重跑），均为 Comet 正式收据，不是脚本自报：

| 轮次 | argv / cwd | scope / tier | 输入快照 | exitCode | contentHash |
|---|---|---|---|---|---|
| 首次 Build check | `./scripts/check.sh` / `.worktrees/_dev-merge-tmp` | `build` / `full` | `7a74824e705768ab1358d6e32b40d064bb9b17cef569f70a0aa4dc1205baec8d`（before==after） | 0 | `b124c37d265f4eb646e89b51f2166e18252b45604db5774a84b11dadb9752263` |
| 重试后重跑 | `./scripts/check.sh` / `.worktrees/_dev-merge-tmp` | `build` / `full` | `845c2657cfe42cedbee2bc98ac05cd15827ee66c1af421f96cf1048d5a47043e`（before==after） | 0 | `f6620d4ebea7dedf33836374c529a7b5f04aa56a044add0babbb390b7df30e69` |

Verify 脚本通过只读 Runtime inspect 断言历史中存在 `exitCode=0`、输入未变的 `full.build.check`，避免依赖会被阶段回退改写的 `comet-state.yaml` 投影。

## 4. 变异验证（判据判别力唯一证明）

两次变异均在规则资产上改阈值，用 `mvn -o -q -pl bone-apps/synap-claim test -Dtest=QlExpressRuleEnginePortAdapterEnrichmentTest` 实跑（当时的模块路径），用 `trap` 保证恢复：

| 变异 | 操作 | 判据用例 | 结果 |
|---|---|---|---|
| M1 | `R-FRAUD-22` 由 `>= 1` 改为 `>= 99` | 跨案重复 / 高频审计两例 | **恰 2 例转红**，均报「命中列表不含 R-FRAUD-22」 |
| M2 | `R-ANOM-03` 由 `>= 3` 改为 `>= 99` | 高频审计用例 | **恰 1 例转红**，报「不含 R-ANOM-03」 |

恢复后 `git diff --exit-code` 与 HEAD 逐字一致，复跑 6/6 全绿。M1/M2 的阈值方向同向收紧但仍分别命中不同规则，证明两条规则各自有独立判别力，而非一条退化表达式的影子。

## 5. 文档回写与契约一致性

- `09-records/AI技术方案-版本历史.md` v2.5：登记两条规则、`claim-ruleset-v3→v4`、两个富化变量、6 项单测，以及「需外部数据源的维度本轮不实装、不伪造」的 gap。
- `07-delivery/04-业务规则书（Rulebook）-门诊小额快赔MVP全集.md` §九 / §九之二：登记 `R-FRAUD-22` / `R-ANOM-03`，含优先级、结论码、risk 语义与排除自身的反例要求。
- 规则表达式、结论码、risk 与 Rulebook 对账一致；本 change 未新增错误码，故无四处齐配置义务。

## 6. 零 DDL / 零外部数据源边界

- 零 DDL：`insured_id_hash` 与 `created_at` 均已存在，只新增 Criteria default 查询，无迁移、无清单变更。
- 零新增配置：规则集位置与门控键不变，参数未外置到 `feature_toggle`。
- 零外部数据源：只取同租户同被保人的历史赔案事实；不接黑名单、诊疗-费用合理性、社会关系网络，不宣称 L3。
- 降级：仓储未装配/查询异常/返回 null 均按空集合，规则未命中但不阻断核责主链。

## 7. 当前树版本漂移（诚实说明）

本批提交时规则集版本为 `claim-ruleset-v4`，其后批次继续演进，当前 dev 与根目录均为 `claim-ruleset-v5`；富化测试的版本断言也同步为 v5。因此独立 Verify 脚本对当前树只断言：

- 版本号仍为 `claim-ruleset-vN` 且 N ≥ 4；
- 两条规则的表达式 / 结论 / risk / priority 未被后续变更改写；
- 历史提交 `320573ffa` 相对父提交确实完成 v3→v4。

这样既不把后续合法演进误判为本批回退，也不让本批的规则资产被静默篡改。

## 8. Verify 执行与诚实边界

- Verify 脚本：`openspec/changes/synapclaim-risk-fraud-dimensions/verify.sh`，六步覆盖定向测试、源码/规则静态断言、Build 回执与任务清单、历史版本与文档 gap、报告证据、交付物存在性。
- 报告与脚本均先落盘、再由 Guard 以 `bash <verify.sh>` 真实执行；Guard 仅绑定收据，不代替脚本结论。

### 8.1 本轮真实执行链（含一次可由证据证明的未执行重试）

1. 首次 Guard 登记报告后创建 `full.verify.check :22` 即以 `CLASSIC_COMMAND_FAILED` 退出；日志无任何脚本步骤输出，Runtime action `receipts=[]`。经只读 inspect 确认原尝试完全未执行，按官方 `mark-unknown`（rev47）→ `retry(not-executed)`（rev48）恢复。
2. 恢复后的 `comet check run verify` 真实派发脚本，但脚本第 3 步当时读取会被阶段回退改写的 `comet-state.yaml`，因找不到 `full.build.check` 而失败（日志止于 `AssertionError: comet-state.yaml 缺 full.build.check 输出`），流程回退到 Build。
3. 已修正脚本：第 3 步改读权威 Runtime Run 历史；同时把 check policy 的通配输入改成显式清单，排除会被 CLI 改写的 `comet-state.yaml`。修正后脚本预跑六步全绿。
4. 按 CLI 回退点重新 `submit-plan` → `complete-build` → Build check 成功（最新 hash `f6620d4e…`，`exitCode=0`），阶段回到 Verify。
5. 第二次 Guard（rev62）创建 `full.verify.check :30` 后虽以 `BLOCKED — Classic SDK has a running Action` 退出，但 Runtime 日志 `10888b1c-205d-40e9-930c-c9674efab0fd.log` 完整记录 `[1/6]` 到 `[6/6]`，末行 `ALL CHECKS PASSED — synapclaim-risk-fraud-dimensions verify`。这是真实验证成功的日志证据。

### 8.2 诚实终态（收据死锁）

- 动作 `:30` 的 Runtime record 仍停在 `status=started`，action `receipts=[]`、`outcome=null`；恢复接口只给出 `mark-unknown → retry(not-executed)` 或 cancel。
- 因原尝试**已真实执行且日志完整**，不能声明 `not-executed`；不使用 cancel，不手改 `comet-state.yaml` / checkpoint，不伪造成功收据。
- Comet Run 停在 `phase=verify`、`revision=62`、action `synapclaim-risk-fraud-dimensions:30` 的诚实终态。代码、报告、Verify 脚本与完整执行日志均以 Git / Runtime 存储为最终事实；本 change 未进入 Archive。
- 本 change 不涉及前后端新端点与新 E2E 路径，故无端点影响门禁/UI 联调义务；前批两端的 E2E 与 UI 巡检证据仍独立有效。
