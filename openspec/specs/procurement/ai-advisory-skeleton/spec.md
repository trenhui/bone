# procurement/ai-advisory-skeleton Specification

## Purpose
procuraAI 业务 AI 建议态纯工程骨架：以治理护栏为裁决来源，提供三态 fail-closed、双标识披露、rules_only 降级与全域总闸联动的可观察行为，明确不接真实模型、不声明效果达标，owner 项固化进决策登记册。

## Requirements

### Requirement: 八能力码登记与未知码拒绝
系统 SHALL 登记 D3-v2 §1.1 口径的 8 个业务 AI 能力码（M1–M7 + ORCHESTRATION）；未知码、空值 MUST fail-closed 返回 UNKNOWN，不得进入建议路径。

#### Scenario: 八码齐全
- **WHEN** 查询能力清单
- **THEN** 返回 M1/M2/M3/M4/M5/M6/M7/ORCHESTRATION 八个码

#### Scenario: 未知码拒绝
- **WHEN** 以未登记码请求建议
- **THEN** 响应 aiAvailable=UNKNOWN、reasonCode=UNKNOWN_CAPABILITY

### Requirement: 三态 fail-closed 与 rules_only 降级
系统 SHALL 在能力未登记 / 停用 / 降级 / 全域 kill switch ENGAGED 时返回 `aiAvailable=UNKNOWN` 且 `degradedTo=rules_only`，主链路不中断；只有治理裁决显式 allowed 且总闸解除才返回 AVAILABLE。UNKNOWN/DEGRADED MUST NOT 压成「AI 未参与」。

#### Scenario: 总闸关闭阻断
- **WHEN** 全域 kill switch 处于 ENGAGED
- **THEN** 任何能力的建议态为 UNKNOWN、degradedTo=rules_only、reasonCode=GLOBAL_KILL_SWITCH_ENGAGED

#### Scenario: 治理拒绝降级
- **WHEN** 治理裁决 denied（停用/降级/未登记）
- **THEN** 建议态为 UNKNOWN 且沿用裁决 reasonCode，不进入 AVAILABLE

#### Scenario: 放行才 AVAILABLE
- **WHEN** 治理裁决 allowed 且总闸解除
- **THEN** 建议态为 AVAILABLE、advisoryOnly=true

### Requirement: 双标识披露与证据禁回传
系统 SHALL 在建议响应中同时携带显式标识与隐式元数据，缺一 `canRelease()` 为 false；证据 MUST 只给引用 URI，不回传模型 output 原文（禁 PII）。披露文案 MUST 说明 AI 仅辅助、人工终决、不参与资金支付/授标/准入定性。

#### Scenario: 双标识齐全才出域
- **WHEN** 显式标识与隐式元数据都存在
- **THEN** canRelease() 为 true；缺任一项为 false

#### Scenario: 证据只给引用
- **WHEN** 生成建议响应
- **THEN** evidenceRef 为 advisory-ref URI，响应体不含模型输出原文

### Requirement: AI 仅建议红线
系统 SHALL 令 `advisoryOnly` 恒为 true，AI 结论不得成为终决；本骨架 MUST NOT 注入 LLM 网关、MUST NOT 产生模型输出、MUST NOT 声明效果达标。

#### Scenario: 建议态恒仅建议
- **WHEN** 任意能力返回建议态
- **THEN** advisoryOnly 恒 true

#### Scenario: 无模型接线
- **WHEN** 审查 ai-advisory 模块依赖
- **THEN** 不依赖 bone-ai-gateway、不声明效果达标

### Requirement: owner 项固化不伪造
系统 SHALL 在决策登记册把 evalset 转正、D3-v2 契约冻结、真模型接入与效果声明、自动放款/授标红线固化为开口项（owner 未指派 / Proposed），不得在工程侧伪造通过线、Kappa 或契约冻结状态。

#### Scenario: owner 项保持 Proposed
- **WHEN** 查阅 DECISION-REGISTER 的 D-15~D-18
- **THEN** 状态为 Proposed 或 Accepted+外部依赖，owner 未指派，不声称已实现
