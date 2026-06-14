package com.bone.engine.extension.core.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExtensionExecutionGuardTest {

  @Test
  @DisplayName("正常任务在超时内返回结果")
  void execute_returnsResult() throws Throwable {
    ExtensionExecutionGuard guard = new ExtensionExecutionGuard(500, 4);
    String value = guard.execute(() -> "ok");
    assertEquals("ok", value);
  }

  @Test
  @DisplayName("超时取消并抛出 TimeoutException")
  void execute_timesOut() {
    ExtensionExecutionGuard guard = new ExtensionExecutionGuard(50, 4);
    assertThrows(
        java.util.concurrent.TimeoutException.class,
        () ->
            guard.execute(
                () -> {
                  Thread.sleep(200);
                  return "late";
                }));
  }

  @Test
  @DisplayName("舱壁满时拒绝新任务")
  void execute_bulkheadFull() throws Exception {
    ExtensionExecutionGuard guard = new ExtensionExecutionGuard(5_000, 1);
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger rejected = new AtomicInteger();

    Thread holder =
        new Thread(
            () -> {
              try {
                guard.execute(
                    () -> {
                      started.countDown();
                      release.await(3, TimeUnit.SECONDS);
                      return null;
                    });
              } catch (Throwable ignored) {
                // test thread
              }
            });
    holder.setDaemon(true);
    holder.start();
    assertTrue(started.await(2, TimeUnit.SECONDS));

    Thread challenger =
        new Thread(
            () -> {
              try {
                guard.execute(() -> "x");
              } catch (Throwable ex) {
                if (ex instanceof IllegalStateException) {
                  rejected.incrementAndGet();
                }
              }
            });
    challenger.setDaemon(true);
    challenger.start();
    challenger.join(2_000);
    release.countDown();
    holder.join(2_000);

    assertEquals(1, rejected.get());
  }
}
