package com.bone.blueprint.infrastructure.channel.openapi;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 淘宝 / 天猫开放平台（TOP）协议规格。
 *
 * <p><b>协议要点（与其余三家差异最大的三点）</b>：
 *
 * <ol>
 *   <li>网关是统一路由：{@code https://eco.taobao.com/router/rest}，接口名以 {@code method} 参数传递（{@code
 *       taobao.trade.orders.get}）。
 *   <li>签名是 {@code MD5(appKey + 参数升序拼接 + appSecret)}
 *       的<strong>大写十六进制</strong>，密钥<strong>前后各一份</strong>—— 只拼尾部是 TOP 上最常见的「签名错误」成因（很多 TOP
 *       系文档示例省略了串首的 appKey）。
 *   <li>令牌参数名是 {@code access_token}，且令牌必须参与签名（这一点与拼多多相同，与京东的 {@code token} 不同）。
 * </ol>
 *
 * <p>业务体节点由 {@code AbstractChannelApiSpec} 兜底扫描 {@code *_response} 命中（{@code
 * trade_orders_get_response}）。
 */
@Component
public class TaobaoApiSpec extends AbstractChannelApiSpec {

  /** TOP 网关（淘宝开放平台 / 天猫）。 */
  public static final String GATEWAY = "https://eco.taobao.com/router/rest";

  public TaobaoApiSpec() {
    super(
        "TAOBAO",
        GATEWAY,
        ChannelSignatureAlgorithm.MD5,
        "access_token",
        false,
        null,
        List.of("data"),
        false);
  }

  @Override
  public Map<String, CredentialSlot> credentialKeys() {
    return Map.of("app_key", CredentialSlot.APP_KEY, "app_secret", CredentialSlot.APP_SECRET);
  }

  /** TOP 要求带接口版本号（骨架不提供，因只有 TOP 系要）。 */
  @Override
  protected Map<String, String> extraCommonParams(String apiMethod) {
    return Map.of("v", "2.0");
  }

  @Override
  protected String formatTimestamp(long epochSeconds) {
    return topTimestamp(epochSeconds);
  }
}
