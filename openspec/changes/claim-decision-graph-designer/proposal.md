## Why

当前决策图由 `claim-ruleset.json` 配置文件驱动，执行引擎「SHALL 只提供允许跳过的节点集合，不得改变节点顺序或注入新节点」。PRD §14 V2.0 要求「决策图可视化编辑器」，而 PRD §7.6 又将「拖拽式规则可视化编辑器」列为二期（V2.0）能力。本 change 在**沿用配置文件驱动、不引入 DDL、不改执行引擎节点顺序语义、坏配置 fail-closed** 的前提下，把只读元数据视图升级为可编辑编排能力。

## What Changes

- **可编辑编排**：在现有只读 `adjust-attribution.ui-schema.json` 元数据视图基础上，提供决策图配置的可视化编辑（节点展示、允许跳过集合配置）。
- **图定义校验**：编辑结果经校验后方可保存，非法配置（改变节点顺序、注入新节点、引用不存在节点）被拒绝。
- **灰度与回滚**：配置变更走灰度发布与秒级回滚，沿用现有 `claim_flow_config` + `feature_toggle` 机制。
- **规格与门禁**：新增 capability spec 定义配置边界、校验规则、fail-closed 与灰度语义；补变异测试。

**非目标（明确不做）**：不引入图定义存储 DDL（沿用配置文件）；不改变执行引擎节点顺序/注入新节点的语义；不碰资金闸与赔付语义；不宣称低代码表单设计器（属另一能力，本 change 仅决策图编排）。

## Capabilities

### New Capabilities
- `claim/decision-graph-designer`: 决策图可视化编排的契约——配置驱动边界、非法配置 fail-closed、节点顺序不可变、灰度与回滚语义，以及变异可证伪性。

### Modified Capabilities
- 无。不修改 `claim/decision-graph-orchestration` 的执行语义需求；本 change 提供编辑侧能力，不改引擎行为。

## Impact

- **代码**：前端 `bone-claim-app` 新增编排页；后端配置校验逻辑（沿用配置文件，无 schema 变更）。
- **配置**：`claim_flow_config` 编辑入口，灰度发布沿用现有机制。
- **测试**：非法配置拒绝、顺序不变、灰度回滚场景测试。
- **不受影响**：决策执行引擎、资金路径、provider、DDL。
