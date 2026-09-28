package com.bone.system.domain.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bone.system.domain.model.config.ConfigHistory;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** {@link ConfigHistory} 纯单测：工厂产出与字段约束（无容器，R8：聚合须同名纯单测）。 */
class ConfigHistoryTest {

  @Test
  void ofCreatesCreateRecordWithoutOldValue() {
    ConfigHistory history =
        ConfigHistory.of(1L, "site.title", null, "Bone Platform", "CREATE", "admin");

    assertEquals(1L, history.getConfigId());
    assertEquals("site.title", history.getConfigKey());
    assertNull(history.getOldValue());
    assertEquals("Bone Platform", history.getNewValue());
    assertEquals("CREATE", history.getChangeType());
    assertEquals("admin", history.getOperator());
  }

  @Test
  void ofCreatesUpdateRecordWithOldAndNewValue() {
    ConfigHistory history =
        ConfigHistory.of(1L, "site.title", "Bone", "new-value", "UPDATE", "admin");

    assertEquals("UPDATE", history.getChangeType());
    assertEquals("Bone", history.getOldValue());
    assertEquals("new-value", history.getNewValue());
  }

  @Test
  void ofStampsCreatedAt() {
    ConfigHistory history = ConfigHistory.of(1L, "site.title", null, "v", "CREATE", "admin");

    // createdAt 由工厂即时生成，不应为 null
    assertEquals(true, history.getCreatedAt() instanceof LocalDateTime);
  }

  @Test
  void auditSummaryRendersChangeForUpdate() {
    ConfigHistory history =
        ConfigHistory.of(1L, "site.title", "Bone", "new-value", "UPDATE", "admin");

    String summary = history.auditSummary();
    assertEquals(true, summary.contains("UPDATE"));
    assertEquals(true, summary.contains("site.title"));
    assertEquals(true, summary.contains("Bone"));
    assertEquals(true, summary.contains("new-value"));
  }

  @Test
  void auditSummaryMarksCreateWithInitOldValue() {
    ConfigHistory history = ConfigHistory.of(1L, "site.title", null, "Bone", "CREATE", "admin");

    assertEquals(true, history.auditSummary().contains("(init)"));
  }
}
