package com.bone.ainotification.infrastructure.notification;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Webhook 请求体 HMAC-SHA256 签名（接收方据以验真，防伪造告警回调）。 */
public final class WebhookSigner {

  private static final String ALGO = "HmacSHA256";

  private WebhookSigner() {}

  /** 返回十六进制签名串（无密钥时返回空，调用方据此省略签名头）。 */
  public static String sign(String payload, String secret) {
    if (secret == null || secret.isBlank()) {
      return "";
    }
    try {
      Mac mac = Mac.getInstance(ALGO);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGO));
      byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(raw);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("webhook 签名失败", e);
    }
  }
}
