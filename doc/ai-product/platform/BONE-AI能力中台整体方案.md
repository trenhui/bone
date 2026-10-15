# BONE AI 能力中台 · 整体方案（正式版）

> **版本**：v1.0（正式版）　**日期**：2026-10-15　**归口**：平台架构组
> **文档定位**：本文件是 Bone AI 能力中台的**唯一方案真源**。落 Bone 平台部署的模块边界、框架选型、许可红线、落地批次一律以本文件为准。本文件融合并取代原《BONE-AI能力中台最佳方案》《BONE-AI能力沉淀模块规划与框架选型建议》《三大方案智能化能力沉淀图谱》三份文档（已废止删除）。
> **输入**：procuraAI（采购 AI）、ClaimSphere（智能理赔）、Synap Equity（智能估值）三套产品方案；Bone 仓库源码实证（2026-10-13 至 10-15）；外部官方来源（EOL / CVE / 许可条款）实证。
> **方法纪律**：存量断言以源码实证为准（读源码，不看注释与旧文档）；外部结论以官方来源为准，无法核实处**显式标注待核定**，不硬凑。

---

## 1. 背景与结论

### 1.1 问题

三个领域完全不同（采购审查 / 智能理赔 / 智能估值）的产品方案，独立设计出 10 项高度同构的智能化能力——**8 项三方案同构、2 项两方案同构**，连技术选型都重复：规则引擎热更、混合检索+rerank、"输入哈希 + 中间表示 + 引擎版本"三元组快照、HITL 三态、评测集+双人 Kappa、适配器模板。不沉淀的后果是三重的：

1. 每个产品各建一套，确定的重复建设浪费；
2. 合规能力（可复现/可回放/人工终审/数据不出域）各自拼装，每个产品单独面对审计挑战；
3. **成本红线 / 证据链 / PII 脱敏散落在产品调用点必被绕过**——这类治理必须是结构强制，不能靠产品自觉。

### 1.2 结论摘要

1. **沉淀边界**：跨域同构的**机制**进平台（解析/双引擎/人环/留痕/网关/检索/评测/安全/预警/交付），领域**资产**留产品（规则库/医学图谱/估值引擎/快照库）——资产是壁垒，不是基础设施；
2. **模块**：新建 `bone-ai/` 平行聚合 **7 模块**（gateway / orchestration / trace / rules / document / rag / eval），**扩展 4 个现有模块**（bone-metadata-engine 建模语义 / notification / integration / extension-sdk），前端加 1 个 AI 子应用。只补空白与治理面，能复用的不新建；
3. **框架**：**一期不引入 Spring AI 任何版本，不引入任何重型 Agent 框架**。LLM 侧自建 OpenAI 兼容客户端 + `LLMClient` 门面（留替换位）；编排侧显式状态机（复用 `WorkflowMetadata` 模型 + 补缺失执行器）；规则侧 QLExpress 为 DSL 默认 + Aviator 双引擎并存；
4. **治理**：证据链靠**结构强制**（网关唯一入站 + 无证据拒返 + 禁直连客户端门禁），不靠约定；AI 结论经 `AiSuggestion`/`Confirmed` 类型隔离才可进资金/责任/数值判定；
5. **合规**：许可排雷前置，新增依赖一律过 CVE/SBOM 门禁，POC 签前法务出 SBOM；
6. **落地**：POC（第 0 批）→ 三批次渐进，每批次有出口判据与机械门禁（check.sh + ArchUnit + 评测基线 + 负向探针）。

### 1.3 存量与空白（家底核查）

| # | 核查项 | 实证结论 | 对方案的影响 |
|---|---|---|---|
| 1 | `AiMetadata`/`AgentMetadata` | 纯配置 POJO（modelName/promptTemplates/temperature…），无模型运行时 | 建模归 engine，运行时必须新建（gateway） |
| 2 | `ExtensionDecisionRecorder` | 内存版（ArrayList + ConcurrentHashMap），无持久化/租户/版本 | 升级为持久化证据链（trace） |
| 3 | `TenantAbstractEntity` | 租户 + 审计基类齐全 | 证据链实体直接继承 |
| 4 | `bone-file` | 已完整实现（22 类，MinIO + Controller + JWT + 租户），但为单 Maven jar 含 Web 层 | 解析模块**复用其存取能力与配置规范，不进程内依赖**（走 Port） |
| 5 | 规则执行运行时 | `RuleEngine`（832 行）+ `BusinessRuleEngine` 族齐全；`MetadataVersionController` 字段级兼容性检查已实现（反射式，有 hack 味），关系/规则两级为空实现 | `bone-ai-rules` **不造执行引擎**，只做治理面（版本/灰度/回归/AI 规则语义）；规则级兼容检查须补齐 |
| 6 | 工作流建模 | `WorkflowMetadata`/`WorkflowState`/`WorkflowTransition` 建模齐全；全仓无通用工作流执行引擎 | orchestration **复用模型并补执行器**，不新造状态机模型 |
| 7 | `bone-integration` | Camel Flow/Connector/Outbox/执行日志齐全 | C10 落扩展，不新建 |
| 8 | `bone-notification` | alert 域齐全（多渠道 + 租户） | C9 落扩展，不新建 |
| 9 | 表达式引擎 | 存量 `com.googlecode.aviator:aviator` 5.4.1（LGPL-2.1+）；上游迁址后 `io.github.aviatorscript:aviator` 5.9.0 为 Apache-2.0（包名兼容）| **存量升级 GAV 即解决许可**（已执行）；QLExpress 作规则 DSL 默认 |
| 10 | `bone-ai/` 落地现状 | 最小网关 + 证据链已在跑：`bone-ai-gateway`（门面：成本红线短路 + `LlmCallRecorded` 事件 + `OpenAiCompatibleClient`/`MockLlmClient` 双实现）；`bone-ai-trace`（`EvidenceSnapshot` 三元组 + 落盘监听 + `findByTraceId` 回放） | 第一批的 gateway/trace 骨架已成；剩余缺口见下 |

**结论**：BONE 存量比预期厚——规则执行、版本机制、工作流建模、文件存储、告警、集成编排都有。真正的空白只有五处：**LLM 运行时、工作流执行器、持久化证据链、文档解析 IR、RAG/评测**；其中前两项最小版已于 2026-10-14 落地（gateway/trace）。**当前剩余空白 = 工作流执行器、文档解析 IR、RAG/评测 + 网关治理面。** 最佳方案 = 只补空白 + 治理面。全部断言的源码位置见**附录 A**（供 L3 审批抽查）。

---

## 2. 能力需求与沉淀边界

### 2.1 可沉淀能力（C1–C10）

| # | 能力 | 沉淀内容 | 同构证据（三产品） | 优先级 |
|---|---|---|---|---|
| C1 | 文档解析与 IR | 统一 IR schema（章节树/段落/表格/条款索引/span）；OCR/表格管线；语义切片；脱敏 + 受控回填 | 三方案各自实现同一层：同选型、同"引用必须定位到原文"、同"脱敏后进 LLM" | P0 |
| C2 | 双引擎与流程编排 | 规则 DSL（JSON+表达式+沙箱热更）；版本化/灰度/shadow；显式状态机（排队→解析→规则→LLM→确认→归档）；AI 节点 fallback 与降级矩阵 | procuraAI "L2 规则命中必出"、ClaimSphere 决策图 `fallback_rule_node`、Equity "LLM 只读数字不产数字"是同一架构决策的三种表述（混合漏检 0.3% vs 纯 LLM 12.7%） | P0 |
| C3 | HITL 确认与反馈回流 | 条目化建议（原文引用+依据+建议值）；置信度分级（≥0.85 直出 / 0.7–0.85 复核 / <0.7 强制人工）；三态决策 + `reason_type` 分类回流；分派路由（按风险等级定向裁决人） | 三方案 HITL 交互完全同构：确认不阻塞流水线、驳回必给理由、反馈进评测集（合规硬门槛：195 号文“辅助性定位”、金融监管人责归属、保险法拒赔必答） | P0 |
| C4 | 结论快照与审计追溯 | 输入哈希 + IR + 引擎版本三元组；不可变档案/版本链；操作全量留痕；回放复现 | review_snapshot / decision_trace+funding_events / research_dossier+as-of 三处独立设计同一机制 | P0 |
| C5 | LLM 网关 | 模型档位路由；租户级 token 配额/降档；调用审计；模型灰度；成本红线强制 | procuraAI 明文设网关、ClaimSphere 统一入口、Equity 模型分层 + 成本监控 | P1 |
| C6 | 受控 RAG | 条款级小片切分；混合检索（向量+BM25+加权 RRF）+ rerank；引用强制带出处、无依据不编造；增量更新；检索评测 | 检索栈三案几乎一致；Equity 引用正确率 ≥98% 与 procuraAI FR-601 同口径 | P1 |
| C7 | 评测与门禁 | 评测集管理（标注指南/双人 Kappa/回流重标）；指标字典；CI 门禁；对抗评测；LLM-as-Judge（judge 辅助可省 60–80% 人工标注量） | 三案同立"先建测试集再谈自动化"军规 | P1 |
| C8 | LLM 安全基线 | 分隔符封装/输入校验/输出溯源校验/工具最小特权/对抗样本库/模型 SBOM/数据分级 | procuraAI（OWASP LLM Top10 + ASI 2026）最深，另两案有数据分级/DPA/本地推理 | P1 |
| C9 | 提醒与预警引擎 | 规则触发（阈值/边界/时点）；多级预警；触达与 ack；消警闭环；漏报率评测 | procuraAI 履约预警（T-15/T-0/T+0 + ERP 消警）与 Equity 跟踪提醒（价格边界/财报季）同构 | P1 |
| C10 | 集成适配与三态灰度（FDE） | 适配器注册表 + 标准字段映射；只读旁路优先；幂等三件套；Shadow→Assisted→Automated 三态开关；审计三件套 | ClaimSphere FDE 与 procuraAI FDE 独立写出几乎相同的原则 | P2 |

### 2.2 不沉淀：领域资产与边界原则

| 产品 | 领域资产（留在产品侧） |
|---|---|
| procuraAI | 法规规则库 R01–R25 + 企业源 ER01+、三一致校验、条款库与智能起草、供应商信用画像 |
| ClaimSphere | 简化资金状态机（两段式）、理算引擎、医学知识图谱、多智能体风控、决策图模板 |
| Synap Equity | PE/PB/DCF 估值引擎、五维财务体检、时点快照库、策略纪律引擎、研究档案与对账误差库 |

**三条边界原则**：

1. **它们是壁垒，不是基础设施**——规则资产/授权数据/知识图谱需 2–3 年积累（模型可买、数据难买），吞进平台会模糊产品归属、拖慢领域迭代；
2. **平台只沉淀"机制与治理"，不吞并"内容与领域逻辑"**——版本化、灰度、回流、留痕、评测的机制进平台；法规三元组、诊断映射、估值公式的内容留产品；
3. **与 Clean Core 一致**——平台能力以契约化服务暴露，领域智能体外置隔离、独立部署，不修改 Bone 核心。

**一句话判断标准**：跨域同构的工程机制进平台；领域知识与数据资产留产品；平台只做"证明与治理"，不做"判断与决策"。

---

## 3. 总体设计

### 3.1 模块结构

```
bone/
├── bone-ai/                          # 新增：AI 能力中台（与 bone-engine/bone-platform 同级）
│   ├── bone-ai-gateway/              # C5+C8：LLM 运行时门面 + guardrail（模型调用唯一出口）
│   ├── bone-ai-orchestration/        # C2 执行 + C3 HITL 子域（复用 WorkflowMetadata 模型）
│   ├── bone-ai-trace/                # C4：持久化证据链（TraceableRecord）
│   ├── bone-ai-rules/                # C2 治理：规则资产版本/灰度/回归 + AI 规则语义
│   ├── bone-ai-document/             # C1：解析 + IR + 脱敏（存取复用 bone-file 规范）
│   ├── bone-ai-rag/                  # C6：混合检索 + 引用强制 + 权限隔离
│   └── bone-ai-eval/                 # C7：评测集/指标字典/CI 门禁
├── 扩展（不新建）：bone-metadata-engine（AI/工作流元数据语义）、bone-notification（C9）、
│                  bone-integration（C10）、bone-extension-sdk（表达式双引擎 SPI）、
│                  bone-frontend/bone-ai-app（AI 子应用，qiankun，建议端口 3013）
```

**为什么是独立聚合而不是塞进 `bone-engine/`**：① 7 个模块 + 独立版本线，"AI 是平台级能力维度"的架构意图需要物理边界；② 依赖单向——`bone-ai-*` 只依赖 framework/core/sdk，产品依赖 ai，ArchUnit 可机器强制"产品域不直连 AI 实现、AI 不碰产品域"；③ 许可隔离——自定义许可组件圈在聚合内，排雷面收敛。

### 3.2 模块职责（能力 → 复用存量 → 新建/扩展理由）

| 模块 | 承载 | 复用存量 | 职责边界与新建理由 |
|---|---|---|---|
| **bone-ai-gateway** | C5 网关 + C8 安全 | `bone-security` 脱敏、`bone-core` 租户上下文 | 模型调用**唯一入站边界**；成本红线（规则命中不调 LLM）、PII 出口脱敏、租户配额、路由/降级、审计必须平台强制。CVE-2026-59308（语义缓存跨租户串话）证明隔离层必须自建。最小版已落地，待补治理面 |
| **bone-ai-orchestration** | C2 执行 + C3 HITL | `WorkflowMetadata` 模型（状态/转换/初始态） | 模型有、执行器无——BONE 最明确的空白；三产品各自造了同一种引擎（规则节点+AI 节点+收敛+降级），复用收益最大的"操作系统级"资产 |
| **bone-ai-trace** | C4 证据链 | `TenantAbstractEntity`；`ExtensionDecisionRecorder` 升级为持久化 | 快照有领域语义（三元组 + 版本链 + 回放 API）；建独立模块、framework 只供基类与留存策略常量（≥7 年/≥6 个月平台统一执行）。防旁路机制：所有 AI 调用经事件/拦截器必然落盘 |
| **bone-ai-rules** | C2 治理 | `RuleEngine`/`BusinessRuleEngine` 执行、`MetadataVersionController` 版本机制、expression SPI | 执行已有但治理没有：版本/灰度/shadow/正反例回归/规则命中语义。不造执行引擎，只做治理面 |
| **bone-ai-document** | C1 解析与 IR | bone-file 的存取能力与 MinIO 配置规范 | 解析管线是空白；条款级引用/证据链/锚点的地基；OCR/GPU 重依赖须与业务隔离部署 |
| **bone-ai-rag** | C6 受控 RAG | bone-ai-trace（引用溯源）、bone-core 租户隔离 | 语料是领域的，检索与生成控制是公共的；"无依据不编造"三产品各写一遍还被审计挑战 |
| **bone-ai-eval** | C7 评测门禁 | 横切挂 `scripts/check.sh` 与发布流水线 | "你怎么证明它准"是政企必答题；评测汇聚 = 数据飞轮平台层复利；judge 调用走网关计量 |
| *扩展* bone-metadata-engine | 建模语义 | AiMetadata/WorkflowMetadata | `AiMetadata` 增模型档位/网关引用/置信度阈值；`WorkflowMetadata` 增 AI 节点/收敛/降级语义——**建模归 engine，执行归 orchestration** |
| *扩展* bone-notification | C9 | alert 域齐全 | 增"规则触发→多级预警→触达 ack→消警闭环（外部回写）"+ 漏报率评测；缺的只是触发与回写 |
| *扩展* bone-integration | C10 | Camel Flow/Connector/Outbox | 注册 AI 节点类型（LLM/RAG/HITL，执行时调 gateway/rules）；三态开关落 Flow 激活态 |
| *扩展* bone-extension-sdk | 表达式 SPI | `AviatorExpressionEvaluator` | 增 QLExpress 实现（规则 DSL 默认）；存量 Aviator 升 GAV；按资产声明方言路由 |

**HITL 的落点**：不单列**服务**，但有明确的域——orchestration 内嵌 HITL 子域（条目化确认 API + 置信度分级 + 三态 + `reason_type` 回流 trace），前端确认台落 `bone-ai-app`（权限/组织复用 bone-iam）。即"引擎的一个收敛节点 + trace 的一条回写记录"，但有明确的应用服务与查询面。**注意**：BONE 没有原生审批/任务引擎可复用（真实流程引擎是 bone-integration 的 Camel Flow，管集成编排、不裁决 AI）——orchestration 复用 `WorkflowMetadata` 模型并补上缺失的执行器；AI 到"待人工"即停在编排收敛节点等待裁决；**不自造审批流、不把 AI 主编排托管给 Camel Flow**（避免第二份流程真源）。

**HITL 状态表（前后端契约基准）**：

| 状态 | 触发 | 语义 | 回流 |
|---|---|---|---|
| `pending` | 编排节点产出 AI 建议（原文引用 + 依据 + 建议值 + 置信度） | 待人工裁决；置信度 <0.7 强制进入（不得自动直出） | — |
| `confirmed` | 人工确认 | 建议值生效，进入下一节点 | trace 记 `decision=confirmed` + 操作人 + 时间 |
| `rejected` | 人工驳回 | 建议值作废，走驳回分支（拒绝流转/转规则兜底） | trace 记 `reason_type`（分类码）+ 备注 |
| `modified` | 人工改判 | 改判值生效；同时作为负样本/纠错样本回流 | trace 记 `reason_type` + 原建议值 vs 改判值 diff |

`reason_type` 用受控字典（`RULE_MISS`/`CONTEXT_MISS`/`SUGGESTION_WRONG`/`POLICY_CONFLICT`），是反馈回流与评测集扩充的**唯一入口**——自由文本不进回流管道（避免脏数据污染评测）。

### 3.3 依赖关系（无环）与分层铁律

```
bone-ai-gateway      → bone-security / bone-core
bone-ai-trace        → bone-core（TenantAbstractEntity）
bone-ai-rules        → bone-metadata-engine + extension-sdk + bone-metadata-sdk
bone-ai-document     → bone-ai-gateway（模型调用）+ FileStorage Port（骨文件走 MinIO 配置规范直连或 HTTP 调 bone-file，不进程内依赖其单 jar）
bone-ai-orchestration → bone-ai-gateway + bone-ai-rules + bone-ai-trace + bone-metadata-engine
bone-ai-rag          → bone-ai-trace + bone-ai-gateway + 向量库（见 §4.5）
bone-ai-eval         → 横切，被发布流水线调用
产品域（procuraAI/ClaimSphere/Equity）→ bone-ai-* + bone-integration + bone-notification
```

**分层铁律**：确定性内核（规则/计算/资金状态机）永远在 domain/产品侧；AI 模块只提供"建议 + 证据"，不参与最终裁决。

**类型护栏（结构强制，不靠运行时嗅探）**：AI 结论一律 `AiSuggestion<T>`（含置信度 + 证据引用）；资金/责任/数值判定入口只收 `Confirmed<T>`（经 HITL 裁决或规则产出）；二者不可隐式互转，ArchUnit 机器强制。出口可伪造，类型骗不了人。

**检索边界**：不自建向量库（实例形态按 §4.5 推迟实测决策），但检索策略（切分/权限过滤/引用强制/RRF/rerank）留在 bone-ai-rag——gateway 只做检索调用代理，**不下放检索决策**，否则网关变上帝模块，与"gateway 不编排、不决策"矛盾。

---

## 4. 框架与组件选型（定案）

### 4.1 LLM 抽象层：一期不引入 Spring AI，自建 OpenAI 兼容客户端

| 路径 | 版本现状（官方来源实证） | 安全/生命周期 | 结论 |
|---|---|---|---|
| A. HeroDevs ELS 1.1.10（2026-09-01） | 修复 4 项 CVE | 已修复 | 备选：商业 fork，坐标/授权需法务确认 |
| B. 官方 1.1.x | Central 止于 **1.1.8**（2026-06-13）；**该线随 Spring Boot 3.5.x 已于 2026-06-30 OSS EOL**（endoflife.date/HeroDevs/JCP 互证），当前无社区补丁 | ❌ 4 个未修复 CVE（2 high）：CVE-2026-59318 工具调用越权、-47851 PDF outline 栈耗尽、-47852 可预测临时目录致 ONNX 模型被替换、-59294 路径穿越 | 安全 + 生命周期双重不达标，否决 |
| C. 官方 2.x（2.0.1/2.1.0-M1） | 绑定 Boot 4.x | 已修复 | 与 Boot 3.5.16 基线冲突，列入后续升级窗口 |
| D. **自建 OpenAI 兼容客户端** | vLLM/Ollama/DashScope/DeepSeek/OneAPI 均为 OpenAI chat/completions 兼容协议 | 不涉及 | ✅ **定案** |

**定案理由**：① gateway 的核心价值（路由/配额/审计/脱敏/灰度/成本红线）与 Spring AI 无关；② 零新依赖——WebClient（已有）+ JSON mode + Micrometer；③ 绕开"1.1.x 有 CVE / 2.x 绑 Boot 4"两难；④ `LLMClient` 门面契约不变，未来 Boot 4 窗口迁 Spring AI 2.x 或修复版时只换门面后一个实现类，升级成本从全仓改造降为"一个模块一个类"；⑤ 若最终仍要引入：只用修复版，且 tool calling 默认关闭（CVE-2026-59318 利用面）、语义缓存禁用或平台自建（租户 key + 完整长度哈希）、PDF outline 深度限制，需 L3 单独审批。

**MCP 双角色（预留，不阻塞建设）**：① Bone 作 Server——模型调用/检索/规则执行以 MCP Tool 暴露，产品侧智能体经 MCP 消费（"平台做能力、产品做智能体"的契约边界）；② Bone 作 Client——消费外部 MCP Server，与 Server 共存于网关同一 Tool 注册表（权限/配额/审计同一出口，不出现绕过网关的第二调用面）。MCP Java SDK 版本线（0.18.x/2.0.x）须同步复核 CVE 状态（CVE-2026-59279 会话无界增长，修复于 2.0.1）；MCP Server 能力单列 POC，不捆绑 Spring AI。

### 4.2 编排 / Agent 框架：不引入重型 Agent 框架

候选 LangGraph/AutoGen/CrewAI/Dify 内核/Spring AI Alibaba Agent 均**不引入**，四条理由：

1. **架构决策不匹配**：三产品核心全部是"确定性工作流 + LLM 节点 + 规则兜底 + HITL"，可复现/可回放/人工终审的合规要求与自主 Agent 开放式推理直接冲突；
2. **已有真源**：bone-integration 的 Camel Flow + orchestration 显式状态机就是编排内核，再引一套 = 第二份流程真源，违背"一份数据一个真源"；
3. **Dify 许可红线**（LICENSE 原文实证）：未经书面授权不得运营多租户环境（tenant=workspace），LOGO 不可移除，**私有化交付同样触发**（纠纷发生在客户现场且客户要求兜底）；
4. **成本红线框架不管**："规则命中不调 LLM"必须网关强制，Agent 框架的自主循环正是成本失控面。

**替代方案**：① 编排 = orchestration 显式状态机 + bone-integration AI 节点；② 工具/能力暴露 = MCP 协议；③ 例外条款——未来确需多智能体时（"复杂度驱动"），在状态机内以子图 + 工具调用实现，不引框架。

**框架试点前置条件（备选路线，不可默认执行）**：确需试点某框架（如 procuraAI 单产品内 LangGraph）须同时满足四条件——① 先过 §4.5/§4.6 许可排雷 + CVE/SBOM 门禁；② 仅限单产品内部，框架代码不进 `bone-ai-*`；③ 试点所有 LLM 调用仍走网关（否则证据链断裂，试点数据不可用）；④ 试点结束把角色/扇出/证据结构抽回 orchestration。任一不满足，自动回落"自建客户端 + orchestration"路径。**LangChain4j 1.21.0（Apache-2.0）为备选而非默认**：出现自建客户端不覆盖的 provider/能力时经 `LLMClient` 门面局部替换，不全家桶引入。

### 4.3 规则表达式：双引擎（已定案并执行）

| 引擎 | 许可实证 | 定位 |
|---|---|---|
| `io.github.aviatorscript:aviator` 5.9.0 | Apache-2.0（原 `com.googlecode.aviator` 5.4.1 为 LGPL-2.1+，上游迁址换许可，包名兼容） | 存量低延迟表达式场景。**GAV 升级已执行**（bone-parent 版本属性 + dependencyManagement + extension-sdk 三处 POM，javap 验证 4 个调用点签名一致、零代码迁移，7 模块构建通过） |
| `com.alibaba:qlexpress4` 4.1.3 | Apache-2.0（2026-08 活跃） | **bone-ai-rules 规则 DSL 默认实现**：表达式追踪（expression tracing）直接服务证据链"判定路径可回放"，且与 procuraAI 规则资产选型一致 |

**接入注意**：① QLExpress 4.x 为 Antlr4 重写（Bone 无存量，不受影响）；4.1.2 已 deprecated，直接用 4.1.3；② **"默认禁止脚本与应用代码交互"的安全策略必须显式保持**，禁止使用会关闭该保护的 open security strategy；③ 双方言并存为默认路径（extension-sdk 低延迟场景按需选用，SPI 按资产声明方言路由）；方言资产盘点结果为零（存量默认方言为 SpEL），新领域规则（如 procuraAI R01–R25）从第一天按 QLExpress DSL 编写，不产生新方言债；`AviatorExpressionEvaluator` 死实现处置（补测试或删除）属代码卫生议题，另案处理。

### 4.4 文档解析 sidecar 接口契约（冻结基线）

sidecar 不是部署形态的省略号，而是必须先冻结的契约——否则双解析器不可互换、单一解析器锁定风险实体化。八要素：

| 契约项 | 定义 |
|---|---|
| 通信协议 | HTTP REST（JSON）唯一基线（docling-serve 原生；PaddleOCR 经 serving 层包同协议）；gRPC 仅吞吐实测不达标时可选，不首发 |
| 输入 | ① 文件引用（MinIO objectKey + bucket，走 bone-file 存储规范）优先，大文件不走 base64 内联；② 解析配置（版面模式/语言/表格保留/页码范围）；③ 租户上下文（脱敏标识，决定 IR 回填策略） |
| 输出 | 统一 IR JSON schema（章节树/段落/表格/条款索引/span 坐标 + 置信度 + 页码锚点）——bone-ai-document 的领域模型，与解析器解耦；解析器特有信息进 `provider_meta` |
| 错误码 | 统一字典：`PARSE_TIMEOUT`/`PARSE_UNSUPPORTED_FORMAT`/`OCR_LOW_CONFIDENCE`（带阈值）/`SIDECAR_UNAVAILABLE`/`PAYLOAD_TOO_LARGE`，与 `ApiResponse` 规范对齐 |
| 超时与重试 | 按页数分档（默认 30s + 2s/页，上限 300s）；仅对幂等 `SIDECAR_UNAVAILABLE` 重试（指数退避 ≤2 次）；超时降级返回部分 IR + `partial=true`，由编排节点决定是否转 HITL |
| GPU 调度 | sidecar 与 Java 主进程分离部署（K8s 独立 Deployment，非 sidecar container pattern——GPU 独占性与扩缩容周期不同）；GPU 共享经推理批处理，单实例并发上限显式配置；CPU 形态（Docling）与 GPU 形态（PaddleOCR）是同一契约的两个实现，按文档形态路由 |
| 部署 | 独立 Deployment + HPA；`/healthz` 含模型加载状态；模型路径/并发/超时经 ConfigMap 注入 |
| 安全 | sidecar 不持租户凭证；文件访问经主进程签发短期 URL；IR 回传走内网（平台已启用 mTLS 则强制） |

契约冻结后 PaddleOCR 与 Docling 成为可互换实现——精度不达标或许可出问题时，替换成本 = 一个适配器类。这是"许可排雷前置"能真正生效的工程前提。

### 4.5 组件选型总表

| 能力 | 选型 | 许可 | 结论 |
|---|---|---|---|
| 文档解析（Java 基线） | Apache PDFBox / Tika / POI 5.3.0（已有） | Apache-2.0 | 默认管线 |
| OCR/复杂版面（sidecar） | **Docling**（MIT，LF AI & Data；生态含 docling-serve/docling-java/docling-eval）或 **PaddleOCR**（Apache-2.0，PP-StructureV3） | MIT / Apache-2.0 | 按形态路由：版面/表格→Docling（CPU 可起步），扫描件/纯 OCR→PaddleOCR |
| 精度升级候选 | MinerU ≥3.1.0（基于 Apache-2.0 的自定义许可） | 自定义 | 私有化内部部署基本不触发门槛、需法务确认版本与权重许可；对外 SaaS 不建议；OmniDocBench 93+ 精度留作不达标时候选 |
| （禁用） | PyMuPDF（AGPL/商业双授权）、Marker（权重 OpenRAIL-M，$5M 收入阈值）、Camelot（MIT 无许可问题，但并入 sidecar 契约以保双解析器可互换，不单独选型） | — | 产品侧历史选型需替换为 Docling/PaddleOCR/PDFBox |
| rerank | **本地 ONNX cross-encoder**（bge-reranker 系，数据不出域）为主；云端 rerank API 经网关代理计量为辅 | 随选型确认 | Spring AI 模型家族无 rerank 类型——不能等框架，bone-ai-rag 自建 |
| 规则表达式 | QLExpress 4.1.3（默认）+ Aviator 5.9.0（存量场景） | Apache-2.0 | 见 §4.3 |
| 向量库 | **pgvector 为最终形态，独立实例决策推迟至 bone-ai-rag 启动时实测**；过渡：MySQL JSON 列 + 应用层余弦（百万级以下）/ ES 向量检索（如有集群） | PostgreSQL 许可 | 不用 MySQL 8.0.31 主库存向量（无向量类型）；**禁用 pg_search**（AGPL-3.0）。推迟理由：新增独立 PG = 第二套备份/监控/HA 运维面，先实测量级再定形态，不为想象中的规模预付运维税 |
| 评测（离线辅助，可选） | RAGAS / promptfoo（Python sidecar） | Apache-2.0 / MIT | 可选增强；主链路用 bone-ai-eval 自建（judge 走网关计量）；解析质量口径参考 docling-eval |
| 模型（Qwen 系） | **27B 档 = Apache-2.0（已核定 2026-10-15，终局）**：**Qwen3.5-27B 与 Qwen3.8-27B 两代均为 Apache-2.0**——官方 HF `Qwen/Qwen3.5-27B` LICENSE、ModelScope/Ollama GGUF 元数据、apxml/CSDN/虎嗅/慧博多源互证；Qwen3.5 系 2026-02-24 发布、家族 0.8B–397B（9B/27B/35B-A3B/122B 型号命名真实有效，产品侧选型成立）；Max/Flash-Next 等档位为 Qwen License / Qwen Community License 1.0（>100M MAU 或 >$20M/月营收需 UI 展示模型名；MaaS/AI Work Assistant 需单独授权；纯内部使用豁免） | 混合 | 按部署形态分层：私有化交付基本豁免；自运营多租户 SaaS 开放模型能力触发 MaaS 条款需法务。**选型锁定 Apache-2.0 档位**。锁型建议：Qwen3.5（稳定性与生态已验证）与 Qwen3.8（能力更强）两代 27B 档许可均干净，按 POC 评测择一，随选型提交 SBOM 复核 |
| 安全 | 无银弹工具包：输入校验/分隔符封装/输出溯源校验/工具最小特权/对抗样本库 + 模型 SBOM + 新增依赖 CVE 扫描 | — | CVE-2026-59308（语义缓存跨租户）/59318（工具调用越权）即"不能依赖框架默认值"的实证 |

### 4.6 许可红线清单（POC 签前法务出 SBOM）

| 组件 | 状态 | 处置 |
|---|---|---|
| Dify 内核 | ⛔ 多租户运营需书面授权（LICENSE 原文），LOGO 不可移除，私有化不豁免 | 不引入 |
| Spring AI 官方 1.1.x（止于 1.1.8） | ❌ 4 个未修复 CVE + 已 EOL | 一期不引；若引只用修复版并按 §4.1 底线缓解 |
| `com.googlecode.aviator:aviator`（旧 GAV） | ⚠️ LGPL-2.1+ | ✅ 已升级 `io.github.aviatorscript:aviator` 5.9.0（Apache-2.0） |
| MinerU | ⚠️ 自定义许可（MAU/营收门槛 + 署名） | 私有化可需法务确认；对外 SaaS 不建议；默认 Docling/PaddleOCR |
| PyMuPDF / Marker / pg_search | ⛔/⚠️ AGPL 或自定义 | 替换为 PDFBox / PaddleOCR / Docling / pgvector |
| Qwen 非 Apache 档位 | ⚠️ MAU/营收/MaaS 条款 | 锁定 Apache-2.0 档（27B 档已核定）；其余按部署形态法务确认 |
| Aviator 5.9.0 / Docling / PaddleOCR / QLExpress 4.1.3 / pgvector | ✅ | 可用 |

### 4.7 LLM 网关：必要性、形态与业界对齐

**结论**：gateway 有必要，但必要的是"模型调用的**治理强制点**"，不是"大而全的网关服务"——当前落前者（库形态），按触发条件演进到后者。

**为什么必须有强制点**（业界均为集中式）：① 治理红线（成本/证据/PII）分散到调用点必被绕过，业界网关定义即"集中式策略强制点"（Kong）；② 多供应商是 2026 常态，OpenAI 兼容协议已是事实标准，路由/降级是三产品公共需求；③ 成本是财务口径（budgets/spend tracking/virtual keys 是业界网关标配），租户级配额只能平台强制；④ PII 防护集中在单一强制点才能保持一致审计轨迹（prompt/response 双向检测）；⑤ fallback/重试/熔断/超时是跨产品重复需求。

**诚实的反方与形态裁决**：单供应商 + 低量（<1 万次/日）+ 原型期，独立网关是负资产（额外一跳 10–95ms、自身成 SPOF）。故**一期 = 库形态**（进程内门面，延时≈零、无 SPOF）；**演进 = 集中式网关**（LiteLLM Proxy/Portkey 形态），触发条件任一满足即立项：非 JVM 调用方出现、跨产品集中配额/审计需求、或模型供应商 ≥3。演进成本可控：`LLMClient` 门面契约不变。

**一处刻意分歧**：业界建议网关故障 "fail open——直连供应商"（成本/可用性导向）；BONE 合规场景直连即断证据链。定案：**fail 到"纯确定性/人工替代"路径，绝不 fail 到直连模型**——合规红线不因可用性让步。

**能力落点对照（2026-10 对标 LiteLLM/Portkey/Kong/Cloudflare/Bedrock）**：

| 业界收敛能力 | 现状 | 落点 |
|---|---|---|
| 统一 OpenAI 兼容 API | ✅ 已有 | 保持 |
| 预算/配额/spend tracking、Virtual Keys 密钥托管 | ⬜ | P1 优先①：租户级 token 上限 + 计量落 trace；密钥出库走 bone-security，模型侧不持有 |
| 路由/负载/降级/重试 | ⬜ | P1 优先② |
| 精确缓存（exact cache） | ⬜ | P2 优先③：命中即不发 LLM，与成本红线同款短路 |
| 语义缓存 | ⚠️ 后置 | 租户 key + 完整哈希，租户隔离未实证前禁用（CVE-2026-59308 教训） |
| Guardrails / PII DLP | ⬜ | P1 优先④：出口脱敏；prompt injection 按 OWASP LLM Top 10（LLM01/LLM02）对表 |
| 调用日志/证据 | ✅ | 保持（超业界基线：哈希防篡改 + 回放） |
| 观测 | ⚠️ 部分 | P1 优先⑤：对齐 OTel GenAI 语义约定（`gen_ai.*` 字段映射版本化（operation.name/provider.name/request.model/response.model/usage.input_tokens/output_tokens/client.operation.duration/conversation.id），不硬编码；提示词/响应**默认不采集**，身份采集与内容采集分离） |
| MCP/工具治理 | 预留 | §4.1 双角色；工具 ACL default-deny |
| 精确/语义缓存之外的能力级灰度 | ⬜ | 每条 AI 能力带一键灰度开关，可瞬时降级为"纯确定性/人工替代"路径，监管或成本压力下秒级关停 |

> 业界对标来源（2026-10 实证）：litellm.ai、docs.langchain.com、konghq.com、apiscout.dev、pkgpulse.com、signoz.io、Dynatrace Hub；OTel GenAI 语义约定仍为 Development 状态，映射表须版本化。

---

## 5. 治理与质量门禁

**证据链结构强制**（不依赖调用方自觉上报）：① 网关是模型调用唯一入站边界，每调用必然发布 `LlmCallRecorded`、trace 必然落快照（结构上无旁路）；② ArchUnit 禁止业务代码 import `LlmClient` 实现类（只可依赖网关门面）；③ 响应对不上证据快照即拒绝返回（运行期断言）。**缺证据 = 拒绝返回**——可追溯性由代码保证，不由文档约定保证。

**网关治理面增补**：模型治理三项（路由/降级/租户配额）+ PII/商业机密出口强制脱敏（复用 `bone-security`，模型侧永不接触原始敏感字段）+ 能力级一键灰度降级。

**评测口径**：影子模式 + 人工抽检 + `reason_type` 回流是**运行机制**，不替代模块——最小 bone-ai-eval（评测集 + 指标字典 + CI 门禁）保留；静默升级为全量标注/评测平台属过度工程，不做；标注只走 `reason_type` 受控字典唯一入口。

**每阶段机械门禁**：`check.sh` + ArchUnit（AI 边界规则集：产品域不直连 AI 实现、AI 不 import 产品域、模型调用 HTTP/key 只在 gateway、业务禁 import `LlmClient` 实现类、AI 服务必须经 trace 落盘、资金/责任/数值判定入口禁收 `AiSuggestion`、禁 import 旧 Aviator GAV）+ 评测基线 + 负向探针。**新增依赖一律过 CVE/SBOM 扫描**（Spring AI 式选型错误靠此机械拦截）。

**治理机制**：每能力指定 owner + 语义化版本 + 评测基线门禁（指标口径沿用 procuraAI §10.1 指标字典）；平台 API 稳定封箱、领域产品经契约（HTTP/MCP）消费；平台能力变更走 L3 审批；入站边界治理约束继续有效（ADR-0028 `*ApplicationService`、`ApiResponse`/`PageResult`、全表租户隔离）。平台化不拖慢产品平台化不拖慢产品——契约先行、实现可替换，P0 四项以"平台默认实现 + 产品可覆盖"落地；MVP 阶段确定性工作流优先，防过度设计。

---

## 6. 落地计划

| 批次 | 范围 | 出口判据 |
|---|---|---|
| **第 0 批（POC，2 周）** | gateway 门面 + 自建 OpenAI 兼容客户端 POC（一家 provider + vLLM 本地）；法务 SBOM 首版；`LLMClient` 契约冻结；CVE 扫描门禁上线 | 门面后可替换实证（换 provider 不改业务代码）；成本红线开关有负向探针 |
| **第一批（最小公共内核）** | bone-ai-gateway（治理面）+ bone-ai-trace + bone-ai-rules **最小版**（规则资产 CRUD + 版本 + 命中标记 + SPI 方言路由）+ bone-ai-orchestration（含 HITL 子域）+ metadata 语义扩展 | procuraAI 场景端到端（解析→规则→LLM→HITL→快照回放，文档解析可用基线实现）；引用定位原文 100%；三元组可回放；`LLMClient` 双实现可替换实证；成本红线负向探针 |
| **第二批（解析与检索）** | bone-ai-document（sidecar 契约冻结）+ bone-ai-rag + bone-ai-rules 完整治理面（灰度/shadow/正反例回归 + 规则级兼容性检查补齐） | 文档 IR 引用率达标 + sidecar 契约冻结后**双解析器互换实证**；RAG 引用正确率 ≥98%；rerank 越权探测有负向探针；规则灰度/回归有负向探针（规则级兼容性检查补齐后）；pgvector 实例形态按 §4.5 实测决策 |
| **第三批（评测与交付）** | bone-ai-eval + bone-notification 消警闭环 + bone-integration AI 节点与三态开关 + 前端 bone-ai-app | 评测门禁挂 CI；客户接入从项目制变配置制（目标 ≤15 人日/客户）；三态切换可回退实证 |

**落地现状**：第一批中 gateway / trace 最小版已落地（§1.3 #10）——门面双实现可替换、成本红线负向探针在列；其余未动工。**第一批必须纳入 rules 最小版**：orchestration 的规则节点依赖它提供"规则命中不调 LLM"语义——若整体后置，端到端链路要么 stub 假跑（出口判据失真），要么规则逻辑写死在编排层（第二份真源）。

**平台级风险提示（需另行拍板）**：Bone 基线 Boot 3.5.16 已随 Boot 3.5.x 于 2026-06-30 到达 OSS EOL（3.5.16 为末版）。AI 中台不加深该风险（自建客户端零框架依赖），但政企 3–5 年周期下"平台跑在无社区补丁基线上"是总风险。**建议 2026-11-30 前二选一拍板**（责任人：平台架构组）：商业支持订阅（Tanzu 企业线至 2032-06-30 或 HeroDevs NES，覆盖范围须含 Bone 实际使用的全部 Spring 组合件，POC 签前法务确认）或 Boot 4 迁移排期（约 83 项 breaking change，Jackson 3/JDK 基线齐变）。两条纪律：① 不在 AI 中台项目内顺带升级 Boot，两件大事解耦、各自可回滚；② Boot 版本变更不与 AI 中台项目捆绑。

---

## 7. 拍板项与技术债

1. **L3 审批清单**：`bone-ai/` 新聚合定位、新 DDL（trace 快照表/评测集表）、QLExpress 4.1.3 与 Aviator 5.9.0 依赖、OCR sidecar 部署形态与契约（§4.4）、pgvector **决策点**（批准"独立实例推迟至 rag 启动实测"这件事本身）、是否仍要 Spring AI（若要：修复版来源与授权）。Spring AI 1.1.8 不在审批清单内（已否决）。
2. **技术债**：`MetadataVersionController` 字段级兼容性检查为反射实现（吞异常，有 hack 味）、关系/规则两级为空实现——bone-ai-rules 优先补齐规则级检查，否则灰度回归缺机器判据；反射实现建议顺手收敛。
3. **Boot 4 窗口**：Spring AI 2.x/MCP Server 能力列为后续窗口 POC，不捆绑本方案。
4. **pgvector 实例形态**：推迟至 rag 启动实测后决策（批准"推迟"而非批准具体实例）。
5. **模型锁型**：Qwen3.5/3.8 两代 27B 档均已核定 Apache-2.0（2026-10-15 终局，出处见 §4.5），型号命名疑问解除；锁型在两代间按 POC 评测择一并提交 SBOM 复核。
6. **代码卫生（另案）**：`AviatorExpressionEvaluator` 死实现处置。

---

## 结语

> **能沉淀的是"机制"**：解析、双引擎、人环、留痕、网关、检索、评测、安全、预警、交付——10 项中 8 项三产品同构，是产品地基与合规门槛，平台一次建设全部复用。
> **不能沉淀的是"资产"**：法规规则库、医学图谱、估值引擎、时点快照库——2–3 年积累的数据飞轮与 know-how，是壁垒，必须留在产品侧。
> **判断标准一句话**：跨域同构的工程机制进平台；领域知识与数据资产留产品；**平台只做"证明与治理"，不做"判断与决策"**。

---

## 附录 A：家底核查实证索引（供 L3 抽查）

| # | 断言 | 证据位置 |
|---|---|---|
| 1 | `AiMetadata` 为纯配置 POJO | `bone-engine/bone-metadata-engine/bone-metadata-engine-domain/src/main/java/com/bone/metadata/engine/domain/metadata/AiMetadata.java:13` |
| 2 | `AgentMetadata` 为纯配置 POJO | 同上目录 `AgentMetadata.java:14` |
| 3 | `ExtensionDecisionRecorder` 内存版（无持久化/租户/版本） | `bone-engine/bone-extension-engine/bone-extension-sdk/src/main/java/com/bone/engine/extension/infrastructure/ExtensionDecisionRecorder.java:13`（字段 :15–17） |
| 4 | `TenantAbstractEntity` 租户 + 审计基类 | `bone-framework/bone-core/src/main/java/com/bone/core/tenant/TenantAbstractEntity.java` |
| 5 | 已有规则执行运行时 | `bone-engine/bone-metadata-engine/bone-metadata-engine-runtime/src/main/java/com/bone/metadata/engine/runtime/RuleEngine.java`（832 行）；同目录 `BusinessRuleEngine.java`/`DefaultBusinessRuleEngine.java`/`BusinessRuleRegistry.java` |
| 6 | 工作流元数据建模齐全、无通用执行引擎 | `bone-metadata-engine-domain/.../domain/metadata/WorkflowMetadata.java`（含 `WorkflowState`/`WorkflowTransition`）；执行器仅 extension-studio `DeploymentStateMachine`（插件部署生命周期） |
| 7 | `MetadataVersionController` 字段级检查已实现、关系/规则两级空实现 | `bone-engine/bone-metadata-engine/bone-metadata-engine-runtime/src/main/java/com/bone/metadata/engine/runtime/version/MetadataVersionController.java` |
| 8 | `bone-file` 已完整实现（单 jar 含 Web 层） | `bone-platform/bone-file/`（22 个 java 文件；MinIO + Controller + JWT + 租户） |
| 9 | 表达式引擎 GAV 升级已执行（Apache-2.0） | `bone-parent/pom.xml` dependencyManagement + bone-extension-sdk POM；Sonatype Central 5.9.0 POM 许可 |
| 10 | Boot 基线 3.5.16（该线已 OSS EOL） | 根 `pom.xml`/`bone-parent/pom.xml`；endoflife.date/spring-boot（3.5.16 = 2026-06-25 末版，OSS 支持 2026-06-30 止） |
| 11 | 许可/CVE 外部实证 | pg_search AGPL-3.0（ParadeDB LICENSE）；Dify 多租户需书面授权（Dify LICENSE）；Spring AI 1.1.x EOL + 4 CVE（Central/spring.io security）；CVE-2026-59308 修复于 2.0.1（spring.io 发布公告）；QLExpress 4.1.3/Aviator 5.9.0 Apache-2.0；Qwen3.8-27B Apache-2.0（官方模型卡 + 多源互证） |
| 12 | bone-ai-gateway 最小版已落地 | `bone-ai/bone-ai-gateway/src/main/java/com/bone/ai/gateway/application/LlmGatewayService.java`（红线短路 + `LlmCallRecorded`）；`infrastructure/OpenAiCompatibleClient.java`/`MockLlmClient.java`；`config/LlmGatewayProperties.java` |
| 13 | bone-ai-trace 持久化证据链已落地 | `bone-ai/bone-ai-trace/src/main/java/com/bone/aitrace/domain/model/EvidenceSnapshot.java`；`infrastructure/event/EvidenceTraceListener.java`（SHA-256 就地计算）；`domain/repository/EvidenceSnapshotRepository.java`（`findByTraceId` 回放） |
