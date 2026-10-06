package com.bone.blueprint.infrastructure.channel.openapi;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

  private static final DateTimeFormatter TOP_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
  public Map<String, String> credentialKeys() {
    return Map.of("app_key", "appKey");
  }

  @Override
  public Map<String, String> commonParams(String apiMethod, long timestampSeconds) {
    Map<String, String> common = params();
    common.put("method", apiMethod);
    common.put("format", "json");
    common.put("v", "2.0");
    common.put("sign_method", "md5");
    common.put("timestamp", timestamp(timestampSeconds));
    return common;
  }

  private static String timestamp(long epochSeconds) {
    return TOP_TIME.format(
        LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault()));
  }
}
