package com.bone.ailedger.domain.model;

/** 预留生命周期状态。 */
public enum ReservationState {
  /** 已预留：额度被持有但未实际占用（可 commit 或 release）。 */
  RESERVED,
  /** 已提交：额度已实际占用（终态，不可再变）。 */
  CONSUMED,
  /** 已释放：预留作废，额度已返还（终态，不可再变）。 */
  RELEASED
}
