# 核验报告：bba6ad30 智能理赔场景端到端验证

- 复核对象：`bba6ad30`（5 文件 +599 行，分支 dev）
- 核验方式：源码走查 + 对照文档真源 + 离线定向跑测（`mvn -o -pl bone-ai/bone-ai-server test`）
- 结论：**有条件通过**（三走查真实跑通、契约总体一致、定位清晰；发现 4 项需注意/建议修正点）

---

## ① 三走查是否真实覆盖全能力（测试非 mock 全链路）

定向运行 `ClaimSettlementScenarioTest`：**Tests run: 3, Failures: 0, Errors: 0, BUILD SUCCESS**；`ClaimControllerTest` 亦 3 用例全过。日志实证真实组件被执行：`QlExpressRuleEngine initialized`（真实 QLExpress）、`OrchestrationService short-circuited by rule hit: ... EXCLUDE`、`HITL item created ... PENDING`、`Claim escalated to HITL`。

真实组合对象（仅仓储 mock，服务与引擎真实）：`QlExpressRuleEngine` / `PdfBoxDocumentParser` / `MockLlmClient` / `LlmGatewayService` / `OrchestrationService` / `HitlService` / `EvidenceTraceListener` + 进程内事件总线。mock 的仅 3 个仓储接口（rule/evidence/hitl repo）。故"测试真实跑通而非 mock 全链路"成立。

### 走查 A — 免责短路（document + rules + orchestration + gateway 成本红线）
- 真实引擎求值：`QlExpressRuleEngine` 对 `amount >= 50000`（hitBlock=true）真实求值，amount=80000 → hit → block → 短路。断言：`mode==SHORT_CIRCUITED`、`verdict==AUTO_REJECT`、`blockedRuleCode==EXCLUDE`（测试 L143-147）。
- document 真实解析：`PdfBoxDocumentParser` 用 PDFBox 解析测试自举生成的保单 PDF → `ClauseSplitter` 切出 Clause 1/Clause 2 → `DocumentIR`；断言 clauses 非空、`clauseNo().startsWith("Clause 1")`（L137-141）。
- 成本红线结构强制：短路后 `OrchestrationService.run` 跳过 LLM 步骤（不触碰 gateway），断言 `published` 为空（L149）、`evidenceRepo` 从不 insert（L150）。走查 A 只读验证"无事件/无留痕"。
- 证据链完整：文件 + 行号确认。

### 走查 B — 常规核赔（orchestration + gateway 计量 + trace 证据链）
- RISK 规则 `amount >= 50000`（hitBlock=false）未命中（amount=2000）→ 调 `MockLlmClient`（真实）→ 断言 `mode==LLM_EVALUATED`、`verdict==AUTO_APPROVE`、`llmResponse` 非空（L177-179）。
- gateway 真实发布：`LlmGatewayService.invoke` 经进程内总线发 `LlmCallRecorded` + `LlmUsageRecorded`；断言 published 含两者（L182-183）。
- trace 真实落快照：总线派发 → `EvidenceTraceListener.onLlmCallRecorded` → `EvidenceSnapshotRepository.insert`（mock 捕获）；断言 `type==LLM_CALL`、tenant=42、traceId/engineVersion 非空（L187-190）。证据三元组（输入 SHA-256 + IR + 引擎版本）由监听器就地计算，可回放成立。

### 走查 C — 高风险终审（orchestration + HITL 五态 + ReasonType + 处置链）
- 真实链路：riskLevel=HIGH → `HitlService.create` → PENDING → 断言 `mode==HITL_PENDING`、`verdict==UNDER_REVIEW`、`hitlStatus==PENDING`（L227-228）。
- 人工驳回（真实 HitlItem.reject）：act("reject", ReasonType.RULE_MISS) → REJECTED；断言 `disposition.action()==reject`、`reasonType==RULE_MISS`、`status==REJECTED`（L233-235）。
- 处置链回放：`dispositionHistory` 断言 1 条、actor/comment 正确（L239-241）。
- 证据链：文件 + 行号确认。

结论：**三走查真实覆盖全部 6 项 AI 能力（document/rules/gateway/trace/orchestration/HITL），名实相符，非 mock 全链路。**

---

## ② 业界对齐是否成立

- 理赔自动化范式（规则短路省成本 → AI 评估 → 低置信/高风险转人工终审）符合保险理赔自动化业界最佳实践：规则先行成本红线、AI 辅助评估、HITL 人工终审兜底，与总案 §3.3「AI 到待人工即停、不自造审批流、不自主执行不可回放」一致。
- "平台只做证明与治理不做判断"被 ClaimService 清晰体现：`underwrite` 只产出 `ClaimDecision`（含证据 IR / 短路信号 / HITL 待决 / 置信度），不驱动业务审批状态机；`act` 只做裁决回写，裁决后状态由业务方消费（ClaimService.java L28-31、L48-49 docstring）。与总案 §3.3「与业务状态机分层」口径一致。

结论：**业界对齐成立。**

---

## ③ 发现的任何问题

### 问题 1（需修正，测试名实相关）：低置信度 HITL 分支在演示中不可达
`ClaimService.extractConfidence` 无视真实 `LlmResponse` 恒返回 0.9（ClaimService.java L171-173）。三走查 confidence 恒为 0.9，均 ≥ 0.7；因此 `confidence < HITL_CONFIDENCE_THRESHOLD` 的转 HITL 分支（L111-112）在三走查中**从未被触发**——HITL 仅在 riskLevel=HIGH 时进入。docstring 声称的"低置信度 / 高风险 → HITL"中"低置信度"路径无任何走查实证。
- 建议：`MockLlmClient` 或 `extractConfidence` 支持按需注入低置信度（如让 mock 可配置置信度），补一条低置信（<0.7）转 HITL 的走查或断言，避免宣称覆盖而实际未覆盖。

### 问题 2（需修正，测试名实相关）：走查 C 的 act 作用在预置 item，而非 underwrite 产出的 item
`underwrite` 经 `HitlService.create` 生成一个**新随机 UUID** 的 hitlItem（日志 id=479edab8…），`decision.hitlItemId()` 即该 id；但随后 `act("t1","hitl-C",...)`（测试 L233）作用于测试预置的 `pendingItem`（id="hitl-C"，L218-220），两者**不是同一对象**。即"高风险终审 → reject 回流"展示的是预置 item 的裁决闭环，与 underwrite 产出的 item 未打通。
- 证据链：文件 + 行号确认。
- 影响：作为无库无 boot 的参考演示可接受；但"端到端"字面下 C 走查的裁决与创建未真正衔接，建议要么让 act 消费 `decision.hitlItemId()`，要么在注释中明确演示取数方式，避免名实偏差。

### 问题 3（注意，契约简化）：置信度三级分档未落地
详设 §4 阈值表（初值）为「≥0.85 直出 / 0.7–0.85 复核 / <0.7 强制人工」；`ClaimService` 仅实现 `confidence < 0.7 → HITL`（L111-112），将 ≥0.7 一律 AUTO_APPROVE，未实现 0.7–0.85"复核"档。因 confidence 恒 0.9，该档完全不可达。属参考宿主的合理简化，但与详设初值不完全一致，建议注明"演示简化，正式档位按详设 §4 校准"。

### 问题 4（注意，接口一致性）：ClaimController 的 `{itemId}` 路径变量与 `@RequestPart("itemId")` 重复
`@PostMapping("/{itemId}/act")`（ClaimController.java L56）路径含 `{itemId}`，但方法体未用 `@PathVariable`，itemId 实际取自 `@RequestPart("itemId")`（L59）；`/{itemId}/history`（L70）同理（L72）。REST 资源寻址与表单数据双源重复，语义冗余、易误读（看似 RESTful 实际全走表单 part）。建议二选一：保留 RESTful 路径用 `@PathVariable`，或去掉 `{itemId}` 统一表单。

### 已知约束评估（任务第 5 项）
- **保单 PDF 用英文条款（PDFBox 标准 Helvetica 不支持中文）**：走查 A 用 "Clause 1/Clause 2" 英文条款自举 PDF，规避了 Helvetica 中文缺字问题。作为演示约束合理（PDFBox 文本层解析本身与语言无关，中文条款在真实嵌入中文字体 PDF 下可解析）；仅限演示路径，不影响真实解析能力。**可接受。**
- **MockLlmClient 置信度固定 0.9 / extractConfidence 写死**：这是**隐藏缺陷的诱因**（见问题 1）——不是纯演示可接受，因为它使低置信度 HITL 路径在演示与单测中均不可达，掩盖了该分支的正确性。应视为待修正项而非可接受项。

---

## ④ 结论

**有条件通过。**

- 三走查真实覆盖全部 6 项能力（非 mock 全链路，定向跑测实证），断言名实相符。✔
- 业界对齐（规则短路省成本 + AI 评估 + HITL 人工终审；平台只做证明不做判断）成立。✔
- 契约一致：HITL 五态枚举（PENDING/CONFIRMED/REJECTED/MODIFIED/ESCALATED）与总案 §3.3 完全一致；ReasonType 字典（RULE_MISS/CONTEXT_MISS/SUGGESTION_WRONG/POLICY_CONFLICT）与总案/详设一致；置信度阈值 0.7 与详设一致；DocumentIR 字段（pageCount/provider/clauses/parseMode=PDFBOX_TEXT）使用正确；hitl_item 语义（scene/suggestion/suggestedValue/riskLevel/confidence）与领域模型一致；`@EnableSqlRepositories` 正确注册 `com.bone.aiorch.domain.hitl` 仓储。✔

**放行条件**（建议在演示定位下先修问题 1、2 中的名实偏差，或至少在注释/报告显式声明为演示简化；问题 3、4 可后续优化）：
1. 让低置信度 HITL 分支可被走查触发（问题 1）——避免"覆盖声称"与实测不符；
2. 打通/明示走查 C 的 underwrite 产出 item 与 act 裁决对象（问题 2）；
3. 注明置信度档位为详设三级分档的演示简化（问题 3）；
4. 收敛 ClaimController 的 `{itemId}` 双源绑定（问题 4）。
