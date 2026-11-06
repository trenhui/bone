# Tasks: synapclaim-risk-fraud-dimensions

## 1. 仓储查询：跨案历史取数

- [x] 1.1 `ClaimCaseRepository` 新增 `default List<ClaimCase> findByInsuredIdHash(Long tenantId, String insuredIdHash)`，复用 `findByCriteria`（仿 `findByInvoice`，显式带 tenantId 条件，不依赖 SDK 自动租户过滤），返回同被保人全部赔案（任意状态）。验证：编译通过；既有 `@ArchTest` 仓储方法命名约束不冲突（非 insert/update/delete/persist/flush/merge/exists）。

## 2. 上下文富化：注入两个历史维度变量

- [x] 2.1 `QlExpressRuleEnginePortAdapter.enrichFromRepositories` 在 `hasClaim` 时调用 `findByInsuredIdHash`，计算 `priorClaimCount`（排除本案自身）与 `recentClaims90d`（排除本案自身且 `createdAt` 在 `now-90d` 之后），以 `double` 注入 facts；返回 null 时按空集合处理。新增私有 helper `enrichPriorClaimFacts`（含 `ChronoUnit` 导入）。验证：既有富化契约（best-effort 降级、异常不阻断主链路）不变。

## 3. 规则资产：新增两条真实维度规则

- [x] 3.1 `claim-ruleset.json` 新增 `R-FRAUD-22`（`category=FRAUD`, `priority=27`, `expression="priorClaimCount >= 1"`, `conclusion="PRIOR_CLAIM_DUPLICATE"`, `risk=true`）与 `R-ANOM-03`（`category=ANOMALY`, `priority=54`, `expression="recentClaims90d >= 3"`, `conclusion="FREQUENCY_ANOMALY"`, `risk=false`）。
- [x] 3.2 版本号 `claim-ruleset-v3` → `claim-ruleset-v4`（保证回放可区分）。

## 4. 测试与变异验证

- [x] 4.1 `QlExpressRuleEnginePortAdapterEnrichmentTest`：既有两条测试补 `when(claim.getTenantId()).thenReturn(1L)` 桩并修正版本断言至 `claim-ruleset-v4`。
- [x] 4.2 新增 `crossCaseDuplicateTriggersFraudRule`：Mock 仓储 `findByInsuredIdHash(1L,"hash-1")` 返回 1 条 prior claim（id≠ctx.claimId、createdAt=now-100d），断言 `R-FRAUD-22` 命中且 `riskHit=true`、`version=claim-ruleset-v4`、`R-ANOM-03` 不命中。
- [x] 4.3 新增 `frequencyAnomalyTriggersAuditRule`：返回 3 条近 90 天 prior claims，断言 `R-ANOM-03` 与 `R-FRAUD-22` 均命中、`R-ANOM-03` 仅审计留痕（`risk=false`）。
- [x] 4.4 新增 `noPriorClaimsNoFalsePositive`：返回空列表，断言 `R-FRAUD-22` / `R-ANOM-03` 均不命中（零误报）。
- [x] 4.5 新增 `excludesCurrentClaimFromHistory`：历史集合仅含本案自身(id=ctx.claimId)，断言两条历史维度均不命中（排除自身、杜绝 100% 误报）。
- [x] 4.6 变异验证（推理）：将 `priorClaimCount >= 1` 的 `>=` 改为 `<` → 4.2 中 priorClaimCount=1 的场景翻转（R-FRAUD-22 不再命中），证明断言可执行、规则非死代码（遵循 ML Test Score「每条规则写成可执行 assertion」）。

## 5. 质量门禁

- [x] 5.1 `mvn -o -pl bone-apps/synap-claim test` 全绿。**实测：1207 tests / 0 failures / 0 errors / 6 skipped**（6 skipped 为预存），本 change 的 6 个富化测试全部通过。
- [x] 5.2 `mvn -o -pl bone-apps/synap-claim spotless:apply` **BUILD SUCCESS**；`git diff --stat` 复核仅本 change 4 个源码/资源文件变动，未触碰并行 `bone-ai-document` 的在途改动（CJK 列宽与 import 顺序已按 google-java-format 逐行修正）。
- [x] 5.3 提交触发 pre-commit，`./scripts/check.sh` **59/59 全绿**，末行「✅ 本地自检通过！」；判据看 exit 0 + 末行，中间 ❌ 为负向探针。

## 6. 文档回写（诚实登记）

- [x] 6.1 `AI技术方案.md` changelog 新增 **v2.5** 行：登记 `R-FRAUD-22` / `R-ANOM-03` 已落地（库内数据驱动）、规则集 v3→v4、富化变量、6 项单测；并显式登记黑名单比对 / 诊疗-费用合理性为「需外部数据源，本轮不实装、不伪造」的 gap，声明不翻转 AI 原生成熟度外部前置判据、仍不得宣称 L3。
- [x] 6.2 提交 **320573ffa**（`feat(synap-claim): #24 ...`），`git commit --only` 圈定 10 个路径，`git show --stat` 复核 10 files / +503 −4 全为本 change 文件；并行会话暂存的 14 个 `bone-ai-document` OCR 文件与 2 个非本轮 doc 改动均未被卷入。共享树 `index.lock` 陈旧锁按纪律（3 次 RC=128 后确认无 git 进程）清除。
