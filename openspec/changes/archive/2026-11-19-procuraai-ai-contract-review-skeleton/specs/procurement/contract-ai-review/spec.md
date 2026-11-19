# Spec Delta：procurement/contract-ai-review

## Purpose

采购合同 AI 辅助审查的可观察行为契约：把合同解析、带锚点的风险报告、人工复核分层、AI 参与披露三态与留痕幂等固化为可验证的外部行为，确保 AI 仅辅助、人工最终决策、PII 禁入与 fail-closed 三态在整条链路上不被稀释。

## ADDED Requirements

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
