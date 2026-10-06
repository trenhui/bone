package com.bone.blueprint.infrastructure.messaging.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 广播任务状态机纯单测。
 *
 * <p>覆盖两个「只有真实 MySQL 才会暴露」的坑：
 *
 * <ol>
 *   <li><b>时间字段必须非空</b>：SDK 的 insert 会把实体字段显式写入，{@code created_at/updated_at/next_retry_at} 任一为
 *       null 都会撞 NOT NULL 约束（DB 的 {@code ON UPDATE CURRENT_TIMESTAMP} 只在 UPDATE 时生效，管不到 INSERT）；
 *   <li><b>重试计数与退避</b>：耗尽转死信、否则回 PENDING 并推迟 {@code nextRetryAt}。
 * </ol>
 */
class ChannelBroadcastTaskTest {

  private static ChannelBroadcastTask newTask(int maxRetry) {
    return ChannelBroadcastTask.pending(1L, 0L, 900001L, "TAOBAO", "TB900001", "商品", 480, maxRetry);
  }

  @Test
  void pendingFillsAllTimestampColumns() {
    ChannelBroadcastTask task = newTask(5);

    assertNotNull(task.getCreatedAt(), "created_at 为 null 会在 insert 时撞 NOT NULL");
    assertNotNull(task.getUpdatedAt(), "updated_at 为 null 会在 insert 时撞 NOT NULL（卡死自愈也依赖它）");
    assertNotNull(task.getNextRetryAt(), "nextRetryAt 为 null 会让退避过滤失效或撞 NOT NULL");
    assertEquals(BroadcastTaskStatus.PENDING, task.getStatus());
    assertEquals(0, task.getRetryCount());
    assertEquals(5, task.getMaxRetry());
    assertEquals(480, task.getTargetStock());
  }

  @Test
  void failedTaskRetriesUntilMaxThenDies() {
    ChannelBroadcastTask task = newTask(2);
    Instant later = Instant.now().plusSeconds(60);

    task.markFailed("限流", later);
    assertEquals(BroadcastTaskStatus.PENDING, task.getStatus(), "未耗尽重试次数应回 PENDING 等待退避后重投");
    assertEquals(1, task.getRetryCount());
    assertEquals(later, task.getNextRetryAt());

    task.markFailed("限流", later);
    assertEquals(BroadcastTaskStatus.FAILED, task.getStatus(), "耗尽重试次数应转死信等待人工介入");
    assertEquals(2, task.getRetryCount());
  }

  @Test
  void failedWithoutNextRetryDiesImmediately() {
    ChannelBroadcastTask task = newTask(5);

    // 渠道商品ID为空这类「再试也不会成功」的场景：直接死信，不浪费 5 轮退避
    task.markFailed("渠道商品ID为空（尚未上架成功）", null);

    assertEquals(BroadcastTaskStatus.FAILED, task.getStatus());
    assertTrue(task.getLastError().contains("渠道商品ID"), "死信必须写明原因，否则运营无从处理");
  }

  @Test
  void longErrorIsTruncatedToColumnWidth() {
    ChannelBroadcastTask task = newTask(5);

    task.markFailed("x".repeat(900), null);

    assertNotNull(task.getLastError());
    assertEquals(512, task.getLastError().length(), "超长渠道报文会把行撑爆，必须截断到列宽");
  }

  @Test
  void sentClearsErrorAndManualRetryResetsCounters() {
    ChannelBroadcastTask task = newTask(3);
    task.markFailed("限流", Instant.now());
    task.markSent();
    assertEquals(BroadcastTaskStatus.SENT, task.getStatus());

    task.markRetriedManually();

    assertEquals(BroadcastTaskStatus.PENDING, task.getStatus());
    assertEquals(0, task.getRetryCount(), "人工重试要清零计数，否则一次重试就立刻再判死信");
  }

  @Test
  void mergedTaskIsSentAndPointsToNewerTask() {
    ChannelBroadcastTask older = newTask(3);
    ChannelBroadcastTask newer = newTask(3);

    older.markMergedInto(newer.getId());

    assertEquals(BroadcastTaskStatus.SENT, older.getStatus(), "被合并的旧任务不能留在队列里反复投递");
    assertEquals(newer.getId(), older.getMergedIntoId(), "必须记下被合并到哪条，便于排查「为什么没投」");
  }
}
