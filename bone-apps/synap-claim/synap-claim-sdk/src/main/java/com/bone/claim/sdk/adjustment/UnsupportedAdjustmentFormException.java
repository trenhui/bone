package com.bone.claim.sdk.adjustment;

/**
 * 责任形式尚无金额语义时的受控拒绝。
 *
 * <p>定额给付 / 津贴给付在 V1 条款语料里没有金额推导规则。此时猜一个公式会产出「看起来正常」的赔款， 比抛异常危险得多，故由内核显式拒绝，交由编排转人工。
 */
public final class UnsupportedAdjustmentFormException extends RuntimeException {

  private final AdjustmentForm form;

  public UnsupportedAdjustmentFormException(AdjustmentForm form) {
    super("责任形式 " + form + " 尚无金额推导规则，禁止按报销型公式计算（LIABILITY_FORM_UNSUPPORTED）");
    this.form = form;
  }

  public AdjustmentForm form() {
    return form;
  }
}
