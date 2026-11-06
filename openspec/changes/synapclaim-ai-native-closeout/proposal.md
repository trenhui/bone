# Proposal: synapclaim-ai-native-closeout

## Why

`synapClaim` 的 AI 原生改造经多轮迭代后，复核器 `doc/ai-product/synapClaim/tools/_ai_native_readiness.py` 实测仍为 **L0 运行态**：9 项判据仅 1 项 SATISFIED（EVAL_GATES_WIRED），8 项 BLOCKED。其中多数判据依赖外部前置（真实 provider/GPU、≥300 独立盲标票据、医学目录语料、税局验真通道），纯编码无法翻转；但 **FLYWHEEL_DOWNSTREAM（学习层校准消费方缺失）、感知层图像 OCR、受控 RAG 接线** 属于可编码缺口——不补齐它们，即使外部条件就绪系统也无法达到 L2/L3。本 change 用业界最佳实践（Google《Rules of ML》、ML Test Score、NIST AI RMF）补齐全部可编码判据，并以复核器为循环终止判据；不可编码前置如实登记，接受最终 `l3Claimable=false` 的诚实结论，禁止造假绿。

## What Changes

- **学习层校准消费方（翻转 FLYWHEEL_DOWNSTREAM）**：在 `synap-claim` 新增校准与离线评测消费链——实现类名匹配复核器正则 `(Calibration|Brier|Champion|EceCalibrat)` 的确定性实现：对 `AiEvalSamplePort` 出站的评测样本计算 Brier 分数 / ECE（期望校准误差），产出校准报告供置信度阈值决策；champion-challenger 离线对比评测（新模型/提示词版本 vs 现行版本，冻结集上跑分），不触碰资金路径。
- **感知层图像解析（推进 PERCEPTION_MODEL_DRIVEN）**：`DocumentParsePort` 新增图像多模态适配器（走 bone-ai-gateway 多模态通道），config 门控默认关闭；不可用/失败时诚实转人工（`FAILED` + 归因码），绝不冒充文本层结果。
- **知识层受控 RAG 接线（推进 CATALOG_ADMISSION / 知识层 L2）**：复用 `bone-ai-rag` 现有能力接线 `KnowledgeRetrievalPort` 语义检索支路，config 门控；语料未装配时 `available=false` fail-closed，不回退到无引用作答。
- **复核器判据与文档回写**：`_ai_native_readiness.py` 按新增能力扩展判据（如 IMAGE_PARSE_WIRED、RAG_WIRED），每项新判据附变异验证；`AI技术方案.md` §1.6/§1.7 同步回写实测状态，目标态不写成现状。
- **测试与 E2E**：新增单元测试（校准计算、图像适配器降级、RAG 门控）、更新前后端 E2E（`synap-claim-e2e.cjs`）覆盖 AI 参与路径并如实记录 `aiParticipated`；质量门禁 `mvn -pl bone-apps/synap-claim test` + `mvn spotless:apply` + `./scripts/check.sh` 全绿。

## Capabilities

### New Capabilities

- `claim/ai-calibration-flywheel`: 学习层校准消费——评测样本 → Brier/ECE 校准报告 → champion-challenger 离线评测 → 置信度阈值与放量的可证伪依据；样本不可伪造、口径可复算。
- `claim/ai-perception-image`: 图像材料多模态解析——`DocumentParsePort` 图像适配、config 门控、字段级置信度与原文坐标、不可用时诚实降级转人工。
- `claim/ai-knowledge-rag`: 受控 RAG 知识检索——语义检索支路接线、检索结果必须带条款/目录引用、语料缺失或门控关闭时 `available=false` fail-closed。
- `claim/ai-native-readiness-governance`: AI 原生就绪度治理——复核器判据扩展（附变异验证）、运行态口径（aiParticipated/provenance）与设计文档诚实回写协议。

### Modified Capabilities

（无——`openspec/specs/` 主规范中无 claim AI 相关既有 capability；上述能力均为新增，不改既有 spec 级行为。）

## Impact

- **代码**：`bone-apps/synap-claim/src/main/java/com/bone/claim/`（application/port、application/service、infrastructure/rag|document、可能新增 domain 校准值对象）；复用 `bone-ai/bone-ai-rag`、`bone-ai-gateway`，不修改其已有行为。
- **配置**：`application.yml` 新增图像解析与 RAG 门控配置项（默认关闭，不改变现网行为）。
- **无 DDL**：不新增/修改任何 `V*__*.sql` 迁移（校准报告走现有评测样本端口与文件产物，不落新表）；历史迁移一律不动。
- **文档**：`doc/ai-product/synapClaim/03-contract/AI技术方案.md`（§1.6/§1.7）、`tools/_ai_native_readiness.py`。
- **测试**：synap-claim 单测、`bone-frontend/e2e/synap-claim-e2e.cjs`、`outputs/claims-e2e-result.json` 运行态产物口径。
- **红线不变**：P1 AI 不碰钱、三档分诊、G0–G4 门禁、决策图五节点 DAG 拓扑不变；不引入自治 Agent。
