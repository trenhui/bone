package com.bone.blueprint.infrastructure.channel.openapi;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 京东开放平台（宙斯 / JOS）协议规格。
 *
 * <p><b>与淘宝的三点关键差异</b>：
 *
 * <ol>
 *   <li>令牌参数名是 {@code token} 而非 {@code access_token}；
 *   <li>业务体包在固定的 {@code jd_response_content} 下，且业务对象是<strong>JSON 字符串</strong>（{@code result}
 *       字段），需要二次解析；
 *   <li>业务参数走 {@code param_json}（复杂参数必须序列化成 JSON），简单参数可直接平铺——扩展实现按接口自行选择。
 * </ol>
 *
 * <p><b>签名</b>：{@code MD5(参数升序拼接 + appSecret)}，密钥只拼在<strong>尾部</strong>（与 TOP 的「前后各一份」不同）。
 */
@Component
public class JdApiSpec extends AbstractChannelApiSpec {

  /** 宙斯网关。 */
  public static final String GATEWAY = "https://router.jd.com/api";

  private static final DateTimeFormatter JD_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public JdApiSpec() {
    super(
        "JD",
        GATEWAY,
        ChannelSignatureAlgorithm.MD5,
        "token",
        false,
        null,
        List.of("jd_response_content", "data", "result"),
        true);
  }

  @Override
  public Map<String, String> credentialKeys() {
    return Map.of("app_key", "appKey");
  }

  @Override
  public Map<String, String> commonParams(String apiMethod, long timestampSeconds) {
    Map<String, String> common = params();
    common.put("method", apiMethod);
    common.put("format", "json");
    common.put("sign_method", "md5");
    common.put("timestamp", timestamp(timestampSeconds));
    return common;
  }

  /** 宙斯：密钥只出现在待签串尾部。 */
  @Override
  protected String signInput(Map<String, String> signedParams, String secret) {
    String joined = ChannelSignatureAlgorithm.concat(signedParams);
    return secret == null ? joined : joined + secret;
  }

  private static String timestamp(long epochSeconds) {
    return JD_TIME.format(
        LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault()));
  }
}
