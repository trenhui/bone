package com.bone.procurement.transaction.domain.model.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** TDD RED：快照哈希的可复现性与「逐行结构参与哈希」——改任一输入哈希必变。 */
class PriceSnapshotTest {

  private PriceSnapshot.Input input() {
    return new PriceSnapshot.Input(
        "PO-2026-000001",
        3,
        List.of(
            new PriceSnapshot.LineInput(
                "ITEM-1",
                new BigDecimal("2.000000"),
                new BigDecimal("10.0000"),
                new BigDecimal("20.0000")),
            new PriceSnapshot.LineInput(
                "ITEM-2",
                new BigDecimal("1.000000"),
                new BigDecimal("5.5000"),
                new BigDecimal("5.5000"))),
        new BigDecimal("2.0000"),
        "CNY",
        new BigDecimal("7.10000000"),
        "2026-11-17T08:00:00Z",
        "HALF_UP",
        "TAX-V2");
  }

  @Test
  void 同输入两次哈希一致() {
    assertThat(PriceSnapshot.computeHash(input())).isEqualTo(PriceSnapshot.computeHash(input()));
  }

  @Test
  void 哈希为64位十六进制() {
    assertThat(PriceSnapshot.computeHash(input())).matches("[0-9a-f]{64}");
  }

  @Test
  void 改行净价哈希必变() {
    PriceSnapshot.Input changed =
        new PriceSnapshot.Input(
            input().poNo(),
            input().poVersion(),
            List.of(
                new PriceSnapshot.LineInput(
                    "ITEM-1",
                    new BigDecimal("2.000000"),
                    new BigDecimal("10.0001"),
                    new BigDecimal("20.0000")),
                input().lines().get(1)),
            input().taxAmount(),
            input().currency(),
            input().fxRate(),
            input().fxAsOf(),
            input().roundingMode(),
            input().taxCodeVersion());
    assertThat(PriceSnapshot.computeHash(changed)).isNotEqualTo(PriceSnapshot.computeHash(input()));
  }

  @Test
  void 改税码版本哈希必变() {
    PriceSnapshot.Input changed =
        new PriceSnapshot.Input(
            input().poNo(),
            input().poVersion(),
            input().lines(),
            input().taxAmount(),
            input().currency(),
            input().fxRate(),
            input().fxAsOf(),
            input().roundingMode(),
            "TAX-V3");
    assertThat(PriceSnapshot.computeHash(changed)).isNotEqualTo(PriceSnapshot.computeHash(input()));
  }
}
