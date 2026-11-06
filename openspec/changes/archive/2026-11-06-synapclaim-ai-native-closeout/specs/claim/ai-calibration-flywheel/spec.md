# spec: claim/ai-calibration-flywheel

## Purpose

把学习层从「只采集评测样本」补齐为「有人消费样本」：对出站评测样本计算校准指标（Brier 分数 / ECE），并支持 champion-challenger 离线对比评测，为置信度阈值与放量决策提供可复算、可证伪的依据。校准消费是纯离线分析能力，绝不参与资金路径与在线决策。

## ADDED Requirements

### Requirement: 校准指标计算
系统 SHALL 能对已采集的 AI 评测样本集合计算 Brier 分数与 ECE（期望校准误差，等宽分桶），并产出包含样本量、桶分布、指标的校准报告；样本量不足时报告 MUST 标注 `INSUFFICIENT_SAMPLES` 而非编造数值。

#### Scenario: 样本充足时产出校准报告
- **WHEN** 评测样本集合包含至少阈值数量的带置信度与金标结论的样本
- **THEN** 计算产出 Brier 分数与 ECE，报告含样本量与分桶明细，且同一输入重复计算结果一致（确定性）

#### Scenario: 样本不足时诚实标注
- **WHEN** 可用样本量低于最小阈值（或样本缺置信度/金标）
- **THEN** 报告标注 `INSUFFICIENT_SAMPLES`，不输出任何指标数值

### Requirement: champion-challenger 离线评测
系统 SHALL 支持在冻结评测集上对比现行版本（champion）与候选版本（challenger）的判定结果，产出逐案差异与汇总指标；评测 MUST 离线执行，不影响在线决策路径。

#### Scenario: 冻结集上对比两个版本
- **WHEN** 提供冻结评测集与 champion/challenger 两版的判定输出
- **THEN** 产出逐案对比（一致/差异及差异方向）与汇总（一致率、各自与金标的一致率），可区分 challenger 优于/劣于 champion 的案件

#### Scenario: 缺金标时仅产差异不给优劣结论
- **WHEN** 评测集样本缺少金标结论
- **THEN** 对比报告仅呈现两版差异清单，不输出「更优」判定

### Requirement: 消费链不回流在线决策
校准报告与 challenger 评测结果 MUST NOT 直接修改在线置信度阈值、分诊档位或资金路径；阈值调整必须经人工依据报告决策并留痕。

#### Scenario: 报告产出后在线行为不变
- **WHEN** 校准报告或 challenger 评测生成完成
- **THEN** 在线决策图的分诊阈值、三档开关与资金路径配置无任何自动变更
