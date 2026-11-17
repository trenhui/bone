## Purpose

让核责规则引擎的「跨案关联」与「历史行为」维度成为**可执行、可回放、可证伪**的规则资产，
而非停留在单案标量（金额 / OCR 置信度 / 字段空值）体检。补齐的目标维度仅依赖库内既有
`insured_id_hash` 列，不引入外部数据源。

## ADDED Requirements

### Requirement: 跨案重复索赔识别
系统 SHALL 在核责上下文富化阶段，按同租户下同一被保人假名化 ID 取出全部赔案（任意状态），
排除当前案件自身后计算历史案件数 `priorClaimCount`，并在其 ≥1 时命中 `R-FRAUD-22`
（结论 `PRIOR_CLAIM_DUPLICATE`，计入反欺诈风险）。

#### Scenario: 同一被保人已有历史赔案
- **WHEN** 新案件核责，同租户同 `insuredIdHash` 下存在 1 条其它赔案
- **THEN** `priorClaimCount=1`、`R-FRAUD-22` 命中且 `riskHit=true`，规则版本记为 `claim-ruleset-v4`

#### Scenario: 新报案不得把自己算进历史
- **WHEN** 历史查询结果仅包含当前正在核责的案件本身
- **THEN** `priorClaimCount=0`，`R-FRAUD-22` 不命中（否则新报案 100% 误报）

### Requirement: 近 90 天高频报案审计留痕
系统 SHALL 统计排除本案自身后、`createdAt` 位于 `now-90d` 之后的历史案件数
`recentClaims90d`，并在其 ≥3 时命中 `R-ANOM-03`（结论 `FREQUENCY_ANOMALY`）。
该规则 MUST 仅作审计留痕（`risk=false`），不得作为硬拒依据，避免对慢病复诊等正常高频小额报案过度拒付。

#### Scenario: 近 90 天 3 案触发审计
- **WHEN** 同被保人近 90 天存在 3 条其它赔案
- **THEN** `R-ANOM-03` 与 `R-FRAUD-22` 均命中，但 `R-ANOM-03` 不改变硬闸结果

#### Scenario: 空历史零误报
- **WHEN** 同被保人无任何历史赔案
- **THEN** 两条历史维度规则均不命中，核责门禁正常通过

### Requirement: 规则集版本可回放
规则集新增或改动任一表达式 / 结论码时 MUST 同步 bump 版本号（`claim-ruleset-v3` → `claim-ruleset-v4`），
使 `t_claim_rule_hit.rule_version` 能区分不同规则集下的命中，回放不混淆。

#### Scenario: 版本随语义变化
- **WHEN** 规则集新增 `R-FRAUD-22` / `R-ANOM-03`
- **THEN** 执行结果携带 `version=claim-ruleset-v4`，旧命中记录仍为 v3

### Requirement: 富化降级不阻断主链路
仓储未装配或查询返回 null / 抛异常时，富化 MUST best-effort 降级为基础 facts
（`priorClaimCount=0` / `recentClaims90d=0`），不得阻断核责主流程。

#### Scenario: 仓储不可用
- **WHEN** `claimCaseRepository` 为 null 或 `findByInsuredIdHash` 抛异常
- **THEN** 两个历史变量按 0 注入，规则正常求值且不抛异常
