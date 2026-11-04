package com.bone.claim.application.port.out;

import java.math.BigDecimal;

/**
 * 发票验真 / 验重端口（出站）。
 *
 * <p><b>一期边界（合规从严，R-FRAUD-06）</b>：本端口只做「票面要素确定性核验 + 跨案发票去重」， <b>不是税局 /
 * 电子票据平台验真</b>。一期无外部查验通道，因此即便本地核验全部通过，也<b>不得</b>返回 {@code PASS}——必须返回 {@code
 * UNVERIFIABLE}，由快赔规约转人工。只有查实了确定性问题才返回 {@code FAIL} 用于前置拦截（把「建单时才 409」提前到材料阶段）。二期接税局通道时只替换适配器实现，
 * 端口与调用方零改动，届时才有资格判 {@code PASS}。
 */
public interface InvoiceVerificationPort {

  VerificationResult verify(InvoiceFacts facts);

  /** 验真输入：来自 {@link DocumentParsePort} 的结构化字段，不接受调用方自由文本。 */
  record InvoiceFacts(
      long claimId,
      Long tenantId,
      String invoiceCode,
      String invoiceNo,
      BigDecimal invoiceAmount,
      BigDecimal itemTotal,
      String invoiceDate) {}

  /**
   * @param available 本次是否真实执行了核验（装配缺失/异常时为 false，消费方须转人工）
   * @param verifyValid 落 {@code t_claim_evidence.verify_valid} 的受控值：PASS / FAIL / UNVERIFIABLE
   * @param reason 受控原因码（禁自由文本），供审计归因与前端提示
   * @param channel 核验通道标识，明确「本地票面核验」与「税局查验」不是一回事
   */
  record VerificationResult(boolean available, String verifyValid, String reason, String channel) {

    public static VerificationResult fail(String reason, String channel) {
      return new VerificationResult(true, "FAIL", reason, channel);
    }

    public static VerificationResult unverifiable(String reason, String channel) {
      return new VerificationResult(true, "UNVERIFIABLE", reason, channel);
    }

    public static VerificationResult unavailable(String reason) {
      return new VerificationResult(false, "UNKNOWN", reason, "NONE");
    }
  }
}
