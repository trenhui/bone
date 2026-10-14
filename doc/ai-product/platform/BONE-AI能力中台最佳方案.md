# BONE AI 能力中台 · 最佳方案（综合版）

> **版本**：v3.4　**日期**：2026-10-14  
> **综合输入**：①《三大方案智能化能力沉淀图谱》（v1.1，能力同构证据）；②《BONE-AI能力沉淀模块规划与框架选型建议》（框架选型/许可排雷）；③ 平行聚合提案（`bone-ai/` 七模块 + 复用 `AiMetadata`/`AgentMetadata`/`ExtensionDecisionRecorder` 的思路）。  
> **方法**：先对全部存量断言做代码实证（读源码，不看注释/文档），再综合。  
> **v2.0 变更**：完成一轮技术选型复核（证据见 §6），推翻 v1.0 的 Spring AI 默认选型，修正 Aviator 处置路径，精细化 MinerU/Qwen 许可结论。  
> **v3.0 变更**：并入一轮外部复核建议（逐条裁定见 §8）——① **生命周期实证补强**：Spring AI 1.1.x 随 Boot 3.5.x **已于 2026-06-30 OSS EOL**（endoflife.date/HeroDevs/JCP 互证），且 **Bone 自身基线 3.5.16 同样过 EOL**，升为平台级显性风险（§3.1/§5）；② **第一批纳入 bone-ai-rules 最小版**，消除 orchestration→rules 依赖断档（§4）；③ 新增 **§3.5 文档解析 sidecar 接口契约**；④ pgvector 实例形态**推迟至 rag 启动实测决策**（§3.4）；⑤ QLExpress/Aviator 表达式语法兼容性评估列入第一批（§3.3）；⑥ HITL 三态状态表（§2.1）；⑦ MCP 双角色明确（§3.1）；⑧ 新增 **§9 家底核查复查清单**（断言→源码行号，供 L3 抽查）。  
> **v3.1 变更（执行记录）**：§3.3 的表达式方言兼容性评估**已执行完毕**（存量盘点 + GAV 升级 + 构建验证，详见 §3.3）；Aviator LGPL→Apache-2.0 许可修复已落地（三处 POM），§3.6 清单同步标注。  
> **v3.2 变更（二轮"复核+统一成稿"裁决并入，2026-10-14）**：逐条裁定见 §8 v3.2 表——① 证据链从"约定+自觉上报"升级为**结构强制**（网关唯一入站、无证据拒返、禁直连 LlmClient 门禁）；② HITL 口径消歧统一（复用 WorkflowMetadata 模型并补执行器，不复用 Camel Flow 做 AI 主编排）；③ 影子模式+人工抽检定性为**运行机制**，最小 eval 模块不降级；④ 向量库不自建并入 rag 边界，检索决策不下放 gateway；⑤ 网关治理四项 + **AiSuggestion/Confirmed 类型护栏**落位；⑥ 框架试点备选加许可排雷前置。⑦ **家底现状回写（2026-10-14）**：bone-ai-gateway / bone-ai-trace 最小版其实已在跑，§1 #10、§2.1、§4 落地现状注、§9 #13–14 同步——"全仓零 LLM 代码"类断言标注为 2026-10-13 快照，防 L3 抽查口径过时。  
> **v3.3 变更(业界最佳实践对标,2026-10-14)**:新增 **§3.7**--gateway 必要性论证(含反方评估与"何时不做"判据)+ 拓扑演进(库形态→集中式网关的触发条件)+ 业界收敛能力面对标落点(LiteLLM/Portkey/Kong/Cloudflare/Bedrock)+ OTel GenAI 观测标准对齐;与业界“fail open”通用建议的**刻意分歧**明示（fail 到确定性/人工路径，不 fail 到直连）。
> **v3.4 变更（2026-10-14 文档治理）**：① 头戳修正（v3.1→v3.4，原头戳未随 v3.2/v3.3 变更递增，README 索引已同步）；② §5 平台级风险补拍板期限（Boot EOL 二选一，建议 2026-11-30 前）；③ 联动《图谱》v1.1 落点指针化、《模块规划》v1.2 口径对齐。

---

## 1. 家底核查表（综合的起点）

| # | 断言 | 实证结果 | 对方案的影响 |
|---|---|---|---|
| 1 | `AiMetadata`/`AgentMetadata` 只是配置建模 | ✅ **属实（2026-10-13 快照）**：纯 POJO（enabled/modelName/promptTemplates/temperature…），被 `EntityMetadata` 持有；当时全仓 grep **无任何 LLM 客户端代码**（无 openai/dashscope/ChatModel，无 spring-ai/langchain4j 依赖）——**2026-10-14 起已变化，见 #10** | 当时结论"模型运行时真空、必须新建"成立；gateway 最小版**已落地**（#10），治理面待补 |
| 2 | `ExtensionDecisionRecorder` 是内存版 | ✅ **属实**（ArrayList + ConcurrentHashMap）：无持久化、无租户、无版本 | 升级为持久化证据链成立——**已落地**（bone-ai-trace，见 #10） |
| 3 | `TenantAbstractEntity` 提供租户+审计基类 | ✅ 属实（extends AbstractEntity 审计/删除 + tenantId） | `TraceableRecord` 直接继承 |
| 4 | `bone-file` "仅规划中" | ❌ **被推翻**：已完整实现——22 个 java 文件（`FileApplication` + adapter/application/domain/infrastructure），MinIO 存储、Controller、JWT、租户配置；但为**单 Maven jar**（含 Web 层） | C1 解析**复用 bone-file 的存取能力与存储配置规范**，但**不进程内依赖整个模块**（见 §2.2 依赖说明） |
| 5 | （前两案漏项）BONE 已有**规则执行运行时** | ✅ `RuleEngine`（832 行，执行 condition/expression，fail-fast+性能指标）、`BusinessRuleEngine`/`DefaultBusinessRuleEngine`/`BusinessRuleRegistry`；`MetadataVersionController` 有版本号生成 + **字段级**兼容性检查（反射读 type 字段 + 吞异常兜底，有 hack 味） | `bone-ai-rules` **不造执行引擎**——执行复用现有 RuleEngine，只做**治理面**（灰度/回归/正反例）+ AI 规则语义 |
| 6 | （前两案漏项）BONE 已有**工作流元数据模型但无执行器** | ✅ `WorkflowMetadata`/`WorkflowState`/`WorkflowTransition` 建模齐全（domain + runtime 仓储端口均有），全仓**无通用工作流执行引擎**（仅 extension-studio 的 `DeploymentStateMachine` 管插件部署生命周期） | `bone-ai-orchestration` **复用 WorkflowMetadata 模型**（扩展 AI 节点语义）并提供缺失的执行器——不新造状态机模型，守"一份数据一个真源" |
| 7 | `bone-integration` 已做连接器/流程编排 | ✅ 属实（Camel Flow/Connector/Outbox） | C10 落扩展，不新建 |
| 8 | `bone-notification` 已有 alert 域 | ✅ 属实（多渠道+租户） | C9 落扩展，不新建 |
| 9 | 表达式引擎现状 | ⚠️ 仅 `com.googlecode.aviator:aviator 5.4.1`（LGPL-2.1+，bone-parent 托管）；**但上游已迁移 groupId 并换许可**：`io.github.aviatorscript:aviator 5.9.0` 为 **Apache-2.0**（2026-02 发布，包名兼容） | **不必整体替换引擎**——存量升级 GAV 即解决许可；QLExpress 作为 bone-ai-rules 的规则 DSL 默认实现（理由见 §3） |
| 10 | （v3.2 现状回写）`bone-ai/` 落地现状 | ✅ **2026-10-14 实测**：最小网关 + 证据链已在跑——`bone-ai-gateway`（`LlmGatewayService` 门面：成本红线拒绝短路 + `LlmCallRecorded` 发布端；`LlmClient` 双实现 `OpenAiCompatibleClient`/`MockLlmClient`；`LlmGatewayProperties` 配置）；`bone-ai-trace`（`EvidenceSnapshot` 三元组：input_hash SHA-256 / payload IR / engine_version + 租户审计；`EvidenceTraceListener` 事件落盘；`findByTraceId` 回放查询；SDK 代理仓储持久化，不再是内存 recorder） | 第一批的 gateway/trace **骨架已成**（双实现 + 红线负向探针测试在列，见 §9 #13–14）；**剩余缺口** = 网关治理面（路由/降级/配额/脱敏/灰度/无证据拒返 + AI 边界 ArchUnit 规则集）+ orchestration/rules/document/rag/eval 全部未建 |

**结论**：BONE 的家底比两份提案估计的都厚——**规则执行、版本机制、工作流建模、文件存储、告警、集成编排都有了**；真正的空白只有五处：**LLM 运行时、工作流执行器、持久化证据链、文档解析 IR、RAG/评测**。**（v3.2 现状同步）**五处空白中"LLM 运行时"与"持久化证据链"的**最小版已于 2026-10-14 落地**（bone-ai-gateway / bone-ai-trace，见 #10）；当前剩余空白 = 工作流执行器、文档解析 IR、RAG/评测 + 网关治理面。最佳方案 = 只补空白 + 治理面，一切能复用的不新建。全部断言的源码位置与行号见 **§9 复查清单**（供 L3 审批抽查）。

---

## 2. 模块结构（推荐：新建平行聚合 `bone-ai/`）

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
│                  bone-integration（C10）、bone-frontend/bone-ai-app（:3013）
```

**为什么是独立聚合而不是塞进 `bone-engine/`**：① 7 个模块 + 独立版本线，"AI 是平台级能力维度"的架构意图需要物理边界；② 依赖单向——`bone-ai-*` 只依赖 framework/core/sdk，产品依赖 ai，ArchUnit 可机器强制"产品域不直连 AI 实现、AI 不碰产品域"；③ 许可隔离——自定义许可组件（sidecar 等）圈在聚合内，排雷面收敛。

### 2.1 逐模块（能力 → 复用存量 → 为什么必须新建）

| 模块 | 沉淀能力 | 复用 BONE 存量 | 为什么必须新建 |
|---|---|---|---|
| **bone-ai-gateway** | C5 网关 + C8 安全 | `bone-security` 脱敏、`bone-core` 租户上下文；`AiMetadata.modelName` 升级为模型档位引用 | 最小版已落地（成本红线 + 证据事件，见 §1 #10），**待补治理面**（优先级排序见 §3.7 业界对标）：路由/降级、配额、审计、灰度、PII 脱敏必须平台强制；**CVE-2026-59308（语义缓存跨租户串话）证明隔离层必须自建，不能依赖框架默认值** |
| **bone-ai-orchestration** | C2 执行 + C3 HITL | **`WorkflowMetadata` 模型**（状态/转换/初始态）；扩展引擎的路由决策思路 | 模型有、执行器无——这是 BONE 最明确的空白；三方案各自造了同一种引擎（规则节点+AI 节点+收敛+降级），复用收益最大的"操作系统级"资产 |
| **bone-ai-trace** | C4 证据链 | `TenantAbstractEntity`（租户+审计）；`ExtensionDecisionRecorder` 升级为持久化 | 内存 recorder 不可审计、无版本回放；195 号文/金融监管刚需"输入哈希+IR+引擎版本"三元组快照 |
| **bone-ai-rules** | C2 治理 | **`RuleEngine`/`BusinessRuleEngine` 执行**、`MetadataVersionController` 版本机制、extension-sdk 表达式 SPI | 执行已有但**治理没有**：版本/灰度/shadow/正反例回归/规则命中不调 LLM 的语义缺位；三方案的规则资产治理完全同构 |
| **bone-ai-document** | C1 解析与 IR | **bone-file 的存取能力与 MinIO 配置规范**（22 类已实现） | 解析管线是空白；条款级引用/证据链/锚点高亮的地基；重依赖（OCR/GPU）需与业务隔离 |
| **bone-ai-rag** | C6 受控 RAG | `bone-ai-trace`（引用溯源）、`bone-core` 租户隔离 | 三方案都有 RAG、BONE 没有；"无依据不编造"的控制逻辑是公共的，语料是领域的 |
| **bone-ai-eval** | C7 评测门禁 | 横切挂 `scripts/check.sh` 与发布流水线 | "你怎么证明它准"是政企必答题；评测汇聚 = 跨产品数据飞轮；judge 调用走网关才能计量 |

**HITL 的落点（调和两案）**：不单列**服务**，但**不是没有域**：`bone-ai-orchestration` 内嵌 HITL 子域（条目化确认 API + 置信度分级 + 三态 + `reason_type` 回流 trace），前端确认台落 `bone-ai-app`。即"引擎的一个收敛节点 + trace 的一条回写记录"，但有明确的应用服务与查询面。

**（v3.2 裁决消歧）**：注意不要误读为"复用 BONE 既有审批/任务引擎"——BONE **没有**原生审批/任务引擎可复用（`WorkflowMetadata` 建模齐全但无执行器，§1 #6；真实流程引擎是 bone-integration 的 Camel Flow，管集成编排、不裁决 AI）。唯一口径三句话：orchestration **复用 `WorkflowMetadata` 模型并补上缺失的执行器**；AI 到"待人工"即停在编排收敛节点等待裁决；bone-ai-hitl **不设独立模块、不自造审批流、也不把 AI 主编排托管给 Camel Flow**（避免第二份流程真源）。

**HITL 状态表（v3.0 补充，前后端契约基准）**：

| 状态 | 触发 | 语义 | 回流 |
|---|---|---|---|
| `pending` | 编排节点产出 AI 建议（含原文引用 + 依据 + 建议值 + 置信度） | 待人工裁决；置信度 <0.7 时强制进入（不得自动直出） | — |
| `confirmed` | 人工确认建议值 | 建议值生效，进入下一节点 | trace 记 `decision=confirmed` + 操作人 + 时间 |
| `rejected` | 人工驳回 | 建议值作废，按编排定义走驳回分支（拒绝流转/转规则兜底） | trace 记 `reason_type`（分类码，非自由文本）+ 备注 |
| `modified` | 人工改判为其他值 | 改判值生效；同时作为**负样本/纠错样本**进回流 | trace 记 `reason_type` + 原建议值 vs 改判值 diff |

`reason_type` 用受控字典（如 `RULE_MISS` / `CONTEXT_MISS` / `SUGGESTION_WRONG` / `POLICY_CONFLICT`），是 C3 反馈回流与 C7 评测集扩充的唯一入口——自由文本不进回流管道（避免脏数据污染评测）。

**C4 的定位裁决（v2.0 消歧）**：bone-ai-trace **建独立模块**（快照有领域语义：三元组 + 版本链 + 回放查询 API）；`bone-framework` 只提供基类（`TenantAbstractEntity` 谱系）与留存策略常量，不承载证据链 API。打消"独立服务会变旁路"顾虑的机制：**所有 AI 服务强制经 trace 的 Spring 事件监听/拦截器落盘**（ArchUnit + 运行期断言双重保证）。

**证据链结构强制（v3.2 升级，回应"约定必被绕过"）**：不依赖调用方自觉上报（"SDK 里记得调 `enqueueEvidence`"恰恰是能被绕过的那类契约）——网关是模型调用**唯一入站边界**，每调用必然发布 `LlmCallRecorded`、trace 必然落快照，结构上无旁路。再补两条机器强制：① ArchUnit 门禁禁止业务代码 import `LlmClient` 实现类（只可依赖网关门面）；② 响应对不上证据快照即拒绝返回（运行期断言）。**缺证据 = 拒绝返回**，可追溯性由代码保证，不由文档约定保证。

**网关治理面增补 + 评测口径（v3.2）**：① 模型治理三项补齐——路由（场景→模型档位）、降级（主模型失败切备用）、配额（租户级 token 上限档位）；② PII/商业机密在网关**出口层强制脱敏**，模型侧永远不接触原始敏感字段（复用 `bone-security`，强制点前置到出口）；③ 每条 AI 能力带**一键灰度开关**，可瞬时降级为"纯确定性/人工替代"路径，监管或成本压力下秒级关停。评测裁决："影子模式 + 人工抽检 + `reason_type` 回流"是**运行机制**，不替代模块——最小 eval（评测集 + 指标字典 + CI 门禁）保留为骨料；静默升级为全量标注/评测平台属过度工程，不做；标注只走 `reason_type` 受控字典唯一入口。

**存量扩展（不新建的理由）**：

- `bone-metadata-engine`：`AiMetadata` 增加模型档位/网关引用/置信度阈值字段；`WorkflowMetadata` 增加 AI 节点/收敛/降级的元数据语义——**建模归 engine，执行归 orchestration**；
- `bone-notification`：C9 增加规则触发 + 消警闭环（已有 alert 域只缺触发与回写）；
- `bone-integration`：C10 增加 AI 节点类型 + Shadow→Assisted→Automated 三态开关（已有 Camel Flow/Connector/Outbox）。

### 2.2 依赖关系（无环）

```
bone-ai-gateway      → bone-security / bone-core
bone-ai-trace        → bone-core（TenantAbstractEntity）
bone-ai-rules        → bone-metadata-engine（RuleEngine/MetadataVersionController）+ extension-sdk（表达式 SPI）+ bone-metadata-sdk
bone-ai-document     → bone-ai-gateway（OCR/抽取模型调用，脱敏后进）
                       + bone-file：**不进程内依赖**（bone-file 为单 jar 含 Web 层），走自建 FileStorage Port，
                       默认实现复用其 MinIO 客户端配置规范直连存储；或 HTTP 调 bone-file 服务
bone-ai-orchestration → bone-ai-gateway + bone-ai-rules + bone-ai-trace + bone-metadata-engine（WorkflowMetadata 模型）
bone-ai-rag          → bone-ai-trace + bone-ai-gateway + pgvector
bone-ai-eval         → 横切，被发布流水线调用，不依赖业务
产品域（procuraAI/ClaimSphere/Equity）→ bone-ai-* + bone-integration + bone-notification
```

**分层铁律**（与三方案一致）：确定性内核（规则/计算/资金状态机）永远在 domain/产品侧；AI 模块只提供"建议 + 证据"，不参与最终裁决。**护栏加码（v3.2）**：不靠运行时嗅探"AI 输出流向"（出口可伪造、嗅探有盲区），靠**类型强制**——AI 结论一律 `AiSuggestion<T>`（含置信度 + 证据引用），资金/责任/数值判定入口只收 `Confirmed<T>`（经 HITL 裁决或规则产出），二者不可隐式互转，配 ArchUnit 门禁机器强制。

**检索边界裁决（v3.2）**：不自建向量库（pgvector 实例形态仍按 §3.4 推迟实测决策，过渡期 MySQL JSON/ES），但检索策略（切分/权限过滤/引用强制/RRF/rerank）留在 `bone-ai-rag`——gateway 只做检索调用代理/适配，**不下放检索决策**，否则网关变上帝模块，与"gateway 不编排、不决策"自相矛盾。

---

## 3. 框架与工具包结论（v2.0 复核修订版，v3.0 并入外部复核：生命周期实证 / sidecar 契约 / pgvector 推迟）

### 3.1 LLM 抽象层：**一期不默认引入 Spring AI**（v1.0 结论"引入 1.1.8"已推翻）

v1.0 的依据是"官方 1.1.x ↔ Boot 3.5.x 唯一对应线"，但复核 CVE 记录后发现该线**安全状态不达标**：

| 路径 | 版本现状（2026-10-13 实证） | CVE 状态 | 评估 |
|---|---|---|---|
| A. HeroDevs ELS | **1.1.10**（2026-09-01）修复 CVE-2026-47851/59318/47852/59294 四项 | 已修复 | 备选：商业 ELS fork，坐标/获取方式/授权需向 HeroDevs 确认，法务过目 |
| B. 官方 1.1.x | Central 止于 **1.1.8**（2026-06-13），此后仅 SNAPSHOT；**该线随 Spring Boot 3.5.x 已于 2026-06-30 到达 OSS EOL**（endoflife.date + HeroDevs + JCP release model 互证）——即当前处于"无社区补丁"状态 | ❌ **4 个未修复 CVE，其中 2 个 high**（PDF outline 栈耗尽、ResourceCacheService 可预测临时目录致 ONNX 模型被替换、路径穿越、工具调用越权） | 不再作为默认选型（安全 + 生命周期双重不达标） |
| C. 官方 2.x | 2.0.1（2026-08-21，Boot 4.1.0）/ 2.1.0-M1（Boot 4.2） | 已修复 | 绑定 Boot 4，与 Bone 3.5.16 终版基线冲突，列为后续升级窗口事项 |
| D. **一期不引，自建 OpenAI 兼容客户端** | vLLM / Ollama / DashScope / DeepSeek / OneAPI 均为 OpenAI chat/completions 兼容协议 | 不涉及 | ✅ **推荐**（理由见下） |

**生命周期实证（v3.0 补强，回应"维护窗口风险"复核意见）**：Spring AI 1.x 与 Spring Boot 3.5.x 同生命周期——Boot 3.5.x OSS 支持已于 **2026-06-30 结束**（endoflife.date：3.5.16 为末版 2026-06-25 发布；JCP 官方 release model 载 3.5.x EOL 于 2026-05～06 区间；HeroDevs/byteiota 均以此为"EOL crunch"）。这一实证把复核意见估计的"预计 2027 年内"修正为**已经发生**，影响两面：

1. **对 Spring AI 选型**：1.1.x 不是"暂缺补丁、等后续版本"，而是整线 EOL——本期选型 D（不引）既是安全决策，也是生命周期决策；
2. **对 Bone 平台基线（超出本方案范围，但必须显性化）**：Bone 自身停在 Boot 3.5.16，**同样已过 OSS EOL**——新增 CVE 不会有社区回移植。政企项目 3–5 年周期下，"平台跑在无社区补丁的基线上"是平台级硬风险，需另行拍板（见 §5 第 5 条）：商业支持（Tanzu 企业线至 2032-06-30 或 HeroDevs NES）或规划 Boot 4 迁移（Broadcom 估算 Boot 4.0 约 83 项 breaking change，Jackson 3/JDK 基线齐变，非周末工程）。**本方案的自建客户端路径不依赖任何 Spring AI 版本，不受 1.x→2.x API 断代影响**——这正是选型 D 相对"押注 1.1.8"的结构性优势。

**推荐 D 的理由**：
1. gateway 的核心价值（路由/配额/审计/脱敏/灰度/成本红线）与 Spring AI 无关——后者提供的是多 provider SDK 抽象与 MCP 支持；
2. OpenAI 兼容协议已是事实标准，自建客户端 ≈ WebClient（Bone 已有）+ 结构化输出（JSON mode 协议原生支持）+ Micrometer 观测包一层，**零新依赖**；
3. 彻底绕开"1.1.x 有 CVE / 2.x 绑 Boot 4"的两难，也不把 L3 审批押在一个安全状态未定的框架上；
4. `LLMClient` 门面契约不变——未来 Boot 4 窗口迁 Spring AI 2.x（或 HeroDevs 修复版）时，替换的是门面后的一个实现类，升级成本从"全仓改造"降为"一个模块一个类"；
5. 若团队评估后仍要引入 Spring AI：**只用修复版（A 或等官方），且 tool calling 默认关闭（CVE-2026-59318 利用面）、语义缓存禁用或平台自建（租户 key + 完整长度哈希）、PDF outline 深度限制**。

**MCP 双角色定位（v3.0 明确）**：MCP 在 Bone 中按两个角色预留，均在 gateway 架构设计中占位：

1. **Bone 作 MCP Server**：把 bone-ai-gateway 的模型调用、bone-ai-rag 的检索、bone-ai-rules 的规则执行以 MCP Tool 暴露，供产品侧智能体（如 procuraAI 的 LangGraph）经 MCP 消费——"平台做能力、产品做智能体"的契约边界；
2. **Bone 作 MCP Client**：未来消费外部 MCP Server（第三方系统工具能力）时经 Client 接入；Client 与 Server 角色共存于网关的同一 Tool 注册表，权限/配额/审计走同一出口，不出现"绕过网关的第二个模型调用面"。

**版本线提醒**：不默认捆绑 Spring AI——MCP Java SDK 自身版本线（0.18.x ↔ Spring AI 1.1.x / 2.0.0 ↔ 2.x）需同步复核其 CVE 状态（CVE-2026-59279 即 MCP Streamable HTTP server 的会话无界增长漏洞，影响 2.0.0、修复于 2.0.1——spring.io/security 实证），MCP Server 能力作为独立 POC 项，不阻塞 gateway 建设。

### 3.2 编排 / Agent 框架：**不引入**（结论不变，理由复核后仍成立）

候选：LangGraph（Python）、AutoGen、CrewAI、Dify 内核、Spring AI Alibaba Agent 框架。四条理由（均已实证）：

1. **架构决策不匹配**：三方案的核心全部是"确定性工作流 + LLM 节点 + 规则兜底 + HITL"——可复现/可回放/人工终审的合规要求与自主 Agent 的开放式推理直接冲突；
2. **已有真源**：bone-integration 的 Camel Flow + bone-ai-orchestration 的显式状态机就是编排内核，再引一套 = 第二份流程真源；
3. **Dify 许可红线（LICENSE 原文实证）**：Apache-2.0 修改版——**未经书面授权不得运营多租户环境（tenant=workspace）**，前端 LOGO 不可移除。Bone 本身是多租户平台 → 嵌 Dify 内核即踩线，且会引入第二套租户/权限/审计模型。**注意该限制同样覆盖私有化交付场景**：Bone 作为产品交付给客户、客户在自有环境运行多租户实例时，Dify 的多租户条款依然触发——政企私有化不是豁免情形，只是让许可纠纷发生在客户现场，且客户会要求 Bone 侧兜底；
4. **成本红线框架不管**："规则命中不调 LLM"必须平台强制（网关），Agent 框架的自主循环恰恰是成本失控面。

**替代方案**：① 编排 = orchestration 显式状态机 + bone-integration AI 节点；② 工具/能力暴露 = **MCP 协议**（Bone 作 Server，产品智能体作 Client）；③ 例外条款——未来场景确需多智能体时（Equity §6.2"复杂度驱动"），在状态机内以子图 + 工具调用实现，不引框架。

**框架试点前置条件（v3.2，回应"试点用框架、验证后抽回"备选）**：该路线**不可默认执行**，须同时满足四条件——① 先过 §3.4/§3.6 的许可排雷 + CVE/SBOM 门禁（Spring AI 即因安全 + 生命周期双重不达标被否，未过排雷的框架无资格进试点）；② 仅限 procuraAI 单产品内部，框架代码不进 `bone-ai-*` 核心模块；③ 试点的所有 LLM 调用仍走网关（否则证据链断裂，试点数据不可用）；④ 试点结束把角色/扇出/证据结构抽回 orchestration，框架不随产品交付蔓延。任一条件不满足，试点自动回落"自建客户端 + orchestration"路径。

### 3.3 规则表达式：**双引擎策略**（v1.0"Aviator 退位"已修正）

| 引擎 | 许可实证 | 定位 |
|---|---|---|
| `com.googlecode.aviator:aviator` 5.4.1 | LGPL-2.1+（POM + 源码头实证） | 存量：**GAV 升级已执行（2026-10-14）**——`io.github.aviatorscript:aviator 5.9.0`（Apache-2.0，包名保持 `com.googlecode.aviator` 不变，javap 对比 4 个调用点签名一致，零代码迁移）；构建验证 7 模块 BUILD SUCCESS；保留服务低延迟表达式场景 |
| `com.alibaba:qlexpress4` 4.1.3 | Apache-2.0（2026-08-15，季度活跃） | **bone-ai-rules 规则 DSL 默认实现**：表达式追踪（expression tracing，AviatorScript 无此文档化能力）直接服务 C4 证据链的"判定路径可回放"；与 procuraAI 规则资产选型一致，利于规则互通 |

**QLExpress 接入注意**（实证）：① 4.x 用 Antlr4 重写解析器，3.x→4.x 是迁移不是原地升级（Bone 无存量，不受影响）；② 4.1.2 已 deprecated，直接用 4.1.3；③ **默认禁止脚本与应用代码交互的安全策略必须保持显式配置**——文档示例中的 open security strategy 会关掉该保护，禁止使用。

**表达式语法兼容性评估（2026-10-14 已执行，结论如下）**：依赖切换 ≠ 资产无缝迁移——本评估先盘点存量再定路径，实证结果：

- **使用面**：全仓仅 `AviatorExpressionEvaluator` 一个类消费 Aviator API（4 个静态调用点：`AviatorEvaluator.getInstance()`/`compile()`/`Expression.execute()`/`setCachedExpressionByDefault`；javap 对比 5.4.1↔5.9.0 签名一致 → 零代码迁移成立）
- **方言资产存量 = 0**：`ExtensionAutoConfiguration.java:154`、`DefaultExtensionPointRouter.java:67`、`ExtensionRegister.java:48` 三处默认 Wiring 全部是 `SpELExpressionEvaluator`；`AviatorExpressionEvaluator` 是无测试覆盖、README 零提及的死实现；运行期表达式来自 Nacos 元数据，引擎默认方言即 SpEL
- **GAV 升级已落地并验证**：三处 POM（bone-parent 版本属性 + dependencyManagement + extension-sdk 可选依赖，均带许可迁移注释）；`mvn -pl bone-extension-sdk -am test` 7 模块 BUILD SUCCESS（bone-core 228 + extension-sdk 45 测试，ArchUnit + spotless 全过）；依赖树实证 `io.github.aviatorscript:aviator:jar:5.9.0:compile`
- **三路径成本重估**：②全量迁移 QLExpress 成本因零存量从"最高"降为"低"——但仍维持 ①**双方言并存**为默认路径（保留 extension-sdk 的低延迟表达式能力供消费者按需选用；bone-ai-rules 新规则走 QLExpress，SPI 按资产声明方言路由）；③兼容层不采纳。procuraAI R01–R25 等新领域规则从第一天按 QLExpress DSL 编写，不产生新方言债
- **遗留项（单独提案，不阻塞）**：`AviatorExpressionEvaluator` 死实现处置（补测试 + 文档化选用方式，或删除）属代码卫生议题，另案处理

### 3.4 组件级工具包（许可排雷前置，全部经官方来源实证）

| 能力 | 选型 | 许可实证 | 结论 |
|---|---|---|---|
| 文档解析（Java 基线） | Apache PDFBox / Tika / POI 5.3.0（已有） | Apache-2.0 | 默认管线 |
| OCR/版面（sidecar） | **Docling**（MIT，LF AI & Data 托管，IBM Research；生态含 docling-serve REST、docling-java、docling-eval）或 **PaddleOCR**（Apache-2.0，PP-StructureV3 版面/表格） | MIT / Apache-2.0 | 按形态路由：版面/表格→Docling（CPU 可起步），扫描件/纯 OCR→PaddleOCR |
| （重新定性）MinerU | ≥3.1.0 由 AGPLv3 切换为**基于 Apache-2.0 的自定义许可**：>100M MAU 或 >$20M/月营收需商业授权、对外在线服务须署名 | 自定义 | ⚠️ 从"⛔避开"调整为"**私有化内部部署基本不触发门槛，需法务确认具体版本与模型权重许可；对外 SaaS 不建议**"。默认仍 Docling/PaddleOCR；其 OmniDocBench 93+ 的解析精度留作精度不达标时的升级候选 |
| （避开） | PyMuPDF（AGPL/商业双授权）、Marker（权重 OpenRAIL-M，$5M 阈值） | — | procuraAI 技术方案里的 PyMuPDF/Camelot 选型需在 Bone 侧替换 |
| rerank（v2.0 新增） | **本地 ONNX cross-encoder**（bge-reranker 系，数据不出域）为主；云端 rerank API 经网关代理计量为辅 | 模型许可需随选型确认 | **Spring AI 模型家族无 rerank 类型**（官方清单仅 Chat/Embedding/Image/Audio/Moderation）——rerank 不能等框架， bone-ai-rag 自建 |
| 向量库 | **pgvector 为最终形态，但独立实例决策推迟至 bone-ai-rag 启动时**（基于实测量级与性能测试）；过渡选项：MySQL JSON 列 + 应用层余弦相似度（百万级以下向量）/ ES/OpenSearch 向量检索（如 Bone 已有或规划集群） | PostgreSQL 许可证（宽松） | 不用 MySQL 8.0.31 主库直接存向量（无向量类型）；不用 pg_search（**AGPL-3.0 实证**：ParadeDB Community 版，Enterprise 才豁免 copyleft）。**推迟理由**：主库已是 MySQL 8.0.31，新增独立 PG = 第二套备份/监控/连接池/HA 运维面，政企场景成本不小——先用最小方案把检索量级与性能实测出来，再定实例形态，避免为想象中的规模提前付运维税 |
| 评测（离线流水线，可选） | RAGAS / promptfoo（Python sidecar） | Apache-2.0 / MIT | 可选增强；平台主链路用 bone-ai-eval 自建（judge 走网关计量）；解析质量口径可参考 docling-eval |
| 模型许可（Qwen 系，v2.0 精细化） | **Qwen3.8-27B 为 Apache-2.0**（procuraAI 选的正是 27B 档 → 许可干净）；Max/Flash-Next 等档位为 Qwen License / Qwen Community License 1.0：>100M MAU 或 >$20M/月营收需 UI 显著展示模型名，MaaS / AI Work Assistant 业务需单独授权，**纯内部使用（不暴露第三方）豁免** | 混合 | 结论按部署形态分层：私有化交付（客户内部用）→ 基本豁免；Bone 自运营多租户 SaaS 开放模型能力 → 触发 MaaS 条款，需法务。**选型时锁定 Apache-2.0 档位尺寸** |

### 3.5 文档解析 sidecar 接口契约（v3.0 新增）

"sidecar"不是部署形态的省略号，而是必须先冻结的契约——否则 PaddleOCR/Docling 双实现不可互换、单一解析器锁定风险会实体化。契约八要素：

| 契约项 | 定义 |
|---|---|
| 通信协议 | **HTTP REST（JSON）为唯一基线**（docling-serve 原生支持；PaddleOCR 经 serving 层包成同协议）；gRPC 仅在吞吐实测不达标时作为可选优化，不首发 |
| 输入 | ① 文件引用（MinIO objectKey + bucket，走 bone-file 存储规范）**优先**，大文件不走 base64 内联；② 解析配置（版面模式/语言/表格结构保留/页码范围）；③ 租户上下文（脱敏标识，决定 IR 回填策略） |
| 输出 | **统一 IR JSON schema**（章节树/段落/表格/条款索引/span 坐标 + 置信度 + 页码锚点）——这是 bone-ai-document 的领域模型，与具体解析器解耦；解析器特有信息进 `provider_meta` 扩展字段 |
| 错误码 | 统一字典：`PARSE_TIMEOUT` / `PARSE_UNSUPPORTED_FORMAT` / `OCR_LOW_CONFIDENCE`（带阈值）/ `SIDECAR_UNAVAILABLE` / `PAYLOAD_TOO_LARGE`；与 Bone `ApiResponse` 错误码规范对齐 |
| 超时与重试 | 单文档超时按页数分档（默认 30s + 2s/页，上限 300s）；重试仅对幂等的 `SIDECAR_UNAVAILABLE`，指数退避 ≤2 次；超时走降级（返回部分 IR + `partial=true`，由编排节点决定是否转 HITL） |
| GPU 调度 | sidecar 与 Java 主进程**分离部署**（K8s 独立 Deployment，非 sidecar container pattern——GPU 独占性与主进程扩缩容周期不同）；GPU 共享经推理批处理，单实例并发上限显式配置；CPU 形态（Docling）与 GPU 形态（PaddleOCR）是同一契约的两个实现，按文档形态路由 |
| 部署形态 | 独立 Deployment + HPA；健康检查 `/healthz`（含模型加载状态）；模型路径/并发/超时阈值经 ConfigMap 注入 |
| 安全 | sidecar 不持有租户凭证；文件访问经主进程签发的短期 URL；IR 回传走内网（如平台已启用 mTLS 则强制） |

契约一旦冻结，PaddleOCR 与 Docling 成为可互换实现——解析精度不达标或默认选型出许可问题时，替换成本 = 一个适配器类。这也是"许可排雷前置"能真正生效的工程前提。

### 3.6 许可红线清单（POC 签前法务出 SBOM）

| 组件 | 状态 | 处置 |
|---|---|---|
| Dify | ⛔ 多租户运营需商业授权（LICENSE 原文实证） | 不引入 |
| `com.googlecode.aviator:aviator`（旧 GAV） | ⚠️ LGPL-2.1+ | ✅ **已升级（2026-10-14）**：`io.github.aviatorscript:aviator 5.9.0`（Apache-2.0），三处 POM 迁移 + 7 模块构建验证通过（详见 §3.3） |
| Spring AI 1.1.8（官方线末版） | ❌ 4 个未修复 CVE（2 high） | 一期不引；若引只用修复版且按 §3.1 底线缓解 |
| MinerU | ⚠️ 自定义许可（MAU/营收门槛 + 署名） | 私有化可用需法务确认；对外 SaaS 不建议 |
| PyMuPDF / Marker / pg_search | ⛔/⚠️ AGPL 或自定义 | 替换为 Docling / PaddleOCR / PDFBox / pgvector |
| Qwen 非 Apache 档位 | ⚠️ MAU/营收/MaaS 条款 | 选型锁定 Apache-2.0 档位（27B）；其余按部署形态法务确认 |
| Docling / PaddleOCR / QLExpress 4.1.3 / pgvector / Aviator 5.9.0 | ✅ Apache-2.0/MIT/PostgreSQL | 可用 |

### 3.7 LLM 网关必要性论证与业界对标（v3.3 新增）

**结论先行**：`bone-ai-gateway` **有必要**，但必要的是"模型调用的**治理强制点**"，不是"大而全的网关服务"——两者是同一资产的两个演进阶段，当前落前者（库形态），按触发条件演进到后者。

**为什么必须有强制点（业界均为集中式，五条）**：

1. **治理红线不可分散**：业界对网关的定义即"a centralized point of policy enforcement for routing, governing, securing, and observing AI traffic at scale"（Kong，2026-05）——成本红线/证据链/PII 脱敏散到产品调用点必被绕过，这正是 §2.1"结构强制"的由来；
2. **多供应商是 2026 常态**：OpenAI 兼容协议已是事实标准（LiteLLM 以一套协议接 100+ 供应商）；procuraAI 私有化 Qwen/vLLM 与云端档位并存，路由/降级是三产品公共需求；
3. **成本是财务口径**：budgets / spend tracking / virtual keys 是业界网关标配（LiteLLM/Portkey），租户级 token 配额只能平台强制，产品侧自觉记账不可信；
4. **合规审计一条轨迹**：PII 防护"集中在单一强制点才能保持审计轨迹一致"（Kong AI PII Sanitizer：20+ PII 类，prompt/response 双向检测，合成替换/还原/命中拦截同一审计轨迹）——v3.2 出口脱敏就是这一模式；
5. **可靠性公共能力**：fallback/重试/熔断/超时是跨产品重复需求，业界对比无网关时的现状：`Manual try/catch chains`（APIScout，2026-03）。

**诚实的反方评估（业界何时不需要网关）**：单供应商 + 低量（<1 万次调用/日）+ 原型期，独立网关是负资产——额外一跳 10–95ms、网关自身成 SPOF（APIScout/PkgPulse 对比实测）。故形态裁决：**一期 = 库形态**（Spring Boot starter 进程内门面，延时≈零、无 SPOF，现状即 `LlmGatewayAutoConfiguration`）；**演进 = 集中式网关**（LiteLLM Proxy/Portkey 形态），触发条件任一满足即立项——非 JVM 调用方出现（FDE 侧 Python/Node）、跨产品集中配额/审计需求、或模型供应商 ≥3。演进成本可控：`LLMClient` 门面契约不变，切换 = 一个模块一个类（§3.1 选型 D 理由 4 的同款纪律）。

**与业界通用建议的一处刻意分歧**：业界建议网关故障时 "fail open——直连供应商"（APIScout），那是成本/可用性导向；BONE 合规场景直连即断证据链。裁决：**fail 到"纯确定性/人工替代"路径（v3.2 一键灰度），绝不 fail 到直连模型**——合规红线不因可用性让步。

**业界收敛能力面 → bone-ai-gateway 落点**（2026-10 对标 LiteLLM/Portkey/Kong/Cloudflare/Bedrock）：

| 业界收敛能力 | 现状 | 计划落点 |
|---|---|---|
| 统一 OpenAI 兼容 API | ✅ 已有（`OpenAiCompatibleClient`） | 保持 |
| 路由/负载/降级/重试 | ⬜ | P1 治理面（§4，优先级②） |
| 预算/配额/spend tracking | ⬜ | P1（优先级①）：租户级 token 上限 + 计量落 trace |
| Virtual Keys / 密钥托管 | ⬜ | P1（优先级①）：密钥出库走 bone-security，模型侧不持有 |
| 调用日志/证据 | ✅ `LlmCallRecorded` + trace | 保持（**超业界基线**：哈希防篡改 + 回放） |
| 精确缓存（exact cache） | ⬜ | P2（优先级③）：命中即不发 LLM，与成本红线同款短路语义 |
| 语义缓存 | ⚠️ 后置 | CVE-2026-59308 教训：租户 key + 完整哈希，租户隔离未实证前禁用 |
| Guardrails / PII DLP | ⬜ | P1（优先级④）：出口脱敏（v3.2）；prompt injection 防护按 OWASP LLM Top 10（LLM01/LLM02）逐项对表 |
| 观测 | ⚠️ 部分 | 对齐 OTel GenAI 语义约定（字段映射见下，优先级⑤） |
| MCP/工具治理 | 预留 | §3.1 双角色；工具 ACL default-deny 参考业界模式（Kong MCP Tool ACLs） |

**观测标准对齐（OTel GenAI 语义约定，已是行业标准）**：`LlmCallRecorded`/`EvidenceSnapshot` 字段映射到 `gen_ai.*`——`gen_ai.operation.name`（chat）、`gen_ai.provider.name`、`gen_ai.request.model`/`gen_ai.response.model`、`gen_ai.usage.input_tokens/output_tokens`、`gen_ai.client.operation.duration`、`gen_ai.conversation.id`。两点注意：① 该规范仍 Development 状态（`gen_ai.system` 已废弃、改 `gen_ai.provider.name`），映射表要版本化，别把规范常量硬编码进领域模型；② 提示词/响应内容**默认不采集**（业界惯例：身份采集与内容采集分离、默认全关），与"模型侧不见原始敏感字段"同款纪律。对齐收益：客户现有 Grafana/Dynatrace/Jaeger 直接消费 AI 遥测，不需另配一套。

> **来源**（2026-10 实证）：litellm.ai/oss + docs.litellm.ai（能力面/OWASP LLM Top 10 控制映射）；docs.langchain.com Portkey 集成（路由/语义缓存/trace_id）；konghq.com《LiteLLM vs Kong》（集中式强制点/PII DLP/MCP Tool ACLs）；apiscout.dev + pkgpulse.com（延时实测/何时不用网关/fail-open 建议）；signoz.io + agentpatterns.ai + Dynatrace Hub（OTel GenAI 语义约定与内容采集默认关闭惯例）。

---

## 4. 落地批次（按"空白优先级"重排）

| 批次 | 模块 | 出口判据 |
|---|---|---|
| **第一批**（最小公共内核） | bone-ai-gateway + bone-ai-trace + **bone-ai-rules（最小版）** + bone-ai-orchestration（+ metadata 的 AiMetadata/WorkflowMetadata 语义扩展） | procuraAI 场景端到端：解析→规则→LLM→HITL→快照回放；引用定位原文 100%；三元组可回放；**LLMClient 门面双实现（mock/真实）可替换实证；成本红线开关（规则命中不调 LLM）有负向探针**（表达式方言兼容性评估已于 2026-10-14 提前完成，见 §3.3） |
| **第二批** | bone-ai-document + bone-ai-rag + bone-ai-rules（完整治理面） | 文档 IR 引用率达标 + **sidecar 契约冻结后双解析器互换实证**；RAG 引用正确率 ≥98%；rerank 越权探测；规则灰度/回归有负向探针（含 `MetadataVersionController` 规则级兼容性检查补齐后）；pgvector 实例形态按 §3.4 实测决策 |
| **第三批** | bone-ai-eval + bone-notification 消警 + bone-integration 三态 + 前端 bone-ai-app | 评测门禁挂 CI；客户接入 ≤15 人日（配置制） |

> **落地现状（2026-10-14 回写）**：第一批中 bone-ai-gateway / bone-ai-trace **最小版已落地**（§1 #10）——门面双实现可替换、成本红线负向探针测试在列；其余（rules 最小版、orchestration、metadata 语义扩展、网关剩余治理面）未动工。

**第一批纳入 rules 最小版的原因（v3.0 修正，回应依赖断档复核意见）**：orchestration 的规则节点依赖 bone-ai-rules 提供"规则命中不调 LLM"语义与规则执行治理——原方案把 rules 整体放第二批，第一批的端到端链路要么用 stub 假装跑通（出口判据失真），要么把规则逻辑偷偷写死在编排层（第二份真源，违反"一份数据一个真源"）。修正后：**第一批的 rules 最小版 = 治理面骨架（规则资产 CRUD + 版本 + 命中标记）+ expression SPI 方言路由 + 表达式语法兼容性评估（§3.3）**；灰度/shadow/正反例回归与 `MetadataVersionController` 规则级兼容性检查补齐，放第二批完整治理面。

每阶段门禁：`check.sh` + ArchUnit（新增"AI 边界"规则集：产品域不直连 AI 实现、AI 模块不 import 产品域、模型调用 HTTP/key 只在 gateway、**业务代码禁 import `LlmClient` 实现类（只可依赖网关门面，防绕过证据链）**、AI 服务必须经 trace 落盘、**资金/责任/数值判定入口禁收 `AiSuggestion` 类型（只收 `Confirmed`，见 §2.2 类型护栏）**、禁 import 旧 Aviator GAV）+ 评测基线 + 负向探针。**新增依赖必须过 CVE/SBOM 扫描**（v1.0"Spring AI 1.1.8"式选型错误靠此门禁机械拦截）。

---

## 5. 拍板项与风险提示

1. **位置**：推荐新建 `bone-ai/` 平行聚合（§2 理由）；需根 `pom.xml` modules + `bone-parent` dependencyManagement 注册，属 **L3**。
2. **L3 审批清单**（v3.0 更新）：新聚合/新 DDL（trace 快照表、评测集表）/QLExpress 4.1.3 与 Aviator 5.9.0（`io.github.aviatorscript`）依赖/**OCR sidecar 部署形态与接口契约（§3.5）**/pgvector **决策点**（"独立实例推迟至 rag 启动实测"这件事本身需批准，而非批准一个具体实例）/是否仍要 Spring AI（若要：修复版来源与授权）。**Spring AI 1.1.8 不在审批清单内**（一期不引，且该线已 EOL）。
3. **技术债提示**（实证精确化）：`MetadataVersionController` 的**字段级**兼容性检查已实现（反射读 `type` 字段、异常吞掉，有 hack 味），**关系/规则两级是 documented skip（空实现）**——bone-ai-rules 的治理面应优先补齐规则兼容性检查，否则灰度回归缺机器判据；反射 hack 建议顺手收敛。
4. **Boot 窗口**：Bone 停在 3.5.16 终版，Spring AI 2.x（Boot 4）是后续升级窗口事项，不在本方案范围；MCP Server 能力单列 POC，不捆绑 Spring AI。
5. **平台基线生命周期风险(v3.0 新增,超出本方案范围但必须显性化)**:Bone 自身基线 Boot 3.5.16 已随 Boot 3.5.x 于 **2026-06-30 到达 OSS EOL**(endoflife.date 实证;商业支持窗口至 2032-06-30)。本方案的 AI 中台不加深该风险(自建客户端零框架依赖,且不引入 Spring AI 任何版本),但政企 3-5 年周期下"平台跑在无社区补丁的基线上"是总风险,建议**另行拍板**：商业支持订阅（Tanzu/HeroDevs NES）或 Boot 4 迁移排期（Broadcom 估算 ~83 项 breaking change，Jackson 3/JDK 基线齐变）。**建议拍板期限：2026-11-30 前**给出二选一结论（责任人：平台架构组）；在此之前新增依赖一律过 CVE/SBOM 门禁，Boot 版本变更不与 AI 中台项目捆绑。**两条纪律**:1 不在 AI 中台项目内顺带升级 Boot--两件大事解耦,各自可回滚;2 若最终选商业支持,其覆盖范围需包含 Bone 实际使用的全部 Spring 组合件,POC 签前法务一并确认。

---

## 6. 复核记录（v1.0 → v2.0 逐条裁定；后续各轮并入记录见 §8）

| # | v1.0 结论 | 裁定 | 证据（2026-10-13 实证） | 处置 |
|---|---|---|---|---|
| 1 | 引入 Spring AI 1.1.8（Boot 3.5.x 唯一对应线） | ❌ **推翻** | Maven Central：1.1.x 止于 1.1.8（2026-06-13）；CVE-2026-59318（tool calling 越权，可被提示注入触发）**影响 1.1.0–1.1.8**，另有 CVE-2026-47851/47852/59294 共 4 项未修复（2 high）；官方修复只在 2.0.1/HeroDevs ELS 1.1.10 | 改为一期不引 + 自建 OpenAI 兼容客户端（§3.1）；若引只用修复版 + 底线缓解 |
| 2 | "1.1.x 不再收新特性" | ⚠️ 修正 | 1.1.8（2026-06）晚于 2.0.0-M7，含 Boot 3.5.15 升级；但 1.1.x 分支此后无官方 GA | 表述改为"官方 1.1.x 线止于 1.1.8" |
| 3 | Aviator（LGPL）→ QLExpress 替换退位 | ⚠️ 优化 | `com.googlecode.aviator:aviator` 5.2.1/5.3.0 POM 为 LGPL；但上游迁址后 `io.github.aviatorscript:aviator 5.9.0` 为 **Apache-2.0**（Sonatype Central） | 存量升级 GAV 即解决许可；QLExpress 定位为规则 DSL 默认（表达式追踪服务 C4），双引擎策略 |
| 4 | MinerU "⛔ 避开（>100M MAU 需商业授权）" | ⚠️ 精细化 | ≥3.1.0 已从 AGPLv3 切换为基于 Apache-2.0 的自定义许可（两独立来源互证），保留 100M MAU/$20M 月营收门槛 + 在线服务署名；早期版本为 AGPL-3.0 | 改为"私有化内部部署基本不触发、需法务确认版本与权重许可；对外 SaaS 不建议"；默认选型不变 |
| 5 | Qwen 系列"⚠️ 需评估"（笼统） | ⚠️ 精细化 | Qwen Community License 1.0 / Qwen License 原文：>100M MAU 或 >$20M/月营收需展示模型名；MaaS/AI Work Assistant 需单独授权；内部使用豁免；**Qwen3.8-27B 为 Apache-2.0** | 按部署形态分层结论；选型锁定 Apache-2.0 档位 |
| 6 | Dify 多租户许可红线 | ✅ 属实 | LICENSE 原文：tenant=workspace，无书面授权不得运营多租户环境；LOGO 不可移除 | 维持不引入 |
| 7 | CVE-2026-59308（语义缓存跨租户） | ✅ 属实 | spring.io/security/cve-2026-59308：影响 2.0.0；**修复版本 2.0.1 已实证**——spring.io 2.0.1 发布公告（2026-08-21）"CVEs fixed in this release"清单明确列有该项（SHA-256 截断致 system prompt 间缓存串话） | 维持"隔离层自建"；语义缓存一期禁用或平台自建（租户 key + 完整哈希） |
| 8 | QLExpress 4.1.3 Apache-2.0 活跃维护 | ✅ 属实 | GitHub/Maven Central：v4.1.3（2026-08-15），季度节奏 | 维持；补接入注意（Antlr4 重写、4.1.2 deprecated、安全策略显式配置） |
| 9 | PaddleOCR Apache-2.0 / Docling MIT | ✅ 属实 | LICENSE 文件实证；Docling 生态含 docling-serve/docling-java/docling-eval | 维持；补生态信息用于 sidecar 与评测 |
| 10 | pg_search AGPL | ✅ 属实 | ParadeDB 官方：Community AGPL-3.0，Enterprise 商业授权豁免 copyleft | 维持不用 |
| 11 | `MetadataVersionController` "关系/规则兼容性检查是空实现" | ⚠️ 精确化 | 源码实证：字段级检查已实现（反射 + 吞异常），关系/规则两级为 documented skip | 表述精确化；治理面优先补齐规则级 |
| 12 | bone-file "仅规划中"→已实现 | ✅ 已修 | 22 个 java 文件，单 Maven jar（含 Web 层） | 存取复用但**不进程内依赖**，走 Port（§2.2） |
| 13 | （v1.0 盲区）rerank 走 Spring AI | 🆕 新增发现 | 官方模型清单无 rerank 类型 | bone-ai-rag 自建 rerank（本地 ONNX 为主） |
| 14 | （v1.0 盲区）C4 定位两案冲突 | 🆕 裁决 | 模块规划主张 bone-framework 横切、最佳方案主张 bone-ai-trace 模块 | 裁决：建 bone-ai-trace 模块 + framework 供基类；强制经事件/拦截器落盘防"旁路" |

---

## 7. 一页纸总结

> **家底**：规则执行、版本机制、工作流建模、文件存储、告警、集成编排**都已有**；空白只有五处——LLM 运行时、工作流执行器、持久化证据链、文档解析 IR、RAG/评测（**其中 LLM 运行时/持久化证据链最小版已于 2026-10-14 落地**，见 §1 #10）。  
> **方案**：新建 `bone-ai/` 聚合 7 模块（gateway/orchestration/trace/rules/document/rag/eval），只补空白与治理面；HITL 内嵌 orchestration 子域；C4 建 trace 模块（framework 供基类）；C9/C10/文件存取全部落现有模块扩展；建模归 bone-metadata-engine、执行归 bone-ai-orchestration，守"一份数据一个真源"。  
> **框架（v2.0 核心变化，v3.0 补强）**：**一期不引入 Spring AI 任何版本**——1.1.x 带 4 个未修复 CVE 且已随 Boot 3.5.x 于 2026-06-30 OSS EOL，2.x 绑 Boot 4——gateway 自建 OpenAI 兼容客户端，`LLMClient` 门面留好替换位；不引 Agent 框架（合规可复现冲突 + Dify 多租户许可红线，私有化交付同样触发）；MCP 双角色预留（Server + Client）；Aviator 升级 GAV 至 Apache-2.0 版 + QLExpress 作规则 DSL 双引擎（附表达式方言兼容性评估）；rerank 自建；许可排雷前置，新增依赖过 CVE/SBOM 门禁。  
> **落地（v3.0 修正）**：第一批 = gateway + trace + **rules 最小版** + orchestration（消除依赖断档）；第二批 = document + rag + rules 完整治理面（**sidecar 契约冻结、pgvector 实例形态实测后决策**）；第三批 = eval + 两扩展 + 前端。  
> **治理加固（v3.2）**：证据链结构强制（网关唯一入站 + 无证据拒返 + 禁直连客户端门禁）；AI 结论经 `AiSuggestion`/`Confirmed` 类型隔离才可进资金/责任/数值判定；网关治理四项（路由/降级/配额/PII 出口脱敏）+ 能力级一键降级；影子模式 + 人工抽检是运行机制、最小 eval 不降级；框架试点备选须先过许可排雷，排不过自动回落自建路径。  
> **网关定位（v3.3）**：gateway 有必要，但必要的是"治理强制点"而非"大而全网关服务"——一期库形态（进程内门面，零延时无 SPOF），非 JVM 调用方/集中配额/供应商 ≥3 时演进集中式网关（§3.7）；与业界 "fail open" 直连不同，BONE fail 到确定性/人工路径；观测对齐 OTel GenAI 语义约定。  
> **平台级提示**：Bone 基线 Boot 3.5.16 已过 OSS EOL——AI 中台不加深此风险，但需另行拍板商业支持或 Boot 4 迁移，不与 AI 项目捆绑。

---

## 8. 复核建议并入记录（v2.0 → v3.0 逐条裁定；v3.1 → v3.2 二轮裁定见下）

| # | 复核建议 | 裁定 | 处置（落点） |
|---|---|---|---|
| 1 | 明确 Spring AI 1.1.x 生命周期终点、预留 1.x→2.x 迁移窗口 | ✅ 采纳并**实证补强** | §3.1：1.1.x 随 Boot 3.5.x **已于 2026-06-30 OSS EOL**（endoflife.date/HeroDevs/JCP 互证）——比"预计 2027 年内"更严峻，风险从"预判"变"既成事实"；Bone 自身基线同样过 EOL → §5 第 5 条平台级显性化；自建客户端路径不依赖任何 Spring AI 版本，无断代迁移负担 |
| 2 | CVE-2026-59308 引用改"2.0.0 存在"、勿断言修复版本 | ✅ **已实证成立，保留原引用** | spring.io 2.0.1 发布公告（2026-08-21）"CVEs fixed in this release"清单明确列有 CVE-2026-59308 → "修复于 2.0.1"有官方来源，无需弱化（§6 第 7 行补证据源） |
| 3 | 增加 QLExpress 与 Aviator 表达式语法兼容性评估 | ✅ 采纳 | §3.3 新增评估任务：存量方言盘点 + 三路径成本（双方言并存为默认建议）+ 评估报告列第一批出口物 |
| 4 | pgvector 独立部署决策推迟至 rag 启动时 | ✅ 采纳并**修正方向** | §3.4："pgvector 为最终形态，实例形态推迟"；过渡方案 MySQL JSON + 应用层余弦（百万级以下）/ ES 向量检索（如有集群）；§4 第二批出口判据、§5 审批清单同步（批准"推迟"而非批准实例） |
| 5 | 增加"文档解析 sidecar 接口契约定义" | ✅ 采纳 | 新增 §3.5：协议/输入/输出 IR schema/错误码/超时重试/GPU 调度/部署/安全八要素；契约冻结 + 双解析器互换实证进第二批出口判据 |
| 6 | Dify 多租户限制同样覆盖私有化交付场景 | ✅ 采纳 | §3.2 Dify 红线补注：客户自有环境运行多租户实例同样触发，政企私有化非豁免，纠纷发生在客户现场 |
| 7 | 明确 MCP 双角色（Server + Client） | ✅ 采纳 | §3.1 重写 MCP 段：双角色定位 + 同一 Tool 注册表共存 + MCP SDK CVE 提醒（CVE-2026-59279 会话耗尽，修复于 2.0.1） |
| 8 | 第一批增加 rules 最小版，或明确 orchestration 规则节点用 stub | ✅ 采纳（选前者，不用 stub） | §4：第一批纳入 rules 最小版（治理面骨架 + SPI 方言路由 + 兼容性评估）；附依赖断档分析（stub 会令出口判据失真、写死编排层会产生第二份真源）；完整治理面留第二批 |
| 9 | 补充 HITL 三态状态表 | ✅ 采纳 | §2.1 状态表：`pending`（<0.7 强制进入）→ `confirmed` / `rejected` / `modified`，`reason_type` 受控字典为回流与评测集扩充唯一入口 |
| 10 | 方案末尾增加家底核查复查清单 | ✅ 采纳 | 新增 §9：12 条断言 → 源码文件/行号对照，供 L3 审批快速抽查 |
| 11 | （顺带）家底核查推断逻辑严谨性 | ✅ 认可 | §9 即为此项的机制化落地——每条断言可定位到文件行号或官方来源 |

### v3.1 → v3.2（二轮"复核 + 统一成稿"裁决，2026-10-14）

| # | 提议 | 裁定 | 处置（落点） |
|---|---|---|---|
| 1 | 模块边界重划：vector 不独立、evaluate 对内过重、bone-ai-hitl 独立成模块、trace/document/rag/eval 可删 | ⚠️ 部分采纳 | 向量库不自建（✅，并入 rag 边界，§2.2）；eval 降级为影子抽检（❌，§2.1 评测口径）；hitl 独立模块（❌，维持 orchestration 内嵌子域，§2.1）；删除 trace/document/rag/eval（❌，与 §9 家底实证冲突——三产品共用的解析 IR/RAG 是重资产，不再是"精简为 4 模块"） |
| 2 | 证据链升级为 SDK 强制 `enqueueEvidence`，缺证据拒绝返回 | ⚠️ 改写采纳 | 方向对（约定必被绕过），机制改写：网关唯一入站 + 事件必然落快照 = 结构无旁路，补"禁直连 LlmClient"门禁 + "无证据拒返"运行期断言（§2.1 证据链结构强制） |
| 3 | HITL 复用 BONE 已有流程/任务节点 | ⚠️ 改写采纳 | 原文自相矛盾（诊断"无审批引擎"却处方"复用已有"）；改口径：复用 WorkflowMetadata 模型 + orchestration 补执行器，HITL = 收敛节点 + trace 回写（§2.1 消歧） |
| 4 | 评测降级为影子模式 + 人工抽检，不必上标注系统 | ⚠️ 部分采纳 | 影子/抽检/回流定性为**运行机制**（✅）；最小 eval（评测集 + 指标字典 + CI 门禁）保留为模块（✅），"怎么证明它准"是政企必答题，影子流程替代不了评测口径；标注只走 `reason_type` 受控字典 |
| 5 | 补充：模型治理 / 安全脱敏 / 灰度开关 / 策略护栏 | ✅ 采纳（护栏加码） | 三项治理 + PII 出口脱敏 + 能力级一键降级落 §2.1；护栏从"运行时嗅探流向"升级为 **AiSuggestion/Confirmed 类型隔离 + ArchUnit 强制**（§2.2 + §4），出口可伪造、类型骗不了人 |
| 6 | ADR：不引重型框架；备选② = procuraAI 试点用框架、验证后抽回 | ✅ / ⚠️ | 主决策成立（§3.2 四条理由 + 替代方案）；备选②加**许可排雷前置 + 四约束**，任一不满足自动回落自建路径（§3.2 框架试点前置条件）——"试点用框架"不是免排雷通道 |
| 7 | 落地路线 P0/P1/P2 | ✅ 归一 | 归入 §4 三批次（P0 ≈ 第一批，procuraAI 端到端即出口判据），不另立路线，避免两套计划口径 |

---

## 9. 家底核查复查清单（L3 审批抽查用）

| # | 断言 | 证据位置（仓库相对路径 : 行，2026-10-13 实测） |
|---|---|---|
| 1 | `AiMetadata` 为纯配置 POJO | `bone-engine/bone-metadata-engine/bone-metadata-engine-domain/src/main/java/com/bone/metadata/engine/domain/metadata/AiMetadata.java:13` |
| 2 | `AgentMetadata` 为纯配置 POJO | 同上目录 `AgentMetadata.java:14` |
| 3 | （2026-10-13 快照）全仓无 LLM 客户端代码、无 spring-ai/langchain4j 依赖——**已过时，现状见 #13–14** | 当时 grep `openai\|dashscope\|ChatModel\|spring-ai\|langchain4j` 全仓 + 全部 pom.xml 零命中；2026-10-14 起 bone-ai/ 含自建 OpenAI 兼容客户端 |
| 4 | `ExtensionDecisionRecorder` 为内存版（ArrayList + ConcurrentHashMap，无持久化/租户/版本） | `bone-engine/bone-extension-engine/bone-extension-sdk/src/main/java/com/bone/engine/extension/infrastructure/ExtensionDecisionRecorder.java:13`（字段 :15–17） |
| 5 | `TenantAbstractEntity` 提供租户 + 审计基类 | `bone-framework/bone-core/src/main/java/com/bone/core/tenant/TenantAbstractEntity.java` |
| 6 | 已有规则执行运行时 | `bone-engine/bone-metadata-engine/bone-metadata-engine-runtime/src/main/java/com/bone/metadata/engine/runtime/RuleEngine.java`（832 行）；同目录 `BusinessRuleEngine.java` / `DefaultBusinessRuleEngine.java` / `BusinessRuleRegistry.java` |
| 7 | 已有工作流元数据、全仓无通用工作流执行引擎 | `bone-metadata-engine-domain/.../domain/metadata/WorkflowMetadata.java`（含 `WorkflowState`/`WorkflowTransition`）；执行器仅 extension-studio 的 `DeploymentStateMachine`（管插件部署生命周期，非业务工作流） |
| 8 | `MetadataVersionController` 字段级兼容性检查已实现、关系/规则两级为空实现 | `bone-engine/bone-metadata-engine/bone-metadata-engine-runtime/src/main/java/com/bone/metadata/engine/runtime/version/MetadataVersionController.java` |
| 9 | `bone-file` 已完整实现（单 Maven jar 含 Web 层） | `bone-platform/bone-file/`（22 个 java 文件；MinIO + Controller + JWT + 租户配置） |
| 10 | 表达式引擎为 Aviator 5.4.1（旧 GAV，LGPL-2.1+）；上游 Apache-2.0 版在 `io.github.aviatorscript:aviator 5.9.0` | `bone-parent/pom.xml` dependencyManagement；Sonatype Central（5.9.0 POM 许可实证） |
| 11 | Boot 基线 3.5.16（且该线已 OSS EOL） | 根 `pom.xml` / `bone-parent/pom.xml`；endoflife.date/spring-boot（3.5.16 = 2026-06-25 末版，OSS 支持 2026-06-30 止） |
| 12 | 许可/CVE 外部实证 | pg_search AGPL-3.0（ParadeDB LICENSE）；Dify 多租户需书面授权（Dify LICENSE）；Spring AI 1.1.x EOL + 4 CVE（Central/spring.io security）；CVE-2026-59308 修复于 2.0.1（spring.io 2.0.1 发布公告）；QLExpress 4.1.3 Apache-2.0（GitHub/Maven Central）——均见 §3、§6 |
| 13 | bone-ai-gateway 最小版已落地（门面红线 + 证据事件 + 双实现） | `bone-ai/bone-ai-gateway/src/main/java/com/bone/ai/gateway/application/LlmGatewayService.java`（红线短路 + `LlmCallRecorded`）；`infrastructure/OpenAiCompatibleClient.java` / `MockLlmClient.java`；`config/LlmGatewayProperties.java`（2026-10-14 实测） |
| 14 | bone-ai-trace 持久化证据链已落地（三元组 + 回放） | `bone-ai/bone-ai-trace/src/main/java/com/bone/aitrace/domain/model/EvidenceSnapshot.java`（input_hash/payload/engine_version）；`infrastructure/event/EvidenceTraceListener.java`（SHA-256 就地计算）；`domain/repository/EvidenceSnapshotRepository.java`（`findByTraceId` 回放） |
