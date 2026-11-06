# spec: claim/ai-native-readiness-governance

## Purpose

让「AI 原生成熟度」可静态复核、可证伪：复核器判据随能力接线同步扩展（每项新判据附变异验证），设计文档只回写实测状态，运行态口径如实记录 AI 参与——目标态永不写成现状。

## ADDED Requirements

### Requirement: 判据扩展须附变异验证
复核器新增判据 MUST 通过自检负向探针（--self-test）：移除对应接线/改回默认值时判据必须翻转，否则判据视为恒真而无效。

#### Scenario: 新判据可被变异翻转
- **WHEN** 对复核器运行自检（内存变造事实做负向探针）
- **THEN** 每项新增判据在对应能力被移除时状态翻转，自检全部通过

### Requirement: 运行态口径如实记录
E2E 与运行态产物 SHALL 如实记录 AI 参与桶（参与/桩/来源不明/降级/无法判定）与归因覆盖率；无真实 provider 时 MUST 标注 RUNTIME_ONLY，禁止以桩执行冒充模型参与。

#### Scenario: 无 provider 时如实标注
- **WHEN** 环境未配置真实 AI provider 并运行 E2E
- **THEN** 结果产物如实记录桩执行口径，复核器将该运行态判据标为 RUNTIME_ONLY 阻断而非达标

### Requirement: 文档诚实回写协议
设计文档 SHALL 区分「实测态」与「目标态」：任何能力在代码与复核器双确认前，文档 MUST NOT 声称其已落地；`l3Claimable=false` 时文档结论必须如实呈现并登记外部前置。

#### Scenario: 未落地能力不得写成现状
- **WHEN** 某能力仅有配置或代码骨架、复核器判据仍 BLOCKED
- **THEN** 设计文档将该能力标注为目标态/规划中，成熟度结论与复核器输出一致
