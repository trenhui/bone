package com.bone.core.web.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.context.RequestContextLifecycle;
import com.bone.core.tenant.context.TenantContext;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TenantInterceptorTest {

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private Object handler;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    handler = new Object();
    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  static class TestLifecycle implements RequestContextLifecycle {
    final AtomicReference<String> tenant = new AtomicReference<>();
    final AtomicReference<String> biz = new AtomicReference<>();
    final AtomicInteger initCount = new AtomicInteger();
    final AtomicInteger clearCount = new AtomicInteger();
    final AtomicBoolean throwOnClear = new AtomicBoolean(false);

    @Override
    public void initialize(String tenantId, String bizCode) {
      tenant.set(tenantId);
      biz.set(bizCode);
      initCount.incrementAndGet();
    }

    @Override
    public void clear() {
      clearCount.incrementAndGet();
      tenant.set(null);
      biz.set(null);
      if (throwOnClear.get()) {
        throw new RuntimeException("Simulated clear error");
      }
    }
  }

  static class FailingInitLifecycle implements RequestContextLifecycle {
    final AtomicInteger clearCount = new AtomicInteger();

    @Override
    public void initialize(String tenantId, String bizCode) {
      throw new IllegalStateException("Simulated init failure");
    }

    @Override
    public void clear() {
      clearCount.incrementAndGet();
    }
  }

  @SuppressWarnings("unchecked")
  private ObjectProvider<RequestContextLifecycle> mockProvider(
      RequestContextLifecycle... lifecycles) {
    return new ObjectProvider<>() {
      @Override
      public RequestContextLifecycle getObject(Object... args) {
        return lifecycles.length > 0 ? lifecycles[0] : null;
      }

      @Override
      public RequestContextLifecycle getIfAvailable() {
        return lifecycles.length > 0 ? lifecycles[0] : null;
      }

      @Override
      public RequestContextLifecycle getIfUnique() {
        return lifecycles.length == 1 ? lifecycles[0] : null;
      }

      @Override
      public RequestContextLifecycle getObject() {
        return lifecycles.length > 0 ? lifecycles[0] : null;
      }

      @Override
      public Stream<RequestContextLifecycle> stream() {
        return Stream.of(lifecycles);
      }

      @Override
      public Stream<RequestContextLifecycle> orderedStream() {
        return Stream.of(lifecycles);
      }
    };
  }

  @Test
  void testNormalRequestLifecycle() {
    TestLifecycle lifecycle = new TestLifecycle();
    TenantInterceptor interceptor = new TenantInterceptor(mockProvider(lifecycle));

    request.addHeader("X-Tenant-Id", "1001");
    request.addHeader("X-Biz-Code", "EXPENSE");

    boolean proceed = interceptor.preHandle(request, response, handler);
    assertThat(proceed).isTrue();
    assertThat(TenantContext.getTenantId()).isEqualTo("1001");
    assertThat(lifecycle.tenant.get()).isEqualTo("1001");
    assertThat(lifecycle.biz.get()).isEqualTo("EXPENSE");
    assertThat(lifecycle.initCount.get()).isEqualTo(1);

    interceptor.afterCompletion(request, response, handler, null);
    assertThat(TenantContext.getTenantId()).isNull();
    assertThat(lifecycle.tenant.get()).isNull();
    assertThat(lifecycle.clearCount.get())
        .isEqualTo(2); // 1 defensive on preHandle + 1 on afterCompletion
  }

  @Test
  void testMissingTenantHeader_failClosed() {
    TestLifecycle lifecycle = new TestLifecycle();
    TenantInterceptor interceptor = new TenantInterceptor(mockProvider(lifecycle));

    boolean proceed = interceptor.preHandle(request, response, handler);
    assertThat(proceed).isTrue();
    assertThat(TenantContext.getTenantId()).isNull();
    assertThat(lifecycle.tenant.get()).isNull();
    assertThat(lifecycle.initCount.get()).isEqualTo(0);

    interceptor.afterCompletion(request, response, handler, null);
    assertThat(TenantContext.getTenantId()).isNull();
  }

  @Test
  void testThreadPoolReuseSafety() throws Exception {
    TestLifecycle lifecycle = new TestLifecycle();
    TenantInterceptor interceptor = new TenantInterceptor(mockProvider(lifecycle));
    ExecutorService executor = Executors.newSingleThreadExecutor();

    try {
      // 任务 1: 请求 tenant-A
      Callable<Void> task1 =
          () -> {
            MockHttpServletRequest req1 = new MockHttpServletRequest();
            req1.addHeader("X-Tenant-Id", "tenant-A");
            interceptor.preHandle(req1, new MockHttpServletResponse(), handler);
            assertThat(TenantContext.getTenantId()).isEqualTo("tenant-A");
            assertThat(lifecycle.tenant.get()).isEqualTo("tenant-A");
            interceptor.afterCompletion(req1, new MockHttpServletResponse(), handler, null);
            return null;
          };

      // 任务 2: 在同一线程上执行未带 Header 的请求，断言绝不读取到 tenant-A 的脏数据
      Callable<Void> task2 =
          () -> {
            MockHttpServletRequest req2 = new MockHttpServletRequest();
            interceptor.preHandle(req2, new MockHttpServletResponse(), handler);
            assertThat(TenantContext.getTenantId()).isNull();
            assertThat(lifecycle.tenant.get()).isNull();
            interceptor.afterCompletion(req2, new MockHttpServletResponse(), handler, null);
            return null;
          };

      Future<Void> f1 = executor.submit(task1);
      f1.get();

      Future<Void> f2 = executor.submit(task2);
      f2.get();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void testDefensivePreHandleCleanup() {
    TestLifecycle lifecycle = new TestLifecycle();
    TenantInterceptor interceptor = new TenantInterceptor(mockProvider(lifecycle));

    // 人为注入未清理的脏数据
    TenantContext.setTenantId("dirty-tenant");
    lifecycle.tenant.set("dirty-extension");

    // 新请求未携带 header
    interceptor.preHandle(request, response, handler);

    // 断言 preHandle 入口防御性清理将旧数据全部冲刷
    assertThat(TenantContext.getTenantId()).isNull();
    assertThat(lifecycle.tenant.get()).isNull();
  }

  @Test
  void testAsyncHandlingStartedReleasesThreadContext() {
    TestLifecycle lifecycle = new TestLifecycle();
    TenantInterceptor interceptor = new TenantInterceptor(mockProvider(lifecycle));

    request.addHeader("X-Tenant-Id", "async-tenant");
    interceptor.preHandle(request, response, handler);
    assertThat(TenantContext.getTenantId()).isEqualTo("async-tenant");

    // 触发异步调度
    interceptor.afterConcurrentHandlingStarted(request, response, handler);

    // 断言当前线程已释放
    assertThat(TenantContext.getTenantId()).isNull();
    assertThat(lifecycle.tenant.get()).isNull();
  }

  @Test
  void testLifecycleExceptionIsolation_guaranteesTenantContextClear() {
    TestLifecycle lifecycle = new TestLifecycle();
    lifecycle.throwOnClear.set(true); // 外部 SPI clear() 异常
    TenantInterceptor interceptor = new TenantInterceptor(mockProvider(lifecycle));

    request.addHeader("X-Tenant-Id", "999");
    interceptor.preHandle(request, response, handler);
    assertThat(TenantContext.getTenantId()).isEqualTo("999");

    // afterCompletion 中即便 SPI 抛异常，TenantContext 依然必须被 finally 清理
    assertThatCode(() -> interceptor.afterCompletion(request, response, handler, null))
        .doesNotThrowAnyException();
    assertThat(TenantContext.getTenantId()).isNull();
  }

  /**
   * 判别用例：SPI 初始化异常时必须在 preHandle 内就地清理 ThreadLocal 并把异常向上抛（fail-closed 拒绝请求）。 Spring 的
   * applyPreHandle 在 preHandle 抛错时不会回调本拦截器 afterCompletion，若不在异常路径清理，租户上下文将泄漏。
   */
  @Test
  void testInitializeFailureCleansContextAndPropagates() {
    FailingInitLifecycle failing = new FailingInitLifecycle();
    TestLifecycle healthy = new TestLifecycle();
    TenantInterceptor interceptor = new TenantInterceptor(mockProvider(failing, healthy));

    request.addHeader("X-Tenant-Id", "fail-tenant");
    assertThatThrownBy(() -> interceptor.preHandle(request, response, handler))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Simulated init failure");

    // 异常必须就地对称清理：TenantContext 与所有 SPI（含已初始化/未初始化的）都被冲刷。
    // failing 被清理 2 次 = 入口防御性清理 1 次 + 异常路径对称清理 1 次
    assertThat(TenantContext.getTenantId()).isNull();
    assertThat(failing.clearCount.get()).isEqualTo(2);
    assertThat(healthy.tenant.get()).isNull();
  }

  @Test
  void testNoArgConstructorCompatibility() {
    TenantInterceptor interceptor = new TenantInterceptor();
    request.addHeader("X-Tenant-Id", "default-test");

    assertThatCode(
            () -> {
              interceptor.preHandle(request, response, handler);
              assertThat(TenantContext.getTenantId()).isEqualTo("default-test");
              interceptor.afterCompletion(request, response, handler, null);
              assertThat(TenantContext.getTenantId()).isNull();
            })
        .doesNotThrowAnyException();
  }
}
