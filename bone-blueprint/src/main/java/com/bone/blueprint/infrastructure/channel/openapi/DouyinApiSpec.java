package com.bone.blueprint.infrastructure.channel.openapi;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 抖音开放平台（电商 / 精选联盟）协议规格。
 *
 * <p><b>与 TOP 系的三点关键差异</b>：
 *
 * <ol>
 *   <li>令牌走 <strong>Header</strong>（{@code Access-Token}）而不是 {@code access_token} 参数——
 *       令牌还参与签名，二者缺一不可（换 Header 但漏签名，抖音侧直接判 {@code sign error}）。
 *   <li>签名是 <strong>HMAC-SHA256</strong> 大写十六进制，密钥只作 HMAC 密钥、不拼进待签串（与三家的「拼串 + MD5」完全不同族）。
 *   <li>响应是统一信封 {@code {"code":0,"data":{...},"message":""}}，业务体固定在 {@code data}，不是接口名派生节点。
 * </ol>
 *
 * <p>判定「渠道拒绝」只看顶层 {@code code} 非 0，与「HTTP 200 但业务失败」这一常见形态相符。
 */
@Component
public class DouyinApiSpec extends AbstractChannelApiSpec {

  /** 抖音电商开放平台网关（openapi 已下线独立鉴权，电商场景走 fxg）。 */
  public static final String GATEWAY = "https://openapi-fxg.jinritemai.com";

  public DouyinApiSpec() {
    super(
        "DOUYIN",
        GATEWAY,
        ChannelSignatureAlgorithm.HMAC_SHA256,
        "access_token",
        true,
        "Access-Token",
        List.of("data"),
        false);
  }

  @Override
  public Map<String, CredentialSlot> credentialKeys() {
    return Map.of("app_key", CredentialSlot.APP_KEY, "app_secret", CredentialSlot.APP_SECRET);
  }

  /** 抖音走 HMAC-SHA256，不需要 {@code sign_method} 参数（骨架按签名算法自动省略）。 */
  @Override
  protected String signMethodValue() {
    return null;
  }

  /** 抖音：HMAC 已经承载密钥，待签串里不再拼接 secret。 */
  @Override
  protected String signInput(Map<String, String> signedParams, String secret) {
    return ChannelSignatureAlgorithm.concat(signedParams);
  }
}
