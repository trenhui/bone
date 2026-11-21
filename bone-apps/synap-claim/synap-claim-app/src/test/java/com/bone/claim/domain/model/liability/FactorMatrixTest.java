package com.bone.claim.domain.model.liability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.claim.domain.model.liability.FactorMatrix.Cell;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 因子矩阵两条判别力：结构变更保留已填值、发布后冻结。 */
class FactorMatrixTest {

  private static final long TENANT = 1L;
  private static final long LIABILITY = 10L;
  private static final String DIMENSION_SET = "HOSPITAL_LEVEL|MEDICAL_INSURANCE";

  private FactorMatrix matrix() {
    return FactorMatrix.of(
        1L,
        TENANT,
        LIABILITY,
        "LB-UUID-001",
        DIMENSION_SET,
        "HOSPITAL_LEVEL=一级",
        "DEFAULT",
        new BigDecimal("0.9000"),
        1);
  }

  @Test
  void replaceCells_structureChange_preservesExistingFilledValue() {
    FactorMatrix matrix = matrix();
    matrix.replaceCells(
        List.of(
            Cell.of("HOSPITAL_LEVEL=一级", "DEFAULT", new BigDecimal("0.9000")),
            Cell.of("HOSPITAL_LEVEL=二级", "DEFAULT", new BigDecimal("0.8000"))));

    // 新增行后，旧 rowKey 及其已填值仍在（不是被结构变更清空）
    assertThat(matrix.cells())
        .extracting(Cell::rowKey)
        .contains("HOSPITAL_LEVEL=一级", "HOSPITAL_LEVEL=二级");
    assertThat(matrix.cells())
        .filteredOn(c -> "HOSPITAL_LEVEL=一级".equals(c.rowKey()))
        .extracting(Cell::value)
        .singleElement()
        .isEqualTo(new BigDecimal("0.9000"));

    // 再次结构变更：即便传入旧键的新值，已填值也不被覆盖；新键正常带入
    matrix.replaceCells(
        List.of(
            Cell.of("HOSPITAL_LEVEL=一级", "DEFAULT", new BigDecimal("0.1000")),
            Cell.of("HOSPITAL_LEVEL=三级", "DEFAULT", new BigDecimal("0.7000"))));

    assertThat(matrix.cells())
        .filteredOn(c -> "HOSPITAL_LEVEL=一级".equals(c.rowKey()))
        .extracting(Cell::value)
        .singleElement()
        .isEqualTo(new BigDecimal("0.9000"));
    assertThat(matrix.cells())
        .filteredOn(c -> "HOSPITAL_LEVEL=三级".equals(c.rowKey()))
        .extracting(Cell::value)
        .singleElement()
        .isEqualTo(new BigDecimal("0.7000"));
  }

  @Test
  void publish_thenReplaceCellsIsRejected() {
    FactorMatrix matrix = matrix();
    matrix.replaceCells(List.of(Cell.of("HOSPITAL_LEVEL=二级", "DEFAULT", new BigDecimal("0.8"))));

    matrix.publish();

    assertThat(matrix.isPublished()).isTrue();
    assertThat(matrix.getStatus()).isEqualTo(FactorMatrix.STATUS_PUBLISHED);
    assertThatThrownBy(
            () ->
                matrix.replaceCells(
                    List.of(Cell.of("HOSPITAL_LEVEL=三级", "DEFAULT", new BigDecimal("0.7")))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("已发布");
    assertThatThrownBy(() -> matrix.updateCellValue(new BigDecimal("0.1")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("已发布");
  }
}
