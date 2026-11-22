package com.bone.claim.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimReport;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimReportRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 受理落库端口实现单测。
 *
 * <p><b>能测什么、不能测什么</b>：真正的「同事务回滚」需要 Spring 事务基础设施，单测里测不到（由场景测试 + 联调覆盖）。
 * 这里锁两件在单测层就能证明、且最容易被人改坏的事：<b>写入顺序</b>（先报案后赔案）与<b>失败不吞</b>（赔案写失败时异常必须 上抛，否则外层会以为提交成功并返回一个查不到的赔案号）。
 */
@ExtendWith(MockitoExtension.class)
class ClaimIntakePortAdapterTest {

  private static final long REPORT_ID = 76102367333449728L;

  @Mock private ClaimReportRepository claimReportRepository;
  @Mock private ClaimCaseRepository claimCaseRepository;
  @InjectMocks private ClaimIntakePortAdapter adapter;

  private static ClaimReport report() {
    return ClaimReport.open(
        REPORT_ID,
        42L,
        "EG20260001",
        7L,
        "hash-abc",
        "OUTPATIENT",
        Instant.parse("2026-03-12T10:00:00Z"),
        "v3",
        "");
  }

  private static ClaimCase firstCase(long reportId) {
    return ClaimCase.draft(
        1001L,
        42L,
        reportId,
        "EG20260001",
        7L,
        "hash-abc",
        "OUTPATIENT",
        "04400001",
        "12345678",
        Instant.parse("2026-03-12T10:00:00Z"),
        "B20260312",
        "idem-intake-0",
        "v3",
        java.util.List.of(
            com.bone.claim.domain.model.claim.ClaimItem.of(
                2001L,
                1L,
                1001L,
                "诊查费",
                "",
                com.bone.claim.domain.model.claim.valueobject.ItemCategory.SERVICE,
                java.math.BigDecimal.ONE,
                java.math.BigDecimal.TEN,
                new java.math.BigDecimal("10.00"))));
  }

  @Test
  @DisplayName("先写报案再写赔案：赔案的 report_id 指向已存在的报案行")
  void writesReportBeforeCase() {
    ClaimReport report = report();
    ClaimCase claim = firstCase(report.getId());

    adapter.persistIntake(report, claim);

    InOrder order = inOrder(claimReportRepository, claimCaseRepository);
    order.verify(claimReportRepository).insert(report);
    order.verify(claimCaseRepository).insert(claim);
  }

  @Test
  @DisplayName("赔案写入失败时异常必须上抛，且不再重试报案")
  void propagatesCaseFailure() {
    ClaimReport report = report();
    ClaimCase claim = firstCase(report.getId());
    when(claimCaseRepository.insert(any(ClaimCase.class)))
        .thenThrow(new IllegalStateException("uk_tenant_idem 冲突"));

    assertThatThrownBy(() -> adapter.persistIntake(report, claim))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("uk_tenant_idem");

    // 报案确实先写了（回滚由事务负责，单测只证明顺序与不吞异常）
    verify(claimReportRepository).insert(report);
  }

  @Test
  @DisplayName("报案写入失败时不会去写赔案（避免孤儿赔案）")
  void doesNotWriteCaseWhenReportFails() {
    ClaimReport report = report();
    ClaimCase claim = firstCase(report.getId());
    when(claimReportRepository.insert(any(ClaimReport.class)))
        .thenThrow(new IllegalStateException("report_no 冲突"));

    assertThatThrownBy(() -> adapter.persistIntake(report, claim))
        .isInstanceOf(IllegalStateException.class);

    verify(claimCaseRepository, never()).insert(any(ClaimCase.class));
  }
}
