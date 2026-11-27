# procurement/contract-ai-review Specification

## Purpose
采购合同 AI 辅助审查的可观察行为契约：把合同解析、带锚点的风险报告、人工复核分层、AI 参与披露三态与留痕幂等固化为可验证的外部行为，确保 AI 仅辅助、人工最终决策、PII 禁入与 fail-closed 三态在整条链路上不被稀释。

## Requirements

### Requirement: 合同解析失败 fail-closed

系统 SHALL 对上传的合同执行解析；当解析失败（含跨页表格无法还原、扫描退化、OCR 不可信、超出可信输入）时，MUST 转人工复核并产出明确的失败状态，MUST NOT 产出自动风险结论或自动放行。解析成功时，条款切分 MUST 保留跨页不截断的条款锚点。

#### Scenario: 解析失败不产出结论
- **WHEN** 上传的合同解析失败（如加密 PDF 或不可读扫描件）
- **THEN** 审查记录进入人工复核状态、携带失败原因；系统不产生 risk_level 结论、不发布 ContractReviewed 事件

#### Scenario: 跨页条款锚点完整
- **WHEN** 一个关键条款跨越 PDF 两页
- **THEN** 风险报告的该条款锚点覆盖完整条款文本，不因分页截断

### Requirement: 风险报告字段与 PII 禁回传

系统 SHALL 对审查结果输出带条款锚点的风险明细（严重度、依据、置信度），且 ContractReviewed 事件 payload MUST 严格等于 D3-v2 §2 的 7 字段（review_id / contract_no / risk_level / risk_items_hash / review_snapshot_ref / ai_available / model_version_snapshot）。风险明细不进事件；报告与事件 MUST NOT 回传 `output` 原文、合同正文全文、供应商联系人 / 身份证 / 银行账号。

#### Scenario: 事件 payload 字段锁定
- **WHEN** 审查完成并发布 ContractReviewed
- **THEN** payload 仅含 7 个约定字段，risk_items_hash 为明细摘要哈希，审查快照仅有 review_snapshot_ref 引用

#### Scenario: PII 注入被拒
- **WHEN** 模型 `output` 原文含身份证号或银行账号被尝试写入报告/事件
- **THEN** 入口拒绝并触发 PII 告警，报告中不含该值（对应负向用例 AI-3）

### Requirement: risk_level UNKNOWN 不降级

系统 SHALL 将 `risk_level` 的 `UNKNOWN` 视为独立风险态；MUST NOT 压成「无风险」或 `LOW`。消费方对 `UNKNOWN` MUST 隔离告警并转人工。

#### Scenario: UNKNOWN 风险态隔离
- **WHEN** 审查结果 risk_level = UNKNOWN
- **THEN** 该记录进入人工复核队列并隔离告警，不被当作低风险自动通过

### Requirement: HITL 置信度分层路由

系统 SHALL 按置信度与后果严重度分层路由审查结果：低置信、高后果、或命中关键负向用例的结果 MUST 转人工复核；AI SHALL 仅提供辅助结论，最终决策权归人工。人工推翻 MUST 记录入追溯链，MUST NOT 静默采纳 AI 结论（含"自信的错误答案"）。

#### Scenario: 低置信转人工
- **WHEN** 审查结果置信度低于冻结阈值
- **THEN** 记录进入人工复核队列，不自动归档 / 不自动放行

#### Scenario: 高置信错误被推翻留痕
- **WHEN** AI 以高置信判定某条款无风险，人工复核推翻该判定
- **THEN** 推翻动作入追溯链并触发评测集增补标记，不被静默采纳

### Requirement: AI 参与披露三态

系统 SHALL 忠实呈现 AI 参与态：`ai_available` 为 AVAILABLE / UNAVAILABLE / UNKNOWN 三态，参与态 UNDETERMINED MUST NOT 压成「AI 未参与」。`model_version_snapshot`（入口可填、版本串截断 128 字符）MUST NOT 作为 AI 参与的证据；唯一认可的参与证据来源是 LLM 节点自报的 `ai_available`。前端对 UNKNOWN / UNDETERMINED MUST 显示「无法确认」并保留原始码。

#### Scenario: 版本快照不冒充参与
- **WHEN** model_version_snapshot 有值但 ai_available = UNAVAILABLE/UNKNOWN
- **THEN** 系统判定为「AI 未确认」，不展示为 AI 已参与（对应负向用例 AI-4）

#### Scenario: UNDETERMINED 不被压低
- **WHEN** 参与态为 UNDETERMINED 或 ai_available = UNKNOWN（含缺省 fail-closed）
- **THEN** 前端显示「无法确认」并保留原始码，不显示「AI 未参与」

### Requirement: 不可信输入隔离

系统 SHALL 将合同 / 附件文本视为攻击者可控的不可信输入：系统提示与用户输入隔离，外部文本 MUST NOT 进入指令通道；AI MUST NOT 触发写 / 放款 / 授标工具；输出 MUST 经确定性规则复核后展示；检出隐藏指令 / 越狱即 fail-closed 转人工并留审计。

#### Scenario: 隐藏指令注入 fail-closed
- **WHEN** 合同正文含隐藏指令试图把 FORBIDDEN 条款判为无风险
- **THEN** 注入防护命中，记录转人工并留审计，不产出自动低风险结论（对应负向用例 PROMPT-INJ-01/02）

### Requirement: 审查留痕、幂等与追溯链

系统 SHALL 以 `review_id`（rv-<ULID>）作为跨域幂等键；消费 ContractReviewed MUST 幂等（同一 review_id 只挂 1 条审查结论）。降级 / 停用 / 人工推翻 MUST 入追溯链，留痕可回溯且快照不可变。

#### Scenario: 重复投递幂等
- **WHEN** 同一 review_id 的 ContractReviewed 被重复投递
- **THEN** 系统只挂 1 条审查结论，不重复处理（对应负向用例 M3-9）

#### Scenario: 降级入追溯链
- **WHEN** AI 网关不可用时审查降级为纯规则路径
- **THEN** 降级动作与原因入追溯链，可回溯，且不跳过人工法审

### Requirement: 能力开关与权限边界

系统 SHALL 以能力开关 `bone.procurement.contract-ai-review.enabled`（默认 false）控制审查端点注册；关闭时端点 MUST NOT 注册。审查写操作与读操作 MUST 受权限点保护，租户上下文 MUST 从登录态取得、不接受自报，租户不一致 MUST fail-closed。

#### Scenario: 开关关闭不注册端点
- **WHEN** 能力开关为 false
- **THEN** 审查相关端点不注册，请求返回未找到，不存在"能调用但无 AI 能力"的假能力

#### Scenario: 越权与跨租户失败关闭
- **WHEN** 无权限用户调用写端点，或命令自带租户与登录态不一致
- **THEN** 请求被拒绝（403 / fail-closed），不产生审查副作用

### Requirement: ContractReviewed 采用持久化事务性发布

系统 SHALL 通过持久化 Outbox 发布 ContractReviewed：审查结论与 Outbox 记录 MUST 在同一事务内落库，发布成功前事件不得丢失；进程重启后未发布事件 MUST 可被继续投递。事件 payload MUST 保持既有 7 字段锁定，MUST NOT 回传 output 原文或任何 PII。

#### Scenario: 事务性发布不丢事件
- **WHEN** 审查结论提交后、事件投递前进程崩溃并重启
- **THEN** Outbox 中未投递事件仍可被发现并继续投递，事件不丢失且 payload 仍为 7 字段

#### Scenario: 发布与结论同事务
- **WHEN** 审查结论写入成功
- **THEN** 对应 Outbox 记录在同一事务内可见；结论写入失败时不得留下孤立 Outbox 记录

### Requirement: 幂等消费与跨租户隔离

系统 SHALL 以持久化 Inbox 消费 ContractReviewed，以 `review_id` 为幂等键：同一 review_id 的重复投递 MUST 只产生一条结论；事件 MUST 携带 tenant 上下文，跨租户错投 MUST 被拒绝并留下拒绝留痕，MUST NOT 用事件的 tenant 覆盖接收方租户或从默认租户继续处理。

#### Scenario: 重复投递只消费一次
- **WHEN** 同一 review_id 的 ContractReviewed 被重复投递多次
- **THEN** Inbox 仅记录一条结论，重复投递返回"已跳过"且不产生第二条追溯记录

#### Scenario: 跨租户事件拒绝且留痕
- **WHEN** 事件的 tenant 与消费方租户上下文不一致
- **THEN** 事件被拒绝、不产生结论，系统留下跨租户拒绝留痕（含 review_id 与拒绝原因）

#### Scenario: 重启后去重状态保持
- **WHEN** 已消费的 review_id 在进程重启后再次投递
- **THEN** 依据持久化 Inbox 判定为重复并跳过，不因内存清空而重复消费

### Requirement: 消费失败进入 DLQ 且可重放

系统 SHALL 在消费处理失败（含解析异常、下游不可用）时把事件投入持久化 DLQ 并记录失败原因；运维可依据 `review_id` 触发重放，重放成功后的结论 MUST 与首次成功消费一致，同一 review_id 绝不产生重复结论。

#### Scenario: 失败入 DLQ
- **WHEN** ContractReviewed 消费过程中抛出可重试异常
- **THEN** 事件进入 DLQ 并记录 review_id 与失败原因，不产生半条结论

#### Scenario: DLQ 按 review_id 重放
- **WHEN** 对 DLQ 中的事件按 review_id 触发重放且依赖恢复
- **THEN** 事件被重新消费并产出唯一结论，DLQ 项标记为已解决，重复重放不产生第二条结论

### Requirement: 事件闭环不改变人工终决与披露语义

升级为持久化闭环 MUST NOT 稀释既有语义：`risk_level=UNKNOWN` 仍隔离转人工、AI 参与披露三态不变、人工推翻的审查快照不可变、降级/停用/人工推翻仍入追溯链。

#### Scenario: UNKNOWN 在持久化链路仍隔离
- **WHEN** 经 Outbox/Inbox 消费的事件 risk_level = UNKNOWN
- **THEN** 该结论仍隔离转人工，不被当作低风险自动通过

#### Scenario: 人工推翻快照不可变
- **WHEN** 人工推翻一条经持久化闭环产生的结论
- **THEN** 原 review_snapshot_ref 指向的快照保持不变，推翻动作入追溯链
