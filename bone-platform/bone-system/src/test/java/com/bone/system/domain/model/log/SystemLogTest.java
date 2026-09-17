package com.bone.system.domain.model.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import com.bone.system.domain.model.log.vo.LogLevel;
import org.junit.jupiter.api.Test;

/** {@link SystemLog} 纯单测：append-only 审计快照的记录语义与级别校验（无容器）。 */
class SystemLogTest {

  @Test
  void testCreateRecordsSnapshot() {
    SystemLog log = SystemLog.create(1L, LogLevel.ERROR, "bone-system", "鉴权失败", "trace-01");

    assertEquals(LogLevel.ERROR, log.getLevel());
    assertEquals("bone-system", log.getService());
    assertEquals("鉴权失败", log.getContent());
    assertEquals("trace-01", log.getTraceId());
    assertNotNull(log.getCreatedAt());
  }

  @Test
  void testTraceIdMayBeAbsent() {
    SystemLog log = SystemLog.create(2L, LogLevel.INFO, "bone-system", "健康检查", null);

    assertEquals("健康检查", log.getContent());
    assertEquals(null, log.getTraceId());
  }

  @Test
  void testInvalidLogLevelRejected() {
    assertThrows(DomainException.class, () -> LogLevel.fromString(null));
    assertThrows(DomainException.class, () -> LogLevel.fromString("VERBOSE"));
  }
}
