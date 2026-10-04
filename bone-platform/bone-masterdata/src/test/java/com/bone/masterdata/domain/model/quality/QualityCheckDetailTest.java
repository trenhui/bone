package com.bone.masterdata.domain.model.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link QualityCheckDetail} 纯单测：通过/未通过两种形态与必填列赋值（无容器）。 */
class QualityCheckDetailTest {

  @Test
  void testPassCarriesAllColumns() {
    QualityCheckDetail detail = QualityCheckDetail.pass(1L, 10L, 20L, 30L, "非空校验 → 通过");

    assertEquals(1L, detail.getId());
    assertEquals(10L, detail.getQualityCheckId());
    assertEquals(20L, detail.getRuleId());
    assertEquals(30L, detail.getRecordId());
    assertTrue(detail.getPassed());
    assertEquals("非空校验 → 通过", detail.getMessage());
    assertNotNull(detail.getCheckedAt());
  }

  @Test
  void testFailMarksNotPassed() {
    QualityCheckDetail detail = QualityCheckDetail.fail(1L, 10L, 20L, 30L, "非空校验 → 字段[code]为必填项");

    // passed 列是 TINYINT(1) NOT NULL，SDK 走显式全列 INSERT，null 会插入失败。
    assertEquals(Boolean.FALSE, detail.getPassed());
    assertTrue(detail.getMessage().contains("必填"));
  }
}
