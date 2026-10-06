package com.bone.blueprint.infrastructure.channel.openapi;

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
  public Map<String, CredentialSlot> credentialKeys() {
    return Map.of("client_id", CredentialSlot.APP_KEY, "client_secret", CredentialSlot.APP_SECRET);
  }

  /** 拼多多的 {@code format} 取大写 {@code JSON}（其余三家是小写 {@code json}）。 */
  @Override
  protected String formatValue() {
    return "JSON";
  }

  @Override
  protected String formatTimestamp(long epochSeconds) {
    return topTimestamp(epochSeconds);
  }
}
