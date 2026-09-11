package com.bone.masterdata.domain.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** {@link QualityReport} 纯单测：质检报告快照创建（无容器）。 */
class QualityReportTest {

  @Test
  void testCreateSnapshotsReport() {
    QualityReport report = QualityReport.create(1L, 100L, "{\"issues\":[]}", 0);

    assertEquals(100L, report.getQualityCheckId());
    assertEquals("{\"issues\":[]}", report.getReportData());
    assertEquals(0, report.getIssueCount());
    assertNotNull(report.getCreatedAt());
  }
}
