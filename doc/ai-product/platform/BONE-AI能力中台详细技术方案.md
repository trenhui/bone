# BONE AI 能力中台 · 详细技术方案（详设）

> **版本**：v1.1　**日期**：2026-10-15　**归口**：平台架构组
> **v1.1 变更（对齐已落地 gateway/trace 代码）**：§2.1 门面契约补"已落地基线 vs 目标态"差异（P1）；§2.2 trace 字段表/DDL 对齐已落地 `ai_trace_snapshot`（P3）+ "无证据拒返"落地机制（P2）+ 租户兜底待定注记（P4）；§2.4 澄清 rules 执行复用边界（P6）；§2.7 澄清 eval 落点（P8）。
> **配套**：《BONE-AI 能力中台整体方案》（下称**总案**）——总案定"做什么 / 为什么 / 边界红线"（选型定案、许可红线、沉淀边界见总案 §2/§4/§5，不在本文重述），本文定"怎么建"：模块详设、接口与数据结构、阈值初值、任务分解。《BONE-AI 依赖与许可 SBOM 清单》为许可真源。
> **阈值约定**：标注（初值）的口径均为可校准值，压测/评测后修订登记在本文；总案不硬编码阈值。所有新 DDL 以最终 L3 批复为准（总案 §7），本文 DDL 为**拟定稿**。

---

## 1. 运行时架构与部署拓扑

```
产品服务（procuraAI / ClaimSphere / Equity，独立部署，Spring Boot 3.5.x）
   │ 进程内 starter 依赖（bone-ai-gateway/trace/rules…，库形态零额外跳）
   ├─▶ bone-ai-gateway ──HTTP(OpenAI 兼容)──▶ 模型服务（vLLM/Ollama 本地；云端备援随客户形态启用）
   ├─▶ bone-ai-document ──HTTP REST──▶ 解析 sidecar（默认 Docling | PaddleOCR 候选，K8s 独立 Deployment）
   ├─▶ bone-ai-rag ──▶ 向量库（过渡 MySQL JSON + 应用层余弦 | 终局 pgvector 独立实例，实测后定）
   ├─▶ bone-ai-orchestration（状态机 + HITL） / bone-ai-rules（治理面）
   └─▶ bone-ai-trace ──▶ MySQL 8.0.31（快照表，分区见 §2.2）+ MinIO（大 IR 对象）
横切：bone-security（脱敏/密钥）· bone-iam（权限）· bone-metadata-engine（建模语义）· 发布流水线（eval 门禁，不建独立模块）
```

边界不变式（ArchUnit 强制，见 §3.3）：产品依赖 `bone-ai-*` 门面，不直连 AI 实现类；`bone-ai-*` 不 import 产品域；模型 HTTP/key 只出现在 gateway。

---

## 2. 模块详设

### 2.1 bone-ai-gateway（模型调用唯一入站）

**包结构**：`api`（门面/DTO）· `application`（`LlmGatewayService`）· `infrastructure`（`OpenAiCompatibleClient`/`MockLlmClient`）· `config`（`LlmGatewayProperties`）。

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

> **已落地基线 vs 目标态（2026-10-15 对齐记录，P1）**：上为**目标态**契约。已落地（2026-10-14，批一 POC）为**最小版**——`LlmRequest(model, messages, temperature, maxTokens, jsonResponse, shortCircuited)`、消息 `LlmMessage(role, content)`、包结构 `domain/application/infrastructure/config`（门面与值对象在 `domain`，非 `api`）；出站传输用 **JDK `HttpClient`**（零新依赖，对齐 bone-integration `RestClientImpl`）；`traceId` 当前由网关**每次调用新生成 UUID**；成本红线当前**抛 `BizException(AI_GATEWAY_REDLINE_BLOCKED)`**。演进项（批一 rules/orchestration 落地时）：① `AiContext` 外供 `tenantId+traceId+成本上限`，**traceId 改调用方提供以贯穿全链**（缺省才生成）；② `scenario → 模型档位` 路由；③ `outputSchema` 结构化输出强制；④ 红线演进为**短路返回 `RULE_HIT_NO_LLM` 结果型**（编排据此拿规则结果继续，不靠捕获异常）。

**执行链（顺序固定，任一步短路即返回）**：
1. **成本红线**：该场景 `RULE_HIT_BLOCK=true`（规则已命中不调 LLM）→ 短路返回 `RULE_HIT_NO_LLM`；
2. **出口脱敏**：PII/商业机密按 `MaskingRule` 出口层替换（模型侧永不接触原始敏感字段；合成占位符留映射表供回填，由调用方显式恢复）；
3. **路由**：`scenario → 模型档位`（配置表；档位=实际模型名+参数档）；**级联路由**（初值）：结构化/简单场景先行小模型，`outputSchema` 校验失败或置信不足自动升档一次；
4. **调用**：超时/重试见 §4 阈值表；幂等键 = `traceId+scene+hash(messages)`；
5. **证据发布**：每次调用（含短路与失败）发布 `LlmCallRecorded` 事件（payload 精简至 ≤8KB，正文超限走对象存储）；
7. **降级与 fail 落点**：provider 超时/失败 → 自动降备援档位一次（同城备模型）→ 仍失败返回 `AI_GATEWAY_PROVIDER_FAIL`，编排落 `FALLBACK_NODE`/转 HITL——**绝不 fail 到直连模型**（总案 §4.7 刻意分歧）；
6. **响应校验**：`outputSchema` 校验失败 → 一次修复重试 → 仍失败转 HITL（不静默出坏数据）；**对不上证据快照的响应拒绝返回**（运行期断言）。

**配置模型（`LlmGatewayProperties`，Nacos/ConfigMap）**：档位路由表、租户配额（token/日、调用次数/日、超限降档→纯确定性路径）、红线开关、脱敏规则、超时/重试、灰度开关（能力级一键降级）。**密钥**：出库走 bone-security（Virtual Key 语义），配置不含明文 key。

**观测**：`gen_ai.*` 语义约定映射（表见 §3.2，映射表版本化、不硬编码常量）；提示词/响应内容**默认不采集**。

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
> **租户兜底待定（P4）**：`TenantContext` 为字符串，非数值编码（如 `system`）经 `getTenantIdAsLong()` 得 null → 落库前须兜底（映射平台租户 0 或显式拒绝）；批一 rules/orchestration 落地时定。

**落盘时序**：网关事件 `LlmCallRecorded` → `EvidenceTraceListener`（就地哈希+写库）→ `EvidenceSnapshotRepository.findByTraceId` 回放。**结构强制**：无旁路 API——业务代码没有直接写快照的入口；网关是唯一事件发布方、监听是唯一落库方。

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

**执行器**：复用 `WorkflowMetadata`（状态/转换建模已有），补执行器——节点类型 `RULE_NODE` / `AI_NODE` / `HITL_NODE`（收敛）/ `FALLBACK_NODE`（降级）。执行循环：取激活节点 → 求值（规则优先）→ AI 节点产出 `AiSuggestion<T>` → 需人工则停 `HITL_NODE` → 裁决后进入下一转换。**超时/超轮**：单节点超时与轮次上限（初值：单节点 120s、编排轮次上限 8 轮，均可在 WorkflowMetadata 按流程声明覆盖）触发 `ESCALATED` → 转人工全程处理（状态名与产品侧 FR-205 口径一致）。

**类型护栏落地**：

```java
public record AiSuggestion<T>(T value, double confidence, EvidenceRef evidence) {}  // AI 唯一产出
public record Confirmed<T>(T value, ConfirmedBy by, Instant at) {}                  // HITL 裁决或规则产出
// 资金/责任/数值判定入口签名只收 Confirmed<T>；工厂方法是唯一构造通道，ArchUnit 禁隐式互转
```

**HITL 子域接口（REST，确认台 bone-ai-app 消费，权限复用 bone-iam）**：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/ai/v1/hitl/items?status=&scene=&risk=` | 待裁决列表（按风险分派裁决人；置信度 <0.7 强制在列） |
| POST | `/api/ai/v1/hitl/items/{id}/act` | `{action: confirm\|reject\|modify, value?, reason_type?, comment}`；`reject/modify` 必带 `reason_type` |
| GET | `/api/ai/v1/hitl/items/{id}` | 处置链（含 ESCALATED 记录） |

`reason_type` 字典：`RULE_MISS`/`CONTEXT_MISS`/`SUGGESTION_WRONG`/`POLICY_CONFLICT`（唯一回流入口，自由文本不进管道）。五态（PENDING / CONFIRMED / REJECTED / MODIFIED / ESCALATED）与回流口径见总案 §3.3（含与产品侧业务状态机的分层说明），不重复。

```sql
-- 拟定，随第一批补报 L3（总案 §7 新 DDL 清单增补 hitl_item）
CREATE TABLE hitl_item (
  item_id VARCHAR(32) PRIMARY KEY, trace_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(32) NOT NULL,
  scene VARCHAR(64), suggestion JSON NOT NULL,          -- AiSuggestion 序列化（含 evidence_ref）
  status VARCHAR(16) NOT NULL,                          -- PENDING/CONFIRMED/REJECTED/MODIFIED/ESCALATED
  risk_level VARCHAR(8), assignee VARCHAR(64),
  decision_value JSON, reason_type VARCHAR(32), comment VARCHAR(512),
  decided_by VARCHAR(64), decided_at DATETIME,
  KEY idx_status (tenant_id, status, risk_level)
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
- **第一批·最小 IR**：存量 PDFBox/Tika 做条款切片 + span 定位（不等 sidecar，先供端到端出口判据）；
- **第二批·完整管线**：sidecar 契约（总案 §4.4 冻结基线）接入，默认 **Docling**（版面/表格，CPU 可起步），PaddleOCR 为扫描件候选按需启用（不同时维护双实现）。

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

**检索链（一次查询）**：租户/权限过滤 → 向量（bge-m3，1024 维起步）+ BM25 双路 → **RRF 融合**（k=60，权重 0.5/0.5 起步）→ rerank（本地 ONNX cross-encoder，top-30→top-5）→ **引用强制**（`chunk → clauses[].span` 必填，无出处不得生成）→ 生成（LLM 经网关）。

**切片策略（总案"条款级优先、超长二次切分"的阈值化）**：条款为单位入库；单条 >2000 汉字按语义段二次切分（overlap 200 字）；标题/章节层级写入 `chunk.path` 供引用面包屑。

**增量更新**：文档版本事件 → 失效旧 chunk → 重切重嵌（幂等，以 `doc_version` 为批键）。

**接口**：`POST /api/ai/v1/rag/query`（`{query, doc_scope?, top_k}` → `[{chunk_ref, clause_ref, span, score, path}]`）；引用正确率目标 ≥98%（评测口径见 §2.7）。

### 2.7 评测（挂发布流水线，不建独立模块）

**结构**：`eval_case`（输入/期望/双人标注 guidline/`kappa` 记录）+ `eval_run`（运行快照）+ 指标字典（遗漏率/误报率/P/R/F1/引用正确率/采纳率/成本）；门禁 = 发布流水线关卡，不过不合入。

| 门禁关卡 | 指标（第一批基线） | 未达后果 |
|---|---|---|
| 端到端质量 | 引用定位原文 100%（第一批出口判据） | 阻断 |
| 检索质量 | RAG 引用正确率 ≥98% | 阻断（第二批） |
| 稳定性 | 各批负向探针（成本红线/rerank 越权/规则灰度/网关延时增量/trace 落盘成功率） | 阻断 |
| 成本 | 单请求 token 上限与红线命中率 | 告警 + 超限降档 |

LLM-as-Judge 经网关计量（省 60–80% 人工标注）；冷启动 = 小规模人工标注 + HITL 回流滚雪球；不做全量标注平台（总案 §5）。

**DDL（评测集表——总案 §7 明列的两个新 DDL 之一；宿主 = 发布流水线读表出报告，写入走评测管理 REST）**：

```sql
-- 拟定，待 L3（HC-008 四列 + 软删 + Snowflake 主键；实体届时 extends TenantAggregateRoot<Long> + 自声明 @Deleted）
CREATE TABLE eval_case (
    id          BIGINT       NOT NULL COMMENT '主键（Snowflake）',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
    scene       VARCHAR(64)  DEFAULT NULL COMMENT '场景',
    case_type   VARCHAR(16)  NOT NULL COMMENT 'GOLDEN/NEGATIVE/ADVERSARIAL',
    input_doc   VARCHAR(128) DEFAULT NULL COMMENT '输入文档引用',
    input_q     TEXT         DEFAULT NULL COMMENT '输入问题',
    expected    JSON         DEFAULT NULL COMMENT '期望输出',
    annotators  VARCHAR(128) DEFAULT NULL COMMENT '标注人',
    kappa       DECIMAL(4,3) DEFAULT NULL COMMENT '双人标注一致性',
    version     INT          NOT NULL DEFAULT 0 COMMENT '版本',
    created_by  VARCHAR(64)  DEFAULT NULL COMMENT '创建人',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删标记',
    PRIMARY KEY (id),
    KEY idx_eval_case_scene (tenant_id, scene, case_type, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI评测用例';
CREATE TABLE eval_run (
    id          BIGINT       NOT NULL COMMENT '主键（Snowflake）',
    tenant_id   BIGINT       NOT NULL DEFAULT 0 COMMENT '租户ID',
    gate        VARCHAR(32)  NOT NULL COMMENT '门禁名',
    code_ref    VARCHAR(64)  DEFAULT NULL COMMENT '代码引用',
    prompt_ver  VARCHAR(32)  DEFAULT NULL COMMENT '提示词版本',
    rule_ver    VARCHAR(32)  DEFAULT NULL COMMENT '规则版本',
    model       VARCHAR(64)  DEFAULT NULL COMMENT '模型',
    metrics     JSON         NOT NULL COMMENT '指标',
    verdict     VARCHAR(8)   DEFAULT NULL COMMENT '结论',
    started_at  DATETIME(3)  DEFAULT NULL COMMENT '开始时间',
    finished_at DATETIME(3)  DEFAULT NULL COMMENT '结束时间',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '软删标记',
    PRIMARY KEY (id),
    KEY idx_eval_run_gate (gate, finished_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI评测运行';
```

---

## 3. 跨切规范

### 3.1 错误码与 API 规范
- 对齐 `ApiResponse`/`PageResult`（ADR-0028 入站约束）；AI 域错误码全表：

| 码 | HTTP | 可重试 | 语义 / 编排处置 |
|---|---|---|---|
| `AI_GATEWAY_TIMEOUT` | 504 | 是 | 模型超时→降备援档；再失败转 FALLBACK/HITL |
| `AI_GATEWAY_PROVIDER_FAIL` | 502 | 是 | provider 故障→降档；绝不直连 |
| `AI_GATEWAY_REDLINE_BLOCKED` | 200 业务型 | — | 规则命中不调 LLM（短路 `RULE_HIT_NO_LLM`） |
| `AI_GATEWAY_BUDGET_EXCEEDED` | 200 业务型 | — | 配额超限→降纯确定性路径 |
| `AI_GATEWAY_PII_BLOCKED` | 422 | 否 | 出口脱敏策略拦截，补齐字段后重试 |
| `AI_GATEWAY_EVIDENCE_MISSING` | 500 | 否 | 响应对不上快照**拒绝返回**（fail-closed） |
| `AI_RAG_NO_BASIS` | 200 业务型 | — | 无出处不生成、如实说明（防幻觉） |
| `AI_RAG_PERMISSION_DENIED` | 403 | 否 | 越权检索（含 rerank 越权探测口径） |
| `AI_HITL_ALREADY_DECIDED` / `AI_HITL_INVALID_TRANSITION` | 409 / 422 | 否 | 五态流转约束 |
| `AI_EVAL_GATE_FAILED` | 流水线 | — | 评测门禁未过，阻断发布 |

（`AI_PARSE_*` 错误字典见 §2.5 sidecar 契约，对齐 `ApiResponse` 规范）
- REST 面统一 `/api/ai/v1/`；写接口幂等键；租户从上下文取（禁止入参传租户）。

### 3.2 OTel `gen_ai.*` 映射（映射表版本化：`v1`）

| 事件字段 | OTel 属性 |
|---|---|
| op / provider / model | `gen_ai.operation.name`(chat) / `gen_ai.provider.name` / `gen_ai.request.model` |
| tokens_in / tokens_out | `gen_ai.usage.input_tokens` / `gen_ai.usage.output_tokens` |
| latency_ms | `gen_ai.client.operation.duration` |
| trace_id | `gen_ai.conversation.id` |

提示词/响应**默认不采集**（内容采集面独立开关，默认全关）。

### 3.3 ArchUnit 规则清单（第一批门禁实现）
1. 产品域不 import `com.bone.ai.**.infrastructure`（不直连 AI 实现）；
2. `com.bone.ai.**` 不 import 业务产品包名；
3. 业务代码禁 import `LlmClient` 实现类，仅可依赖网关门面；
4. 模型 HTTP/key 出现处收敛于 gateway 包（白名单路径）；
5. AI 服务出口必须经 trace 落盘（调用侧断言 + 事件监听兜底）；
6. 资金/责任/数值判定入口禁 `AiSuggestion` 参数（只收 `Confirmed`）；
7. 禁 Aviator 全量 GAV（`com.googlecode.aviator` / `io.github.aviatorscript`；零消费者死实现已删除）。

### 3.4 数据库与安全
- 全表租户隔离 + 审计字段（`tenant_id/created_by/created_at`）对齐《数据库开发规范》；
- 密钥：模型 key 走 bone-security 出库（Virtual Key 语义），日志/快照不得含明文 key；
- AI 输出落业务表前必须有 `Confirmed` 溯源（`decided_by`/`evidence_ref` 二选一必填）。

---

### 3.5 主流程时序与异常流（fail-closed）

**主流程（一次 AI 建议）**：① 产品调 `LlmGatewayService.chat()`（带 traceId）→ ② 红线/脱敏/路由/调用 → ③ 发布 `LlmCallRecorded`、trace 同步落快照 → ④ 响应对快照校验通过返回 `AiSuggestion` → ⑤ 编排停 `HITL_NODE`（置信度 <0.7 或策略要求）→ ⑥ 确认台裁决 → ⑦ `Confirmed` 回业务、`reason_type` 回流评测。

| 异常 | 判定 | 处置 |
|---|---|---|
| trace 落盘失败 | 快照写库/事件失败 | **响应拒绝返回**（`AI_GATEWAY_EVIDENCE_MISSING`）；事件进补偿队列重放 ≤24h，业务捕获转 HITL |
| 响应校验失败 | `outputSchema` 不过/引用缺失 | 一次修复重试 → 仍败转 HITL（不出坏数据） |
| sidecar 超时 | 返部分 IR | `partial=true` 交编排：小额场景可用部分结果+人工确认，全额场景转 HITL |
| provider 故障 | 降档仍失败 | `AI_GATEWAY_PROVIDER_FAIL` → FALLBACK_NODE / HITL |
| 编排超轮/超时 | §4 阈值 | `ESCALATED` 人工全程处理（与产品侧 FR-205 同名贯通，见总案 §3.3） |
| HITL 超时未裁决 | 分派后 48h | 升级提醒 + 改派；再超时 `ESCALATED` |

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
| 3 | 置信度分档 | ≥0.85 直出 / 0.7–0.85 复核 / <0.7 强制人工（POC 后按评测校准） | HITL | POC 评测 |
| 4 | 切片 | 条款级优先；>2000 字二次切分，overlap 200 | rag | 检索评测 |
| 5 | RRF | k=60，向量/BM25 = 0.5/0.5 | rag | 引用正确率回归 |
| 6 | rerank 窗口 | top-30 → top-5 | rag | 同上 |
| 7 | 检索 P95 | <800ms（不含大模型生成） | rag | 第二批压测 |
| 8 | 证据载荷 | 内联 ≤2MB，超限对象存储 | trace | 线上分布 |
| 9 | 证据事件 payload | ≤8KB（正文超限转对象存储） | gateway | 线上分布 |
| 10 | 租户配额默认档 | 100 万 token/日、5000 次/日（超限降档非拒绝） | gateway | 成本口径 |
| 11 | 精确缓存 TTL | 24h（命中不发 LLM） | gateway | 成本口径 |
| 12 | 留存 | 审计 ≥7 年 / 运行日志 ≥6 个月 | trace | 合规 |
| 13 | 编排轮次 / 节点超时 | 8 轮 / 120s（流程可声明覆盖） | orchestration | 试点场景 |
| 14 | HITL 裁决超时 | 分派后 48h 未决 → 升级改派 | HITL | 试点场景 |
| 15 | 网关延时增量（库形态） | P99 <10ms | gateway | POC |
| 16 | trace 落盘成功率 | ≥99.9%（低于即告警，拒返面放大） | trace | 线上观测 |

---

## 5. 工程落地分解（WBS）

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
| 1.1 | gateway 治理面（路由/配额/脱敏/灰度/降档） | 配额超限降档实证；PII 出口脱敏用例 |
| 1.2 | trace 完整（DDL+留存+回放 API） | 三元组回放实证；无证据拒返断言 |
| 1.3 | rules 最小版（CRUD/版本/命中标记/兼容性检查补齐） | 规则命中不调 LLM 语义打通 orchestration 规则节点 |
| 1.4 | orchestration 执行器 + HITL 子域（五态/确认台 API） | procuraAI 端到端：解析→规则→LLM→HITL→回放 |
| 1.5 | metadata 语义扩展（AiMetadata 档位/阈值、WorkflowMetadata AI 节点） | 建模→执行链路一致性 |
| 1.6 | 最小 IR（PDFBox/Tika 条款切片 + span） | 引用定位原文 100% |
| 1.7 | ArchUnit 规则集 + 门禁接入 | 7 条规则拦截演示 |

### 第二批（解析检索，出口判据见总案 §6）

| # | 任务 | 验收物 |
|---|---|---|
| 2.1 | document 完整管线（sidecar 契约接入，默认 Docling；PaddleOCR 候选按需启用） | 契约冻结 + 默认 Docling 跑通 + PaddleOCR 单适配器就位（替换成本 = 一个类）；互换性由契约保证，不强制双跑实证 |
| 2.2 | IR schema 全量落地（条款索引/span/置信度）+ 最小 IR 平滑切换 | IR 引用率达标（评测口径） |
| 2.3 | rag 全链（切片/双路/RRF/rerank/引用强制/权限过滤） | RAG 引用正确率 ≥98%；rerank 越权负向探针 |
| 2.4 | rules 完整治理面（灰度/shadow/正负例回归/规则级兼容性检查） | 规则灰度负向探针；兼容门禁实证 |
| 2.5 | pgvector 实测决策（量级/延迟/运维面） | 决策记录 + L3 补批 |
| 2.6 | 影子模式 + 人工抽检运行机制（reason_type 回流接通） | 首份影子比对报告 |

### 第三批（评测与交付，出口判据见总案 §6）

| # | 任务 | 验收物 |
|---|---|---|
| 3.1 | eval 门禁扩指标（遗漏率/误报率/采纳率）+ judge 计量接入 | 流水线关卡全量启用 |
| 3.2 | notification 消警闭环（触发→预警→ack→外部回写） | 漏报率评测上线 |
| 3.3 | integration AI 节点 + Shadow→Assisted→Automated 三态 | 三态切换可回退实证 |
| 3.4 | bone-ai-app（HITL 确认台/评测看板/网关监控） | 新客户接入 ≤15 人日（配置制） |

---


---

## 6. 依赖、环境与风险

**运行环境**：JDK 21 / Spring Boot 3.5.x（现状基线）；MySQL 8.0.31 主库；MinIO；推理 vLLM（本地 GPU）或 Ollama（POC）；sidecar 容器（Docling CPU 起步）。**依赖红线**：新增依赖过 CVE/SBOM 门禁（负表见 SBOM §4）；Boot 升级走平台轨道不与本项目捆绑（总案 §6）。

**风险与开放决策**：总案 §7 的 L3 清单为准（聚合定位/新 DDL/sidecar 形态/pgvector 推迟/锁型与 Spring AI 例外）；详设新增两条：① `GenAI` 观测规范仍 Development——映射表版本化，勿硬编码常量；② bge-m3 维度/模型升级走版本迁移（`embedding_ver` 入 chunk 元数据，全量重建按批次灰度）。

---

## 附录 A：平台 API 面（消费方视角）

| 面 | 接口 | 部署形态 | 消费方 |
|---|---|---|---|
| 模型调用 | `LlmGatewayService.chat()` | 进程内 starter | 产品服务 |
| 解析 | `POST /api/ai/v1/documents/parse`（异步 task） | REST | document/编排 |
| HITL | `GET/POST /api/ai/v1/hitl/...` | REST | bone-ai-app |
| RAG | `POST /api/ai/v1/rag/query` | REST | 产品服务/编排 |
| 规则 | `GET/POST /api/ai/v1/rules/...`（CRUD/发布/灰度） | REST | 管理员/规范流程 |
| 证据回放 | `GET /api/ai/v1/trace/{traceId}` | REST | 审计/法务/产品自查 |
| 评测 | `GET /api/ai/v1/eval/runs?gate=` | REST | 研发/发布流水线 |

> 进程内门面优先（零额外跳）；跨语言调用方出现时经 MCP/HTTP 暴露（网关演进触发条件见总案 §4.7），契约不变。
>
> **门面分层**：产品 → `LlmGatewayService`（红线/脱敏/路由/证据编排）→ `LlmClient`（provider SPI，仅 gateway 内部依赖）；产品只依赖门面，禁 import `LlmClient` 实现（ArchUnit #3）。

## 附录 B：与既有资产的衔接

| 既有资产 | 衔接方式 |
|---|---|
| `RuleEngine`/`BusinessRuleEngine` | rules 复用执行，只加治理面 |
| `WorkflowMetadata` | orchestration 复用建模，补执行器 |
| `ExtensionDecisionRecorder` | trace 持久化升级（数据结构映射，旧调用点迁移） |
| bone-file / `FileStorage Port` | document 按 MinIO 规范直连或 HTTP，不进程内依赖 |
| bone-notification / bone-integration | C9/C10 扩展点（第三批） |
| `MetadataVersionController` | rules 补规则级兼容性检查（字段级反射实现收敛属技术债） |

## 附录 C：产品侧衔接契约

| 环节 | 平台侧 | 产品侧（procuraAI / ClaimSphere / Equity） |
|---|---|---|
| 建议供给 | orchestration 产出 `AiSuggestion<T>`（value + 置信度 + evidence_ref） | 消费建议并渲染证据（条款锚点高亮），不直接落业务决定 |
| 裁决回收 | HITL act → `Confirmed<T>` + `reason_type` 回流 | 业务状态机继续流转（如 procuraAI FR-205 修订流；ESCALATED 两端同名贯通，见总案 §3.3） |
| 证据引用 | `GET /api/ai/v1/trace/{traceId}` + IR span | 结论卡片引用定位原文、审计回放 |
| 规则资产 | rules 版本 / 灰度 / 回归（机制） | 领域规则内容（R01–R25 / 决策图 / 估值校验）留产品侧（总案 §2.2） |
| 评测回流 | `reason_type` → eval_case 滚雪球 | 产品标注 / 黄金集喂评测算（省 60–80% 人工） |
| 消警与集成 | notification / integration 扩展点（第三批） | 履约预警、影子三态接入各自系统 |

> 铁律重申：AI 模块只供"建议 + 证据"；资金 / 责任 / 数值判定只消费 `Confirmed<T>`；领域资产不入平台。
