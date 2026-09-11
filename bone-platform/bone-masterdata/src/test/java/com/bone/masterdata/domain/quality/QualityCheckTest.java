package com.bone.masterdata.domain.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bone.masterdata.domain.model.quality.event.QualityCheckCompletedEvent;
import org.junit.jupiter.api.Test;

/** {@link QualityCheck} 纯单测：RUNNING 起步、complete 汇总并发布完成事件（无容器）。 */
class QualityCheckTest {

  @Test
  void testCreateStartsAtRunning() {
    QualityCheck check = QualityCheck.create(1L, 100L);

    assertEquals("RUNNING", check.getStatus());
    assertEquals("quality-check", check.getCheckName());
    assertNotNull(check.getStartedAt());
  }

  @Test
  void testCompleteAggregatesCountsAndPublishesEvent() {
    QualityCheck check = QualityCheck.create(1L, 100L);

    check.complete(100, 96, 4);

    assertEquals("COMPLETED", check.getStatus());
    assertEquals(100, check.getTotalRecords());
    assertEquals(96, check.getPassedRecords());
    assertEquals(4, check.getFailedRecords());
    assertNotNull(check.getEndedAt());
    assertEquals(1, check.getDomainEvents().size());
    assertInstanceOf(QualityCheckCompletedEvent.class, check.getDomainEvents().get(0));
  }
}
