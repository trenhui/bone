# Design: synapclaim-ai-native-closeout

## Context

- 复核器 `doc/ai-product/synapClaim/tools/_ai_native_readiness.py` 是本 change 的循环终止判据（实测 L0：9 判据 1 SATISFIED / 8 BLOCKED）；其判定逻辑为静态核验（不读活库、不起服务），其中 FLYWHEEL_DOWNSTREAM 唯一纯编码可翻：要求 `bone-apps/synap-claim/src/main/java` 下存在类名匹配 `(Calibration|Brier|Champion|EceCalibrat)` 的实现类。
- 决策图固定五节点 DAG（`DecisionGraphExecutor`）：EvidenceLoad→RuleCheck→AiAssess→Adjust→Arbitrate；Adjust 为确定性理算（护城河，禁 AI）。端口已就绪：`DocumentParsePort`（已有 TextLayer/Llm 两实现）、`KnowledgeRetrievalPort`（已有目录准入实现）、`AiGatewayPort`、`AiEvalSamplePort`。
- `bone-ai/bone-ai-rag` 检索能力已存在但未接线；`bone-ai-gateway` 已具备多模态探活与契约校验；`EventAiEvalSamplePortAdapter` 已把 DIFF 结论转成评测样本事件出站——但全仓无消费方。
- 约束：不新增/修改 `V*__*.sql`；不动 multi-insurer change 产物；AI 不碰钱（P1）；默认配置不得改变现网行为。

## Goals / Non-Goals

**Goals**
- FLYWHEEL_DOWNSTREAM 翻转：实现校准消费链（Brier/ECE + champion-challenger 离线评测），确定性、可复算。
- 感知/知识两层推进：图像多模态适配器与受控 RAG 支路接线，均 config 门控、默认关闭、诚实降级。
- 复核器判据扩展（附变异验证）+ 技术方案文档诚实回写 + 单测/E2E 全绿。

**Non-Goals**
- 不扩 DAG 拓扑、不引入自治 Agent、不做在线阈值自动调整。
- 不实现真实 provider 接入/GPU 供给、盲标票据征集、医学语料建设、税局验真（外部前置，只登记不实现）。
- 不把目标态写成现状；接受最终 `l3Claimable=false`。

## Decisions

### D1 校准消费方（翻转 FLYWHEEL_DOWNSTREAM）

代码实测（2026-11-05）：`AiConfidenceCalibration` 域内核**已完整实现**（ECE/Brier/阈值扫描/`challengerAdmissible`/变异探针），复核器判据 `has_class=true` 但 `calibration_wired=false`——缺的只是生产代码消费方。因此：

- 新增 `AiCalibrationReportApplicationService`（application 层，只读报表）：从盲标仓储读 UNBLINDED+PAIRED 任务 → 解析 `aiSuggestionJson`（`aiConfidence`/`decisionResult`，PAIRED 已保证 `confidenceSource=MODEL`）+ `labelResultJson`（人工结论受控键；解析失败的样本计数 `unparseableSamples`，不编造）→ 构造 `CalibrationSample(BLIND_LABEL, frozenSetExcluded=true, sliceKey=渠道或GLOBAL)` → 调用 `AiConfidenceCalibration.evaluate(...)` 与 `challengerAdmissible(...)`。
- `BlindLabelTaskRepository` 增 `findUnblindedPaired(limit)` + 自定义 SQL 文件（资源文件，非 DDL 迁移）。
- 报表端点 `GET /claim-ops/ai-calibration/report`（只读）；阈值 minSamples=300 / perSlice=30 为请求参数缺省值，不写死在线配置；产出结论仅供人工决策，绝不自动改在线阈值（P1 + MRM）。
- 静态引用 `AiConfidenceCalibration.evaluate(` 出现在非测试生产代码 → 复核器 `calibration_wired` 翻转。

### D2 图像感知（最小网关多模态扩展）

代码实测：`AiGatewayPort` 仅 `assess()`；`LlmMessage` 为纯文本（role+content）；`OpenAiCompatibleClient` 按 `Map<String,String>` 序列化 messages。因此：

- bone-ai-gateway（向后兼容最小扩展）：`LlmMessage` 增可选 `imageUrl`（新 3 参构造，旧 2 参构造保留、imageUrl=null）；`OpenAiCompatibleClient` 对带 imageUrl 的 user 消息序列化为 OpenAI content-parts 数组（text + image_url）；上游不支持视觉时返回错误 → 调用方按不可用诚实降级，不重试不冒充。
- synap-claim：`DocumentParsePort` 增 default 方法 `parseImage(DocType, String imageRef)`（default 返回 `IMAGE_NOT_SUPPORTED`，既有适配器零改动）；新增 `ImageDocumentParsePortAdapter`（`@ConditionalOnProperty bone.claim.document-parse.image.enabled=true` 默认关；复用 PiiMasker/SafePromptBuilder/LlmOutputGuard 三件套；mock 响应显式拒绝 `IMAGE_MOCK_REJECTED`；失败/超时/契约违反落 `FAILED` + 归因码转人工，禁止对图像做文本层解析兜底）。
- `EvidenceApplicationService.parseImage(evidenceId)`：按 `evidence.fileRef` 走图像通道，产出与文本层同构的 `ParseOutcome`。
- 运行态口径：模型参与如实记 provenance，mock/失败计入降级桶，不冒充确定性解析。

### D3 受控 RAG（组合实现）

- 新增 `RagKnowledgeRetrievalPortAdapter`：组合「现有目录准入核验（行为不变）+ 语义检索支路（`RagApplicationService.retrieve`，`bone.claim.knowledge.rag.enabled` 默认 false）」；检索结果必须带 clauseRef，无引用丢弃并计数；语料缺失/门控关/检索不可用 → `available=false` + 受控成因码，纳入 KnowledgeBasis 二级归因（命中/语料缺失/门控关闭可区分）。
- 依赖：synap-claim 增 `bone-ai-rag` 模块依赖（build 时确认 pom）。
- 既有目录准入单测全保留全绿；语义支路独立单测。

### D4 复核器判据扩展

- 新增 `IMAGE_PARSE_WIRED`（适配器类存在 + yml 门控键存在 + 默认 false 三要素）、`RAG_WIRED`（同构三要素）；细化 `FLYWHEEL_DOWNSTREAM` 保持现有 `calibration_wired` 静态引用语义。
- 全部新判据纳入 `--self-test` 负向探针（移除接线/改默认值必须翻转），变异探针不翻转即判红。

### D5 E2E 与质量门禁

- `bone-frontend/e2e/synap-claim-e2e.cjs` 增量：图像/RAG 门控默认关的行为断言 + 校准报表端点断言；运行态产物如实记录 aiParticipated/provenance，无 provider 时判据如实 RUNTIME_ONLY。
- 门禁：`mvn spotless:apply` → `mvn -pl bone-apps/synap-claim test` → `./scripts/check.sh` 全绿后收口循环复核。

### 关键取舍（备选已否决）

1. **校准消费方放 `synap-claim` 应用层，纯离线、零 DDL**
   - 选型：新增确定性服务类（类名含 `Calibration`/`Brier`/`EceCalibrat`/`Champion` 以满足复核器正则），输入为 `AiEvalSamplePort` 已出站样本的快照集合，输出校准报告对象与（可选）文件产物；challenger 评测复用冻结评测集 `eval/ai-decision-evalset.json`。
   - 备选①：落库新表存报告——否决，引入 DDL 违反本 change 约束且无在线消费方。备选②：放 bone-ai 平台层——否决，复核器只扫 synap-claim 且业务口径（理赔分诊）属应用层。
   - 阈值不自动调整：报告仅产出结论供人工决策（P1 + MRM 三道防线）。
2. **图像解析作为 `DocumentParsePort` 第三实现，门控选择**
   - 选型：`ImageDocumentParsePortAdapter` 走 gateway 多模态；`application.yml` 增 `bone.claim.document.parse.image.enabled`（默认 false）+ 模式路由；失败/超时/契约违反落 `FAILED` + `AiDegradationReason` 同口径归因码。
   - 备选：并跑文本层兜底——否决，对图像做文本层解析等于冒充（§11.5 降级可归因红线）。
3. **受控 RAG 作为 `KnowledgeRetrievalPort` 组合实现**
   - 选型：新适配器组合「目录准入核验（现有）+ 语义检索（bone-ai-rag，门控）」；检索结果必须带引用，无语料/门控关时 `available=false` 带成因码，纳入 KnowledgeBasis 二级归因。
   - 备选：替换现有目录准入——否决，目录准入是已验收行为，组合保住回归面。
4. **复核器扩展最小化 + 变异验证**
   - 新增判据仅覆盖本轮真实接线（如 IMAGE_PARSE_WIRED、RAG_WIRED、CALIBRATION_CONSUMER 细化），全部进 `--self-test` 负向探针；不新增依赖运行数据的假判据。
5. **E2E 复用 `synap-claim-e2e.cjs` 增量改造**
   - 新增图像/RAG/校准的路径覆盖（门控关为默认断言，门控开为条件分支）；无 provider 时运行态判据如实 RUNTIME_ONLY，产物不造假。

## Risks / Trade-offs

- [类名匹配正则被判为「应试」] → 实现必须是真实可用的校准计算（带单测证明数值正确性），复核器只作为最低门槛而非充分条件。
- [门控默认关导致「接线了但跑不到」] → 判据设计为「接线 + 门控存在 + 降级诚实」三要素静态核验；运行态另设 RUNTIME_ONLY 判据，不混装。
- [challenger 评测缺金标被滥用为「更优」结论] → spec 明确缺金标只产差异清单；实现强制该约束。
- [RAG 组合实现回归风险] → 目录准入路径行为不变（现有单测全保留），语义检索支路独立单测。

## Migration Plan

全部为增量代码 + 默认关闭的配置，无数据迁移；回滚 = 关闭配置门控或回退代码，现网行为不受影响。

## Open Questions

- 校准报告是否需要前端展示入口（当前仅产物 + 接口可查，属可后续追加项，不阻塞判据）。
