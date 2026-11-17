# claim/review-fatigue Specification

## Purpose
把设计文档中的审核疲劳控制变成可观测、可证伪的工作台判据：以现有任务事实统计单班完成量，
超过上限的复核人不再进入候选；连续审核提示只做 soft prompt，不阻断人工终审，也不改变资金路径。

## Requirements

### Requirement: 单班超量候选不可用
系统 SHALL 在任务归属求解前，按租户与复核人统计 UTC 当日 `status=DONE` 的任务数；
当该数达到配置上限（基线 120）时，该 `USER` 候选 MUST 判为不可用，由既有 `TaskAssigner`
的“有匹配但候选不可用 → 入池”回落处理。计数异常 MUST 按不可用处理，不得把状态未知的人派入任务。

#### Scenario: 达到单班上限
- **WHEN** 复核人当日完成数等于 `maxPerShift`，且其为唯一匹配规则目标
- **THEN** 该候选不可用，任务入池并可被再派给未超量复核人

#### Scenario: 计数异常
- **WHEN** 工作量仓储查询抛异常
- **THEN** 候选按不可用处理，任务入池；异常不外溢到收单链

### Requirement: 配置 fail-closed 且可观测
系统 SHALL 从 `REVIEW_FATIGUE_GUARD` 的 `config_json` 读取参数；无记录、未开启、非法 JSON、
字段越界或未知键 MUST 回落基线或登记拒绝原因，不得静默生效，也不得把拒绝值写回任务事实。

#### Scenario: 非法配置
- **WHEN** `maxPerShift` 配置为 0 或非整数
- **THEN** 使用基线 120，并在评估结果的 `rejected` 中给出原因

### Requirement: 连续审核 soft prompt
系统 SHALL 提供只读评估端点，返回是否超量、是否应提示休息、敏感案件是否适合派给该复核人；
提示 MUST 不阻断提交、不自动改派、不产生新的案件状态。

#### Scenario: 连续时长达到阈值
- **WHEN** 调用方传入 `continuousMinutes` 等于配置阈值
- **THEN** `restPrompt=true`，但端点不执行任何写操作

#### Scenario: 敏感案件班末提示
- **WHEN** 调用方传入金额达到敏感阈值且 `minutesToShiftEnd` 小于班末排除阈值
- **THEN** `sensitiveEligible=false`，仅作为工作台提示返回；本 change 不宣称已实现服务端班末硬排除
