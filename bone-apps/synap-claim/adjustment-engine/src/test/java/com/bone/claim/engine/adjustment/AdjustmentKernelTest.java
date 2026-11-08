package com.bone.claim.engine.adjustment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.claim.sdk.adjustment.AdjustmentCommand;
import com.bone.claim.sdk.adjustment.AdjustmentForm;
import com.bone.claim.sdk.adjustment.AdjustmentReceipt;
import com.bone.claim.sdk.adjustment.UnsupportedAdjustmentFormException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 纯函数内核的链序、精度、零赔付归因与三类分派钉子。 */
class AdjustmentKernelTest {

  private static AdjustmentCommand command(
      AdjustmentForm form, String base, String deduct, String percent, String limit) {
    return new AdjustmentCommand(
        form,
        new BigDecimal(base),
        new BigDecimal(deduct),
        new BigDecimal(percent),
        new BigDecimal(limit));
  }

  @Test
  void deductibleAppliesBeforeRatio() {
    AdjustmentReceipt receipt =
        AdjustmentKernelRegistry.require(AdjustmentForm.REIMBURSEMENT)
            .calculate(command(AdjustmentForm.REIMBURSEMENT, "20000", "10000", "80", "0"));
    assertEquals(new BigDecimal("8000.00"), receipt.totalAmount());
    assertTrue(receipt.steps().get(1).startsWith("DEDUCT:10000"));
  }

  @Test
  void limitCapsAfterRatio() {
    AdjustmentReceipt receipt =
        new ReimbursementKernel()
            .calculate(command(AdjustmentForm.REIMBURSEMENT, "200", "0", "90", "100"));
    assertEquals(new BigDecimal("100.00"), receipt.totalAmount());
  }

  @Test
  void deductibleOverBaseIsClampedToZero() {
    AdjustmentReceipt receipt =
        new ReimbursementKernel()
            .calculate(command(AdjustmentForm.REIMBURSEMENT, "30", "300", "90", "0"));
    assertEquals(new BigDecimal("0.00"), receipt.totalAmount());
    assertEquals("ZERO_DEDUCT", receipt.zeroReason());
  }

  @Test
  void zeroRatioAndZeroBaseAreAttributedSeparately() {
    assertEquals(
        "ZERO_PERCENT",
        new ReimbursementKernel()
            .calculate(command(AdjustmentForm.REIMBURSEMENT, "100", "0", "0", "0"))
            .zeroReason());
    assertEquals(
        "ZERO_REASONABLE",
        new ReimbursementKernel()
            .calculate(command(AdjustmentForm.REIMBURSEMENT, "0", "0", "80", "0"))
            .zeroReason());
  }

  @Test
  void calculationIsDeterministic() {
    AdjustmentCommand c = command(AdjustmentForm.REIMBURSEMENT, "1234.56", "100", "87.5", "5000");
    AdjustmentReceipt first = new ReimbursementKernel().calculate(c);
    AdjustmentReceipt second = new ReimbursementKernel().calculate(c);
    assertEquals(first, second);
  }

  @Test
  void threeFormsAreDispatchedAndUnsupportedFormsFailClosed() {
    assertEquals(3, AdjustmentKernelRegistry.registeredForms().size());
    for (AdjustmentForm form : AdjustmentForm.values()) {
      assertEquals(form, AdjustmentKernelRegistry.require(form).form());
    }
    for (AdjustmentForm form :
        new AdjustmentForm[] {AdjustmentForm.FIXED_AMOUNT, AdjustmentForm.ALLOWANCE}) {
      UnsupportedAdjustmentFormException ex =
          assertThrows(
              UnsupportedAdjustmentFormException.class,
              () ->
                  AdjustmentKernelRegistry.require(form)
                      .calculate(command(form, "1000", "0", "80", "0")));
      assertEquals(form, ex.form());
    }
  }

  @Test
  void supportMatrixOnlyIncludesReimbursement() {
    assertTrue(AdjustmentKernelRegistry.supports(AdjustmentForm.REIMBURSEMENT));
    assertFalse(AdjustmentKernelRegistry.supports(AdjustmentForm.FIXED_AMOUNT));
    assertFalse(AdjustmentKernelRegistry.supports(AdjustmentForm.ALLOWANCE));
    assertThrows(IllegalArgumentException.class, () -> AdjustmentKernelRegistry.require(null));
  }

  @Test
  void wrongFormIsRejectedByKernel() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ReimbursementKernel()
                .calculate(command(AdjustmentForm.ALLOWANCE, "100", "0", "80", "0")));
  }
}
