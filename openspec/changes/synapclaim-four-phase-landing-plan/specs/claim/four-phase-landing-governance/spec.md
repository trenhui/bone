## Purpose

为 synapClaim 四阶段实施路线图的每个节点提供可机检、可复核的落地现状契约，并把卡在外部前置的解锁过程纳入受控门禁，使工程事实与能力宣称不再互相混淆，防止把 fail-closed 姿态误当缺陷、把桩/设计误当达标。

## ADDED Requirements

### Requirement: 四阶段落地现状如实登记
系统 SHALL 为路线图每个节点登记一个受控的四态之一：`IMPLEMENTED`（有代码与测试证据）、`PARTIAL_DEFAULT_OFF`（代码就绪但默认关闭）、`STUB_SAFETY_LOCK`（刻意保留的安全桩）、`DESIGN_ONLY`（仅设计/草案）。每个登记 MUST 附代码证据锚点与验证方式，且 MUST NOT 把 `PARTIAL_DEFAULT_OFF` 或 `STUB_SAFETY_LOCK` 表述为达标或已上线。

#### Scenario: 默认关节点不得表述为已实现
- **WHEN** 一个节点的代码默认关闭或处于安全桩状态
- **THEN** 其状态被登记为 `PARTIAL_DEFAULT_OFF` 或 `STUB_SAFETY_LOCK`，而非 `IMPLEMENTED`

#### Scenario: 现状登记可复核
- **WHEN** 复核任一节点的落地状态
- **THEN** 能定位到具体的代码证据锚点与验证方式，且结论与当前代码一致

### Requirement: 代码可落地差距的收口不改资金语义
对纯代码可补齐的差距（生产理算引擎从 `@Deprecated` 回退路径切到新管线、免赔剩余维度的显式拒绝），系统 SHALL 按受控顺序收口；任何收口 MUST NOT 改变赔付金额、额度占用/冻结语义或决策结果，且 MUST 保留 fail-closed 回退。

#### Scenario: 免赔不可达维度显式拒绝
- **WHEN** 责任限定条件出现无本地事实列或依赖外部目录的维度（区域/医保/药品/诊疗）
- **THEN** 系统返回稳定原因码的显式拒绝，不静默忽略、不按无限制全额理算、不改变金额语义

#### Scenario: 引擎切换保留回退
- **WHEN** 生产金额计算由 `@Deprecated` 旧引擎切换到新管线
- **THEN** 新管线可用性不足时仍回落 fail-closed，不产生无依据的自动放款

### Requirement: 外部前置解锁受控且 AI 不代签
四类外部前置（盲标数据集 / 目录准入 / Go-Live 合规 13 册 / L2 资金闸双签）的解锁 SHALL 各自绑定前置条件、门禁判据与责任方；AI MUST 只承担准备与验证工作，MUST NOT 代签、伪造盲标或证据、把合成/桩数据冒充真人盲标或真实 provider 参与。

#### Scenario: 合成数据不得冒充真人盲标
- **WHEN** 评测使用合成或桩数据
- **THEN** 数据被显式标注为 SIMULATION/合成，相关判据保持未达标，不被登记为真实人工盲标

#### Scenario: 合规签署不由 AI 代签
- **WHEN** Go-Live 13 册处于 `draft`
- **THEN** 系统保持 `GOLIVE_NOT_DRAFT` 未达标，AI 不将其标记为 `current`

#### Scenario: 资金闸变更需双签
- **WHEN** 涉及把视觉反诈安全桩 `available=false` 改为 `true`
- **THEN** 该动作被识别为 L2 资金行为变更，须指定 Review 人与产品签核人书面授权后方可执行，AI 不单方变更

### Requirement: 证据视图不翻绿门禁且单一真源
本规划产出的任何状态或证据视图 SHALL 为只读补充，MUST NOT 替代或放宽 `ai-native-readiness` 12 项门禁判据；两者冲突时 MUST 取更保守（未就绪/BLOCKED）的一方。

#### Scenario: 证据已就绪但门禁仍 BLOCKED
- **WHEN** 规划视图报告某姿态已就绪而对应门禁判据仍为 BLOCKED
- **THEN** 成熟度档位按 BLOCKED 判定，不出现因补充输入而翻绿
