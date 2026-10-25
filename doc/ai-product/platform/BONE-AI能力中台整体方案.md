# BONE AI 能力中台 · 整体方案

> **版本**：v1.16（2026-10-24，归口：平台架构组）｜本文是 Bone AI 能力中台**唯一方案真源**：模块边界、框架选型、许可红线、落地批次以本文为准。业界对位见附录 B。⚠️ 阈值初值、DDL、API 面与任务分解见《BONE-AI能力中台详细技术方案》（详设），总案不硬编码阈值。
> **v1.16 变更（诊断复核 §7.4 剩余缺口二/三闭环）**：① **faithfulness 语义级评测落地**（详设 §2.7 / RAG 方案 v1.10 §11 B8）：双层判官——rag 确定性 `HeuristicAnswerQualityJudge`（CI 门禁，`RagAnswerGoldenSetEvalTest` 回答级 golden-set 含幻觉/半据半编阴性对照，`mvn clean verify` 强制）+ app `LlmAnswerQualityJudge`（走 `JudgePort` 网关计量，opt-in 语义校准）+ REST `POST /api/ai/v1/eval/judge/answer-quality`；`AnswerQuality` 三项不变量 + fail-closed（空上下文/空答案/输出畸形 0 分）。② **pgvector 索引化设计闭环**（RAG 方案 v1.10 §14）：`VectorSearchPort` 抽象 + 四阶段迁移（双写→回填→影子→切流退役）+ 验收闸门（recall@10≥0.98 / P99 / 构建耗时）+ fail-closed 回退预案；实现待 R4 排期（批二放量前置）。③ §4.5 向量库行、§5 C7 评测行、§6 现状基线与第二批出口同步。
> **v1.15 变更（企业复杂场景诊断优化落地，承接《方案诊断复核-2026-10-24》P0/P1/P2）**：**类型护栏由「目标态」转「已机械强制」**——`AiSuggestion`/`Confirmed` 两类型在 `bone-ai-orchestration` 落地 + ArchUnit 规则 6 拦截「AI 建议越出决策包」，`HitlItem.toConfirmed()` 为业务侧唯一确认产物入口（§3.2/§5）；**RAG 文档级 ACL 默认改 fail-closed**——未接入 ACL 实现即拒绝检索（`DenyDocAcl` 空可见集），不再放行全租户，接入方 ACL 适配由 L3 可选**升为 Go-Live 硬依赖**（§4.5/§6）；**写入侧 PII 闭环**——ingest 字段级等长掩码（身份证/手机号/银行卡/邮箱，span 锚点不漂移）默认开（§4.5）；**RAG 规模水位下调**——`max-scan` 默认 10000→3000（实测安全水位 2000~5000 取中偏上），pgvector+ANN 由「不为想象规模预付」改判**第二批前启动**（§4.5/§6）；**集中式网关触发条件修订**——三产品共享 provider 即已满足「跨产品集中配额审计」现状，配额/密钥/成本权威态提前收口 `bone-ai-app` 宿主（§4.7）；**可观测接线**——`MicrometerLlmMetrics` 宿主适配器落地，`LlmMetrics` 发射点自此有真实消费方（§4.1/§5）；**威胁模型决策**升为上线前硬依赖并归档决策记录（§6/§7）。
> **v1.14 变更（置信度分档对齐详设，承接《方案诊断复核-2026-10-23》F1）**：§2.1 C3 置信度分级改为与详设 §4 #3 同构——**0.85 = 自动放行线**（置信度 ≥0.85 **且** 金额 ≤ 阈值 **且** AI 可用 → 自动通过，任一不满足转人工，非纯「直出」）；**0.7 = HITL 待裁列表展示线**（<0.7 强制在列、不得直出）；删中间「0.7–0.85 复核」档（无抽样机制前不单列操作）。消除总案与详设跨文档矛盾。
> **v1.13 变更（RAG 诊断 P1 项落地，见详设 v1.6 与《RAG方案诊断与优化-2026-10-23》）**：§4.5 新增三行——**文档级 ACL seam**（`RagAclPort` 端口 + 默认 `NoDocAcl` 兜底 + 无 ACL 归属 403 fail-closed，接入方 ACL 适配为 L3 硬依赖）、**MMR 多样性选取**（默认关闭、λ 约定防误配、按评测集校准）、**降级可观测**（`RagEventSink` 端口 + 响应 `degradations` 痕迹码，trace 适配留 L3）；§6 现状基线 rag 行同步，查询改写记决策（批二启动，暂不建零消费方 machinery）。
> **v1.12 变更（RAG 接线修复，见详设 v1.5 与《RAG方案诊断与优化-2026-10-23》）**：§4.5 rerank / Embedding 行订正为**按 `base-url` 条件装配**（此前 `HttpBge*Client` 与 `RagInferenceProperties` 零接线 / 零注册，生产形态恒为「过渡哈希 + 无精排」而与方案描述不符）；新增「检索护栏」行（`max-scan` fail-closed + `min-score` 过滤 + `doc_version` 幂等批键）；§6 现状基线 rag 行同步。降级口径：provider 故障读路径 WARN + 本域内降级（BM25 单路 / RRF 粗序），严禁切云端辅路。
> **v1.11 变更（生产硬依赖代码落地，见详设 v1.4）**：三项硬依赖已由「待建」改判**代码落地**——① 多副本 Redis 分布式配额（`RedisTenantQuotaGuard`，Redisson 固定窗口 + 故障 fail-open，`@ConditionalOnClass` 守卫零新增必选依赖）；② 密钥启动 fail-fast（未解析 `${}` 占位符即启动失败）；③ Actuator liveness/readiness 探针分组；另 trace 回放 / rules 管理 REST 面已在 `bone-ai-app` 落地（附录 A #15）。剩余 Go-Live 硬依赖收敛为：Virtual Key 接入、告警规则、故障演练记录、威胁模型决策。
> **v1.10 变更（生产就绪强化）**：§6 增加「上线判据」——切生产流量前须过详设 §6 生产就绪清单六域与 Go-Live 判据（硬依赖：密钥托管 / 多副本 Redis 配额 / 健康探针 / 告警规则 / 威胁模型决策 / 故障演练）。
> **v1.9 变更（诊断后修订，见《方案诊断报告-2026-10-23》）**：模块结构对齐 `bone-ai/` 11 模块真值（notification/integration/ledger 由「扩展存量」改判「新建」并补决策记录，新增 C11 额度台账）；`bone-ai-app` 定位正名（后端 REST 聚合宿主，非前端子应用）；撤回 `WorkflowMetadata` 复用完成态表述；证据链口径统一（真实调用留证、短路/缓存命中不留证）；现状与批次重基线；L3 清单补全 8 张新 DDL 与聚合宿主部署形态。

## 0. 一句话结论（TL;DR）

1. **沉淀机制、留住资产**：跨域同构的**机制**进平台（解析 / 双引擎编排 / HITL / 留痕 / 网关 / 检索 / 评测 / 安全 / 预警 / 交付），领域**资产**留产品（规则库 / 图谱 / 估值引擎 / 快照库）。平台只做"证明与治理"，不做"判断与决策"。
2. **怎么建**：新建 `bone-ai/` 平行聚合（**11 模块**：gateway / orchestration / trace / rules / document / rag / notification / integration / ledger + 后端聚合宿主 bone-ai-app + 参考宿主 bone-ai-server；评测能力挂发布流水线，不建模块），只补空白与治理面；一期**不引 Spring AI 与重型 Agent 框架**——自建 OpenAI 兼容客户端 + `LLMClient` 门面、显式状态机编排、规则 DSL = QLExpress 唯一（表达式追踪服务"判定路径可回放"；存量表达式执行为 SpEL，属计算域非规则引擎）。
3. **怎么落**：治理靠结构强制（网关唯一入站 + 无证据拒返 + `AiSuggestion`/`Confirmed` 类型隔离【**v1.15 已机械强制**】+ ArchUnit 门禁）；POC（第 0 批）→ 三批次垂直切片，每批有出口判据与机械门禁；许可排雷前置（SBOM + CVE 扫描）。

---

## 1. 背景与结论

procuraAI / SynapClaim / Synap Equity 三产品方案独立设计，收敛出 10 项高度同构能力（8 项三方案同构、2 项两方案同构），连选型都重复（规则热更、混合检索 + rerank、三元组快照、HITL 五态、评测集 + 双人 Kappa）。这是业界 AI 演进阶段二的典型触发信号（多业务线重复建设同类能力时，RAG / 路由 / 评测 / 治理自然沉淀为平台层）；三产品均为"建议 + 人工裁决"形态，定位在阶段二起点，不做阶段三的 Agent 自主写操作。

不沉淀的后果：① 重复建设；② 合规能力（可复现 / 可回放 / 人工终审 / 数据不出域）各自拼装、各自面对审计；③ 成本红线 / 证据链 / PII 脱敏散落调用点必被绕过——治理必须结构强制。

### 1.1 家底核查（源码实证，明细见附录 A）

BONE 存量比预期厚，真正空白只有五处：**LLM 运行时、工作流执行器、持久化证据链、文档解析 IR、RAG / 评测**。截至 2026-10-23，11 个 `bone-ai-*` 模块均已落地最小可用版（主代码 + 单测 + 架构测试；gateway / trace 最小版于 2026-10-14 首批落地），多节点状态机、pgvector 终局、OTel 打点、前端确认台为目标态（详设 §5 现状基线）。存量资产结论：

| 存量 | 结论 | 方案影响 |
|---|---|---|
| `AiMetadata` / `AgentMetadata` | 纯配置 POJO，无运行时 | 建模归 engine，运行时新建 gateway |
| `ExtensionDecisionRecorder` | 内存版，无持久化 / 租户 / 版本 | 升级为持久化证据链 trace |
| 规则执行（`RuleEngine` + `BusinessRuleEngine` 族） | 执行齐全，治理缺失 | `bone-ai-rules` 只做治理面（版本 / 灰度 / 回归），不造执行引擎 |
| `WorkflowMetadata`（状态 / 转换齐全） | 有模型、无执行器；**orchestration 未接线该模型**（代码零引用，顺序流水线执行器已落地） | `WorkflowMetadata` 驱动的多节点状态机（含 `FALLBACK_NODE`）为**目标态**；「补执行器」实际排期应按「建状态机」估，不按「接现成模型」估 |
| `bone-file`（MinIO + Controller + JWT + 租户） | 存取齐全但单 jar 含 Web 层 | document 复用其规范、走 Port，不进程内依赖 |
| `bone-integration` / `bone-notification` | Flow / Outbox / alert 域齐全 | C9 / C10 落扩展，不新建 |
| Aviator 5.4.1（LGPL） | `AviatorExpressionEvaluator` 零消费者、零测试（默认与硬编码路径均为 SpEL，代码实证） | 死实现整体删除（类 + optional 依赖 + dependencyManagement），移出选型 |

---

## 2. 沉淀边界

### 2.1 可沉淀能力（C1–C10）

| # | 能力 | 沉淀内容 | 优先级 |
|---|---|---|---|
| C1 | 文档解析与 IR | 统一 IR（章节树 / 表格 / 条款索引 / span）+ OCR / 表格管线 + 语义切片 + 脱敏回填；引用必须定位原文。**最小 IR（存量 PDFBox/Tika 条款切片 + span 定位）第一批先用，完整管线第二批** | P0 |
| C2 | 双引擎与编排 | 规则 DSL（JSON + 表达式 + 沙箱热更）+ 版本 / 灰度 / shadow + 显式状态机（排队→解析→规则→LLM→确认→归档）+ fallback 降级矩阵 | P0 |
| C3 | HITL 确认与回流 | 条目化建议（引用 + 依据 + 建议值）+ 置信度分级（**0.85 = 自动放行线**：置信度 ≥0.85 **且** 金额 ≤ 阈值 **且** AI 可用 → 自动通过，任一不满足 → 转人工；**0.7 = HITL 待裁列表展示线**（<0.7 强制在列、不得直出）；中间「0.7–0.85 复核」档在无抽样机制前不单列操作，见详设 §4 #3）+ **五态**（PENDING + 三裁决 + ESCALATED）+ `reason_type` 回流 + 按风险分派裁决人 | P0 |
| C4 | 快照与审计追溯 | 输入哈希 + IR + 引擎版本三元组 + 不可变档案 / 版本链 + 全量留痕 + 回放 | P0 |
| C5 | LLM 网关 | 模型档位路由 + 租户配额 / 降档 + 调用审计 + 灰度 + 成本红线强制 | P1 |
| C6 | 受控 RAG | 条款级切片 + 混合检索（向量 + BM25 + RRF）+ rerank + 强制带出处、无依据不编造 + 增量更新 | P1 |
| C7 | 评测与门禁 | 评测集（标注指南 / 双人 Kappa / 回流重标）+ 指标字典 + CI 门禁 + 对抗评测 + LLM-as-Judge（省 60–80% 标注量）。**faithfulness / answer_relevance / context_precision 三项已落地（v1.16）**：rag 侧确定性判官 + 回答级 golden set 门禁（含幻觉 / 半据半编阴性对照）+ app 侧语义判官 + REST 判官端点；挂发布流水线，不建模块 | P1 |
| C8 | LLM 安全基线 | OWASP LLM Top 10 对表 + 分隔符封装 / 输入校验 / 输出溯源 / 工具最小特权 / 对抗样本库 / 模型 SBOM / 数据分级 | P1 |
| C9 | 提醒预警 | 规则触发（阈值 / 边界 / 时点）+ 多级预警 + 触达 ack + 消警闭环 + 漏报率评测。**落 `bone-ai-notification` 新建模块**（2026-10 改判：AI 域告警契约需独立稳定，且与存量 bone-notification 的 Flow/Outbox 域不相干；Webhook 出站 + HMAC 签名 + 消警守卫，零新第三方依赖） | P1 |
| C10 | 集成适配与三态灰度 | 适配器注册表 + 只读旁路优先 + 幂等三件套 + Shadow→Assisted→Automated 三态 + 审计三件套。**落 `bone-ai-integration` 新建模块**（2026-10 改判：SSRF 守卫 / HMAC 回调校验 / 三态模式属 AI 出站治理面，与存量 bone-integration 的内部集成域边界不同；出站走 JDK `HttpClient`，零新依赖） | P2 |
| C11 | 额度台账（控额内核） | 多维限额声明 + 两阶段预留（reserve/commit/release）+ 冻结解冻 + 编码化结论（`available = limit - consumed - reserved - frozen`，未登记上限 fail-closed 为 0）。从 pk-tpa-saas 控额引擎抽取的**域中立确定性内核**，保险语义（维度定义 / 免赔 / 后付责任）留产品域。落 `bone-ai-ledger`（进程内 `InMemoryLimitLedger` 默认，多副本以 `@Bean` 覆盖共享存储） | P0 |

> **改判记录（v1.9）**：C9/C10 原定「落扩展（bone-notification / bone-integration），不新建」。实现阶段改判为新建 `bone-ai-notification` / `bone-ai-integration`——理由延用 §3.1 独立聚合三理由（依赖单向可 ArchUnit 强制、契约独立版本线、许可隔离面收敛），且存量两模块域语义（内部集成 / 消息通道）与 AI 出站治理面错位。C11 为 v1.9 补录：ledger 内核先于本文档落地，现补入能力表与模块结构，排期与 C2/C4 同批考虑（控额是理赔 / 采购资金域共性机制）。

### 2.2 不沉淀：领域资产

procuraAI（法规规则库 R01–R25、三一致校验、供应商画像）、SynapClaim（资金状态机、理算引擎、医学图谱、决策图模板）、Equity（PE/PB/DCF 引擎、财务体检、时点快照库）留在产品侧。

**判断标准（一条）**：跨域同构的工程机制进平台，领域知识与数据资产留产品。理由：① 资产是壁垒（2–3 年积累，模型可买、数据难买），吞进平台拖慢领域迭代；② 平台能力契约化暴露，智能体外置隔离（Clean Core）。

---

## 3. 总体设计

### 3.1 模块结构

```
bone/
├── bone-ai/                          # 新增平行聚合（与 bone-engine / bone-platform 同级，11 模块）
│   ├── bone-ai-gateway/              # C5+C8：LLM 运行时门面 + guardrail（模型调用唯一出口）
│   ├── bone-ai-orchestration/        # C2 执行 + C3 HITL 子域（顺序流水线已落地；WorkflowMetadata 状态机为目标态）
│   ├── bone-ai-trace/                # C4：持久化证据链
│   ├── bone-ai-rules/                # C2 治理：版本 / 灰度 / 回归 + AI 规则语义（不造执行引擎）
│   ├── bone-ai-document/             # C1：解析 + IR + 脱敏（PDFBox 默认 + Docling sidecar 适配器就位）
│   ├── bone-ai-rag/                  # C6：混合检索 + 引用强制 + 权限隔离（最小全链已落地：容量护栏/双路/RRF/rerank 条件装配/doc_version 幂等）
│   ├── bone-ai-notification/         # C9：AI 域预警触达 + 消警闭环（2026-10 由「扩展存量」改判新建）
│   ├── bone-ai-integration/          # C10：AI 出站适配 + 三态灰度 + 回调校验（2026-10 由「扩展存量」改判新建）
│   ├── bone-ai-ledger/               # C11：多维额度台账 + 两阶段预留（域中立确定性内核）
│   ├── bone-ai-app/                  # 后端 REST 聚合宿主：HITL/评测/RAG/预警/集成统一 HTTP 面 + Actuator（非前端）
│   └── bone-ai-server/               # 参考宿主（blueprint 风格 demo + 端到端集成测试，非生产拓扑）
├── 扩展（不新建）：bone-metadata-engine（建模语义）、bone-extension-sdk（表达式 SPI）
│  （前端确认台 qiankun 子应用拟落 bone-frontend/、建议端口 3013——批三目标态，当前 bone-frontend/apps 尚无此应用）
```
（C7 评测不建模块：评测集 / 指标字典 / CI 门禁为发布流水线资产，judge 走网关计量；评测管理 REST 与仓储落 bone-ai-app）

**部署双形态（v1.9 明确）**：主形态 = 产品服务进程内嵌库（零额外跳，`bone-ai-server` 为接线模板）；辅形态 = `bone-ai-app` 聚合宿主（AI 域 REST 面集中暴露，供确认台 / 跨语言消费方 / 运维面）。聚合宿主**不是第二份编排真源**——实现仍是那批库；其出现不改「产品侧零跳直连」，也不构成总案 §4.7「库→集中式网关」演进触发条件的满足（触发条件看非 JVM 调用方 / 跨产品集中配额审计 / 供应商 ≥3）。

独立聚合三理由：① AI 是平台级能力维度，需物理边界 + 独立版本线；② 依赖单向（`bone-ai-*` 只依赖 framework / core / sdk，产品依赖 ai），ArchUnit 可强制"产品不直连 AI 实现、AI 不碰产品域"；③ 许可隔离面收敛。

### 3.2 职责与依赖（无环）

| 模块 | 复用存量 | 边界 |
|---|---|---|
| gateway | `bone-security`（脱敏）、`bone-core`（租户） | 模型调用唯一入站；成本红线、PII 出口脱敏（默认开）、租户配额、场景路由降级、输出内容安全、审计平台强制 |
| orchestration | `WorkflowMetadata` 模型（目标态接线） | 顺序流水线执行器已落地（规则预检→短路→LLM→`DecisionGate`）；经门面（`LlmGatewayApplicationService`）依赖网关，禁 import 其 infrastructure；HITL 子域（五态 + `reason_type`）内嵌本模块 |
| trace | `TenantAbstractEntity`、`ExtensionDecisionRecorder`（升级） | 三元组快照 + 版本链 + 回放 API（进程内；REST 面露暴随 bone-ai-app 控制面补）；留存时长与分区策略集中配置，framework 只供基类与常量 |
| rules | `RuleEngine` 触发 / 编排 / 元数据装载；expression SPI；版本机制**自建**（`MetadataVersionController` 零消费方、规则级兼容检查为空实现，bone-ai-rules 亦无 engine 依赖，「复用存量版本机制」不成立） | 只做治理面，不造执行引擎；QLExpress 求值自建（存量 RuleEngine 走 SpEL，不执行 QLExpress） |
| document | bone-file 存取规范 | PDFBox 默认管线 + Docling sidecar 适配器就位（默认关）；OCR / GPU 重依赖与业务隔离部署 |
| rag | trace（溯源）、bone-core（租户隔离） | 语料归领域，检索与生成控制归平台；引用强制 fail-closed（缺 `clauseRef`/span 非法即丢弃） |
| notification | 无（新建） | AI 域预警：规则触发→多级预警→触达 ack→消警闭环；Webhook 出站 + HMAC 签名 |
| integration | 无（新建） | AI 出站治理：外部系统适配 + SSRF 守卫 + HMAC 回调校验 + Shadow/Assisted/Automated 三态 |
| ledger | 无（新建，内核抽自 pk-tpa-saas 控额引擎） | 域中立额度台账：多维限额 + 两阶段预留 + 冻结解冻；`InMemoryLimitLedger` 默认，多副本 `@Bean` 覆盖 |
| app（宿主） | 以上全部库 | 聚合 REST 面（HITL/评测/RAG/预警/集成）+ 评测管理 + Actuator；不含领域逻辑 |

```
gateway → bone-security / bone-core
trace → bone-core + bone-ai-gateway（订阅 LlmCallRecorded，唯一事件落库方；另订阅 LlmUsageRecorded 落计量）
rules → bone-metadata-engine + extension-sdk + sdk
document → gateway + FileStorage Port（走 MinIO 规范直连或 HTTP，不进程内依赖 bone-file 单 jar）
orchestration → gateway + rules + trace + metadata-engine（经门面，禁直连 infrastructure）
rag → trace + gateway + 向量库（§4.5）
notification / integration / ledger → bone-core（+ ledger 无 AI 依赖，域中立）
app → 上述全部 bone-ai-*（装配宿主，不新增领域逻辑）
产品域 → bone-ai-* + integration + notification
```

**设计约束（§5 门禁实现）**：① 分层铁律——确定性内核（规则 / 计算 / 资金状态机）永远在 domain / 产品侧，AI 模块只给"建议 + 证据"，不做最终裁决；② 类型护栏——AI 结论一律 `AiSuggestion<T>`（置信度 + 证据引用），资金 / 责任 / 数值判定入口只收 `Confirmed<T>`（HITL 裁决或规则产出），ArchUnit 禁止 `AiSuggestion` 越出 `domain.decision` 包（**v1.15 已机械强制**：两类型 + ArchUnit 规则 6 + `HitlItem.toConfirmed()` 落地，接入文档可宣称「编译期 + 架构门禁双重拦截」）；③ 检索边界——向量库实例形态可推迟（§4.5），但切分 / 权限过滤 / 引用强制 / RRF / rerank 策略留 rag，gateway 只做调用代理、不下放检索决策。

### 3.3 HITL 落点与状态契约

HITL 不单列服务，是 orchestration 内嵌子域（确认 API + 置信度分级 + **五态** + `reason_type` 回流 trace），其 REST 面由 `bone-ai-app`（后端聚合宿主，`HitlController`）暴露；前端确认台（qiankun 子应用，权限/组织复用 bone-iam）为批三目标态（`bone-frontend/apps` 尚无此应用）；合规硬门槛：195 号文“辅助性定位”与人责归属。AI 到“待人工”即停在编排收敛节点等待裁决；不自造审批流、不把主编排托管给 Camel Flow（避免第二份流程真源）。

| 状态 | 语义 | 回流 |
|---|---|---|
| `pending` | 待裁决；<0.7 强制进入，不得直出 | —（等待裁决，不回流） |
| `confirmed` | 建议值生效，进下一节点 | trace 记操作人 + 时间 |
| `rejected` | 建议值作废，走驳回分支 | trace 记 `reason_type` + 备注 |
| `modified` | 改判值生效，作纠错样本 | trace 记 `reason_type` + 原值 vs 改判值 diff |
| `escalated` | 超时 / 超轮触发，转人工全程处理 | trace 记升级原因 + 转交记录，不参与常规回流 |

`reason_type` 受控字典（`RULE_MISS` / `CONTEXT_MISS` / `SUGGESTION_WRONG` / `POLICY_CONFLICT`）是回流与评测扩充唯一入口，自由文本不进回流管道。

**与业务状态机分层**：HITL 五态是裁决态（PENDING + 三裁决 + ESCALATED）；产品侧业务处置态（如 procuraAI FR-205 修订流：待修订→修订中→重审中→定稿）是另一层状态机，两者不混用——仅 ESCALATED 两端贯通（编排超时/超轮→转人工全程），与产品侧 FR-205 同名同义。

---

## 4. 框架与组件选型

### 4.1 LLM 抽象：一期不引 Spring AI，自建 OpenAI 兼容客户端

Spring AI 1.1.x 已 OSS EOL（2026-06-30）且有未修复 CVE（含 CVE-2026-59318 工具调用越权）；2.x 绑定 Boot 4 / Jackson 3，Boot 3.5.x 基线无法加载，升 Boot 4 是全仓 breaking 升级（约 83 处）。定案：自建 OpenAI 兼容客户端（**已落地用 JDK `HttpClient` + Jackson**，`OpenAiCompatibleClient` + `LlmHttpTransport` + `ResilientLlmTransport`（超时 / 重试 / 熔断）三类约 250 行、**零新依赖**，与 bone-integration `RestClientImpl` 一致；JSON mode 已落地、`LlmMetrics` 观测端口已就位（Noop 默认，OTel 映射随 Micrometer 依赖 L3 后补），`LLMClient` 门面契约冻结。网关核心价值（路由 / 配额 / 审计 / 脱敏 / 成本红线 / 证据链）Spring AI 本就不提供，两者解耦：AI 先跑业务，Boot 4 走平台升级轨道（§6 时限）。

**迁移阶梯（只换门面实现，不动业务）**：① 自建（默认）；② 覆盖不了的 provider/能力 → LangChain4j（Apache-2.0，Boot 3.5 可用）局部替换，不全家桶；③ Boot 4 落地后按需评估 Spring AI 2.x。若引框架：只用修复版、tool calling 默认关、语义缓存自建（租户 key + 全长度哈希），须 L3 单批。

**供应链**：2026-03 LiteLLM PyPI 包被植入凭据窃取恶意软件，暴露"Python 深依赖树承载密钥"的结构性风险；Java 自建客户端零新增依赖，适配私有化与气隙环境。

MCP 双角色（预留，不捆绑 Spring AI）：Server（模型 / 检索 / 规则以 Tool 暴露）+ Client（消费外部 MCP Server，共用网关 Tool 注册表与审计出口）；MCP SDK 锁 ≥2.0.1（CVE-2026-59279 修复）。

### 4.2 编排 / Agent：不引重型框架

LangGraph / AutoGen / CrewAI / Dify 内核 / Spring AI Alibaba Agent 均不引入：① 三产品核心是"确定性工作流 + LLM 节点 + 规则兜底 + HITL"，合规（可复现 / 可回放 / 人工终审）与自主 Agent 开放推理冲突；② Camel Flow + orchestration 状态机已是编排内核，再引即第二真源；③ Dify LICENSE 禁止未经书面授权的多租户运营（含私有化交付）；④ "规则命中不调 LLM"须网关强制，Agent 自主循环是成本失控面。

替代：编排 = orchestration 状态机 + integration AI 节点；能力暴露 = MCP；确需多智能体时在状态机内以子图 + 工具调用实现。试点例外（须同时满足）：过许可 + CVE / SBOM 门禁、仅限单产品内部不进 `bone-ai-*`、调用仍走网关、结束后抽回 orchestration，否则自动回落自建路径（provider 扩展走 §4.1 迁移阶梯）。

### 4.3 规则表达式：QLExpress 唯一，SpEL 冻结为非规则引擎

`com.alibaba:qlexpress4` 4.1.3（Apache-2.0）是 `bone-ai-rules` 规则 DSL 唯一引擎：表达式追踪直接服务"判定路径可回放"（AI 合规硬需求），原生支持脚本热更与版本治理；沙箱保持"禁止脚本与应用代码交互"策略。

**存量口径（代码实证）**：extension 域（`ExtensionAutoConfiguration` 默认 bean、`ExtensionRegister` / `DefaultExtensionPointRouter` 硬编码 `new SpELExpressionEvaluator()`）与 metadata 域（`ExpressionEngine` / `DynamicSqlProcessor` / `MyBatisSqlProcessor` 的 `SpelExpressionParser`）表达式执行均为 **SpEL**——嵌入式表达式，无热更、无追踪、无版本治理，定位是字段/公式计算，**不是规则引擎**，冻结不动。SpEL 与 QLExpress 分属两个定位，不构成双规则引擎；"为何不直接用 SpEL"的答案即此边界。

**Aviator 处置：删除（方案 A）**。`AviatorExpressionEvaluator` 实现 `ExpressionEvaluator` SPI 但零消费者、零测试，依赖 `<optional>true</optional>`——扩展引擎设计原定"默认 SpEL + Aviator 可选"（doc/design/modules/5），从未启用；LGPL→Apache-2.0 的 GAV 升级已执行但无使用对象。删除实现 + optional 依赖 + `bone-parent` dependencyManagement，缩小 CVE / SBOM / 维护面。

### 4.4 文档解析 sidecar 契约（冻结基线）

先冻契约、后选实现：默认 Docling，PaddleOCR 为可替换实现（替换成本 = 一个适配器类）：HTTP REST（JSON）唯一基线；输入 = 文件引用（MinIO objectKey 优先）+ 解析配置 + 租户上下文；输出 = 统一 IR JSON + `provider_meta`；统一错误字典（`PARSE_TIMEOUT` / `UNSUPPORTED_FORMAT` / `OCR_LOW_CONFIDENCE` / `SIDECAR_UNAVAILABLE` / `PAYLOAD_TOO_LARGE`）对齐 `ApiResponse`；超时按页数分档、仅幂等失败有限重试，超时返部分 IR + `partial=true` 转 HITL（阈值见详设，不在总案硬编码）；独立 Deployment + HPA + `/healthz`；sidecar 不持租户凭证（短期 URL），IR 走内网（mTLS 若启用则强制）。

### 4.5 组件选型总表

| 能力 | 选型 | 说明 |
|---|---|---|
| Java 基线解析 | PDFBox / Tika / POI（已有，Apache-2.0） | 默认管线 |
| OCR / 复杂版面 | 默认 Docling（MIT，版面 / 表格，CPU 可起步）；PaddleOCR（Apache-2.0）为扫描件候选，按需启用 | 契约冻结下的可互换实现（§4.4），不同时维护双实现；精度不达标时候选 MinerU（自定义许可，私有化内部署需法务确认）；负表（PyMuPDF / Marker / pg_search）见 SBOM §4 |
| rerank | bge 系 cross-encoder（数据不出域）：`RerankPort` 契约，未配 `bone.ai.rag.rerank.base-url` 时 `NoOpReranker`（保持 RRF 粗序），配置后 `HttpBgeRerankClient` 接**自托管推理服务**（非云端辅路——数据不出域红线；provider 故障 ⇒ 检索链 WARN + 降级 RRF 粗序，读路径可用性优先，2026-10-23 接线） | rag 自建；实现形态为 HTTP 客户端 + 可替换 Port |
| Embedding | bge-m3 系（兼顾中英条款，1024 维起步）：`EmbeddingPort` 契约，未配 `bone.ai.rag.embedding.base-url` 时 `TransitionalHashEmbedding`（开发 / 无推理服务兜底），配置后 `HttpBgeEmbeddingClient` 接自托管推理服务（provider 故障 ⇒ WARN + 降级 BM25 单路，2026-10-23 条件装配） | 维度变更走版本迁移（`embedding_ver` 入 chunk 元数据）；切片条款级优先、超长条款按语义段二次切分（阈值见详设） |
| 检索护栏 | `max-scan` 容量护栏 fail-closed（超限拒答、不静默截断；**默认 3000**，2026-10-24 由 10000 下调——过渡态内存打分几千条已劣化，10000 是「不可行点」才触发）+ `min-score` 分数阈值（精排后过滤，宁缺毋滥）+ `doc_version` 增量幂等批键（同版本同切片数跳过 / 异版本残缺重建） | 2026-10-23 落地，默认值 2026-10-24 诊断后修订，详见详设 §2.6/§4 #20-22 |
| 检索权限隔离 | `RagAclPort` 文档级 ACL 端口（契约 = 调用方可见 `doc_id` 集合；**默认 fail-closed 拒绝检索**（`DenyDocAcl` 空可见集，2026-10-24 起不再放行全租户——企业单租户内多部门 / 多团队场景下兜底放行即跨文档越权面）；接入方 `@Bean` 覆盖后无 ACL 归属的 doc fail-closed 403 `AI_RAG_DOC_FORBIDDEN`；身份上下文解析、不接收 user 入参） | 2026-10-23 落地 seam，2026-10-24 改默认 fail-closed；**接入方 ACL 适配（文档中心）由 L3 可选升为 Go-Live 硬依赖**，详见详设 §2.6 |
| 写入侧 PII 保护 | ingest 字段级**等长局部掩码**（身份证保留前 6 后 4 / 手机号前 3 后 4 / 银行卡后 4（Luhn 防误伤）/ 邮箱保留首字符 + 域名；`bone.ai.rag.ingest.pii-mask-enabled` 默认开；等长保证 span 锚点不漂移；掩码处数经 `INGEST_PII_MASKED` 事件可观测） | 2026-10-24 落地，与网关出口脱敏形成「入库→存储→检索→生成出口」全链路 PII 闭环 |
| 检索多样性 | MMR 选取式去重（`λ·相关性 − (1−λ)·与已选相似度`；`bone.ai.rag.mmr-lambda` 默认 0 = 关闭，λ∈(0,1) 生效、开启时精排 3 倍扩样；λ=0 约定为关闭而非纯多样性） | 2026-10-23 落地，阈值按评测集回流校准（详设 §4 #23） |
| 降级可观测 | `RagEventSink` 事件端口（默认结构化 WARN）+ 响应 `degradations` 降级痕迹码（`EMBEDDING_DEGRADED` / `RERANK_DEGRADED`） | 2026-10-23 落地 seam；trace 落库 + 告警适配为 L3 目标态，详见详设 §2.6 |
| 向量库 | 终局 pgvector 独立实例（实例形态 rag 启动时实测决策）；过渡用 MySQL JSON + 应用层余弦（单租户切片数千级）；**pgvector + HNSW 提前至第二批前启动**（2026-10-24 改判：任一产品语料过百万切片即系统性劣化，不为想象规模拖延，但也不再等「出现才排期」）。**设计已闭环（v1.16，RAG 方案 v1.10 §14）**：`VectorSearchPort` 检索端口抽象 + 四阶段迁移（双写 → 回填 → 影子评测 → 切流退役）+ 验收闸门（recall@10 ≥0.98 / P99 / 索引构建耗时）+ 回退预案；**实现待 R4 排期**，代码不动点仅端口与配置 | 不为想象规模预付运维税；不引入 ES 作为第三套检索栈；设计前置只为"切流时不必改业务代码"，非提前建容量 |
| 网关指标接线 | `LlmMetrics` 端口 + 宿主 `MicrometerLlmMetrics` 适配器（`bone-ai-app` 存在 `MeterRegistry` 即自动装配：provider 调用计数 / 延迟 P99 / TTFT / token 三维度 / 短路 / 缓存 / 配额命中；库形态仍零依赖 Noop） | 2026-10-24 落地；OTel `gen_ai.*` 语义映射随告警规则接入（映射表版本化） |
| 评测离线辅助 | RAGAS（Python sidecar，可选）；**faithfulness 双层判官（v1.16 落地）**：rag `HeuristicAnswerQualityJudge`（确定性 bigram 包含度，CI 门禁承重）+ app `LlmAnswerQualityJudge`（走 `JudgePort` 网关计量，opt-in 语义校准） | 评测集 / 指标字典 / CI 门禁挂发布流水线，不建模块；judge 走网关计量；对抗评测走 C8 对抗样本库 + CI 负向探针，不引 promptfoo；**判官分层原则：门禁只吃确定性判官，语义判官只做离校准入不回写门禁** |
| 模型 | **锁定 Apache-2.0 档：Qwen3.5-27B / Qwen3.8-27B**（已核定终局，二选一） | 其余档位按部署形态法务确认（多租户 SaaS 触发 MaaS 条款需审批）；随选型交 SBOM 复核 |
| 安全 | 输入校验 / 分隔符封装 / 输出溯源 / 工具最小特权 / 对抗样本库 + 模型 SBOM + 依赖 CVE 扫描 | 无银弹工具包 |

### 4.6 许可红线（POC 签前法务出 SBOM）

Dify 内核（多租户需书面授权）⛔不引入；Spring AI 1.1.x（EOL + 未修复 CVE）❌一期不引；Aviator（新旧 GAV）⛔整体移出选型（零消费者死实现删除）；MinerU / Qwen 非 Apache 档按部署形态法务确认；QLExpress 4.1.3 / Docling / PaddleOCR / pgvector ✅可用。新增依赖一律过 CVE / SBOM 门禁。送审底稿见 `BONE-AI依赖与许可SBOM清单.md`（5 项待法务确认）。

### 4.7 网关形态：先库、后服务（与业界对齐但一处分歧）

网关必要性 = "模型调用的治理强制点"，不是"大而全的服务"：调用点分散则成本 / 证据 / PII 必被绕过（Kong 定义即集中式策略强制点）；多供应商 + OpenAI 兼容协议是 2026 常态；租户配额只能平台强制；fallback / 重试 / 熔断跨产品重复。故**一期 = 库形态**（进程内门面，零额外延时、无 SPOF）；任一触发即演进为集中式网关（LiteLLM Proxy / Portkey 形态）：出现非 JVM 调用方、跨产品集中配额审计、或供应商 ≥3。演进成本可控（`LLMClient` 契约不变）。

**v1.15 修订（企业复杂场景诊断 P1-2：触发条件与现状自相矛盾的消解）**：原触发条件之一是「跨产品集中配额审计」——而**现状已是 3 个产品（procuraAI / SynapClaim / Equity）共享 provider、各自进程内嵌库**，这正是该触发条件已满足的场景，却仍用纯库形态：N 份配额状态、N 套配置、N 份审计分布在不同产品进程，「跨产品集中配额审计」在库形态下无从做起。修订为**两段式收口**：① **权威态提前收口**（第一批后即启动）——`bone-ai-app` 宿主已具备集中 REST 面雏形，把**配额权威态、密钥出库、成本计量的真源**收敛到宿主（契约不变、各产品仍进程内嵌库复用同一端口实现，多副本走 Redis 共享），消除「N 份配额分散」；② **完整集中式网关**（LiteLLM / Portkey 形态）仍按原触发条件演进——非 JVM 调用方出现或供应商 ≥3 时整体切换，`LLMClient` 契约不变、业务零改。骨子里不因「想象的非 JVM 调用方」提前建重型服务，也不因「库形态惰性」放任成本 / 审计继续分散。**部署形态与接入顺序的决策就绪材料**见《BONE-AI架构决策记录ADR包-2026-10-24》ADR-3（建议：第一阶段轻集中网关面，接入顺序 Equity→procuraAI→SynapClaim，每产品双跑对账一周）。

**刻意分歧**：业界网关故障建议 fail open 直连供应商；BONE 合规场景直连即断证据链。定案 **fail 到"纯确定性 / 人工替代"路径，绝不 fail 到直连模型**，能力级灰度开关支持秒级降级。

**能力落点（对标 LiteLLM / Portkey / Kong / Bedrock，P1 同批不单独立项）**：统一 OpenAI 兼容 API ✅；预算 / 配额 / spend tracking / Virtual Keys（密钥走 bone-security）；路由降级 + 重试（仅幂等可重试故障，如 sidecar 不可用，绝不重试 provider 语义错误）+ 熔断（circuit breaker，防 provider 退化重试风暴）；精确缓存（与成本红线同款短路）；语义缓存后置（租户隔离实证前禁用）；Guardrails / PII DLP（OWASP LLM01 / LLM02 对表）；调用日志证据 ✅（哈希防篡改 + 回放超业界基线）；观测（对齐 OTel GenAI `gen_ai.*` 映射表版本化、提示词默认不采集）。随第一批增量：级联路由（小模型先行、不足自动升级）+ 结构化输出强制（JSON Schema 校验，失败转 HITL）+ 提示词版本化（随 `AiMetadata` 版本走、可回滚）；灾备路由（云端作备）随客户形态启用，默认 fail 到确定性 / 人工路径。

---

## 5. 治理与质量门禁

**治理靠结构强制，不靠约定**——证据链、类型护栏、许可红线全部机械落地：

| 门禁 | 规则 | 违反后果 |
|---|---|---|
| 证据链 | 网关唯一入站；**每次真实触达模型的调用必发 `LlmCallRecorded`（失败也补发）、trace 同步落快照；响应对不上快照拒绝返回**。红线短路与精确缓存命中**不留证**（未触达模型，负向探针断言无事件） | 缺证据 = 拒返，可追溯由代码保证 |
| 类型护栏 | 资金 / 责任 / 数值判定入口禁收 `AiSuggestion`，只收 `Confirmed<T>` | ArchUnit 编译期拦截（**v1.15 已机械强制**：`AiSuggestion`/`Confirmed` 两类型 + 规则 6「AI 建议禁越出 `domain.decision` 包」+ `HitlItem.toConfirmed()` 确认产物入口；记录不变量单测锁死） |
| 依赖单向 | 产品不直连 AI 实现类（禁 import `LlmClient` 实现）；AI 不 import 产品域 | ArchUnit 编译期拦截 |
| 密钥边界 | 模型 HTTP / key 只在 gateway | ArchUnit + 代码评审 |
| 许可 | 禁 Aviator 全量 GAV（零消费者死实现已删）；新增依赖过 CVE / SBOM | CI 门禁 |
| 质量 | 评测基线 + 负向探针（成本红线、rerank 越权、规则灰度、网关延时增量、trace 落盘成功率） | 发布流水线拦截 |

**评测口径**：影子模式 + 抽检 + `reason_type` 回流是运行机制；最小 eval（评测集 + 指标字典 + CI 门禁）保留，不做全量标注平台；冷启动用小规模人工标注 + HITL 回流滚雪球，不搞一次性大标注。契约先行、实现可替换，P0 以"平台默认实现 + 产品可覆盖"落地；入站边界治理约束继续有效（ADR-0028 `*ApplicationService`、`ApiResponse`/`PageResult`、全表租户隔离）。

---

## 6. 落地计划

**现状基线（2026-10-24 晚重基线，承接企业复杂场景诊断优化 + v1.16 评测闭环）**：11 模块均已有最小落地（主代码 + 单测 + ArchUnit）。已落地：gateway 治理面（红线/脱敏/配额/路由/缓存/注入/内容安全端口，默认开关见详设 §2.1）、trace 三元组 + 同步落证 + 拒返断言、rules 最小版（CRUD/版本/命中标记/回归门禁）、orchestration 顺序流水线 + `DecisionGate` + HITL 五态与处置链 + **类型护栏（`AiSuggestion`/`Confirmed` + ArchUnit 规则 6，v1.15 转机械强制）**、document 最小 IR（PDFBox + Docling sidecar 适配器就位默认关）、rag 最小全链（租户隔离 + **文档级 ACL 默认 fail-closed（v1.15）** + 容量护栏（max-scan 默认 3000，v1.15 下调）+ 双路 + RRF + rerank 条件装配与降级 + 引用强制 fail-closed + `doc_version` 幂等 ingest + **写入侧 PII 等长掩码（v1.15）** + MMR 多样性（默认关）+ 降级痕迹可观测 + **回答质量三项判官与回答级 golden-set 门禁（v1.16）**）、notification/integration/ledger 最小版、bone-ai-app 聚合 REST 宿主（**+ Micrometer 指标适配器，v1.15** + **判官端点，v1.16**）、bone-ai-server 参考宿主。目标态（未落地）：多节点状态机（`FALLBACK_NODE`）、pgvector + HNSW 终局（**第二批前启动**，v1.15 提前；**迁移设计已闭环 v1.16，仅剩实施**）、OTel `gen_ai.*` 打点、前端确认台（qiankun）、模型锁型定版、RAG 查询改写（决策：批二按评测回流启动，暂不建零消费方 machinery）、RAG ACL 接入方适配（**Go-Live 硬依赖**，v1.15 上提）、语义判官生产校准集扩充（v1.16 起 opt-in，不阻塞门禁）。故各批工作性质从「建成」调整为「验收 + 补治理面 + 出口判据实证」。

| 批次 | 范围 | 出口判据 |
|---|---|---|
| 第 0 批 POC（2 周） | gateway 门面 + 自建客户端（一家 provider + vLLM 本地）；`LLMClient` 契约冻结；最小 eval 挂 CI；法务 SBOM 首版；CVE 门禁上线 | 换 provider 不改业务代码；成本红线有负向探针 |
| 第一批（最小内核） | gateway 治理面 ✅已落地 + trace ✅ + rules 最小版 ✅（CRUD + 版本 + 命中标记）+ orchestration（含 HITL）✅ + metadata 语义扩展 + **最小 IR（存量 PDFBox/Tika 条款切片 + span 定位）** ✅ | procuraAI 端到端（解析→规则→LLM→HITL→回放）；引用定位原文 100%；三元组可回放；rules 最小版必含（否则规则逻辑写死编排层成第二真源） |
| 第二批（解析检索） | document 完整管线（OCR / 复杂版面，默认 Docling；sidecar 适配器已就位待验收）+ rag 最小全链 ✅（待 pgvector 终局实测与指标达标）+ rules 完整面（灰度 / shadow / 回归） | RAG 引用正确率 ≥98%；rerank 越权 / 规则灰度有负向探针；pgvector 形态按实测决策。**pgvector 迁移设计已就绪（v1.16，RAG 方案 §14）：四阶段 + 验收闸门（recall@10 ≥0.98）可直接执行，本批只需排期实施、不需再出设计** |
| 第三批（评测交付） | 评测门禁挂 CI + notification 消警闭环 + integration AI 节点三态 + `bone-ai-app` 后端 ✅（前端确认台 / 评测看板待建） | 评测门禁挂 CI 实证；客户接入目标 ≤15 人日；三态可回退实证。**faithfulness 门禁已随 v1.16 提前入库（`RagAnswerGoldenSetEvalTest` 回答级），本批验的是"挂 CI + 语义判官校准集扩充"，不是从零建评测** |

各批通用基线阈值见详设，不在总案硬编码。**上线判据（生产就绪）**：各批出口判据之外，切生产流量前须过《详设》§6 生产就绪清单六域（部署配置 / 可观测 / 安全合规 / 容量性能 / 灰度回退 / 上线判据）——硬依赖项（密钥托管、多副本 Redis 分布式配额、健康探针、告警规则、故障演练记录、威胁模型决策（**决策就绪材料见 ADR-1：三产品威胁矩阵 + 内容安全默认开建议 + 注入检测三件套**）、**RAG 接入方 ACL 适配（v1.15 上提：默认 fail-closed 已兜底越权面，但「拒绝检索」不是生产形态，接入真实 ACL 才能既安全又可用）**、**写入侧 PII 策略确认（v1.15：默认等长掩码已落地，产品侧须确认掩码口径满足其检索语义或显式评估关闭；可逆令牌化与 KMS 的决策就绪材料见 ADR-2）**）未清不算可上线。**平台级风险（解耦单列）**：Boot 3.5.x 已 OSS EOL，AI 中台零框架依赖不加深该风险；建议 2026-11-30 前二选一拍板（商业订阅或 Boot 4 排期），不与 AI 项目捆绑升级。

---

## 7. 待拍板（L3）

1. `bone-ai/` 聚合定位（**11 模块**：6 能力模块 + notification/integration/ledger + app 聚合宿主 + server 参考宿主；评测挂流水线不建模块）+ **8 张新 DDL 追认**（`ai_trace_snapshot` / `hitl_item` / `hitl_disposition` / `rule_asset` / `rule_asset_version` / `ai_rag_chunk` / `eval_case` / `eval_run`，均已入 `bone-init.sql`）+ QLExpress 4.1.3 依赖（规则 DSL 唯一引擎）；
2. OCR sidecar 部署形态与契约（§4.4）；
3. 批准"pgvector 独立实例推迟至 rag 实测"（批推迟本身，非具体实例）——**v1.15 修订为「第二批前启动」**：不为想象规模拖延，亦不再等出现才排期；实例形态（独立部署 / 共享集群）仍由 rag 实测决策；
4. 模型锁型：Qwen3.5 / 3.8 系 27B 二选一（POC 评测定）+ SBOM 复核；是否仍要 Spring AI（若要：修复版来源与授权，1.1.8 已否决不在列）；
5. **`bone-ai-app` 聚合宿主部署形态**（资源配置 / 与产品进程内嵌库形态的边界 / 是否随首批客户交付）——v1.9 新增：实现已落地为可部署应用，需 L3 确认其定位与准入条件。
6. **威胁模型场景化决策（v1.15 新增，上线前硬依赖）**：三产品按场景威胁模型决定提示注入检测 / 输出内容安全开关（涉及对外 / 对客户内容的产品侧建议默认开输出内容安全；注入检测默认关可接受但须配误伤监控与人工抽检），决策记录归档（详设 §6.3）；
7. **租户标识模型（v1.15 新增，非数值租户接入前）**：`tenant_id BIGINT` 数值编码对字符串租户键（子域 / UUID / 组织编码）fail-closed 拒绝——抽象字符串主键或增加映射层，避免把「非数值」等同于「非法」（详设 §2.2 注）。

技术债（另案，不阻塞）：`MetadataVersionController` 关系 / 规则两级兼容为空、字段级反射实现需收敛，rules 优先补齐。该类当前 Java 侧零消费方，bone-ai-rules 无 bone-metadata-engine 依赖——规则版本 / 兼容性机制由 bone-ai-rules 自建，不按「复用存量底座」排期。

---

## 结语

机制进平台一次建成、三个产品复用；资产留产品沉淀为壁垒。平台的交付标准只有一条：每个 AI 结论都能拿出证据链自证。

---

## 附录 A：实证索引（供 L3 抽查）

| # | 断言 | 位置 |
|---|---|---|
| 1–2 | `AiMetadata` / `AgentMetadata` 纯配置 | `bone-metadata-engine-domain/.../domain/metadata/AiMetadata.java:13`、`AgentMetadata.java:14` |
| 3 | `ExtensionDecisionRecorder` 内存版 | `bone-extension-sdk/.../infrastructure/ExtensionDecisionRecorder.java:13` |
| 4 | `TenantAbstractEntity` 基类 | `bone-framework/bone-core/.../tenant/TenantAbstractEntity.java` |
| 5–7 | 规则运行时 / 工作流建模 / 版本检查现状 | `.../runtime/RuleEngine.java` 及同目录 `BusinessRule*`；`.../domain/metadata/WorkflowMetadata.java`；`.../runtime/version/MetadataVersionController.java` |
| 8 | `bone-file` 单 jar 实现 | `bone-platform/bone-file/` |
| 9–11 | Aviator 移除（类 + pom 依赖）/ Boot 基线 / 许可 CVE 外部来源 | `bone-extension-sdk/pom.xml`（aviator 依赖移除）、`.../support/expression/AviatorExpressionEvaluator.java`（删除）；endoflife.date + Central + spring.io 公告；ParadeDB / Dify LICENSE 原文；Qwen 官方模型卡 |
| 12–13 | gateway / trace 最小版已落地 | `bone-ai/bone-ai-gateway/.../application/LlmGatewayApplicationService.java`、`infrastructure/OpenAiCompatibleClient.java`；`bone-ai-trace/.../domain/model/EvidenceSnapshot.java`、`infrastructure/event/EvidenceTraceListener.java` |
| 14 | 11 模块全景与「已落地最小版」 | `bone-ai/pom.xml`（module 声明 23–33 行）；各模块 `src/main` + `ArchitectureTest`；`bone-init.sql` 8 张 AI 表（§12.1/12.2）；`BoneAiAppApplication.java`（聚合宿主 javadoc + 5 包 `@EnableSqlRepositories`）；`bone-frontend/apps/`（无 AI 前端应用） |
| 15 | 生产硬依赖代码落地（2026-10-23） | Redis 分布式配额：`bone-ai-gateway/.../infrastructure/quota/RedisTenantQuotaGuard.java` + `config/RedisQuotaAutoConfiguration.java`（`@ConditionalOnClass` 守卫，`.imports` 已注册）；密钥 fail-fast：`config/LlmGatewayProperties.java` `validateProviderSecrets()`；探针分组：`bone-ai-app/src/main/resources/application.yml`；REST 管理面：`bone-ai-app/.../web/TraceController.java`、`RuleController.java` |

## 附录 B：业界对位

| 主题 | 业界主流（2026-10） | BONE 定案 | 差异说明 |
|---|---|---|---|
| 网关 | LiteLLM / Portkey / Kong AI Gateway / Cloudflare AI Gateway（统一 OpenAI 兼容 API + 路由 / 配额 / 缓存 / Guardrails / 观测） | 一期库形态自建门面，`LLMClient` 契约兼容演进为集中式网关 | 库形态零延时无 SPOF；供应链更安全（见 §4.1）；证据链与回放超业界基线 |
| 编排 | Anthropic "Building Effective Agents"：受监管领域用确定性工作流 + 人工终审，不用自主 Agent 循环 | 显式状态机（WorkflowMetadata）+ HITL 收敛节点 | 与业界共识一致，自主循环仅限试点四条件 |
| 工具互操作 | MCP / A2A 成为标准协议 | MCP 双角色（Server + Client），SDK ≥2.0.1 | 预留同向演进，不捆绑框架 |
| 观测 | OTel GenAI 语义约定（`gen_ai.*`） | 对齐映射表版本化，提示词默认不采集 | 一致 |
| 评测 | Langfuse / LangSmith + RAGAS；LLM-as-Judge 省标注 | 自建最小 eval（评测集 + 指标字典 + CI 门禁）+ judge 走网关 | 不做全量标注平台，HITL 回流滚雪球 |
| 检索 | Modular RAG（混合检索 + RRF + rerank + 强制出处）为生产主流 | 条款级切片 + 向量 + BM25 + RRF + rerank + 无依据不编造 | 一致，权限隔离为私有化增量 |
| 模型 | 开源权重 + 私有化部署是政企主线 | Qwen3.5 / 3.8 系 27B（Apache-2.0 档，已核定终局） | 许可红线前置（多租户 SaaS 触发 MaaS 条款需审批） |
