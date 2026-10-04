package com.bone.masterdata.domain.model.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.masterdata.domain.model.quality.event.QualityCheckCompletedEvent;
import java.time.LocalDateTime;
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

  // HC-008 补 updated_at 的核心约定：SDK 不自动填该列，实体不显式赋值就会得到恒为初始值的列。
  // 少了这个断言，漏赋值不会有任何测试失败，只会在库表里留下一个"从不变化"的时间戳。
  @Test
  void testCreateInitializesUpdatedAtAlongsideCreatedAt() {
    QualityCheck check = QualityCheck.create(1L, 100L);

    assertNotNull(check.getUpdatedAt());
    assertEquals(check.getCreatedAt(), check.getUpdatedAt());
  }

  @Test
  void testCompleteRefreshesUpdatedAtButNotCreatedAt() throws InterruptedException {
    QualityCheck check = QualityCheck.create(1L, 100L);
    LocalDateTime createdAt = check.getCreatedAt();
    LocalDateTime updatedAtBefore = check.getUpdatedAt();

    Thread.sleep(5);
    check.complete(10, 9, 1);

    assertEquals(createdAt, check.getCreatedAt(), "创建时刻不应被状态变更改写");
    assertTrue(
        check.getUpdatedAt().isAfter(updatedAtBefore), "complete() 必须刷新 updatedAt，否则任务状态变更时刻不可追溯");
  }
}
