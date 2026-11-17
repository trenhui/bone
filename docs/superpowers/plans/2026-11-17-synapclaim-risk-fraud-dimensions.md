---
change: synapclaim-risk-fraud-dimensions
design-doc: openspec/changes/synapclaim-risk-fraud-dimensions/design.md
base-ref: 34a61d51ca2a1d0bcb48df37d804f7190279de70
baseline-head: 34a61d51c
baseline-gate: 59/59
plan-revision: 1
---

<!-- comet-task-authority: openspec/changes/synapclaim-risk-fraud-dimensions/tasks.md -->

# Build Plan — 智能风控真实维度（synapclaim-risk-fraud-dimensions）

> 唯一口径：本 plan 是本次 Build 的实施/验收口径；`tasks.md` 是任务完成状态真源，`design.md` 是已批准技术真源。两处历史表述若与本 plan 冲突，以本 plan 为准。
> 基线：`dev=34a61d51c`（批4已合入），`check.sh` 59/59 绿。本 change 的实现已于 2026-11-07 由 `320573ffa` 真实入库，本轮不重写代码，只做 Comet 正规收口与证据复核。

## 1. 范围

在既有「只读上下文 → 仓储富化 → QLExpress 求值」链路上，正式收口两条库内数据驱动风控维度：
- `priorClaimCount`：同租户同被保人历史案件数（排除本案），命中 `R-FRAUD-22`（risk=true, priority 27）。
- `recentClaims90d`：近 90 天历史案件数（排除本案），命中 `R-ANOM-03`（risk=false, priority 54）。
规则集版本 `claim-ruleset-v3` → `claim-ruleset-v4`。零 DDL、零新增配置、零外部数据源依赖；黑名单比对与诊疗-费用合理性继续登记为 gap，不伪造、不宣称 L3。

## 2. 任务映射（comet-task-ref → 证据）

| 任务 | task-ref | 证据 |
|---|---|---|
| 1.1 仓储查询 | `b0dce67b-f9b2-4298-8953-fcaba3f076a2` | `ClaimCaseRepository.findByInsuredIdHash`；复用 `findByCriteria`，显式带 tenantId |
| 2.1 上下文富化 | `4c96b4a3-156d-4539-93d6-c1e61b0da97e` | `QlExpressRuleEnginePortAdapter.enrichFromRepositories` + `enrichPriorClaimFacts`；排除本案、best-effort 降级 |
| 3.1 规则资产 | `f5c11c8e-2407-45ab-afc8-6b49a89f4c92` | `claim-ruleset.json` 中 `R-FRAUD-22` / `R-ANOM-03` |
| 3.2 版本 bump | `7587f58f-bfdd-4c39-b44c-4da6941261a0` | `claim-ruleset-v4` |
| 4.1 既有测试补桩 | `cc46c076-4684-4ca8-afbe-734b60ad2a62` | `QlExpressRuleEnginePortAdapterEnrichmentTest` 版本断言与 tenantId 桩 |
| 4.2 重复索赔命中 | `5592f819-8414-4d59-899e-a203b5380570` | `crossCaseDuplicateTriggersFraudRule` |
| 4.3 高频审计命中 | `5c7a44d6-3a08-4b96-81e3-02d8148d601e` | `frequencyAnomalyTriggersAuditRule` |
| 4.4 零误报 | `a0928085-1269-4b8a-9d14-f76b0e5c5e21` | `noPriorClaimsNoFalsePositive` |
| 4.5 排除自身 | `cbc8e7f9-48bb-42bf-9bf8-3509956059a2` | `excludesCurrentClaimFromHistory` |
| 4.6 变异验证 | `719573d7-22a6-42a4-b05d-085dc7c57a28` | M1/M2 实跑转红、恢复后 6/6 绿，见 `tasks.md` |
| 5.1 全量单测 | `ec26bfed-1119-4c72-899b-905504bca598` | 历史实测 1207 run；本轮随 `check.sh`/全量单测复核 |
| 5.2 格式门禁 | `58c4c0f9-e6cf-4ec7-aa0e-aaa32673564b` | `mvn -o spotless:apply/check` |
| 5.3 全量门禁 | `349f03fa-eb57-417e-ad50-86d3e629335d` | `./scripts/check.sh` exit 0 + 末行「✅ 本地自检通过！」 |
| 6.1 文档回写 | `d68ceddb-9b33-40bc-9867-279a697ead67` | `AI技术方案.md` v2.5 changelog 与 gap 登记 |
| 6.2 精确提交 | `7ace2525-a0d2-47c7-bcce-771fcff89db5` | `320573ffa`，10 paths，+503/-4，未卷入在途文件 |

## 3. 验收判据

1. 定向：`mvn -o -pl bone-apps/synap-claim/synap-claim-app -am test -Dtest=QlExpressRuleEnginePortAdapterEnrichmentTest` 6/6 绿。
2. 全量：`./scripts/check.sh` exit 0，末行绿；全量单测无 failures/errors。
3. 资产：`claim-ruleset.json` 含两条规则且 version=v4；`git diff` 对已落地实现零残留。
4. 诚实边界：不新增 DDL/配置；不把外部数据源维度写成已实现；不翻转 L3 前置判据。

<!-- comet-task-ref:b0dce67b-f9b2-4298-8953-fcaba3f076a2 -->
<!-- comet-task-ref:4c96b4a3-156d-4539-93d6-c1e61b0da97e -->
<!-- comet-task-ref:f5c11c8e-2407-45ab-afc8-6b49a89f4c92 -->
<!-- comet-task-ref:7587f58f-bfdd-4c39-b44c-4da6941261a0 -->
<!-- comet-task-ref:cc46c076-4684-4ca8-afbe-734b60ad2a62 -->
<!-- comet-task-ref:5592f819-8414-4d59-899e-a203b5380570 -->
<!-- comet-task-ref:5c7a44d6-3a08-4b96-81e3-02d8148d601e -->
<!-- comet-task-ref:a0928085-1269-4b8a-9d14-f76b0e5c5e21 -->
<!-- comet-task-ref:cbc8e7f9-48bb-42bf-9bf8-3509956059a2 -->
<!-- comet-task-ref:719573d7-22a6-42a4-b05d-085dc7c57a28 -->
<!-- comet-task-ref:ec26bfed-1119-4c72-899b-905504bca598 -->
<!-- comet-task-ref:58c4c0f9-e6cf-4ec7-aa0e-aaa32673564b -->
<!-- comet-task-ref:349f03fa-eb57-417e-ad50-86d3e629335d -->
<!-- comet-task-ref:d68ceddb-9b33-40bc-9867-279a697ead67 -->
<!-- comet-task-ref:7ace2525-a0d2-47c7-bcce-771fcff89db5 -->

## 4. 不做的事

- 不重写 `320573ffa` 已落地代码，不回改历史。
- 不接黑名单/图计算/诊疗合理性（外部数据源未就绪）。
- 不改动资金三值口径，不让规则引擎碰钱（F1）。
- 不手改受管 Comet state/checkpoint；收据死锁时保持诚实终态。
