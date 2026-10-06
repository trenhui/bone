package com.bone.blueprint.infrastructure.channel.openapi;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 拼多多开放平台协议规格。
 *
 * <p><b>与另外三家的关键差异</b>：
 *
 * <ol>
 *   <li>凭证不叫 appKey：{@code client_id} / {@code client_secret}。映射在 {@link #credentialKeys()} 里声明，
 *       客户端因此只需要认 {@code appKey / appSecret / accessToken} 三个凭证槽位。
 *   <li>错误体是<strong>嵌套</strong>的：{@code {"..._response":{"error_response":{"error_code":...}}}}， 所以
 *       {@link AbstractChannelApiSpec} 的错误查找必须下探到业务体内部，只查根层会把拼多多的每一次拒绝都误判成成功。
 *   <li>签名与淘宝同构：{@code MD5(client_secret + 参数升序 + client_secret)}，令牌 {@code access_token} 参与签名。
 * </ol>
 */
@Component
public class PddApiSpec extends AbstractChannelApiSpec {

  /** 拼多多开放平台网关。 */
  public static final String GATEWAY = "https://gw-api.pinduoduo.com/api/router";

  private static final DateTimeFormatter PDD_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public PddApiSpec() {
    super(
        "PDD",
        GATEWAY,
        ChannelSignatureAlgorithm.MD5,
        "access_token",
        false,
        null,
        List.of("data", "result"),
        false);
  }

  @Override
  public Map<String, String> credentialKeys() {
    return Map.of("client_id", "appKey");
  }

  @Override
  public Map<String, String> commonParams(String apiMethod, long timestampSeconds) {
    Map<String, String> common = params();
    common.put("method", apiMethod);
    common.put("format", "JSON");
    common.put("sign_method", "md5");
    common.put("timestamp", timestamp(timestampSeconds));
    return common;
  }

  private static String timestamp(long epochSeconds) {
    return PDD_TIME.format(
        LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault()));
  }
}
