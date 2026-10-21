package com.bone.ailedger.domain.model;

/** 台账操作结论：编码原因 + 人读细节 + 受影响的预留（无则为 null）。 */
public record LimitVerdict(LimitVerdictReason reason, String detail, Reservation reservation) {

  /** 业务是否成功（区别于「额度不足 / 预留无效 / 参数非法」这几类失败结论）。 */
  public boolean ok() {
    return reason != LimitVerdictReason.LIMIT_EXCEEDED
        && reason != LimitVerdictReason.RESERVATION_NOT_FOUND
        && reason != LimitVerdictReason.AMOUNT_INVALID;
  }

  public static LimitVerdict of(LimitVerdictReason reason, String detail) {
    return new LimitVerdict(reason, detail, null);
  }

  public static LimitVerdict of(LimitVerdictReason reason, String detail, Reservation reservation) {
    return new LimitVerdict(reason, detail, reservation);
  }
}
