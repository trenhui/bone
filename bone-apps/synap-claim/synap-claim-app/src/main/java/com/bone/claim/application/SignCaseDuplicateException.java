package com.bone.claim.application;

/** 并发建案撞幂等键：携带胜出案件，供订阅方在事务外回写明细，不留下孤儿报案。 */
public class SignCaseDuplicateException extends RuntimeException {

  private final long winnerClaimId;
  private final String winnerClaimNo;

  public SignCaseDuplicateException(long winnerClaimId, String winnerClaimNo) {
    super("并发幂等命中已有案件: claimId=" + winnerClaimId);
    this.winnerClaimId = winnerClaimId;
    this.winnerClaimNo = winnerClaimNo;
  }

  public long getWinnerClaimId() {
    return winnerClaimId;
  }

  public String getWinnerClaimNo() {
    return winnerClaimNo;
  }
}
