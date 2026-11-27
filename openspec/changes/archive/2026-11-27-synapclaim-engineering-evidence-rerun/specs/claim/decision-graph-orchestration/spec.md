## ADDED Requirements

### Requirement: fail-closed 落点的可查询证据
系统 SHALL 在每个进入 `MANUAL_REQUIRED` 的决策执行轨迹中记录可查询的 fail-closed 原因码（至少覆盖：视觉能力不可用、规则引擎桩、provider 未配置、knowledge 能力不可用），使人工复核可判定「不可用」而非误读为「已核无风险」；该记录 MUST NOT 改变节点拓扑、跳过规则或改变最终决策结果。

#### Scenario: MANUAL_REQUIRED 携带原因码
- **WHEN** 决策因某项能力可用姿态为 false 落到 MANUAL_REQUIRED
- **THEN** 执行轨迹记录对应 fail-closed 原因码，可查询且不影响决策结果

#### Scenario: 原因码不改变决策
- **WHEN** 同一案件仅新增 fail-closed 原因码记录
- **THEN** 决策结果、节点顺序与跳过集合均保持不变
