package com.bone.claim.sdk.adjustment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** SDK 纯 Java 契约的装载与非法值拒绝钉子。 */
class AdjustmentContractTest {

  @Test
  void nullOrBlankFallsBackToReimbursement() {
    assertEquals(AdjustmentForm.REIMBURSEMENT, AdjustmentForm.of(null));
    assertEquals(AdjustmentForm.REIMBURSEMENT, AdjustmentForm.of("   "));
  }

  @Test
  void formStringIsNormalized() {
    assertEquals(AdjustmentForm.FIXED_AMOUNT, AdjustmentForm.of(" fixed_amount "));
    assertEquals(AdjustmentForm.ALLOWANCE, AdjustmentForm.of("allowance"));
  }

  @Test
  void unknownFormIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> AdjustmentForm.of("FIXED"));
  }

  @Test
  void commandRejectsNegativeAndMissingForm() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AdjustmentCommand(
                AdjustmentForm.REIMBURSEMENT,
                new BigDecimal("-1"),
                BigDecimal.ZERO,
                BigDecimal.TEN,
                BigDecimal.ZERO));
    assertThrows(
        NullPointerException.class,
        () ->
            new AdjustmentCommand(
                null, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ZERO));
  }

  @Test
  void commandNormalizesNullAmountsToZero() {
    AdjustmentCommand command =
        new AdjustmentCommand(AdjustmentForm.REIMBURSEMENT, null, null, null, null);
    assertEquals(BigDecimal.ZERO, command.baseAmount());
    assertEquals(BigDecimal.ZERO, command.deductible());
    assertEquals(BigDecimal.ZERO, command.limit());
  }

  @Test
  void receiptCopiesStepsDefensively() {
    java.util.List<String> steps = new java.util.ArrayList<>(java.util.List.of("BASE:1"));
    AdjustmentReceipt receipt = new AdjustmentReceipt(BigDecimal.ONE, "", steps);
    steps.add("MUTATED");
    assertEquals(1, receipt.steps().size());
  }
}
