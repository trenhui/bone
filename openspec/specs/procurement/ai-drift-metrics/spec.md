# procurement/ai-drift-metrics Specification

## Purpose
为 AI 决策治理提供漂移信号与治理指标的只读观测能力：基于人工终决与追溯的真实计数，以 fail-closed 三态判词呈现漂移风险，帮助 owner 判断是否需要复核与评测扩样，同时明确不声明模型效果达标。

## Requirements

### Requirement: 漂移信号三态判词

系统 SHALL 对指定能力码计算漂移信号，判词只能是 UNDETERMINED、NO_ACTIONABLE_SIGNAL、REVIEW_REQUIRED 三态之一；任何判词都不得对外声明"模型达标/模型健康"。

#### Scenario: 阈值未配置时不可判定
- **WHEN** 能力已登记且存在人工终决记录，但漂移阈值未配置
- **THEN** 判词为 UNDETERMINED，原因为阈值未配置，override 率为不可判定（不返回数值）

#### Scenario: 样本不足时不可判定
- **WHEN** 已配置阈值，但观测窗口内人工终决样本数低于最小样本量
- **THEN** 判词为 UNDETERMINED，原因为样本不足，override 率为不可判定

#### Scenario: override 率触阈需复核
- **WHEN** 已配置阈值且样本充足，人工终决推翻率大于或等于阈值
- **THEN** 判词为 REVIEW_REQUIRED，返回真实 override 率，提示需 owner 复核与评测扩样，且不自动改变任何能力状态

#### Scenario: 未触阈仅为无信号不构成背书
- **WHEN** 已配置阈值且样本充足，人工终决推翻率低于阈值
- **THEN** 判词为 NO_ACTIONABLE_SIGNAL，返回真实 override 率，且明确该判词不构成模型效果背书

### Requirement: 漂移信号 fail-closed 输入约束

系统 SHALL 对非法输入 fail-closed：阈值超出 (0,1]、最小样本量非正数、推翻数大于样本数、样本数或推翻数为负，均不得产生有效判词。

#### Scenario: 非法阈值被拒绝
- **WHEN** 配置的 override 率阈值小于等于 0 或大于 1
- **THEN** 该配置被拒绝，不产生漂移信号

#### Scenario: 计数矛盾被拒绝
- **WHEN** 推翻数大于样本总数，或任一计数为负
- **THEN** 该次评估被拒绝，不产生漂移信号

#### Scenario: 未登记能力不可评估
- **WHEN** 对未登记的能力码请求漂移信号
- **THEN** 请求被拒绝，不返回任何判词或指标

### Requirement: 治理指标只读观测端点

系统 SHALL 提供需要 `procurement:ai-governance:read` 权限的只读 HTTP 端点，返回能力码、三态判词、样本数、推翻数、override 率、阈值、最小样本量、原因码；读操作不得写入追溯链、不得改变能力状态、不得落库。

#### Scenario: 只读端点返回真实计数
- **WHEN** 具 read 权限的调用方查询已登记能力的治理指标
- **THEN** 端点返回 ApiResponse 包装的指标投影，样本数与推翻数取自人工终决台账与追溯链的真实计数

#### Scenario: 缺少权限被拒绝
- **WHEN** 无 `procurement:ai-governance:read` 权限的调用方查询指标
- **THEN** 请求被拒绝，不返回指标

#### Scenario: 读操作无副作用
- **WHEN** 多次查询同一能力的治理指标
- **THEN** 追溯链条目数不变、能力状态不变、无新增持久化记录
