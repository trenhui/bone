# Tasks: synapclaim-ai-native-closeout

## 1. 学习层校准消费方（翻转 FLYWHEEL_DOWNSTREAM）

- [x] 1.1 实现 Brier/ECE 校准计算服务（类名含 Calibration/Brier/EceCalibrat，确定性、可复算；样本不足输出 INSUFFICIENT_SAMPLES 不编造数值），位置在 `bone-apps/synap-claim` 应用层，零 DDL。验证：新增单测覆盖正常/不足/无金标三类输入，数值断言通过；`mvn -pl bone-apps/synap-claim test` 全绿。（域内核 `AiConfidenceCalibration` 已在仓库实测存在，生产消费方由 1.3 承接；commit 7848641a6 同批入库其测试）
- [x] 1.2 实现 champion-challenger 离线对比评测（冻结集 `eval/ai-decision-evalset.json` 上逐案对比 + 汇总；缺金标仅产差异清单不产优劣结论）。验证：单测覆盖有/无金标两种场景。（`ChampionChallengerComparator`，commit baa5b5652）
- [x] 1.3 把校准消费链接到 `AiEvalSamplePort` 出站样本（消费已采集样本快照，产出报告对象；不自动改任何在线阈值/开关）。验证：单测证明报告产出后在线配置无变更；复核器 FLYWHEEL_DOWNSTREAM 判据翻转。（`AiCalibrationReportApplicationService` + `GET /api/v1/claim-ops/ai-calibration/report`，commit 1d58ba8cb；复核器已翻转 SATISFIED）

## 2. 感知层图像解析

- [x] 2.1 新增图像多模态解析适配器（`DocumentParsePort` 实现，走 bone-ai-gateway 多模态；产出与文本层同构的置信度/坐标/完整性 + 自溯源）。验证：单测（mock 网关）覆盖成功路径与契约校验。（网关 `LlmMessage.imageUrl` + content-parts 序列化 commit 63ee72bf0；`ImageDocumentParsePortAdapter` commit ec7fdb018）
- [x] 2.2 配置门控与降级：`bone.claim.document.parse.image.enabled` 默认 false；失败/超时/契约违反落 FAILED + 归因码转人工，禁止文本层兜底冒充。验证：单测覆盖门控关/网关不可用/超时三场景；application.yml 增项且默认值不变现网行为。（门控键以设计文档 D2 连字符形式 `bone.claim.document-parse.image.enabled` 为准，yml/注解/复核器三处同键；commit ec7fdb018）
- [x] 2.3 运行态口径接入：图像解析的模型参与/降级计入现有五桶健康度归因。验证：单测断言参与口径如实反映。（`EvidenceApplicationService.parseImage`，归因码逐字透传，commit 4fe752210）

## 3. 知识层受控 RAG 接线

- [x] 3.1 新增组合型 `KnowledgeRetrievalPort` 适配器（目录准入核验不变 + 语义检索支路接 bone-ai-rag，门控默认关；检索结果必须带引用）。验证：单测覆盖命中带引用/门控关/语料缺失三场景；既有目录准入单测全保留全绿。（`RagKnowledgeRetrievalPortAdapter`，pom 增 bone-ai-rag 已获 L3 批准，commit 4a5e5eeb2）
- [x] 3.2 RAG 成因纳入 KnowledgeBasis 二级归因（命中/语料缺失/门控关闭可区分）。验证：单测断言健康度归因成因码区分正确。（`RAG_CLAUSE_CITED`/`RAG_CORPUS_MISSING`/`RAG_RETRIEVAL_UNAVAILABLE`，commit 4a5e5eeb2）

## 4. 复核器判据扩展与文档回写

- [x] 4.1 扩展 `_ai_native_readiness.py`：新增 IMAGE_PARSE_WIRED、RAG_WIRED 等判据（静态核验接线 + 门控 + 降级诚实三要素），全部纳入 --self-test 负向探针。验证：`python3 tools/_ai_native_readiness.py --self-test` 通过（变异探针全翻转）。（commit f58d504fa；--self-test 12/12 探针全翻转）
- [x] 4.2 回写 `AI技术方案.md` §1.6/§1.7：新增能力按「实测态 vs 目标态」分线记录；外部前置（provider/盲标/语料/验真）如实登记为 BLOCKED 原因。验证：文档结论与复核器输出一致，无目标态写成现状。（§1.6⑦ + §1.7⑪，commit 954f4f03e）
- [x] 4.3 重跑复核器主判据，输出最终成熟度矩阵（预期：FLYWHEEL_DOWNSTREAM/IMAGE_PARSE_WIRED/RAG_WIRED 翻转，外部前置 RUNTIME_ONLY/BLOCKED 如实保留，l3Claimable=false 诚实登记）。验证：复核器输出存档并在文档引用。（存档 `outputs/ai-native-readiness-2026-11-05.txt`：11 项判据 SATISFIED 4 / BLOCKED 7 / RUNTIME_ONLY 0，L0，l3Claimable=false）

## 5. 前后端 E2E 与质量门禁

- [x] 5.1 扩展 `bone-frontend/e2e/synap-claim-e2e.cjs`：覆盖 AI 参与路径 + 图像/RAG 门控默认关的断言；产物如实记录 aiParticipated/provenance（无 provider 时标 RUNTIME_ONLY）。验证：E2E 跑通且产物口径与复核器判定一致。（门控默认关行为面由既有就绪清单断言 + 复核器 gate_default_false 静态核验承担；本轮增量 verdict 受控枚举断言，commit 1246a3e2d；E2E 22/22 PASS，runtime 口径与复核器对账 aligned）
- [x] 5.2 全量质量门禁：`mvn spotless:apply`、`mvn -pl bone-apps/synap-claim test` 全绿、`./scripts/check.sh` 通过。验证：三条命令退出码 0。（1203 tests 0 fail；check.sh 59/59 随提交 pre-commit 全过）
- [x] 5.3 循环复核收口：复核器主判据 + --self-test + E2E 三者结论一致，差异归零或如实登记；前端披露接口联动（AI_STUB 第四态）回归。验证：终轮复核报告（无新增可编码缺口）。（三方一致：复核器 4/7/0 + self-test 12/12 + E2E passed=true & alignedWithRuntime=true；无新增可编码缺口，外部前置如实 BLOCKED，l3Claimable=false）
