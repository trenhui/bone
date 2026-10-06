package com.bone.blueprint.infrastructure.channel.openapi;

import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 渠道开放平台统一客户端 —— 12 个渠道扩展实现唯一的出网入口。
 *
 * <p><b>它把「协议流程」收成一条链路</b>：查规格 → 取凭证 → 拼公共参数/凭证参数/令牌/业务参数 → 签名 → POST → 解析。 12
 * 份扩展实现因此只需要回答「这个渠道这个动作调哪个接口、带什么业务参数、响应怎么映射成领域对象」， <strong>接第五个渠道时本类零改动</strong>。
 *
 * <p><b>为什么「成功但无业务体」是合法的返回</b>：拉单接口查不到订单、下单只回受理号，渠道都会回 200 + 空业务体。 传输层把它判成「成功」， 由扩展实现决定是回落兜底数据（本地
 * MOCK 通道）还是抛「渠道未返回订单」—— 这是渠道的业务语义，不该由客户端替它决定。
 *
 * <p><b>transport=MOCK 时<strong>不</strong>返回任何伪造业务体</b>：只返回「成功 + 无业务体」，让扩展实现走既有的上下文兜底分支。
 * 伪造一份看起来像真的渠道数据，会让自动化测试「通过」而真实通道从未被验证过——那正是多渠道接入最常见的事故来源。
 *
 * <p><b>网络异常与渠道拒绝分开返回</b>：前者抛 {@code BP_CHANNEL_OPENAPI_FAILED}（重试通常有效）， 后者返回 {@code failed}
 * 结果（重试只会重复被拒），由调用方据此推进状态机。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelOpenApiClient {

  /** 参与签名的渠道参数统一出口（四大平台都用 {@code sign} 这个参数名）。 */
  private static final String SIGN_PARAM = "sign";

  private final List<ChannelApiSpec> specs;
  private final ChannelHttpExecutor httpExecutor;
  private final ChannelOpenApiProperties properties;
  private final ChannelCredentialProvider credentialProvider;

  /** 调用渠道开放平台。 */
  public ChannelApiResult call(ChannelApiRequest request) {
    ChannelApiSpec spec = resolveSpec(request.channelCode());
    if (properties.getTransport() != ChannelOpenApiProperties.Transport.HTTP) {
      log.debug(
          "[{}] 渠道通道为 MOCK（未出发 HTTP），扩展实现回落上下文兜底: method={}",
          spec.channelCode(),
          request.apiMethod());
      return ChannelApiResult.empty(null);
    }

    ChannelCredentials credentials = credentialProvider.require(spec.channelCode());
    int maxAttempts = resolveAttempts(request);
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      Map<String, String> signed = buildSignedParams(spec, credentials, request);
      Map<String, String> headers = headers(spec, credentials);
      logRequestParams(spec, request, signed);
      ChannelHttpResponse response;
      try {
        response = httpExecutor.post(spec.gateway(), headers, signed);
      } catch (ChannelHttpTransportException ex) {
        if (attempt >= maxAttempts) {
          throw BlueprintErrors.of(
              BlueprintErrorCodes.CHANNEL_OPENAPI_FAILED,
              spec.channelCode() + " 开放平台不可用: " + ex.getMessage(),
              ex);
        }
        backoff(spec, request, attempt, "网络异常", ex.getMessage());
        continue;
      }
      if (!response.isOk()) {
        if (attempt >= maxAttempts) {
          throw BlueprintErrors.of(
              BlueprintErrorCodes.CHANNEL_OPENAPI_FAILED,
              spec.channelCode()
                  + " 开放平台返回非 2xx: http="
                  + response.statusCode()
                  + ", method="
                  + request.apiMethod()
                  + ", body="
                  + truncate(response.body()));
        }
        backoff(spec, request, attempt, "HTTP " + response.statusCode(), null);
        continue;
      }
      ChannelApiResult result = spec.parse(response.body());
      if (result.isAuthError()) {
        // 令牌失效：自动刷新需要 refresh_token 与 OAuth 端点，本模块不代持；只把可执行的下一步写进日志与异常路径。
        log.warn(
            "[{}] 渠道令牌失效或签名被拒，请重新授权后执行凭证写回: code={}, msg={}, method={}",
            spec.channelCode(),
            result.errorCode(),
            result.errorMessage(),
            request.apiMethod());
        return result;
      }
      return result;
    }
    throw BlueprintErrors.of(
        BlueprintErrorCodes.CHANNEL_OPENAPI_FAILED,
        spec.channelCode() + " 开放平台调用在 " + maxAttempts + " 次尝试后仍未成功");
  }

  /**
   * 实际尝试次数：<b>非幂等请求只试一次</b>。
   *
   * <p><b>为什么写接口不重试（2026-10-06 修正）</b>：早期实现对所有接口统一重试 {@code maxAttempts} 次。
   * 网络异常恰恰发生在「渠道可能已收到请求、只是响应丢了」的时刻，此时重试写接口会放大成 <b>重复上架 / 重复发货 / 重复扣减</b>：{@code goods.add}
   * 重试多出一份商品， {@code order.ship} 重试可能重复发货，{@code goods.update_stock} 重试在叠加语义下会错累库存。
   * 这类错误的代价远高于「一次网络抖动就让调用方看到失败」——失败可以补偿，重复上架只能人工去渠道删。
   *
   * <p>只读接口（拉单 / 查轨迹）由调用方用 {@link ChannelApiRequest#readOnly} 显式标注后才可重试。
   * 采用<b>显式声明</b>而非按接口名猜测：猜错的方向无法预估，而声明把判断责任放在最懂该接口语义的人（扩展实现）手里。
   */
  private int resolveAttempts(ChannelApiRequest request) {
    int configured = Math.max(1, properties.getMaxAttempts());
    return request.idempotent() ? configured : 1;
  }

  /**
   * 指数退避 + 抖动后重试。
   *
   * <p><b>为什么不立即重试</b>：网络抖动与渠道侧限流都有恢复窗口，立刻重试会与它正面撞车， 把一次可自愈的抖动放大成连续失败。
   *
   * <p><b>为什么要抖动</b>：多个线程被渠道同时拒流后，若退避时长完全相同会在同一毫秒再次同时冲击渠道， 形成同步振荡。随机抖动把重试打散。
   */
  private void backoff(
      ChannelApiSpec spec, ChannelApiRequest request, int attempt, String reason, String detail) {
    long base = Math.max(0L, properties.getRetryBackoffMs());
    double multiplier = Math.max(1.0, properties.getRetryBackoffMultiplier());
    long jitter = Math.max(0L, properties.getRetryBackoffJitterMs());
    long delayMs = (long) (base * Math.pow(multiplier, attempt - 1));
    if (jitter > 0) {
      delayMs += ThreadLocalRandom.current().nextLong(jitter + 1);
    }
    log.warn(
        "[{}] 渠道调用失败，退避后重试 | method={} | reason={} | attempt={}/{} | delayMs={} | detail={}",
        spec.channelCode(),
        request.apiMethod(),
        reason,
        attempt,
        resolveAttempts(request),
        delayMs,
        detail);
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw BlueprintErrors.of(
          BlueprintErrorCodes.CHANNEL_OPENAPI_FAILED,
          spec.channelCode() + " 开放平台调用被中断（退避等待中）: " + request.apiMethod(),
          ex);
    }
  }

  /**
   * 请求参数脱敏日志。
   *
   * <p><b>为什么必须落请求参数</b>（2026-10-06 修正）：早期只在出错时记录响应体， 而本模块最难定位的一类错误正是「签名错误」——渠道只回一句 {@code sign
   * error}， 没有请求参数就无法判断「到底发了什么」「是不是少了参数」「时间戳是否过期」。
   *
   * <p><b>脱敏而非全打</b>：{@code sign} / 密钥 / 令牌一旦进日志即视为泄露（应用日志常被广泛采集转发）， 故一律替换为 {@code ***}；{@code
   * appKey} 等标识性参数保留，它正是排查「串了渠道」的关键线索。
   */
  private static void logRequestParams(
      ChannelApiSpec spec, ChannelApiRequest request, Map<String, String> signed) {
    if (!log.isDebugEnabled()) {
      return;
    }
    Map<String, String> safe = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : signed.entrySet()) {
      safe.put(e.getKey(), isSensitive(e.getKey()) ? "***" : e.getValue());
    }
    log.debug(
        "[{}] 渠道请求参数（已脱敏）| method={} | params={}", spec.channelCode(), request.apiMethod(), safe);
  }

  /** 敏感参数名判定：签名、密钥、令牌类参数一律不落明文。 */
  private static boolean isSensitive(String key) {
    if (key == null) {
      return false;
    }
    String lower = key.toLowerCase();
    return lower.contains("sign")
        || lower.contains("secret")
        || lower.contains("token")
        || lower.contains("access_key")
        || lower.contains("password");
  }

  /** 组装「公共参数 + 凭证参数 + 令牌 + 业务参数 + sign」。 */
  private Map<String, String> buildSignedParams(
      ChannelApiSpec spec, ChannelCredentials credentials, ChannelApiRequest request) {
    Map<String, String> signed =
        new LinkedHashMap<>(spec.commonParams(request.apiMethod(), Instant.now().getEpochSecond()));
    for (Map.Entry<String, CredentialSlot> entry : spec.credentialKeys().entrySet()) {
      // 槽位是枚举 ⇒ 拼错在编译期就被拦下，不会静默跳过导致渠道侧报「签名错误」
      putIfNotBlank(signed, entry.getKey(), entry.getValue().valueOf(credentials));
    }
    if (!spec.tokenAsHeader()) {
      putIfNotBlank(signed, spec.tokenParamName(), credentials.accessToken());
    }
    if (request.params() != null) {
      signed.putAll(request.params());
    }
    signed.put(SIGN_PARAM, spec.sign(signed, credentials.appSecret()));
    return signed;
  }

  /** 令牌走 Header 的渠道（抖音）在这里带上令牌。 */
  private Map<String, String> headers(ChannelApiSpec spec, ChannelCredentials credentials) {
    Map<String, String> headers = new LinkedHashMap<>();
    if (spec.tokenAsHeader()) {
      putIfNotBlank(headers, spec.tokenHeaderName(), credentials.accessToken());
    }
    return headers;
  }

  private ChannelApiSpec resolveSpec(String channelCode) {
    if (specs != null) {
      for (ChannelApiSpec spec : specs) {
        if (spec.channelCode().equals(channelCode)) {
          return spec;
        }
      }
    }
    throw BlueprintErrors.of(
        BlueprintErrorCodes.CHANNEL_CODE_INVALID, "渠道未接入开放平台（无协议规格）: " + channelCode);
  }

  private static void putIfNotBlank(Map<String, String> target, String key, String value) {
    if (key != null && value != null && !value.isBlank()) {
      target.put(key, value);
    }
  }

  /** 响应体只进日志、不进异常文案（渠道错误体常含敏感字段）。 */
  private static String truncate(String body) {
    if (body == null) {
      return "null";
    }
    return body.length() <= 200 ? body : body.substring(0, 200) + "…(truncated)";
  }
}
