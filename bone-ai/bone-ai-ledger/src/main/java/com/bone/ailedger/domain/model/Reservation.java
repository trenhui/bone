package com.bone.ailedger.domain.model;

import java.math.BigDecimal;

/**
 * 额度预留（两阶段占位）：先 {@code reserve} 持有额度，再 {@code commit} 实际占用或 {@code release} 返还。
 *
 * <p>两阶段是控额正确性的关键：直付 / 异步理算场景下「先占位、后确认」避免并发双花（两个请求都看到有余额就都扣）。
 *
 * @param id 预留标识（调用方生成，用于幂等重试与后续 commit/release）
 * @param scope 所属维度范围
 * @param amount 预留金额
 * @param state 当前状态
 */
public record Reservation(String id, LimitScope scope, BigDecimal amount, ReservationState state) {

  public boolean isReserved() {
    return state == ReservationState.RESERVED;
  }
}
