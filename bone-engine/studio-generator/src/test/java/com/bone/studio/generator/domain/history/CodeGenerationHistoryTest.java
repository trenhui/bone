package com.bone.studio.generator.domain.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link CodeGenerationHistory} 纯单测：生成历史的 PENDING→SUCCESS/FAILED 终局记录（无容器）。 */
class CodeGenerationHistoryTest {

  private CodeGenerationHistory createHistory() {
    return CodeGenerationHistory.create(
        "T-1001",
        "TPL-1",
        "实体模板",
        "订单生成",
        "ds-order",
        List.of("ic_order"),
        "com.bone.order",
        "order");
  }

  @Test
  void testCreateStartsAtPending() {
    CodeGenerationHistory history = createHistory();

    assertEquals("PENDING", history.getStatus());
    assertEquals("T-1001", history.getTaskId());
    assertEquals(List.of("ic_order"), history.getTableNames());
    assertNotNull(history.getStartedAt());
    assertNull(history.getCompletedAt());
  }

  @Test
  void testCompleteRecordsArtifactSummary() {
    CodeGenerationHistory history = createHistory();

    history.complete(12, 340L, "/out/bone/order");

    assertEquals("SUCCESS", history.getStatus());
    assertEquals(12, history.getFileCount());
    assertEquals(340L, history.getExecutionTime());
    assertEquals("/out/bone/order", history.getOutputPath());
    assertNotNull(history.getCompletedAt());
  }

  @Test
  void testFailRecordsErrorMessage() {
    CodeGenerationHistory history = createHistory();

    history.fail("数据源连接失败");

    assertEquals("FAILED", history.getStatus());
    assertEquals("数据源连接失败", history.getErrorMessage());
    assertNotNull(history.getCompletedAt());
  }
}
