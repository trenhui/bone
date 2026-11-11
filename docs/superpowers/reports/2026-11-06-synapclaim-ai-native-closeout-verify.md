# 验证报告：synapclaim-ai-native-closeout

- 日期：2026-11-06
- 验证模式：full（scale：任务 14 > 3、变更文件 52 > 8）
- review_mode：standard —— build 阶段（executing-plans）已对同一 diff 完成标准代码审查，verify 不重复评审未变化 diff；本轮新增改动仅并行会话在制品（见「范围与归因」），不属于本 change 审查范围。
- 产物语言：zh-CN

## 范围与归因（dirty worktree）

- 本 change 全部 9 个提交（7848641a6..c2b1d837b）已入库 dev，工作区验证基于已提交状态。
- 工作区现存未提交/暂存改动（AiRolloutDrill*、TrustedOcr*、OcrDocType、治理文档、e2e/复核器演练扩展、新 change multi-insurer-biz-identity-catalog-mapping）经归因属于**另一活跃会话的在制品**，按用户既定决策不接管、不提交、不修改；验证运行时包含其已暂存代码（编译与测试均通过，未对证据产生混淆归因）。

## 新鲜验证证据（2026-11-06 实跑）

| 检查 | 命令 | 结果 |
|---|---|---|
| 单测 | `mvn -pl bone-apps/synap-claim test` | Tests run: 1203, Failures: 0, Errors: 0, Skipped: 6，BUILD SUCCESS |
| 质量门禁 | `./scripts/check.sh` | 59/59 全过（exit 0） |
| 复核器主判据 | `python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py` | 判据 12 项：SATISFIED 5 / BLOCKED 7 / RUNTIME_ONLY 0；l3Claimable=false（诚实登记） |
| 复核器自检 | `... --self-test` | PASS（13 探针全翻转，含并行会话新增演练探针） |
| 前后端 E2E | `node bone-frontend/e2e/synap-claim-e2e.cjs` | PASS 22/22（真实服务栈无 mock；产物 outputs/claims-e2e-result.json） |

三方一致：复核器静态判据（static l3Claimable=false）↔ E2E 运行期就绪清单（runtime=false）↔ 对账检查（aligned）全部通过；E2E runtime 口径与复核器对账 aligned。

## 完整验证 7 项检查

1. **tasks.md 全部完成**：PASS —— 14/14 勾选，且逐任务回填 commit 号（7848641a6 / baa5b5652 / 1d58ba8cb / 63ee72bf0 / ec7fdb018 / 4fe752210 / 4a5e5eeb2 / f58d504fa / 954f4f03e / 1246a3e2d 等）。
2. **实现符合 change design.md 高层决策**：PASS —— D1 `AiCalibrationReportApplicationService` + `GET /api/v1/claim-ops/ai-calibration/report`（只读、minSamples=300/30 治理下限不可绕过）；D2 `ImageDocumentParsePortAdapter` + 门控键 `bone.claim.document-parse.image.enabled` 默认 false（yml:54-63）；D3 `RagKnowledgeRetrievalPortAdapter` + `bone.claim.knowledge.rag.enabled` 默认 false（yml:64-70）；D4 复核器 IMAGE_PARSE_WIRED / RAG_WIRED 判据 + 全量负向探针；D5 E2E 增量与门禁全绿。
3. **实现符合 Design Doc**（docs/superpowers/specs/2026-11-05-synapclaim-ai-native-closeout-design.md）：PASS —— 文件存在，技术设计与 change design.md 决策一致（校准离线零 DDL、图像诚实降级三归因码、RAG 组合适配器、复核器变异验证）。
4. **能力规格场景全部通过**：PASS ——
   - ai-calibration-flywheel：Brier/ECE 确定性计算（单测数值断言）；样本不足 → `INSUFFICIENT_SAMPLES` 不编造（E2E 实证 verdict=NO_EVIDENCE 且 ece/brier/proposed 全 null）；缺金标只产差异清单；报告产出不改在线配置（单测断言）。
   - ai-perception-image：多模态通道同构产出；门控默认关零模型调用；网关不可用/超时/契约违反 → `FAILED` + IMAGE_GATEWAY_UNAVAILABLE / IMAGE_OUTPUT_INVALID / IMAGE_MOCK_REJECTED 转人工；参与口径如实入桶（parseImage 归因码逐字透传）。
   - ai-knowledge-rag：命中必带 clauseRef；语料缺失/门控关 → available=false + RAG_CORPUS_MISSING / RAG_RETRIEVAL_UNAVAILABLE / 门控关成因可区分；默认关时目录准入行为不变（既有单测全保留全绿）。
   - ai-native-readiness-governance：新判据全部纳入 --self-test 负向探针（13 探针全翻转）；E2E 无 provider 时如实 RUNTIME_ONLY/BLOCKED 不造假；文档区分实测态 vs 目标态（AI技术方案.md §1.6⑦/§1.7⑪）。
5. **proposal.md 目标已满足**：PASS —— FLYWHEEL_DOWNSTREAM / IMAGE_PARSE_WIRED / RAG_WIRED 三项可编码判据全部翻转 SATISFIED；外部前置（PROVIDER_CONFIGURED、盲标、语料、验真、BASELINE、FROZEN_EVALSET、GOLIVE 草稿、RUNTIME_MODEL_COVERAGE）如实 BLOCKED；接受 l3Claimable=false 诚实结论（符合 proposal「禁止造假绿」约定）。
6. **delta spec 与 design doc 无矛盾**：PASS —— 4 个 capability 的 delta spec 与 D1–D5 决策一一对应；门控键命名按 design doc D2 连字符口径统一（tasks.md 2.2 已注明三处同键）；无 spec 有而 design doc 未记录的增量。
7. **关联设计文档可定位**：PASS —— docs/superpowers/specs/2026-11-05-synapclaim-ai-native-closeout-design.md 存在且与 change 对应。

## 问题清单

- CRITICAL：无
- IMPORTANT：无
- WARNING：无
- SUGGESTION：无新增（设计文档「Open Questions：校准报告前端展示入口」为可后续追加项，change 范围内不阻塞，已在 proposal/design 登记）

## 结论

7 项检查全部 PASS，无 CRITICAL / IMPORTANT 问题，验证通过。成熟度结论维持诚实判定：**L0 可判定档，l3Claimable=false（SATISFIED 5 / BLOCKED 7，其中 5 项 SATISFIED 含并行会话新增的 ROLLOUT_ROLLBACK_DRILL）**；外部前置就绪前不可宣称 L3。
