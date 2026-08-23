package com.bone.core.tenant.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
}
