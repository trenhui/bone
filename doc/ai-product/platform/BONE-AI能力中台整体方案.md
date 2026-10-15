# BONE AI 能力中台 · 整体方案

> **版本**：v1.8（2026-10-15，归口：平台架构组）｜本文是 Bone AI 能力中台**唯一方案真源**：模块边界、框架选型、许可红线、落地批次以本文为准。业界对位见附录 B。⚠️ 阈值初值、DDL（拟定）、API 面与任务分解见《BONE-AI能力中台详细技术方案》（详设），总案不硬编码阈值。

## 0. 一句话结论（TL;DR）

1. **沉淀机制、留住资产**：跨域同构的**机制**进平台（解析 / 双引擎编排 / HITL / 留痕 / 网关 / 检索 / 评测 / 安全 / 预警 / 交付），领域**资产**留产品（规则库 / 图谱 / 估值引擎 / 快照库）。平台只做"证明与治理"，不做"判断与决策"。
2. **怎么建**：新建 `bone-ai/` 平行聚合（6 新模块 + 扩展 4 存量模块 + 1 AI 子应用；评测能力挂发布流水线，不建模块），只补空白与治理面；一期**不引 Spring AI 与重型 Agent 框架**——自建 OpenAI 兼容客户端 + `LLMClient` 门面、显式状态机编排、规则 DSL = QLExpress 唯一（表达式追踪服务"判定路径可回放"；存量表达式执行为 SpEL，属计算域非规则引擎）。
3. **怎么落**：治理靠结构强制（网关唯一入站 + 无证据拒返 + `AiSuggestion`/`Confirmed` 类型隔离 + ArchUnit 门禁）；POC（第 0 批）→ 三批次垂直切片，每批有出口判据与机械门禁；许可排雷前置（SBOM + CVE 扫描）。

---

## 1. 背景与结论

procuraAI / ClaimSphere / Synap Equity 三产品方案独立设计，收敛出 10 项高度同构能力（8 项三方案同构、2 项两方案同构），连选型都重复（规则热更、混合检索 + rerank、三元组快照、HITL 五态、评测集 + 双人 Kappa）。这是业界 AI 演进阶段二的典型触发信号（多业务线重复建设同类能力时，RAG / 路由 / 评测 / 治理自然沉淀为平台层）；三产品均为"建议 + 人工裁决"形态，定位在阶段二起点，不做阶段三的 Agent 自主写操作。

不沉淀的后果：① 重复建设；② 合规能力（可复现 / 可回放 / 人工终审 / 数据不出域）各自拼装、各自面对审计；③ 成本红线 / 证据链 / PII 脱敏散落调用点必被绕过——治理必须结构强制。

### 1.1 家底核查（源码实证，明细见附录 A）

BONE 存量比预期厚，真正空白只有五处：**LLM 运行时、工作流执行器、持久化证据链、文档解析 IR、RAG / 评测**（其中 gateway / trace 最小版已于 2026-10-14 落地）。其余复用：

| 存量 | 结论 | 方案影响 |
|---|---|---|
| `AiMetadata` / `AgentMetadata` | 纯配置 POJO，无运行时 | 建模归 engine，运行时新建 gateway |
| `ExtensionDecisionRecorder` | 内存版，无持久化 / 租户 / 版本 | 升级为持久化证据链 trace |
| 规则执行（`RuleEngine` + `BusinessRuleEngine` 族） | 执行齐全，治理缺失 | `bone-ai-rules` 只做治理面（版本 / 灰度 / 回归），不造执行引擎 |
| `WorkflowMetadata`（状态 / 转换齐全） | 有模型、无执行器 | orchestration 复用模型、补执行器 |
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
| C3 | HITL 确认与回流 | 条目化建议（引用 + 依据 + 建议值）+ 置信度分级（初始 ≥0.85 直出 / 0.7–0.85 复核 / <0.7 强制人工，POC 后按评测校准）+ **五态**（PENDING + 三裁决 + ESCALATED）+ `reason_type` 回流 + 按风险分派裁决人 | P0 |
| C4 | 快照与审计追溯 | 输入哈希 + IR + 引擎版本三元组 + 不可变档案 / 版本链 + 全量留痕 + 回放 | P0 |
| C5 | LLM 网关 | 模型档位路由 + 租户配额 / 降档 + 调用审计 + 灰度 + 成本红线强制 | P1 |
| C6 | 受控 RAG | 条款级切片 + 混合检索（向量 + BM25 + RRF）+ rerank + 强制带出处、无依据不编造 + 增量更新 | P1 |
| C7 | 评测与门禁 | 评测集（标注指南 / 双人 Kappa / 回流重标）+ 指标字典 + CI 门禁 + 对抗评测 + LLM-as-Judge（省 60–80% 标注量）；挂发布流水线，不建模块 | P1 |
| C8 | LLM 安全基线 | OWASP LLM Top 10 对表 + 分隔符封装 / 输入校验 / 输出溯源 / 工具最小特权 / 对抗样本库 / 模型 SBOM / 数据分级 | P1 |
| C9 | 提醒预警 | 规则触发（阈值 / 边界 / 时点）+ 多级预警 + 触达 ack + 消警闭环 + 漏报率评测 | P1 |
| C10 | 集成适配与三态灰度 | 适配器注册表 + 只读旁路优先 + 幂等三件套 + Shadow→Assisted→Automated 三态 + 审计三件套 | P2 |

### 2.2 不沉淀：领域资产

procuraAI（法规规则库 R01–R25、三一致校验、供应商画像）、ClaimSphere（资金状态机、理算引擎、医学图谱、决策图模板）、Equity（PE/PB/DCF 引擎、财务体检、时点快照库）留在产品侧。

**判断标准（一条）**：跨域同构的工程机制进平台，领域知识与数据资产留产品。理由：① 资产是壁垒（2–3 年积累，模型可买、数据难买），吞进平台拖慢领域迭代；② 平台能力契约化暴露，智能体外置隔离（Clean Core）。

---

## 3. 总体设计

### 3.1 模块结构

```
bone/
├── bone-ai/                          # 新增平行聚合（与 bone-engine / bone-platform 同级）
│   ├── bone-ai-gateway/              # C5+C8：LLM 运行时门面 + guardrail（模型调用唯一出口）
│   ├── bone-ai-orchestration/        # C2 执行 + C3 HITL 子域（复用 WorkflowMetadata 模型）
│   ├── bone-ai-trace/                # C4：持久化证据链
│   ├── bone-ai-rules/                # C2 治理：版本 / 灰度 / 回归 + AI 规则语义（不造执行引擎）
│   ├── bone-ai-document/             # C1：解析 + IR + 脱敏
│   └── bone-ai-rag/                  # C6：混合检索 + 引用强制 + 权限隔离
├── 扩展（不新建）：bone-metadata-engine（建模语义）、bone-notification（C9）、
│                  bone-integration（C10）、bone-extension-sdk（表达式 SPI）、
│                  bone-frontend/bone-ai-app（AI 子应用，qiankun，建议端口 3013）
```
（C7 评测不建模块：评测集 / 指标字典 / CI 门禁为发布流水线资产，judge 走网关计量）

独立聚合三理由：① AI 是平台级能力维度，需物理边界 + 独立版本线；② 依赖单向（`bone-ai-*` 只依赖 framework / core / sdk，产品依赖 ai），ArchUnit 可强制"产品不直连 AI 实现、AI 不碰产品域"；③ 许可隔离面收敛。

### 3.2 职责与依赖（无环）

| 模块 | 复用存量 | 边界 |
|---|---|---|
| gateway | `bone-security`（脱敏）、`bone-core`（租户） | 模型调用唯一入站；成本红线、PII 出口脱敏、租户配额、路由降级、审计平台强制 |
| orchestration | `WorkflowMetadata` 模型 | 补缺失的执行器；经 Port 依赖网关 / 规则 / trace，不直连实现（防上帝模块） |
| trace | `TenantAbstractEntity`、`ExtensionDecisionRecorder`（升级） | 三元组快照 + 版本链 + 回放 API；留存时长与分区策略集中配置，framework 只供基类与常量 |
| rules | `RuleEngine` 执行、`MetadataVersionController` 版本机制、expression SPI | 只做治理面，不造执行引擎 |
| document | bone-file 存取规范 | 解析管线空白；OCR / GPU 重依赖与业务隔离部署 |
| rag | trace（溯源）、bone-core（租户隔离） | 语料归领域，检索与生成控制归平台 |

```
gateway → bone-security / bone-core
trace → bone-core + bone-ai-gateway（订阅 LlmCallRecorded，唯一事件落库方）
rules → bone-metadata-engine + extension-sdk + sdk
document → gateway + FileStorage Port（走 MinIO 规范直连或 HTTP，不进程内依赖 bone-file 单 jar）
orchestration → gateway + rules + trace + metadata-engine（经 Port，不直连实现）
rag → trace + gateway + 向量库（§4.5）
产品域 → bone-ai-* + integration + notification
```

**设计约束（§5 门禁实现）**：① 分层铁律——确定性内核（规则 / 计算 / 资金状态机）永远在 domain / 产品侧，AI 模块只给"建议 + 证据"，不做最终裁决；② 类型护栏——AI 结论一律 `AiSuggestion<T>`（置信度 + 证据引用），资金 / 责任 / 数值判定入口只收 `Confirmed<T>`（HITL 裁决或规则产出），ArchUnit 禁止隐式互转；③ 检索边界——向量库实例形态可推迟（§4.5），但切分 / 权限过滤 / 引用强制 / RRF / rerank 策略留 rag，gateway 只做调用代理、不下放检索决策。

### 3.3 HITL 落点与状态契约

HITL 不单列服务，是 orchestration 内嵌子域（确认 API + 置信度分级 + **五态** + `reason_type` 回流 trace），前端确认台落 `bone-ai-app`（权限/组织复用 bone-iam）；合规硬门槛：195 号文“辅助性定位”与人责归属。AI 到“待人工”即停在编排收敛节点等待裁决；不自造审批流、不把主编排托管给 Camel Flow（避免第二份流程真源）。

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

Spring AI 1.1.x 已 OSS EOL（2026-06-30）且有未修复 CVE（含 CVE-2026-59318 工具调用越权）；2.x 绑定 Boot 4 / Jackson 3，Boot 3.5.x 基线无法加载，升 Boot 4 是全仓 breaking 升级（约 83 处）。定案：自建 OpenAI 兼容客户端（**已落地用 JDK `HttpClient` + Jackson**，约 200 行、**零新依赖**，与 bone-integration `RestClientImpl` 一致；JSON mode + Micrometer 观测随治理面补），`LLMClient` 门面契约冻结。网关核心价值（路由 / 配额 / 审计 / 脱敏 / 成本红线 / 证据链）Spring AI 本就不提供，两者解耦：AI 先跑业务，Boot 4 走平台升级轨道（§6 时限）。

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
| rerank | 本地 ONNX cross-encoder（bge 系，数据不出域） | rag 自建；不上云端辅路（数据不出域红线，双路验证成本不值首批投入） |
| Embedding | bge-m3 系（兼顾中英条款，1024 维起步） | 维度变更走版本迁移；切片条款级优先、超长条款按语义段二次切分（阈值见详设） |
| 向量库 | 终局 pgvector 独立实例（实例形态 rag 启动时实测决策）；过渡用 MySQL JSON + 应用层余弦（百万级以下） | 不为想象规模预付运维税；不引入 ES 作为第三套检索栈 |
| 评测离线辅助 | RAGAS（Python sidecar，可选） | 评测集 / 指标字典 / CI 门禁挂发布流水线，不建模块；judge 走网关计量；对抗评测走 C8 对抗样本库 + CI 负向探针，不引 promptfoo |
| 模型 | **锁定 Apache-2.0 档：Qwen3.5-27B / Qwen3.8-27B**（已核定终局，二选一） | 其余档位按部署形态法务确认（多租户 SaaS 触发 MaaS 条款需审批）；随选型交 SBOM 复核 |
| 安全 | 输入校验 / 分隔符封装 / 输出溯源 / 工具最小特权 / 对抗样本库 + 模型 SBOM + 依赖 CVE 扫描 | 无银弹工具包 |

### 4.6 许可红线（POC 签前法务出 SBOM）

Dify 内核（多租户需书面授权）⛔不引入；Spring AI 1.1.x（EOL + 未修复 CVE）❌一期不引；Aviator（新旧 GAV）⛔整体移出选型（零消费者死实现删除）；MinerU / Qwen 非 Apache 档按部署形态法务确认；QLExpress 4.1.3 / Docling / PaddleOCR / pgvector ✅可用。新增依赖一律过 CVE / SBOM 门禁。送审底稿见 `BONE-AI依赖与许可SBOM清单.md`（5 项待法务确认）。

### 4.7 网关形态：先库、后服务（与业界对齐但一处分歧）

网关必要性 = "模型调用的治理强制点"，不是"大而全的服务"：调用点分散则成本 / 证据 / PII 必被绕过（Kong 定义即集中式策略强制点）；多供应商 + OpenAI 兼容协议是 2026 常态；租户配额只能平台强制；fallback / 重试 / 熔断跨产品重复。故**一期 = 库形态**（进程内门面，零额外延时、无 SPOF）；任一触发即演进为集中式网关（LiteLLM Proxy / Portkey 形态）：出现非 JVM 调用方、跨产品集中配额审计、或供应商 ≥3。演进成本可控（`LLMClient` 契约不变）。

**刻意分歧**：业界网关故障建议 fail open 直连供应商；BONE 合规场景直连即断证据链。定案 **fail 到"纯确定性 / 人工替代"路径，绝不 fail 到直连模型**，能力级灰度开关支持秒级降级。

**能力落点（对标 LiteLLM / Portkey / Kong / Bedrock，P1 同批不单独立项）**：统一 OpenAI 兼容 API ✅；预算 / 配额 / spend tracking / Virtual Keys（密钥走 bone-security）；路由降级重试；精确缓存（与成本红线同款短路）；语义缓存后置（租户隔离实证前禁用）；Guardrails / PII DLP（OWASP LLM01 / LLM02 对表）；调用日志证据 ✅（哈希防篡改 + 回放超业界基线）；观测（对齐 OTel GenAI `gen_ai.*` 映射表版本化、提示词默认不采集）。随第一批增量：级联路由（小模型先行、不足自动升级）+ 结构化输出强制（JSON Schema 校验，失败转 HITL）+ 提示词版本化（随 `AiMetadata` 版本走、可回滚）；灾备路由（云端作备）随客户形态启用，默认 fail 到确定性 / 人工路径。

---

## 5. 治理与质量门禁

**治理靠结构强制，不靠约定**——证据链、类型护栏、许可红线全部机械落地：

| 门禁 | 规则 | 违反后果 |
|---|---|---|
| 证据链 | 网关唯一入站，每调用必发 `LlmCallRecorded`、trace 必落快照；响应对不上快照**拒绝返回** | 缺证据 = 拒返，可追溯由代码保证 |
| 类型护栏 | 资金 / 责任 / 数值判定入口禁收 `AiSuggestion`，只收 `Confirmed<T>` | ArchUnit 编译期拦截 |
| 依赖单向 | 产品不直连 AI 实现类（禁 import `LlmClient` 实现）；AI 不 import 产品域 | ArchUnit 编译期拦截 |
| 密钥边界 | 模型 HTTP / key 只在 gateway | ArchUnit + 代码评审 |
| 许可 | 禁 Aviator 全量 GAV（零消费者死实现已删）；新增依赖过 CVE / SBOM | CI 门禁 |
| 质量 | 评测基线 + 负向探针（成本红线、rerank 越权、规则灰度、网关延时增量、trace 落盘成功率） | 发布流水线拦截 |

**评测口径**：影子模式 + 抽检 + `reason_type` 回流是运行机制；最小 eval（评测集 + 指标字典 + CI 门禁）保留，不做全量标注平台；冷启动用小规模人工标注 + HITL 回流滚雪球，不搞一次性大标注。契约先行、实现可替换，P0 以"平台默认实现 + 产品可覆盖"落地；入站边界治理约束继续有效（ADR-0028 `*ApplicationService`、`ApiResponse`/`PageResult`、全表租户隔离）。

---

## 6. 落地计划

| 批次 | 范围 | 出口判据 |
|---|---|---|
| 第 0 批 POC（2 周） | gateway 门面 + 自建客户端（一家 provider + vLLM 本地）；`LLMClient` 契约冻结；最小 eval 挂 CI；法务 SBOM 首版；CVE 门禁上线 | 换 provider 不改业务代码；成本红线有负向探针 |
| 第一批（最小内核） | gateway 治理面 + trace + rules 最小版（CRUD + 版本 + 命中标记）+ orchestration（含 HITL）+ metadata 语义扩展 + **最小 IR（存量 PDFBox/Tika 条款切片 + span 定位）** | procuraAI 端到端（解析→规则→LLM→HITL→回放）；引用定位原文 100%；三元组可回放；rules 最小版必含（否则规则逻辑写死编排层成第二真源） |
| 第二批（解析检索） | document 完整管线（OCR / 复杂版面，默认 Docling）+ rag + rules 完整面（灰度 / shadow / 回归） | RAG 引用正确率 ≥98%；rerank 越权 / 规则灰度有负向探针；pgvector 形态按实测决策 |
| 第三批（评测交付） | 评测门禁挂 CI + notification 消警闭环 + integration AI 节点三态 + `bone-ai-app` | 评测门禁挂 CI 实证；客户接入目标 ≤15 人日；三态可回退实证 |

现状：第一批中 gateway / trace 最小版已落地，其余未动工。各批通用基线阈值见详设，不在总案硬编码。**平台级风险（解耦单列）**：Boot 3.5.x 已 OSS EOL，AI 中台零框架依赖不加深该风险；建议 2026-11-30 前二选一拍板（商业订阅或 Boot 4 排期），不与 AI 项目捆绑升级。

---

## 7. 待拍板（L3）

1. `bone-ai/` 聚合定位（6 模块，评测挂流水线不建模块）+ 新 DDL（trace 快照 / 评测集表）+ QLExpress 4.1.3 依赖（规则 DSL 唯一引擎）；
2. OCR sidecar 部署形态与契约（§4.4）；
3. 批准"pgvector 独立实例推迟至 rag 实测"（批推迟本身，非具体实例）；
4. 模型锁型：Qwen3.5 / 3.8 系 27B 二选一（POC 评测定）+ SBOM 复核；是否仍要 Spring AI（若要：修复版来源与授权，1.1.8 已否决不在列）。

技术债（另案，不阻塞）：`MetadataVersionController` 关系 / 规则两级兼容为空、字段级反射实现需收敛，rules 优先补齐。

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
| 12–13 | gateway / trace 最小版已落地 | `bone-ai/bone-ai-gateway/.../application/LlmGatewayService.java`、`infrastructure/OpenAiCompatibleClient.java`；`bone-ai-trace/.../domain/model/EvidenceSnapshot.java`、`infrastructure/event/EvidenceTraceListener.java` |

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
