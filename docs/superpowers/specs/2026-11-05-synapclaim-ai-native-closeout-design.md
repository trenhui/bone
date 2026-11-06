# Design Doc — synapclaim-ai-native-closeout

---
comet_change: synapclaim-ai-native-closeout
role: technical-design
canonical_spec: openspec
---

> 状态：已确认（2026-11-05）｜ Change: openspec/changes/synapclaim-ai-native-closeout ｜ 产物语言 zh-CN
> 本文档是 Superpowers Design Doc；OpenSpec 侧设计（proposal/specs/tasks 对位）见 change 目录 design.md，两者同步维护。

## 1. 问题与目标

复核器 `doc/ai-product/synapClaim/tools/_ai_native_readiness.py` 实测 synapClaim 为 L0 运行态（9 判据 1 SATISFIED / 8 BLOCKED）。目标：补齐**全部可编码缺口**（学习层校准消费方、感知层图像、知识层受控 RAG、复核器判据扩展），外部前置（provider/GPU、≥300 盲标票据、医学语料、验真通道）如实登记；循环复核终止判据 = 复核器 + --self-test + E2E 三者一致，接受 `l3Claimable=false` 的诚实结论。

红线：P1 AI 不碰钱、三档分诊、G0–G4 门禁、五节点 DAG 拓扑不变；无 DDL、不改已有迁移、默认配置不变现网行为。

## 2. 代码实测结论（设计依据）

| 事实 | 位置 | 设计含义 |
|---|---|---|
| `AiConfidenceCalibration` 域内核已完整（ECE/Brier/阈值扫描/challenger 判定） | `synap-claim/domain/model/evaluation/AiConfidenceCalibration.java` | 校准只缺**生产消费方**（复核器 `calibration_wired` 扫非测试代码中的 `AiConfidenceCalibration.` 静态引用） |
| 盲标链完整：dispatch→submit→unblind，解盲后 `aiSuggestionJson`（含 aiConfidence/decisionResult）+ `labelResultJson` | `BlindLabelApplicationService` | 校准样本直接取自 UNBLINDED+PAIRED 盲标任务 |
| 网关纯文本协议：`LlmMessage(role, content)`、`OpenAiCompatibleClient` 按 `Map<String,String>` 序列化 | `bone-ai-gateway` | 图像支持需最小多模态扩展（向后兼容） |
| `DocumentParsePort.parse(DocType, String rawText)`；`EvidenceApplicationService.parse` 以 rawText 驱动；evidence 有 `fileRef` | `synap-claim` | 图像通道需端口扩展 + 按文件类型路由 |
| `RagApplicationService.retrieve(RagQuery)→RagRetrieveOutcome` 已存在未接线 | `bone-ai-rag` | 知识层组合适配器即可，不改平台行为 |
| `KnowledgeRetrievalPort` 现实现为目录准入核验（fail-closed） | `CatalogKnowledgeRetrievalPortAdapter` | 组合实现保住回归面 |

## 3. 设计决策（D1–D5）

### D1 校准消费方（翻转 FLYWHEEL_DOWNSTREAM）
- `AiCalibrationReportApplicationService`（application 层，只读）：`BlindLabelTaskRepository.findUnblindedPaired(limit)`（+SQL 资源文件，非 DDL）→ 解析 `aiSuggestionJson`/`labelResultJson`（解析失败计数 `unparseableSamples`，不编造）→ `CalibrationSample(BLIND_LABEL, frozenSetExcluded=true, sliceKey=渠道或GLOBAL)` → `AiConfidenceCalibration.evaluate(...)` / `challengerAdmissible(...)`。
- 端点 `GET /claim-ops/ai-calibration/report`；minSamples=300/perSlice=30 为请求参数缺省；结论仅供人工决策，绝不自动改在线阈值。
- 组件边界：解析与样本映射是纯函数（可单测）；仓储查询是唯一 IO；报表服务无事务写。

### D2 图像感知（最小网关多模态扩展）
- gateway：`LlmMessage` 增可选 `imageUrl`（3 参构造，2 参保留）；`OpenAiCompatibleClient` 带图消息序列化为 OpenAI content-parts（text+image_url）；上游不支持视觉→错误→调用方诚实降级。
- synap-claim：`DocumentParsePort` 增 default `parseImage`（默认 IMAGE_NOT_SUPPORTED，既有适配器零改动）；`ImageDocumentParsePortAdapter`（门控 `bone.claim.document-parse.image.enabled` 默认 false；PiiMasker/SafePromptBuilder/LlmOutputGuard 三件套；mock 拒绝 `IMAGE_MOCK_REJECTED`；失败落 FAILED+归因码转人工，禁止文本层兜底冒充）。
- `EvidenceApplicationService.parseImage(evidenceId)` 按 `fileRef` 路由。

### D3 受控 RAG（组合实现）
- `RagKnowledgeRetrievalPortAdapter` = 目录准入（不变）+ 语义检索支路（`RagApplicationService.retrieve`，门控 `bone.claim.knowledge.rag.enabled` 默认 false）；结果必须带 clauseRef，无引用丢弃计数；无语料/门控关→`available=false`+成因码，进 KnowledgeBasis 二级归因。
- synap-claim pom 增 `bone-ai-rag` 依赖。

### D4 复核器判据扩展
- 新增 `IMAGE_PARSE_WIRED` / `RAG_WIRED`（接线+门控+降级诚实三要素静态核验）；全部进 `--self-test` 负向探针，变异不翻转即判红。

### D5 E2E 与门禁
- `synap-claim-e2e.cjs`：门控默认关断言 + 校准报表端点断言；运行态如实记录 aiParticipated/provenance，无 provider 判 RUNTIME_ONLY。
- 门禁顺序：`mvn spotless:apply` → `mvn -pl bone-apps/synap-claim test` → `./scripts/check.sh`。

## 4. 数据流

```
盲标 PAIRED 解盲 ──► findUnblindedPaired ──► 样本映射(纯函数) ──► AiConfidenceCalibration.evaluate
                                                                      │
图像 evidence ──► parseImage(门控) ──► gateway multimodal ──► ParsedDocument 同构      ▼
                                                                      GET /claim-ops/ai-calibration/report
条款/目录语料 ──► RagKnowledgeRetrievalPortAdapter(门控) ──► KnowledgeBasis(带引用/fail-closed)
```

## 5. 错误处理

- 校准：样本不足 `INSUFFICIENT_SAMPLES`；结论解析失败计数不阻断；报告只读不改配置。
- 图像：门控关=零调用；网关异常/超时/契约违反/mock=FAILED+归因码（IMAGE_GATEWAY_UNAVAILABLE/IMAGE_OUTPUT_INVALID/IMAGE_MOCK_REJECTED），转人工。
- RAG：门控关=行为与现状一致；检索不可用/无语料=available=false+成因码；无引用结果丢弃计数。

## 6. 测试策略

- 单测：校准数值正确性（手算 ECE/Brier 对拍）、样本映射（含 unparseable）、图像门控三态（关/不可用/成功）、RAG 三态（关/缺语料/命中带引用）、网关多模态序列化。
- 回归：既有目录准入、盲标、决策图单测全保留。
- E2E：前端 `synap-claim-e2e.cjs` 增量断言；产物口径与复核器一致。
- 终止判据：复核器主判据 + --self-test + E2E 三者一致，差异归零或如实登记。

## 7. 迁移与回滚

全部为增量代码 + 默认关闭配置，无数据迁移；回滚=关门控或回退代码，现网行为不受影响。

## 8. Open Questions

- 校准报表是否需要前端展示入口（不阻塞判据，可后续追加）。
