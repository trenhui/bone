---
doc_id: plan.ainative
role: guide
currency: current
---

# synapClaim · AI 原生改造执行计划（Plan 产物 / gsd-core Plan 阶段）

> 本计划为 gsd-core **Plan 阶段**交付物，承接 `tools/_ai_native_readiness.py`（AI 原生就绪度复核器）的静态事实结论，将 12 项判据映射为可执行的「阶段 / 负责 / 动作 / 验收」，并叠加 Phase B/V2 能力纵深路线。
>
> **黄金法则**：本计划中的状态与数值以 `tools/_ai_native_readiness.py` 实跑结果为准，不据此宣称能力已具备；进入 Execute 阶段后，每阶段末端重跑该脚本，以「判据翻转 + l3Claimable=true」作为可复用终止判据。

## 0. 现状基线（2026-11-07 实跑 `tools/_ai_native_readiness.py`）

- **成熟度 L0**（AI 全链路降级，纯规则路径）；12 判据 **5 SAT / 7 BLOCKED**；`l3Claimable = false`
- 运行期证据：`nodes=104, aiParticipated=8, participationRate=0.0769, probeVerdict=NOT_CONFIGURED`
- **关键洞察**：AI 原生**代码脚手架已就位**——
  `RAG_WIRED` / `IMAGE_PARSE_WIRED` / `FLYWHEEL_DOWNSTREAM` / `ROLLOUT_ROLLBACK_DRILL` / `EVAL_GATES_WIRED` 全部 SATISFIED，且**默认关闭、降级诚实**（符合本仓强护栏纪律）。
  卡住 L3 的不是「缺代码」，而是 **激活（provider/解析模式）+ 取证（真实评测/冻结集）+ 上线治理（13 册 go-live 合规）** 三类闸门。

## 1. 工作流拆分

- **WS-A（达标流）**：关闭 7 个 BLOCKED 判据 → 达 L3。验收 = 复核脚本重跑判据翻转。
- **WS-B（深化流）**：AI 原生能力纵深（Phase B/V2 路线图），与 WS-A 并行推进。

## 2. WS-A：判据 → 动作映射（每项含阶段 / 负责 / 动作 / 验收 / 为何）

| # | 判据（现状） | 阶段 | 负责 | 动作 | 验收（脚本翻转） | 为何 |
|---|---|---|---|---|---|---|
| A1 | `PROVIDER_CONFIGURED`（URL 缺失） | P0 激活 | 运维 / SRE | 部署注入 `BONE_AI_GATEWAY_BASE_URL`（含鉴权） | → SATISFIED | LLM 出口未接，所有 AI 行为无法发生，是 L0→L1 前提 |
| A2 | `PERCEPTION_MODEL_DRIVEN`（仅文本层） | P0 激活 | 运维 | 置 `BONE_CLAIM_DOCUMENT_PARSE_MODE=llm` + A1 已配 | → SATISFIED | 感知层仅数电票文本，开 llm 让多模态解析参与 |
| A3 | `RUNTIME_MODEL_COVERAGE`（provider 未配） | P0 激活 | 运维 + QA | A1/A2 后跑前后端 E2E，写 `runtime.probeVerdict=REACHABLE` 且参与率>0 | → SATISFIED | 运行期证据是 L3 唯一真准；配置态不能替代（复核器第 11 轮教训） |
| A4 | `BASELINE_WIRED`（声明未接线） | P1 证据 | 算法 / 评测 | 跑真实评测，填充 `bone-ai-rag/eval/baseline-metrics.json`：`status=wired` + `provenance`(collector/hash/时间) + `sourceSymbol` 可达 + 顶层非 skeleton | → SATISFIED | 非 ML 基线须可复现采集证据，否则假绿（脚本已防改串刷绿） |
| A5 | `FROZEN_EVALSET_ACTIVE`（冻结集未 active） | P1 证据 | 算法 / 评测 | 用真实引擎 `verify_manifest` 产出 active 冻结集（cases 非空 + hash 匹配） | → SATISFIED | 回归门禁需受控冻结集防漂移假绿 |
| A6 | `CATALOG_ADMISSION_ACTIVE`（默认 NONE） | P2 治理 / 决策 | 产品 + 架构 | 评审 `catalog_policy` 默认 NONE→是否改 MANDATORY；确认 `ItemCatalogPort` 生产实现已接线 | 默认 MANDATORY 且端口接线 → SATISFIED | NONE 是刻意 fail-safe（避免全量停摆）；改 MANDATORY 强制准入风险大，**AI 不单方改** |
| A7 | `GOLIVE_NOT_DRAFT`（13 册 draft） | P2 治理 | 合规 / 法务 + 研发 | 13 册 go-live（PIA/DPIA、SLA、容量、租户隔离 CI、算法备案…）补齐正文标 `current` | 13 册 `currency=current` 且正文无「须建设 / 待评审」→ SATISFIED | 上线治理是 L3 合规闸门；**未达 L3 前不提前标 current**（防假绿） |

> 5 个 SATISFIED 判据：保持绿，无需动作（注意它们本就是「门控默认关」的正确形态，切勿为「好看」而误开）。

## 3. WS-B：AI 原生能力深化（P0–P3，对应前轮设计复核）

| 阶段 | 能力 | 动作（受控、走既有 port / ACL） | 验收 | 为何 |
|---|---|---|---|---|
| **P0** | 启用 RAG | provider 就绪后 ingest 语料 + 置 `bone.claim.knowledge.rag.enabled=true`（代码已接线、门控默认关） | 案件链路产生 `clauseRef` 语义证据 | 知识层当前仅目录准入，AI 原生需语义知识 |
| **P1** | 自然语言入口 | 新增 claim-assistant controller（走 RAG + gateway、`@PreAuthorize`、ACL） | 自然语言问政策 / 材料导引可答 | AI 原生第一公民是 NL 交互（当前全 REST CRUD） |
| **P1** | Tool Use | 受控工具面（查政策库 / 历史 / 触发 OCR）经 `port` / ACL，AI 不自建连接 | AI 经工具取数成功 | 从被动节点→受控能动性，是「AI 原生」与「调一次 LLM」的本质区别 |
| **P1** | 多模态激活 | 图像解析已接线门控，激活 + 补全降级码覆盖 | 翻拍 / 照片件进入解析 | 感知层补全 |
| **P2** | 监督式 Agentic 编排 | 固定 DAG 内引入 LLM 规划 / 路由 + 监督多智能体，保留三态开关 + 人工终审 | 复杂案件由 supervisor 路由多 agent，高危仍转人工 | 从固定拓扑→护栏内动态编排 |
| **P2** | 反欺诈去桩 | `GraphFraudPort` / `VisualForensicsPort` 接真实信号 | `available=true` 且参与分诊 | 当前风控空壳 |
| **P2** | MRM 闭环 | champion-challenger、漂移监控、盲标 ≥300、ECE 校准 | 盲标达标 + 漂移告警 | 数据飞轮 + 监管可解释 |
| **P3** | 治理加固 | 三态开关 + 错误预算默认开（`AiRolloutApplicationService` 已具备） | 放量门禁默认生效 | 强项别丢，做成默认能力 |

## 4. 阶段编排（gsd-core 循环）

```
P0 激活(A1→A2→A3)  ──►  P1 证据(A4→A5)  ──►  P2 治理(A6 决策 + A7)
   │                                                │
   └──── WS-B 纵深(P0→P3) 与上述并行推进 ────────────┘
每阶段末端：重跑 tools/_ai_native_readiness.py，以「判据翻转 + l3Claimable=true」为可复用终止判据
```

## 5. 反假绿纪律（沿用复核器设计，不可破）

- 不编造 `baseline` / `frozen` 数据刷 A4 / A5；
- `catalog_policy` 默认 NONE 不单方改（A6 须产品 / 架构拍板）；
- go-live 13 册在系统未达 L3 前不标 `current`（A7）；
- 所有「开启」动作（RAG / 图像 / provider）均经既有门控 + `@ConditionalOnProperty`，默认关。

## 6. 交付物与下一步

- 本计划为 **Plan 产物**；进入 **Execute** 需提供：① provider 凭证（走 A1–A3）、② 真实评测产物（走 A4–A5）、③ 组织 / 合规对 A6 / A7 的签署。
- 关联文档（按 id 检索，不在此复述口径）：`contract.ai`（AI 编排 / 评测）、`design.bone-ai-deps`（中台依赖）、`delivery.11`（L3 外部前置运行手册）、`golive.00`（上线门禁总纲）、`records.evidence.readiness-20261107`（就绪度时点快照）。
- 路线分歧：** regulated 金融域的「AI 原生」必须是「受治理的 AI 原生」**——扩展 AI 的能动性边界（规划 / 工具 / 语义知识 / 多模态），而非拆除确定性护栏与人工终审。
