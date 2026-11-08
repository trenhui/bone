package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.IndemnityApplicationService.AdjustmentPreview;
import com.bone.claim.application.IndemnityApplicationService.AdjustmentResult;
import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 理算运维编排单测（§10.2：存量重算 / 规则回归的批量语义）。
 *
 * <p>重点钉两条：①逐案隔离——一案失败（含已 CONFIRMED 被拒）只进报告、不中断整批； ②dryRun 只试算不落库——「未回写已支付案件」是 Go/No-Go 硬判据。
 */
class AdjustmentOpsApplicationServiceTest {

  private final IndemnityApplicationService indemnity = mock(IndemnityApplicationService.class);
  private final AdjustmentOpsApplicationService ops =
      new AdjustmentOpsApplicationService(indemnity);

  private static AdjustmentPreview preview(long claimId, String oldTotal, String newTotal) {
    return new AdjustmentPreview(
        claimId,
        "r1",
        9001L,
        oldTotal == null ? null : new BigDecimal(oldTotal),
        "hash-old",
        new BigDecimal(newTotal),
        "hash-new",
        oldTotal == null || !new BigDecimal(oldTotal).equals(new BigDecimal(newTotal)),
        true);
  }

  @Test
  @DisplayName("dryRun：逐案试算出差异报告，绝不触发真重算（未回写已付案件）")
  void dryRunNeverRecalculates() {
    when(indemnity.preview(eq(1L), anyString())).thenReturn(preview(1L, "100.00", "120.00"));
    when(indemnity.preview(eq(2L), anyString())).thenThrow(new DomainException("理算明细不能为空"));

    List<AdjustmentOpsApplicationService.RecomputeItem> report =
        ops.recompute(
            new AdjustmentOpsApplicationService.RecomputeReq(List.of(1L, 2L), "r2", true));

    verify(indemnity, never()).recalculate(anyLong(), anyString());
    assertThat(report).hasSize(2);
    assertThat(report.get(0).outcome()).isEqualTo("PREVIEWED");
    assertThat(report.get(0).totalChanged()).isTrue();
    assertThat(report.get(0).newTotalAmount()).isEqualByComparingTo("120.00");
    assertThat(report.get(1).outcome()).isEqualTo("FAILED");
    assertThat(report.get(1).message()).contains("理算明细不能为空");
  }

  @Test
  @DisplayName("真重算：已 CONFIRMED 案件被聚合拒绝只记 REJECTED，后续案件照常执行")
  void rejectedClaimDoesNotAbortBatch() {
    when(indemnity.preview(eq(1L), anyString())).thenReturn(preview(1L, "100.00", "100.00"));
    when(indemnity.preview(eq(2L), anyString())).thenReturn(preview(2L, "50.00", "60.00"));
    when(indemnity.recalculate(eq(1L), anyString())).thenThrow(new DomainException("已确认的理算结果禁止重算"));
    when(indemnity.recalculate(eq(2L), anyString()))
        .thenReturn(
            new AdjustmentResult(
                9002L,
                new BigDecimal("60.00"),
                "hash-new",
                "{}",
                List.of(),
                "L-1",
                null,
                BigDecimal.ZERO,
                true));

    List<AdjustmentOpsApplicationService.RecomputeItem> report =
        ops.recompute(
            new AdjustmentOpsApplicationService.RecomputeReq(List.of(1L, 2L), "r2", false));

    assertThat(report).hasSize(2);
    assertThat(report.get(0).outcome()).isEqualTo("REJECTED");
    assertThat(report.get(0).message()).contains("已确认");
    assertThat(report.get(1).outcome()).isEqualTo("RECALCULATED");
    assertThat(report.get(1).newTotalAmount()).isEqualByComparingTo("60.00");
  }

  @Test
  @DisplayName("批量边界：空清单与超上限一律拒绝（内部入口也要设防）")
  void batchBounds() {
    assertThatThrownBy(
            () ->
                ops.recompute(
                    new AdjustmentOpsApplicationService.RecomputeReq(List.of(), "r1", true)))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("claimIds 不能为空");
    assertThatThrownBy(
            () ->
                ops.recompute(
                    new AdjustmentOpsApplicationService.RecomputeReq(
                        java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList(),
                        "r1",
                        true)))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("100");
  }

  @Test
  @DisplayName("回归：金额或哈希漂移即失败，汇总 passed/failed 与逐案明细一致")
  void regressionCountsPassAndFail() {
    when(indemnity.preview(eq(1L), anyString())).thenReturn(preview(1L, "100.00", "100.00"));
    when(indemnity.preview(eq(2L), anyString())).thenReturn(preview(2L, "50.00", "80.00"));

    AdjustmentOpsApplicationService.RegressionReport report =
        ops.regression(
            new AdjustmentOpsApplicationService.RegressionReq(
                "r2",
                List.of(
                    new AdjustmentOpsApplicationService.BaselineCase(
                        1L, new BigDecimal("100.00"), null),
                    new AdjustmentOpsApplicationService.BaselineCase(
                        2L, new BigDecimal("50.00"), null))));

    assertThat(report.totalCases()).isEqualTo(2);
    assertThat(report.passed()).isEqualTo(1);
    assertThat(report.failed()).isEqualTo(1);
    assertThat(report.failures()).hasSize(1);
    assertThat(report.failures().get(0).claimId()).isEqualTo(2L);
    assertThat(report.failures().get(0).actualTotalAmount()).isEqualByComparingTo("80.00");
  }

  @Test
  @DisplayName("回归：基准集为空直接拒绝（空集恒绿是无意义的发布门禁）")
  void regressionRequiresBaseline() {
    assertThatThrownBy(
            () ->
                ops.regression(new AdjustmentOpsApplicationService.RegressionReq("r1", List.of())))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("基准集不能为空");
  }
}
