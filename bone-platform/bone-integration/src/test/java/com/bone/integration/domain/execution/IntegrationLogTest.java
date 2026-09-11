package com.bone.integration.domain.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.execution.event.ExecutionCompletedEvent;
import com.bone.integration.domain.model.execution.event.ExecutionStartedEvent;
import com.bone.integration.domain.model.execution.vo.ExecutionStatus;
import org.junit.jupiter.api.Test;

/** {@link IntegrationLog} 纯单测：PENDING→RUNNING→SUCCESS/FAILED/TIMEOUT 状态机与非法迁移拒绝（无容器）。 */
class IntegrationLogTest {

  private IntegrationLog createPendingLog() {
    return IntegrationLog.create(1L, 100L, "{\"orderId\":1}");
  }

  @Test
  void testCreateStartsAtPendingAndPublishesStartedEvent() {
    IntegrationLog log = createPendingLog();

    assertEquals(ExecutionStatus.PENDING, log.getStatus());
    assertEquals(100L, log.getFlowId());
    assertEquals(1, log.getDomainEvents().size());
    assertInstanceOf(ExecutionStartedEvent.class, log.getDomainEvents().get(0));
  }

  @Test
  void testCreateRejectsNullFlowId() {
    assertThrows(DomainException.class, () -> IntegrationLog.create(1L, null, "{}"));
  }

  @Test
  void testStartMovesToRunning() {
    IntegrationLog log = createPendingLog();

    log.start();

    assertEquals(ExecutionStatus.RUNNING, log.getStatus());
    assertNotNull(log.getStartedAt());
  }

  @Test
  void testStartOnlyFromPending() {
    IntegrationLog log = createPendingLog();
    log.start();

    // 已 RUNNING 再次 start 拒绝
    assertThrows(DomainException.class, log::start);
  }

  @Test
  void testCompleteOnlyFromRunning() {
    IntegrationLog log = createPendingLog();
    // PENDING 直接 complete 拒绝
    assertThrows(DomainException.class, () -> log.complete("{}"));

    log.start();
    log.clearDomainEvents();
    log.complete("{\"code\":0}");

    assertEquals(ExecutionStatus.SUCCESS, log.getStatus());
    assertEquals("{\"code\":0}", log.getOutputData());
    assertNotNull(log.getEndedAt());
    assertNull(log.getErrorMessage());
    assertEquals(2, log.getDomainEvents().size());
    assertInstanceOf(ExecutionCompletedEvent.class, log.getDomainEvents().get(0));
  }

  @Test
  void testFailMarksFailedFromRunning() {
    IntegrationLog log = createPendingLog();
    log.start();
    log.clearDomainEvents();

    log.fail("连接目标系统超时");

    assertEquals(ExecutionStatus.FAILED, log.getStatus());
    assertEquals("连接目标系统超时", log.getErrorMessage());
    assertNotNull(log.getEndedAt());
    // fail 同样追加失败完成事件与流程执行结果事件
    assertEquals(2, log.getDomainEvents().size());
    assertInstanceOf(ExecutionCompletedEvent.class, log.getDomainEvents().get(0));
  }

  @Test
  void testTimeoutMarksTimeoutFromRunning() {
    IntegrationLog log = createPendingLog();
    log.start();
    log.clearDomainEvents();

    log.timeout();

    assertEquals(ExecutionStatus.TIMEOUT, log.getStatus());
    assertEquals("执行超时", log.getErrorMessage());
    assertNotNull(log.getEndedAt());
    assertEquals(2, log.getDomainEvents().size());
    assertInstanceOf(ExecutionCompletedEvent.class, log.getDomainEvents().get(0));
  }

  @Test
  void testTerminalStateGuardsFurtherLifecycleMoves() {
    IntegrationLog log = createPendingLog();
    log.start();
    log.complete("ok");

    // 终态 SUCCESS 上 start / fail 均拒绝
    assertThrows(DomainException.class, log::start);
    assertThrows(DomainException.class, () -> log.fail("late error"));
    assertThrows(DomainException.class, () -> log.complete("again"));
    assertThrows(DomainException.class, log::timeout);
  }
}
