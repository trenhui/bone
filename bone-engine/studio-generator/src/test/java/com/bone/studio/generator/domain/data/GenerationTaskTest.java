package com.bone.studio.generator.domain.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link GenerationTask} 纯单测：PENDING→PROCESSING→SUCCESS/FAILED 生成任务状态机（无容器）。 */
class GenerationTaskTest {

  private GenerationTask createTask() {
    return GenerationTask.create(
        1L,
        1L,
        "T-1001",
        "订单项目",
        "com.bone.order",
        "order",
        11L,
        List.of("ic_order"),
        List.of(1L, 2L),
        "{}");
  }

  @Test
  void testCreateStartsAtPending() {
    GenerationTask task = createTask();

    assertEquals("PENDING", task.getStatus());
    assertEquals("T-1001", task.getTaskId());
    assertEquals(List.of("ic_order"), task.getTableNames());
    assertNull(task.getStartedAt());
  }

  @Test
  void testMarkProcessingTransitionsFromPending() {
    GenerationTask task = createTask();

    task.markProcessing();

    assertEquals("PROCESSING", task.getStatus());
    assertNotNull(task.getStartedAt());
  }

  @Test
  void testMarkCompletedPublishesArtifacts() {
    GenerationTask task = createTask();

    task.markCompleted(List.of(), "http://download/bone/order.zip");

    assertEquals("SUCCESS", task.getStatus());
    assertEquals("http://download/bone/order.zip", task.getZipUrl());
    assertNotNull(task.getCompletedAt());
  }

  @Test
  void testMarkFailedRecordsError() {
    GenerationTask task = createTask();

    task.markFailed("模板编译失败");

    assertEquals("FAILED", task.getStatus());
    assertEquals("模板编译失败", task.getErrorMessage());
    assertNotNull(task.getCompletedAt());
  }
}
