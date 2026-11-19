# 提案：procuraAI 合同智能审查 walking skeleton（首切片）

## Why

procuraAI 是一套文档设计极度完备、但 AI 主体能力几乎零实现的产品包：已落地的只有采购主数据（MDM）与 PR→PO 交易骨架（3 个归档 change、53/53 任务），而作为其核心价值的「AI 智能风险审查与合同智能处理」在源码中零命中，评测集仍是 `seededDraft`（27 例，目标 200），门禁全绿守护的只是文档一致性。

`deferred-backlog.md` 把原 27 项中的 23 项迁出，其中组 4「AI 合同审查 walking skeleton」是 AI 主体里优先级最高、可独立交付验证的最小切片。本次先落地该切片，把"纸面契约"变成一条可运行、可验证、可 fail-closed 的端到端链路，为后续组 5 决策治理、F1–F4 地基与 N1–N8 内核建立真实代码锚点。

## What Changes

- **新增 AI 合同审查应用能力**（能力码 M2）：合同上传与解析端点，解析失败 fail-closed 转人工、不产出自动结论；复用 `bone-ai-document` 的 `DocumentParseApplicationService` / `ClauseSplitter`，不重建文档解析底座。
- **新增风险标注与审查报告**：按条款锚点（跨页不截断）输出严重度、依据、置信度；审查快照只存引用不存合同正文；**不回传 `output` 原文 / PII**（`ContractReviewed` payload 7 字段唯一真源，D3-v2 §2）。
- **新增 HITL 置信度分层路由**：低置信 / 高后果 / 命中负向用例的审查结果转人工复核，AI 仅辅助、人工最终决策；不得自动归档 / 自动放行。
- **新增 AI 参与披露三态**：`ai_available`（AVAILABLE/UNAVAILABLE/UNKNOWN）与 `UNDETERMINED` 三态忠实呈现——`UNKNOWN`/`UNDETERMINED` 不得压成「AI 未参与」，前端显示「无法确认」并保留原始码；`model_version_snapshot` 不得冒充参与证据。
- **新增审查留痕与追溯链**：降级 / 停用 / 人工推翻入追溯链，`ContractReviewed` 消费侧幂等（`review_id` 唯一），不变量可验证。
- **新增可运行的评测与负向探针**：以现有 `eval/ai-decision-evalset.json` 的 CONTRACT / DISCLOSE / PROMPT-INJ / AI-* 负向用例为验收基线，注入必红、还原必绿；`testSymbol` 对齐源码符号、`groundTruthRef` 保持指向 D3-v2 真源文档锚点（A16 约束不可指向源码）。
- **能力开关**：新增 `bone.procurement.contract-ai-review.enabled`（默认 false），关闭时端点不注册，避免"能调用但无 AI 能力"的假能力。

**非目标（本次明确不做）**：AI 决策治理（组 5：kill switch 演练 / override 回流台账）、F1–F4 地基层全量（数据集 / 标注规范 / 漂移闭环）、G1–G16 能力卡、真实跨域 ACL（Finance `InvoiceMatched`）、法务法源射程结论、模型选型冻结、`t_proc_*` 新 DDL 落库（L3）、评测集扩容到 ≥200（属地基 R-3）。地基层与 owner 决策项按既有纪律单独立项；本次交付的 walking skeleton 使用 fail-closed 降级路径与现有内存/受控夹具，不伪造模型效果数字。

## Capabilities

### New Capabilities

- `procurement/contract-ai-review`: 采购合同 AI 辅助审查的可观察行为——合同解析失败 fail-closed、带条款锚点的风险报告（PII 禁回传）、HITL 置信度分层路由、AI 参与披露三态、审查留痕与幂等消费、能力开关与权限边界。

### Modified Capabilities

- （无。本次不改动现有 spec 级需求；现有 `ai-governance-console` 属理赔域治理控制台，职责不同，复用其"三态/证据/负向用例"设计范式但不改其 spec。）

## Impact

- **代码**：新增 `bone-procurement-contract`（或并入 `bone-procurement` 聚合器）审查应用/领域/适配层；复用 `bone-ai-document`（解析/切分）、`bone-ai-gateway`（LLM 降级/注入防护）、`bone-ai-trace`（留痕）。不修改 Bone 核心（Clean Core）。
- **契约**：消费 INBOUND 事件 `ContractReviewed`（topic `domain.procurement.contract_reviewed.v1` 冻结值，D3-v2 §2 唯一真源）；新增审查 REST 端点与披露端点，须落入 `contracts/openapi.yaml`（本次不新增事件到 AsyncAPI，避免双真源）。
- **权限**：新增 `procurement:contract:review:write/read` 权限码与菜单/权限种子（对齐既有 G40 门禁）。
- **数据**：审查快照引用、`t_proc_*` 幂等消费记录如需落库，仅用既有 SDK 实体与 `bone-init.sql` 既有表的字段级扩展草案；**新表 DDL 属 L3，本次不在生产库执行**。
- **门禁**：AI 评测集治理 / 文档族一致性门禁保持绿态；新增能力码 M2 的源码可达性与关键负向探针须注入必红、还原必绿。
- **AI 合规**：全程遵守 D3-v2 §3（AI 仅辅助、人工最终决策、三态 fail-closed、PII 禁入、证据不回传 output 原文）。
