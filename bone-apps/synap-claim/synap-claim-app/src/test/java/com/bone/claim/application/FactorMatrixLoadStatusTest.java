package com.bone.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bone.claim.application.FactorMatrixApplicationService.MatrixView;
import com.bone.claim.application.port.out.TenantPort;
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

/**
 * 因子矩阵 load 状态的判别力测试（configuration-face 缺陷钉子）。
 *
 * <p><b>为什么单列</b>：此前 load 用 {@code FactorMatrix.draft()} 造壳，status 恒 DRAFT，{@code replaceCells()}
 * 只并格不同步状态——已发布版本 load 出来仍显示 DRAFT，用户会以为发布失败而重复发布。删掉「从真实行派生状态」的实现，本测试第一个用例即转红，是真变异杀。
 */
@ExtendWith(MockitoExtension.class)
class FactorMatrixLoadStatusTest {

  private static final long TENANT = 1L;
  private static final long LIABILITY = 10L;
  private static final String DIMENSION_SET = "HOSPITAL_LEVEL";
  private static final int VERSION = 1;

  @Mock FactorMatrixRepository repository;
  @Mock TenantPort tenantPort;
  @InjectMocks FactorMatrixApplicationService service;

  private FactorMatrix row(String rowKey, boolean published) {
    FactorMatrix m =
        FactorMatrix.of(
            (long) rowKey.hashCode(),
            TENANT,
            LIABILITY,
            "LB-UUID",
            DIMENSION_SET,
            rowKey,
            FactorMatrix.DEFAULT_COL_KEY,
            new BigDecimal("0.9000"),
            VERSION);
    if (published) {
      m.publish();
    }
    return m;
  }

  private void stubRows(List<FactorMatrix> rows) {
    when(tenantPort.currentTenantId()).thenReturn(TENANT);
    when(repository.findByLiabilityAndVersion(eq(TENANT), eq(LIABILITY), anyString(), eq(VERSION)))
        .thenReturn(rows);
  }

  @Test
  @DisplayName("全部行已发布 → load 显示 PUBLISHED（旧实现恒 DRAFT，此例即变异杀）")
  void publishedRows_loadShowsPublished() {
    stubRows(List.of(row("HOSPITAL_LEVEL=一级", true), row("HOSPITAL_LEVEL=二级", true)));

    MatrixView view = service.load(LIABILITY, DIMENSION_SET, VERSION);

    assertThat(view.status()).isEqualTo(FactorMatrix.STATUS_PUBLISHED);
    assertThat(view.cells()).hasSize(2);
  }

  @Test
  @DisplayName("任一行仍 DRAFT → load 显示 DRAFT（如实暴露未发布改动，不虚报生效）")
  void partiallyPublished_loadShowsDraft() {
    stubRows(List.of(row("HOSPITAL_LEVEL=一级", true), row("HOSPITAL_LEVEL=二级", false)));

    MatrixView view = service.load(LIABILITY, DIMENSION_SET, VERSION);

    assertThat(view.status()).isEqualTo(FactorMatrix.STATUS_DRAFT);
  }

  @Test
  @DisplayName("空矩阵 → load 显示 DRAFT（不凭空报 PUBLISHED）")
  void emptyMatrix_loadShowsDraft() {
    stubRows(List.of());

    MatrixView view = service.load(LIABILITY, DIMENSION_SET, VERSION);

    assertThat(view.status()).isEqualTo(FactorMatrix.STATUS_DRAFT);
    assertThat(view.cells()).isEmpty();
  }
}
