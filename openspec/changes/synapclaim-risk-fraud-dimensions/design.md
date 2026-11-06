# Design: synapclaim-risk-fraud-dimensions

## 架构决策

沿用已落地的「**只读上下文 → 仓储富化 → QLExpress 求值**」三段式架构，不新增架构概念。两条新维度属于「**历史关联维度**」，与既有 30+ 标量维度同一落点（`enrichFromRepositories`），复用 `claimCaseRepository` 的 `@Autowired(required=false)` 装配与 best-effort 降级契约——单元夹具以 `new` 创建适配器（仓储为 null）时自动降级为基础 facts，既有测试零改动。

### 数据流

```
ClaimContextView(claimId)
   └─ enrichFromRepositories
        ├─ claimCaseRepository.findById(claimId)            // 既有：取本案，拿 insuredIdHash / tenantId
        ├─ claimCaseRepository.findByInsuredIdHash(tenantId, insuredIdHash)  // 新增：同被保人全部赔案
        ├─ priorClaimCount  = count(排除本案自身)
        └─ recentClaims90d  = count(排除本案自身 且 createdAt 在 now-90d 之后)
   └─ facts 注入 priorClaimCount / recentClaims90d (double)
   └─ QLExpress 对 R-FRAUD-22 / R-ANOM-03 求值
```

### 关键判定

1. **排除本案自身**：重复/高频计数必须剔除当前正在核责的赔案（`p.getId() != null && !p.getId().equals(ctx.claimId())`），否则「新报案」会因把自己算进历史而必然命中 R-FRAUD-22，造成 100% 误报。这是本 change 最易踩的坑，单测必须覆盖「仅本案、无历史」的零命中场景（`excludesCurrentClaimFromHistory`）。
2. **窗口基准用 `Instant.now()`**：`recentClaims90d` 以「当前核责时刻」为右端点回看 90 天，语义是「近期报案密度」，符合高频异常的业务直觉；测试用 `now.minus(10d)` / `now.minus(100d)` 构造，90 天窗口远大于测试构造时差，无 flaky 风险。
3. **版本 bump v3→v4**：规则集新增规则改变语义，按 `QlExpressRuleEnginePortAdapter` 既有契约「改动任一表达式/结论码必须同步 bump」，保证 `t_claim_rule_hit.rule_version` 回放正确（旧命中 v3、新命中 v4，互不混淆）。
4. **降级一致性**：`findByInsuredIdHash` 返回 null（仓储异常/未装配）时按空集合处理，规则按「变量缺失→静默未命中」，绝不阻断主链路——与既有富化 catch 块契约一致。
5. **风险归属**：`R-FRAUD-22` 计入反欺诈风险（`risk=true`，命中即拉高 `riskHit`、收紧硬闸）；`R-ANOM-03` 仅作审计留痕（`risk=false`，高频报案提示人工但非硬拒），避免对正常高频小额报案（如慢病复诊）过度拒付。

### 不做的事（防范围蔓延 / 防造假绿）

- 不接黑名单比对、诊疗-费用合理性、社会关系网络等需外部数据源的维度（本轮登记 gap）。
- 不翻转 AI 原生成熟度外部前置判据（provider/perception/runtime coverage）——它们依赖真实 AI 网关，本地确定性适配器无法凑 L3。
- 不新增 DDL/迁移：`insured_id_hash` 列已存在，新查询走 Criteria default 方法。
- 不引入新配置项：规则集文件位置与门控键不变。
