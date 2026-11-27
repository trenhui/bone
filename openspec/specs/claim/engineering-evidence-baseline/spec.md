# claim/engineering-evidence-baseline Specification

## Purpose
为 synapClaim 的每一处 fail-closed / 默认关安全姿态提供结构化、可机检、可变异证伪的工程证据，使 L2/L3/L4 授权解锁有可信前置；本能力不改资金行为、不改赔付金额、不放宽既有门禁判据。

## Requirements

### Requirement: 工程证据视图结构可机检
系统 SHALL 提供汇总工程证据的只读视图，逐项列出 fail-closed 落点、不可达维度原因、provider 配置口径与能力声明-实现一致性，每项 SHALL 含稳定判据 ID、当前状态与受控原因码。该视图 MUST 不改变任何业务状态，也不得替代 `ai-native-readiness` 的 12 项门禁判据。

#### Scenario: 证据视图稳定可查
- **WHEN** 请求工程证据视图
- **THEN** 返回含每项判据稳定 ID、状态与原因码的结构化结果，且无业务状态写入

#### Scenario: 证据视图不放宽门禁
- **WHEN** 工程证据视图中某项标记为已就绪
- **THEN** `ai-native-readiness` 对应门禁判据状态不受影响，仍需按原判据独立判定

### Requirement: 不可达维度显式拒绝
当责任限定条件配置中出现当前无配置列或依赖外部目录的维度（区域 / 医保 / 药品 / 诊疗）时，系统 MUST 返回显式拒绝结果与稳定原因码，清晰说明该维度不可判定及解锁前置；禁止静默忽略、禁止按「无限制」继续理算，禁止因此改变赔付金额。

#### Scenario: 不可达维度被显式拒绝
- **WHEN** 限定条件配置包含区域/医保/药品/诊疗任一维度
- **THEN** 判据返回显式拒绝与稳定原因码，案件不被静默按无限制处理

#### Scenario: 已支持维度正常判定
- **WHEN** 限定条件配置仅包含医院与病种两个已落地维度
- **THEN** 按既有确定行为比对，不产生不可达拒绝

#### Scenario: 拒绝不改变金额语义
- **WHEN** 出现不可达维度拒绝
- **THEN** 不触发任何赔付金额计算或额度占用变更

### Requirement: provider 配置缺失 fail-fast 且口径如实
当 AI 网关 base-url 未配置时，系统 SHALL 在尝试进入自动放行路径前以可诊断错误 fail-fast，并保持既有 fail-closed 决策结果（`MANUAL_REQUIRED`）；运行态产物 MUST 如实标注无真实 provider，禁止以本地桩执行冒充模型参与。

#### Scenario: 无 provider 时 fail-fast
- **WHEN** AI 网关 base-url 缺失且案件进入自动放行前置校验
- **THEN** 返回可诊断错误、决策保持 MANUAL_REQUIRED，不进入自动放款路径

#### Scenario: 桩执行不得冒充模型
- **WHEN** 仅有本地桩能力参与决策
- **THEN** 运行态产物如实标注桩口径，归因覆盖率与参与率按真实参与桶统计

### Requirement: 新增判据须变异可证伪
工程证据底座每一项新增判据 MUST 配套变异验证：移除接线或改回默认姿态时，判据 MUST 翻转；仅改状态字面串不得翻绿。自检负向探针全部通过方可视为判据有效。

#### Scenario: 移除接线判据翻转
- **WHEN** 移除某项工程证据判据的接线并运行自检
- **THEN** 该判据状态翻转，自检如实报告失败

#### Scenario: 刷状态字面串不得翻绿
- **WHEN** 仅把状态字段改为目标值而无真实证据
- **THEN** 判据保持未就绪，不出现假绿
