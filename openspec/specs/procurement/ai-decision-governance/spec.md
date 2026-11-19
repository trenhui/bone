# procurement/ai-decision-governance Specification

## Purpose
procuraAI 决策治理的可观察行为契约：把能力三态 fail-closed 准入裁决、具名责任人、一键停用/降级与追溯、人工终决与 override 回流台账、能力开关与权限边界固化为可验证行为，确保 AI 仅辅助、人工最终决策、可停用可审计在整条治理链路上不被稀释。

## Requirements

### Requirement: 能力三态 fail-closed 准入裁决
系统 SHALL 为每个 AI 能力登记运行态并据此裁决推理准入：只有 `AVAILABLE` 允许进入 AI 推理路径；`DISABLED` / `DEGRADED` / `UNKNOWN` / `UNDETERMINED` MUST 拒绝。未登记能力 MUST 按 `UNKNOWN_CAPABILITY` 拒绝。`UNKNOWN` / `UNDETERMINED` 不可判定态 MUST NOT 被压成「可用」或「已停用」放行。放行裁决 MUST 恒带「AI 仅辅助、人工终决」语义（`ALLOWED_ADVISORY_ONLY`），不得授予 AI 终决权。

#### Scenario: 未登记能力被拒
- **WHEN** 对未登记能力码调用准入裁决
- **THEN** 返回 `allowed=false`、`reasonCode=UNKNOWN_CAPABILITY`，不进入任何 AI 推理路径

#### Scenario: 停用与降级能力被拒
- **WHEN** 能力处于 DISABLED 或 DEGRADED 态时调用准入裁决
- **THEN** 返回 `allowed=false` 且 reasonCode 对应 CAPABILITY_DISABLED / CAPABILITY_DEGRADED

#### Scenario: 不可判定态不得压成可用
- **WHEN** 能力处于 UNKNOWN 或 UNDETERMINED 态
- **THEN** 准入裁决拒绝，`effectiveState` 原样返回该不可判定态，不被改写为 AVAILABLE

#### Scenario: 放行也仅辅助
- **WHEN** 能力处于 AVAILABLE 态被放行
- **THEN** 裁决 `allowed=true`、`reasonCode=ALLOWED_ADVISORY_ONLY`，且 AI 不具备终决权

### Requirement: 具名责任人与能力登记
系统 SHALL 要求每个登记能力携带具名责任人（userId 非空）；能力登记初始态 MUST 为 `UNKNOWN`（登记不等于启用），须经具名操作显式启用后才可放行。责任人变更 MUST 留痕。空责任人 / 空能力码 MUST 被拒绝。

#### Scenario: 登记不自动启用
- **WHEN** 登记一个新能力
- **THEN** 其状态为 UNKNOWN、不放行推理，追溯链写入 REGISTERED

#### Scenario: 空责任人与空能力码被拒
- **WHEN** 以空 userId 或空白能力码登记
- **THEN** 请求被拒绝，不产生能力登记副作用

### Requirement: 一键停用/降级与停机演练
系统 SHALL 支持 `enable/disable/degrade/undetermined` 状态迁移；操作人 MUST 从登录态取得、不接受请求自报，缺登录态 MUST fail-closed 拒绝。`UNDETERMINED` 态 MUST NOT 直接启用；降级 MUST 仅从 AVAILABLE 态触发。每次变更 MUST 写入中央追溯链（操作人 + 理由 + 前后态），支持停机演练复盘。

#### Scenario: 匿名治理操作被拒
- **WHEN** 无登录态时调用停用/降级/启用
- **THEN** 操作被拒绝（fail-closed），能力状态不变

#### Scenario: 操作人不接受自报
- **WHEN** 请求体尝试携带自报操作人
- **THEN** 系统忽略该值、仅使用登录态操作人；追溯链记录的是登录态身份

#### Scenario: 不可判定态不得直接启用
- **WHEN** 对 UNDETERMINED 态能力执行 enable
- **THEN** 迁移被拒绝，能力保持隔离不放行

#### Scenario: 停机演练可追溯
- **WHEN** 具名操作人对能力执行 disable 并填写理由
- **THEN** 能力进入 DISABLED、准入裁决随即拒绝，中央追溯链写入 DISABLED 事件含操作人与理由

### Requirement: 人工终决与 override 回流台账
系统 SHALL 将 AI 建议结论与人工终决结论分字段存储；复核人 MUST 从登录态取得、不接受自报，且 MUST 记录耗时与理由。AI 终局护栏 MUST 保证终决权恒在人工侧。结论不一致 MUST 识别为 override、触发 `evaluationFeedbackRaised` 评测增补标记，并计入 override 统计作为 drift 告警输入。系统 MUST NOT 保存 AI output 原文（PII 禁入）。

#### Scenario: 人工推翻触发评测回流
- **WHEN** AI 结论为 LOW、人工终决为 HIGH
- **THEN** 记录 `override=true`、`evaluationFeedbackRaised=true`，override 计数加一，追溯链写入 HUMAN_OVERRIDE

#### Scenario: 结论一致不算 override
- **WHEN** AI 结论与人工终决一致
- **THEN** 记录 `override=false`、不触发评测增补标记

#### Scenario: 匿名终决被拒
- **WHEN** 无登录态时提交人工终决
- **THEN** 请求被拒绝，不产生终决记录

#### Scenario: 终决权恒在人工侧
- **WHEN** 任意人工终决记录生成
- **THEN** `humanHasFinalAuthority=true`，AI 结论仅作建议字段、不构成终决

### Requirement: 能力开关与权限边界
系统 SHALL 以能力开关 `bone.procurement.ai-governance.enabled`（默认 false）控制端点注册；关闭时端点 MUST NOT 注册。写操作（登记/状态变更/人工终决）MUST 受 write 权限保护，读操作（清单/详情/裁决/追溯/override 计数）MUST 受 read 权限保护。权限码 MUST 幂等登记且不与既有采购权限号段冲突。

#### Scenario: 开关关闭不注册端点
- **WHEN** 能力开关为 false
- **THEN** 治理端点不注册，不存在「能调用但无治理能力」的假能力

#### Scenario: 读写权限分离
- **WHEN** 调用写端点 / 读端点
- **THEN** 分别要求 `procurement:ai-governance:write` / `procurement:ai-governance:read` 权限，无权限被拒绝

#### Scenario: 权限种子幂等
- **WHEN** 重复执行权限种子 SQL
- **THEN** 权限码与角色绑定不产生重复行，且号段与既有采购权限错开
