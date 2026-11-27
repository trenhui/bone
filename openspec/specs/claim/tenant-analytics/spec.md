# claim/tenant-analytics Specification

## Purpose
定义按保司（租户）维度的运营报表契约：租户分组、跨租户隔离、影子自动化率的 scopeNote 降级语义与无维度时的全量回退，使运营分析既能按租户对比，又不改变既有指标口径或产生跨租户数据泄漏。

## Requirements

### Requirement: 租户分组聚合
运营报表 MUST 支持按 `tenantId` 分组返回终态分布、影子自动化率与时长分位；分组结果与全量口径 MUST 使用同一套指标定义，不得为分组另立计算口径。

#### Scenario: 按租户分组返回
- **WHEN** 请求按租户维度的运营报表
- **THEN** 响应按 tenantId 分组，每组指标与全量口径定义一致

#### Scenario: 无租户维度时回退全量
- **WHEN** 请求未指定租户维度
- **THEN** 返回与现有 `/analytics/summary` 一致的全量口径结果

### Requirement: 跨租户隔离
报表 MUST NOT 在任一租户的分组结果中返回其他租户的数据；请求方无权访问的租户数据 MUST 被排除。

#### Scenario: 租户 A 看不到租户 B 数据
- **WHEN** 租户 A 请求分组报表，数据集中同时含 A、B 两租户案件
- **THEN** 响应仅含租户 A 的分组数据，不含租户 B

### Requirement: 影子指标降级语义
分组报表中的影子自动化率 MUST 携带 scopeNote，明确其为 Shadow 评测口径而非生产出金率；该指标 MUST NOT 被表述或命名为生产自动化率。

#### Scenario: 影子指标带降级说明
- **WHEN** 分组结果包含影子自动化率
- **THEN** 响应附 scopeNote 说明其为影子评测口径，不得作为出金率证据

### Requirement: 指标口径与资金边界
本能力 MUST NOT 改变任何既有指标的计算方式，MUST NOT 改变赔付金额、放款开关或 fail-closed 姿态；报表为只读观测，不产生任何业务副作用。

#### Scenario: 报表只读无副作用
- **WHEN** 生成分组报表
- **THEN** 不产生任何案件状态变更、资金写入或配置改动
