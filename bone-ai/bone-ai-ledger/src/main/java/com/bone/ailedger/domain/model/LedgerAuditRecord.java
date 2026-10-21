package com.bone.ailedger.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 额度变异审计记录（碰钱内核的留痕，合规 / 对账 / 纠纷溯源的地基）。
 *
 * <p>与 HITL 处置链（审「人的决策」：谁在何时做了什么裁决）互补：本记录审「钱的变动」——每一笔余额变动的机械真相。 两者叠加才构成完整闭环： 审计链说「这一维的可用额从 X 变成
 * Y、因为哪笔操作」，处置链说「这个案子是谁定的、凭什么」。
 *
 * <p>带 {@code before} / {@code after} 两份快照，使恒等式 {@code available = limit - consumed - reserved -
 * frozen} 可被逐笔复核（对账时不需重放全史）；{@code reason} 用机器编码，便于按「超限拒绝」等类型聚合监控。
 *
 * @param scope 变异所属维度范围
 * @param operation 操作名（declareLimit / reserve / commit / release / freeze / thaw）
 * @param reservationId 相关预留标识（无则 null）
 * @param amount 本次操作金额
 * @param reason 操作结论编码（含失败结论，如 LIMIT_EXCEEDED——「试图超限」同样是合规关注事件）
 * @param before 操作前快照
 * @param after 操作后快照
 * @param occurredAt 发生时刻
 */
public record LedgerAuditRecord(
    LimitScope scope,
    String operation,
    String reservationId,
    BigDecimal amount,
    LimitVerdictReason reason,
    LimitSnapshot before,
    LimitSnapshot after,
    Instant occurredAt) {}
