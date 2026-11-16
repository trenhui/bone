# claim/decision-graph-orchestration Specification

## Purpose
把决策执行从「5 节点串行、配置只控制跳过」收敛为文档约定的 7 步链路与可编排流程层，并给脚本类条件提供受控执行边界，使执行轨迹、失败降级与配置读取行为可被外部验证。

## Requirements

### Requirement: 决策图节点拓扑与可查询轨迹

系统 SHALL 按单一真源（`DecisionGraph.canonical()`）登记的阶段顺序执行决策链路，并为每个阶段写入可查询的执行轨迹。阶段数、名称与 `node_type` 映射 SHALL 只有一份定义，文档不得另写第二套。流程配置 SHALL 只提供「允许跳过的节点集合」，不得改变节点顺序或注入新节点。跳过 AI 评估节点时 SHALL 无条件产出 `MANUAL_REQUIRED` 与 `FLOW_CONFIG_AI_SKIPPED`，不得当作加速路径。

#### Scenario: 完整链路

- **WHEN** 流程配置为空且所有节点处理器可用，提交一个待决策赔案
- **THEN** 轨迹包含与 `canonical()` 完全一致的全部阶段记录，顺序一致，且每个阶段有明确结果与耗时

#### Scenario: 跳过 AI 评估转人工

- **WHEN** 流程配置声明跳过 AI 评估节点
- **THEN** 该节点不执行且轨迹标注跳过原因，最终结论为 `MANUAL_REQUIRED`，携带 `FLOW_CONFIG_AI_SKIPPED`

#### Scenario: 白名单外节点不得跳过

- **WHEN** 流程配置包含白名单外的节点名（例如 `Adjust`）
- **THEN** 该节点仍被执行，配置写入端或读取端拒绝/忽略该项，且不产生跳过记录

#### Scenario: 配置读取失败回落

- **WHEN** 配置仓储不可用或 `config_json` 损坏
- **THEN** 系统按空跳过集合继续执行完整链路，不因配置读取失败而中断理赔决策

### Requirement: 显式流程层编排

系统 SHALL 提供 Stage → FlowFireService → Trigger → Condition 的显式流程层，由 Condition 求值结果决定后续流转。Condition 求值异常或超时 SHALL fail-closed：停止推进、返回可诊断错误、不写业务状态。

#### Scenario: 正常流转

- **WHEN** 阶段完成且 Trigger 命中、所有 Condition 求值为真
- **THEN** FlowFireService 推进到下一 Stage，并留下可查询的触发记录

#### Scenario: 求值异常 fail-closed

- **WHEN** 任一 Condition 求值抛出异常或超过约束
- **THEN** 流程停止推进，返回可诊断错误，且不产生状态变更或领域事件

### Requirement: 脚本类条件受控执行

脚本类 Condition SHALL 在受限沙箱内执行。沙箱 SHALL 禁止访问宿主文件系统、网络与任意反射调用，并强制超时与执行步数上限。越界脚本 SHALL 被终止，返回可诊断错误，且不得改变任何业务状态。

#### Scenario: 合法脚本求值

- **WHEN** 脚本只读取传入的事实上下文并返回布尔值
- **THEN** 沙箱返回该布尔值，流程按结果继续

#### Scenario: 越界脚本被阻断

- **WHEN** 脚本尝试访问文件系统、发起网络连接、调用反射或耗尽执行步数
- **THEN** 沙箱终止执行并返回可诊断错误，流程 fail-closed，业务状态保持不变

#### Scenario: 沙箱未启用时不得静默放行

- **WHEN** 运行时未提供可用沙箱实现
- **THEN** 脚本类 Condition 求值失败并按 fail-closed 处理，不得在宿主环境直接执行脚本
