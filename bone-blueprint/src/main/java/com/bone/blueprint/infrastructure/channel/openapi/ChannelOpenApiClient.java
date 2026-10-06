package com.bone.blueprint.infrastructure.channel.openapi;

import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    int attempts = Math.max(1, properties.getMaxAttempts());
    for (int attempt = 1; attempt <= attempts; attempt++) {
      Map<String, String> signed = buildSignedParams(spec, credentials, request);
      Map<String, String> headers = headers(spec, credentials);
      ChannelHttpResponse response;
      try {
        response = httpExecutor.post(spec.gateway(), headers, signed);
      } catch (ChannelHttpTransportException ex) {
        if (attempt >= attempts) {
          throw BlueprintErrors.of(
              BlueprintErrorCodes.CHANNEL_OPENAPI_FAILED,
              spec.channelCode() + " 开放平台不可用: " + ex.getMessage(),
              ex);
        }
        log.warn("[{}] 渠道调用网络异常，准备重试: attempt={}/{}", spec.channelCode(), attempt, attempts);
        continue;
      }
      if (!response.isOk()) {
        if (attempt >= attempts) {
          throw BlueprintErrors.of(
              BlueprintErrorCodes.CHANNEL_OPENAPI_FAILED,
              spec.channelCode()
                  + " 开放平台返回非 2xx: http="
                  + response.statusCode()
                  + ", body="
                  + truncate(response.body()));
        }
        continue;
      }
      ChannelApiResult result = spec.parse(response.body());
      if (result.isAuthError()) {
        // 令牌失效：自动刷新需要 refresh_token 与 OAuth 端点，本模块不代持；只把可执行的下一步写进日志与异常路径。
        log.warn(
            "[{}] 渠道令牌失效或签名被拒，请重新授权后执行凭证写回: code={}, msg={}",
            spec.channelCode(),
            result.errorCode(),
            result.errorMessage());
        return result;
      }
      return result;
    }
    throw BlueprintErrors.of(
        BlueprintErrorCodes.CHANNEL_OPENAPI_FAILED,
        spec.channelCode() + " 开放平台调用在 " + attempts + " 次尝试后仍未成功");
  }

  /** 组装「公共参数 + 凭证参数 + 令牌 + 业务参数 + sign」。 */
  private Map<String, String> buildSignedParams(
      ChannelApiSpec spec, ChannelCredentials credentials, ChannelApiRequest request) {
    Map<String, String> signed =
        new LinkedHashMap<>(spec.commonParams(request.apiMethod(), Instant.now().getEpochSecond()));
    for (Map.Entry<String, String> entry : spec.credentialKeys().entrySet()) {
      switch (entry.getValue()) {
        case "appKey" -> putIfNotBlank(signed, entry.getKey(), credentials.appKey());
        case "appSecret" -> putIfNotBlank(signed, entry.getKey(), credentials.appSecret());
        case "accessToken" -> putIfNotBlank(signed, entry.getKey(), credentials.accessToken());
        default -> log.warn("未知凭证槽位，已跳过: {}", entry.getValue());
      }
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
