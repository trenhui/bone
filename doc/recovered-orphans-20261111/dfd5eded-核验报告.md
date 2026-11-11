# 业界最佳实践补强提交 dfd5eded · 独立核验报告

> 复核人：verifier 子 Agent　复核日期：2026-10-16　复核对象：commit `dfd5eded`（21 文件，+1043 行）
> 核验方式：源码走读 + 文档真源契约比对 + 定向单测实证（gateway/rules/orchestration 三模块关键测试实跑，均 exit 0）
> 说明：本复核未改动任何文件，未做提交；未跑完整 maven 构建，仅对 4 个功能点相关单测定向运行。

## 0. 结论总览

| # | 功能点 | 名实相符 | 主要问题 | 定级 |
|---|---|---|---|---|
| 1 | gateway 熔断+重试（ResilientLlmTransport） | ✅ 相符 | 无 | — |
| 2 | gateway 计量事件（LlmUsageRecorded） | ✅ 相符 | 无 | — |
| 3 | rules 发布回归门禁（RuleRegressionGate） | ⚠️ 基本相符 | 负例样本 + 求值错误会误通过（契约不一致） | 中 |
| 4 | orchestration HITL 五态子域 | ✅ 相符 | 无 | — |

**整体结论：有条件通过。** 4 个功能点主体全部名实相符、测试实证通过、与总案/详设契约一致，无架构违规与过度设计。需修正 1 处健壮性契约不一致（问题 ①），另有 1 处测试缺口（问题 ②），均不影响当前发布路径的正确性，建议小修后合入。

---

## 1. 功能点 1：gateway 熔断+重试（ResilientLlmTransport）—— 相符

### 1.1 内层重试、外层熔断
`ResilientLlmTransport.post()` 中 `Retry.decorateSupplier(retry, () -> delegate.post(...))` 包在里层，`circuitBreaker.executeSupplier(retried)` 包在外层（`ResilientLlmTransport.java:76-77`）。与声明"内层重试外层熔断"完全一致。含义正确：熔断只统计重试耗尽后的最终失败，不因可重试瞬时抖动误开熔断。

### 1.2 重试谓词语义化
`isRetryable()`（`ResilientLlmTransport.java:81-88`）：
- `CallNotPermittedException` → 不重试（:81，被熔断拒绝交上层降级）；
- `RetriableHttpException` → 重试（:84，即 5xx / 网络瞬时）；
- 其余（含 4xx 的 `IllegalStateException`）→ 不重试。

4xx 由 `jdkTransport` 抛 `IllegalStateException`（`LlmGatewayAutoConfiguration.java` 内 lambda，非 `RetriableHttpException`），故 4xx 不重试。实证：单测 `does_not_retry_client_4xx` 断言只调用 1 次。✅

### 1.3 fail-closed 不直连模型（熔断 OPEN 后拒绝调用）
熔断 OPEN 时 `circuitBreaker.executeSupplier` 直接抛 `CallNotPermittedException` 而不执行 delegate，不触碰模型。实证：单测 `opens_circuit_after_failure_rate_threshold` 灌入失败至 OPEN 后，断言后续调用抛 `CallNotPermittedException` 且 `calls` 计数不再增加（即未再次触碰 delegate）。**fail-closed 成立。** 上层 `LlmGatewayService.invoke()` 对该异常不做吞掉/降级直连，异常正常传播（见功能点 2），符合总案 §4.1"绝不直连模型"。✅

### 1.4 装配：ObjectProvider 条件启用、无 registry 回落直连
`LlmGatewayAutoConfiguration.llmTransport()`（`LlmGatewayAutoConfiguration.java:64-71`）：以 `ObjectProvider<CircuitBreakerRegistry> registryProvider` 注入，`getIfAvailable()` 为 null 时回落直接 JDK 传输（POC 零依赖）。与声明一致。✅

### 1.5 依赖管理
`bone-parent/pom.xml` 显式托管 `resilience4j-circuitbreaker/retry/core 1.7.0` + `vavr 0.10.2`，注释明确为压回 spring-cloud BOM 2.2.0（2.x 换代 vavr、API 变更）。`resilience4j-circuitbreaker` 传递依赖 core + vavr，托管防版本分裂。依赖红线（SBOM/CVE）由既有门禁覆盖。✅ 实测编译产物存在、单测 4/4 通过。

---

## 2. 功能点 2：gateway 计量事件（LlmUsageRecorded）—— 相符

### 2.1 双事件分离发布
`LlmGatewayService.invoke()`（`LlmGatewayService.java:43-45`）在真实调用 `llmClient.chat(request)` 成功后依次发布：
- `LlmCallRecorded.of(...)`（证据链："发生了什么"）；
- `LlmUsageRecorded.of(...)`（计量："花了多少 token/成本"）。

两事件关注点分离清晰，符合任务声明与总案 §4.1 成本红线治理。实证：单测 `invoke_delegates_to_client_and_publishes_event_when_no_redline` 断言两种事件均发布。✅

### 2.2 short-circuit 时不发布
`LlmGatewayService.java:38` `request.shortCircuited()` 为真时抛 `BizException(400, ..., AI_GATEWAY_REDLINE_BLOCKED)`，未触碰 client、未发布任何事件。实证：单测 `invoke_blocks_and_never_calls_client_or_publishes_when_rule_short_circuited` 用 `verifyNoInteractions(client)` + `verifyNoInteractions(publisher)` 证实。✅

### 2.3 计量契约合理性
`LlmUsageRecorded` 含 `tenantId`（POC 单租户下 `tenantOf()` 返回 null，设计合理）、`model`、`usage`、`occurredAt`，并提供 `eventId()`（UUID）供计量幂等去重。与 LiteLLM/Portkey spend tracking 的按租户/模型聚合语义对齐。✅

---

## 3. 功能点 3：rules 发布回归门禁（RuleRegressionGate）—— 基本相符，1 处契约不一致

### 3.1 正/负例回归、任一不符拒绝
`RuleRegressionGate.evaluate()` 对每个样本 `ok = hit == c.expectHit()`（`RuleRegressionGate.java:47`），整体 `allMatch(CaseResult::passed)`。正例必须命中、负例必须不命中。实证：单测 `fails_when_positive_case_misses`、`fails_when_negative_case_hits`。✅

### 3.2 empty cases 直接过
`RuleRegressionGate.java:33-36`：`cases == null || cases.isEmpty()` 返回 `RegressionResult.pass(List.of())`。实证：单测 `empty_cases_pass_directly`。✅

### 3.3 失败逐条回显 label
`RuleService.assertRegression()`（`RuleService.java:98-112`）：失败时收集 `c.label() + "(期望 X, 实际 Y)"` 拼入 `IllegalArgumentException("rule regression failed: ...")`。实证：单测断言 message 含 label `小额不应命中`。✅

### 3.4 reject 后 RuleAsset 仍为 DRAFT（不污染版本链）
`RuleService.publishFirst/publishNext` 中 `assertRegression`（:62/:90）在 `rule.publishFirst/publishNext`（:63/:91）**之前**调用；回归失败抛异常时发布方法尚未执行，故 `RuleAsset.status` 保持 `DRAFT`、版本链未增。实证：单测 `publish_first_rejects_when_regression_case_fails` 断言 `rule.getStatus().name() == "DRAFT"`。✅

### 3.5 ⚠️ 问题 ①：负例样本 + 求值错误会误通过（契约不一致，中等）
`RuleRegressionGate.java:40-47`：
```java
if (ev.errorMessage() != null) {
  hit = false;                       // 求值/编译错误一律当作"未命中"
  detail = "EVAL_ERROR: " + ev.errorMessage();
} else {
  hit = ev.hit();
  ...
}
boolean ok = hit == c.expectHit();   // 负例(expectHit=false) + 错误 → ok=true
```
当样本为**负例**（`expectHit=false`）且表达式求值报错（`errorMessage != null`）时，`ok = false == false = true`，该样本被判通过——违背类 Javadoc（`RuleRegressionGate.java:18`）"样本命中但求值报错计为失败"的声明。**对正例样本成立，对负例样本不成立。**

影响评估：
- **发布路径已由 `precheck()` 部分缓解**（`RuleService.java:61` 编译预检拦截纯编译错误，如语法错误 `bad (`），因此纯编译错误不会走到回归门禁。
- 但 **运行时求值错误**（类型不匹配、方法不存在、除零等，`QlExpressRuleEngine` 在 `catch (Exception)` 时返回 `RuleEvaluation.error`）在 `precheck` 空上下文（`Map.of()`）下通常不触发，只在带值的样本上下文触发。此时若该样本为负例，回归误判通过，坏表达式可借负例样本混入版本链。
- 单测 `eval_error_counts_as_failure` 只用**正例**样本覆盖，未覆盖负例 + 错误场景，故该边界未暴露。

**建议**：将 errorMessage 作为整体失败短路处理，与 expectHit 无关——即 `ev.errorMessage() != null` 时直接 `passed=false`，或改为 `hit` 保持三态（命中/未命中/错误）并把错误样本强制判失败。

---

## 4. 功能点 4：orchestration HITL 五态子域 —— 相符

### 4.1 五态枚举与 §3.3 契约一致
`HitlStatus` 枚举（`HitlStatus.java`）：`PENDING / CONFIRMED / REJECTED / MODIFIED / ESCALATED`，与总案 §3.3（`pending/confirmed/rejected/modified/escalated`）及详设 §2.3 DDL 状态列（`PENDING/CONFIRMED/REJECTED/MODIFIED/ESCALATED`）三处一致。`terminal()` 定义终态集合。✅

### 4.2 ReasonType 受控回流
`ReasonType` 枚举：`RULE_MISS / CONTEXT_MISS / SUGGESTION_WRONG / POLICY_CONFLICT`，与总案 §3.3 及详设 §2.3 字典一致。`HitlItem.reject()`（:93）与 `modify()`（:105）在 `reasonType == null` 时抛 `IllegalArgumentException("... must carry reason_type")`；`confirm()` 可带（回流可选）。实证：单测 `reject_requires_reason_type`、`modify_updates_value_and_requires_reason`。✅

### 4.3 终态不可再裁决（escalate 后 confirm 抛 IllegalStateException）
`HitlItem.requirePending()`（:122-124）在 confirm/reject/modify 中调用，非 PENDING 抛 `IllegalStateException("item already resolved: ...")`。`escalate()`（:115-116）仅允许 PENDING 升级，否则抛 `IllegalStateException("only PENDING item can be escalated, current=...")`。因此 escalate → ESCALATED 后再 confirm 走 `requirePending()` 抛异常。实证：单测 `escalate_moves_to_escalated_and_rejects_further_actions`。✅

### 4.4 处置链 dispositionHistory 可回放
`HitlItem.record()` 记录每次裁决的 `action / reasonType / value / comment / actor / actedAt` 至 `dispositions`；`dispositionHistory()`（:149）返回不可变列表。实证：单测 `disposition_history_replays_chain` 断言 action/actor/reasonType。✅

### 4.5 持久化与装配
- `HitlItemRepository extends Repository<HitlItem, String>`（`HitlItemRepository.java`），由 bone-metadata-sdk 动态代理（HC-001 唯一方案，同 RuleAsset 模式）。`findPending` 用 `QueryBuilder` 查 PENDING 后内存过滤 `scene/riskLevel`，与 Known facts 一致。
- `OrchestrationAutoConfiguration.hitlService()` 在 `@ConditionalOnBean(HitlItemRepository.class)` 时启用，未启用持久化时自动跳过。✅

---

## 5. 发现的其它问题（非功能点阻断）

### 5.1 问题 ②：熔断拒绝路径缺计量/证据不发布的显式测试（测试缺口，轻微）
`LlmGatewayService.invoke()` 在 `llmClient.chat()` 抛 `CallNotPermittedException` 时异常正常传播、不发布事件（符合"真实调用后才发证据"），但 `LlmGatewayServiceTest` 未显式覆盖该场景（仅覆盖红线短路与正常发布）。建议补一条"熔断拒绝时不发布任何事件"的用例，消除隐含行为依赖。

### 5.2 观察：`LlmUsageRecorded.eventId()` 每次调用生成新 UUID
作为幂等去重键每次值均不同（`eventId()` 每次 `UUID.randomUUID()`），严格讲不满足"按事件幂等去重"（除非消费方按内容哈希）。当前一期库形态由订阅方自行聚合，非缺陷，可留待集中式网关演进时审视。

### 5.3 观察：HITL 处置链暂为 `@Transient` 进程内记录
`HitlItem.dispositions` 标注 `@Transient`（Javadoc 说明批二以 `@Cascade` 落 `hitl_disposition` 子表）。符合详设第一批验收口径（处置链回放=进程内满足），为明确的阶段化设计，非缺陷。

---

## 6. 业界最佳实践对齐情况

| 实践 | 本提交实现 | 对齐 |
|---|---|---|
| Provider 韧性（熔断防重试风暴 + 语义化重试） | `ResilientLlmTransport`（Resilience4j 编程式，内层重试外层熔断，5xx/网络重试、4xx 不重试） | ✅ LiteLLM / Portkey / Kong AI Gateway |
| 熔断 fail-closed（绝不直连模型） | 熔断 OPEN 抛 `CallNotPermittedException` 拒绝调用，上层不降级直连 | ✅ 总案 §4.1/§4.7 |
| Spend tracking（计量与证据分离关注点） | `LlmUsageRecorded` vs `LlmCallRecorded` 双事件分离发布 | ✅ LiteLLM/Portkey spend tracking |
| 规则发布即回归（正负例门禁） | `RuleRegressionGate` 发布前正/负例回归，任一不符拒绝 | ✅ OPA / Drools / 规则平台（详见 3.5 缺陷） |
| AI Agent 人工终审五态 + 受控回流字典 | HITL 五态子域 + `ReasonType` 枚举约束回流 | ✅ Anthropic Effective Agents / §3.3 契约 |
| DDD 分层门禁 | 每模块 `ArchitectureTest`（domain 不依赖外层 / application 不依赖 infra / app 服务不持领域规则）均在提交前已含 | ✅ |

依赖红线：新增 `resilience4j 1.7.0` + `vavr 0.10.2` 由 `bone-parent` 统一托管版本、防 spring-cloud BOM 2.2.0 分裂，符合许可/CVE 门禁流程（SBOM 复核既有机制）。

---

## 7. 结论

### 7.1 结论：**有条件通过**

4 个功能点主体全部名实相符、单测实证通过、与总案/详设契约一致，无架构违规、无过度设计、无并行会话文件污染（提交仅 21 个本任务文件）。

需要修正 1 处契约不一致（问题 ①：`RuleRegressionGate` 负例样本 + 求值错误误通过，`RuleRegressionGate.java:40-47`），建议补 1 条测试（问题 ②：熔断拒绝路径不发事件），均不阻断当前发布路径的正确性。

**建议**：问题 ① 修复后合入；问题 ② 作为后续补测项跟踪。

### 7.2 测试实证清单（本复核实跑，非依赖既有 BUILD 记录）

| 模块 | 测试类 | 结果 |
|---|---|---|
| gateway | `ResilientLlmTransportTest` | 4/4 通过（5xx 重试、4xx 不重试、熔断 OPEN 拒绝、成功不误开） |
| gateway | `LlmGatewayServiceTest` | 2/2 通过（真实调用双事件、红线短路不发事件） |
| orchestration | `HitlServiceTest` | 8/8 通过（五态流转、reason 必带、escalate 后禁裁决、处置链回放） |
| rules | `RuleRegressionGateTest` | 5/5 通过（正例/负例/编译错误/空样本） |
| rules | `RuleServiceTest` | 11/11 通过（发布、回归拒绝后保持 DRAFT） |

以上定向测试命令均以 `mvn -o -q -pl <module> test -Dtest=<Test>` 实跑，exit 0，未跑完整 maven 构建。

---

## 8. 复核范围与局限

- 已核对：4 个功能点源码逻辑、文档真源契约（总案 §3.3/§4.1/§4.7、详设 §2.3/§2.4/WBS 1.3/1.4）、bone-parent 依赖托管、装配条件、各模块 ArchUnit 门禁存在性、提交文件范围（无并行会话污染）。
- 未执行：完整 maven 构建（任务约束）、`bone-metadata-sdk` 动态代理的 `HitlItemRepository` 端到端落库行为（依赖运行时代理生成）、trace 模块对 `LlmCallRecorded` 的落库订阅（非本提交范围）。
- 已知风险提示：并行会话活跃，存在 `index.lock` 竞争可能；本复核仅读未写，未产生锁竞争。