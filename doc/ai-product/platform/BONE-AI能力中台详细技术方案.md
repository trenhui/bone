# BONE AI 能力中台 · 详细技术方案（详设）

> **版本**：v1.8　**日期**：2026-10-23　**归口**：平台架构组
> **v1.8 变更（流式 SSE 落地——对齐 2026 业界最佳实践，关闭最大能力缺口）**：① §2.1 新增**流式能力段**——`LlmClient.streamChat(req, Consumer<LlmStreamEvent>)`（默认缓冲回退，零改传输实现）+ `LlmStreamEvent` sealed 事件（Token/Done/Error）；`LlmGatewayApplicationService.streamInvoke` **复用与 `invoke` 完全相同的治理链**（红线→路由→配额→注入→脱敏→缓存命中），仅在末端逐片下发 Token、首 token 记 TTFT（`LlmMetrics.recordFirstToken`）、流末做内容安全/结构化校验/缓存写入/计量/证据（与 `invoke` 同口径）；② 传输层 `LlmHttpTransport.streamPost` 默认缓冲回退，仅 `JdkLlmTransport` 走 **JDK `HttpClient` 真 SSE**（`BodyHandlers.ofInputStream()` 行读），`ResilientLlmTransport` 透传（熔断只守卫「流获取」不守卫消耗，避免吞掉半截流）；③ 契约一致性定案——**流式与 `invoke` 同口径：面向调用方返回原文，PII 脱敏只发生在出站请求与证据落库两处**（不做逐片流式脱敏，避免跨 chunk 边界漏脱敏与证据标签泄露调用方）；④ §3.2 补 `gen_ai.time_to_first_token`（TTFT）映射；⑤ §6.2 指标化行补 TTFT 发射点已具备；⑥ 验证证据：**gateway 115 测试全绿（含 ArchUnit 12 条——订正 v1.7「11 条」口径，实际 `ArchitectureTest` 引用 12 条规则；新增流式 7 例：SSE 解析/事件顺序/stream:true+include_usage 写入/缓冲回退/错误载荷/流末内容安全拦截/TTFT 与证据脱敏口径 + 缓存命中流式）**。
> **v1.7 变更（密钥管理 seam 接线，续 Go-Live 硬依赖）**：① §2.1 密钥行补 **`ApiKeyProvider` 按调用解析**（此前端口已就位却零消费方——客户端构造期固化 `String apiKey`，密钥轮换语义落空，同 `LlmMetrics` 零消费方反模式；现补默认 `ConfigApiKeyProvider` + `@ConditionalOnMissingBean` 装配，客户端每次请求现取，接入方以 `@Bean` 覆盖为 bone-security Virtual Key 实现即生效，热路径零改动）；② §6.1 密钥托管行状态更新——网关侧 seam 已接通（✅），剩余 L3 收敛为 bone-security Virtual Key 能力本体实现；③ §2.1 默认开关表密钥行同步；④ 验证证据：gateway 108 测试全绿（含 ArchUnit 12 条，新增密钥轮换 / 空值 / 热更新 5 例；v1.8 订正原「11 条」为 12 条）。
> **v1.6 变更（RAG 诊断 P1 项落地，承接《RAG方案诊断与优化-2026-10-23》）**：① §2.6 检索链补**文档级 ACL seam**（`RagAclPort` 契约 + 默认 `NoDocAcl` 兜底 + 显式作用域无 ACL 归属 403 fail-closed，L3 前接入方适配为硬依赖）、**MMR 多样性选取**（`MmrSelector` 选取式，λ 默认 0 = 关闭、开启时精排 3 倍扩样，λ=0 约定为关闭而非纯多样性）、**降级可观测**（`RagEventSink` 端口 + 响应 `degradations` 痕迹码 + 默认 WARN 事件，trace 适配留 L3）；② §3.1 错误码表补 `AI_RAG_DOC_FORBIDDEN`（403），目标态语义注同步；③ §4 阈值表补 #23 MMR λ；④ §6.4 补检索权限隔离 / 降级可观测两行状态；⑤ §2.6 backlog 收敛——查询改写记**决策：暂不落地零消费方 machinery**（无 producer 先建合并机制即死代码，正是 P0 修掉的反模式；批二按评测回流启动），仅剩 trace/告警接线与 ingest ACL 两项；⑥ 验证证据：rag 43 测试全绿（RagServiceTest 13 例含 ACL/MMR/降级痕迹新场景 + ArchUnit 12 条），app 51 测试全绿（RagControllerTest 响应结构与痕迹透传断言）。
> **v1.5 变更（RAG 诊断后修订，承接《RAG方案诊断与优化-2026-10-23》）**：① **§2.6 检索链按代码真序重写**——补容量护栏（`max-scan` fail-closed）、引用强制前置（打分前剔除无出处切片）、双路降级口径（嵌入故障 ⇒ BM25 单路 / rerank 故障 ⇒ RRF 粗序，均 WARN，严禁切云端）、`min-score` 过滤；② §2.6 实现形态订正——`EmbeddingPort`/`RerankPort` 由「恒默认」改为**按 `base-url` 条件装配**（`RagInferenceProperties` 经 `@EnableConfigurationProperties` 注册，配置面自此生效），修复 rerank/HTTP 客户端零接线与配置死代码；③ §2.6 增量幂等落地——`doc_version` 批键判定（同版本同切片数跳过 / 异版本残缺软删重建）；④ §3.1 错误码表补 `AI_RAG_SCOPE_OVER_LIMIT`（422），目标态语义注同步；⑤ §4 阈值表补 #20 容量护栏 / #21 分数阈值 / #22 topK，rerank 窗口行注明 NoOp 默认；⑥ §6.4 补检索容量护栏行；⑦ §2.6 记 P1 backlog（文档级 ACL / 查询改写 / MMR / 降级事件 trace 化）。
> **v1.4 变更（生产硬依赖代码落地，承接诊断报告 P2 项）**：① §2.1 默认开关表补**密钥启动 fail-fast**（`api-key` 未解析 `${}` 占位符即启动失败）与**配额 Redis 分布式版**（`RedisTenantQuotaGuard`，Redisson 固定窗口 + 故障 fail-open）；② §6 生产就绪清单回写代码落地状态——探针分组（liveness/readiness）已配、Redis 配额不再是多副本硬依赖、密钥 fail-fast 已具备；③ 附录 A：trace 回放 / rules 管理 REST 由「目标态」改判**已落地**（`bone-ai-app` 的 `TraceController` / `RuleController`，租户隔离 + `ApiResponse` 信封 + 全局 BizException 翻译）；④ 验证证据：gateway 103 测试全绿（含 ArchUnit 7 条），app 控制器 13 测试全绿。
> **v1.3 变更（生产就绪强化，承接诊断报告 P2 项）**：**新增 §6 生产就绪清单**——部署配置 / 可观测 / 安全合规 / 容量性能 / 灰度回退 / 上线判据六域，逐项标注代码已具备（✅）与硬依赖待建（⏳），定义「可切生产流量」的 Go-Live 判据（含 Redis 分布式配额、密钥托管、威胁模型决策、故障演练等硬依赖）；原 §6 顺延为 §7；附录 A 去重复段。类型护栏 `AiSuggestion`/`Confirmed` 标目标态（代码零引用，见 §2.3）。
> **v1.2 变更（诊断后修订，见《方案诊断报告-2026-10-23》）**：§2.1 门面契约注补 `scenario` 字段与「默认开关状态表」（回答「零配置时有什么保护」）；**执行链按代码真序重写**（修编号错乱 + 补配额/注入/缓存/内容安全/计量五步 + 明确短路抛异常不发事件）；§2.2 P4 租户兜底标注已收敛（fail-closed）；§2.3 撤回 `WorkflowMetadata` 复用完成态、HITL REST 落点正名、超时口径统一为「创建后 48h」；§2.5/§2.6 补 sidecar 适配器与推理服务过渡形态；§2.7 eval DDL 对齐 `bone-init.sql` 落地版 + Kappa 待办；**§3.1 错误码表重写为代码真源**；§3.5 主流程时序对齐；§4 置信度分档收敛为可操作口径 + 门禁指标补度量定义；§5 增加现状基线；附录 A/B 去矛盾。
> **v1.1 变更（对齐已落地 gateway/trace 代码）**：§2.1 门面契约补"已落地基线 vs 目标态"差异（P1）；§2.2 trace 字段表/DDL 对齐已落地 `ai_trace_snapshot`（P3）+ "无证据拒返"落地机制（P2）+ 租户兜底待定注记（P4）；§2.4 澄清 rules 执行复用边界（P6）；§2.7 澄清 eval 落点（P8）。
> **配套**：《BONE-AI 能力中台整体方案》（下称**总案**）——总案定"做什么 / 为什么 / 边界红线"（选型定案、许可红线、沉淀边界见总案 §2/§4/§5，不在本文重述），本文定"怎么建"：模块详设、接口与数据结构、阈值初值、任务分解。《BONE-AI 依赖与许可 SBOM 清单》为许可真源。
> **阈值约定**：标注（初值）的口径均为可校准值，压测/评测后修订登记在本文；总案不硬编码阈值。DDL 口径：8 张 AI 表已入 `bone-init.sql`（本文 §2.2/§2.7 为落地版镜像），后续新增表以 L3 批复为准（总案 §7）。

---

## 1. 运行时架构与部署拓扑

```
产品服务（procuraAI / ClaimSphere / Equity，独立部署，Spring Boot 3.5.x）
   │ 进程内 starter 依赖（bone-ai-gateway/trace/rules…，库形态零额外跳）
   ├─▶ bone-ai-gateway ──HTTP(OpenAI 兼容)──▶ 模型服务（vLLM/Ollama 本地；云端备援随客户形态启用）
   ├─▶ bone-ai-document ──HTTP REST──▶ 解析 sidecar（默认 Docling | PaddleOCR 候选，K8s 独立 Deployment）
   ├─▶ bone-ai-rag ──▶ 向量库（过渡 MySQL JSON + 应用层余弦 | 终局 pgvector 独立实例，实测后定）
   ├─▶ bone-ai-orchestration（状态机 + HITL） / bone-ai-rules（治理面） / bone-ai-ledger（额度台账）
   └─▶ bone-ai-trace ──▶ MySQL 8.0.31（快照表，分区见 §2.2）+ MinIO（大 IR 对象）
bone-ai-app（后端聚合宿主，可选部署形态）：HITL / 评测 / RAG / 预警 / 集成统一 REST 面 + Actuator
横切：bone-security（脱敏/密钥）· bone-iam（权限）· bone-metadata-engine（建模语义）· 发布流水线（eval 门禁，不建独立模块）
```

边界不变式（ArchUnit 强制，见 §3.3）：产品依赖 `bone-ai-*` 门面，不直连 AI 实现类；`bone-ai-*` 不 import 产品域；模型 HTTP/key 只出现在 gateway。

---

## 2. 模块详设

### 2.1 bone-ai-gateway（模型调用唯一入站）

**包结构**：`api`（门面/DTO）· `application`（`LlmGatewayApplicationService`）· `infrastructure`（`OpenAiCompatibleClient`/`MockLlmClient`）· `config`（`LlmGatewayProperties`）。

**门面契约（冻结，替换 provider 不改业务）**：

```java
public interface LlmClient {                       // SPI，产品禁 import 实现类
    LlmResponse chat(LlmRequest req);              // 同步单轮；流式后续按需扩展
}
public record LlmRequest(String scenario,          // 场景键，驱动档位路由
                         List<ChatMessage> messages,
                         JsonSchema outputSchema,  // 可选：结构化输出强制
                         AiContext ctx) {}          // tenantId + traceId + 成本上限
```

> **已落地基线 vs 目标态（2026-10-23 更新）**：上为**目标态**契约。已落地为**渐进版**——`LlmRequest(model, messages, temperature, maxTokens, jsonResponse, shortCircuited, traceId, scenario)`（`scenario` 已于 2026-10-21 补入，驱动档位路由）、消息 `LlmMessage(role, content)`、包结构 `domain/application/infrastructure/config`；出站传输用 **JDK `HttpClient`**（零新依赖）；`traceId` **已改为调用方提供以贯穿全链**（缺省才生成，与证据链/计量共用同一键）；成本红线**抛 `BizException(400, AI_GATEWAY_REDLINE_BLOCKED)`**（非结果型短路——结果型短路为目标态）；租户上下文经 `TenantProvider` 取（替代目标态 `AiContext` 内嵌）。**流式已于 v1.8 落地**：渐进版在门面上增补 `LlmClient.streamChat(req, Consumer<LlmStreamEvent>)`（默认缓冲回退，零改传输实现）与 `LlmStreamEvent` sealed 事件（Token/Done/Error）；`LlmGatewayApplicationService.streamInvoke` 复用 `invoke` 全治理链，首 token 记 TTFT。目标态契约将 `chat` 演进为 `stream`（统一返回 `Stream<LlmStreamEvent>` 或 `Flow<LlmStreamEvent>`）。
>
> **治理面落地状态（2026-10-23）**：① 出口 PII 脱敏 ✅（`PiiRedactor` 端口 + 正则默认实现，身份证 ISO 7064 / 银行卡 Luhn 校验位闸门防误伤，出站与落证双向掩码；**默认开启**）；② 结构化输出校验 ✅（`jsonResponse=true` 时校验合法 JSON，失败发失败证据并抛 `AI_GATEWAY_OUTPUT_INVALID`；JSON Schema 强校验待 L3 依赖审批）；③ 精确缓存 ✅（`LlmResponseCache` 有界 LRU+TTL，仅 `temperature=0` 参与，**租户入键**防跨租户串答，命中不发证据/计量；**默认关闭**）；④ 租户配额 ✅（`TenantQuotaGuard` 进程内固定窗口，超限 429 `AI_GATEWAY_QUOTA_EXCEEDED`；**多副本需换 Redis**）；⑤ 计量 ✅（`LlmUsageRecorded` 事件 + `LlmUsageSink` 默认结构化日志）；⑥ 提示注入防护 ✅（`PromptInjectionGuard` 端口 + `HeuristicInjectionGuard` 关键词默认实现，仅检 user 消息、在脱敏前检测，超限 400 `AI_GATEWAY_INJECTION_SUSPECTED`；**默认关闭**——启发式误伤风险，语义级检测需以 `@Bean` 覆盖为模型分类器/云端护栏，属 L3）；⑦ 场景档位路由 ✅（`ModelRouter` 端口 + `ConfigDrivenModelRouter`，2026-10-21 接入调用链；无配置直通）；⑧ 输出内容安全 ✅（`ContentSafetyGuard` 端口，OWASP LLM05/09，命中抛 `AI_GATEWAY_CONTENT_UNSAFE` 且留证；**默认关闭**）；⑨ 级联升档 ✅（`CascadeLlmClient`：小模型先行、校验失败自动升档一次）；⑩ 观测发射 ✅（`LlmMetrics` 端口，Noop 默认）；⑪ **流式响应 ✅**（`LlmClient.streamChat` + `LlmStreamEvent` sealed 事件，JDK `HttpClient` 真 SSE + 缓冲回退，复用 `invoke` 全部治理链、首 token 记 TTFT，v1.8 落地）。**仍待**：OTel `gen_ai.*` 打点（需 Micrometer 依赖，L3）、红线演进为结果型短路、JSON Schema 强校验、配额多副本共享存储。

**执行链（顺序固定，与 `LlmGatewayApplicationService.invoke` 代码一致；任一步抛错即终止）**：
1. **租户解析**：所有分支之前取租户（计量口径按租户聚合，短路路径也要记量化发射点）；
2. **成本红线**：`shortCircuited=true`（编排侧规则已命中 `RULE_HIT_BLOCK`）→ **抛 `BizException(400, AI_GATEWAY_REDLINE_BLOCKED)`，不触碰 client、不发事件**（负向探针的地基）；
3. **场景路由**：`scenario → 模型档位`（配置驱动；未声明场景 / 无映射即按请求模型原样调用）；
4. **租户配额**：超限抛 429 `AI_GATEWAY_QUOTA_EXCEEDED`（编排落纯确定性路径；超限租户不消耗后续算力）；
5. **提示注入检测**：仅检 user 消息原文（脱敏前检测最稳），超限抛 400 `AI_GATEWAY_INJECTION_SUSPECTED`；**默认关**；
6. **出口脱敏**：PII/商业机密按 `MaskingRule` 出口层替换（模型侧永不接触原始敏感字段；出站请求与落证响应双向掩码，证据表不成未脱敏副本）；**默认开**；
7. **精确缓存查询**：仅 `temperature=0`（或未指定）且租户入键参与；**命中直接返回、不发证据 / 计量**（证据链只记真实发生的调用）；**默认关**；
8. **调用**：`ResilientLlmTransport` 承载超时 / 重试（仅幂等可重试故障）/ 熔断；失败走 9.5 补发失败证据后上抛（编排转人工）；
9. **输出内容安全**：模型产出不可接受即抛 `AI_GATEWAY_CONTENT_UNSAFE`（越狱尝试也留证，便于复盘）；**默认关**；
10. **结构化输出校验**：`jsonResponse=true` 时校验合法 JSON，失败抛 `AI_GATEWAY_OUTPUT_INVALID`（不静默出坏数据）；
11. **写缓存 + 计量**：`LlmUsageRecorded` 独立事件（花了多少）与 `LlmCallRecorded`（发生了什么）分离发布；
12. **证据发布**：发布 `LlmCallRecorded`（响应内容脱敏、payload ≤8KB，正文超限走对象存储）；同步监听落库失败即异常向上传播——**响应对不上快照拒绝返回**（fail-closed，绝不 fail 到直连模型）。

**默认开关状态表（回答「零配置时我有什么」）**：

| 防线 | 默认 | 说明 |
|---|---|---|
| 成本红线（规则命中不调 LLM） | **强制开** | 编排侧强制，不依赖调用方自觉 |
| 证据链（调用留证 + 拒返断言） | **强制开** | 合规底线，不提供关闭开关 |
| 出口 PII 脱敏 | **默认开** | `bone.ai.gateway.pii-redaction-enabled` |
| 租户配额 | **默认开** | 进程内固定窗口（默认档见 §4 #10）；宿主存在 `RedissonClient` bean 时自动切 Redis 分布式固定窗口（`quota-redis-enabled=true` 可关），Redis 故障 fail-open + WARN |
| 密钥启动校验 | **强制开** | `api-key` 含未解析 `${}` 占位符即启动失败（防模板配置流入生产）；https 远端空 key WARN（Virtual Key 部署下配置态空 key 属预期噪声，见 §2.1 密钥段） |
| 密钥按调用解析 | **强制开** | `ApiKeyProvider` 端口每次请求现取（默认 `ConfigApiKeyProvider` 读配置；`@Bean` 覆盖即切 Virtual Key / 轮换密钥，换 key 不重启） |
| 场景档位路由 | 配置驱动 | 无配置 = 按请求模型直通（`PassthroughModelRouter`） |
| 结构化输出校验 | `jsonResponse=true` 时开 | JSON 合法性校验；Schema 强校验为目标态 |
| 提示注入检测 | 默认关 | 启发式误伤风险；开启走 `HeuristicInjectionGuard` |
| 输出内容安全 | 默认关 | 同上；可 `@Bean` 覆盖为模型分类器 |
| 精确缓存 | 默认关 | 租户入键；仅 `temperature=0` 参与 |
| 级联升档 | 档位表配置 | `CascadeLlmClient` 包在调用链外，按需装配 |

**配置模型（`LlmGatewayProperties`，Nacos/ConfigMap）**：档位路由表、租户配额（token/日、调用次数/日、超限降档→纯确定性路径）、红线开关、脱敏规则、超时/重试、灰度开关（能力级一键降级）。**密钥**：出库走 bone-security（Virtual Key 语义），配置不含明文 key。密钥解析经 `ApiKeyProvider` 端口**按调用**完成（2026-10-23 接线：此前端口零消费方、客户端构造期固化 key，轮换语义落空）——默认 `ConfigApiKeyProvider` 从配置现取（Nacos 热更新换 key 不重启），合规形态以 `@Bean ApiKeyProvider` 覆盖为 bone-security Virtual Key 实现即生效（短时限 key 过期刷新、provider 侧轮换，客户端与热路径零改动）；返回 null/空 = 不带 Authorization 头（本地无鉴权 provider）。

**观测**：`LlmMetrics` 端口已就位（Noop 默认，成本 / 延迟 / 错误率 / 短路 / 配额超限发射点）；`gen_ai.*` 语义约定映射（表见 §3.2，映射表版本化、不硬编码常量）**待 Micrometer 依赖落地后接入（L3）**；提示词/响应内容**默认不采集**。

**流式能力（v1.8 落地——对齐 2026 业界最佳实践，关闭最大能力缺口）**：此前网关**全链同步**，而 SSE/流式已是 OpenAI 兼容生态与 LiteLLM/Portkey/Kong 等网关的事实标配（首 token 体验、长文逐步呈现、降级兜底）。本次补齐且不引入新依赖、不复制治理链：

- **门面契约**：`LlmClient` 新增 `void streamChat(LlmRequest req, Consumer<LlmStreamEvent> sink)`（默认实现缓冲回退：调一次 `chat` 后发一个 `Token` + `Done`，**接入方/传输实现零改动即可用**）；`LlmStreamEvent` 为 sealed 事件——`Token(delta)` / `Done(model, finishReason, usage)` / `Error(message, cause)`。
- **应用层 `streamInvoke`**：**复用 `invoke` 完全相同的治理链**（红线短路 → 场景路由 → 租户配额 → 提示注入检测 → 出站脱敏 → 缓存命中），仅在末端逐片下发——首 token 到达即发射 `LlmMetrics.recordFirstToken`（TTFT）；流末对全文做内容安全（OWASP LLM05/09）/ 结构化输出校验 / 精确缓存写入 / token 计量 / 证据发布（与 `invoke` 同口径）；provider 错误与治理拦截经 `Error` 事件下发并留证。
- **传输层**：`LlmHttpTransport.streamPost` 默认缓冲回退（`Stream.of(post(...))`）；仅 `JdkLlmTransport` 走 **JDK `HttpClient` 真 SSE**（`BodyHandlers.ofInputStream()` 行读、`data:` 过滤、`[DONE]` 剥离、读毕自动关流）；`ResilientLlmTransport` 透传——**熔断只守卫「流获取」、不守卫「流消耗」**，避免熔断中途吞掉半截流（流一旦拿到就让它自然走完）。`OpenAiCompatibleClient` 发 `stream:true` + `stream_options.include_usage:true`，解析兼容 `delta.content` 与缓冲 `message.content` 两种形态、错误载荷与 `usage`。
- **契约一致性定案（关键设计取舍）**：**流式与 `invoke` 同口径——面向调用方返回原文，PII 脱敏只发生在「出站请求」与「证据落库」两处，绝不改写业务答案**。故**不做逐片流式脱敏**：一段 PII 恰被 SSE 切在两 chunk 间，逐片正则对单边都「不像完整 PII」而漏脱敏；而累积重脱敏又会在每条增量上回吐 `[手机号]` 这类证据标签，既泄露脱敏机制又违背「原文下发」契约。对必须零泄露的含 PII 请求，治理层可强制走缓冲（非流式）模式（详见接入指南 §2.7）。
- **错误语义**：provider 故障 / 内容安全拦截 / 输出校验失败均以 `Error` 事件终止并发布失败证据，调用方据 `Error` 停止渲染并兜底，不会拿到半截 `Done`。

### 2.2 bone-ai-trace（持久化证据链）

**核心模型 `EvidenceSnapshot`**（不可变，租户隔离）：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT（Snowflake）pk | 主键（禁自增，仓内规范） |
| trace_id | VARCHAR(64) 索引 | 贯穿一次业务动作全链（回放关联键） |
| evidence_type | VARCHAR(32) | 证据类型（批一 `LLM_CALL`，可扩展 RAG/规则） |
| input_hash | VARCHAR(64) | SHA-256，监听内就地计算（防篡改校验） |
| engine_version | VARCHAR(128) | 模型/规则/提示词版本（回放复现） |
| payload | MEDIUMTEXT | 证据 IR（请求+响应 JSON） |
| tenant_id | BIGINT NOT NULL DEFAULT 0 | 租户（0=平台）；非数值编码兜底见下注 |
| deleted / created_at / updated_at | — | 软删 + 审计（HC-008 四必备列） |

> **已落地基线（2026-10-14，P3 对齐）**：实体 `EvidenceSnapshot extends TenantAggregateRoot<Long>`、表 `ai_trace_snapshot` 已入 `bone-init.sql` 并登记《数据库开发规范》§2.12；上表与 DDL 以落地为准。`payload_ref`（大 IR 拆存 MinIO、内联 ≤2MB）为批二增强，届时加列。
> **租户兜底已收敛（v1.2，原 P4 待定）**：`TenantContext` 为字符串，非数值编码（如 `system`）经 `getTenantIdAsLong()` 得 null → **fail-closed 显式拒绝**（`AI_HITL_TENANT_NOT_NUMERIC`），不静默映射平台租户 0——静默归零会把业务租户证据错标成平台资产，审计时无法区分。

**落盘时序**：网关事件 `LlmCallRecorded` → `EvidenceTraceListener`（就地哈希+写库）→ `EvidenceSnapshotRepository.findByTraceId` 回放；另 `LlmUsageRecorded` → `LlmUsageListener` → `LlmUsageSink`（默认结构化日志，spend tracking 消费方）。**结构强制**：无旁路 API——业务代码没有直接写快照的入口；网关是唯一事件发布方、监听是唯一落库方。

**"无证据拒返"落地机制（P2 定案）**：监听用**同步 in-tx `@EventListener`**（非 `@TransactionalEventListener(AFTER_COMMIT)`）——`publish()` 返回即快照已写；insert 失败则异常向上传播、网关 `invoke` 拒返（对齐"fail 到确定性/人工，绝不 fail 到直连模型"）。**明文禁止 trace 用 AFTER_COMMIT 投影模式**（那会让落盘滞后于响应返回，门禁失效）；也**不做 read-back 查询验证**（会造成 gateway↔trace 依赖环——已落地方向为 trace→gateway）。

**留存与分区**：审计留存 ≥7 年、运行日志 ≥6 个月（`retention_policy` 常量集中配置，framework 提供常量不提供业务 API）；MySQL **按 `created_at` 按月 RANGE 分区**（分区键须入主键，满足 MySQL 约束），租户检索走 `idx_tenant_time`；冷分区归档 MinIO 后 detach。

```sql
-- 已落地真源（bone-init.sql, 2026-10-14；HC-008 四列 + 软删声明 + Snowflake 主键；本文以落地为准）
CREATE TABLE ai_trace_snapshot (
    id             BIGINT       NOT NULL COMMENT '证据快照主键（Snowflake）',
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
    trace_id       VARCHAR(64)  NOT NULL COMMENT 'LLM调用追踪ID（回放关联键）',
    evidence_type  VARCHAR(32)  NOT NULL COMMENT '证据类型（LLM_CALL）',
    input_hash     VARCHAR(64)  NOT NULL COMMENT '输入IR的SHA-256（防篡改校验）',
    engine_version VARCHAR(128) DEFAULT NULL COMMENT '引擎/模型版本（回放复现）',
    payload        MEDIUMTEXT   DEFAULT NULL COMMENT '证据IR（请求+响应JSON）',
    deleted        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删标记',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_ai_trace_trace_id (trace_id),
    KEY idx_ai_trace_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI证据快照（LLM调用留痕）';

-- 批二·合规分区增强（须 L3：主键改 (id, created_at) + 同步 bone-init.sql + 冷分区归档 MinIO 后 detach）
-- CREATE TABLE ai_trace_snapshot ( ...同上列...,
--   PRIMARY KEY (id, created_at),
--   KEY idx_ai_trace_trace_id (trace_id), KEY idx_ai_trace_tenant_time (tenant_id, created_at)
-- ) PARTITION BY RANGE (TO_DAYS(created_at)) ( PARTITION p_init VALUES LESS THAN (TO_DAYS('2026-12-01')) )
--   ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI证据快照（LLM调用留痕）';
```

> **已收敛（2026-10-15）**：DDL 以落地 `ai_trace_snapshot` 为准（HC-008 四列 + 软删 + Snowflake 主键，`bone-init.sql` 与《数据库开发规范》§2.12 已登记）；按月 RANGE 分区留作**批二增强**（见 DDL 内注释，须 L3 + 主键改 `(id, created_at)` + 同步 `bone-init.sql`）。租户非数值编码兜底见字段表注（P4）。

### 2.3 bone-ai-orchestration（状态机执行器 + HITL 子域）

**执行器**：目标态为 `WorkflowMetadata`（状态/转换建模已有）驱动的多节点状态机——节点类型 `RULE_NODE` / `AI_NODE` / `HITL_NODE`（收敛）/ `FALLBACK_NODE`（降级），执行循环：取激活节点 → 求值（规则优先）→ AI 节点产出 `AiSuggestion<T>` → 需人工则停 `HITL_NODE` → 裁决后进入下一转换。**当前未接线 `WorkflowMetadata`（代码零引用）**：落地的是顺序流水线执行器（见下），多节点状态机与 `FALLBACK_NODE` 为目标态，排期按「建状态机」估、不按「接现成模型」估。**超时/超轮**（目标态口径）：单节点超时与轮次上限（初值：单节点 120s、编排轮次上限 8 轮，可在 WorkflowMetadata 按流程声明覆盖）触发 `ESCALATED` → 转人工全程处理（状态名与产品侧 FR-205 口径一致）。

**已落地最小执行器 vs 目标态（2026-10-18）**：当前实现为顺序流水线——**规则预检 → 短路判定 → LLM 步骤 → 确定性闸门裁决**，多节点状态机与 `FALLBACK_NODE` 仍属目标态。已落地的关键规则：
- **AI 故障不阻断业务**（架构红线）：LLM 超时 / 熔断 / 5xx / 红线命中不再向上抛，而是记为 `aiError` 并由闸门转人工，`OrchestrationResult` 同时返回 `aiError` 供审计；编程错误（NPE/IAE）仍上抛——不把真 bug 伪装成「模型不可用」；
- **确定性闸门 `DecisionGate`**：AI 只出建议，能否自动放行由确定性策略裁决——置信度 < 0.85 或金额 > 5000 元（均可配）→ `verdict.requiresHuman()`；纯规则决策（无 LLM 步骤）直接放行。转人工原因用受控码（`CONFIDENCE_LOW` / `AMOUNT_HIGH` / `AI_UNAVAILABLE`）便于监控聚合；
- **HITL item 创建由调用方按 verdict 驱动**（当前：产品 / 参考宿主调 `HitlApplicationService.create`；编排内自动建 item 并停等在 `HITL_NODE` 为目标态）。

**类型护栏（目标态契约；v1.2 修正：类型尚未落地，代码零引用）**：

```java
public record AiSuggestion<T>(T value, double confidence, EvidenceRef evidence) {}  // AI 唯一产出
public record Confirmed<T>(T value, ConfirmedBy by, Instant at) {}                  // HITL 裁决或规则产出
// 资金/责任/数值判定入口签名只收 Confirmed<T>；工厂方法是唯一构造通道，ArchUnit 禁隐式互转
```

> **落地实况（2026-10-23 核验）**：上述两个 record 在 `bone-ai` 中**不存在**，ArchUnit 规则 6 暂无拦截对象。当前等价防线是**运行期闸门**：`DecisionGate`（置信度 / 金额 / AI 可用性合取）+ HITL 五态与 `hitl_disposition.decided_by` 留痕 + `OrchestrationResult.aiError` 审计。**类型级护栏列入批二补建**（与 rag 引用强制同期：先有 `AiSuggestion` 产出类型，再补 ArchUnit 互转禁令）；在类型落地前，「资金/责任/数值判定只消费 Confirmed」是**工程约定 + 评审项**，不是编译期保证——接入文档不得宣称已机械强制。

**HITL 子域接口（应用服务在 orchestration；REST 控制器由 `bone-ai-app` 后端聚合宿主的 `HitlController` 暴露，前端确认台 qiankun 子应用消费、权限复用 bone-iam——前端为批三目标态）**：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ai/v1/hitl/items?status=&scene=&risk=` | 待裁决列表（按风险分派裁决人；置信度 <0.7 强制在列） |
| POST | `/api/ai/v1/hitl/items/{itemId}/act` | `{action: confirm\|reject\|modify, value?, reason_type?, comment}`；`reject/modify` 必带 `reason_type` |
| GET | `/api/ai/v1/hitl/items/{itemId}` | 待裁项详情；**处置链**（逐条裁决留痕）走 `GET /api/ai/v1/hitl/items/{itemId}/dispositions` |
| GET / POST | `/api/ai/v1/hitl/items/overdue` · `/items/escalate-overdue` | 超时（创建后 48h）查询与批量升级（上层显式触发） |

`reason_type` 字典：`RULE_MISS`/`CONTEXT_MISS`/`SUGGESTION_WRONG`/`POLICY_CONFLICT`（唯一回流入口，自由文本不进管道）。五态（PENDING / CONFIRMED / REJECTED / MODIFIED / ESCALATED）与回流口径见总案 §3.3（含与产品侧业务状态机的分层说明），不重复。

```sql
-- 已落地（bone-init.sql，2026-10-17 随 HITL 闭环实现入库）；实现以 bone-init.sql 为准，此处为口径摘录：
-- ① item_id 实现为 UUID（36 字符），建表须 VARCHAR(64)——原拟 32 会截断；
-- ② tenant_id 为 BIGINT（平台租户口径，非本段原拟的 VARCHAR——非数值租户编码 fail-closed）；
-- ③ 处置链独立为 hitl_disposition 子表（逐条裁决留痕，跨重启可回放），不复用 item 行上的最后裁决列。
CREATE TABLE hitl_item (
  item_id VARCHAR(64) PRIMARY KEY, trace_id VARCHAR(64) NOT NULL, tenant_id BIGINT NOT NULL,
  scene VARCHAR(64), suggestion JSON NOT NULL,          -- AiSuggestion 序列化（含 evidence_ref）
  status VARCHAR(16) NOT NULL,                          -- PENDING/CONFIRMED/REJECTED/MODIFIED/ESCALATED
  risk_level VARCHAR(8), assignee VARCHAR(64),
  decision_value JSON, reason_type VARCHAR(32), comment VARCHAR(512),
  decided_by VARCHAR(64), decided_at DATETIME,
  KEY idx_status (tenant_id, status, risk_level)
);

CREATE TABLE hitl_disposition (
  id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL,
  item_id VARCHAR(64) NOT NULL,
  action VARCHAR(16) NOT NULL,                          -- confirm/reject/modify/escalate
  reason_type VARCHAR(32), value JSON, comment VARCHAR(512),
  acted_by VARCHAR(64) NOT NULL, acted_at DATETIME NOT NULL,
  KEY idx_item (tenant_id, item_id)
);
```

### 2.4 bone-ai-rules（治理面，不造执行引擎）

**职责**：规则资产 CRUD + 版本（`rule_asset`/`rule_asset_version`）+ 灰度（`gray_percent`）+ shadow 双跑比对 + 正负例回归（连评测门禁）+ **命中标记语义**（`RULE_HIT_BLOCK`，供网关成本红线消费）+ 回放（QLExpress 表达式追踪 + 三元组快照写入 trace 的 `engine_version` 链路）。

**引擎口径（总案 §4.3 定案，落地约束）**：
- 规则 DSL = **QLExpress 4.1.3 唯一**：表达式追踪直接服务"判定路径可回放"，原生支持脚本热更与版本治理；沙箱保持"禁止脚本与应用代码交互"（禁用 open security strategy）；
- 规则文本保持声明式（比较 / 阈值 / 字符串匹配），复杂逻辑留 domain；
- 存量 SpEL（extension / metadata 域，字段/公式计算）**冻结不动且不算规则引擎**：嵌入式表达式、无热更、无追踪，与 QLExpress 定位不同，不构成双规则引擎；
- **执行复用边界（澄清，P6）**：`RuleEngine`/`BusinessRuleEngine` 经 metadata `ExpressionEngine` 走 **SpEL**，**不执行 QLExpress**。故"复用现有 RuleEngine"指复用其**触发 / 编排 / 元数据装载 / 版本机制**，**新规则资产的 QLExpress 求值由 bone-ai-rules 自建**；治理面（版本/灰度/回归/命中标记）是净增价值，按 DSL 定位分流，不构成"第二规则引擎"（求值不重复造、治理是新增）。
- 规则级兼容性检查（`MetadataVersionController` 规则层空白）由本模块补齐：版本 diff 同 `rule_eval_case` 回归绿灯才允许发布。

### 2.5 bone-ai-document（解析 + IR + 脱敏）

**两阶段管线（总案 §6 批次一致）**：
- **第一批·最小 IR（已落地）**：存量 PDFBox 做条款切片 + span 定位（`PdfBoxDocumentParser` + `ClauseSplitter`，默认解析器，不等 sidecar 先供端到端出口判据）；
- **第二批·完整管线（适配器已就位，待验收）**：sidecar 契约（总案 §4.4 冻结基线）接入，默认 **Docling**（版面/表格，CPU 可起步）——`DoclingSidecarAdapter` 已落地，`bone.ai.document.docling.enabled=true` 即作为主解析器替换 PDFBox（默认关）；PaddleOCR 为扫描件候选按需启用（`OcrPort` 默认 `NoOpOcrClient`，`HttpOcrClient` 接远程 OCR 服务；不同时维护双实现）。

**sidecar 契约参数（总案委托的阈值初值）**：

| 项 | 初值 | 校准依据 |
|---|---|---|
| 超时 | 30s + 2s/页，上限 300s | 压测分布（第二批） |
| 重试 | 仅 `SIDECAR_UNAVAILABLE`，指数退避 ≤2 次 | 幂等语义 |
| 降级 | 超时返 `partial=true` 部分 IR → 编排转 HITL | 总案 §4.4 |
| 单文档上限 | 50MB / 1000 页（超限 `PAYLOAD_TOO_LARGE`） | 产品侧材料实际分布 |
| OCR 低置信阈值 | 0.7（`OCR_LOW_CONFIDENCE` 触发转人工标注） | 与产品侧 OCR 熔断口径一致 |

**IR JSON schema（统一领域模型，解析器解耦，`provider_meta` 放解析器私有信息）**：

```
DocumentIR { doc_id, tenant_id, source_ref, page_count, parse_mode, provider, provider_meta,
  sections[]   { section_id, title, level, span:{page,box}, children[] },
  paragraphs[] { para_id, section_id, text, span, lang },
  tables[]     { table_id, section_id, cells[][], span, confidence },
  clauses[]    { clause_id, section_id, text, span, clause_type? },   // 条款索引：引用定位原文的锚点
  redactions[] { field, policy } }
```

**存取**：文件引用走自建 `FileStorage Port`（MinIO 配置规范，与 bone-file 同规范直连或 HTTP 调用，不进程内依赖单 jar）；脱敏回填 = 网关出口占位符映射还原（仅授权场景）。

### 2.6 bone-ai-rag（受控检索）

**检索链（一次查询，与代码真序一致，2026-10-23 重写；P1 项 ACL / MMR / 降级可观测同日落地）**：租户过滤 → **文档级 ACL**（默认 `NoDocAcl` 仅租户硬隔离；接入方提供 `RagAclPort` 受限可见集合后：显式 `doc_scope` 无 ACL 归属 ⇒ 403 `AI_RAG_DOC_FORBIDDEN` fail-closed，无作用域时按可见集过滤）→ **容量护栏**（`maxScan` 超限 fail-closed 抛 `AI_RAG_SCOPE_OVER_LIMIT`，绝不静默截断）→ **引用强制前置**（无 `clauseRef`/span 非法切片在打分前剔除，不占精排名额）→ 向量（bge-m3，1024 维起步）+ BM25 双路（**嵌入 provider 故障 ⇒ WARN 事件 + 降级 BM25 单路**）→ **RRF 融合**（k=60，权重 0.5/0.5 起步；版本不符切片不入向量路，RRF 按「末位之后」计最弱）→ 候选池（`candidatePool`，默认 30）→ **rerank**（bge 系 cross-encoder，top-30→top-5；**provider 故障 ⇒ WARN 事件 + 降级 RRF 粗序**）→ `minScore` 过滤（宁缺毋滥，返回数可小于 topK）→ **MMR 多样性选取**（λ≤0 关闭；开启时精排按 3 倍扩样保证候选面）→ topK。降级口径：读路径可用性优先，降级仅限**本域内已实现路径**（BM25 单路 / RRF 粗序），**严禁切云端辅路**（数据不出域红线）；降级可观测（`RagEventSink` 事件 + 响应 `degradations` 痕迹码），非「假装已精排」。

**实现形态（已落地最小全链，2026-10-23 接线修复 + P1 落地）**：`EmbeddingPort` / `RerankPort` 均按 `bone.ai.rag.embedding.base-url` / `bone.ai.rag.rerank.base-url` **条件装配**——配了走 `HttpBgeEmbeddingClient` / `HttpBgeRerankClient`（共享 TEI / infinity 兼容推理服务，多副本友好、数据不出域），未配回落 `TransitionalHashEmbedding` / `NoOpReranker`（`RagInferenceProperties` 经 `@EnableConfigurationProperties` 注册，`bone.ai.rag.*` 配置面自此生效）；**引用强制为 fail-closed**：无出处切片前置剔除，绝不以无出处内容喂生成；`ChunkRepository`（SDK 动态代理）落 `ai_rag_chunk`（已入 `bone-init.sql`，`doc_version` 为增量幂等批键、`embedding_ver` 支持换模型灰度重建）。

**文档级 ACL（P1-7，2026-10-23 落地 seam）**：`RagAclPort`（domain 端口，契约 = 返回调用方可见 `doc_id` 集合；`null` = 无文档级 ACL 模型、仅租户硬隔离兜底；非 `null` = 受限集合、无 ACL 归属的 doc 不可见）。默认 `NoDocAcl`，接入方以 `@Bean` 覆盖（读文档中心 / bone-ai-document ACL，调用方身份由实现自行从上下文解析，端口不接收 user 入参——防伪造身份参数绕权）。**为何是端口而非 rag 自建 ACL 表**：文档权限模型归属文档中心，rag 不重复建设权限存储；L3 私有化交付前必须接入真实 ACL（当前兜底形态存在同租户内跨文档越权面）。

**多样性 MMR（P1-9，2026-10-23 落地，默认关闭）**：`MmrSelector` 选取式（非重排）——从精排后候选迭代挑 topK，每步选 `λ·相关性 − (1−λ)·与已选的最大相似度` 最大者，防 top-K 全来自同一条款/同一章节；相似度用切片入库时写入的嵌入向量（仅同模型版本可用，否则按 0 处理、MMR 自然退化为相关性原序）。λ 约定（`bone.ai.rag.mmr-lambda`，模块级旋钮同 candidatePool、不走请求级）：默认 0 = 关闭；λ∈(0,1) 生效；λ=1 纯相关性（等价关闭）；**λ=0 按约定视为关闭而非数学上的纯多样性**（防运维误配乱序）。阈值须按评测集回流数据校准（批二），默认关闭避免误伤（与 minScore 同哲学）。开启时精排候选按 3 倍扩样（`MMR_EXPANSION`，受候选池上限约束）——选取式 MMR 需要比 topK 更大的候选面才能「换掉」同质项；关闭时不多要，避免白付 cross-encoder 推理成本。

**降级可观测性（P1-10，2026-10-23 落地 seam）**：`RagEventSink` 端口 + `RagEvent(type, detail)`；检索链降级 / 数据卫生事件（`EMBEDDING_DEGRADED` / `RERANK_DEGRADED` / `DROP_INVALID_CHUNKS`）经端口下沉，默认 `LoggingRagEventSink`（结构化 WARN）；**响应体 `degradations` 透出降级痕迹码**（接入方 / 评测管线可观测，而非只翻服务端日志）。目标态：接入方以 `@Bean` 覆盖为 bone-ai-trace 落库 + 告警适配（纳入 trace 落盘成功率 ≥99.9% 同一条告警线），检索链零改动。

**切片策略（总案"条款级优先、超长二次切分"的阈值化）**：条款为单位入库；单条 >2000 汉字按语义段二次切分（overlap 200 字）；标题/章节层级写入 `chunk.path` 供引用面包屑。

**增量更新（幂等，2026-10-23 落地）**：文档版本事件 → `ingest(tenantId, docId, docVersion, segments)` 按版本批键判定——**同版本且切片数一致 ⇒ 跳过**（同版本内容不变的版本事件语义）；**异版本或残缺**（如上次崩溃半截）⇒ 软删该文档全部旧切片后重切重嵌（写入路径 fail-closed：嵌入失败上抛，不留半截新版本，重入自愈）。

**接口**：`POST /api/ai/v1/rag/retrieve`（`{query, doc_scope?, top_k?, min_score?, max_scan?}` → `{results: [{chunk_ref, clause_ref, span, score, path}], degradations: [痕迹码]}`；`min_score`/`max_scan` 请求级覆盖配置默认值；`degradations` 非空 = 本次发生能力档位下降，引用强制语义不随降级改变）+ `POST /api/ai/v1/rag/ingest`（`{doc_id, doc_version, segments}`，`doc_version` 幂等批键必填）；引用正确率目标 ≥98%（评测口径见 §2.7 / §4 #19）。

**backlog（P1 剩余）**：① 查询改写（multi-query / HyDE）——**决策：暂不落地 machinery**。无改写 producer（LLM 改写依赖网关稳定 + 评测集回流数据标定收益，报告建议批二定）时先建合并机制 = 零消费方死代码（正是本轮 P0 修掉的反模式）；接线点：orchestration 侧生成多查询 → rag 多查询合并，批二按评测回流数据启动。② 降级事件 trace 落库 + 告警接线（端口已就位，L3 接入方适配）。③ ingest 侧 ACL 校验（写入路径当前不做文档可见性判定，属管理面语义，随文档中心 ACL 模型一并定）。

### 2.7 评测（挂发布流水线，不建独立模块）

**结构**：`eval_case`（输入/期望/双人标注 guidline/`kappa` 记录）+ `eval_run`（运行快照）+ 指标字典（遗漏率/误报率/P/R/F1/引用正确率/采纳率/成本）；门禁 = 发布流水线关卡，不过不合入。

| 门禁关卡 | 指标（第一批基线） | 未达后果 |
|---|---|---|
| 端到端质量 | 引用定位原文 100%（第一批出口判据） | 阻断 |
| 检索质量 | RAG 引用正确率 ≥98% | 阻断（第二批） |
| 稳定性 | 各批负向探针（成本红线/rerank 越权/规则灰度/网关延时增量/trace 落盘成功率） | 阻断 |
| 成本 | 单请求 token 上限与红线命中率 | 告警 + 超限降档 |

LLM-as-Judge 经网关计量（省 60–80% 人工标注）；冷启动 = 小规模人工标注 + HITL 回流滚雪球；不做全量标注平台（总案 §5）。

**DDL（评测集表——已落地 `bone-init.sql` §12.2；宿主 = 发布流水线读表出报告，写入走 bone-ai-app 评测管理 REST）**：

```sql
-- 已落地真源（bone-init.sql, 2026-10；HC-008 四列 + 软删 + Snowflake 主键；本文以落地为准）
CREATE TABLE eval_case (
    id             BIGINT       NOT NULL COMMENT '评测用例主键（Snowflake）',
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
    scene          VARCHAR(64)  DEFAULT NULL COMMENT '场景',
    case_type      VARCHAR(32)  DEFAULT NULL COMMENT '用例类型（RAG/CLASSIFY/...）',
    input          MEDIUMTEXT   DEFAULT NULL COMMENT '输入（query/文档片段）',
    expected       MEDIUMTEXT   DEFAULT NULL COMMENT '期望输出/标注',
    expected_hit   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否应被命中（正样本标记）',
    difficulty     INT          DEFAULT NULL COMMENT '难度（标注）',
    kappa          DECIMAL(4,3) DEFAULT NULL COMMENT '双人标注一致性（Kappa）',
    deleted        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删标记',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_eval_case_scene (tenant_id, scene, case_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI评测用例';

CREATE TABLE eval_run (
    id             BIGINT       NOT NULL COMMENT '评测运行主键（Snowflake）',
    tenant_id      BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
    gate           VARCHAR(32)  NOT NULL COMMENT '门禁名',
    case_id        BIGINT       DEFAULT NULL COMMENT '关联用例ID',
    predicted      MEDIUMTEXT   DEFAULT NULL COMMENT '模型/系统输出',
    expected       MEDIUMTEXT   DEFAULT NULL COMMENT '期望输出（从用例快照）',
    expected_hit   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否应被命中',
    predicted_hit  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '系统实际是否命中',
    cited_clause   VARCHAR(128) DEFAULT NULL COMMENT '引用条款（引用正确率计量；null=未引用）',
    judge_score    DECIMAL(5,4) DEFAULT NULL COMMENT 'LLM-as-Judge 评分（gateway 计量）',
    passed         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '门禁是否通过',
    finished_at    DATETIME(3)  DEFAULT NULL COMMENT '完成时间',
    deleted        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删标记',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_eval_run_gate (tenant_id, gate, finished_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI评测运行';
```

> **v1.2 对齐说明**：原拟定稿的 `annotators` / `version` / `code_ref` / `prompt_ver` / `rule_ver` / `model` / `metrics` / `verdict` / `started_at` 列在落地版中已调整（标注人走审计字段口径；版本/模型等运行上下文入 `gate` + 关联记录）；落地版新增 `expected_hit` / `predicted_hit` / `cited_clause` / `judge_score` / `passed`——**「引用正确率 ≥98%」的计量即 `cited_clause` 与黄金集标注 span 的重合判定**（脚本可算，不依赖人工目测）。双人 Kappa 已落 `eval_case.kappa` 列；**待办**：标注指南与双人标注流程尚未形成文档资产（C7 冷启动输入）。

---

## 3. 跨切规范

### 3.1 错误码与 API 规范
- 对齐 `ApiResponse`/`PageResult`（ADR-0028 入站约束）；AI 域错误码**以代码 `*ErrorCodes` 类为真源**（`AiGatewayErrorCodes` / `AiRagErrorCodes` / `AiOrchErrorCodes` / `AiDocErrorCodes`），全表如下：

| 码 | HTTP | 状态 | 语义 / 编排处置 |
|---|---|---|---|
| `AI_GATEWAY_REDLINE_BLOCKED` | 400（BizException） | 已落地 | 规则命中不调 LLM：编排侧根本不进 LLM 步骤（无请求、无事件）；直调网关带 `shortCircuited` 即抛此码 |
| `AI_GATEWAY_QUOTA_EXCEEDED` | 429 | 已落地 | 租户配额超限 → 编排落纯确定性路径（超限降档非拒绝） |
| `AI_GATEWAY_INJECTION_SUSPECTED` | 400 | 已落地（默认关） | 提示注入疑似（启发式，仅检 user 消息）→ 转 HITL |
| `AI_GATEWAY_CONTENT_UNSAFE` | 400 | 已落地（默认关） | 输出内容安全命中（OWASP LLM05/09）→ 转 HITL；越狱尝试也留证 |
| `AI_GATEWAY_OUTPUT_INVALID` | 400 | 已落地 | 结构化输出校验失败 → 一次修复重试（目标态）→ 转 HITL |
| `AI_GATEWAY_PROVIDER_RESPONSE_INVALID` | 502 | 已落地 | provider 响应不可解析 → 编排转 HITL |
| `AI_RAG_TENANT_CONTEXT_MISSING` | 400 | 已落地 | 检索缺租户上下文 → fail-closed 拒绝 |
| `AI_RAG_EMBEDDING_PROVIDER_FAIL` / `AI_RAG_RERANK_PROVIDER_FAIL` | 502 | 已落地 | 推理服务故障 → 检索链降级（BM25 单路 / RRF 粗序）+ WARN（绝不切云端辅路）；编排侧仅在双降级仍不可用时转 HITL |
| `AI_RAG_SCOPE_OVER_LIMIT` | 422 | 已落地（2026-10-23） | 检索作用域切片数超 `bone.ai.rag.max-scan` 容量护栏 → fail-closed 拒绝（提示收窄 `doc_scope` 或调大阈值），绝不静默截断 |
| `AI_RAG_DOC_FORBIDDEN` | 403 | 已落地（2026-10-23） | 接入方提供文档级 ACL 后，显式 `doc_scope` 无 ACL 归属 → fail-closed 拒绝（绝不静默回落全租户检索）；默认无 ACL 模型时不触发 |
| `AI_HITL_NOT_FOUND` | 404 | 已落地 | 待裁项不存在 / 无权访问 |
| `AI_HITL_ALREADY_DECIDED` | 409 | 已落地 | 五态流转约束：已裁项禁止重复裁决 |
| `AI_HITL_INVALID_TRANSITION` | 422 | 已落地 | 五态流转约束 |
| `AI_HITL_REASON_REQUIRED` | 422 | 已落地 | reject/modify 必带 `reason_type` |
| `AI_HITL_TENANT_NOT_NUMERIC` | 422 | 已落地 | 非数值租户编码 fail-closed（§2.2 已收敛项） |
| `AI_DOC_TOO_LARGE` / `AI_DOC_TOO_MANY_PAGES` | 422 | 已落地 | 50MB / 1000 页上限（§2.5 sidecar 契约） |
| `AI_DOC_SIDECAR_UNAVAILABLE` | 503 | 已落地 | sidecar 不可用 → 指数退避 ≤2 次重试；仍败返 `partial=true` 部分 IR 转 HITL |
| `AI_DOC_READ_FAILED` / `AI_DOC_OCR_PROVIDER_FAIL` | 422 / 502 | 已落地 | 读取失败 / OCR 服务故障 |

**目标态语义（无独立错误码，接入方勿写死分支）**：① provider 超时 / 故障的降档由 `ResilientLlmTransport` + 编排 `aiError` 承载，**无** `AI_GATEWAY_TIMEOUT` / `AI_GATEWAY_PROVIDER_FAIL` 码；②「无证据拒返」**无** `AI_GATEWAY_EVIDENCE_MISSING` 码——同步监听 insert 失败即异常向上传播（fail-closed），按 `BizException` 处理；③ RAG 无 `AI_RAG_NO_BASIS` / `AI_RAG_PERMISSION_DENIED` 码——「无出处不生成」由检索侧 fail-closed 丢弃 + 生成侧契约保证，「rerank 越权」是负向探针场景（测试断言），非运行时错误（例外：容量护栏 `AI_RAG_SCOPE_OVER_LIMIT` 与文档级 ACL `AI_RAG_DOC_FORBIDDEN`，见上表）；④ `AI_EVAL_GATE_FAILED` 是发布流水线关卡语义，由流水线工具呈现，非运行时错误码。

（`AI_DOC_*` 错误字典即 §2.5 sidecar 契约错误表，对齐 `ApiResponse` 规范）
- REST 面统一 `/api/ai/v1/`；写接口幂等键；租户从上下文取（禁止入参传租户）。

### 3.2 OTel `gen_ai.*` 映射（映射表版本化：`v1`）

| 事件字段 | OTel 属性 |
|---|---|
| op / provider / model | `gen_ai.operation.name`(chat) / `gen_ai.provider.name` / `gen_ai.request.model` |
| tokens_in / tokens_out | `gen_ai.usage.input_tokens` / `gen_ai.usage.output_tokens` |
| latency_ms | `gen_ai.client.operation.duration` |
| ttft_ms（首 token 时延，v1.8 流式新增） | `gen_ai.time_to_first_token` |
| trace_id | `gen_ai.conversation.id` |

提示词/响应**默认不采集**（内容采集面独立开关，默认全关）。

### 3.3 ArchUnit 规则清单（第一批门禁实现）
1. 产品域不 import `com.bone.ai.**.infrastructure`（不直连 AI 实现）；
2. `com.bone.ai.**` 不 import 业务产品包名；
3. 业务代码禁 import `LlmClient` 实现类，仅可依赖网关门面；
4. 模型 HTTP/key 出现处收敛于 gateway 包（白名单路径）；
5. AI 服务出口必须经 trace 落盘（调用侧断言 + 事件监听兜底）；
6. 资金/责任/数值判定入口禁 `AiSuggestion` 参数（只收 `Confirmed`）——**目标态**：`AiSuggestion`/`Confirmed` 类型批二补建后启用；当前由 `DecisionGate` + HITL 留痕提供运行期等价防线；
7. 禁 Aviator 全量 GAV（`com.googlecode.aviator` / `io.github.aviatorscript`；零消费者死实现已删除）。

> **口径说明（v1.8 订正，承接诊断复核 F5）**：上表为「第一批门禁」的 7 条代表性规则。`BoneDddArchRules` 共享规则库共 20+ 条，各模块 `ArchitectureTest` 按自身边界 `@ArchTest` 引用；gateway 实际引用 **12 条**（含框架共享的 god-object / 租户上下文 / 异常后缀 / 仓储包等规则），v1.7 头曾误记「11 条」，本次订正为 12。「11 模块各 7 条」指第一批门禁的 7 条在 11 模块统一落地，非各模块引用总数。

### 3.4 数据库与安全
- 全表租户隔离 + 审计字段（`tenant_id/created_by/created_at`）对齐《数据库开发规范》；
- 密钥：模型 key 走 bone-security 出库（Virtual Key 语义），日志/快照不得含明文 key；
- AI 输出落业务表前必须有 `Confirmed` 溯源（`decided_by`/`evidence_ref` 二选一必填）——**目标态口径**：`Confirmed<T>` 类型批二落地前，由 HITL `hitl_disposition.decided_by` + 评审项保证。

---

### 3.5 主流程时序与异常流（fail-closed）

**主流程（一次 AI 建议，与代码一致的顺序流水线）**：① 产品调 `OrchestrationApplicationService.run()`（带 traceId，全链同一键）→ ② 规则预检（QLExpress 求值 + 灰度分桶）→ ③ 命中 `RULE_HIT_BLOCK` ⇒ 短路：不调 LLM、不发请求、不发证据，只产短路信号 → ④ 未命中 ⇒ 调网关（红线→路由→配额→注入→脱敏→缓存→调用→内容安全→结构化校验→计量→落证）→ ⑤ 发布 `LlmCallRecorded`、trace 同步落快照（insert 失败即拒返）→ ⑥ `DecisionGate` 裁决（置信度 ≥0.85 **且** 金额 ≤ 阈值 **且** AI 可用 → 自动通过；否则转人工）→ ⑦ 需人工：调用方按 verdict 建 HITL item（当前形态；编排内停 `HITL_NODE` 为目标态）→ ⑧ 确认台（bone-ai-app REST）裁决 → ⑨ `Confirmed` 回业务、`reason_type` 回流评测。

| 异常 | 判定 | 处置 |
|---|---|---|
| trace 落盘失败 | 快照写库/事件失败 | **响应拒绝返回**（fail-closed；无独立错误码——同步监听 insert 失败异常直接向上传播，编排捕获转 HITL） |
| 响应校验失败 | `jsonResponse` 输出非法 JSON | 抛 `AI_GATEWAY_OUTPUT_INVALID` → 编排转 HITL（不出坏数据；一次修复重试为目标态） |
| sidecar 超时 | 返部分 IR | `partial=true` 交编排：小额场景可用部分结果+人工确认，全额场景转 HITL |
| provider 故障 | 降备援档一次仍失败 | 记 `aiError` → 闸门转 HITL（`AI_UNAVAILABLE`；绝不 fail 到直连模型） |
| 编排超轮/超时 | §4 阈值（目标态：多节点状态机） | `ESCALATED` 人工全程处理（与产品侧 FR-205 同名贯通，见总案 §3.3） |
| HITL 超时未裁决 | **创建后** 48h（§4 #14） | 升级提醒 + 改派；再超时 `ESCALATED` |

### 3.6 灰度与回滚（Runbook 摘要）

- **能力级灰度**：`ai.capability.<scene>.mode = shadow\|assisted\|automated\|off`，`off` 秒级降级为纯确定性/人工路径（总案 §5）；
- **模型档位回滚**：档位路由表配置版本化（每次变更存版本 + 快照），回滚 = 切回上一版配置，目标 ≤5 分钟，无需发版；
- **规则包回滚**：`rule_asset_version` 置 rollback，影子比对绿灯后全量切回；
- **发布门禁失败**：评测/探针不过不合入；已发布指标劣化 → 开关置 `off` + 人工回档，事故登记 `eval_run`；
- **证据链不可关**：灰度/降级不影响 trace 落盘与拒返断言（合规底线，不提供关闭开关）。

---

## 4. 非功能阈值总表（初值，压测校准后修订）

| # | 项 | 初值 | 所属 | 校准点 |
|---|---|---|---|---|
| 1 | sidecar 超时 | 30s + 2s/页，cap 300s | document | 第二批压测 |
| 2 | LLM 同步调用超时 | 30s（结构化）/ 90s（长文）；重试 ≤1 | gateway | POC |
| 3 | 置信度分档（v1.2 收敛为可操作口径） | **0.85 = 自动放行线**（`DecisionGate`：置信度 ≥0.85 **且** 金额 ≤ 阈值 **且** AI 可用 → 自动通过；任一不满足 → `requiresHuman` 转人工）；**0.7 = HITL 待裁列表展示线**（<0.7 强制在列、不得直出）。原「0.7–0.85 复核」档在无抽样机制前不单列操作（若引入抽检：POC 后按 `reason_type` 回流数据定抽样率） | HITL / orchestration | POC 评测 |
| 4 | 切片 | 条款级优先；>2000 字二次切分，overlap 200 | rag | 检索评测 |
| 5 | RRF | k=60，向量/BM25 = 0.5/0.5 | rag | 引用正确率回归 |
| 6 | rerank 窗口 | top-30 → top-5（`bone.ai.rag.candidate-pool` 默认 30；未配 rerank base-url 时 `NoOpReranker` 保持 RRF 粗序，窗口不生效） | rag | 同上 |
| 7 | 检索 P95 | <800ms（不含大模型生成） | rag | 第二批压测 |
| 20 | 检索容量护栏 | `bone.ai.rag.max-scan` 默认 10000（作用域切片数上限，超限 fail-closed 抛 `AI_RAG_SCOPE_OVER_LIMIT`；过渡态全量载入内存打分的兜底，终局索引化后即迁移触发信号） | rag | 第二批压测 |
| 21 | 检索分数阈值 | `bone.ai.rag.min-score` 默认 0 = 不启用（精排后过滤，量纲双轨：rerank 分 / RRF 融合分 ~0.01；须按评测集回流分布标定，默认关闭避免误杀） | rag | 检索评测 |
| 22 | 检索返回条数 | topK 默认 5、上限 50（`bone.ai.rag-top-k`；请求可覆盖） | rag | 试点场景 |
| 23 | 检索多样性权重 | `bone.ai.rag.mmr-lambda` 默认 0 = 关闭（λ∈(0,1) 生效，λ=1 纯相关性等价关闭，λ=0 约定为关闭而非纯多样性；开启时精排按 3 倍扩样；须按评测集回流数据校准后启用，默认关闭避免误伤） | rag | 检索评测（批二） |
| 8 | 证据载荷 | 内联 ≤2MB，超限对象存储 | trace | 线上分布 |
| 9 | 证据事件 payload | ≤8KB（正文超限转对象存储） | gateway | 线上分布 |
| 10 | 租户配额默认档 | 100 万 token/日、5000 次/日（超限降档非拒绝） | gateway | 成本口径 |
| 11 | 精确缓存 TTL | 24h（命中不发 LLM） | gateway | 成本口径 |
| 12 | 留存 | 审计 ≥7 年 / 运行日志 ≥6 个月 | trace | 合规 |
| 13 | 编排轮次 / 节点超时 | 8 轮 / 120s（流程可声明覆盖；多节点状态机为目标态） | orchestration | 试点场景 |
| 14 | HITL 裁决超时 | **创建后 48h 未决** → 升级改派（**已落地**：`HitlItem.isOverdue` 以 `createdAt` 起算 + `HitlApplicationService.escalateOverdue` 批量升级，`GET/POST /api/ai/v1/hitl/items/{overdue,escalate-overdue}` 供上层显式触发；不加 `deadline_at` 列——SLA 策略调整须对存量项一致生效；派单时间戳未建模，故 SLA 口径为「创建后」而非「分派后」） | HITL | 试点场景 |
| 15 | 网关延时增量（库形态） | P99 <10ms | gateway | POC |
| 16 | trace 落盘成功率 | ≥99.9%（低于即告警，拒返面放大） | trace | 线上观测 |
| 17 | 决策闸门金额阈值 | 5000 元（超过强制人工，可按产品覆盖 `bone.ai.orchestration.amount-threshold`；**域特异默认值，产品须显式评估**——非金额场景传 null 即跳过金额闸门） | orchestration | 试点场景 |
| 18 | AI 失败策略 | **不阻断业务**：超时/熔断/5xx 记为 `aiError` → 闸门转人工（`AI_UNAVAILABLE`），不向上抛；编程错误（NPE/IAE）仍上抛 | orchestration | 架构红线 |
| 19 | 门禁指标度量定义 | 「引用正确率」= `eval_run.cited_clause` 与黄金集标注 span 的重合率（§2.7，脚本判定）；「接入 ≤15 人日」= 加入依赖到首个场景端到端跑通的工程人日（客户 POC 实测回填） | 评测 / 交付 | 第二批 |

---

## 5. 工程落地分解（WBS）

**现状基线（2026-10-23 晚，与总案 §6 重基线一致）**：11 模块均已有最小落地。✅ = 最小版已落地（待出口判据验收 / 补治理面）；⏳ = 目标态。已落地：gateway 治理面全端口（红线/脱敏/配额/路由/缓存/注入/内容安全/级联/计量）、trace（三元组 + 同步落证 + 拒返）、rules 最小版（CRUD/版本/命中标记/回归门禁/QLExpress 求值）、orchestration 顺序流水线 + `DecisionGate` + HITL 五态/处置链/超时升级、document 最小 IR（PDFBox + Docling sidecar 适配器就位默认关）、rag 最小全链（租户隔离 + **文档级 ACL seam** + 容量护栏 + 双路 + RRF + rerank 条件装配与降级 + 引用强制 fail-closed + `doc_version` 幂等 ingest + **MMR 多样性选取（默认关）** + **降级痕迹可观测**；未配推理服务时哈希/NoOp 兜底）、notification/integration/ledger 最小版、bone-ai-app 聚合 REST 宿主、bone-ai-server 参考宿主。⏳ 目标态：多节点状态机（`FALLBACK_NODE`）、pgvector 终局、OTel 打点、前端确认台、模型锁型定版、JSON Schema 强校验、红线结果型短路、RAG 查询改写（决策：批二按评测回流启动，暂不建零消费方 machinery）。

### 第 0 批 POC（2 周）

| # | 任务 | 验收物 |
|---|---|---|
| 0.1 | `LLMClient` 契约冻结 + `OpenAiCompatibleClient` 完成（1 家 provider + vLLM 本地） | 换 provider 不改业务代码实证 |
| 0.2 | 成本红线短路 + `LlmCallRecorded` 事件 | 负向探针（规则命中不调 LLM） |
| 0.3 | 最小 eval 挂 CI（引用地面样本 + 指标脚本） | 流水线可跑第一份报告 |
| 0.4 | 法务 SBOM 首版送审 + CVE 门禁上线 | SBOM 清单 v1.1 签字 |
| 0.5 | 模型锁型评测（Qwen3.5/3.8-27B 二选一） | 评测对比表 + SBOM 复核 |

### 第一批（最小公共内核）

| # | 任务 | 验收物 |
|---|---|---|
| 1.1 | gateway 治理面（路由/配额/脱敏/灰度/降档）✅ 已落地，待出口判据验收 | 配额超限降档实证；PII 出口脱敏用例 |
| 1.2 | trace 完整（DDL+留存+回放 API）✅ 已落地（回放为进程内服务），待验收 | 三元组回放实证；无证据拒返断言 |
| 1.3 | rules 最小版（CRUD/版本/命中标记/兼容性检查补齐）✅ 已落地，待验收 | 规则命中不调 LLM 语义打通 orchestration 规则节点 |
| 1.4 | orchestration 执行器 + HITL 子域（五态/确认台 API）✅ 已落地（顺序流水线），待验收 | procuraAI 端到端：解析→规则→LLM→HITL→回放 |
| 1.5 | metadata 语义扩展（AiMetadata 档位/阈值、WorkflowMetadata AI 节点）⏳ 目标态 | 建模→执行链路一致性 |
| 1.6 | 最小 IR（PDFBox/Tika 条款切片 + span）✅ 已落地 | 引用定位原文 100% |
| 1.7 | ArchUnit 规则集 + 门禁接入 ✅ 已落地（11 模块各 7 条） | 7 条规则拦截演示 |

### 第二批（解析检索，出口判据见总案 §6）

| # | 任务 | 验收物 |
|---|---|---|
| 2.1 | document 完整管线（sidecar 契约接入，默认 Docling；PaddleOCR 候选按需启用）✅ 适配器就位（默认关），待跑通验收 | 契约冻结 + 默认 Docling 跑通 + PaddleOCR 单适配器就位（替换成本 = 一个类）；互换性由契约保证，不强制双跑实证 |
| 2.2 | IR schema 全量落地（条款索引/span/置信度）+ 最小 IR 平滑切换 ✅ 已落地，待评测口径验收 | IR 引用率达标（评测口径） |
| 2.3 | rag 全链（切片/双路/RRF/rerank/引用强制/权限过滤）✅ 最小全链已落地，待指标达标 | RAG 引用正确率 ≥98%（`cited_clause` 与黄金集 span 重合率，§4 #19）；rerank 越权负向探针 |
| 2.4 | rules 完整治理面（灰度/shadow/正负例回归/规则级兼容性检查）✅ 最小版已落地，待补全 | 规则灰度负向探针；兼容门禁实证 |
| 2.5 | pgvector 实测决策（量级/延迟/运维面）⏳ | 决策记录 + L3 补批 |
| 2.6 | 影子模式 + 人工抽检运行机制（reason_type 回流接通）⏳ | 首份影子比对报告 |

### 第三批（评测与交付，出口判据见总案 §6）

| # | 任务 | 验收物 |
|---|---|---|
| 3.1 | eval 门禁扩指标（遗漏率/误报率/采纳率）+ judge 计量接入 ✅ 最小版已落地（EvalController + eval_case/eval_run），待挂 CI 实证 | 流水线关卡全量启用 |
| 3.2 | notification 消警闭环（触发→预警→ack→外部回写）✅ 最小版已落地，待验收 | 漏报率评测上线 |
| 3.3 | integration AI 节点 + Shadow→Assisted→Automated 三态 ✅ 最小版已落地（IntegrationMode 三态 + HMAC 回调校验 + SSRF 守卫），待验收 | 三态切换可回退实证 |
| 3.4 | bone-ai-app 后端聚合宿主 ✅ 已落地；HITL 确认台 / 评测看板 / 网关监控（前端 qiankun 子应用）⏳ 目标态 | 新客户接入 ≤15 人日（配置制，§4 #19 口径） |

---


---

## 6. 生产就绪清单（Go-Live 判据）

> **回答一个问题**：沉淀的 AI 能力要上生产，还差什么。六域清单 + 上线判据，双形态适用（产品进程内嵌库为主形态，`bone-ai-app` 聚合宿主为辅形态）。状态：✅ = 代码已具备；⏳ = 待建（含责任批次）。**硬依赖项未清 = 不可切生产流量。**

### 6.1 部署与配置

| 项 | 要求 | 状态 |
|---|---|---|
| 配置外化 | `bone.ai.*` 全量参数（provider `base-url`/档位路由表/红线/配额/脱敏规则/缓存与注入开关）经 Nacos / ConfigMap 注入，不入镜像、不入代码 | ⏳ 随客户环境落地（app 已读 `application.yml` 骨架） |
| 密钥托管 | api-key 走 bone-security Virtual Key 语义，配置与日志零明文（当前为直接配置项，上生产前必须替换）；**网关侧 seam 已接通**（2026-10-23：`ApiKeyProvider` 按调用解析 + 默认 `ConfigApiKeyProvider` + `@ConditionalOnMissingBean` 覆盖点，客户端每次请求现取——换 key 不重启，轮换 / 短时限 Virtual Key 语义自此成立）；**启动 fail-fast 已具备**（`LlmGatewayProperties.validateProviderSecrets`：未解析 `${}` 占位符直接启动失败；https 远端空 key WARN——见 §2.1） | ✅ seam 已接通，**⏳ L3 剩余 bone-security Virtual Key 能力本体**（密钥托管存储 + 出库接口 + `@Bean ApiKeyProvider` 适配实现；此前「零消费方死缝」已修，接入方覆盖即生效、网关零改动） |
| 数据库 | 8 张表已入 `bone-init.sql`（HC-008 四必备列 + 软删 + Snowflake 主键）；HikariCP 连接池按 QPS 规划 | ✅ |
| app 宿主部署 | K8s Deployment + HPA + Actuator liveness/readiness 分组探针 + 资源限额；与产品进程内嵌库的边界见总案 §3.1/§4.7 | ⏳（`spring-boot-starter-actuator` + **探针分组配置已落地** ✅——`application.yml` 已配 liveness/readiness 分组；Deployment/HPA/资源清单待补） |
| sidecar（批二） | Docling 独立 Deployment、CPU 限额、契约版本化（§2.5） | ⏳ |

### 6.2 可观测（三件套：指标 / 日志 / 追踪）

| 项 | 要求 | 状态 |
|---|---|---|
| 健康检查 | Actuator `/health`（liveness/readiness 分组）+ `/info`（parent 已配 build-info） | ✅ 依赖 + **探针分组配置均已落地**（`bone-ai-app/src/main/resources/application.yml`：`probes.enabled=true` + `group.liveness`/`group.readiness`） |
| LLM 计量 | `LlmUsageSink` 结构化日志（token / 耗时 / 档位 / 租户 / 场景）→ 日志平台聚合看板 | ✅ 代码具备，看板 ⏳ |
| 指标化 | `LlmMetrics` 端口 + Micrometer：网关 P99 延时（§4 #15）、配额命中率、短路率、降档率、缓存命中率、**TTFT（首 token 时延，`recordFirstToken` 发射点 v1.8 已具备）**；再接 OTel `gen_ai.*` 语义约定（映射表 v1，含 `gen_ai.time_to_first_token`，规范仍 Development，勿硬编码常量） | ⏳ L3（需 Micrometer 依赖，走 CVE/SBOM 门禁） |
| 证据链观测 | trace 落盘成功率 ≥99.9% 告警（§4 #16；同步 insert + 拒返断言代码已具备，告警规则待配） | ⏳ |
| 告警出口 | `bone-ai-notification` Webhook + HMAC 签名可作告警通道（多级预警 + ack + 消警闭环） | ✅ 最小版已落地 |

### 6.3 安全与合规

| 项 | 要求 | 状态 |
|---|---|---|
| 默认防线（零配置保护） | 成本红线强制 ✅；出口 PII 脱敏默认开 ✅（身份证 ISO 7064 / 银行卡 Luhn 校验位闸门）；租户配额默认开 ✅；结构化校验（`jsonResponse` 时）✅——总表见 §2.1 | ✅ |
| 威胁模型评估 | 提示注入检测（默认关，启发式误伤面）与输出内容安全（默认关）：上线前按场景威胁模型决定是否开启；开启即配误伤监控与人工抽检 | ⏳ **硬依赖**（决策记录归档） |
| 出站安全 | SSRF 守卫（integration 出站）+ HMAC 回调校验 + Webhook 签名（notification） | ✅ |
| 审计留存 | 证据快照 ≥7 年 / 运行日志 ≥6 个月（§4 #12）；`ai_trace_snapshot` 分区策略（按月 / 按租户）随数据量落地 | ⏳ |
| 供应链 | SBOM 冻结 + CVE 门禁 + 负表拦截（SBOM §6）；客户交付披露口径待法务回复 5 项待确认 | ✅ 流程已立，法务签字 ⏳ |

### 6.4 容量与性能

| 项 | 要求 | 状态 |
|---|---|---|
| 网关增量 | 库形态 P99 <10ms（§4 #15） | ✅ 设计具备，POC 实测回填 |
| 配额多副本 | `TenantQuotaGuard` 双实现：进程内固定窗口（单副本）+ **Redis 分布式固定窗口**（`RedisTenantQuotaGuard`，Redisson `RMap` 计数 + 首键 TTL；容器存在 `RedissonClient` bean 且 `quota-redis-enabled=true`（默认）时自动启用；**Redis 故障 fail-open + WARN**——配额是成本防线不是可用性防线，不能因缓存抖动阻断业务）。多副本部署不再有硬依赖 | ✅（`bone-ai-gateway` 已落地；`spring-boot-starter-data-redis`/Redisson 由宿主按需提供，`@ConditionalOnClass` 守卫，零新增必选依赖） |
| 检索性能 | 检索 P95 <800ms 不含生成（§4 #7，批二压测）；provider 超时/重试/降档参数按容量实测（§4 #2） | ⏳ 批二 |
| 检索容量护栏 | `max-scan` fail-closed 已落地（§4 #20，超限即拒、不静默截断）；容量水位观测（租户切片数分布）与 pgvector 索引化排期 | ✅ 护栏代码具备，水位观测 ⏳ 批二 |
| 检索权限隔离 | 租户硬隔离 + **文档级 ACL seam**（`RagAclPort` 契约 + fail-closed 403）已落地；**接入方 ACL 适配**（文档中心 / bone-ai-document 可见集合）为 L3 硬依赖——未接入前同租户内跨文档越权面仅由「租户硬隔离 + 网关层约定」兜底 | ✅ seam 代码具备，ACL 适配 ⏳ L3 |
| 降级可观测 | 降级痕迹码透出响应 `degradations` + `RagEventSink` 事件端口（默认 WARN 日志）；trace 落库 + 告警接线（落盘成功率 ≥99.9% 告警线） | ✅ 端口与痕迹具备，trace 适配 ⏳ L3 |
| 证据容量 | 事件 payload ≤8KB、大 IR 走 MinIO（§4 #8/#9） | ✅ |

### 6.5 灰度与回退（§3.6 Runbook 摘要的上线动作）

- 能力级开关 `shadow / assisted / automated / off`，`off` 秒级降级为纯确定性/人工路径 ✅；
- 模型档位回滚 = 配置版本切回（目标 ≤5 分钟，无需发版）✅ 机制具备；
- provider 故障演练：停 vLLM / 断网 → 验证 `aiError` → `DecisionGate` 转人工路径（AI 不阻断业务，§4 #18）——代码路径 ✅，**演练记录** ⏳ 硬依赖；
- 规则灰度 / shadow + 正负例回归门禁 ⏳ 批二；
- 回滚面：库形态 = 依赖版本回退；app 宿主 = 镜像回退；DDL 前向兼容（只加列不改义，存量数据不迁移）。

### 6.6 上线判据（Definition of Done——全绿方可切生产流量）

1. 六域清单无未清**硬依赖**：密钥托管（网关侧 seam 已接通，剩余 bone-security Virtual Key 能力本体 + `@Bean` 适配；启动 fail-fast 已具备）、健康探针、告警规则、故障演练记录、威胁模型决策（Redis 分布式配额已代码落地，多副本部署即时生效，不再列为硬依赖）；
2. 负向探针全绿：红线短路不发事件、规则命中不调 LLM、越权检索拒绝、无证据拒返、注入/内容安全策略生效（若开启）；
3. 端到端演练：解析 → 规则 → LLM → `DecisionGate` → HITL → 回放，在**生产配置**（真实 provider + 密钥托管 + 生产库）跑通一轮；
4. 对应批次出口判据（总案 §6）全过；
5. 回滚预案实操过一次：依赖回退 / 镜像回退 / 配置回切 ≤5 分钟；
6. on-call 手册 + 异常处置表（§3.5）随版本发布，值班人知晓 HITL 48h 升级路径。

---

## 7. 依赖、环境与风险

**运行环境**：JDK 21 / Spring Boot 3.5.x（现状基线）；MySQL 8.0.31 主库；MinIO；推理 vLLM（本地 GPU）或 Ollama（POC）；sidecar 容器（Docling CPU 起步）。**依赖红线**：新增依赖过 CVE/SBOM 门禁（负表见 SBOM §4）；Boot 升级走平台轨道不与本项目捆绑（总案 §6）。

**风险与开放决策**：总案 §7 的 L3 清单为准（聚合定位/新 DDL/sidecar 形态/pgvector 推迟/锁型与 Spring AI 例外）；详设新增两条：① `GenAI` 观测规范仍 Development——映射表版本化，勿硬编码常量；② bge-m3 维度/模型升级走版本迁移（`embedding_ver` 入 chunk 元数据，全量重建按批次灰度）。

---

## 附录 A：平台 API 面（消费方视角）

| 面 | 接口 | 部署形态 | 消费方 |
|---|---|---|---|
| 模型调用 | `LlmGatewayApplicationService.invoke()` | 进程内 starter | 产品服务 / 编排 |
| 编排 | `OrchestrationApplicationService.run()` | 进程内 starter | 产品服务 |
| 解析 | `DocumentParseApplicationService`（进程内；REST 面露暴为目标态） | 进程内 starter | 产品服务 / 编排 |
| HITL | `GET/POST /api/ai/v1/hitl/...`（items/act/dispositions/overdue/escalate-overdue） | REST（bone-ai-app） | 确认台（前端目标态）/ 上层调度 |
| RAG | `POST /api/ai/v1/rag/retrieve` · `/ingest` | REST（bone-ai-app） | 产品服务 / 编排 |
| 评测 | `GET /api/ai/v1/eval/runs` · `POST /cases` · `/runs` · `/judge` | REST（bone-ai-app） | 研发 / 发布流水线 |
| 预警 | `POST /api/ai/v1/notify/dispatch` · `/ack` | REST（bone-ai-app） | 上层业务 |
| 集成 | `POST /api/ai/v1/integration/invoke` | REST（bone-ai-app） | 上层业务 |
| 规则 | `GET/POST/PUT /api/ai/v1/rules/...`（CRUD / 发布首版与次版 / 回滚 / 灰度 / hitBlock / 废弃 / 试算求值，均租户隔离） | REST（bone-ai-app） | 管理员 / 规范流程 |
| 证据回放 | `GET /api/ai/v1/trace/{traceId}`（三元组回放）· `/item/{id}`（审计抽查，越权 403） | REST（bone-ai-app） | 审计 / 法务 / 产品自查 |
| 演示 | `POST /api/ai/v1/pipeline`（bone-ai-app / bone-ai-server） | REST | 联调 / Demo |

> 进程内门面优先（零额外跳）；跨语言调用方出现时经 MCP/HTTP 暴露（网关演进触发条件见总案 §4.7），契约不变。除规则与证据回放外，其余 REST 面（HITL/RAG/评测/预警/集成/演示）均已在 `bone-ai-app` 落地；标注「目标态」的面当前仅进程内服务可用——接入方按目标态排期，勿按已发布 API 排期。
>
> **门面分层**：产品 → `LlmGatewayApplicationService`（红线/脱敏/路由/证据编排）→ `LlmClient`（provider SPI，仅 gateway 内部依赖）；产品只依赖门面，禁 import `LlmClient` 实现（ArchUnit #3）。

## 附录 B：与既有资产的衔接

| 既有资产 | 衔接方式 |
|---|---|
| `RuleEngine`/`BusinessRuleEngine` | rules 复用其**触发 / 编排 / 元数据装载**；存量执行走 SpEL，**新规则资产的 QLExpress 求值由 bone-ai-rules 自建**（§2.4 P6 澄清），治理面为净增价值 |
| `WorkflowMetadata` | **目标态**驱动 orchestration 多节点状态机（含 `FALLBACK_NODE`）；当前顺序流水线未接线该模型 |
| `ExtensionDecisionRecorder` | trace 持久化升级（数据结构映射，旧调用点迁移） |
| bone-file / `FileStorage Port` | document 按 MinIO 规范直连或 HTTP，不进程内依赖 |
| bone-notification / bone-integration | **不挂 AI 出站治理面**：C9/C10 落 bone-ai-notification / bone-ai-integration 新建模块（总案 v1.9 改判），存量两模块维持原域 |
| `MetadataVersionController` | rules 补规则级兼容性检查（字段级反射实现收敛属技术债） |
| pk-tpa-saas 控额引擎 | bone-ai-ledger 内核抽取来源（域中立额度台账；保险语义留产品域） |
| bone-ai-app / bone-ai-server | 聚合宿主 / 参考宿主：装配上述库 + REST 面，不含领域逻辑（server 内含 ClaimService 理赔 demo，仅演示用途） |

## 附录 C：产品侧衔接契约

| 环节 | 平台侧 | 产品侧（procuraAI / ClaimSphere / Equity） |
|---|---|---|
| 建议供给 | orchestration 产出建议（**当前形态**：`OrchestrationResult` = 规则结果 + `llmResponse` + `DecisionGate` verdict + `aiError`；**目标态**：`AiSuggestion<T>` = value + 置信度 + evidence_ref，批二补建） | 消费建议并渲染证据（条款锚点高亮），不直接落业务决定 |
| 裁决回收 | HITL act → `Confirmed<T>` + `reason_type` 回流 | 业务状态机继续流转（如 procuraAI FR-205 修订流；ESCALATED 两端同名贯通，见总案 §3.3） |
| 证据引用 | `GET /api/ai/v1/trace/{traceId}` + IR span | 结论卡片引用定位原文、审计回放 |
| 规则资产 | rules 版本 / 灰度 / 回归（机制） | 领域规则内容（R01–R25 / 决策图 / 估值校验）留产品侧（总案 §2.2） |
| 评测回流 | `reason_type` → eval_case 滚雪球 | 产品标注 / 黄金集喂评测算（省 60–80% 人工） |
| 消警与集成 | notification / integration 扩展点（第三批） | 履约预警、影子三态接入各自系统 |

> 铁律重申：AI 模块只供"建议 + 证据"；资金 / 责任 / 数值判定只消费 `Confirmed<T>`（类型批二落地前由 `DecisionGate` + HITL 留痕等价保证）；领域资产不入平台。
