# Verify 报告 — procuraAI 人类监督有效性护栏（内部模拟 POC）

- change: `procuraai-oversight-effectiveness`
- 日期: 2026-11-25
- 执行配置: `build_mode=autonomous` / `tdd_mode=tdd` / `review_mode=standard`
- 工作区: `dev`，isolation=current
- 结论: **PASS**（结构、三态判词、fail-closed 不变量、门禁不退化、独立审查 0 CRITICAL/0 IMPORTANT）；**不代表模型效果或监督充分性达标**

## 1. 目标与边界

人工终决台账已有 `reviewDurationMs`/`reason`/`overrideAccepted`/`evaluationFeedbackRaised` 字段，但没有任何护栏消费它们。本 change 用领域纯函数三态判词识别三类监督失真：橡皮图章复核、无理由推翻、override 未回流评测；配套只读治理端点与 fail-closed 约束。依据：金监总局《关于银行业保险业人工智能安全开发应用的指导意见》§22 人工最终决策、§21 标识告知留存，以及 Gartner 2026 关于 approval fatigue 使审批退化为“虚假安全”的判断。

**验证的是工程结构与 fail-closed 不变量，不是 AI 模型效果。**

**非目标（未做，也不声称）**：接真实模型、放宽自治档、改人工终决权、改动 DDL/表/列、改 CI 三入口判据集合、改 evalset 状态、设定 owner 通过线、执行生产库操作、声明模型效果达标。

## 2. 交付物（file:line 证据）

| 类别 | 文件 | 要点 |
|---|---|---|
| 领域枚举 | `.../domain/model/capability/OversightVerdict.java:4` | 三态 `UNDETERMINED`/`NO_ACTIONABLE_SIGNAL`/`REVIEW_REQUIRED`；`isActionable()` 仅触阈为真（:10） |
| 领域阈值 | `.../domain/service/OversightThresholds.java:8` | 缺省未配置；负耗时下限/非正样本量/非法比率拒绝（:13-24）；`unconfigured()`（:28） |
| 领域结果 | `.../domain/model/capability/OversightSignal.java:12` | 三类真实计数 + 原因码（:22-27）；`claimsModelHealthy()` 恒 false（:30） |
| 领域策略 | `.../domain/service/OversightSignalPolicy.java:11` | 纯函数裁决：非法输入拒绝（:26）→未配置（:37）→样本不足（:45）→无理由推翻（:69）→未回流（:80）→比率触阈（:92）→无信号（:102） |
| 台账只读扩展 | `.../domain/gateway/HumanDecisionLedger.java:22` | `findByCapability(CapabilityCode)`；SDK adapter 复用 `query()`，退役内存实现同步签名；无 DDL/新端口 |
| 应用投影 | `.../application/OversightMetricsView.java:7` | 只读计数与原因码，`modelHealthClaim=NONE`（:19），无 `reason`/结论原文 |
| 应用服务 | `.../application/AiGovernanceApplicationService.java:185` | 未登记能力拒绝；真实计数；未配置时 `fastReviewCount=0`；只读无副作用（不写 trace/ledger/能力状态） |
| 配置属性 | `.../application/AiGovernanceProperties.java:26,29,32` | `oversightReviewDurationFloorMs=null`、`oversightMinSampleSize=20`、`oversightFastReviewRatio=null` |
| HTTP 端点 | `.../adapter/web/controller/AiGovernanceController.java:108-119` | `GET /capabilities/{code}/oversight`，`@PreAuthorize` read 权限；方法只接受 `String code`，阈值只取服务端配置 |
| 测试 | `OversightSignalTest`(9) / `OversightMetricsApplicationTest`(9) / `AiGovernanceControllerContractTest`(+2) / `AiGovernanceToggleTest`(+1) | 领域裁决、真实计数与诚实边界、HTTP 契约与签名、配置缺省 |

## 3. 内部模拟 POC 场景（真实终决记录驱动）

以应用服务 + 台账端口构造真实 `HumanFinalDecision`，阈值 `floor=1000ms / min=3 / ratio=0.3`：

| 场景 | 输入 | 判词 | 关键断言 |
|---|---|---|---|
| 三类风险并存 | 10 条：3 条 50ms、3 条无理由推翻、3 条历史未回流、1 条有效复核 | REVIEW_REQUIRED | 计数 3/3/3；原因码 `OVERSIGHT_UNREASONED_OVERRIDE`；`requiresOwnerReview=true` |
| 仅橡皮图章触阈 | 10 条中 3 条 50ms | REVIEW_REQUIRED | 原因码 `OVERSIGHT_FAST_REVIEW_RATIO` |
| 阈值未配置 | 1 条 50ms，`unconfigured()` | UNDETERMINED | 原因码 `OVERSIGHT_THRESHOLD_NOT_CONFIGURED`；`fastReviewCount=0`（I1 负向） |
| 样本不足 | 1 条 50ms | UNDETERMINED | `OVERSIGHT_INSUFFICIENT_SAMPLE` |
| 无风险 | 10 条有效复核 3000ms | NO_ACTIONABLE_SIGNAL | `modelHealthClaim=NONE`，不背书 |
| API 新建 override | 1 条有理由推翻（写入路径自动置回流真） | 未触阈 | `feedbackNotRaisedCount=0`（I2 诚实边界） |
| 未登记能力 | `M2` 未注册 | 拒绝 | `CapabilityNotFoundException` |

证据：`OversightMetricsApplicationTest` 9 例 + `OversightSignalTest` 9 例全绿；计数来自台账真实记录，非 mock 断言。

## 4. fail-closed 负向探针

| 探针 | 期望 | 结果 |
|---|---|---|
| 负耗时下限 / 非正样本量 / NaN、越界比率 | `IllegalArgumentException` | PASS（`OversightSignalTest.非法阈值与非法计数被拒绝`） |
| 推翻数 > 样本 / 负数 | 拒绝，不产生判词 | PASS（领域策略 :26） |
| 零样本 | UNDETERMINED | PASS |
| 风险优先于比率 | 无理由推翻/未回流优先于橡皮图章 | PASS（:53 与 POC 场景） |
| 比率 `==` 阈值 | 触阈 | PASS（:61） |
| 任何判词 `claimsModelHealthy()` | 恒 false | PASS（领域测试与投影断言） |
| HTTP `modelHealthClaim` | 恒 `NONE` | PASS |
| 客户端传阈值 | 方法签名仅 `String`，无 `@RequestParam` | PASS（`AiGovernanceControllerContractTest:66-70`） |
| 多次读同一能力 | trace/ledger 条目数不变 | PASS（`读操作无副作用`） |
| 理由原文回传 | 投影 `toString()` 不含敏感串 | PASS（`投影不回传理由原文`） |
| 配置缺省 | 三个 owner 通过线默认未配置 | PASS（`AiGovernanceToggleTest`） |

## 5. 独立审查与修复

独立 reviewer 初评：**0 CRITICAL / 2 IMPORTANT**。

- **I1（已修复）**：阈值未配置时 `floor=Long.MAX_VALUE` 会把全部样本算作 `fastReviewCount`，在 UNDETERMINED 判词下仍外泄“100% 橡皮图章”的误导计数。修复为未配置时计数置 0（`AiGovernanceApplicationService.java:192-198`），并加负向断言（`OversightMetricsApplicationTest.java:101`）。
- **I2（按方案 a 处理）**：`recordHumanDecision` 把新 override 自动置 `evaluationFeedbackRaised=true`（上一轮已交付的人工终决契约），因此“未回流评测”对新 API 数据不可达。未擅自修改既有写入语义；在 spec/design 明示该检查仅覆盖历史遗留/外部写入记录，并加显式测试（`OversightMetricsApplicationTest.java:133`）。**留给 owner**：若需让新数据真实回流，属人工终决契约变更，另行立项。

独立复验结论：两个 IMPORTANT 均闭合，**0 CRITICAL / 0 新 IMPORTANT**。

## 6. 门禁与构建验证

| 检查 | 结果 |
|---|---|
| 领域 TDD | RED（符号不存在）→ GREEN（9/9） |
| 应用 TDD | RED（缺投影/方法）→ GREEN（9/9） |
| 适配层 TDD | RED（缺端点权限/配置）→ GREEN（契约+Toggle） |
| ai-governance 模块 `mvn test` | **126 例 / 0 失败 / 0 错误 / BUILD SUCCESS**（含 ArchitectureTest 13/13） |
| 采购聚合 `mvn test`（10 模块） | **BUILD SUCCESS / EXIT=0**；作为 Build 绑定检查再次真实执行并生成 SDK 收据 |
| Comet build guard `--apply` | ALL CHECKS PASSED → phase=verify |
| evalset 门禁 | RESULT: PASS（仍 `seededDraft`，未改状态） |
| alignment 门禁 | 全部通过 **10/10** |
| frontmatter 门禁 | 33 份文档 PASS |
| persistence A10.29 | PASS（9 表 / 双唯一键 / 10 端口） |
| event-chain 门禁 | PASS（3 表 / 4 负向探针 / 端口持久化） |

## 7. 状态机说明（诚实记录）

独立审查后我修改了 `design.md` 的诚实边界说明，触发 SDK `CLASSIC_EVIDENCE_DRIFT`（设计批准后变更）。已先提交 `edit-review` 的 `non-behavioral` 判断（SDK 的 build 判据不消费该记录），随后严格按 SDK 要求执行 `revise-design` → `propose-design`（新 hash `e471caac`）→ `decide-design` → `complete-design`，并重新执行绑定构建检查。全程未绕过门禁、未改写实现；原批准覆盖的 D1–D5 决策未变。

## 8. 诚实边界

- 门禁绿只证明结构、fail-closed 不变量与文档族一致；不等于 AI 能力可上线。
- 阈值缺省 `UNDETERMINED` 是刻意设计，不是缺陷；owner 通过线未设定且不接受客户端注入。
- “未回流评测”仅覆盖台账中标记确为假的历史/外部记录；新 API 路径不可达，见第 5 节。
- 样本量来自台账全量只读查询；大租户下较重，本期仅低频治理端点，后续优化留 owner 立项。
- 本 change 未改 DDL / `bone-init.sql` / CI 三入口判据 / evalset 状态，未接真实模型，未执行生产库操作，未新增权限码，未改变人工终决权。

**结论：PASS（工程结构与 POC 验证通过），不声明模型效果达标。**
