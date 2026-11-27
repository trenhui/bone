# Spec Delta: 采购 AI 运行期就绪度

## Purpose

以只读、可证伪的方式公示采购 AI 在运行期是否可判定可用，防止配置缺失、Mock 身份或评测未转正被误读为"AI 已上场、能力已达标"。

## ADDED Requirements

### Requirement: 就绪度只读端点必须从四类事实推导受控判词

系统 MUST 提供只读端点 `GET /api/v1/procurement/ai-readiness`，判词 ONLY 由四类运行期事实推导：provider 配置态、AI 能力注册态、全域 kill switch 态、评测集状态。端点 MUST NOT 执行模型调用或写入状态，判词取值 ONLY 为 `AVAILABLE`、`DEGRADED`、`UNKNOWN`。响应 MUST 分离"部署门禁通过"与"授标/放款许可"两种语义。

#### Scenario: 默认配置下不得显示可用

- **WHEN** provider 未配置且能力注册存在、总闸开启、评测为 seededDraft
- **THEN** readiness 判词 MUST NOT 为 `AVAILABLE`，且响应 MUST 标识 provider 未配置这一事实

#### Scenario: 评测未转正时不得声称效果达标

- **WHEN** 评测集状态为 `seededDraft` 或通过线未冻结
- **THEN** 就绪度响应 MUST NOT 出现"效果达标""模型准确"之类断言，即使其余事实均为通过

#### Scenario: 取数失败 fail-closed

- **WHEN** 四类事实中任意一类读取失败或返回未知码
- **THEN** 判词 MUST 为 `UNKNOWN`，MUST NOT 回落到 `AVAILABLE` 或 `DEGRADED`

### Requirement: Mock 模型身份必须降级为不可判定并强制人工终决

合同 AI 审查链路在调用 provider 时 MUST 识别响应是否来自确定性 Mock（例如响应含 `mock:true` 或等价 provider 标识）。命中 Mock 身份时，审查结果 MUST 降级为 `UNKNOWN` 三态并强制人工终决，MUST NOT 返回 `AVAILABLE`，MUST NOT 把 Mock 回显当作模型参与证据。

就绪度输出与启动日志 MUST 在 provider 未配置时显式提示"当前为 Mock / 未配置真实 provider"，不得静默回退到可用语义。

#### Scenario: Mock 回显不得被当成模型结论

- **WHEN** provider 返回带 `mock:true` 的响应
- **THEN** 审查结果 MUST 为 `UNKNOWN` 且要求人工终决，MUST NOT 计入任何"模型参与"或"效果达标"统计

#### Scenario: 未配置 provider 的启动提示

- **WHEN** 应用以默认配置启动且 `bone.ai.gateway.base-url` 未设置
- **THEN** 启动日志或就绪度响应 MUST 出现"provider 未配置/Mock"提示，且 readiness 判词 MUST NOT 为 `AVAILABLE`

### Requirement: 判词对前端必须诚实可渲染

前端就绪度与治理页面 MUST 以受控映射渲染判词：未知判词 MUST 显示原始码并告警，MUST NOT 乐观映射为可用；取数失败 MUST 显示"无法确认"，MUST NOT 显示成"就绪"或数值 0；`false` MUST NOT 渲染为达标。

#### Scenario: 未知判词显示原始码

- **WHEN** 后端返回不在受控集合内的判词
- **THEN** 前端 MUST 显示原始判词字符串并给出告警样式，不得折叠为"可用"或默认成功态

#### Scenario: 请求失败显示无法确认

- **WHEN** readiness 端点请求失败或无响应
- **THEN** 前端 MUST 显示"无法确认"，不得显示 0、空白或成功态
