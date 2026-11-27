# Spec Delta：procurement/contract-ai-review

## ADDED Requirements

### Requirement: ContractReviewed 采用持久化事务性发布

系统 SHALL 通过持久化 Outbox 发布 ContractReviewed：审查结论与 Outbox 记录 MUST 在同一事务内落库，发布成功前事件不得丢失；进程重启后未发布事件 MUST 可被继续投递。事件 payload MUST 保持既有 7 字段锁定，MUST NOT 回传 output 原文或任何 PII。

#### Scenario: 事务性发布不丢事件
- **WHEN** 审查结论提交后、事件投递前进程崩溃并重启
- **THEN** Outbox 中未投递事件仍可被发现并继续投递，事件不丢失且 payload 仍为 7 字段

#### Scenario: 发布与结论同事务
- **WHEN** 审查结论写入成功
- **THEN** 对应 Outbox 记录在同一事务内可见；结论写入失败时不得留下孤立 Outbox 记录

### Requirement: 幂等消费与跨租户隔离

系统 SHALL 以持久化 Inbox 消费 ContractReviewed，以 `review_id` 为幂等键：同一 review_id 的重复投递 MUST 只产生一条结论；事件 MUST 携带 tenant 上下文，跨租户错投 MUST 被拒绝并留下拒绝留痕，MUST NOT 用事件的 tenant 覆盖接收方租户或从默认租户继续处理。

#### Scenario: 重复投递只消费一次
- **WHEN** 同一 review_id 的 ContractReviewed 被重复投递多次
- **THEN** Inbox 仅记录一条结论，重复投递返回"已跳过"且不产生第二条追溯记录

#### Scenario: 跨租户事件拒绝且留痕
- **WHEN** 事件的 tenant 与消费方租户上下文不一致
- **THEN** 事件被拒绝、不产生结论，系统留下跨租户拒绝留痕（含 review_id 与拒绝原因）

#### Scenario: 重启后去重状态保持
- **WHEN** 已消费的 review_id 在进程重启后再次投递
- **THEN** 依据持久化 Inbox 判定为重复并跳过，不因内存清空而重复消费

### Requirement: 消费失败进入 DLQ 且可重放

系统 SHALL 在消费处理失败（含解析异常、下游不可用）时把事件投入持久化 DLQ 并记录失败原因；运维可依据 `review_id` 触发重放，重放成功后的结论 MUST 与首次成功消费一致，同一 review_id 绝不产生重复结论。

#### Scenario: 失败入 DLQ
- **WHEN** ContractReviewed 消费过程中抛出可重试异常
- **THEN** 事件进入 DLQ 并记录 review_id 与失败原因，不产生半条结论

#### Scenario: DLQ 按 review_id 重放
- **WHEN** 对 DLQ 中的事件按 review_id 触发重放且依赖恢复
- **THEN** 事件被重新消费并产出唯一结论，DLQ 项标记为已解决，重复重放不产生第二条结论

### Requirement: 事件闭环不改变人工终决与披露语义

升级为持久化闭环 MUST NOT 稀释既有语义：`risk_level=UNKNOWN` 仍隔离转人工、AI 参与披露三态不变、人工推翻的审查快照不可变、降级/停用/人工推翻仍入追溯链。

#### Scenario: UNKNOWN 在持久化链路仍隔离
- **WHEN** 经 Outbox/Inbox 消费的事件 risk_level = UNKNOWN
- **THEN** 该结论仍隔离转人工，不被当作低风险自动通过

#### Scenario: 人工推翻快照不可变
- **WHEN** 人工推翻一条经持久化闭环产生的结论
- **THEN** 原 review_snapshot_ref 指向的快照保持不变，推翻动作入追溯链
