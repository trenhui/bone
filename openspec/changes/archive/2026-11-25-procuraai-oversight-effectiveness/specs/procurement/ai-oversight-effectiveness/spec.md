# procurement/ai-oversight-effectiveness Specification

## Purpose
为采购 AI 治理提供人类监督有效性的只读观测能力：基于人工终决台账的真实记录，以 fail-closed 三态判词识别复核流于形式（耗时过短）、推翻无理由、推翻未回流评测三类监督质量失真，帮助 owner 判断是否需要强化人工监督，同时明确不声明模型效果达标、不替代人工终决。

## ADDED Requirements

### Requirement: 监督有效性三态判词

系统 SHALL 对指定能力码计算监督有效性信号，判词只能是 UNDETERMINED、NO_ACTIONABLE_SIGNAL、REVIEW_REQUIRED 三态之一；任何判词都不得对外声明"模型达标/模型健康/监督充分"，也不得自动改变任何能力状态或绕过人工终决。

系统 SHALL 仅依据人工终决台账的真实记录计算，识别以下三类可复核风险：
- 复核耗时低于 owner 配置的时长下限（`reviewDurationFloorMs`）视为"橡皮图章复核"；
- AI 结论与人工结论不一致（override）但理由为空视为"无理由推翻"；
- override 但 `evaluationFeedbackRaised` 为假视为"未回流评测"。

> 边界说明（诚实口径）：本 capability 是**只读审计护栏**，不改变人工终决既有契约。当前人工终决写入路径在 override 时自动把 `evaluationFeedbackRaised` 置为真（"推翻即触发评测增补"的既有承诺），因此通过本系统 API 新建的 override 不会命中"未回流评测"；该检查覆盖的是台账中标记确为假的记录（历史遗留、数据迁移或外部写入）。调用方不得据此声称"新数据已被检测为回流正常"。让新数据的回流标记由真实评测回流流程显式赋值属于人工终决契约变更，需独立立项，不在本 capability 范围内。

#### Scenario: 阈值未配置时不可判定
- **WHEN** 能力已登记且存在人工终决记录，但监督阈值未配置
- **THEN** 判词为 UNDETERMINED，原因为阈值未配置，不返回监督质量结论

#### Scenario: 样本不足时不可判定
- **WHEN** 已配置阈值，但人工终决记录数低于最小样本量
- **THEN** 判词为 UNDETERMINED，原因为样本不足，不返回监督质量结论

#### Scenario: 橡皮图章复核触阈需复核
- **WHEN** 已配置阈值且样本充足，复核耗时低于下限的记录达到或超过 owner 配置的比例阈值
- **THEN** 判词为 REVIEW_REQUIRED，原因码标注复核耗时过短，返回真实计数，提示需 owner 复核监督流程，且不自动改变能力状态

#### Scenario: 无理由推翻需复核
- **WHEN** 已配置阈值且样本充足，存在 override 但理由为空的记录
- **THEN** 判词为 REVIEW_REQUIRED，原因码标注无理由推翻，返回真实计数，且不自动改变能力状态

#### Scenario: 未回流评测需复核
- **WHEN** 已配置阈值且样本充足，台账中存在 override 但 `evaluationFeedbackRaised` 为假的记录（历史遗留或外部写入）
- **THEN** 判词为 REVIEW_REQUIRED，原因码标注未回流评测，返回真实计数，且不自动改变能力状态

#### Scenario: API 新建 override 不命中未回流评测
- **WHEN** override 由本系统人工终决 API 新建，其 `evaluationFeedbackRaised` 被既有写入路径置为真
- **THEN** 该记录不计入未回流评测数；系统不得因此对外声称"回流已被本护栏验证正常"

#### Scenario: 无风险信号不构成背书
- **WHEN** 已配置阈值且样本充足，三类风险均未触阈
- **THEN** 判词为 NO_ACTIONABLE_SIGNAL，且明确该判词不构成模型效果或监督充分性背书

### Requirement: 监督信号 fail-closed 输入约束

系统 SHALL 对非法输入 fail-closed：阈值为负、最小样本量非正、时长下限为负、记录计数为负，均不得产生有效判词。阈值只能由服务端配置提供，不得由 HTTP 请求参数注入。

#### Scenario: 非法阈值被拒绝
- **WHEN** 配置的时长下限或最小样本量或风险比例为负数
- **THEN** 该配置被拒绝，不产生监督信号

#### Scenario: 未登记能力不可评估
- **WHEN** 对未登记的能力码请求监督有效性信号
- **THEN** 请求被拒绝，不返回任何判词或指标

#### Scenario: 客户端不得注入阈值
- **WHEN** 调用方尝试通过请求参数覆盖服务端阈值
- **THEN** 请求参数被忽略或拒绝，判词只依据服务端配置计算

### Requirement: 监督有效性只读观测端点

系统 SHALL 提供需要 `procurement:ai-governance:read` 权限的只读 HTTP 端点，返回能力码、三态判词、原因码、真实计数（样本数/耗时过短数/无理由推翻数/未回流评测数）与"不构成模型背书"标记；读操作不得写入追溯链、不得改变能力状态、不得落库。

#### Scenario: 只读端点返回真实计数
- **WHEN** 具 read 权限的调用方查询已登记能力的监督有效性
- **THEN** 端点返回 ApiResponse 包装的信号投影，各计数取自人工终决台账的真实记录

#### Scenario: 缺少权限被拒绝
- **WHEN** 无 `procurement:ai-governance:read` 权限的调用方查询监督有效性
- **THEN** 请求被拒绝，不返回信号

#### Scenario: 读操作无副作用
- **WHEN** 多次查询同一能力的监督有效性
- **THEN** 追溯链条目数不变、能力状态不变、人工终决台账记录数不变、无新增持久化记录
