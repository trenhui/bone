# BONE AI 能力沉淀 · 模块规划与框架选型建议

> **版本**：v1.2　**日期**：2026-10-14  
> **v1.2 变更（2026-10-14 文档治理）**：① 修正内部口径矛盾——§1/§6 原“5 个新建”与 §2.1 六行、一页纸“hitl 新建”不一致，统一为 **7 模块聚合**（gateway/document/rules/orchestration(内嵌 HITL)/trace/rag/eval，对齐《最佳方案》v3.4）；② §2.1/§3.2 命名对齐（bone-ai-doc→document、bone-ai-rule→rules、编排落 orchestration）；③ 互引版本同步（图谱 v1.1、最佳方案 v3.4）。
> **v1.1 变更**：技术选型复核后修订——Spring AI 由"引入 1.1.8"改为一期不默认引入（官方 1.1.x 止于 1.1.8 且有 4 个未修复 CVE，含 CVE-2026-59318 工具调用越权）；Aviator 处置改为"升级 GAV 至 Apache-2.0 版 + QLExpress 双引擎"；MinerU/Qwen 许可结论精细化；C4 定位与《BONE-AI能力中台最佳方案》v2.0 对齐（建 bone-ai-trace 模块）。模块结构与落地路线不变。完整复核记录见最佳方案 §6。  
> **上游**：`doc/ai-product/platform/三大方案智能化能力沉淀图谱.md`（v1.1，10 项能力 C1–C10 的同构证据）
> **问题**：① BONE 工程应增加哪些模块来沉淀这些能力，为什么？② 是否引入智能体框架 / 开源工具包，为什么？
> **基线事实**（本机仓库实证 + 官方文档核实，2026-10-13）：
> - Bone：Spring Boot **3.5.16**（3.5.x 终版）、JDK 21、Maven 单体仓库、SCA 2025.0.0.0、MySQL 8.0.31 主库；
> - 已有资产：Aviator 5.4.1（`bone-extension-sdk` 的 `AviatorExpressionEvaluator`，表达式 SPI 已存在）、Apache POI 5.3.0、Apache Camel 4.18.2（最后支持 Boot 3.x 的线）、`bone-notification`（完整 alert 域 + 多渠道）、`bone-integration`（Camel Flow 引擎 + Connector + Outbox + 执行日志）、`bone-file`、`bone-iam`、`bone-masterdata`（steward 审批模式）；
> - 全仓 grep：**无** spring-ai / langchain4j / qlexpress / drools 依赖（图谱 v1.0 §6"已有 QLExpress"表述有误，已修正——实际是 Aviator，且为 LGPL，见 §4.2）。

---

## 1. 结论（TL;DR）

**模块**：10 项能力对应 **7 个新建模块**（`bone-ai/` 聚合：gateway / document / rules / orchestration(内嵌 HITL 子域) / trace / rag / eval，以《最佳方案》v3.4 为准），**3 个扩展现有模块**（notification/integration/extension-sdk），**1 个（C10）完全复用现有集成平台**——原“5 新建 + 1 横切”表述随 C4 独立为 trace 模块、HITL 内嵌 orchestration 而过时；新建前先扩展是避免“第二套实现”的关键。

**框架**：**一期不默认引入 Spring AI**（v1.0 的"引入 1.1.8"经 CVE 复核已推翻：官方 1.1.x 线止于 1.1.8 且有 4 个未修复 CVE（2 high），2.x 绑 Boot 4）——`bone-ai-gateway` 自建 OpenAI 兼容客户端（协议已是事实标准，零新依赖），`LLMClient` 门面预留替换位，Boot 4 窗口再评估 Spring AI 2.x 或 HeroDevs 修复版；**不引入任何重型 Agent 框架**（LangGraph / AutoGen / CrewAI / Dify 内核）——三方案的核心架构决策全部是"确定性工作流 + LLM 节点 + 规则兜底 + HITL"，与自主 Agent 的不可复现性在合规上直接冲突；Dify 还有多租户许可红线（LICENSE 原文实证）。组件级工具包按"许可排雷前置"原则逐个选型（§4）。

---

## 2. 模块规划：能力 → Bone 落点

### 2.1 新建模块（下表 6 行 + §2.3 trace = **7 个**；C3 HITL 内嵌 orchestration、不单列服务）

| 模块 | 承载能力 | 核心内容 | 为什么新建（而不是并入现有模块） |
|---|---|---|---|
| **bone-ai-gateway** | C5 LLM 网关 + C8 安全基线 | 模型路由（规则命中不调 LLM 的红线在此强制）；租户 Token 配额与降档；调用审计；模型版本灰度；语义缓存（**必须带租户隔离，一期建议禁用或平台自建**）；guardrail（输入校验/分隔符封装/输出溯源校验） | 成本红线与安全红线是**治理强制点**，不能依赖产品自觉，也不能散落在各调用方；CVE-2026-59308（Spring AI 语义缓存跨租户串话，SHA-256 截断致 system prompt 间缓存互串）恰好证明缓存/隔离必须在平台层自建。**一期不默认引入 Spring AI**（官方 1.1.8 有 4 个未修复 CVE、2.x 绑 Boot 4），自建 OpenAI 兼容客户端（vLLM/Ollama/DashScope/DeepSeek/OneAPI 协议同构），`LLMClient` 门面预留 Spring AI 替换位 |
| **bone-ai-document** | C1 文档解析与 IR | 统一 IR schema(章节树/段落/表格/条款索引/span);Java 基线解析(PDFBox/Tika/POI,Apache-2.0);OCR/复杂版面走可插拔 sidecar(Docling / PaddleOCR);**脱敏 + 受控回填**;存取走自建 FileStorage Port(复用 bone-file 的 MinIO 配置规范,不进程内依赖其单 jar) | 条款级引用、证据链、前端锚点高亮三个产品全部依赖 IR;解析管线含 GPU/重依赖,必须与业务服务隔离部署 |
| **bone-ai-rules** | C2 双引擎与编排 | 规则 DSL(JSON + 表达式 + 沙箱);规则版本化/灰度/shadow;显式状态机(排队→解析→规则→LLM→确认→归档);AI 节点 fallback 与降级矩阵 | 这是三方案共同的**核心架构决策与主要壁垒**(混合 0.3% vs 纯 LLM 12.7% 漏检);版本化+灰度+降级是治理语义,不属于任何单一产品 |
| **bone-ai-hitl**（内嵌于 orchestration） | C3 HITL 与反馈回流 | 条目化建议（原文引用+依据+建议值）；置信度分级（≥0.85 直出 / 0.7–0.85 复核 / <0.7 提示）；三态决策 + reason_type 分类回流 trace | "AI 提议、人审批"是 195 号文/金融监管/保险法的合规硬门槛；masterdata 的 steward 审批是领域内模式，AI 确认台是跨域通用交互。**不单列服务**（HITL 本质是"编排引擎的一个收敛节点 + trace 的一条回写记录"），但保留明确的应用服务与查询面，前端确认台落 bone-ai-app，权限/组织复用 bone-iam |
| **bone-ai-rag** | C6 受控 RAG | 条款级小片切分；混合检索（向量 + BM25 + 加权 RRF）+ rerank；**引用强制带出处、无依据不编造**；增量更新管道；检索评测 | 语料是领域的，检索与生成控制逻辑是公共的；向量库与 MySQL 主库隔离部署（见 §4.1 选型） |
| **bone-ai-eval** | C7 评测与门禁 | 评测集管理（标注指南/双人 Kappa/回流重标）；指标字典（遗漏率/误报率/P/R/F1/采纳率/引用率）；CI 门禁；LLM-as-Judge（走网关计费） | "你怎么证明它准"是政企必答题；评测汇聚 = 数据飞轮在平台层复利；judge 调用必须过网关才能计量与审计 |

### 2.2 扩展现有模块（3 个）——先查后用，避免第二套实现

| 现有模块 | 扩展内容 | 证据（为什么不需要新建） |
|---|---|---|
| **bone-notification** | C9 提醒预警：增加"规则触发（阈值/边界/时点）→ 多级预警 → 触达与 ack → **消警闭环**（外部系统回写）"语义；补漏报率评测 | 已有 `AlertService`/`AlertLevel`/`AlertChannel`（DingTalk/Mail/SMS/Webhook/InApp）/`NotificationMessage`（租户隔离），"发完即忘"缺的只是触发规则与消警闭环，不是另一套通知 |
| **bone-integration** | C10 FDE：新增 **AI 节点类型**（LLM/RAG/HITL 节点注册进现有 `NodeType`，执行时调 bone-ai-gateway/rule）；三态开关（Shadow→Assisted→Automated）落到 Flow 激活态上 | 已有 Camel `FlowGraph`/`FlowNodeExecutor` + Connector（JDBC/Redis/MQ/S3/FTP/Mongo）+ Outbox + 执行日志 + 密钥加密；两套 FDE 方案写的"只读优先/幂等/三阶段/可回退"正是此模块缺的产品化封装 |
| **bone-extension-sdk** | C2 规则表达式：`ExpressionEvaluator` SPI 增加 **QLExpress 实现**（bone-ai-rules 规则 DSL 默认）；存量 Aviator 实现**升级 GAV** 至 `io.github.aviatorscript:aviator 5.9.0` | 已有 `AviatorExpressionEvaluator`（SPI 形态）；旧 GAV `com.googlecode.aviator:aviator 5.4.1` 为 **LGPL-2.1+**（POM 实证），但上游迁址后的 5.9.0 已换 **Apache-2.0**（包名兼容、零代码迁移）——升级 GAV 即解决许可；QLExpress 4.1.3（Apache-2.0，2026-08 活跃）的表达式追踪（AviatorScript 无）直接服务证据链回放，且与 procuraAI 选型一致 |

### 2.3 并入横切层（1 个）与前端（1 项）

- **C4 结论快照与审计追溯** → **建 `bone-ai-trace` 模块**（与最佳方案 v2.0 裁决一致）：拥有证据链聚合（输入哈希 + 中间表示 + 引擎版本三元组）与回放查询 API；`bone-framework` 只提供基类（`TenantAbstractEntity` 谱系）与留存策略常量（≥7 年/≥6 个月平台统一执行）。打消"独立服务会变旁路"顾虑的机制：所有 AI 服务强制经 trace 的 Spring 事件监听/拦截器落盘（ArchUnit + 运行期断言双重保证）。
- **前端**：`bone-frontend` 增加 AI 子应用（qiankun，建议端口 3013）：HITL 确认工作台、评测看板、网关监控、AI 流程三态面板。对齐现有 9 子应用规范（ID 全 string、禁 `toLocaleString`）。

### 2.4 落点总览

```
                    ┌─────────────────────────────────────────────┐
   C1 文档解析/IR → │ bone-ai-document（新） ↔ bone-file（存，Port）  │
   C2 双引擎/编排 → │ bone-ai-orchestration（新，执行）+ bone-ai-rules（新，治理）│
   C3 HITL      → │ bone-ai-orchestration HITL 子域（不单列服务）   │
   C4 快照/审计  → │ bone-ai-trace（新）      ↔ bone-framework（基类）│
   C5 LLM 网关  → │ bone-ai-gateway（新，自建 OpenAI 兼容客户端）   │
   C6 受控 RAG  → │ bone-ai-rag（新）        ↔ pgvector（独立部署） │
   C7 评测门禁  → │ bone-ai-eval（新）       ↔ gateway（judge 计量）│
   C8 安全基线  → │ bone-ai-gateway guardrail + bone-framework（横切）│
   C9 提醒预警  → │ bone-notification（扩）  ↔ bone-system AlertRecord│
   C10 FDE      → │ bone-integration（扩：AI 节点 + 三态开关）      │
                    └─────────────────────────────────────────────┘
```

> 模块命名与结构以《BONE-AI能力中台最佳方案》v3.4 §2 的 `bone-ai/` 聚合（gateway/orchestration/trace/rules/document/rag/eval）为准；本表为能力→落点视角，两者是同一结构的两种视图。

---

## 3. 智能体框架：引入建议（分层回答）

### 3.1 LLM 抽象层 → **一期不默认引入 Spring AI，自建 OpenAI 兼容客户端**（v1.1 修订）

v1.0 依据"官方 1.1.x ↔ Boot 3.5.x 唯一对应线"推荐 1.1.8，CVE 复核后推翻：

| 维度 | 事实（2026-10-13 官方来源实证） | 结论 |
|---|---|---|
| 版本现状 | Maven Central：1.1.x 止于 **1.1.8**（2026-06-13，含 Boot 3.5.15 升级）；**且该线随 Spring Boot 3.5.x 已于 2026-06-30 OSS EOL**（endoflife.date/HeroDevs/JCP release model 互证，当前无社区补丁）；2.0.1（Boot 4.1.0）/2.1.0-M1（Boot 4.2）；HeroDevs ELS 有 1.1.10（2026-09-01） | 官方 1.1.x 线已终结（安全 + 生命周期双重不达标） |
| 安全状态 | 1.1.8 关联 **4 个未修复 CVE（2 high）**：CVE-2026-59318（tool calling 越权，可被提示注入触发，影响 1.1.0–1.1.8）、CVE-2026-47851（PDF outline 栈耗尽）、CVE-2026-47852（ResourceCacheService 可预测临时目录致 ONNX 模型被替换）、CVE-2026-59294（路径穿越）；官方修复只在 2.0.1 / HeroDevs 1.1.10 | ❌ 不再作为默认选型 |
| 替代路径 | vLLM / Ollama / DashScope / DeepSeek / OneAPI 均为 OpenAI chat/completions 兼容协议；结构化输出（JSON mode）协议原生 | ✅ **自建客户端**（WebClient + Micrometer，Bone 已有），零新依赖 |
| 门面价值 | `LLMClient` 契约（路由/配额/审计/降级/脱敏）与具体 SDK 解耦 | Boot 4 窗口迁 Spring AI 2.x 或 HeroDevs 修复版时，替换的是门面后的一个实现类 |
| 若仍要引入 | 只用修复版；tool calling 默认关闭；语义缓存禁用或平台自建（租户 key + 完整长度哈希）；PDF outline 深度限制 | 底线缓解，需 L3 单独审批 |
| MCP | MCP Java SDK 版本线（0.18.x ↔ 1.1.x / 2.0.0 ↔ 2.x）需同步复核 | Bone 作 MCP Server 的方向不变，但**单列 POC，不捆绑 Spring AI、不阻塞 gateway** |

### 3.2 编排 / Agent 运行时 → **不引入重型 Agent 框架**（明确不建议）

候选：LangGraph（Python）、AutoGen、CrewAI、Dify 内核、Spring AI Alibaba Agent 框架。不引入的四条理由：

1. **架构决策不匹配**：三方案的核心全部是"确定性工作流 + LLM 节点 + 规则兜底 + HITL"——procuraAI ADR-01（L2 规则命中必出）、ClaimSphere 决策图（`fallback_rule_node`）、Equity 数字注入（LLM 只读数字不产数字）。自主 Agent 的开放式推理与**可复现/可回放/人工终审**的合规要求直接冲突。
2. **已有真源**：bone-integration 的 Camel Flow + bone-ai-rule 的显式状态机就是编排内核。再引一套 = 第二份流程真源，违背 Bone"一份数据一个真源"铁律。
3. **Dify 许可红线**（LICENSE 原文实证）：Apache-2.0 修改版，**未经书面授权不得运营多租户环境**（tenant=workspace），且前端 LOGO 不可移除。Bone 本身是多租户平台 → 嵌 Dify 内核即踩线；且会引入第二套租户/权限/审计模型，与 bone-iam 冲突。
4. **成本红线框架不管**："规则命中不调 LLM"必须平台强制（网关），Agent 框架的自主循环恰恰是成本失控面。

**替代方案**:① 编排 = bone-ai-orchestration 显式状态机 + bone-integration AI 节点；2 工具/能力暴露 = **MCP 协议**(Bone 作 Server,产品智能体作 Client);3 例外条款--未来场景确需多智能体时(Equity §6.2"复杂度驱动"),在 bone-ai-rule 状态机内以子图 + 工具调用实现,不引框架。

### 3.3 备选说明：LangChain4j 1.21.0（Apache-2.0，2026-10-02）

模型/向量库覆盖最广、与 Boot 版本解耦、有 Agentic 原语与 Saga 式工具补偿。**定位为备选而非默认**：Bone 是 Spring 技术栈，且一期自建客户端已覆盖 OpenAI 兼容协议全家桶；若未来出现自建客户端不覆盖的 provider/能力（如特殊 rerank、非 OpenAI 协议厂商），经 `LLMClient` 门面局部替换（LangChain4j 或 Spring AI 修复版），不全家桶引入。

---

## 4. 开源工具包选型（按能力，许可排雷前置）

### 4.1 组件级选型表

| 能力 | 选型 | 许可 | 排雷结论 |
|---|---|---|---|
| 文档解析（Java 基线） | Apache PDFBox / Tika / POI 5.3.0（已有） | Apache-2.0 | 干净，直接用作 bone-ai-doc 默认管线 |
| OCR / 复杂版面（sidecar） | **Docling**（MIT，LF AI & Data 托管；生态含 docling-serve REST、docling-java、docling-eval）或 **PaddleOCR**（Apache-2.0，PP-StructureV3） | Apache-2.0 / MIT | 按文档形态路由（扫描件→PaddleOCR，版面/表格→Docling；CPU 可起步，GPU 提吞吐） |
| （重新定性） | MinerU（≥3.1.0 已由 AGPLv3 切换为**基于 Apache-2.0 的自定义许可**：>100M MAU 或 >$20M/月营收需商业授权 + 对外在线服务署名）；PyMuPDF（AGPL/商业双授权）；Marker（权重 OpenRAIL-M，$5M 阈值） | — | MinerU 从"绝对避开"调整为"私有化内部部署基本不触发门槛、需法务确认具体版本与模型权重许可；对外 SaaS 不建议"（其 OmniDocBench 93+ 精度留作候选）；PyMuPDF/Camelot 选型需在 Bone 侧替换 |
| rerank（v1.1 新增） | **本地 ONNX cross-encoder**（bge-reranker 系，数据不出域）为主；云端 rerank API 经网关代理计量为辅 | 模型许可随选型确认 | **Spring AI 模型家族无 rerank 类型**（官方清单仅 Chat/Embedding/Image/Audio/Moderation）——不能等框架，bone-ai-rag 自建 |
| 规则表达式 | **QLExpress 4.1.3**（com.alibaba:qlexpress4，2026-08 活跃）作 bone-ai-rules 规则 DSL 默认；存量 Aviator **升级 GAV** 至 `io.github.aviatorscript:aviator 5.9.0` | Apache-2.0 | Aviator 旧 GAV 为 LGPL-2.1+，但上游 5.9.0 已换 Apache-2.0（包名兼容，零代码迁移）；QLExpress 默认禁止脚本与应用代码交互，安全策略需显式配置（禁用 open security strategy） |
| 向量库 | **pgvector**（独立 PostgreSQL；起步期可同实例独立 database + 独立账号） | PostgreSQL 许可证（宽松） | 不用 MySQL 8.0.31 主库（无向量类型）；不用 pg_search（AGPL-3.0：ParadeDB Community 版，Enterprise 才豁免 copyleft） |
| 模型许可（Qwen 系） | **Qwen3.8-27B 为 Apache-2.0**（procuraAI 选的 27B 档许可干净）；Max/Flash-Next 等为 Qwen License / Qwen Community License 1.0 | 混合 | 非 Apache 档位：>100M MAU 或 >$20M/月营收需 UI 展示模型名，MaaS/AI Work Assistant 需单独授权，纯内部使用豁免——按部署形态分层（私有化交付基本豁免；Bone 自运营 SaaS 开放模型能力触发 MaaS 条款） |
| 评测（离线流水线，可选） | RAGAS / promptfoo（Python sidecar） | Apache-2.0 / MIT | 可选增强；平台主链路用 bone-ai-eval 自建（judge 走网关计量）；解析质量口径可参考 docling-eval |
| 安全 | 无银弹工具包：输入校验/分隔符封装/输出溯源校验/工具最小特权/对抗样本库 + 模型 SBOM + 新增依赖 CVE 扫描 | — | CVE-2026-59308（语义缓存跨租户）/59318（工具调用越权，影响 Spring AI 1.1.0–1.1.8）是"不能依赖框架默认值"的实证 |

### 4.2 许可红线清单（POC 签前法务出 SBOM）

| 组件 | 状态 | 处置 |
|---|---|---|
| Dify | ⛔ 多租户运营需商业授权（LICENSE 原文实证：tenant=workspace） | 不引入；仅允许单租户私有化场景评估（且不建议） |
| Spring AI 官方 1.1.x（止于 1.1.8） | ❌ 4 个未修复 CVE（2 high，含 CVE-2026-59318 工具调用越权） | 一期不引入；若引只用修复版（HeroDevs 1.1.10 或 2.x）且按 §3.1 底线缓解 |
| `com.googlecode.aviator:aviator`（旧 GAV） | ⚠️ LGPL-2.1+ | ✅ **已升级（2026-10-14）**：`io.github.aviatorscript:aviator 5.9.0`（Apache-2.0，包名兼容），三处 POM 迁移 + 构建验证通过；方言资产盘点为零（详见《最佳方案》§3.3） |
| MinerU | ⚠️ ≥3.1.0 为 Apache-2.0 基础自定义许可（MAU/营收门槛 + 署名） | 私有化内部部署需法务确认版本与权重许可；对外 SaaS 不建议；默认 Docling/PaddleOCR |
| PyMuPDF / Marker / pg_search | ⛔/⚠️ AGPL 或自定义/权重许可 | 替换为 PDFBox / PaddleOCR / Docling / pgvector |
| Qwen 非 Apache 档位 | ⚠️ MAU/营收/MaaS 条款 | 选型锁定 Apache-2.0 档位（Qwen3.8-27B）；其余按部署形态法务确认 |
| Aviator 5.9.0 / Docling / PaddleOCR / QLExpress 4.1.3 / pgvector | ✅ Apache-2.0/MIT/PostgreSQL | 可用 |

---

## 5. 落地路线（四阶段，每阶段门禁：check.sh + ArchUnit + 评测基线 + 负向探针）

| 阶段 | 范围 | 出口判据 |
|---|---|---|
| **Phase 0**（2 周） | `bone-ai-gateway` 门面 + **自建 OpenAI 兼容客户端** POC（一家 provider + vLLM 本地）；法务 SBOM 首版；LLMClient 契约冻结；新增依赖 CVE 扫描门禁上线 | 门面后可替换实证（换 provider 不改业务代码）；成本红线开关（规则命中不调 LLM）有负向探针 |
| **Phase 1**（P0 四项） | bone-ai-trace + bone-ai-orchestration（含 HITL 子域）+ bone-ai-document 基线解析 | 三方案任一场景端到端跑通（解析→规则→LLM→HITL→快照回放）；引用定位到原文 100%；快照三元组可回放 |
| **Phase 2**（P1 治理） | bone-ai-rules / bone-ai-rag / bone-ai-eval / gateway guardrail 与配额 | 混合检索引用正确率 ≥98%（口径同 Equity）；评测集 + 指标字典 + CI 门禁上线；注入防御有对抗样本库 |
| **Phase 3**（P2 交付） | bone-integration AI 节点 + 三态开关；bone-notification 消警闭环；前端 AI 子应用 | 新客户接入从项目制变配置制（目标 ≤15 人日）；三态切换可回退实证 |

**治理机制**（沿用图谱 §6）：每能力指定 owner + 语义化版本 + 评测基线门禁；平台 API 稳定封箱、领域产品经契约（HTTP/MCP）消费；平台能力变更走 L3 审批（对齐 AGENTS.md 自主权分级）。

---

## 6. 一页纸总结

> **模块**：新建 7 模块（gateway / document / rules / orchestration(含 HITL 子域) / trace / rag / eval），扩展 3 模块（notification 消警、integration AI 节点、extension-sdk 表达式双引擎），前端加 1 个子应用——先查后用，Bone 已有 asset 比预想多，能扩展不新建。（最终模块结构以《BONE-AI能力中台最佳方案》v3.4 的 7 模块 `bone-ai/` 聚合为准。）
> **框架（v1.1 核心变化）**：**一期不默认引入 Spring AI**——官方 1.1.x 止于 1.1.8 且有 4 个未修复 CVE（含 CVE-2026-59318 工具调用越权）、2.x 绑 Boot 4；gateway 自建 OpenAI 兼容客户端，`LLMClient` 门面预留替换位；**不引入重型 Agent 框架**（合规可复现性冲突 + Dify 多租户许可红线 + 已有 Flow 真源）；MCP 单列评估；Aviator 升级 GAV 至 Apache-2.0 版 + QLExpress 作规则 DSL；rerank 自建（Spring AI 无 rerank 模型类型）。
> **工具包**：解析用 PaddleOCR/Docling（Apache-2.0/MIT；MinerU 私有化可需法务确认），向量用 pgvector（宽松许可，独立部署）；**许可排雷前置，POC 签前法务出 SBOM，新增依赖过 CVE 扫描**。
