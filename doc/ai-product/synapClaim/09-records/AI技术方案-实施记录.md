---
doc_id: record.ai.impl-log
role: record
currency: historical
---

# AI技术方案 · 实施记录（历史证据）

> 本文收录逐轮循环复核、变异验证与边界登记，属实施过程证据，不承担当前技术口径。目标态 / 当前态与红线以《AI技术方案》正文为准；逐版变更明细见《AI技术方案 · 版本历史》。

## 1. §1.7 循环复核逐轮记录（v2.5–v2.10）

#### ⑥ 循环复核第 2 轮（v2.5）：发现并修正一处实现偏差

| 项 | 内容 |
|---|---|
| 发现 | v2.4 的「分桶必需」实际是**档位级**判定（`stage.permitsAutoSettlement()`）：CANARY 档下未命中灰度桶、或落在渠道白名单外的案件本就走人工，却被无谓收紧。与 v2 校准要求的「仅在会被自动出金的案件上要求模型参与」不一致，违反渐进交付的爆炸半径控制 |
| 修正 | `AiRolloutApplicationService.modelParticipationRequired(tenantId, claimId, channel)`：与资金侧准入共用 `load(long)` 的同一份 `AiRolloutPolicy`，并复用 `AiRolloutPolicy#admitsCase` 的**同一分桶键**（阶段闸 + 渠道闸 + 比例闸）；`DecisionGraphExecutor` 传入 `claimId` 与 `sourceChannel` |
| 保留 | `AiRolloutStage.requiresModelParticipation()` 仍作为「档位是否需要模型」的显式领域判据参与组合，两个方法都有真实消费方，不产生「配了不生效」的方法 |
| 证据 | 新增 5 例案件级分桶用例（未配置 / 命中白名单 / 白名单外 / AUTOMATED / 显式关闭）；变异：摘除 `admitsCase` 组合 ⇒ 1 例转红（`Expecting value to be false but was true`）、17 例仍绿；还原后全模块 **1137 run / 0 fail / 0 error / 6 skipped**，`scripts/check.sh` 59/59 全绿 |

#### ⑦ 循环终止判据：L3 就绪度复核器

复核结论不再靠人读文档推断。`tools/_ai_native_readiness.py` 只读取仓库**可静态核验的事实**，逐项判定 §1.7 ③ 的前置条件并输出 `l3Claimable`：

```bash
python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py
python3 doc/ai-product/synapClaim/tools/_ai_native_readiness.py --self-test
```

**2026-11-05 实测输出**：9 项判据，`SATISFIED 1 / BLOCKED 7 / RUNTIME_ONLY 1`；当前可判定成熟度 **L0（运行态纯规则路径）**；**`l3Claimable = false`**。唯一 SATISFIED 是「评测门禁已带 `--check` 接线」——即目前只有护栏是真实生效的。`--self-test` 的 3 个负向探针（删 `--check` 接线 / 目录默认值改 `MANDATORY` / 注入 provider 环境变量）全部发生状态翻转，证明判据有判别力而非恒真。

判据刻意把 `aiCoverage` 等运行期数据标为 `RUNTIME_ONLY` 并阻断：静态文件里没有真实流量证据，绝不因「没数据」判成「达标」。

**循环收敛结论**：代码侧循环到此收敛。7 项 BLOCKED 中，provider/密钥/GPU、非 ML 基线数字、冻结集真实样本、目录语料与策略、合规分册转正共 5 类属外部条件或组织审批；`FLYWHEEL_DOWNSTREAM`（校准消费方）虽可编码，但缺真实样本与人工盲标金标准时仍无法验收；`RUNTIME_MODEL_COVERAGE` 只能由真实流量给出。**继续编码可以改变「可编码的评测骨架」，但不会让 `l3Claimable` 变为 true**。因此后续循环的形态应转为：按 ③ 的 owner 逐项闭环外部条件 → 每闭环一项重跑本复核器 → 由输出决定是否进入下一 Gate；不得用改文档或加护栏替代实证。

#### ⑧ 循环复核第 3 轮（v2.6）：前后端真实端到端测试

**起栈（非 mock）**：MySQL 8.0.31（`bone` 库 209 表 / `t_claim*` 24 表）+ IAM 8081 + 网关 8888 + synap-claim 8087 + shell 3000 + claim-app 3013；五个端点 health 全 200，后端日志均出现 `Started ... in ... seconds`（判据，非推断）。

**E2E 资产（已入版本控制，可重复运行）**：`bone-frontend/e2e/start-services.sh`（每次先重建 claim 与 gateway jar 再起后端）、`start-frontend.sh`（shell + 子应用）、`synap-claim-e2e.cjs`（Playwright 1.64.1 + Chromium）。运行：先后台起两个脚本，再 `node bone-frontend/e2e/synap-claim-e2e.cjs`；产物 `outputs/claims-e2e-result.json` 与两张截图。启动脚本刻意不含 `kill`：共享树下不得由 E2E 脚本终止可能属于其他会话的进程。后台任务被会话回收（如网关任务收到 SIGTERM / exit 143）只表示**托管 shell 结束**，不等于服务栈不可用；判据一律用**业务路径**探测，不看后台任务状态：网关的 `/actuator/health` 是聚合健康度，本 E2E 只起 IAM + claim 时未启动的 masterdata / blueprint 上游会把它拉成 **503**，但 `/api/v1/iam/login` 与理赔路由完全正常（2026-11-05 实测 health=503 而 E2E 12/12 PASS）。用 health 判断网关可用会把正常链路误判成故障。

| 组 | 判据 | 实测 |
|---|---|---|
| A 正向（经网关、真实 JWT 与 DB） | 登录 200 且签发 JWT（4475 字符）；赔案列表 200（3 条）；`ai-readiness` 200 且 `deploymentGatesPassed=false`（6 blockers）；运行期健康度 31 天窗口 200 | 5/5 |
| A 负向探针 | 无 token ⇒ 401；35 天窗口 ⇒ 400 `SC_CLAIM_AI_RUNTIME_HEALTH_QUERY_INVALID` | 2/2 |
| B 浏览器 | shell 登录落地 token → qiankun `#subapp-viewport` 渲染核赔工作台（4 个主体标记全命中）→ 截图；旧宽松正则会被菜单骗过、新严格判据不会；链路 0 个 5xx | 5/5 |
| **合计** | | **12/12 PASS** |

**E2E 抓到并修复的三类真实问题**：

1. **网关部署态陈旧**：`claim-ops` 三端点经 8888 全 404，直连 8087 为 200/400 ⇒ 判定「路由缺」而非「服务缺」。根因是**旧构建的 gateway jar 不含源码中已登记的 `synap-claim-ops` 路由**；重建 jar 并重启网关后 404→200。E2E 前置必须重建网关 jar，否则会把部署态误判成代码缺陷。
2. **首版 UI 断言自造假绿**：用 `body` 文本正则匹配到 shell 菜单「智能理赔」即判通过。改为断言 `#subapp-viewport` 内 4 个主体标记（核赔工作台 / 报案号 / 待领取 / 决策图）命中 ≥3，并加负向自检证明旧宽松正则会被菜单文本骗过、新判据不会。
3. **5xx 监听漏报**：首版监听器注册在导航之后且只过滤 claims 前缀，漏掉 4 个 503。改为全程捕获并区分链路内外：4 个 503 全部来自 shell 仪表盘/通知接口（`/api/v1/notification/*`、`/api/v1/console/*`，对应服务本次未启动），**理赔链路 0 个 5xx**，已如实记入结果 JSON 的 `ui.external5xx`。

**运行期证据接入循环判据**：E2E 产物的 `runtime` 段被 `tools/_ai_native_readiness.py` 消费，`RUNTIME_MODEL_COVERAGE` 从「`RUNTIME_ONLY`（缺证据）」升级为「`BLOCKED`（有证据且为 0）」：`nodesScanned=88 / aiParticipated=0 / aiDegraded=88 / participationRate=0.0000 / capabilityMissing=75(KNOWLEDGE_UNAVAILABLE)`。复核器现输出 **SATISFIED 1 / BLOCKED 8 / RUNTIME_ONLY 0，`l3Claimable=false`**，4 个负向探针全部翻转。

**判据自身的循环复核（同轮）**：并行会话新增了 `AiConfidenceCalibration` 类及单测，复核器初版「实现类名命中即 SATISFIED」随之转绿；但排查发现生产代码只有 `AiModelCard` 的 javadoc `{@link}` 提及、无任何真实调用，属于「有类没接线」。故把 `FLYWHEEL_DOWNSTREAM` 收紧为「必须存在非注释行的真实调用」，自测探针改为双向翻转（5/5 PASS），最终仍为 `SATISFIED 1 / BLOCKED 8 / RUNTIME_ONLY 0`。**就绪度复核器自身也要接受防假绿审查**：判据变松比能力没进展更危险。

> 这是 L3 未达成的最硬证据：**不是没有数据，而是真实流量证明模型一次都没有参与决策**。它与 §11.4/§11.5 的 dev 库取证、§1.7 ③ 的前置清单三方一致，且由可重复运行的 E2E 与复核器自动产出，不再依赖人工叙述。

#### ⑨ 循环复核第 4 轮（v2.7）：判据自身假绿的修复与三方对账

**背景**：并行会话在同一共享树推进《AI 原生收口实施计划》，落地了 `AiConfidenceCalibration`（ECE/Brier/阈值扫描/challenger 内核）、`BlindLabelCalibrationSamples`（盲标→样本映射）、`AiCalibrationReportApplicationService`（只读报表，真实调用 `from(...)` 与 `evaluate(...)`）。这是**真实进展**，复核器 `FLYWHEEL_DOWNSTREAM` 随之由 BLOCKED 转 SATISFIED。

**但循环复核发现判据的论证路径是错的**：初版只要生产代码里出现 `AiConfidenceCalibration.` 就判「已接线」，于是一行 `import ...AiConfidenceCalibration.CalibrationSample;`（仅引入数据类）同样能翻绿。也就是说**即使把真实计算调用全部删掉，判据仍然绿**——符合本项目「脚本存在 ≠ 门禁生效 / 配了不生效」的同型假绿。并行会话自带的 `_check_ai_native_calibration.py` docstring 亦明确「本脚本不判定飞轮已接线到生产链路」，两者结论一度矛盾。

| 修正 | 内容 |
|---|---|
| 三要素判据 | `FLYWHEEL_DOWNSTREAM` 改为**实现类 + 盲标样本源 `BlindLabelCalibrationSamples.from(` + 校准计算 `AiConfidenceCalibration.evaluate(` / `expectedCalibrationError(` / `brierScore(` / `challengerAdmissible(`** 三者同时成立才 SATISFIED |
| 排除伪引用 | import 行、javadoc `{@link}`、`//`/`/*` 注释、单测引用一律不算生产接线；扫描根缺失 fail-closed |
| 精确归因 | 计算调用是飞轮定义性动作，优先归因 `CALIBRATION_COMPUTE_NOT_WIRED`；其次 `SAMPLE_SOURCE_NOT_WIRED`、`CALIBRATION_CONSUMER_MISSING` |
| 回归钉子 | 新增 `scripts/tests/test_ai_native_readiness_flywheel.py`（7 例，临时源码夹具、不改真仓库）：import-only / javadoc-only 不得翻绿，真实调用与三要素齐备必须翻绿，扫描根漂移必须判红 |
| 变异验证 | M1 恢复旧判据（import 即算）⇒ 1 例转红；M2 去掉注释行过滤 ⇒ 1 例转红；还原后 7/7 OK。`--self-test` 现 7 个探针全翻转（新增「摘除实现类 / 样本源 / 计算调用」三探针） |

**前后端 E2E 增加循环收敛对账（C 组，3 条断言）**：`bone-frontend/e2e/synap-claim-e2e.cjs` 调用复核器解析 `l3Claimable` 与 9 项状态，与运行期 `ai-readiness` 端点的 `deploymentGatesPassed` 对账；任一端单独翻绿即判红，并用「把输出伪造成 `l3Claimable=true`」做负向自检证明对账判据有判别力。产物 `outputs/claims-e2e-result.json` 新增 `runtime.readinessSnapshot`（含 7 个 BLOCKED 判据 ID）。**实测 15/15 PASS**，网关经业务路径 `POST /api/v1/iam/login` 200 就绪（不用聚合健康度）。

**本轮实测存档** `outputs/ai-native-readiness-2026-11-06.txt`：判据 9 项 `SATISFIED 2 / BLOCKED 7 / RUNTIME_ONLY 0`，当前可判定成熟度 **L0**，`l3Claimable = false`；运行期 E2E 证据 `nodes=88 / aiParticipated=0 / participationRate=0 / capabilityMissing=75`。

**本机真实推理条件实测全无**：`ollama` 未安装、11434/8000/1234 等本地推理端口无监听、Python 推理栈（torch/transformers/onnxruntime/llama_cpp/openai/vllm）全缺、无 >100MB 模型文件、`BONE_AI_GATEWAY_BASE_URL` 等 provider 环境变量为空。故 `PROVIDER_CONFIGURED`、`PERCEPTION_MODEL_DRIVEN`、`RUNTIME_MODEL_COVERAGE` 本轮**物理不可能达标**，只能由采购/密钥/算力与真实流量闭环，绝不以 mock 或改配置伪造。

**门禁权属登记**：`scripts/check.sh` 属 AGENTS.md **L3（CI 脚本需架构师审批）**，本轮未接线；`_ai_native_readiness.py` 与 `_check_ai_native_calibration.py` 定位为**主动循环复核工具**（由复核流程显式运行），不是每次提交的阻断门禁，故不把它们计入 L3 阻塞项，避免制造误报。若后续要门禁化，须走架构师审批并按 `scripts/gate-liveness-allowlist.json` 登记。

> 循环结论不变：7 项 BLOCKED 中 5 类属外部条件或组织审批，2 项须真实盲标样本与真实流量。**继续编码可增强评测骨架与护栏，但不会让 `l3Claimable` 变 true**；判据本身也必须接受防假绿审查——判据变松比能力没进展更危险。

#### ⑩ 循环复核第 5 轮（v2.8）：业界实践对账与「治理下限不可绕过」缺陷

**本轮方法**：用户两次强调「基于业界最佳实践」，故先把复核器 9 项判据与权威实践逐项对账，再按缺口选一个**可编码、可测、可 E2E 验证**的落点，而不是继续加护栏。

| 业界实践 | 核心主张 | 本系统既有覆盖 | 本轮处置 |
|---|---|---|---|
| Google《Rules of ML》#1/#2 | 先定指标与非 ML 基线，再谈模型 | G0 基线判据、不套用竞品数字、审批门禁 | 无缺口 |
| Google《ML Test Score》(Breck 2017) | 每条测试是可执行 assertion；四类取**最低分**封顶；按重要切片测模型质量 | `EVAL_GATES_WIRED`、冻结集、`CalibrationReport` 切片统计 | **补**：治理下限写成 8 例 assertion + 3 条运行期 E2E |
| Sculley《Hidden Technical Debt》 | fragile configs / undeclared consumers / 配置债 | 单一配置源、能力声明 `available=false` | **补**：阈值下限不得由 HTTP 参数改写；生效值回传可审计 |
| NIST AI RMF 1.0 | validity & reliability、可解释、问责 | 决策轨迹可回放、模型卡、拒赔人工确认 | 仍缺**回滚演练**的静态判据（登记，不伪造） |
| Anthropic《Building Effective Agents》 | workflow 优先，先简单后复杂 | 固定 5 节点 DAG、复杂度路由、不造自治 Agent | 无缺口 |
| 生产 RAG / 保险 STP | 评测先行、HITL、渐进放量 | 独立盲标、放量阶段机、错误预算 | 语料 / provider 属外部前置 |
| ADR-0046（并行会话，本轮新增） | 字段级可信度、置信度非唯一闸门、七层管线、Jev 非最终判定器 | 文档状态「已接受」，明写**暂无代码落地** | PoC 与 OCR 评测报告属外部前置 |

**缺陷 1（真实、可复现）**：`AiCalibrationReportController` 把 `minSamples` / `minSamplesPerSlice` 两个 query 参数**原样透传**给服务，而缺省治理下限是 300/30。传 `?minSamples=1&minSamplesPerSlice=1` 即可让 1 条盲标样本产出 `THRESHOLD_PROPOSED` 阈值建议，绕过「样本不足禁止重定阈值」的防假绿闸。既有单测甚至把 `service.report(1, 2)` 固化为「请求参数缺省」特性。

| 修正 | 内容 |
|---|---|
| 边界守卫 | `guardedMinSamples(requested, floor)`：null / 0 / 负数 / 低于下限一律钳制到治理下限；**只允许上调**。守卫加在对外只读端点，领域内核仍允许任意 minSamples 供离线实验（分层不越界） |
| 可观测性 | `AiCalibrationReport` 回传 `effectiveMinSamples` / `effectiveMinSamplesPerSlice`，让「生效的治理参数」可被审计与端到端验证，而非埋在代码里（Sculley：配置债） |
| 测试 | 新建 `AiCalibrationReportGuardTest` 8 例：下界钳制、上调保留、切片下限独立、端点真实调用下限、1 条样本不得产出阈值建议、回传字段非硬编码 |
| 变异验证 | 守卫改为「允许下调」⇒ **4 例转红**（含 `Expecting value to be false but was true` 的 1 条样本产出建议）；还原后 12/12 全绿 |

**缺陷 2（我自己造的假绿）**：E2E 首版负向断言只检查「`minSamples=1` 不产出 `THRESHOLD_PROPOSED`」，但 dev 库无盲标样本时 `NO_EVIDENCE` 会短路——**守卫在不在都会通过**。改为断言生效下限回传值（默认 300/30、`minSamples=1` 仍 300/30、`500/50` 如实回传），判据才有判别力。

**缺陷 3（过期证据）**：C 组收敛对账调用复核器时，读到的是**磁盘上上一轮** E2E 产物；一次失败产物使输出从 `2/7/0` 漂到 `2/6/1`。改为 C 组前先落盘本轮 `runtime` 中途快照（末尾写完整结果、无 `provisional` 标记），对账恒用本轮证据。

**部署态踩坑（可复用）**：源码已有校准报表端点，但 8087 跑的是旧 jar ⇒ 经网关 404（与 §1.7 ⑧ 的网关 jar 同族）。重启时三次踩坑：① `lsof -ti tcp:8087` 不带 `-sTCP:LISTEN` 会把客户端 fd 算进去，误判「端口未释放」；② 旧进程对 SIGTERM 不退出，须 `kill -9`；③ `PIDS` 变量带尾随空格导致 zsh `kill: illegal pid`。**判据一律用 `lsof -ti tcp:8087 -sTCP:LISTEN` + 业务路径 HTTP 200**。重启期间网关熔断短暂触发，服务恢复后自动 half-open 复原，无需重启网关。

**本轮实测**：Java 相关 **12/12**（8 新 + 4 既有）；相关测试变异 4 红→还原全绿；前后端 E2E **19/19 PASS**（A 11 / C 3 / B 5），新增校准报表 4 条运行期断言；运行期 `effective=300/30`（旁路尝试）/ `500/50`（显式上调）/ `verdict=NO_EVIDENCE`；机器判据仍 **SATISFIED 2 / BLOCKED 7 / RUNTIME_ONLY 0，L0，`l3Claimable = false`**。

> 本轮补齐的是**治理与可验证性**（L3 的必要条件），不是模型能力本身。7 项 BLOCKED 仍全部存在；把文档改成 current、把 draft 转正、或硬编码覆盖率，只会制造假绿。

#### ⑪ 循环复核第 6 轮（v2.9）：可编码缺口收口——三判据翻转与判据面同步扩展

**本轮落地（全部 TDD 先红后绿、逐提交、门禁 59/59）**：

| 判据 | 落地内容 | 三要素证据 |
|---|---|---|
| `FLYWHEEL_DOWNSTREAM` | 校准生产消费方：`BlindLabelTaskRepository.findUnblindedPaired`（UNBLINDED+PAIRED 样本源，SQL 资源文件非 DDL）→ `BlindLabelCalibrationSamples`（解析失败逐条计数不编造，channel 切片缺省 GLOBAL）→ `AiCalibrationReportApplicationService`（只读，`AiConfidenceCalibration.evaluate` 静态调用）+ `GET /api/v1/claim-ops/ai-calibration/report`（`@PreAuthorize` + `ApiResponse`）；治理下限 300/30 只许上调（承接 ⑩ 缺陷 1 的守卫） | 实现类 + 样本源生产调用 + 校准计算生产调用，import/注释/单测不算接线 |
| `IMAGE_PARSE_WIRED` | 感知层图像通道：`LlmMessage.imageUrl` + `OpenAiCompatibleClient` content-parts 序列化（网关多模态最小扩展）；`DocumentParsePort.parseImage` default 方法扩展（既有适配器零改动）；`ImageDocumentParsePortAdapter`（`@ConditionalOnProperty` 默认 false，复用 PiiMasker/SafePromptBuilder/LlmOutputGuard，文本层委托基线适配器）；`EvidenceApplicationService.parseImage`（!available→FAILED / !complete→MANUAL / 否则 DONE，归因码逐字透传） | 适配器类 + `BONE_CLAIM_DOCUMENT_PARSE_IMAGE_ENABLED:false` 占位 + `IMAGE_MOCK_REJECTED`/`IMAGE_GATEWAY_UNAVAILABLE`/`IMAGE_OUTPUT_INVALID` 归因码 |
| `RAG_WIRED` | 知识层受控 RAG：pom 增 `bone-ai-rag`（L3 依赖，已获批准）；`RagKnowledgeRetrievalPortAdapter`（目录准入 fail-closed 主路不变，仅「目录未收录且有检索词」走语义支路；引用强制——无 `clauseRef` 结果丢弃并计数；`ClaimRagScanConfiguration` 门控开才装配 `com.bone.airag`）；`KnowledgeBasis` 增 `RAG_CLAUSE_CITED`/`RAG_CORPUS_MISSING`/`RAG_RETRIEVAL_UNAVAILABLE` 二级归因（与 `AiDegradationReason` 分工：一个答「模型为什么没跑成」、一个答「知识层为什么判不了」） | 适配器类 + `BONE_CLAIM_KNOWLEDGE_RAG_ENABLED:false` 占位 + `RAG_CORPUS_MISSING`/`RAG_RETRIEVAL_UNAVAILABLE`/`clauseRef` 成因码 |

**判据面同步扩展（复核器自身也接受防假绿审查）**：`tools/_ai_native_readiness.py` 新增 `IMAGE_PARSE_WIRED` / `RAG_WIRED` 两判据，与 `FLYWHEEL_DOWNSTREAM` 同为三要素结构（实现类 + 门控默认关 `${ENV:false}` 占位 + 降级码齐全），新增 `gate_default_false` 辅助函数（缺键/默认 true 视为未接线或行为变更）；`--self-test` 负向探针扩至 **12 个**（新增图像/RAG 接线双向翻转、门控默认值改 true、mock 拒绝码移除），全部发生状态翻转。

**本轮实测存档** `outputs/ai-native-readiness-2026-11-05.txt`：判据 **11 项 `SATISFIED 4 / BLOCKED 7 / RUNTIME_ONLY 0`**（较上轮 `2/7/0` 翻转 2 项，加上轮已翻转的 `FLYWHEEL_DOWNSTREAM`），当前可判定成熟度 **L0**，**`l3Claimable = false`**；运行期 E2E 证据 `nodes=88 / aiParticipated=0 / participationRate=0`。

**诚实结论**：4 项 SATISFIED 全部是「护栏与评测骨架」类判据（评测门禁接线、飞轮下游消费方、图像/RAG 受控接线）——它们让「能力缺失时可归因、可证伪、默认配置不变现网行为」，但**没有让模型处理过任何一张真实票据**。7 项 BLOCKED（provider/GPU、非 ML 基线、冻结集、感知模型驱动、目录准入策略、Go-Live 分册、运行期参与率）仍全部属外部条件或组织审批，与 §1.7 ③ 前置清单三方一致。**继续编码不会让 `l3Claimable` 变 true**；阈值建议只在盲标样本 ≥300 且签核后进入配置（AI 不碰钱）。

## 2. §11 各节变异验证与边界记录

### §11.11 放量治理增强

**变异验证（3 次，均字节级还原后重跑全绿）**：① 删 `completePreExam` 调用 ⇒ 5 例转红；② `ROUTED_TO_ADJUST` 分支只改返回码不真 `escalate` ⇒ 4 例转红；③ 反向——去掉「已推进则跳过」闸门 ⇒ 恰 2 例（`alreadyInAdjustQueueIsUntouched` / `pendingPreExamIsNotSilentlyStarted`）转红，说明闸门不是装饰。

### §11.12 渐进放量阶段机

**变异验证（6 次，均还原后复跑全绿）**：① 删渠道白名单闸 ⇒ 恰好两层 `channelScopeFailClosed` 红；② 缺失渠道放行（null 改放行）⇒ 同一用例红；③ 删生效窗闸 ⇒ 恰好只红 `scheduledWindowInactive`（策略层 `isEffective` 用例不受牵连——闸在服务层，判据分层正确）；④ 桩判成 MODEL ⇒ 溯源 2 例红；⑤ 忽略降级 ⇒ 溯源 2 例红；⑥ null 置信度不强制 UNKNOWN ⇒ 1 例红。

### §11.13 独立盲标

**边界**：盲标队列的**标注工作台前端**与校准曲线（ECE/Brier）重算未落地——本节交付的是队列结构与闸门，标注人在独立界面看原始票据影像的流程属交付排期；同案隔期重标允许（规则版本升级后复测），但同一时刻至多一个未解盲任务。

