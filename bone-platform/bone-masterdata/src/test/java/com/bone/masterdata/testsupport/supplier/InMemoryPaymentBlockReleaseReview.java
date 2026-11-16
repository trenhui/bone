package com.bone.masterdata.testsupport.supplier;

import com.bone.masterdata.domain.gateway.PaymentBlockReleaseReviewPort;
import java.time.Instant;

/**
 * 付款冻结解除「人工复核留痕」的联调夹具。
 *
 * <p><b>默认未留痕</b>（fail-closed）：没有显式 {@link #trace()} 就不能解除冻结。若默认为已留痕， spec:83
 * 的「只接受外部财务事件<b>加人工复核留痕</b>」会在所有用例里退化成只校验前一半。
 *
 * <p><b>留痕带时间戳</b>（独立审查 I-4）：只有「不早于本次冻结」的复核才放行，历史留痕不能永久放行后续每一次解除。 负向用例可先留痕再冻结，验证旧留痕不覆盖新冻结。
 */
public final class InMemoryPaymentBlockReleaseReview implements PaymentBlockReleaseReviewPort {

  private Instant tracedAt;

  @Override
  public boolean releaseReviewTraced(long tenantId, String partyId, Instant blockedAt) {
    if (tracedAt == null || blockedAt == null) {
      return false;
    }
    return !tracedAt.isBefore(blockedAt);
  }

  /** 以当前时间留痕。 */
  public void trace() {
    this.tracedAt = Instant.now();
  }

  /** 以指定时间留痕，供「留痕早于本次冻结」的负向用例使用。 */
  public void traceAt(Instant at) {
    this.tracedAt = at;
  }

  public void reset() {
    this.tracedAt = null;
  }
}
