package com.bone.masterdata.domain.model.quality;

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

  // 计数列在 DB 上是 NOT NULL，而 SDK 走显式全列 INSERT（NULL 会盖掉 DEFAULT 0）。
  // 创建时未初始化 → "先落 RUNNING 再回填"的写法插入即 500，故此处钉死初始值为 0。
  @Test
  void testCreateInitializesCountersForNotNullInsert() {
    QualityCheck check = QualityCheck.create(1L, 100L);

    assertEquals(0, check.getTotalRecords());
    assertEquals(0, check.getPassedRecords());
    assertEquals(0, check.getFailedRecords());
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
