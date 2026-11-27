# claim/mrm-governance Specification

## Purpose
定义 MRM 模型风险治理的运行期可查契约：模型卡、PSI 漂移四态与 champion-challenger 结果的可查口径，按维度的偏差监测视图，以及样本不足时的 fail-closed 语义，使治理状态可判定而不伪造金标或宣称模型无偏差。

## Requirements

### Requirement: 治理状态运行期可查
模型卡、PSI 漂移四态与 champion-challenger 对比结果 MUST 可作为运行期只读查询返回；查询结果 MUST 如实反映离线内核当前状态，不得把未验证的模型卡标记为「已验证」。

#### Scenario: 可查状态反映真实内核
- **WHEN** 查询 MRM 治理状态
- **THEN** 返回的模型卡/漂移四态/对比结果与离线内核一致，未验证项如实标识

### Requirement: 偏差监测按维度
偏差监测视图 MUST 支持按机构等级等维度分组观测；每个维度的样本量 MUST 可见；不得在样本不足时输出偏差数值或优劣结论。

#### Scenario: 按维度展示且样本可见
- **WHEN** 查询偏差监测视图
- **THEN** 按机构等级等维度返回观测结果与各维度样本量

#### Scenario: 样本不足不给数值
- **WHEN** 某维度样本量低于阈值
- **THEN** 该维度返回 `INSUFFICIENT_SAMPLES` 且不输出偏差数值或结论

### Requirement: 无金标 fail-closed
无金标样本时，champion-challenger 与偏差结论 MUST 返回 `NO_EVIDENCE` 或 `INSUFFICIENT_SAMPLES`，不得臆造优劣或偏差结论；系统 MUST NOT 为凑样本写入伪造数据。

#### Scenario: 空样本诚实不可用
- **WHEN** 金标样本为空
- **THEN** 评测返回 `NO_EVIDENCE`，不产生任何优劣或偏差结论

### Requirement: 治理边界
本能力 MUST NOT 改变赔付金额、放款开关或任何资金路径；MUST NOT 触碰监管签署状态或合规分册 currency；治理查询为只读观测，不产生业务副作用。

#### Scenario: 治理查询无副作用
- **WHEN** 查询 MRM 治理状态或偏差视图
- **THEN** 不产生案件状态变更、资金写入、配置改动或合规状态变更
