package com.bone.blueprint.infrastructure.channel.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.blueprint.common.BlueprintErrorCodes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 开放平台客户端的三个 P1 修复的守门测试（2026-10-06）。
 *
 * <p><b>为什么这三项必须有测试</b>：它们都属于「不抛异常、不影响启动、单测全绿」的缺陷类别， 修复前线上表现分别是：①高并发时连接池从不复用（每次新建 HttpClient）→
 * TIME_WAIT 堆积、偶发 connect timeout； ②写接口在网络抖动时重试 → <b>重复上架 / 重复发货 / 库存错累</b>；③签名错误时无请求参数可查 → 渠道只回
 * {@code sign error}，无法定位。
 *
 * <p>这三项都不会让任何现有断言变红，所以必须显式锁住，否则下次重构就会悄悄退回去。
 */
class ChannelOpenApiResilienceTest {

  /** 可控的传输桩：记录调用次数，按脚本决定成功 / 网络异常 / 非 2xx。 */
  private static final class ScriptedExecutor implements ChannelHttpExecutor {
    final AtomicInteger calls = new AtomicInteger();
    final int failTimes;
    final boolean networkException;

    ScriptedExecutor(int failTimes, boolean networkException) {
      this.failTimes = failTimes;
      this.networkException = networkException;
    }

    @Override
    public ChannelHttpResponse post(
        String url, Map<String, String> headers, Map<String, String> form) {
      if (calls.incrementAndGet() <= failTimes) {
        if (networkException) {
          throw new ChannelHttpTransportException("模拟网络异常", new RuntimeException("boom"));
        }
        return new ChannelHttpResponse(503, "{\"error_response\":{\"code\":\"503\"}}");
      }
      return new ChannelHttpResponse(200, "{\"ok\":1}");
    }
  }

  private static ChannelOpenApiProperties props(String transport) {
    ChannelOpenApiProperties p = new ChannelOpenApiProperties();
    p.setTransport(ChannelOpenApiProperties.Transport.valueOf(transport));
    p.setMaxAttempts(3);
    p.setRetryBackoffMs(1);
    p.setRetryBackoffJitterMs(0);
    return p;
  }

  private static ChannelCredentialProvider credentials() {
    ChannelCredentialProvider provider = mock(ChannelCredentialProvider.class);
    when(provider.require(anyString()))
        .thenReturn(new ChannelCredentials("ak", "as", "token", null));
    return provider;
  }

  private static ChannelOpenApiClient client(
      ChannelHttpExecutor executor, ChannelOpenApiProperties p) {
    return new ChannelOpenApiClient(List.of(new TaobaoApiSpec()), executor, p, credentials());
  }

  @Nested
  @DisplayName("P1-2：重试只对幂等请求生效")
  class RetryOnlyForIdempotent {

    @Test
    @DisplayName("非幂等请求（写接口）遇网络异常只试 1 次（重试=重复上架/重复发货）")
    void nonIdempotentRequestDoesNotRetry() {
      ScriptedExecutor executor = new ScriptedExecutor(99, true);
      ChannelOpenApiClient client = client(executor, props("HTTP"));

      assertThrows(
          RuntimeException.class,
          () -> client.call(ChannelApiRequest.of("TAOBAO", "taobao.item.add", 1L)),
          "写接口网络异常应直接失败");

      assertEquals(
          1,
          executor.calls.get(),
          "写接口重试会放大成重复上架/重复发货——goods.add 多出一份商品、order.ship 重复发货，" + "这类错误的代价远高于让调用方看到一次失败");
    }

    @Test
    @DisplayName("非幂等请求遇非 2xx 也不重试")
    void nonIdempotentRequestDoesNotRetryOnHttpError() {
      ScriptedExecutor executor = new ScriptedExecutor(99, false);
      ChannelOpenApiClient client = client(executor, props("HTTP"));

      assertThrows(
          RuntimeException.class,
          () -> client.call(ChannelApiRequest.of("TAOBAO", "taobao.item.add", 1L)));
      assertEquals(1, executor.calls.get(), "HTTP 5xx 同样可能已被渠道处理，不应自动重试写接口");
    }

    @Test
    @DisplayName("幂等只读请求遇网络异常会重试并在后续成功时返回结果")
    void idempotentRequestRetries() {
      ScriptedExecutor executor = new ScriptedExecutor(1, true);
      ChannelOpenApiClient client = client(executor, props("HTTP"));

      ChannelApiResult result =
          client.call(ChannelApiRequest.readOnly("TAOBAO", "taobao.trade.orders.get", 1L));

      assertTrue(result.success(), "拉单属只读，重试后应成功返回");
      assertEquals(2, executor.calls.get(), "首次网络异常后应重试一次");
    }

    @Test
    @DisplayName("幂等请求重试到耗尽后抛出（不静默返回失败）")
    void idempotentRequestFailsAfterAttemptsExhausted() {
      ScriptedExecutor executor = new ScriptedExecutor(99, true);
      ChannelOpenApiClient client = client(executor, props("HTTP"));

      assertThrows(
          RuntimeException.class,
          () -> client.call(ChannelApiRequest.readOnly("TAOBAO", "taobao.trade.orders.get", 1L)));
      assertEquals(3, executor.calls.get(), "maxAttempts=3 应真实重试 3 次");
    }

    @Test
    @DisplayName("默认即非幂等（不靠接口名猜测，必须显式声明）")
    void defaultIsNonIdempotent() {
      assertFalse(ChannelApiRequest.of("TAOBAO", "x.y.z", 1L).idempotent());
      assertTrue(ChannelApiRequest.readOnly("TAOBAO", "x.y.z", 1L).idempotent());
    }

    @Test
    @DisplayName("with/withAll 不丢失幂等标记（否则补参后会被当成写接口）")
    void withKeepsIdempotentFlag() {
      ChannelApiRequest ro = ChannelApiRequest.readOnly("TAOBAO", "a.b", 1L).with("k", "v");
      assertTrue(ro.idempotent(), "with 后丢失标记会让只读请求不再重试");
      assertTrue(ro.withAll(Map.of("k2", "v2")).idempotent());
      assertFalse(ChannelApiRequest.of("TAOBAO", "a.b", 1L).with("k", "v").idempotent());
    }
  }

  @Nested
  @DisplayName("P1-1：HttpClient 复用")
  class HttpClientReuse {

    @Test
    @DisplayName("多次调用复用同一个 HttpClient 实例（连接池不被反复重建）")
    void reusesHttpClientAcrossCalls() throws Exception {
      ChannelOpenApiProperties p = props("MOCK");
      JdkChannelHttpExecutor executor = new JdkChannelHttpExecutor(p);

      // 反射读私有缓存，验证「同一配置下 client() 返回同一实例」
      var method = JdkChannelHttpExecutor.class.getDeclaredMethod("client");
      method.setAccessible(true);
      Object first = method.invoke(executor);
      Object second = method.invoke(executor);

      assertSame(first, second, "每次请求 new HttpClient 会让连接池与 selector 线程池反复重建");
    }

    @Test
    @DisplayName("连接超时配置变更时重建客户端")
    void rebuildsOnTimeoutChange() throws Exception {
      ChannelOpenApiProperties p = props("MOCK");
      JdkChannelHttpExecutor executor = new JdkChannelHttpExecutor(p);

      var method = JdkChannelHttpExecutor.class.getDeclaredMethod("client");
      method.setAccessible(true);
      Object first = method.invoke(executor);
      p.setConnectTimeoutMs(p.getConnectTimeoutMs() + 1000);
      Object second = method.invoke(executor);

      assertFalse(first == second, "超时配置变了仍复用旧客户端 ⇒ 新配置不生效");
    }
  }

  @Nested
  @DisplayName("P1-3：请求参数脱敏日志")
  class RequestParamLogging {

    @Test
    @DisplayName("签名/密钥/令牌类参数不落明文（应用日志常被广泛采集转发）")
    void sensitiveKeysAreMasked() throws Exception {
      // 走真实客户端，确认调用链不会因脱敏判定抛错，同时锁死脱敏判据
      ChannelOpenApiProperties p = props("HTTP");
      ChannelOpenApiClient client = client(new ScriptedExecutor(0, false), p);
      assertTrue(
          client
              .call(ChannelApiRequest.readOnly("TAOBAO", "taobao.trade.orders.get", 1L))
              .success());

      // appKey 保留（排查「串了渠道」的关键线索），签名/密钥/令牌必须判为敏感
      assertTrue(isSensitiveForTest("sign"));
      assertTrue(isSensitiveForTest("access_token"));
      assertTrue(isSensitiveForTest("app_secret"));
      assertFalse(isSensitiveForTest("app_key"));
      assertFalse(isSensitiveForTest("method"));
    }

    /** 通过反射调用私有的脱敏判定，锁死判据（键名里含 sign/secret/token 即视为敏感）。 */
    private boolean isSensitiveForTest(String key) throws Exception {
      var m = ChannelOpenApiClient.class.getDeclaredMethod("isSensitive", String.class);
      m.setAccessible(true);
      return (boolean) m.invoke(null, key);
    }
  }

  @Nested
  @DisplayName("错误码与告警")
  class ErrorPaths {

    @Test
    @DisplayName("重试耗尽抛出的错误码是 CHANNEL_OPENAPI_FAILED（供上层区分可重试/不可重试）")
    void usesExpectedErrorCode() {
      ScriptedExecutor executor = new ScriptedExecutor(99, true);
      ChannelOpenApiClient client = client(executor, props("HTTP"));

      var ex =
          assertThrows(
              RuntimeException.class,
              () ->
                  client.call(ChannelApiRequest.readOnly("TAOBAO", "taobao.trade.orders.get", 1L)));
      assertTrue(
          String.valueOf(ex.getMessage()).contains(BlueprintErrorCodes.CHANNEL_OPENAPI_FAILED),
          "错误码应稳定为 BP_CHANNEL_OPENAPI_FAILED，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("MOCK 通道不出网（一次 HTTP 都不发）")
    void mockTransportDoesNotCallHttp() {
      ScriptedExecutor executor = new ScriptedExecutor(0, false);
      ChannelOpenApiClient client = client(executor, props("MOCK"));

      ChannelApiResult result = client.call(ChannelApiRequest.of("TAOBAO", "taobao.item.add", 1L));

      assertTrue(result.success());
      assertEquals(0, executor.calls.get(), "MOCK 通道必须零出网");
    }

    @Test
    @DisplayName("未接入渠道报 CHANNEL_CODE_INVALID（而非静默，且零出网）")
    void unknownChannelFailsFast() {
      ScriptedExecutor executor = new ScriptedExecutor(0, false);
      ChannelOpenApiClient client = client(executor, props("HTTP"));

      var ex =
          assertThrows(
              RuntimeException.class, () -> client.call(ChannelApiRequest.of("NOPE", "x", 1L)));
      assertTrue(
          String.valueOf(ex.getMessage()).contains(BlueprintErrorCodes.CHANNEL_CODE_INVALID));
      assertEquals(0, executor.calls.get(), "渠道未接入时必须在出网前就失败");
    }
  }
}
