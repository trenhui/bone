package com.bone.core.tenant.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.threadlocal.TransmittableThreadLocal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** TenantContext 租户上下文测试：设置/读取/清除/数值转换/跨线程传递 */
class TenantContextTest {

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void defaultValueIsNull() {
    assertThat(TenantContext.getTenantId()).isNull();
    assertThat(TenantContext.getTenantIdAsLong()).isNull();
  }

  @Test
  void setAndGetStringTenantId() {
    TenantContext.setTenantId("acme");
    assertThat(TenantContext.getTenantId()).isEqualTo("acme");
    // 非数值租户编码无法转 Long
    assertThat(TenantContext.getTenantIdAsLong()).isNull();
  }

  @Test
  void setAndGetNumericTenantId() {
    TenantContext.setTenantId("1234567890123456789");
    assertThat(TenantContext.getTenantIdAsLong()).isEqualTo(1234567890123456789L);
  }

  @Test
  void setLongTenantId_convertsToString() {
    TenantContext.setTenantId(42L);
    assertThat(TenantContext.getTenantId()).isEqualTo("42");
    assertThat(TenantContext.getTenantIdAsLong()).isEqualTo(42L);
  }

  @Test
  void setBlankOrNullIsHandled() {
    TenantContext.setTenantId("");
    assertThat(TenantContext.getTenantIdAsLong()).isNull();
    TenantContext.setTenantId((String) null);
    assertThat(TenantContext.getTenantId()).isNull();
  }

  @Test
  void clearRemovesContext() {
    TenantContext.setTenantId("acme");
    TenantContext.clear();
    assertThat(TenantContext.getTenantId()).isNull();
  }

  @Test
  void contextPropagatesToChildThreads() throws Exception {
    TenantContext.setTenantId("acme");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      CompletableFuture<String> future =
          CompletableFuture.supplyAsync(TenantContext::getTenantId, executor);
      assertThat(future.get()).isEqualTo("acme");
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void contextsAreIsolatedBetweenThreads() throws Exception {
    TenantContext.setTenantId("thread-main");
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      // 子线程捕获创建时的值后，主线程修改不应影响子线程已捕获的值
      CompletableFuture<String> future =
          CompletableFuture.supplyAsync(TenantContext::getTenantId, executor);
      assertThat(future.get()).isEqualTo("thread-main");
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void wrapPropagatesToReusedWorkerThread() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      TenantContext.setTenantId("acme");
      Future<String> first =
          executor.submit(TransmittableThreadLocal.wrap(() -> TenantContext.getTenantId()));
      assertThat(first.get()).isEqualTo("acme");

      // 复用同一 worker：InheritableThreadLocal 不会重新继承，必须靠 wrap() 在提交时捕获
      TenantContext.setTenantId("acme2");
      Future<String> second =
          executor.submit(TransmittableThreadLocal.wrap(() -> TenantContext.getTenantId()));
      assertThat(second.get()).isEqualTo("acme2");
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void clearDoesNotBreakSubsequentWrapCapture() throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      // 先创建并复用 worker 线程（此时主线程无租户，触发一次性继承 null）
      Future<String> warmup =
          executor.submit(TransmittableThreadLocal.wrap(() -> TenantContext.getTenantId()));
      assertThat(warmup.get()).isNull();

      TenantContext.setTenantId("acme");
      TenantContext.clear();
      // clear() 之后仍能通过 wrap() 捕获新租户（remove 不再从 HOLDER 注销全局注册）
      TenantContext.setTenantId("acme2");
      Future<String> future =
          executor.submit(TransmittableThreadLocal.wrap(() -> TenantContext.getTenantId()));
      assertThat(future.get()).isEqualTo("acme2");
    } finally {
      executor.shutdownNow();
    }
  }
}
