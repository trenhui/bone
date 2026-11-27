package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.adjustment.engine.FeePools;
import com.bone.claim.domain.model.adjustment.engine.PayRatioTable;
import com.bone.claim.domain.model.liability.AdjLiability;
import com.bone.claim.domain.model.liability.FactorMatrix;
import com.bone.claim.domain.repository.FactorMatrixRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 当前生效赔付比例表的判别力：无发布回落 / 取最高已发布版本 / 装载成多维表。 */
@ExtendWith(MockitoExtension.class)
class FactorMatrixActiveRatiosTest {

  private static final long TENANT = 1L;
  private static final long LIABILITY_ID = 10L;
  private static final BigDecimal PAY_PERCENT = new BigDecimal("80");

  @Mock FactorMatrixRepository repository;
  @Mock TenantPort tenantPort;
  @InjectMocks FactorMatrixApplicationService service;

  private AdjLiability liability() {
    return AdjLiability.of(
        LIABILITY_ID,
        TENANT,
        "LB-UUID",
        "v1",
        7L,
        "R01",
        "REIMBURSEMENT",
        PAY_PERCENT,
        new BigDecimal("50"),
        new BigDecimal("1000"),
        0,
        0,
        "ACTIVE");
  }

  private FactorMatrix publishedRow(int version, String rowKey, String value) {
    FactorMatrix m =
        FactorMatrix.of(
            (long) rowKey.hashCode(),
            TENANT,
            LIABILITY_ID,
            "LB-UUID",
            "HOSPITAL_LEVEL",
            rowKey,
            FactorMatrix.DEFAULT_COL_KEY,
            new BigDecimal(value),
            version);
    m.publish();
    return m;
  }

  @Test
  @DisplayName("无已发布矩阵 → uniform(payPercent)，且不查版本明细（零行为变更）")
  void noPublished_fallsBackToUniform() {
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    when(repository.findPublishedByLiability(TENANT, LIABILITY_ID)).thenReturn(List.of());

    PayRatioTable table = service.activeRatiosFor(liability());

    assertThat(table.form()).isEqualTo(PayRatioTable.FORM_UNIFORM);
    assertThat(
            table
                .lookup(PayRatioTable.RatioQuery.of(FeePools.Bucket.REASONABLE, BigDecimal.ZERO))
                .percent())
        .isEqualByComparingTo(PAY_PERCENT);
    verify(repository, never()).findByLiabilityAndVersion(any(), any(), any(), anyInt());
  }

  @Test
  @DisplayName("有已发布矩阵 → 取最高 version 的行装载成多维表（不是旧版本）")
  void publishedPicksHighestVersion() {
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    when(repository.findPublishedByLiability(TENANT, LIABILITY_ID))
        .thenReturn(
            List.of(
                publishedRow(1, "HOSPITAL_LEVEL=一级", "90"),
                publishedRow(2, "HOSPITAL_LEVEL=一级", "95"),
                publishedRow(2, "HOSPITAL_LEVEL=二级", "75")));
    when(repository.findByLiabilityAndVersion(eq(TENANT), eq(LIABILITY_ID), isNull(), eq(2)))
        .thenReturn(
            List.of(
                publishedRow(2, "HOSPITAL_LEVEL=一级", "95"),
                publishedRow(2, "HOSPITAL_LEVEL=二级", "75")));

    PayRatioTable table = service.activeRatiosFor(liability());

    assertThat(table.form()).isEqualTo(PayRatioTable.FORM_MULTI);
    PayRatioTable.RatioQuery q =
        new PayRatioTable.RatioQuery(null, null, BigDecimal.ZERO, BigDecimal.ZERO, "一级", null);
    assertThat(table.lookup(q).percent()).isEqualByComparingTo(new BigDecimal("95"));
    verify(repository).findByLiabilityAndVersion(eq(TENANT), eq(LIABILITY_ID), isNull(), eq(2));
  }

  @Test
  @DisplayName("liability 为 null → 安全回落 uniform（防御性，不 NPE）")
  void nullLiability_fallsBackToUniform() {
    PayRatioTable table = service.activeRatiosFor(null);
    assertThat(table.form()).isEqualTo(PayRatioTable.FORM_UNIFORM);
  }
}
