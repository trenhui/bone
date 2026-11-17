# Proposal: synapclaim-risk-fraud-dimensions

## Why

`synapClaim` 智能风控引擎（`QlExpressRuleEnginePortAdapter` + `claim-ruleset.json`）当前虽已资产化 32 条规则，但**真实可执行的数据驱动维度**仍偏薄：绝大多数规则基于单案标量（金额、OCR 置信度、字段空值），缺少**跨案关联**与**历史行为**维度。目标图描述的「30+ 风控-合规-临床维度」中，可仅凭现有库内数据真实落地的两类——**跨案重复索赔**与**近 90 天高频报案**——尚未接入规则集。

补齐这两类维度有真实业务价值：同一被保人（`insuredIdHash`）短期内反复报案、或已有在办/历史赔案再次报案，是理赔欺诈与滥用的最高频信号之一，且**不依赖任何外部数据源**（黑名单比对、诊疗-费用合理性需外部数据，本轮如实登记为 gap，不伪造）。本 change 严格遵循已落地的「仓储富化 → 规则求值」架构（`enrichFromRepositories` 已就绪），新增两条真实维度规则，使风控引擎从「单案体检」进化为「带历史视角的体检」。

## What Changes

- **仓储查询新增**：`ClaimCaseRepository.findByInsuredIdHash(Long tenantId, String insuredIdHash)`——按被保人假名化 ID 取同租户全部赔案（任意状态），供重复/高频判定（排除本案自身）。
- **上下文富化新增**：`QlExpressRuleEnginePortAdapter.enrichFromRepositories` 在仓储就绪时，调用上述方法计算并注入两个变量 `priorClaimCount`（历史案件数，排除本案）、`recentClaims90d`（近 90 天新增案件数，排除本案）；任一异常 best-effort 降级为基础 facts（与既有富化契约一致）。
- **规则资产新增**：`claim-ruleset.json` 新增 `R-FRAUD-22`（`priorClaimCount >= 1` → `PRIOR_CLAIM_DUPLICATE`，risk=true，priority 27）与 `R-ANOM-03`（`recentClaims90d >= 3` → `FREQUENCY_ANOMALY`，risk=false，priority 54）；版本由 `claim-ruleset-v3` bump 至 `claim-ruleset-v4`（保证回放能回到当时那套规则）。
- **测试**：扩展 `QlExpressRuleEnginePortAdapterEnrichmentTest`，新增两条维度规则的命中/未命中断言（加载真实 ruleset，Mock 仓储返回构造的 prior claims）；既有两条测试补充 `getTenantId()` 桩以匹配新增富化调用。
- **质量门禁**：`mvn -pl bone-apps/synap-claim test` + `mvn spotless:apply` + `./scripts/check.sh` 全绿。

## Capabilities

### New Capabilities

（无新增 capability 级能力；本 change 是在既有 `claim/rule-engine` 能力上扩展两条可执行维度规则。）

### Modified Capabilities

- `claim/rule-engine`：富化变量表新增 `priorClaimCount` / `recentClaims90d`；规则集版本 v3→v4；新增跨案重复与高频报案两类真实维度。

## Impact

- **代码**：`ClaimCaseRepository.java`（新增 default 方法，无 SQL 模板）、`QlExpressRuleEnginePortAdapter.java`（富化新增两变量 + 私有 helper）、`claim-ruleset.json`（2 规则 + 版本号）、`QlExpressRuleEnginePortAdapterEnrichmentTest.java`（4 新测试 + 2 既有测试补桩/版本断言）。
- **无 DDL**：`findByInsuredIdHash` 走 `findByCriteria` default 方法，不新增 SQL 模板、不新增迁移；`insured_id_hash` 列已存在且为查询维度（存量已落库）。
- **无配置变更**：规则集位置与门控键不变；仅版本号 bump。
- **红线不变**：规则引擎仍只做「核责 + 反欺诈硬闸」这一路，金额仍由领域聚合确定性产出（F1：AI/规则不碰钱）；不引入自治 Agent。
- **诚实边界**：黑名单比对、诊疗-费用合理性等需外部数据源的维度本轮**不实装、不伪造**，在 `AI技术方案.md` 风控维度台账中登记为 gap（待外部数据源就绪后单独 change）。本 change 不翻转任何 AI 原生成熟度外部前置判据（`PROVIDER_CONFIGURED` / `PERCEPTION_MODEL_DRIVEN` / `RUNTIME_MODEL_COVERAGE`），仅强化规则引擎的真实维度覆盖。
